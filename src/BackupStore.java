import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.zip.*;

final class BackupStore {
    static final String MANIFEST="backup-manifest.properties";
    static String hash(InputStream in)throws Exception {var digest=MessageDigest.getInstance("SHA-256");byte[] bytes=new byte[65536];for(int n;(n=in.read(bytes))!=-1;)digest.update(bytes,0,n);return HexFormat.of().formatHex(digest.digest());}
    static String hash(Path file)throws Exception {try(var in=Files.newInputStream(file)){return hash(in);}}
    static boolean excluded(Path relative){String first=relative.getName(0).toString(),name=relative.getFileName().toString();return first.equals("backups")||name.endsWith(".rdbackup")||relative.getNameCount()==1&&(first.equals("settings.properties")||first.equals("realtor.db")||first.startsWith("realtor.db-"));}
    static Path name(Path data,boolean automatic){return data.resolve("backups").resolve((automatic?"auto-":"manual-")+LocalDateTime.now().toString().replace(":","-")+"-"+UUID.randomUUID()+".rdbackup");}
    static Path create(Connection db,Path data,Path archive)throws Exception {
        return create(db,data,archive,false);
    }
    static Path create(Connection db,Path data,Path archive,boolean automatic)throws Exception {
        Path root=data.toRealPath();archive=archive.toAbsolutePath().normalize();Files.createDirectories(archive.getParent());if(Files.exists(archive))throw new IOException("Choose a new backup filename; existing backups are not overwritten.");
        Path stage=Files.createTempDirectory("RealtorDesk-backup-work-");Path temporary=Files.createTempFile(archive.getParent(),"backup-",".tmp");
        try {
            try(var p=db.prepareStatement("VACUUM main INTO ?")){p.setString(1,stage.resolve("realtor.db").toString());p.execute();}
            try(var files=Files.walk(root)){for(Path file:files.toList()){if(file.equals(root))continue;Path relative=root.relativize(file);if(excluded(relative)||file.toRealPath().equals(temporary.toRealPath()))continue;if(Files.isSymbolicLink(file)||!file.toRealPath().startsWith(root))throw new IOException("Linked data files cannot be backed up: "+file);if(Files.isDirectory(file))Files.createDirectories(stage.resolve(relative));else{Files.createDirectories(stage.resolve(relative).getParent());Files.copy(file,stage.resolve(relative));if(Files.mismatch(file,stage.resolve(relative))!=-1)throw new IOException("A file changed while backing up: "+file);}}}
            Files.createDirectories(stage.resolve("documents"));
            try(var copy=DriverManager.getConnection("jdbc:sqlite:"+stage.resolve("realtor.db"));var s=copy.createStatement();var rows=s.executeQuery("SELECT id,path FROM documents");var update=copy.prepareStatement("UPDATE documents SET path=? WHERE id=?")) {
                while(rows.next()){Path file=Path.of(Objects.requireNonNull(rows.getString(2),"Missing document path"));if(!file.isAbsolute())file=root.resolve(file);file=file.toRealPath();Path relative;
                    if(file.startsWith(root)&&!excluded(root.relativize(file)))relative=root.relativize(file);else{relative=Path.of("documents",UUID.randomUUID()+"-"+file.getFileName());Files.copy(file,stage.resolve(relative));if(Files.mismatch(file,stage.resolve(relative))!=-1)throw new IOException("Document changed during backup: "+file);}
                    if(!Files.isRegularFile(stage.resolve(relative)))throw new IOException("A document is missing: "+file);update.setString(1,relative.toString().replace('\\','/'));update.setLong(2,rows.getLong(1));update.executeUpdate();}
            }
            var manifest=new Properties();manifest.setProperty("format","1");manifest.setProperty("kind",automatic?"automatic":"manual");manifest.setProperty("version",BuildInfo.VERSION);manifest.setProperty("created",Instant.now().toString());
            try(var files=Files.walk(stage)){for(Path file:files.filter(Files::isRegularFile).toList())manifest.setProperty("file:"+stage.relativize(file).toString().replace('\\','/'),hash(file));}
            try(var zip=new ZipOutputStream(Files.newOutputStream(temporary))){try(var files=Files.walk(stage)){for(Path file:files.filter(Files::isRegularFile).toList()){zip.putNextEntry(new ZipEntry(stage.relativize(file).toString().replace('\\','/')));Files.copy(file,zip);zip.closeEntry();}}zip.putNextEntry(new ZipEntry(MANIFEST));manifest.store(zip,"RealtorDesk backup");zip.closeEntry();}
            try(var zip=new ZipFile(temporary.toFile())){if(zip.getEntry(MANIFEST)==null||zip.getEntry("realtor.db")==null)throw new IOException("Backup verification failed.");for(String key:manifest.stringPropertyNames())if(key.startsWith("file:")){var entry=zip.getEntry(key.substring(5));if(entry==null)throw new IOException("Incomplete backup.");try(var in=zip.getInputStream(entry)){if(!hash(in).equals(manifest.getProperty(key)))throw new IOException("Backup checksum verification failed.");}}}
            Files.move(temporary,archive);return archive;
        }finally{Files.deleteIfExists(temporary);cleanup(stage,"RealtorDesk-backup-work-");}
    }
    static Path restore(Path archive,Path destination,Path current,Path settingsFile,Properties settings)throws Exception {
        Path original=current.toRealPath(),target=destination.toAbsolutePath().normalize();Files.createDirectories(target.getParent());target=Files.exists(target)?target.toRealPath():target.getParent().toRealPath().resolve(target.getFileName());
        if(target.startsWith(original)||original.startsWith(target))throw new IOException("Restore to a separate empty folder.");if(Files.exists(target))try(var files=Files.list(target)){if(files.findAny().isPresent())throw new IOException("Restore folder must be empty.");}
        Path stage=Files.createTempDirectory(target.getParent(),"RealtorDesk-restore-");boolean installed=false;
        try {
            var entries=new HashSet<String>();long total=0;
            try(var zip=new ZipInputStream(Files.newInputStream(archive))){for(ZipEntry entry;(entry=zip.getNextEntry())!=null;){String name=entry.getName();Path output=stage.resolve(name).normalize();if(name.contains("\\")||name.contains(":")||!output.startsWith(stage)||name.startsWith("/")||!entries.add(name))throw new IOException("Unsafe or duplicate backup entry.");if(entry.isDirectory()){Files.createDirectories(output);continue;}Files.createDirectories(output.getParent());try(var out=Files.newOutputStream(output,StandardOpenOption.CREATE_NEW)){byte[] buffer=new byte[65536];for(int n;(n=zip.read(buffer))!=-1;){total+=n;if(total>20L*1024*1024*1024)throw new IOException("Backup exceeds the 20 GB restore limit.");out.write(buffer,0,n);}}}}
            var manifest=AppSettings.load(stage.resolve(MANIFEST));if(!"1".equals(manifest.getProperty("format"))||!Files.isRegularFile(stage.resolve("realtor.db")))throw new IOException("This is not a supported RealtorDesk backup.");
            if(Updater.compareVersions(manifest.getProperty("version","0.0.0"),BuildInfo.VERSION)>0)throw new IOException("Update RealtorDesk before restoring this newer backup.");
            try(var files=Files.walk(stage)){for(Path file:files.filter(Files::isRegularFile).toList()){String name=stage.relativize(file).toString().replace('\\','/');if(name.equals(MANIFEST))continue;if(!hash(file).equals(manifest.getProperty("file:"+name)))throw new IOException("Backup checksum failed: "+name);}}
            for(String key:manifest.stringPropertyNames())if(key.startsWith("file:")){Path file=stage.resolve(key.substring(5)).normalize();if(!file.startsWith(stage)||!Files.isRegularFile(file))throw new IOException("Backup is incomplete or unsafe.");}
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+stage.resolve("realtor.db"));var s=db.createStatement()) {
                try(var r=s.executeQuery("PRAGMA quick_check")){if(!r.next()||!"ok".equals(r.getString(1)))throw new IOException("Backup database failed verification.");}
                for(String table:new String[]{"clients","properties","documents"})try(var r=s.executeQuery("SELECT COUNT(*) FROM "+table)){r.next();}
                try(var rows=s.executeQuery("SELECT id,path FROM documents");var p=db.prepareStatement("UPDATE documents SET path=? WHERE id=?")){while(rows.next()){String name=rows.getString(2);if(name==null)throw new IOException("Missing document path.");Path relative=Path.of(name),file=stage.resolve(relative).normalize();if(relative.isAbsolute()||!file.startsWith(stage)||!Files.isRegularFile(file))throw new IOException("Backup document is unavailable.");p.setString(1,target.resolve(relative).normalize().toString());p.setLong(2,rows.getLong(1));p.executeUpdate();}}
            }
            Files.delete(stage.resolve(MANIFEST));Files.createDirectories(stage.resolve("documents"));if(Files.exists(target))Files.delete(target);Files.move(stage,target);installed=true;
            var updated=new Properties();updated.putAll(settings);updated.setProperty(DataLocation.KEY,target.toString());AppSettings.save(settingsFile,updated);settings.clear();settings.putAll(updated);return target;
        }finally{if(!installed)cleanup(stage,"RealtorDesk-restore-");}
    }
    static void cleanup(Path folder,String prefix)throws IOException {if(!folder.getFileName().toString().startsWith(prefix))throw new IOException("Unexpected work folder");try(var files=Files.walk(folder)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    static boolean automatic(Path file){try(var zip=new ZipFile(file.toFile())){var entry=zip.getEntry(MANIFEST);if(entry==null)return false;var properties=new Properties();try(var in=zip.getInputStream(entry)){properties.load(in);}return "automatic".equals(properties.getProperty("kind"));}catch(IOException e){return false;}}
    static void retainAutomatic(Path data)throws IOException {Path folder=data.resolve("backups");if(!Files.isDirectory(folder))return;try(var files=Files.list(folder)){var backups=files.filter(p->p.getFileName().toString().startsWith("auto-")&&p.toString().endsWith(".rdbackup")&&automatic(p)).sorted(Comparator.comparing((Path p)->p.getFileName().toString()).reversed()).toList();for(int i=5;i<backups.size();i++)Files.delete(backups.get(i));}}
    static String fingerprint(Connection db,Path data)throws Exception {var digest=MessageDigest.getInstance("SHA-256");try(var s=db.createStatement();var r=s.executeQuery("SELECT total_changes(),(SELECT * FROM pragma_data_version)")){r.next();digest.update((r.getLong(1)+":"+r.getLong(2)).getBytes(java.nio.charset.StandardCharsets.UTF_8));}try(var files=Files.walk(data)){for(Path p:files.filter(Files::isRegularFile).sorted().toList())if(!excluded(data.relativize(p)))digest.update((p+":"+Files.size(p)+":"+Files.getLastModifiedTime(p)).getBytes(java.nio.charset.StandardCharsets.UTF_8));}return HexFormat.of().formatHex(digest.digest());}
}
