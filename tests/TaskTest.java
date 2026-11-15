import java.sql.*;
import java.time.*;
import javax.swing.*;

final class TaskTest {
    static int checks;static void check(boolean b){if(!b)throw new AssertionError("Task check "+checks);checks++;}
    static int run()throws Exception {
        try(var db=DriverManager.getConnection("jdbc:sqlite::memory:");var s=db.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");s.execute("CREATE TABLE clients(id INTEGER PRIMARY KEY,name TEXT)");s.execute("INSERT INTO clients VALUES(1,'Jamie Smith')");s.execute("CREATE TABLE properties(id INTEGER PRIMARY KEY,client_id INTEGER,address TEXT,mls_number TEXT)");s.execute("INSERT INTO properties VALUES(5,1,'12 Oak Street','001-MLS')");TaskStore.migrate(db);TaskStore.migrate(db);
            LocalDate today=LocalDate.now();TaskStore.save(db,new TaskStore.Task(null,"Inspection",1L,5L,today.toString(),"High","Bring checklist",false,true,null));
            TaskStore.save(db,new TaskStore.Task(null,"General task",null,null,"","Normal","",false,true,null));TaskStore.save(db,new TaskStore.Task(null,"Future",null,null,today.plusDays(2).toString(),"Low","",false,true,null));
            check(TaskStore.load(db,1).clientId()==1L);check(TaskStore.load(db,1).propertyId()==5L);check(TaskStore.load(db,1).notes().equals("Bring checklist"));check(TaskStore.load(db,2).due().isEmpty());
            var due=TaskStore.reminders(db,today);check(due.size()==1);check(due.get(0).id()==1L);TaskStore.acknowledged(db,due,today);check(TaskStore.reminders(db,today).isEmpty());check(TaskStore.reminders(db,today.plusDays(1)).size()==1);
            TaskStore.save(db,new TaskStore.Task(1L,"Updated inspection",1L,5L,today.toString(),"High","Updated",false,true,null));check(TaskStore.reminders(db,today).isEmpty());
            TaskStore.save(db,new TaskStore.Task(1L,"Updated inspection",1L,5L,today.minusDays(1).toString(),"High","Updated",false,true,null));check(TaskStore.reminders(db,today).size()==1);
            TaskStore.complete(db,1);check(TaskStore.reminders(db,today).isEmpty());check(TaskStore.load(db,1).completed());
            TaskStore.save(db,new TaskStore.Task(1L,"Inspection",1L,5L,today.toString(),"High","Updated",false,false,null));check(TaskStore.reminders(db,today).isEmpty());
            try{TaskStore.save(db,new TaskStore.Task(null," ",null,null,"","Normal","",false,true,null));throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}
            try{TaskStore.save(db,new TaskStore.Task(null,"Invalid date",null,null,"2026-02-30","Normal","",false,true,null));throw new AssertionError();}catch(java.time.format.DateTimeParseException expected){checks++;}
            SwingUtilities.invokeAndWait(()->{
                var panel=new TasksPanel(db);check(panel.model.getRowCount()==3);panel.search.setText("Jamie Oak");check(panel.table.getRowCount()==1);panel.table.setRowSelectionInterval(0,0);check(panel.selected()==1L);
                panel.search.setText("");panel.dueOnly.doClick();check(panel.table.getRowCount()==1);TaskStoreComplete(db,1);panel.refresh();check(panel.table.getRowCount()==0);
                panel.completed.doClick();check(panel.table.getRowCount()==1);panel.dueOnly.doClick();check(panel.table.getRowCount()==3);panel.timer.stop();
            });
            s.execute("DELETE FROM properties WHERE id=5");check(TaskStore.load(db,1).propertyId()==null);check(TaskStore.load(db,1).clientId()==1L);TaskStore.delete(db,2);try{TaskStore.load(db,2);throw new AssertionError();}catch(SQLException expected){checks++;}
        }return checks;
    }
    static void TaskStoreComplete(Connection db,long id){try{TaskStore.complete(db,id);}catch(SQLException e){throw new RuntimeException(e);}}
}
