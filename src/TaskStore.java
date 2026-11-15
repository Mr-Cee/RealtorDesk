import java.sql.*;
import java.time.*;
import java.util.*;

final class TaskStore {
    record Task(Long id,String title,Long clientId,Long propertyId,String due,String priority,String notes,boolean completed,boolean remind,String notified) { }
    static void migrate(Connection db)throws SQLException {try(var s=db.createStatement()){s.execute("CREATE TABLE IF NOT EXISTS tasks(id INTEGER PRIMARY KEY,title TEXT NOT NULL,client_id INTEGER REFERENCES clients(id) ON DELETE SET NULL,property_id INTEGER REFERENCES properties(id) ON DELETE SET NULL,due_date TEXT,priority TEXT NOT NULL DEFAULT 'Normal',notes TEXT,completed INTEGER NOT NULL DEFAULT 0,remind INTEGER NOT NULL DEFAULT 1,last_notified TEXT)");}}
    static Long nullable(ResultSet r,String column)throws SQLException {long value=r.getLong(column);return r.wasNull()?null:value;}
    static Task read(ResultSet r)throws SQLException{return new Task(r.getLong("id"),r.getString("title"),nullable(r,"client_id"),nullable(r,"property_id"),Objects.toString(r.getString("due_date"),""),r.getString("priority"),Objects.toString(r.getString("notes"),""),r.getInt("completed")!=0,r.getInt("remind")!=0,r.getString("last_notified"));}
    static Task load(Connection db,long id)throws SQLException {try(var p=db.prepareStatement("SELECT * FROM tasks WHERE id=?")){p.setLong(1,id);try(var r=p.executeQuery()){if(!r.next())throw new SQLException("Task no longer exists. Refresh the list.");return read(r);}}}
    static void save(Connection db,Task task)throws SQLException {
        if(task.title()==null||task.title().isBlank())throw new IllegalArgumentException("Task title is required.");String due=task.due()==null?"":task.due().trim();if(!due.isEmpty())due=LocalDate.parse(due).toString();
        if(!java.util.List.of("Low","Normal","High").contains(task.priority()))throw new IllegalArgumentException("Select a priority.");
        String notified=null;if(task.id()!=null){var old=load(db,task.id());if(old.due().equals(due)&&old.remind()==task.remind()&&old.completed()==task.completed())notified=old.notified();}
        String sql=task.id()==null?"INSERT INTO tasks(title,client_id,property_id,due_date,priority,notes,completed,remind,last_notified) VALUES(?,?,?,?,?,?,?,?,?)":"UPDATE tasks SET title=?,client_id=?,property_id=?,due_date=?,priority=?,notes=?,completed=?,remind=?,last_notified=? WHERE id=?";
        try(var p=db.prepareStatement(sql)){p.setString(1,task.title().trim());if(task.clientId()==null)p.setNull(2,Types.INTEGER);else p.setLong(2,task.clientId());if(task.propertyId()==null)p.setNull(3,Types.INTEGER);else p.setLong(3,task.propertyId());p.setString(4,due);p.setString(5,task.priority());p.setString(6,task.notes());p.setInt(7,task.completed()?1:0);p.setInt(8,task.remind()?1:0);p.setString(9,notified);if(task.id()!=null)p.setLong(10,task.id());if(p.executeUpdate()!=1)throw new SQLException("Task no longer exists.");}
    }
    static java.util.List<Task> reminders(Connection db,LocalDate today)throws SQLException {var result=new ArrayList<Task>();try(var p=db.prepareStatement("SELECT * FROM tasks WHERE completed=0 AND remind=1 AND due_date<>'' AND due_date<=? AND (last_notified IS NULL OR last_notified<>?) ORDER BY due_date,id")){p.setString(1,today.toString());p.setString(2,today.toString());try(var rows=p.executeQuery()){while(rows.next())result.add(read(rows));}}return result;}
    static void acknowledged(Connection db,java.util.List<Task> tasks,LocalDate today)throws SQLException {try(var p=db.prepareStatement("UPDATE tasks SET last_notified=? WHERE id=?")){for(var task:tasks){p.setString(1,today.toString());p.setLong(2,task.id());p.addBatch();}p.executeBatch();}}
    static void complete(Connection db,long id)throws SQLException {try(var p=db.prepareStatement("UPDATE tasks SET completed=1 WHERE id=?")){p.setLong(1,id);p.executeUpdate();}}
    static void delete(Connection db,long id)throws SQLException {try(var p=db.prepareStatement("DELETE FROM tasks WHERE id=?")){p.setLong(1,id);p.executeUpdate();}}
}
