import java.nio.file.*;
import java.sql.*;
import javax.swing.*;
import javax.swing.table.*;

final class CommissionTest {
    static int checks;
    static void check(boolean value){if(!value)throw new AssertionError("Commission check "+checks);checks++;}
    static int run()throws Exception {
        Path file=Files.createTempFile("RealtorDesk-commission-",".db");
        try {
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+file);var s=db.createStatement()) {
                s.execute("CREATE TABLE clients(id INTEGER PRIMARY KEY,name TEXT NOT NULL,phone TEXT,email TEXT,status TEXT,notes TEXT)");ClientStore.migrate(db);ClientStore.save(db,null,"Jamie","Smith","555","","Active","",ClientStore.Type.SELLER);
                s.execute("CREATE TABLE properties(id INTEGER PRIMARY KEY,client_id INTEGER REFERENCES clients(id),address TEXT NOT NULL,status TEXT,notes TEXT)");s.execute("INSERT INTO properties VALUES(5,1,'12 Main, Juneau, AK 99801','Closed','Keep notes')");
                PropertyStore.migrate(db);PropertyStore.migrate(db);var old=PropertyStore.load(db,5);check(old.commissionCents()==null);
                PropertyStore.save(db,new PropertyStore.Property(old.id(),old.clientId(),old.address(),old.status(),old.notes(),old.parts(),54000000L,"001-MLS",PropertyPrice.parse("$16,200.50")));
                PropertyStore.saveClient(db,1L,()->ClientStore.save(db,1L,"Jamie","Smith","555","","Active","",ClientStore.Type.SELLER),PropertyStore.list(db,1L));check(PropertyStore.load(db,5).commissionCents()==1620050L);
                PropertyStore.saveClient(db,1L,()->ClientStore.save(db,1L,"Jamie","Smith","555","","Active","",ClientStore.Type.SELLER),java.util.List.of());check(PropertyStore.load(db,5).commissionCents()==1620050L);
            }
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+file)) {
                var saved=PropertyStore.load(db,5);check(saved.commissionCents()==1620050L);check(saved.closedPriceCents()==54000000L);check(saved.mlsNumber().equals("001-MLS"));
                try{PropertyStore.save(db,new PropertyStore.Property(saved.id(),null,saved.address(),saved.status(),saved.notes(),saved.parts(),saved.closedPriceCents(),saved.mlsNumber(),-1L));throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}
                PropertyStore.save(db,new PropertyStore.Property(saved.id(),null,saved.address(),saved.status(),saved.notes(),saved.parts(),saved.closedPriceCents(),saved.mlsNumber(),null));check(PropertyStore.load(db,5).commissionCents()==null);
            }
        }finally{Files.deleteIfExists(file);}
        SwingUtilities.invokeAndWait(()->{
            var model=new DefaultTableModel(new Object[][]{{1L,"Jamie","12 Main","Closed",54000000L,"001-MLS",1620050L},{2L,null,"99 Oak","Sold",40000000L,"002-MLS",90000L}},new String[]{"ID","Client","Address","Status","Closed Price","MLS #","Commission"}){public Class<?> getColumnClass(int c){return c==4||c==6?Long.class:Object.class;}};
            var table=new JTable(model);table.setAutoCreateRowSorter(true);PropertyPrice.configureTable(table);
            String[] names={"Client","Address","MLS #","Status","Closed Price","Commission"};for(int i=0;i<names.length;i++)check(table.getColumnName(i).equals(names[i]));
            var label=(JLabel)table.prepareRenderer(table.getCellRenderer(0,5),0,5);check(label.getText().equals("$16,200.50"));check(label.getHorizontalAlignment()==SwingConstants.CENTER);
            table.getRowSorter().toggleSortOrder(6);check(table.convertRowIndexToModel(0)==1);
        });return checks;
    }
}
