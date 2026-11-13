import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import javax.swing.*;

public class DocumentTest {
    static int checks;
    static void check(boolean value){if(!value)throw new AssertionError();checks++;}
    interface Work{void run()throws Exception;}
    static void rejects(Work work)throws Exception{try{work.run();}catch(SQLException|java.io.IOException e){checks++;return;}throw new AssertionError("Expected rejection");}
    static long files(Path storage)throws Exception{try(var stream=Files.list(storage)){return stream.count();}}
    public static void main(String[] args)throws Exception {
        Path root=Files.createTempDirectory("RealtorDesk-documents-test-");
        Path storage=Files.createDirectory(root.resolve("documents"));
        try(var db=DriverManager.getConnection("jdbc:sqlite::memory:");var s=db.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("CREATE TABLE clients(id INTEGER PRIMARY KEY,name TEXT,phone TEXT,email TEXT)");
            s.execute("CREATE TABLE documents(id INTEGER PRIMARY KEY,client_id INTEGER REFERENCES clients(id),name TEXT,path TEXT,added TEXT)");
            s.execute("INSERT INTO clients VALUES(31,'Alex Client','555-0101','a@example.test'),(52,'Alex Client','555-0102','b@example.test'),(73,'New Client','555-0103','new@example.test')");
            Path legacy=storage.resolve("existing-file.txt");Files.writeString(legacy,"Existing attachment");
            try(var p=db.prepareStatement("INSERT INTO documents VALUES(5,31,'Existing attachment',?,'2020-01-01')")){p.setString(1,legacy.toString());p.executeUpdate();}
            Path a=Files.createDirectories(root.resolve("a")).resolve("contract.txt"),b=Files.createDirectories(root.resolve("b")).resolve("contract.txt");
            Files.writeString(a,"Client A's contract");Files.writeString(b,"Client B's contract");
            DocumentStore.add(db,storage,31,a);DocumentStore.add(db,storage,52,b);
            var clients=DocumentStore.clients(db);check(clients.size()==3);check(clients.stream().filter(c->c.id()==31).findFirst().orElseThrow().documentCount()==2);
            check(clients.stream().filter(c->c.id()==52).findFirst().orElseThrow().documentCount()==1);check(clients.stream().filter(c->c.id()==73).findFirst().orElseThrow().documentCount()==0);
            var first=DocumentStore.documents(db,31);var second=DocumentStore.documents(db,52);check(first.size()==2);check(second.size()==1);check(DocumentStore.documents(db,73).isEmpty());
            check(first.stream().allMatch(d->d.clientId()==31));check(second.stream().allMatch(d->d.clientId()==52));
            var firstNew=first.stream().filter(d->d.name().equals("contract.txt")).findFirst().orElseThrow();var secondNew=second.get(0);
            check(!firstNew.path().equals(secondNew.path()));check(Files.readString(DocumentStore.file(db,31,firstNew.id())).equals("Client A's contract"));
            check(Files.readString(DocumentStore.file(db,52,secondNew.id())).equals("Client B's contract"));check(DocumentStore.file(db,31,5).equals(legacy));
            rejects(()->DocumentStore.file(db,31,secondNew.id()));
            long before=files(storage);rejects(()->DocumentStore.add(db,storage,999,a));check(files(storage)==before);
            s.execute("CREATE TRIGGER reject_test_document BEFORE INSERT ON documents WHEN NEW.client_id=73 BEGIN SELECT RAISE(ABORT,'Test insert failure'); END");
            rejects(()->DocumentStore.add(db,storage,73,a));check(files(storage)==before);check(DocumentStore.documents(db,73).isEmpty());s.execute("DROP TRIGGER reject_test_document");
            SwingUtilities.invokeAndWait(()->{try{panelChecks(db,storage,a);}catch(Exception e){throw new RuntimeException(e);}});
            Files.delete(Path.of(secondNew.path()));rejects(()->DocumentStore.file(db,52,secondNew.id()));check(DocumentStore.documents(db,52).size()==1);
            check(Files.readString(legacy).equals("Existing attachment"));check(Files.readString(a).equals("Client A's contract"));
        }finally {
            Path allowed=Path.of(System.getProperty("java.io.tmpdir")).toRealPath(),target=root.toRealPath();
            if(!target.getParent().equals(allowed)||!target.getFileName().toString().startsWith("RealtorDesk-documents-test-"))throw new AssertionError("Unsafe test cleanup path");
            try(var walk=Files.walk(target)){for(Path path:walk.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}
        }
        System.out.println("PASS: "+checks+" document grouping, client isolation, navigation and attachment checks");
    }
    static void panelChecks(Connection db,Path storage,Path source)throws Exception {
        var panel=new DocumentsPanel(db,storage);panel.clientsTable.setSize(800,400);
        check(panel.selectedClient==null);check(panel.clientsModel.getRowCount()==3);
        panel.clientsTable.getRowSorter().toggleSortOrder(3);
        clickClient(panel,52,MouseEvent.BUTTON3);check(panel.selectedClient==null);
        panel.clientsTable.dispatchEvent(new MouseEvent(panel.clientsTable,MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,5,350,1,false,MouseEvent.BUTTON1));check(panel.selectedClient==null);
        clickClient(panel,52,MouseEvent.BUTTON1);check(panel.selectedClient.id()==52);check(panel.documentsModel.getRowCount()==1);check(panel.clientDetails.getText().contains("b@example.test"));
        panel.showClients();check(panel.selectedClient==null);
        int row=findViewRow(panel,31);panel.clientsTable.setRowSelectionInterval(row,row);panel.openClientButton.doClick();check(panel.selectedClient.id()==31);check(panel.documentsModel.getRowCount()==2);
        panel.showClient(73);check(panel.documentsModel.getRowCount()==0);check(panel.documentStatus.getText().startsWith("No documents"));
        DocumentStore.add(db,storage,73,source);panel.refreshVisible();check(panel.documentsModel.getRowCount()==1);panel.showClients();
        int modelRow=panel.clientsTable.convertRowIndexToModel(findViewRow(panel,73));check(((Number)panel.clientsModel.getValueAt(modelRow,4)).longValue()==1);
        try(var s=db.createStatement()){s.execute("UPDATE clients SET name='Renamed Client' WHERE id=52");}
        panel.showClient(52);check(panel.clientTitle.getText().equals("Documents / Renamed Client"));check(panel.documentsModel.getRowCount()==1);
        searchChecks(panel,storage,source);
    }
    static void searchChecks(DocumentsPanel panel,Path storage,Path source)throws Exception {
        panel.showClients();panel.clientSearch.setText("A@EXAMPLE");check(panel.clientsTable.getRowCount()==1);check(panel.clientStatus.getText().equals("1 of 3 clients"));
        clickClient(panel,31,MouseEvent.BUTTON1);check(panel.selectedClient.id()==31);
        panel.documentSearch.setText("EXISTING 2020");check(panel.documentsTable.getRowCount()==1);check(panel.documentStatus.getText().equals("1 of 2 documents"));
        panel.documentsTable.getRowSorter().toggleSortOrder(1);panel.documentsTable.setRowSelectionInterval(0,0);
        check(Files.readString(panel.selectedDocumentFile()).equals("Existing attachment"));
        panel.documentSearch.setText(".*");check(panel.documentsTable.getRowCount()==0);check(panel.documentStatus.getText().contains("No matches"));check(panel.selectedDocumentFile()==null);
        panel.documentSearch.setText("contract.txt");check(panel.documentsTable.getRowCount()==1);
        panel.refreshVisible();check(panel.documentSearch.getText().equals("contract.txt"));check(panel.documentsTable.getRowCount()==1);
        DocumentStore.add(panel.db,storage,31,source);panel.refreshVisible();check(panel.documentsTable.getRowCount()==2);
        panel.showClients();check(panel.clientSearch.getText().equals("A@EXAMPLE"));check(panel.clientsTable.getRowCount()==1);
        panel.clientSearch.setText("5550102");check(panel.clientsTable.getRowCount()==1);clickClient(panel,52,MouseEvent.BUTTON1);
        check(panel.selectedClient.id()==52);check(panel.documentSearch.getText().isEmpty());check(panel.documentsTable.getRowCount()==1);
        panel.documentSearch.setText("existing");check(panel.documentsTable.getRowCount()==0); // Another client's file cannot appear.
        panel.showClients();panel.clientSearch.setText("renamed b@example");check(panel.clientsTable.getRowCount()==1);
        panel.clientSearch.setText("no such client");check(panel.clientsTable.getRowCount()==0);check(!panel.openClientButton.isEnabled());check(panel.clientStatus.getText().contains("No matches"));
        panel.refreshVisible();check(panel.clientSearch.getText().equals("no such client"));check(panel.clientsTable.getRowCount()==0);
        panel.clientSearch.setText("");check(panel.clientsTable.getRowCount()==3);check(panel.openClientButton.isEnabled());
        panel.showClient(31);panel.documentSearch.setText("contract");panel.documentSearch.setText("");check(panel.documentsTable.getRowCount()==3);
    }
    static int findViewRow(DocumentsPanel panel,long id) {
        for(int i=0;i<panel.clientsModel.getRowCount();i++)if(((Number)panel.clientsModel.getValueAt(i,0)).longValue()==id)return panel.clientsTable.convertRowIndexToView(i);
        throw new AssertionError("Client missing");
    }
    static void clickClient(DocumentsPanel panel,long id,int button) {
        int row=findViewRow(panel,id);Rectangle cell=panel.clientsTable.getCellRect(row,0,true);
        panel.clientsTable.dispatchEvent(new MouseEvent(panel.clientsTable,MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,cell.x+5,cell.y+5,1,false,button));
    }
}
