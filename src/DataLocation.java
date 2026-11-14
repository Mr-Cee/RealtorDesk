import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

final class DataLocation {
    static final String KEY="dataDirectory";
    static Path folder(Properties settings,Path fallback)throws IOException {
        String configured=settings.getProperty(KEY,"").trim();
        Path folder=(configured.isEmpty()?fallback:Path.of(configured)).toAbsolutePath().normalize();
        if(!configured.isEmpty()&&(!Files.isDirectory(folder)||!Files.isRegularFile(folder.resolve("realtor.db"))))
            throw new IOException("Your saved data folder is unavailable: "+folder+". Reconnect the drive or restore the folder before opening RealtorDesk. No new database was created.");
        return folder;
    }
    static Path relocate(Connection source,Path current,Path requested,Path settingsFile,Properties updated)throws Exception {
        Path original=current.toRealPath(),target=requested.toAbsolutePath().normalize();
        if(target.startsWith(original)||original.startsWith(target))throw new IOException("Choose a separate empty folder, outside the current data folder.");
        Files.createDirectories(target.getParent());
        target=Files.exists(target)?target.toRealPath():target.getParent().toRealPath().resolve(target.getFileName());
        if(target.startsWith(original)||original.startsWith(target))throw new IOException("The new folder overlaps the current data folder.");
        if(Files.exists(target))try(var files=Files.list(target)){if(files.findAny().isPresent())throw new IOException("Choose an empty folder. Existing files will not be overwritten.");}
        Path stage=Files.createTempDirectory(target.getParent(),"RealtorDesk-move-");boolean installed=false;
        try {
            // SQLite creates a consistent snapshot, including committed WAL contents.
            try(var p=source.prepareStatement("VACUUM main INTO ?")){p.setString(1,stage.resolve("realtor.db").toString());p.execute();}
            try(var walk=Files.walk(original)) {
                for(Path path:walk.toList()) {
                    if(path.equals(original))continue;Path relative=original.relativize(path);String name=relative.toString();
                    if(relative.getNameCount()==1&&(name.equals("settings.properties")||name.equals("realtor.db")||name.startsWith("realtor.db-")))continue;
                    if(!path.toRealPath().startsWith(original)||Files.isSymbolicLink(path))throw new IOException("Cannot relocate linked files or folders: "+path);
                    Path destination=stage.resolve(relative);
                    if(Files.isDirectory(path))Files.createDirectories(destination);
                    else {Files.createDirectories(destination.getParent());Files.copy(path,destination);if(Files.mismatch(path,destination)!=-1)throw new IOException("File verification failed: "+path);}
                }
            }
            Files.createDirectories(stage.resolve("documents"));
            try(var snapshot=DriverManager.getConnection("jdbc:sqlite:"+stage.resolve("realtor.db"))) {
                snapshot.setAutoCommit(false);
                try(var s=snapshot.createStatement();var rows=s.executeQuery("SELECT id,path FROM documents");var update=snapshot.prepareStatement("UPDATE documents SET path=? WHERE id=?")) {
                    while(rows.next()) {
                        String stored=rows.getString(2);if(stored==null||stored.isBlank())throw new IOException("A document has no file path. Resolve missing documents before moving data.");
                        Path file=Path.of(stored);if(!file.isAbsolute())file=original.resolve(file);file=file.toRealPath();
                        if(!Files.isRegularFile(file))throw new IOException("Document file is unavailable: "+file);
                        Path relative;
                        if(file.startsWith(original))relative=original.relativize(file);
                        else {relative=Path.of("documents",UUID.randomUUID()+"-"+file.getFileName());Files.copy(file,stage.resolve(relative));if(Files.mismatch(file,stage.resolve(relative))!=-1)throw new IOException("Document verification failed: "+file);}
                        if(!Files.isRegularFile(stage.resolve(relative)))throw new IOException("Copied document is missing: "+relative);
                        update.setString(1,target.resolve(relative).toString());update.setLong(2,rows.getLong(1));update.executeUpdate();
                    }
                    snapshot.commit();
                }catch(Exception e){snapshot.rollback();throw e;}
                try(var s=snapshot.createStatement();var row=s.executeQuery("PRAGMA quick_check")){if(!row.next()||!row.getString(1).equals("ok"))throw new IOException("The copied database failed verification.");}
            }
            if(Files.exists(target))Files.delete(target); // Validated empty; never remove existing data.
            Files.move(stage,target);installed=true;
            var saved=new Properties();saved.putAll(updated);saved.setProperty(KEY,target.toString());
            AppSettings.save(settingsFile,saved);updated.clear();updated.putAll(saved);
            return target;
        }finally {
            // Remove only our private staging folder. Original data is always retained.
            if(!installed&&Files.exists(stage)&&stage.getParent().equals(target.getParent())&&stage.getFileName().toString().startsWith("RealtorDesk-move-"))
                try(var walk=Files.walk(stage)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
        }
    }
}
