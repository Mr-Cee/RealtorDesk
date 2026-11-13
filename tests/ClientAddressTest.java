import java.nio.file.*;
import java.sql.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

final class ClientAddressTest {
    static int checks;
    static void check(boolean value){if(!value)throw new AssertionError("Seller address check "+checks);checks++;}
    static int run() throws Exception {
        Path file=Files.createTempFile("RealtorDesk-address-test-",".db");
        try {
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+file);var s=db.createStatement()) {
                s.execute("CREATE TABLE clients(id INTEGER PRIMARY KEY,name TEXT NOT NULL,phone TEXT,email TEXT,status TEXT,notes TEXT,client_type TEXT NOT NULL,first_name TEXT NOT NULL,last_name TEXT NOT NULL)");
                s.execute("INSERT INTO clients VALUES(7,'Mary Ann Smith','555','mary@example.test','Active','Keep notes','Seller','Mary Ann','Smith')");
                s.execute("PRAGMA user_version=3");
                ClientStore.migrate(db);ClientStore.migrate(db);
                var old=ClientStore.load(db,7);check(old.sellerAddress().isEmpty());check(old.firstName().equals("Mary Ann"));check(old.notes().equals("Keep notes"));
                try(var r=s.executeQuery("PRAGMA user_version")){check(r.next()&&r.getInt(1)==6);}
                ClientStore.save(db,7L,"Mary Ann","Smith","555","mary@example.test","Active","Keep notes",ClientStore.Type.SELLER,"  123 Oak Street, Unit 4, Anchorage, AK 99501  ");
                ClientStore.migrate(db);check(ClientStore.load(db,7).sellerAddress().equals("123 Oak Street, Unit 4, Anchorage, AK 99501"));
                ClientStore.save(db,null,"Jamie","O'Brien","555","","Lead","",ClientStore.Type.BOTH,"45 Pine Lane, Juneau, AK 99801");
                try(var r=s.executeQuery("SELECT seller_address FROM clients WHERE name='Jamie O''Brien'")){check(r.next()&&r.getString(1).contains("Pine Lane"));}
                ClientStore.save(db,7L,"Mary Ann","Smith","555","mary@example.test","Active","Keep notes",ClientStore.Type.BUYER);
                check(ClientStore.load(db,7).sellerAddress().contains("Oak Street"));
                ClientStore.save(db,7L,"Mary Ann","Smith","555","mary@example.test","Active","Keep notes",ClientStore.Type.BOTH);
            }
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+file)) {
                var saved=ClientStore.load(db,7);check(saved.sellerAddress().contains("99501"));check(saved.type()==ClientStore.Type.BOTH);
                ClientStore.save(db,7L,"Mary Ann","Smith","555","mary@example.test","Active","Keep notes",ClientStore.Type.SELLER,"");
                check(ClientStore.load(db,7).sellerAddress().isEmpty());
            }
        } finally {Files.deleteIfExists(file);}
        check(ClientStore.Type.SELLER.isSeller());check(ClientStore.Type.BOTH.isSeller());check(!ClientStore.Type.BUYER.isSeller());check(!ClientStore.Type.UNSPECIFIED.isSeller());
        SwingUtilities.invokeAndWait(()->{
            var model=new DefaultTableModel(new Object[][]{
                {7L,"Mary Ann","Smith","555","mary@example.test","Seller","123 Oak Street, Unit 4, Anchorage, AK 99501","Active",""},
                {9L,"Jamie","Brown","555","","Buyer & Seller","45 Pine Lane, Juneau, AK 99801","Lead",""},
                {10L,"Alex","Smith","555","","Buyer","","Lead",""}
            },new String[]{"ID","First name","Last name","Phone","Email","Client type","Seller address","Status","Notes"});
            var table=new JTable(model);table.setAutoCreateRowSorter(true);var search=new JTextField();var count=new JLabel();ClientSearch.attach(table,search,count);
            for(String query:new String[]{"oak","SMITH Oak","123 street","Unit 4","Anchorage 99501","mary 99501","brown pine"}){search.setText(query);check(table.getRowCount()==1);}
            search.setText("smith pine");check(table.getRowCount()==0);
            search.setText("oak");model.setValueAt("55 Birch Road, Fairbanks, AK 99701",0,6);
            table.getRowSorter().allRowsChanged();check(table.getRowCount()==0);
            search.setText("smith birch");check(table.getRowCount()==1);check(count.getText().equals("1 of 3 clients"));
            search.setText("");check(table.getRowCount()==3);
        });
        return checks;
    }
}
