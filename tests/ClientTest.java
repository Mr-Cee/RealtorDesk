import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import javax.swing.*;
import javax.swing.table.*;

public class ClientTest {
    static int checks;
    static void check(boolean condition){if(!condition)throw new AssertionError();checks++;}
    public static void main(String[] args) throws Exception {
        Path folder=Files.createTempDirectory("RealtorDesk-client-tests-");Path file=folder.resolve("clients.db");
        try {
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+file);var s=db.createStatement()) {
                s.execute("PRAGMA foreign_keys=ON");
                s.execute("CREATE TABLE clients(id INTEGER PRIMARY KEY,name TEXT NOT NULL,phone TEXT,email TEXT,status TEXT,notes TEXT)");
                for(String table:new String[]{"properties","calls","documents"})s.execute("CREATE TABLE "+table+"(id INTEGER PRIMARY KEY,client_id INTEGER REFERENCES clients(id),notes TEXT)");
                s.execute("INSERT INTO clients VALUES(7,'Existing client','555-0100','existing@example.test','Lead','Keep these notes')");
                for(String table:new String[]{"properties","calls","documents"})s.execute("INSERT INTO "+table+" VALUES(1,7,'Keep linked history')");
                s.execute("PRAGMA user_version=1");
                ClientStore.migrate(db);
                var old=ClientStore.load(db,7);
                check(old.type()==ClientStore.Type.UNSPECIFIED);check(old.id()==7);check(old.name().equals("Existing client"));check(old.notes().equals("Keep these notes"));
                check(old.phone().equals("555-0100"));check(old.status().equals("Lead"));
                check(old.firstName().equals("Existing"));check(old.lastName().equals("client"));
                try(var r=s.executeQuery("PRAGMA user_version")){check(r.next()&&r.getInt(1)==5);}
                ClientStore.migrate(db);
                int columns=0;try(var r=s.executeQuery("PRAGMA table_info(clients)")){while(r.next())columns++;}check(columns==11);
                for(var type:ClientStore.Type.values()) {
                    ClientStore.save(db,7L,"Existing","client","555-0100","existing@example.test","Active","Keep these notes",type);
                    check(ClientStore.load(db,7).type()==type);
                }
                for(String table:new String[]{"properties","calls","documents"})try(var r=s.executeQuery("SELECT client_id,notes FROM "+table)){check(r.next()&&r.getLong(1)==7&&r.getString(2).equals("Keep linked history"));}
                ClientStore.save(db,null,"Jamie","O'Brien","555-0101","jamie@example.test","Lead","A new buyer",ClientStore.Type.BUYER);
                try(var r=s.executeQuery("SELECT COUNT(*) FROM clients")){check(r.next()&&r.getInt(1)==2);}
                boolean rejected=false;try{ClientStore.save(db,7L," ","","","","","",ClientStore.Type.SELLER);}catch(IllegalArgumentException e){rejected=true;}check(rejected);
            }
            // Verify the role after closing and reopening the database, as the installed app does.
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+file);var s=db.createStatement()) {
                check(ClientStore.load(db,7).type()==ClientStore.Type.BOTH);check(ClientStore.load(db,7).firstName().equals("Existing"));check(ClientStore.load(db,7).lastName().equals("client"));
                try(var r=s.executeQuery("SELECT id FROM clients WHERE name='Jamie O''Brien'")){check(r.next());check(ClientStore.load(db,r.getLong(1)).type()==ClientStore.Type.BUYER);}
            }
        }finally{Files.deleteIfExists(file);Files.delete(folder);}
        SwingUtilities.invokeAndWait(ClientTest::tableChecks);
        System.out.println("PASS: "+checks+" client migration, persistence and double-click checks");
    }
    static void tableChecks() {
        var model=new DefaultTableModel(new Object[][]{{7L,"Zoe"},{42L,"Alex"},{19L,"Morgan"}},new String[]{"ID","Name"});
        var table=new JTable(model);table.setAutoCreateRowSorter(true);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);table.setSize(400,200);table.setRowHeight(25);
        var edited=new ArrayList<Long>();var refreshes=new int[1];Runnable button=ClientTableActions.install(table,edited::add,()->refreshes[0]++);
        table.getRowSorter().toggleSortOrder(1); // Alex is first visually, but not in the underlying model.
        click(table,0,MouseEvent.BUTTON1,2);check(edited.equals(java.util.List.of(42L)));check(refreshes[0]==1);
        table.moveColumn(0,1);click(table,2,MouseEvent.BUTTON1,2);check(edited.get(1)==7L);
        int before=edited.size();click(table,1,MouseEvent.BUTTON1,1);click(table,1,MouseEvent.BUTTON3,2);click(table,1,MouseEvent.BUTTON1,3);
        table.dispatchEvent(new MouseEvent(table,MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,5,180,2,false,MouseEvent.BUTTON1));check(edited.size()==before);
        @SuppressWarnings("unchecked") var sorter=(TableRowSorter<DefaultTableModel>)table.getRowSorter();
        sorter.setRowFilter(RowFilter.regexFilter("Morgan",1));click(table,0,MouseEvent.BUTTON1,2);check(edited.get(edited.size()-1)==19L);
        table.clearSelection();before=edited.size();button.run();check(edited.size()==before);
        table.setRowSelectionInterval(0,0);button.run();check(edited.get(edited.size()-1)==19L);check(refreshes[0]==edited.size());
    }
    static void click(JTable table,int row,int button,int count) {
        Rectangle cell=table.getCellRect(row,0,true);
        table.dispatchEvent(new MouseEvent(table,MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,cell.x+5,cell.y+5,count,false,button));
    }
}
