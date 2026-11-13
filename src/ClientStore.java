import java.sql.*;

final class ClientStore {
    enum Type {
        UNSPECIFIED("Unspecified"),BUYER("Buyer"),SELLER("Seller"),BOTH("Buyer & Seller");
        final String label;Type(String label){this.label=label;}public String toString(){return label;}
        static Type from(String value){for(var type:values())if(type.label.equals(value))return type;return UNSPECIFIED;}
    }
    record Client(long id,String name,String phone,String email,String status,String notes,Type type) { }
    static void migrate(Connection db) throws SQLException {
        boolean auto=db.getAutoCommit();db.setAutoCommit(false);
        try(var s=db.createStatement()) {
            boolean exists=false;
            try(var columns=s.executeQuery("PRAGMA table_info(clients)")){while(columns.next())if("client_type".equals(columns.getString("name")))exists=true;}
            if(!exists)s.execute("ALTER TABLE clients ADD COLUMN client_type TEXT NOT NULL DEFAULT 'Unspecified' CHECK(client_type IN ('Unspecified','Buyer','Seller','Buyer & Seller'))");
            int version;
            try(var row=s.executeQuery("PRAGMA user_version")){row.next();version=row.getInt(1);}
            if(version<2)s.execute("PRAGMA user_version=2");
            db.commit();
        }catch(SQLException e){db.rollback();throw e;}finally{db.setAutoCommit(auto);}
    }
    static Client load(Connection db,long id) throws SQLException {
        try(var p=db.prepareStatement("SELECT id,name,phone,email,status,notes,client_type FROM clients WHERE id=?")) {
            p.setLong(1,id);
            try(var row=p.executeQuery()) {
                if(!row.next())throw new SQLException("This client no longer exists. Refresh the client list.");
                return new Client(row.getLong(1),row.getString(2),row.getString(3),row.getString(4),row.getString(5),row.getString(6),Type.from(row.getString(7)));
            }
        }
    }
    static void save(Connection db,Long id,String name,String phone,String email,String status,String notes,Type type) throws SQLException {
        if(name==null||name.isBlank())throw new IllegalArgumentException("Client name is required.");
        if(type==null)throw new IllegalArgumentException("Select a client type.");
        String sql=id==null?"INSERT INTO clients(name,phone,email,status,notes,client_type) VALUES(?,?,?,?,?,?)":"UPDATE clients SET name=?,phone=?,email=?,status=?,notes=?,client_type=? WHERE id=?";
        try(var p=db.prepareStatement(sql)) {
            p.setString(1,name.trim());p.setString(2,phone);p.setString(3,email);p.setString(4,status);p.setString(5,notes);p.setString(6,type.label);
            if(id!=null)p.setLong(7,id);
            if(p.executeUpdate()!=1)throw new SQLException("This client no longer exists. Refresh the client list.");
        }
    }
}
