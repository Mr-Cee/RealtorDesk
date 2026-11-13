import java.sql.*;
import java.util.*;

final class PropertyStore {
    record Property(Long id,Long clientId,String address,String status,String notes) {
        public String toString(){return address+(status==null||status.isBlank()?"":" - "+status);}
    }
    static void migrate(Connection db)throws SQLException {
        boolean auto=db.getAutoCommit();db.setAutoCommit(false);
        try(var s=db.createStatement()) {
            int version;try(var r=s.executeQuery("PRAGMA user_version")){r.next();version=r.getInt(1);}
            if(version<7) {
                s.executeUpdate("INSERT INTO properties(client_id,address,status,notes) SELECT c.id,TRIM(c.seller_address),'','' FROM clients c WHERE TRIM(COALESCE(c.seller_address,''))<>'' AND NOT EXISTS(SELECT 1 FROM properties p WHERE p.client_id=c.id AND LOWER(TRIM(p.address))=LOWER(TRIM(c.seller_address)))");
                s.execute("PRAGMA user_version=7");
            }
            db.commit();
        }catch(SQLException e){db.rollback();throw e;}finally{db.setAutoCommit(auto);}
    }
    static java.util.List<Property> list(Connection db,Long clientId)throws SQLException {
        var list=new ArrayList<Property>();
        try(var p=db.prepareStatement("SELECT id,client_id,address,status,notes FROM properties WHERE "+(clientId==null?"client_id IS NULL":"client_id=?")+" ORDER BY address COLLATE NOCASE,id")) {
            if(clientId!=null)p.setLong(1,clientId);try(var r=p.executeQuery()){while(r.next())list.add(read(r));}
        }return list;
    }
    static Property read(ResultSet r)throws SQLException {long client=r.getLong(2);Long owner=r.wasNull()?null:client;return new Property(r.getLong(1),owner,r.getString(3),r.getString(4),r.getString(5));}
    static Property load(Connection db,long id)throws SQLException {
        try(var p=db.prepareStatement("SELECT id,client_id,address,status,notes FROM properties WHERE id=?")){p.setLong(1,id);try(var r=p.executeQuery()){if(!r.next())throw new SQLException("Property no longer exists. Refresh the list.");return read(r);}}
    }
    static void save(Connection db,Property property)throws SQLException {
        if(property.address()==null||property.address().isBlank())throw new IllegalArgumentException("Property address is required.");
        String sql=property.id()==null?"INSERT INTO properties(client_id,address,status,notes) VALUES(?,?,?,?)":"UPDATE properties SET client_id=?,address=?,status=?,notes=? WHERE id=?";
        try(var p=db.prepareStatement(sql)){if(property.clientId()==null)p.setNull(1,Types.INTEGER);else p.setLong(1,property.clientId());p.setString(2,property.address().trim());p.setString(3,property.status());p.setString(4,property.notes());if(property.id()!=null)p.setLong(5,property.id());if(p.executeUpdate()!=1)throw new SQLException("Property no longer exists. Refresh the list.");}
    }
    interface ClientSave{void run()throws Exception;}
    static void saveClient(Connection db,Long clientId,ClientSave saveClient,java.util.List<Property> desired)throws Exception {
        if(!db.getAutoCommit())throw new SQLException("A save is already in progress.");
        db.setAutoCommit(false);
        try {
            var original=clientId==null?java.util.List.<Property>of():list(db,clientId);
            saveClient.run();long owner;
            if(clientId!=null)owner=clientId;else try(var s=db.createStatement();var r=s.executeQuery("SELECT last_insert_rowid()")){r.next();owner=r.getLong(1);}
            var retained=new HashSet<Long>();
            for(var item:desired) {
                if(item.id()!=null) {
                    if(!retained.add(item.id()))throw new SQLException("Property was attached twice.");
                    var current=load(db,item.id());
                    if(current.clientId()!=null&&!current.clientId().equals(owner))throw new SQLException("This property is now linked to another client. Refresh and try again.");
                }
                save(db,new Property(item.id(),owner,item.address(),item.status(),item.notes()));
            }
            for(var current:original)if(!retained.contains(current.id()))save(db,new Property(current.id(),null,current.address(),current.status(),current.notes()));
            db.commit();
        }catch(Exception e){db.rollback();throw e;}finally{db.setAutoCommit(true);}
    }
}
