import java.sql.*;

final class ClientStore {
    enum Type {
        UNSPECIFIED("Unspecified"),BUYER("Buyer"),SELLER("Seller"),BOTH("Buyer & Seller");
        final String label;Type(String label){this.label=label;}public String toString(){return label;}
        boolean isSeller(){return this==SELLER||this==BOTH;}
        static Type from(String value){for(var type:values())if(type.label.equals(value))return type;return UNSPECIFIED;}
    }
    record Client(long id,String name,String firstName,String lastName,String phone,String email,String status,String notes,Type type,String sellerAddress) { }
    static String cleanName(String value){return value==null?"":value.replaceAll("(?U)^\\s+|\\s+$","");}
    static String[] splitLegacyName(String value) {
        String[] parts=cleanName(value).split("(?U)\\s+",2);
        return new String[]{parts[0],parts.length>1?parts[1]:""};
    }
    static String fullName(String first,String last){return (cleanName(first)+" "+cleanName(last)).trim();}
    static void migrate(Connection db) throws SQLException {
        boolean auto=db.getAutoCommit();db.setAutoCommit(false);
        try(var s=db.createStatement()) {
            var columns=new java.util.HashSet<String>();
            try(var rows=s.executeQuery("PRAGMA table_info(clients)")){while(rows.next())columns.add(rows.getString("name").toLowerCase(java.util.Locale.ROOT));}
            if(!columns.contains("client_type"))s.execute("ALTER TABLE clients ADD COLUMN client_type TEXT NOT NULL DEFAULT 'Unspecified' CHECK(client_type IN ('Unspecified','Buyer','Seller','Buyer & Seller'))");
            boolean firstAdded=!columns.contains("first_name"),lastAdded=!columns.contains("last_name");
            if(firstAdded)s.execute("ALTER TABLE clients ADD COLUMN first_name TEXT NOT NULL DEFAULT ''");
            if(lastAdded)s.execute("ALTER TABLE clients ADD COLUMN last_name TEXT NOT NULL DEFAULT ''");
            if(firstAdded||lastAdded) {
                try(var rows=s.executeQuery("SELECT id,name,first_name,last_name FROM clients");var update=db.prepareStatement("UPDATE clients SET first_name=?,last_name=? WHERE id=?")) {
                    while(rows.next()) {
                        String[] parts=splitLegacyName(rows.getString("name"));
                        update.setString(1,firstAdded?parts[0]:rows.getString("first_name"));update.setString(2,lastAdded?parts[1]:rows.getString("last_name"));update.setLong(3,rows.getLong("id"));update.executeUpdate();
                    }
                }
            }
            if(!columns.contains("seller_address"))s.execute("ALTER TABLE clients ADD COLUMN seller_address TEXT NOT NULL DEFAULT ''");
            int version;
            try(var row=s.executeQuery("PRAGMA user_version")){row.next();version=row.getInt(1);}
            if(version<4)s.execute("PRAGMA user_version=4");
            db.commit();
        }catch(SQLException e){db.rollback();throw e;}finally{db.setAutoCommit(auto);}
    }
    static Client load(Connection db,long id) throws SQLException {
        try(var p=db.prepareStatement("SELECT id,name,first_name,last_name,phone,email,status,notes,client_type,seller_address FROM clients WHERE id=?")) {
            p.setLong(1,id);
            try(var row=p.executeQuery()) {
                if(!row.next())throw new SQLException("This client no longer exists. Refresh the client list.");
                return new Client(row.getLong(1),row.getString(2),row.getString(3),row.getString(4),row.getString(5),row.getString(6),row.getString(7),row.getString(8),Type.from(row.getString(9)),row.getString(10));
            }
        }
    }
    static void save(Connection db,Long id,String firstName,String lastName,String phone,String email,String status,String notes,Type type) throws SQLException {
        save(db,id,firstName,lastName,phone,email,status,notes,type,id==null?"":load(db,id).sellerAddress());
    }
    static void save(Connection db,Long id,String firstName,String lastName,String phone,String email,String status,String notes,Type type,String sellerAddress) throws SQLException {
        String first=cleanName(firstName),last=cleanName(lastName),name=fullName(first,last);
        if(name.isBlank())throw new IllegalArgumentException("Enter a first or last name.");
        if(type==null)throw new IllegalArgumentException("Select a client type.");
        String sql=id==null?"INSERT INTO clients(name,phone,email,status,notes,client_type,first_name,last_name,seller_address) VALUES(?,?,?,?,?,?,?,?,?)":"UPDATE clients SET name=?,phone=?,email=?,status=?,notes=?,client_type=?,first_name=?,last_name=?,seller_address=? WHERE id=?";
        try(var p=db.prepareStatement(sql)) {
            p.setString(1,name);p.setString(2,phone);p.setString(3,email);p.setString(4,status);p.setString(5,notes);p.setString(6,type.label);p.setString(7,first);p.setString(8,last);
            p.setString(9,sellerAddress==null?"":sellerAddress.trim());
            if(id!=null)p.setLong(10,id);
            if(p.executeUpdate()!=1)throw new SQLException("This client no longer exists. Refresh the client list.");
        }
    }
}
