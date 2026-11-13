import java.nio.file.*;
import java.sql.*;
import java.util.*;
import javax.swing.*;
import javax.swing.table.*;

final class PropertyPriceTest {
    static int checks;
    static void check(boolean value){if(!value)throw new AssertionError("Price check "+checks);checks++;}
    static int run()throws Exception {
        check(PropertyPrice.parse("")==null);check(PropertyPrice.parse("0")==0);check(PropertyPrice.parse("$425,000.25")==42500025L);check(PropertyPrice.parse("425000.2")==42500020L);
        check(PropertyPrice.formatted(42500025L).equals("$425,000.25"));check(PropertyPrice.editable(42500025L).equals("425000.25"));check(PropertyPrice.formatted(null).isEmpty());
        for(String bad:new String[]{"-1","1.234","42,50","NaN","$","99999999999999999999999"})try{PropertyPrice.parse(bad);throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}
        Path file=Files.createTempFile("RealtorDesk-price-",".db");
        try {
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+file);var s=db.createStatement()) {
                s.execute("CREATE TABLE clients(id INTEGER PRIMARY KEY,name TEXT NOT NULL,phone TEXT,email TEXT,status TEXT,notes TEXT)");ClientStore.migrate(db);
                ClientStore.save(db,null,"Jamie","Smith","555","","Active","",ClientStore.Type.SELLER);
                s.execute("CREATE TABLE properties(id INTEGER PRIMARY KEY,client_id INTEGER REFERENCES clients(id),address TEXT NOT NULL,status TEXT,notes TEXT,street_number TEXT,direction TEXT,street_name TEXT,street_type TEXT,unit TEXT,city TEXT,state TEXT,zip TEXT)");
                s.execute("INSERT INTO properties VALUES(5,1,'12 Main, Juneau, AK 99801','Sold','Keep notes','12','','Main','','','Juneau','AK','99801')");s.execute("PRAGMA user_version=8");
                PropertyStore.migrate(db);PropertyStore.migrate(db);var old=PropertyStore.load(db,5);
                check(old.closedPriceCents()==null);check(old.notes().equals("Keep notes"));check(old.clientId()==1L);
                PropertyStore.save(db,new PropertyStore.Property(old.id(),old.clientId(),old.address(),old.status(),old.notes(),old.parts(),42500025L));
                PropertyStore.saveClient(db,1L,()->ClientStore.save(db,1L,"Jamie","Smith","555","","Active","Updated",ClientStore.Type.SELLER),PropertyStore.list(db,1L));
                check(PropertyStore.load(db,5).closedPriceCents()==42500025L);
                PropertyStore.saveClient(db,1L,()->ClientStore.save(db,1L,"Jamie","Smith","555","","Active","Updated",ClientStore.Type.SELLER),java.util.List.of());
                check(PropertyStore.load(db,5).closedPriceCents()==42500025L);check(PropertyStore.load(db,5).clientId()==null);
            }
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+file)) {
                var saved=PropertyStore.load(db,5);check(saved.closedPriceCents()==42500025L);
                PropertyStore.save(db,new PropertyStore.Property(saved.id(),null,saved.address(),saved.status(),saved.notes(),saved.parts(),null));check(PropertyStore.load(db,5).closedPriceCents()==null);check(PropertyStore.load(db,5).notes().equals("Keep notes"));
            }
        }finally{Files.deleteIfExists(file);}
        SwingUtilities.invokeAndWait(()->{
            var model=new DefaultTableModel(new Object[][]{{1L,"A","100 Oak","Sold",90000L},{2L,"B","200 Oak","Sold",100000L},{3L,"C","300 Oak","Listed",null}},new String[]{"ID","Client","Address","Status","Closed Price"}){public Class<?> getColumnClass(int c){return c==4?Long.class:Object.class;}};
            var table=new JTable(model);table.setAutoCreateRowSorter(true);PropertyPrice.configureTable(table);
            check(table.getColumnCount()==4);check(table.getColumnName(0).equals("Client"));check(table.getColumnName(3).equals("Closed Price"));
            table.getRowSorter().toggleSortOrder(4);check(table.convertRowIndexToModel(0)==2);check(table.convertRowIndexToModel(1)==0);check(table.convertRowIndexToModel(2)==1);
            var label=(JLabel)table.prepareRenderer(table.getCellRenderer(1,3),1,3);check(label.getText().equals("$900.00"));
            var edited=new ArrayList<Long>();var action=ClientTableActions.install(table,edited::add,()->{});table.setRowSelectionInterval(2,2);action.run();check(edited.equals(java.util.List.of(2L)));
        });return checks;
    }
}
