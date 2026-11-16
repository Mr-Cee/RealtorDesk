import java.sql.*;
import java.util.*;

/** Checklist steps are ordinary tasks, so completion, dates and reminders have one source. */
final class ChecklistStore {
    record Template(long id,String name,String steps) { public String toString(){return name;} }
    static void migrate(Connection db)throws SQLException {
        try(var s=db.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS checklist_templates(id INTEGER PRIMARY KEY,name TEXT NOT NULL UNIQUE,steps TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS property_checklists(id INTEGER PRIMARY KEY,property_id INTEGER NOT NULL UNIQUE REFERENCES properties(id) ON DELETE CASCADE,name TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS checklist_steps(checklist_id INTEGER NOT NULL REFERENCES property_checklists(id) ON DELETE CASCADE,task_id INTEGER NOT NULL UNIQUE REFERENCES tasks(id) ON DELETE CASCADE,position INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS checklist_setup(id INTEGER PRIMARY KEY)");
            try(var r=s.executeQuery("SELECT COUNT(*) FROM checklist_setup")){if(r.next()&&r.getInt(1)>0)return;}
        }
        transaction(db,()->{
            template(db,null,"Buyer","Confirm buyer needs and financing\nSchedule showings\nPrepare and submit offer\nConfirm earnest money\nSchedule inspection\nReview inspection results\nConfirm appraisal\nConfirm financing approval\nSchedule final walkthrough\nConfirm closing paperwork\nComplete closing and key handoff");
            template(db,null,"Seller","Confirm seller goals\nPrepare listing paperwork\nPrepare property and photos\nPublish listing\nCoordinate showings\nReview offers\nConfirm accepted offer and earnest money\nCoordinate inspection\nConfirm appraisal\nConfirm closing paperwork\nComplete closing and key handoff");
            try(var s=db.createStatement()){s.execute("INSERT INTO checklist_setup VALUES(1)");}
        });
    }
    static java.util.List<Template> templates(Connection db)throws SQLException {
        var list=new ArrayList<Template>();try(var s=db.createStatement();var r=s.executeQuery("SELECT * FROM checklist_templates ORDER BY name COLLATE NOCASE")){while(r.next())list.add(new Template(r.getLong("id"),r.getString("name"),r.getString("steps")));}return list;
    }
    static java.util.List<String> lines(String text){return text.lines().map(String::trim).filter(x->!x.isEmpty()).toList();}
    static void template(Connection db,Long id,String name,String steps)throws SQLException {
        if(name.isBlank()||lines(steps).isEmpty())throw new IllegalArgumentException("Enter a template name and at least one step.");
        try(var p=db.prepareStatement(id==null?"INSERT INTO checklist_templates(name,steps) VALUES(?,?)":"UPDATE checklist_templates SET name=?,steps=? WHERE id=?")){p.setString(1,name.trim());p.setString(2,String.join("\n",lines(steps)));if(id!=null)p.setLong(3,id);p.executeUpdate();}
    }
    static Long checklist(Connection db,long property)throws SQLException {try(var p=db.prepareStatement("SELECT id FROM property_checklists WHERE property_id=?")){p.setLong(1,property);try(var r=p.executeQuery()){return r.next()?r.getLong(1):null;}}}
    static boolean linked(Connection db,Long task)throws SQLException {
        if(task==null)return false;
        try(var s=db.createStatement();var r=s.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='checklist_steps'")){if(!r.next())return false;}
        try(var p=db.prepareStatement("SELECT 1 FROM checklist_steps WHERE task_id=?")){p.setLong(1,task);try(var r=p.executeQuery()){return r.next();}}
    }
    static long lastId(Connection db)throws SQLException {try(var s=db.createStatement();var r=s.executeQuery("SELECT last_insert_rowid()")){r.next();return r.getLong(1);}}
    static void apply(Connection db,long property,Template template)throws Exception {
        transaction(db,()->{
            try(var p=db.prepareStatement("INSERT INTO property_checklists(property_id,name) VALUES(?,?)")){p.setLong(1,property);p.setString(2,template.name());p.executeUpdate();}
            long checklist=lastId(db);int position=0;
            for(String title:lines(template.steps()))add(db,checklist,property,title,"",false,position++);
        });
    }
    static long add(Connection db,long checklist,long property,String title,String due,boolean complete,int position)throws SQLException {
        Long client;try(var p=db.prepareStatement("SELECT client_id FROM properties WHERE id=?")){p.setLong(1,property);try(var r=p.executeQuery()){if(!r.next())throw new SQLException("Property no longer exists.");client=TaskStore.nullable(r,"client_id");}}
        TaskStore.save(db,new TaskStore.Task(null,title,client,property,due,"Normal","",complete,true,null));long task=lastId(db);
        try(var p=db.prepareStatement("INSERT INTO checklist_steps VALUES(?,?,?)")){p.setLong(1,checklist);p.setLong(2,task);p.setInt(3,position);p.executeUpdate();}return task;
    }
    interface Work {void run()throws Exception;}
    static void transaction(Connection db,Work work)throws SQLException {
        if(!db.getAutoCommit())throw new SQLException("A save is already in progress.");db.setAutoCommit(false);
        try{work.run();db.commit();}catch(Exception e){db.rollback();if(e instanceof SQLException sql)throw sql;if(e instanceof RuntimeException runtime)throw runtime;throw new SQLException(e);}finally{db.setAutoCommit(true);}
    }
}
