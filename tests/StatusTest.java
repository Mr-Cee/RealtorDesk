import java.sql.*;
import javax.swing.*;
import javax.swing.table.*;

final class StatusTest {
    static int checks;
    static void check(boolean value){if(!value)throw new AssertionError("Status check "+checks);checks++;}
    static int run()throws Exception {
        check(Statuses.client(null,ClientStore.Type.BUYER).equals("Lead"));check(Statuses.property(null).equals("Listed"));
        check(Statuses.client("Active",ClientStore.Type.BUYER).equals("Looking for Home"));check(Statuses.client("Active",ClientStore.Type.SELLER).equals("Selling Home"));
        check(Statuses.client("Custom old value",ClientStore.Type.UNSPECIFIED).equals("Lead"));check(Statuses.property("Sold").equals("Closed"));check(Statuses.property("Under contract").equals("Offered"));
        try(var db=DriverManager.getConnection("jdbc:sqlite::memory:");var s=db.createStatement()) {
            s.execute("CREATE TABLE clients(id INTEGER PRIMARY KEY,name TEXT NOT NULL,phone TEXT,email TEXT,status TEXT,notes TEXT)");ClientStore.migrate(db);
            ClientStore.save(db,null,"Alex","Buyer","555","","Active","Keep client",ClientStore.Type.BUYER);
            ClientStore.save(db,null,"Jamie","Seller","555","","Lead","Keep notes",ClientStore.Type.SELLER);
            s.execute("UPDATE clients SET status='Active' WHERE id=1");s.execute("UPDATE clients SET status='Pending' WHERE id=2");
            s.execute("CREATE TABLE properties(id INTEGER PRIMARY KEY,client_id INTEGER REFERENCES clients(id),address TEXT NOT NULL,status TEXT,notes TEXT)");PropertyStore.migrate(db);
            s.execute("INSERT INTO properties(client_id,address,status,notes) VALUES(1,'12 Oak','Sold','Keep property'),(2,'20 Pine','Custom old value','Keep old')");
            Statuses.migrate(db);Statuses.migrate(db);
            check(ClientStore.load(db,1).status().equals("Looking for Home"));check(ClientStore.load(db,2).status().equals("Offered"));check(ClientStore.load(db,1).notes().equals("Keep client"));
            check(PropertyStore.load(db,1).status().equals("Closed"));check(PropertyStore.load(db,2).status().equals("Listed"));check(PropertyStore.load(db,1).clientId()==1L);
            try(var r=s.executeQuery("SELECT status FROM legacy_statuses WHERE record_type='properties' AND record_id=2")){check(r.next()&&r.getString(1).equals("Custom old value"));}
            try(var r=s.executeQuery("SELECT COUNT(*) FROM legacy_statuses")){check(r.next()&&r.getInt(1)==4);}
        }
        SwingUtilities.invokeAndWait(()->{
            var client=Statuses.clientField(null,ClientStore.Type.BUYER);check(client.getItemCount()==5);check(client.getSelectedItem().equals("Lead"));check(!client.isEditable());
            var property=Statuses.propertyField(null);check(property.getItemCount()==3);check(property.getSelectedItem().equals("Listed"));check(!property.isEditable());
            var model=new DefaultTableModel(new Object[][]{{1L,"Oak","Listed"},{2L,"Oak","Closed"},{3L,"Pine","Offered"}},new String[]{"ID","Address","Status"});
            var table=new JTable(model);table.setAutoCreateRowSorter(true);var search=new JTextField();var count=new JLabel();var include=new JCheckBox("Include closed",true);
            ClientSearch.attachWithClosed(table,search,count,"properties","","",include,2,1);
            check(table.getRowCount()==3);search.setText("oak");check(table.getRowCount()==2);include.doClick();check(table.getRowCount()==1);check(count.getText().equals("1 of 3 properties"));
            search.setText("");check(table.getRowCount()==2);include.doClick();check(table.getRowCount()==3);include.doClick();
            model.setRowCount(0);model.addRow(new Object[]{4L,"Birch","Closed"});model.addRow(new Object[]{5L,"Birch","Listed"});check(table.getRowCount()==1);
            search.setText("birch");check(table.getRowCount()==1);table.getRowSorter().toggleSortOrder(1);check(((Number)model.getValueAt(table.convertRowIndexToModel(0),0)).longValue()==5L);
            include.doClick();check(table.getRowCount()==2);search.setText("no matches");check(table.getRowCount()==0);check(count.getText().contains("No matches"));
        });return checks;
    }
}
