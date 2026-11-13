import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

final class DocumentStore {
    record Client(long id,String name,String phone,String email,long documentCount) { }
    record Document(long id,long clientId,String name,String path,String added) { }
    static java.util.List<Client> clients(Connection db) throws SQLException {
        var clients=new ArrayList<Client>();
        try(var s=db.createStatement();var rows=s.executeQuery("SELECT c.id,c.name,c.phone,c.email,COUNT(d.id) FROM clients c LEFT JOIN documents d ON d.client_id=c.id GROUP BY c.id,c.name,c.phone,c.email ORDER BY c.name COLLATE NOCASE,c.id")) {
            while(rows.next())clients.add(new Client(rows.getLong(1),rows.getString(2),rows.getString(3),rows.getString(4),rows.getLong(5)));
        }
        return clients;
    }
    static Client client(Connection db,long id) throws SQLException {
        try(var p=db.prepareStatement("SELECT id,name,phone,email FROM clients WHERE id=?")) {
            p.setLong(1,id);try(var rows=p.executeQuery()) {
                if(!rows.next())throw new SQLException("This client no longer exists. Return to the client list and refresh.");
                return new Client(id,rows.getString(2),rows.getString(3),rows.getString(4),0);
            }
        }
    }
    static java.util.List<Document> documents(Connection db,long clientId) throws SQLException {
        var documents=new ArrayList<Document>();
        try(var p=db.prepareStatement("SELECT id,client_id,name,path,added FROM documents WHERE client_id=? ORDER BY added DESC,id DESC")) {
            p.setLong(1,clientId);try(var rows=p.executeQuery()){while(rows.next())documents.add(read(rows));}
        }
        return documents;
    }
    static Document read(ResultSet rows) throws SQLException {return new Document(rows.getLong(1),rows.getLong(2),rows.getString(3),rows.getString(4),rows.getString(5));}
    static Path file(Connection db,long clientId,long documentId) throws SQLException,IOException {
        try(var p=db.prepareStatement("SELECT id,client_id,name,path,added FROM documents WHERE client_id=? AND id=?")) {
            p.setLong(1,clientId);p.setLong(2,documentId);
            try(var rows=p.executeQuery()) {
                if(!rows.next())throw new SQLException("This document is not in the selected client's documents. Refresh the list.");
                String stored=read(rows).path();
                if(stored==null||stored.isBlank())throw new IOException("This document's file could not be found.");
                Path path=Path.of(stored);
                if(!Files.isRegularFile(path))throw new IOException("This document's file could not be found. It may have been moved or deleted.");
                return path;
            }
        }
    }
    static void add(Connection db,Path storage,long clientId,Path source) throws Exception {
        client(db,clientId);
        if(!Files.isRegularFile(source))throw new IOException("Choose a document file.");
        Files.createDirectories(storage);
        Path destination=storage.resolve(UUID.randomUUID()+"-"+source.getFileName());
        Files.copy(source,destination);
        try(var p=db.prepareStatement("INSERT INTO documents(client_id,name,path,added) VALUES(?,?,?,?)")) {
            p.setLong(1,clientId);p.setString(2,source.getFileName().toString());p.setString(3,destination.toAbsolutePath().toString());p.setString(4,LocalDate.now().toString());p.executeUpdate();
        }catch(Exception e){Files.deleteIfExists(destination);throw e;}
    }
}
