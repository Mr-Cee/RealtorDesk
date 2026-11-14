import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

final class DataLocationTest {
    static int checks;
    static void check(boolean value){if(!value)throw new AssertionError("Data location check "+checks);checks++;}
    interface Work{void run()throws Exception;}
    static void rejects(Work work)throws Exception{try{work.run();throw new AssertionError("Expected failure");}catch(IOException expected){checks++;}}
    static int run()throws Exception {
        Path root=Files.createTempDirectory("RealtorDesk-location-test-").toRealPath();
        try {
            Path old=Files.createDirectories(root.resolve("old")),target=root.resolve("new data"),settingsFile=old.resolve("settings.properties");
            var settings=new Properties();settings.setProperty("theme","ocean");settings.setProperty("repository","Mr-Cee/RealtorDesk");AppSettings.save(settingsFile,settings);
            check(DataLocation.folder(settings,old).equals(old));
            Path artifact=Files.createDirectories(old.resolve("exports")).resolve("report.csv");Files.writeString(artifact,"preserve export");
            Path external=root.resolve("external attachment.txt");Files.writeString(external,"external contents");
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+old.resolve("realtor.db"));var s=db.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");s.execute("CREATE TABLE clients(id INTEGER PRIMARY KEY,name TEXT NOT NULL,phone TEXT,email TEXT,status TEXT,notes TEXT)");ClientStore.migrate(db);
                ClientStore.save(db,null,"Jamie","Smith","555","","Active","Keep client",ClientStore.Type.SELLER,"","2026-11-13",new KeyDates("2026-01-01","2026-01-11","2026-01-31"));
                s.execute("CREATE TABLE properties(id INTEGER PRIMARY KEY,client_id INTEGER REFERENCES clients(id),address TEXT NOT NULL,status TEXT,notes TEXT)");PropertyStore.migrate(db);
                var address=new PropertyAddress("12","","Main","","","Juneau","AK","99801");PropertyStore.save(db,new PropertyStore.Property(null,1L,address.formatted(),"Closed","Keep notes",address,54000000L,"001-MLS"));
                s.execute("CREATE TABLE documents(id INTEGER PRIMARY KEY,client_id INTEGER REFERENCES clients(id),name TEXT,path TEXT,added TEXT)");
                DocumentStore.add(db,old.resolve("documents"),1L,external);
                try(var p=db.prepareStatement("INSERT INTO documents(client_id,name,path,added) VALUES(1,'external',?,'2026-11-13')")){p.setString(1,external.toString());p.executeUpdate();}
                String originalPath=DocumentStore.documents(db,1L).stream().filter(d->!d.name().equals("external")).findFirst().orElseThrow().path();
                Path nonempty=Files.createDirectories(root.resolve("nonempty"));Files.writeString(nonempty.resolve("keep.txt"),"keep");
                rejects(()->DataLocation.relocate(db,old,nonempty,settingsFile,settings));check(Files.readString(nonempty.resolve("keep.txt")).equals("keep"));check(!settings.containsKey(DataLocation.KEY));
                rejects(()->DataLocation.relocate(db,old,old.resolve("nested"),settingsFile,settings));check(!Files.exists(old.resolve("nested")));
                check(DataLocation.relocate(db,old,target,settingsFile,settings).equals(target));
                check(Files.isRegularFile(old.resolve("realtor.db")));check(Files.readString(artifact).equals("preserve export"));
                check(Files.readString(target.resolve("exports/report.csv")).equals("preserve export"));check(!Files.exists(target.resolve("settings.properties")));
                check(DocumentStore.documents(db,1L).stream().anyMatch(d->originalPath.equals(d.path()))); // Snapshot source remains unchanged.
            }
            var reopened=AppSettings.load(settingsFile);check(reopened.getProperty("theme").equals("ocean"));check(reopened.getProperty("repository").equals("Mr-Cee/RealtorDesk"));check(DataLocation.folder(reopened,old).equals(target));
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+target.resolve("realtor.db"))) {
                check(ClientStore.load(db,1).keyDates().close().equals("2026-01-31"));check(ClientStore.load(db,1).notes().equals("Keep client"));
                var property=PropertyStore.load(db,1);check(property.clientId()==1L);check(property.closedPriceCents()==54000000L);check(property.mlsNumber().equals("001-MLS"));
                check(DocumentStore.documents(db,1L).size()==2);
                for(var doc:DocumentStore.documents(db,1L)){Path file=DocumentStore.file(db,1L,doc.id());check(file.startsWith(target));check(Files.readString(file).equals("external contents"));}
                Path third=root.resolve("third");check(DataLocation.relocate(db,target,third,settingsFile,reopened).equals(third));check(DataLocation.folder(AppSettings.load(settingsFile),old).equals(third));
            }
            Path third=root.resolve("third");
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+third.resolve("realtor.db"));var s=db.createStatement()) {
                for(var doc:DocumentStore.documents(db,1L))check(DocumentStore.file(db,1L,doc.id()).startsWith(third));
                var updated=AppSettings.load(settingsFile);Path invalidSettings=Files.createDirectory(root.resolve("settings-is-directory"));
                rejects(()->DataLocation.relocate(db,third,root.resolve("inactive verified copy"),invalidSettings,updated));
                check(DataLocation.folder(AppSettings.load(settingsFile),old).equals(third));check(DataLocation.folder(updated,old).equals(third));check(Files.isRegularFile(third.resolve("realtor.db")));
                s.execute("UPDATE documents SET path='missing-file-that-does-not-exist' WHERE id=1");
                rejects(()->DataLocation.relocate(db,third,root.resolve("missing-doc-copy"),settingsFile,updated));
                check(!Files.exists(root.resolve("missing-doc-copy")));check(DataLocation.folder(AppSettings.load(settingsFile),old).equals(third));
            }
            var unavailable=new Properties();unavailable.setProperty(DataLocation.KEY,root.resolve("not-mounted").toString());rejects(()->DataLocation.folder(unavailable,old));check(!Files.exists(root.resolve("not-mounted")));
            try(var files=Files.list(root)){check(files.noneMatch(p->p.getFileName().toString().startsWith("RealtorDesk-move-")));}
        }finally {
            Path parent=Path.of(System.getProperty("java.io.tmpdir")).toRealPath(),target=root.toRealPath();
            if(!target.getParent().equals(parent)||!target.getFileName().toString().startsWith("RealtorDesk-location-test-"))throw new AssertionError("Unsafe cleanup");
            try(var files=Files.walk(target)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
        }
        return checks;
    }
}
