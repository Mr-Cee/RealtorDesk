import java.sql.*;
import java.util.*;
import javax.swing.*;

final class Statuses {
    static final String[] CLIENT={"Lead","Looking for Home","Selling Home","Offered","Closed"};
    static final String[] PROPERTY={"Listed","Offered","Closed"};
    static String key(String value){return value==null?"":value.trim().toLowerCase(Locale.ROOT);}
    static String client(String value,ClientStore.Type type) {
        for(String allowed:CLIENT)if(key(allowed).equals(key(value)))return allowed;
        return switch(key(value)){case "sold","closed"->"Closed";case "offer","under offer","under contract","pending"->"Offered";case "active"->type==ClientStore.Type.BUYER?"Looking for Home":type==ClientStore.Type.SELLER?"Selling Home":"Lead";case "looking","buyer"->"Looking for Home";case "selling","listed","seller"->"Selling Home";default->"Lead";};
    }
    static String property(String value){for(String allowed:PROPERTY)if(key(allowed).equals(key(value)))return allowed;return switch(key(value)){case "sold"->"Closed";case "offer","under offer","under contract","pending"->"Offered";default->"Listed";};}
    static JComboBox<String> clientField(String value,ClientStore.Type type){var field=new JComboBox<>(CLIENT);field.setSelectedItem(client(value,type));return field;}
    static JComboBox<String> propertyField(String value){var field=new JComboBox<>(PROPERTY);field.setSelectedItem(property(value));return field;}
    static void migrate(Connection db)throws SQLException {
        boolean auto=db.getAutoCommit();db.setAutoCommit(false);
        try(var s=db.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS legacy_statuses(record_type TEXT,record_id INTEGER,status TEXT,PRIMARY KEY(record_type,record_id))");
            for(String table:new String[]{"clients","properties"})try(var rows=s.executeQuery("SELECT id,status"+(table.equals("clients")?",client_type":"")+" FROM "+table);var update=db.prepareStatement("UPDATE "+table+" SET status=? WHERE id=?");var archive=db.prepareStatement("INSERT OR IGNORE INTO legacy_statuses VALUES(?,?,?)")) {
                while(rows.next()) {
                    String before=rows.getString(2),after=table.equals("clients")?client(before,ClientStore.Type.from(rows.getString(3))):property(before);
                    if(!Objects.equals(before,after)){archive.setString(1,table);archive.setLong(2,rows.getLong(1));archive.setString(3,before);archive.executeUpdate();update.setString(1,after);update.setLong(2,rows.getLong(1));update.executeUpdate();}
                }
            }
            db.commit();
        }catch(SQLException e){db.rollback();throw e;}finally{db.setAutoCommit(auto);}
    }
}
