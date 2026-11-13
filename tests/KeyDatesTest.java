import java.nio.file.*;
import java.sql.*;
import java.time.*;
import javax.swing.*;

final class KeyDatesTest {
    static int checks;
    static void check(boolean value){if(!value)throw new AssertionError("Key dates check "+checks);checks++;}
    static int run() throws Exception {
        Path file=Files.createTempFile("RealtorDesk-key-dates-",".db");
        try {
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+file);var s=db.createStatement()) {
                s.execute("CREATE TABLE clients(id INTEGER PRIMARY KEY,name TEXT NOT NULL,phone TEXT,email TEXT,status TEXT,notes TEXT,client_type TEXT,first_name TEXT,last_name TEXT,seller_address TEXT,last_communicated TEXT)");
                s.execute("INSERT INTO clients VALUES(7,'Jamie Smith','555','','Active','Keep notes','Seller','Jamie','Smith','123 Oak Street','2026-11-13')");
                s.execute("PRAGMA user_version=5");ClientStore.migrate(db);ClientStore.migrate(db);
                var old=ClientStore.load(db,7);check(old.keyDates().equals(KeyDates.EMPTY));check(old.lastCommunicated().equals("2026-11-13"));check(old.sellerAddress().equals("123 Oak Street"));
                try(var r=s.executeQuery("SELECT date_start,date_offer,date_close FROM clients WHERE id=7")){check(r.next());check(r.getString(1)==null&&r.getString(2)==null&&r.getString(3)==null);}
                ClientStore.save(db,7L,"Jamie","Smith","555","","Active","Keep notes",ClientStore.Type.SELLER,"123 Oak Street","2026-11-13",new KeyDates("2026-01-01","2026-01-11","2026-01-31"));
                // An unrelated edit must preserve all three reporting fields.
                ClientStore.save(db,7L,"Jamie","Smith","555","","Active","Updated notes",ClientStore.Type.SELLER);
                check(ClientStore.load(db,7).keyDates().equals(new KeyDates("2026-01-01","2026-01-11","2026-01-31")));
                ClientStore.save(db,null,"Alex","Brown","555","","Lead","",ClientStore.Type.BUYER,"","",new KeyDates("2026-02-01","",""));
                try(var r=s.executeQuery("SELECT AVG(julianday(date_offer)-julianday(date_start)), AVG(julianday(date_close)-julianday(date_offer)), AVG(julianday(date_close)-julianday(date_start)) FROM clients")) {
                    check(r.next());check(r.getDouble(1)==10);check(r.getDouble(2)==20);check(r.getDouble(3)==30);
                }
            }
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+file)) {
                check(ClientStore.load(db,7).keyDates().close().equals("2026-01-31"));
                ClientStore.save(db,7L,"Jamie","Smith","555","","Active","Updated notes",ClientStore.Type.SELLER,"123 Oak Street","2026-11-13",new KeyDates("2026-01-01","",null));
                check(ClientStore.load(db,7).keyDates().equals(new KeyDates("2026-01-01","","")));
            }
        } finally {Files.deleteIfExists(file);}
        check(new KeyDates(null," ",null).equals(KeyDates.EMPTY));check(new KeyDates(" 2024-02-29 ","","").start().equals("2024-02-29"));
        try{new KeyDates("2025-02-29","","");throw new AssertionError();}catch(java.time.format.DateTimeParseException expected){checks++;}
        SwingUtilities.invokeAndWait(()->{
            var panel=new KeyDatesPanel(new KeyDates("2026-01-01","2026-01-11","2026-01-31"));
            check(panel.getComponentCount()==6);check(panel.dates().start().equals("2026-01-01"));
            panel.offer.setDate(null);panel.close.setDate(LocalDate.of(2026,2,1));
            check(panel.dates().equals(new KeyDates("2026-01-01","","2026-02-01")));
        });
        return checks;
    }
}
