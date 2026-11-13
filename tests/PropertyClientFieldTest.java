import java.sql.*;
import javax.swing.*;

final class PropertyClientFieldTest {
    static int checks;
    static void check(boolean value){if(!value)throw new AssertionError("Property client field check "+checks);checks++;}
    static int run()throws Exception {
        try(var db=DriverManager.getConnection("jdbc:sqlite::memory:");var s=db.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");s.execute("CREATE TABLE clients(id INTEGER PRIMARY KEY,name TEXT NOT NULL,phone TEXT,email TEXT,status TEXT,notes TEXT)");ClientStore.migrate(db);
            ClientStore.save(db,null,"Alex","Smith","555-0101","","Active","",ClientStore.Type.BUYER);
            ClientStore.save(db,null,"Alex","Smith","555-0102","","Active","",ClientStore.Type.SELLER);
            s.execute("CREATE TABLE properties(id INTEGER PRIMARY KEY,client_id INTEGER REFERENCES clients(id),address TEXT NOT NULL,status TEXT,notes TEXT)");PropertyStore.migrate(db);
            var address=new PropertyAddress("12","","Main","","","Juneau","AK","99801");
            PropertyStore.save(db,new PropertyStore.Property(null,2L,address.formatted(),"Closed","Keep notes",address,54000000L));
            SwingUtilities.invokeAndWait(()->{try {
                var old=PropertyStore.load(db,1);var field=new PropertyClientField(db,old.clientId());
                check(field.getItemCount()==3);check(field.clientId()==2L);check(field.getSelectedItem().toString().contains("555-0102"));
                // Saving other changes with the preselected owner preserves the existing assignment and price.
                PropertyStore.save(db,new PropertyStore.Property(old.id(),field.clientId(),old.address(),"Closed",old.notes(),old.parts(),old.closedPriceCents()));
                check(PropertyStore.load(db,1).clientId()==2L);check(PropertyStore.load(db,1).closedPriceCents()==54000000L);
                field.setSelectedIndex(1);check(field.clientId()==1L);check(PropertyStore.load(db,1).clientId()==2L); // Merely changing the form does not write.
                PropertyStore.save(db,new PropertyStore.Property(old.id(),field.clientId(),old.address(),old.status(),old.notes(),old.parts(),old.closedPriceCents()));check(PropertyStore.load(db,1).clientId()==1L);
                field.setSelectedIndex(0);check(field.clientId()==null);
                PropertyStore.save(db,new PropertyStore.Property(old.id(),field.clientId(),old.address(),old.status(),old.notes(),old.parts(),old.closedPriceCents()));check(PropertyStore.load(db,1).clientId()==null);check(PropertyStore.load(db,1).notes().equals("Keep notes"));
                var fresh=new PropertyClientField(db,null);check(fresh.clientId()==null);
            }catch(Exception e){throw new RuntimeException(e);}});
            try{new PropertyClientField(db,999L);throw new AssertionError();}catch(SQLException expected){checks++;}
        }return checks;
    }
}
