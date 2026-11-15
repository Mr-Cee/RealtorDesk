import java.awt.*;
import java.sql.*;
import javax.swing.*;

final class TaskPhoneTest {
    static int checks;static void check(boolean value){if(!value)throw new AssertionError("Task phone check "+checks);checks++;}
    static int run()throws Exception {
        try(var db=DriverManager.getConnection("jdbc:sqlite::memory:");var s=db.createStatement()) {
            s.execute("CREATE TABLE clients(id INTEGER PRIMARY KEY,name TEXT,phone TEXT,email TEXT)");s.execute("INSERT INTO clients VALUES(1,'Jamie Smith','(907) 555-0100','jamie@example.test'),(2,'Alex Brown','','alex@example.test')");
            s.execute("CREATE TABLE properties(id INTEGER PRIMARY KEY,client_id INTEGER,address TEXT,mls_number TEXT)");TaskStore.migrate(db);
            SwingUtilities.invokeAndWait(()->{try {
                var client=new PropertyClientField(db,null);var form=new TaskForm();form.clientField(client);var scroll=form.scrollPane(new Rectangle(0,0,1100,780));
                check(!form.phone.isVisible());check(!form.phoneLabel.isVisible());int emptyHeight=scroll.getPreferredSize().height;
                client.assign(new ClientPicker.Client(1L,"Jamie Smith","(907) 555-0100","jamie@example.test"));
                check(form.phone.isVisible());check(form.phoneLabel.isVisible());check(form.phone.getText().equals("(907) 555-0100"));check(!form.phone.isEditable());check(client.display.getText().equals("Jamie Smith"));check(scroll.getPreferredSize().height>emptyHeight);
                client.action.doClick();check(!form.phone.isVisible());check(!form.phoneLabel.isVisible());check(form.phone.getText().isEmpty());check(scroll.getPreferredSize().height==emptyHeight);
                client.assign(new ClientPicker.Client(2L,"Alex Brown","","alex@example.test"));check(client.clientId()==2L);check(!form.phone.isVisible());check(!form.phoneLabel.isVisible());
                var existing=new TaskForm();existing.clientField(new PropertyClientField(db,1L));check(existing.phone.isVisible());check(existing.phone.getText().equals("(907) 555-0100"));
                var tasks=new TasksPanel(db);check(tasks.model.getColumnCount()==7);for(int i=0;i<tasks.model.getColumnCount();i++)check(!tasks.model.getColumnName(i).equalsIgnoreCase("Phone"));tasks.timer.stop();
            }catch(Exception e){throw new RuntimeException(e);}});
            s.execute("UPDATE clients SET phone='555-0199' WHERE id=1");
            SwingUtilities.invokeAndWait(()->{try{var form=new TaskForm();form.clientField(new PropertyClientField(db,1L));check(form.phone.getText().equals("555-0199"));}catch(SQLException e){throw new RuntimeException(e);}});
        }return checks;
    }
}
