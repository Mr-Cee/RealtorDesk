import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.*;
import javax.swing.*;
import javax.swing.table.*;

public class ClientNameSearchTest {
    static int checks;
    static void check(boolean value){if(!value)throw new AssertionError();checks++;}
    public static void main(String[] args)throws Exception {
        try(var db=DriverManager.getConnection("jdbc:sqlite::memory:");var s=db.createStatement()) {
            s.execute("CREATE TABLE clients(id INTEGER PRIMARY KEY,name TEXT NOT NULL,phone TEXT,email TEXT,status TEXT,notes TEXT,client_type TEXT NOT NULL DEFAULT 'Unspecified')");
            s.execute("PRAGMA user_version=2");
            String[][] names={{"  Mary Ann van Buren  ","Mary","Ann van Buren"},{"Prince","Prince",""},{"Smith, Jane","Smith,","Jane"},{"Renée O'Connor","Renée","O'Connor"},{"Ada\u00a0Lovelace","Ada","Lovelace"}};
            try(var p=db.prepareStatement("INSERT INTO clients(id,name,phone,email,status,notes,client_type) VALUES(?,?,'555','person@example.test','Active','Keep notes','Buyer')")) {
                for(int i=0;i<names.length;i++){p.setLong(1,i+1);p.setString(2,names[i][0]);p.executeUpdate();}
            }
            ClientStore.migrate(db);
            for(int i=0;i<names.length;i++) {
                var client=ClientStore.load(db,i+1);check(client.name().equals(names[i][0]));check(client.firstName().equals(names[i][1]));check(client.lastName().equals(names[i][2]));
                check(client.type()==ClientStore.Type.BUYER);check(client.notes().equals("Keep notes"));
            }
            ClientStore.save(db,1L,"Mary Ann","van Buren","555","person@example.test","Active","Keep notes",ClientStore.Type.BOTH);
            ClientStore.migrate(db);var corrected=ClientStore.load(db,1);check(corrected.firstName().equals("Mary Ann"));check(corrected.lastName().equals("van Buren"));check(corrected.name().equals("Mary Ann van Buren"));
            ClientStore.save(db,null,"","North Star LLC","555","","Lead","Business",ClientStore.Type.SELLER);
            try(var row=s.executeQuery("SELECT id FROM clients WHERE name='North Star LLC'")){check(row.next());var business=ClientStore.load(db,row.getLong(1));check(business.firstName().isEmpty());check(business.lastName().equals("North Star LLC"));}
        }
        SwingUtilities.invokeAndWait(ClientNameSearchTest::searchChecks);
        checks+=ClientAddressTest.run();
        System.out.println("PASS: "+checks+" split-name migration and client-search checks");
    }
    static void searchChecks() {
        var model=new DefaultTableModel(new Object[][]{
            {1L,"José","van Buren","(907) 555-0100","jose@example.test","Buyer","Active","Looking in Anchorage"},
            {2L,"Mary Ann","O'Brien","907-555-0200","mary+home@example.test","Seller","Lead","Needs [inspection]"},
            {3L,"Jamie","Smith",null,"JAMIE@example.test","Buyer & Seller","Active",null},
            {4L,"","North Star LLC","555.0300",null,"Unspecified","Lead","Business"}
        },new String[]{"ID","First name","Last name","Phone","Email","Client type","Status","Notes"});
        var table=new JTable(model);table.setAutoCreateRowSorter(true);table.setSize(900,250);var search=new JTextField();var count=new JLabel();ClientSearch.attach(table,search,count);
        query(table,search,"JOSE",1);check(id(table,0)==1);query(table,search,"van buren",1);query(table,search,"buren jose",1);
        query(table,search,"buyer anchorage",1);query(table,search,"9075550100",1);query(table,search,"(907)555-0200",1);check(id(table,0)==2);
        query(table,search,"mary+home",1);query(table,search,"[inspection]",1);query(table,search,".*",0);check(count.getText().contains("No matches"));
        query(table,search,"null",0);query(table,search,"seller",2);query(table,search,"active buyer",2);query(table,search,"North Star",1);
        query(table,search,"",4);check(count.getText().equals("4 of 4 clients"));
        table.getRowSorter().toggleSortOrder(2);query(table,search,"seller",2);
        var edited=new ArrayList<Long>();ClientTableActions.install(table,edited::add,()->{});
        Rectangle row=table.getCellRect(0,0,true);table.dispatchEvent(new MouseEvent(table,MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,row.x+5,row.y+5,2,false,MouseEvent.BUTTON1));
        check(edited.equals(java.util.List.of(2L)));
        query(table,search,"buyer anchorage",1);model.addRow(new Object[]{5L,"Alex","Stone","555","new@example.test","Buyer","Active","Anchorage"});
        check(table.getRowCount()==2);check(count.getText().equals("2 of 5 clients"));
        model.removeRow(0);check(table.getRowCount()==1);check(id(table,0)==5);check(count.getText().equals("1 of 4 clients"));
        model.setRowCount(0);check(table.getRowCount()==0);check(count.getText().equals("0 of 0 clients"));
        model.addRow(new Object[]{6L,"Morgan","Green","555","","Seller","Lead","Anchorage"});check(table.getRowCount()==0);
        query(table,search,"",1);check(id(table,0)==6);
    }
    static void query(JTable table,JTextField search,String text,int rows){search.setText(text);check(table.getRowCount()==rows);}
    static long id(JTable table,int viewRow){return ((Number)table.getModel().getValueAt(table.convertRowIndexToModel(viewRow),0)).longValue();}
}
