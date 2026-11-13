import java.awt.event.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import javax.swing.*;
import javax.swing.table.*;

final class ClientDateTest {
    static int checks;
    static void check(boolean value){if(!value)throw new AssertionError("Client date check "+checks);checks++;}
    static int run()throws Exception {
        Path file=Files.createTempFile("RealtorDesk-client-date-",".db");
        try {
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+file);var s=db.createStatement()) {
                s.execute("CREATE TABLE clients(id INTEGER PRIMARY KEY,name TEXT NOT NULL,phone TEXT,email TEXT,status TEXT,notes TEXT,client_type TEXT,first_name TEXT,last_name TEXT,seller_address TEXT)");
                s.execute("INSERT INTO clients VALUES(7,'Jamie Smith','555','','Active','Keep notes','Seller','Jamie','Smith','123 Oak Street')");
                s.execute("PRAGMA user_version=4");ClientStore.migrate(db);ClientStore.migrate(db);
                check(ClientStore.load(db,7).lastCommunicated().isEmpty());check(ClientStore.load(db,7).sellerAddress().equals("123 Oak Street"));
                ClientStore.save(db,7L,"Jamie","Smith","555","","Active","Keep notes",ClientStore.Type.SELLER,"123 Oak Street","2024-02-29");
                ClientStore.save(db,7L,"Jamie","Smith","555","","Active","Keep notes",ClientStore.Type.SELLER,"123 Oak Street");
                check(ClientStore.load(db,7).lastCommunicated().equals("2024-02-29"));
                try {ClientStore.save(db,7L,"Jamie","Smith","555","","Active","Keep notes",ClientStore.Type.SELLER,"123 Oak Street","2025-02-29");throw new AssertionError();}
                catch(java.time.format.DateTimeParseException expected){check(ClientStore.load(db,7).lastCommunicated().equals("2024-02-29"));}
                ClientStore.save(db,null,"Alex","Brown","555","","Lead","",ClientStore.Type.BUYER,"","2026-11-13");
            }
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+file)) {
                check(ClientStore.load(db,7).lastCommunicated().equals("2024-02-29"));
                ClientStore.save(db,7L,"Jamie","Smith","555","","Active","Keep notes",ClientStore.Type.SELLER,"123 Oak Street","");
                check(ClientStore.load(db,7).lastCommunicated().isEmpty());
            }
        }finally{Files.deleteIfExists(file);}
        SwingUtilities.invokeAndWait(()->{
            var picker=new DatePicker("");check(picker.date().isEmpty());picker.setDate(LocalDate.of(2024,2,29));check(picker.date().equals("2024-02-29"));
            var calendar=new DatePicker.CalendarPanel(LocalDate.of(2024,2,29));
            check(calendar.displayed().equals(YearMonth.of(2024,2)));
            int buttons=0;JToggleButton selected=null;
            for(var component:calendar.days.getComponents())if(component instanceof JToggleButton button){buttons++;if(button.isSelected())selected=button;}
            check(buttons==29);check(selected!=null&&selected.getText().equals("29"));
            calendar.showMonth(YearMonth.of(2025,2));buttons=0;
            for(var component:calendar.days.getComponents())if(component instanceof JToggleButton)buttons++;
            check(buttons==28);
            calendar.showMonth(YearMonth.of(2026,12));calendar.navigate(1);check(calendar.displayed().equals(YearMonth.of(2027,1)));
            calendar.navigate(-1);check(calendar.displayed().equals(YearMonth.of(2026,12)));
            for(var component:calendar.days.getComponents())if(component instanceof JToggleButton button&&button.getText().equals("15")){button.doClick();break;}
            check(calendar.selected.equals(LocalDate.of(2026,12,15)));
            check(picker.date().equals("2024-02-29")); // Calendar changes are not committed before OK.
            picker.setDate(calendar.selected);check(picker.date().equals("2026-12-15"));picker.setDate(null);check(picker.date().isEmpty());
            var model=new DefaultTableModel(new Object[][]{{42L,"Zoe","2026-11-13"},{7L,"Alex","2024-02-29"}},new String[]{"ID","First name","Date Last Communicated"});
            var table=new JTable(model);table.setAutoCreateRowSorter(true);table.setSize(500,200);ClientTableActions.hideId(table);
            check(table.getColumnCount()==2);check(table.getColumnName(0).equals("First name"));check(model.getColumnCount()==3);
            table.getRowSorter().toggleSortOrder(2);var edited=new ArrayList<Long>();ClientTableActions.install(table,edited::add,()->{});
            var search=new JTextField();ClientSearch.attach(table,search,new JLabel());search.setText("Alex 2024-02-29");check(table.getRowCount()==1);
            table.moveColumn(0,1);var cell=table.getCellRect(0,0,true);
            table.dispatchEvent(new MouseEvent(table,MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,cell.x+4,cell.y+4,2,false,MouseEvent.BUTTON1));
            check(edited.equals(java.util.List.of(7L)));
        });
        return checks;
    }
}
