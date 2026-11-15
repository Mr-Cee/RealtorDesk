import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.zip.*;

final class BackupTest {
    static int checks;static void check(boolean b){if(!b)throw new AssertionError("Backup check "+checks);checks++;}
    interface Work{void run()throws Exception;}
    static void rejects(Work work)throws Exception{try{work.run();throw new AssertionError("Expected restore/backup rejection");}catch(IOException expected){checks++;}}
    static int run()throws Exception {
        Path root=Files.createTempDirectory("RealtorDesk-backup-test-").toRealPath();
        try {
            Path data=Files.createDirectory(root.resolve("data")),archive=root.resolve("manual.rdbackup"),settingsFile=data.resolve("settings.properties");var settings=new Properties();settings.setProperty("theme","ocean");AppSettings.save(settingsFile,settings);
            Path input=root.resolve("contract.txt");Files.writeString(input,"Original contract");Files.createDirectories(data.resolve("exports"));Files.writeString(data.resolve("exports/file.txt"),"Original export");
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+data.resolve("realtor.db"));var s=db.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");s.execute("CREATE TABLE clients(id INTEGER PRIMARY KEY,name TEXT NOT NULL,phone TEXT,email TEXT,status TEXT,notes TEXT)");ClientStore.migrate(db);ClientStore.save(db,null,"Original","Client","555","","Lead","",ClientStore.Type.BUYER);
                s.execute("CREATE TABLE properties(id INTEGER PRIMARY KEY,client_id INTEGER REFERENCES clients(id),address TEXT NOT NULL,status TEXT,notes TEXT)");PropertyStore.migrate(db);s.execute("CREATE TABLE documents(id INTEGER PRIMARY KEY,client_id INTEGER REFERENCES clients(id),name TEXT,path TEXT,added TEXT)");DocumentStore.add(db,data.resolve("documents"),1L,input);
                TaskStore.migrate(db);TaskStore.save(db,new TaskStore.Task(null,"Backed up task",1L,null,"2026-11-15","High","Preserve",false,true,null));
                String before=BackupStore.fingerprint(db,data);check(BackupStore.create(db,data,archive).equals(archive));check(Files.size(archive)>0);check(BackupStore.fingerprint(db,data).equals(before));
                try(var zip=new ZipFile(archive.toFile())){check(zip.getEntry("realtor.db")!=null);check(zip.getEntry(BackupStore.MANIFEST)!=null);check(zip.getEntry("settings.properties")==null);}
                rejects(()->BackupStore.create(db,data,archive));s.execute("UPDATE clients SET name='Newer record' WHERE id=1");check(!BackupStore.fingerprint(db,data).equals(before));
                Path restored=root.resolve("restored");check(BackupStore.restore(archive,restored,data,settingsFile,settings).equals(restored));check(DataLocation.folder(AppSettings.load(settingsFile),data).equals(restored));
                check(ClientStore.load(db,1L).name().equals("Newer record"));
                try(var copy=DriverManager.getConnection("jdbc:sqlite:"+restored.resolve("realtor.db"))){check(ClientStore.load(copy,1L).name().equals("Original Client"));var doc=DocumentStore.documents(copy,1L).get(0);check(DocumentStore.file(copy,1L,doc.id()).startsWith(restored));check(Files.readString(DocumentStore.file(copy,1L,doc.id())).equals("Original contract"));check(TaskStore.load(copy,1).title().equals("Backed up task"));}
                check(Files.readString(restored.resolve("exports/file.txt")).equals("Original export"));
                Path corrupt=root.resolve("corrupt.rdbackup");try(var original=new ZipFile(archive.toFile());var out=new ZipOutputStream(Files.newOutputStream(corrupt))){var entries=original.entries();while(entries.hasMoreElements()){var entry=entries.nextElement();out.putNextEntry(new ZipEntry(entry.getName()));if(entry.getName().equals("exports/file.txt"))out.write("Tampered".getBytes());else try(var in=original.getInputStream(entry)){in.transferTo(out);}out.closeEntry();}}
                rejects(()->BackupStore.restore(corrupt,root.resolve("bad-restored"),data,settingsFile,settings));check(!Files.exists(root.resolve("bad-restored")));check(DataLocation.folder(AppSettings.load(settingsFile),data).equals(restored));
                Path unsafe=root.resolve("unsafe.rdbackup");try(var zip=new ZipOutputStream(Files.newOutputStream(unsafe))){zip.putNextEntry(new ZipEntry("../escaped.txt"));zip.write(1);zip.closeEntry();}rejects(()->BackupStore.restore(unsafe,root.resolve("unsafe-restored"),data,settingsFile,settings));check(!Files.exists(root.resolve("escaped.txt")));
                Files.createDirectories(data.resolve("backups"));Files.writeString(data.resolve("backups/manual-keep.rdbackup"),"manual");for(int i=0;i<7;i++)try(var zip=new ZipOutputStream(Files.newOutputStream(data.resolve("backups/auto-0"+i+".rdbackup")))){zip.putNextEntry(new ZipEntry(BackupStore.MANIFEST));var props=new Properties();props.setProperty("kind","automatic");props.store(zip,"");zip.closeEntry();}BackupStore.create(db,data,data.resolve("backups/auto-08-manual.rdbackup"));BackupStore.retainAutomatic(data);try(var files=Files.list(data.resolve("backups"))){check(files.count()==7);}check(Files.exists(data.resolve("backups/manual-keep.rdbackup")));check(!Files.exists(data.resolve("backups/auto-00.rdbackup")));check(Files.exists(data.resolve("backups/auto-08-manual.rdbackup")));
                s.execute("UPDATE documents SET path='missing-attachment.txt'");rejects(()->BackupStore.create(db,data,root.resolve("missing.rdbackup")));check(!Files.exists(root.resolve("missing.rdbackup")));
            }
        }finally{if(!root.getParent().equals(Path.of(System.getProperty("java.io.tmpdir")).toRealPath()))throw new AssertionError("Unsafe cleanup");try(var files=Files.walk(root)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
        return checks;
    }
}
