import java.sql.*;
import java.time.*;
import javax.swing.*;

final class ChecklistTest {
    static int checks;static void check(boolean value){if(!value)throw new AssertionError("Checklist check "+checks);checks++;}
    static int count(Connection db,String table)throws Exception {try(var s=db.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM "+table)){r.next();return r.getInt(1);}}
    static int run()throws Exception {
        try(var db=DriverManager.getConnection("jdbc:sqlite::memory:");var s=db.createStatement()){
            s.execute("PRAGMA foreign_keys=ON");s.execute("CREATE TABLE clients(id INTEGER PRIMARY KEY,name TEXT)");s.execute("INSERT INTO clients VALUES(1,'Jamie')");s.execute("CREATE TABLE properties(id INTEGER PRIMARY KEY,client_id INTEGER,address TEXT)");s.execute("INSERT INTO properties VALUES(7,1,'12 Oak Street'),(8,NULL,'20 Pine Street')");
            TaskStore.migrate(db);check(!ChecklistStore.linked(db,1L));ChecklistStore.migrate(db);ChecklistStore.migrate(db);check(ChecklistStore.templates(db).size()==2);
            var template=ChecklistStore.templates(db).get(0);ChecklistStore.apply(db,7,template);check(count(db,"tasks")==11);check(count(db,"checklist_steps")==11);check(ChecklistStore.checklist(db,7)!=null);check(TaskStore.load(db,1).clientId()==1L);check(TaskStore.load(db,1).propertyId()==7L);check(TaskStore.reminders(db,LocalDate.now()).isEmpty());check(ChecklistStore.linked(db,1L));
            try{ChecklistStore.apply(db,7,template);throw new AssertionError();}catch(SQLException expected){checks++;}check(count(db,"tasks")==11);check(db.getAutoCommit());
            ChecklistStore.template(db,template.id(),"Custom buyer","Inspection\n\n Closing ");check(count(db,"tasks")==11);check(ChecklistStore.templates(db).get(0).steps().equals("Inspection\nClosing"));
            ChecklistStore.apply(db,8,ChecklistStore.templates(db).get(0));check(count(db,"tasks")==13);check(TaskStore.load(db,12).clientId()==null);
            var task=TaskStore.load(db,1);TaskStore.save(db,new TaskStore.Task(1L,task.title(),1L,7L,LocalDate.now().toString(),"Normal","Notes",false,true,null));check(TaskStore.reminders(db,LocalDate.now()).size()==1);
            TaskStore.complete(db,1);check(TaskStore.reminders(db,LocalDate.now()).isEmpty());
            SwingUtilities.invokeAndWait(()->{var panel=new ChecklistPanel(db,7);check(panel.model.getRowCount()==11);check(panel.progress.getText().contains("1 of 11"));check(panel.model.getValueAt(0,3).equals("Completed"));});
            TaskStore.delete(db,1);check(count(db,"checklist_steps")==12);
            try{ChecklistStore.transaction(db,()->ChecklistStore.add(db,ChecklistStore.checklist(db,7),7," ","",false,99));throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}check(count(db,"tasks")==12);
            try{ChecklistStore.apply(db,999,template);throw new AssertionError();}catch(SQLException expected){checks++;}check(count(db,"property_checklists")==2);
            s.execute("DELETE FROM checklist_templates");ChecklistStore.migrate(db);check(ChecklistStore.templates(db).isEmpty());check(count(db,"checklist_steps")==12);
            s.execute("DELETE FROM properties WHERE id=7");check(count(db,"property_checklists")==1);check(count(db,"checklist_steps")==2);check(TaskStore.load(db,2).propertyId()==null);
        }return checks;
    }
}
