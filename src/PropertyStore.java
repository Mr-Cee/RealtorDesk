import java.sql.*;
import java.util.*;

final class PropertyStore {
    static final String COLUMNS="id,client_id,address,status,notes,street_number,direction,street_name,street_type,unit,city,state,zip,closed_price_cents,mls_number";
    record Property(Long id,Long clientId,String address,String status,String notes,PropertyAddress parts,Long closedPriceCents,String mlsNumber) {
        Property {mlsNumber=mlsNumber==null?"":mlsNumber.trim();}
        Property(Long id,Long clientId,String address,String status,String notes,PropertyAddress parts,Long closedPriceCents){this(id,clientId,address,status,notes,parts,closedPriceCents,"");}
        Property(Long id,Long clientId,String address,String status,String notes,PropertyAddress parts){this(id,clientId,address,status,notes,parts,null,"");}
        Property(Long id,Long clientId,String address,String status,String notes){this(id,clientId,address,status,notes,null,null,"");}
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
            var columns=new HashSet<String>();try(var r=s.executeQuery("PRAGMA table_info(properties)")){while(r.next())columns.add(r.getString("name"));}
            for(String column:new String[]{"street_number","direction","street_name","street_type","unit","city","state","zip"})if(!columns.contains(column))s.execute("ALTER TABLE properties ADD COLUMN "+column+" TEXT NOT NULL DEFAULT ''");
            if(version<8){try(var r=s.executeQuery("SELECT id,address FROM properties");var p=db.prepareStatement("UPDATE properties SET street_number=?,direction=?,street_name=?,street_type=?,unit=?,city=?,state=?,zip=? WHERE id=?")){while(r.next()){bindAddress(p,1,PropertyAddress.legacy(r.getString(2)));p.setLong(9,r.getLong(1));p.executeUpdate();}}s.execute("PRAGMA user_version=8");}
            if(!columns.contains("closed_price_cents"))s.execute("ALTER TABLE properties ADD COLUMN closed_price_cents INTEGER CHECK(closed_price_cents IS NULL OR closed_price_cents>=0)");
            if(version<9)s.execute("PRAGMA user_version=9");
            if(!columns.contains("mls_number"))s.execute("ALTER TABLE properties ADD COLUMN mls_number TEXT NOT NULL DEFAULT ''");
            if(version<10)s.execute("PRAGMA user_version=10");
            db.commit();
        }catch(SQLException e){db.rollback();throw e;}finally{db.setAutoCommit(auto);}
    }
    static java.util.List<Property> list(Connection db,Long clientId)throws SQLException {
        var list=new ArrayList<Property>();
        try(var p=db.prepareStatement("SELECT "+COLUMNS+" FROM properties WHERE "+(clientId==null?"client_id IS NULL":"client_id=?")+" ORDER BY address COLLATE NOCASE,id")) {
            if(clientId!=null)p.setLong(1,clientId);try(var r=p.executeQuery()){while(r.next())list.add(read(r));}
        }return list;
    }
    static Property read(ResultSet r)throws SQLException {long client=r.getLong(2);Long owner=r.wasNull()?null:client;return new Property(r.getLong(1),owner,r.getString(3),r.getString(4),r.getString(5),new PropertyAddress(r.getString(6),r.getString(7),r.getString(8),r.getString(9),r.getString(10),r.getString(11),r.getString(12),r.getString(13)),r.getObject(14)==null?null:r.getLong(14),r.getString(15));}
    static Property load(Connection db,long id)throws SQLException {
        try(var p=db.prepareStatement("SELECT "+COLUMNS+" FROM properties WHERE id=?")){p.setLong(1,id);try(var r=p.executeQuery()){if(!r.next())throw new SQLException("Property no longer exists. Refresh the list.");return read(r);}}
    }
    static void save(Connection db,Property property)throws SQLException {
        if(property.address()==null||property.address().isBlank())throw new IllegalArgumentException("Property address is required.");
        if(property.closedPriceCents()!=null&&property.closedPriceCents()<0)throw new IllegalArgumentException("Price cannot be negative.");
        PropertyAddress parts=property.parts()==null?PropertyAddress.legacy(property.address()):property.parts();
        String address=property.parts()==null?property.address().trim():parts.complete()?parts.formatted():property.address();
        String sql=property.id()==null?"INSERT INTO properties(client_id,address,status,notes,street_number,direction,street_name,street_type,unit,city,state,zip,closed_price_cents,mls_number) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)":"UPDATE properties SET client_id=?,address=?,status=?,notes=?,street_number=?,direction=?,street_name=?,street_type=?,unit=?,city=?,state=?,zip=?,closed_price_cents=?,mls_number=? WHERE id=?";
        try(var p=db.prepareStatement(sql)){if(property.clientId()==null)p.setNull(1,Types.INTEGER);else p.setLong(1,property.clientId());p.setString(2,address);p.setString(3,property.status());p.setString(4,property.notes());bindAddress(p,5,parts);if(property.closedPriceCents()==null)p.setNull(13,Types.BIGINT);else p.setLong(13,property.closedPriceCents());p.setString(14,property.mlsNumber());if(property.id()!=null)p.setLong(15,property.id());if(p.executeUpdate()!=1)throw new SQLException("Property no longer exists. Refresh the list.");}
    }
    static void bindAddress(PreparedStatement p,int offset,PropertyAddress a)throws SQLException {String[] fields={a.number(),a.direction(),a.street(),a.type(),a.unit(),a.city(),a.state(),a.zip()};for(int i=0;i<fields.length;i++)p.setString(offset+i,fields[i]);}
    static void delete(Connection db,long id,Long expectedClient)throws SQLException {
        try(var p=db.prepareStatement("DELETE FROM properties WHERE id=? AND client_id IS ?")) {
            p.setLong(1,id);if(expectedClient==null)p.setNull(2,Types.INTEGER);else p.setLong(2,expectedClient);
            if(p.executeUpdate()!=1)throw new SQLException("This property changed or no longer exists. Refresh and review it before deleting.");
        }
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
                save(db,new Property(item.id(),owner,item.address(),item.status(),item.notes(),item.parts(),item.closedPriceCents(),item.mlsNumber()));
            }
            for(var current:original)if(!retained.contains(current.id()))save(db,new Property(current.id(),null,current.address(),current.status(),current.notes(),current.parts(),current.closedPriceCents(),current.mlsNumber()));
            db.commit();
        }catch(Exception e){db.rollback();throw e;}finally{db.setAutoCommit(true);}
    }
}
