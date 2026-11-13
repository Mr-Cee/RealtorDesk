import java.nio.file.*;
import java.sql.*;
import java.util.*;
import javax.swing.*;
import javax.swing.table.*;

final class PropertyMlsTest {
    static int checks;
    static void check(boolean value){if(!value)throw new AssertionError("MLS check "+checks);checks++;}
    static int run()throws Exception {
        Path file=Files.createTempFile("RealtorDesk-mls-",".db");
        try {
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+file);var s=db.createStatement()) {
                s.execute("CREATE TABLE clients(id INTEGER PRIMARY KEY,name TEXT NOT NULL,phone TEXT,email TEXT,status TEXT,notes TEXT)");ClientStore.migrate(db);ClientStore.save(db,null,"Jamie","Smith","555","","Active","",ClientStore.Type.SELLER);
                s.execute("CREATE TABLE properties(id INTEGER PRIMARY KEY,client_id INTEGER REFERENCES clients(id),address TEXT NOT NULL,status TEXT,notes TEXT)");s.execute("INSERT INTO properties VALUES(5,1,'12 N Main St, Juneau, AK 99801','Closed','Keep notes')");
                PropertyStore.migrate(db);PropertyStore.migrate(db);var old=PropertyStore.load(db,5);check(old.mlsNumber().isEmpty());check(old.clientId()==1L);check(old.notes().equals("Keep notes"));
                PropertyStore.save(db,new PropertyStore.Property(old.id(),old.clientId(),old.address(),old.status(),old.notes(),old.parts(),54000000L," 001-A25 "));
                PropertyStore.saveClient(db,1L,()->ClientStore.save(db,1L,"Jamie","Smith","555","","Active","Updated",ClientStore.Type.SELLER),PropertyStore.list(db,1L));
                check(PropertyStore.load(db,5).mlsNumber().equals("001-A25"));
                PropertyStore.saveClient(db,1L,()->ClientStore.save(db,1L,"Jamie","Smith","555","","Active","Updated",ClientStore.Type.SELLER),java.util.List.of());check(PropertyStore.load(db,5).mlsNumber().equals("001-A25"));
            }
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+file)) {
                var p=PropertyStore.load(db,5);check(p.mlsNumber().equals("001-A25"));check(p.closedPriceCents()==54000000L);check(p.clientId()==null);
                PropertyStore.save(db,new PropertyStore.Property(p.id(),null,p.address(),p.status(),p.notes(),p.parts(),p.closedPriceCents(),""));check(PropertyStore.load(db,5).mlsNumber().isEmpty());
            }
        }finally{Files.deleteIfExists(file);}
        SwingUtilities.invokeAndWait(()->{
            var model=new DefaultTableModel(new Object[][]{{5L,"Jamie","12 N Main St, Juneau, AK 99801","Closed",54000000L,"001-A25"},{9L,"Alex","55 Pine Lane, Anchorage, AK 99501","Active",null,"77889"},{11L,null,"99 Oak Road, Wasilla, AK 99654","Closed",null,""}},new String[]{"ID","Client","Address","Status","Closed Price","MLS #"}){public Class<?> getColumnClass(int c){return c==4?Long.class:Object.class;}};
            var table=new JTable(model);table.setAutoCreateRowSorter(true);PropertyPrice.configureTable(table);var search=new JTextField();var count=new JLabel();
            ClientSearch.attach(table,search,count,"properties","No properties yet.","Address or MLS",2,5);
            for(String query:new String[]{"12","N Main","juneau","99801","001","a25","juneau a25","55 99501","pine anchorage","778","wasilla oak"}){search.setText(query);check(table.getRowCount()==1);}
            search.setText("juneau 778");check(table.getRowCount()==0);check(count.getText().contains("No matches"));
            search.setText("Closed");check(table.getRowCount()==0);search.setText(".*");check(table.getRowCount()==0);
            table.getRowSorter().toggleSortOrder(2);search.setText("77889");var edited=new ArrayList<Long>();var edit=ClientTableActions.install(table,edited::add,()->{});table.setRowSelectionInterval(0,0);edit.run();check(edited.equals(java.util.List.of(9L)));
            model.setValueAt("NEW-88",1,5);table.getRowSorter().allRowsChanged();check(table.getRowCount()==0);search.setText("new-88");check(table.getRowCount()==1);
            search.setText("");check(table.getRowCount()==3);check(count.getText().equals("3 of 3 properties"));check(table.getColumnCount()==5);
        });return checks;
    }
}
