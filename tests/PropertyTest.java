import java.sql.*;
import java.util.*;
import javax.swing.*;

final class PropertyTest {
    static int checks;
    static void check(boolean value){if(!value)throw new AssertionError("Property check "+checks);checks++;}
    static void saveClient(Connection db,Long id,String first)throws Exception{ClientStore.save(db,id,first,"Smith","555","","Active","",ClientStore.Type.SELLER);}
    static int run()throws Exception {
        try(var db=DriverManager.getConnection("jdbc:sqlite::memory:");var s=db.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");s.execute("CREATE TABLE clients(id INTEGER PRIMARY KEY,name TEXT NOT NULL,phone TEXT,email TEXT,status TEXT,notes TEXT)");
            s.execute("CREATE TABLE properties(id INTEGER PRIMARY KEY,client_id INTEGER REFERENCES clients(id),address TEXT NOT NULL,status TEXT,notes TEXT)");ClientStore.migrate(db);
            ClientStore.save(db,null,"Jamie","Smith","555","","Active","",ClientStore.Type.SELLER,"123 Oak Street");
            ClientStore.save(db,null,"Alex","Smith","555","","Active","",ClientStore.Type.BUYER);
            s.execute("INSERT INTO properties(client_id,address,status,notes) VALUES(1,'123 Oak Street','Listed','Keep notes')");
            PropertyStore.migrate(db);PropertyStore.migrate(db);check(PropertyStore.list(db,1L).size()==1);check(PropertyStore.list(db,1L).get(0).notes().equals("Keep notes"));
            PropertyStore.save(db,new PropertyStore.Property(null,null,"45 Pine Lane","Available","No client yet"));
            var unassigned=PropertyStore.list(db,null).get(0);check(unassigned.clientId()==null);
            PropertyStore.save(db,new PropertyStore.Property(unassigned.id(),2L,unassigned.address(),"Under offer",unassigned.notes()));check(PropertyStore.list(db,null).isEmpty());check(PropertyStore.list(db,2L).size()==1);
            PropertyStore.save(db,new PropertyStore.Property(unassigned.id(),null,unassigned.address(),"Available",unassigned.notes()));
            var desired=new ArrayList<>(PropertyStore.list(db,1L));desired.add(unassigned);desired.add(new PropertyStore.Property(null,null,"99 Birch Road","New","Created in client editor"));
            PropertyStore.saveClient(db,1L,()->saveClient(db,1L,"Renamed"),desired);check(PropertyStore.list(db,1L).size()==3);check(ClientStore.load(db,1).firstName().equals("Renamed"));
            long originalId=PropertyStore.list(db,1L).stream().filter(p->p.address().equals("123 Oak Street")).findFirst().orElseThrow().id();
            PropertyStore.saveClient(db,1L,()->saveClient(db,1L,"Renamed"),java.util.List.of(PropertyStore.load(db,originalId)));
            check(PropertyStore.list(db,1L).size()==1);check(PropertyStore.list(db,null).size()==2);PropertyStore.migrate(db);check(PropertyStore.list(db,1L).size()==1);
            // New client plus attached existing and newly created property commit as one unit.
            var available=PropertyStore.list(db,null).get(0);
            PropertyStore.saveClient(db,null,()->saveClient(db,null,"New"),java.util.List.of(available,new PropertyStore.Property(null,null,"100 Cedar Avenue","Lead","")));
            check(PropertyStore.list(db,3L).size()==2);check(ClientStore.load(db,3).name().equals("New Smith"));
            try{PropertyStore.saveClient(db,2L,()->saveClient(db,2L,"Should roll back"),java.util.List.of(PropertyStore.load(db,originalId)));throw new AssertionError();}
            catch(SQLException expected){check(ClientStore.load(db,2).firstName().equals("Alex"));check(PropertyStore.load(db,originalId).clientId()==1L);}
            try{PropertyStore.saveClient(db,null,()->saveClient(db,null,"Should not exist"),java.util.List.of(new PropertyStore.Property(null,null," ","","")));throw new AssertionError();}
            catch(IllegalArgumentException expected){try(var r=s.executeQuery("SELECT COUNT(*) FROM clients")){check(r.next()&&r.getInt(1)==3);}check(db.getAutoCommit());}
            SwingUtilities.invokeAndWait(()->{try {
                var panel=new ClientPropertiesPanel(db,1L);check(panel.drafts().size()==1);
                panel.model.clear();panel.model.addElement(new PropertyStore.Property(null,null,"Draft only","",""));
                check(PropertyStore.list(db,1L).size()==1);check(PropertyStore.list(db,1L).get(0).address().equals("123 Oak Street"));
                var fresh=new ClientPropertiesPanel(db,null);check(fresh.drafts().isEmpty());
            }catch(Exception e){throw new RuntimeException(e);}});
            PropertyStore.save(db,new PropertyStore.Property(originalId,1L,"123 Updated Oak Street","Sold","Changed from Properties"));check(PropertyStore.list(db,1L).get(0).address().equals("123 Updated Oak Street"));
            try(var r=s.executeQuery("SELECT GROUP_CONCAT(address,'; ') FROM properties WHERE client_id=1")){check(r.next()&&r.getString(1).equals("123 Updated Oak Street"));}
        }
        // Seller addresses without an existing matching property become linked records on upgrade.
        try(var db=DriverManager.getConnection("jdbc:sqlite::memory:");var s=db.createStatement()) {
            s.execute("CREATE TABLE clients(id INTEGER PRIMARY KEY,name TEXT NOT NULL,phone TEXT,email TEXT,status TEXT,notes TEXT)");ClientStore.migrate(db);
            s.execute("CREATE TABLE properties(id INTEGER PRIMARY KEY,client_id INTEGER REFERENCES clients(id),address TEXT NOT NULL,status TEXT,notes TEXT)");
            ClientStore.save(db,null,"Legacy","Client","555","","Lead","",ClientStore.Type.SELLER,"50 Legacy Lane");PropertyStore.migrate(db);
            check(PropertyStore.list(db,1L).get(0).address().equals("50 Legacy Lane"));check(ClientStore.load(db,1).sellerAddress().equals("50 Legacy Lane"));
            PropertyStore.saveClient(db,1L,()->saveClient(db,1L,"Legacy"),java.util.List.of());PropertyStore.migrate(db);check(PropertyStore.list(db,1L).isEmpty());check(PropertyStore.list(db,null).size()==1);
        }
        return checks;
    }
}
