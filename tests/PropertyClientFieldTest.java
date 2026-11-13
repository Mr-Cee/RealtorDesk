import java.sql.*;
import java.awt.event.*;
import javax.swing.*;

final class PropertyClientFieldTest {
    static int checks;
    static void check(boolean value){if(!value)throw new AssertionError("Property client picker check "+checks);checks++;}
    static int run()throws Exception {
        try(var db=DriverManager.getConnection("jdbc:sqlite::memory:");var s=db.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");s.execute("CREATE TABLE clients(id INTEGER PRIMARY KEY,name TEXT NOT NULL,phone TEXT,email TEXT,status TEXT,notes TEXT)");ClientStore.migrate(db);
            ClientStore.save(db,null,"Alex","Smith","555-0101","one@example.test","Active","",ClientStore.Type.BUYER);
            ClientStore.save(db,null,"Alex","Smith","555-0102","two@example.test","Active","",ClientStore.Type.SELLER);
            s.execute("CREATE TABLE properties(id INTEGER PRIMARY KEY,client_id INTEGER REFERENCES clients(id),address TEXT NOT NULL,status TEXT,notes TEXT)");PropertyStore.migrate(db);
            var address=new PropertyAddress("12","","Main","","","Juneau","AK","99801");
            PropertyStore.save(db,new PropertyStore.Property(null,2L,address.formatted(),"Closed","Keep notes",address,54000000L,"001-MLS"));
            try(var p=db.prepareStatement("INSERT INTO clients(name,phone,email) VALUES(?,?,?)")) {
                for(int i=0;i<300;i++){p.setString(1,"Client "+i);p.setString(2,i==299?"(907) 555-0299":"");p.setString(3,"user"+i+"@example.test");p.executeUpdate();}
            }
            try(var p=db.prepareStatement("UPDATE clients SET name=? WHERE id=1")){p.setString(1,"Jos\u00e9 Smith");p.executeUpdate();}
            SwingUtilities.invokeAndWait(()->{try {
                var old=PropertyStore.load(db,1);var field=new PropertyClientField(db,old.clientId());
                check(field.clientId()==2L);check(field.action.getText().equals("Remove"));check(!field.display.isEditable());check(field.display.getText().contains("555-0102"));
                field.action.doClick();check(field.clientId()==null);check(field.action.getText().equals("Add"));check(field.display.getText().isEmpty());check(PropertyStore.load(db,1).clientId()==2L);
                var picker=new ClientPicker(ClientPicker.clients(db));picker.table.setSize(700,400);var closes=new int[1];picker.close=()->closes[0]++;
                check(picker.table.getRowCount()==302);check(picker.table.getColumnCount()==3);check(!picker.add.isEnabled());
                picker.search.setText("5550101");check(picker.table.getRowCount()==1);check(picker.count.getText().equals("1 of 302 clients"));
                picker.table.setRowSelectionInterval(0,0);check(picker.add.isEnabled());picker.add.doClick();check(picker.selected.id()==1L);check(closes[0]==1);
                field.assign(picker.selected);check(field.clientId()==1L);check(field.action.getText().equals("Remove"));check(PropertyStore.load(db,1).clientId()==2L);
                // Canceling a popup never changes the field's assignment.
                picker.cancel.doClick();check(picker.selected==null);check(closes[0]==2);check(field.clientId()==1L);
                picker.search.setText("JOSE smith");check(picker.table.getRowCount()==1);
                picker.search.setText("client user299@example.test");check(picker.table.getRowCount()==1);
                picker.table.getRowSorter().toggleSortOrder(1);picker.search.setText("9075550299");check(picker.table.getRowCount()==1);
                var cell=picker.table.getCellRect(0,0,true);picker.table.dispatchEvent(new MouseEvent(picker.table,MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,cell.x+4,cell.y+4,2,false,MouseEvent.BUTTON1));
                check(picker.selected.id()==302L);check(closes[0]==3);
                field.assign(picker.selected);check(field.clientId()==302L);
                picker.search.setText(".*");check(picker.table.getRowCount()==0);check(!picker.add.isEnabled());check(picker.count.getText().contains("No matches"));
                int before=closes[0];picker.addSelected();check(closes[0]==before);
                picker.search.setText("");check(picker.table.getRowCount()==302);check(field.clientId()==302L);
                PropertyStore.save(db,new PropertyStore.Property(old.id(),field.clientId(),old.address(),old.status(),old.notes(),old.parts(),old.closedPriceCents(),old.mlsNumber()));
                check(PropertyStore.load(db,1).clientId()==302L);check(PropertyStore.load(db,1).closedPriceCents()==54000000L);check(PropertyStore.load(db,1).mlsNumber().equals("001-MLS"));
                field.action.doClick();PropertyStore.save(db,new PropertyStore.Property(old.id(),field.clientId(),old.address(),old.status(),old.notes(),old.parts(),old.closedPriceCents(),old.mlsNumber()));check(PropertyStore.load(db,1).clientId()==null);
                var fresh=new PropertyClientField(db,null);check(fresh.action.getText().equals("Add"));check(fresh.clientId()==null);
                var empty=new ClientPicker(java.util.List.of());check(empty.table.getRowCount()==0);check(!empty.add.isEnabled());check(empty.count.getText().startsWith("No clients yet"));
            }catch(Exception e){throw new RuntimeException(e);}});
            try{new PropertyClientField(db,999L);throw new AssertionError();}catch(SQLException expected){checks++;}
        }return checks;
    }
}
