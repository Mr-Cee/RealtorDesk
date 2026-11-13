import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
import java.sql.Connection;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/** Client list -> one client's document folder. Client IDs keep identically named clients separate. */
final class DocumentsPanel extends JPanel {
    final Connection db;
    final Path storage;
    final CardLayout cards=new CardLayout();
    final JPanel content=new JPanel(cards);
    final DefaultTableModel clientsModel=new DefaultTableModel(new String[]{"ID","Client","Properties","Documents"},0) {
        public boolean isCellEditable(int r,int c){return false;}
        public Class<?> getColumnClass(int c){return c==0||c==3?Long.class:String.class;}
    };
    final DefaultTableModel documentsModel=new DefaultTableModel(new String[]{"ID","Filename","Added"},0) {
        public boolean isCellEditable(int r,int c){return false;}
        public Class<?> getColumnClass(int c){return c==0?Long.class:String.class;}
    };
    final JTable clientsTable=table(clientsModel),documentsTable=table(documentsModel);
    final JLabel clientTitle=new JLabel(),clientDetails=new JLabel(),clientStatus=new JLabel(),documentStatus=new JLabel();
    final JButton openClientButton=new JButton("Open client documents");
    final JTextField clientSearch=new JTextField(25),documentSearch=new JTextField(25);
    DocumentStore.Client selectedClient;

    DocumentsPanel(Connection db,Path storage) {
        super(new BorderLayout());this.db=db;this.storage=storage;
        var documentCountRenderer=new javax.swing.table.DefaultTableCellRenderer();documentCountRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        clientsTable.getColumnModel().getColumn(clientsTable.convertColumnIndexToView(3)).setCellRenderer(documentCountRenderer);
        var clients=new JPanel(new BorderLayout(8,8));
        var clientButtons=new JPanel(new FlowLayout(FlowLayout.LEFT));
        openClientButton.addActionListener(e->openSelectedClient());clientButtons.add(openClientButton);
        var refresh=new JButton("Refresh");refresh.addActionListener(e->safe(this::showClients));clientButtons.add(refresh);
        var clientHeader=new JPanel(new GridLayout(0,1));clientHeader.add(clientButtons);
        clientHeader.add(searchBar("Search clients / properties:",clientSearch));
        ClientSearch.attach(clientsTable,clientSearch,clientStatus,"clients","Add a client in the Clients tab to start organizing their documents.","Search client names or linked property addresses. Use multiple words to narrow results.",1,2);
        clientsTable.getRowSorter().addRowSorterListener(e->openClientButton.setEnabled(clientsTable.getRowCount()>0));
        clients.add(clientHeader,BorderLayout.NORTH);clients.add(new JScrollPane(clientsTable));clients.add(clientStatus,BorderLayout.SOUTH);
        clientsTable.setToolTipText("Click a client to open their documents.");
        clientsTable.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){
            if(!SwingUtilities.isLeftMouseButton(e)||e.getClickCount()!=1)return;
            int row=clientsTable.rowAtPoint(e.getPoint());if(row<0)return;
            clientsTable.setRowSelectionInterval(row,row);openSelectedClient();
        }});
        clientsTable.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"openClientDocuments");
        clientsTable.getActionMap().put("openClientDocuments",new AbstractAction(){public void actionPerformed(ActionEvent e){openSelectedClient();}});
        var documents=new JPanel(new BorderLayout(8,8));
        var header=new JPanel(new BorderLayout(8,8));
        var actions=new JPanel(new FlowLayout(FlowLayout.LEFT));
        var back=new JButton("Back to clients");back.addActionListener(e->safe(this::showClients));actions.add(back);
        var add=new JButton("Add document");add.addActionListener(e->addDocument());actions.add(add);
        var open=new JButton("Open document");open.addActionListener(e->openDocument());actions.add(open);
        var reload=new JButton("Refresh");reload.addActionListener(e->refreshVisible());actions.add(reload);
        clientTitle.setFont(clientTitle.getFont().deriveFont(Font.BOLD,18));
        var heading=new JPanel(new GridLayout(0,1,0,3));heading.setBorder(BorderFactory.createEmptyBorder(0,8,6,8));heading.add(clientTitle);heading.add(clientDetails);
        header.add(actions,BorderLayout.NORTH);header.add(heading,BorderLayout.CENTER);
        header.add(searchBar("Search documents:",documentSearch),BorderLayout.SOUTH);
        ClientSearch.attach(documentsTable,documentSearch,documentStatus,"documents","No documents yet. Add a document to get started.","Search this client's document filenames or added dates. Use multiple words to narrow results.",1,2);
        documents.add(header,BorderLayout.NORTH);documents.add(new JScrollPane(documentsTable));documents.add(documentStatus,BorderLayout.SOUTH);
        documentsTable.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){
            if(SwingUtilities.isLeftMouseButton(e)&&e.getClickCount()==2&&documentsTable.rowAtPoint(e.getPoint())>=0){int row=documentsTable.rowAtPoint(e.getPoint());documentsTable.setRowSelectionInterval(row,row);openDocument();}
        }});
        content.add(clients,"clients");content.add(documents,"documents");add(content);safe(this::showClients);
    }
    static JTable table(DefaultTableModel model) {
        var table=new JTable(model);table.setAutoCreateRowSorter(true);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);table.setRowHeight(28);table.setFillsViewportHeight(true);
        table.removeColumn(table.getColumnModel().getColumn(0));return table;
    }
    static JPanel searchBar(String title,JTextField field) {
        var bar=new JPanel(new FlowLayout(FlowLayout.LEFT));var label=new JLabel(title);label.setLabelFor(field);
        var clear=new JButton("Clear");clear.addActionListener(e->{field.setText("");field.requestFocusInWindow();});
        bar.add(label);bar.add(field);bar.add(clear);return bar;
    }
    void showClients() throws Exception {
        selectedClient=null;var clients=DocumentStore.clients(db);clientsModel.setRowCount(0);
        for(var client:clients)clientsModel.addRow(new Object[]{client.id(),client.name(),client.properties(),client.documentCount()});
        openClientButton.setEnabled(clientsTable.getRowCount()>0);
        cards.show(content,"clients");
    }
    void openSelectedClient() {
        int row=clientsTable.getSelectedRow();if(row<0)return;
        long id=((Number)clientsModel.getValueAt(clientsTable.convertRowIndexToModel(row),0)).longValue();safe(()->showClient(id));
    }
    void showClient(long clientId) throws Exception {
        var client=DocumentStore.client(db,clientId);var documents=DocumentStore.documents(db,clientId);
        boolean changedClient=selectedClient==null||selectedClient.id()!=clientId;
        selectedClient=client;documentsModel.setRowCount(0);
        if(changedClient)documentSearch.setText("");
        for(var document:documents)documentsModel.addRow(new Object[]{document.id(),document.name(),document.added()});
        clientTitle.setText("Documents / "+client.name());
        String email=client.email()==null?"":client.email(),phone=client.phone()==null?"":client.phone();
        clientDetails.setText(email+(email.isBlank()||phone.isBlank()?"":"  |  ")+phone);
        cards.show(content,"documents");
    }
    void refreshVisible() {safe(()->{if(selectedClient==null)showClients();else showClient(selectedClient.id());});}
    void addDocument() {safe(()->{
        if(selectedClient==null)return;
        long clientId=selectedClient.id();var picker=new JFileChooser();picker.setDialogTitle("Add document for "+selectedClient.name());
        if(picker.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        DocumentStore.add(db,storage,clientId,picker.getSelectedFile().toPath());showClient(clientId);
    });}
    void openDocument() {safe(()->{
        Path path=selectedDocumentFile();if(path==null)return;
        if(!Desktop.isDesktopSupported()||!Desktop.getDesktop().isSupported(Desktop.Action.OPEN))throw new Exception("Opening documents is not supported on this computer.");
        Desktop.getDesktop().open(path.toFile());
    });}
    Path selectedDocumentFile() throws Exception {
        int row=documentsTable.getSelectedRow();if(selectedClient==null||row<0)return null;
        long id=((Number)documentsModel.getValueAt(documentsTable.convertRowIndexToModel(row),0)).longValue();
        return DocumentStore.file(db,selectedClient.id(),id);
    }
    interface Work{void run()throws Exception;}
    void safe(Work work){try{work.run();}catch(Exception e){JOptionPane.showMessageDialog(this,e.getMessage(),"Documents",JOptionPane.ERROR_MESSAGE);}}
}
