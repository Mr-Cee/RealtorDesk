import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import com.google.gson.*;

public class RealtorApp {
    static final String VERSION = BuildInfo.VERSION;
    static final Path HOME = Path.of(System.getenv().getOrDefault("LOCALAPPDATA", System.getProperty("user.home")), "RealtorDesk");
    final JFrame frame = new JFrame("RealtorDesk • " + VERSION);
    final Properties settings = new Properties();
    Connection db;
    final JMenuItem updateMenuItem = new JMenuItem("Check for updates");
    boolean updateBusy;
    record Choice(long id, String name) { public String toString() { return name; } }

    public static void main(String[] args) {
        // Older updaters reopen javaw directly. Switch once to the installed native launcher.
        try {
            Path folder=Updater.installationFolder();
            String command=ProcessHandle.current().info().command().orElse("");
            String executable=command.isEmpty()?"":Path.of(command).getFileName().toString();
            if((executable.equalsIgnoreCase("javaw.exe")||executable.equalsIgnoreCase("java.exe")) && Files.isRegularFile(folder.resolve("RealtorDesk.exe"))) {
                new ProcessBuilder(folder.resolve("RealtorDesk.exe").toString()).directory(folder.toFile()).start();
                return;
            }
        } catch(Exception ignored) { /* Development runs and older layouts can start normally. */ }
        SwingUtilities.invokeLater(() -> { try { new RealtorApp().start(); } catch (Exception e) { JOptionPane.showMessageDialog(null, e.toString(), "Unable to start", JOptionPane.ERROR_MESSAGE); } });
    }
    void start() throws Exception {
        Files.createDirectories(HOME.resolve("documents"));
        settings.setProperty("repository", "Mr-Cee/RealtorDesk");
        if (Files.exists(HOME.resolve("settings.properties"))) try (var in = Files.newInputStream(HOME.resolve("settings.properties"))) { settings.load(in); }
        db = DriverManager.getConnection("jdbc:sqlite:" + HOME.resolve("realtor.db"));
        try (var s = db.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("CREATE TABLE IF NOT EXISTS clients(id INTEGER PRIMARY KEY,name TEXT NOT NULL,phone TEXT,email TEXT,status TEXT,notes TEXT)");
            s.execute("CREATE TABLE IF NOT EXISTS properties(id INTEGER PRIMARY KEY,client_id INTEGER REFERENCES clients(id),address TEXT NOT NULL,status TEXT,notes TEXT)");
            s.execute("CREATE TABLE IF NOT EXISTS calls(id INTEGER PRIMARY KEY,client_id INTEGER NOT NULL REFERENCES clients(id),called TEXT,followup TEXT,notes TEXT)");
            s.execute("CREATE TABLE IF NOT EXISTS documents(id INTEGER PRIMARY KEY,client_id INTEGER NOT NULL REFERENCES clients(id),name TEXT,path TEXT,added TEXT)");
            s.execute("PRAGMA user_version=1");
        }
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        var icon=javax.imageio.ImageIO.read(RealtorApp.class.getResource("/app-icon.png"));
        var icons=new ArrayList<Image>();
        for(int size:new int[]{16,32,48,64,128,256})icons.add(icon.getScaledInstance(size,size,Image.SCALE_SMOOTH));
        frame.setIconImages(icons);
        frame.setJMenuBar(createMenuBar());
        var tabs = new JTabbedPane();
        tabs.add("Clients", records("clients", new String[]{"ID","Name","Phone","Email","Status","Notes"}, "SELECT * FROM clients", () -> editClient(null)));
        tabs.add("Properties", records("properties", new String[]{"ID","Client","Address","Status","Notes"}, "SELECT p.id,c.name,p.address,p.status,p.notes FROM properties p LEFT JOIN clients c ON c.id=p.client_id", this::addProperty));
        tabs.add("Calls & follow-ups", records("calls", new String[]{"ID","Client","Called","Follow-up","Notes"}, "SELECT x.id,c.name,x.called,x.followup,x.notes FROM calls x JOIN clients c ON c.id=x.client_id ORDER BY x.followup", this::addCall));
        tabs.add("Documents", records("documents", new String[]{"ID","Client","Filename","Stored path","Added"}, "SELECT d.id,c.name,d.name,d.path,d.added FROM documents d JOIN clients c ON c.id=d.client_id", this::addDocument));
        var root = new JPanel(new BorderLayout(10,10));
        root.setBorder(BorderFactory.createEmptyBorder(14,14,14,14));
        var top = new JPanel(new BorderLayout());
        top.add(new JLabel("RealtorDesk — Clients, homes and conversations"), BorderLayout.WEST);
        root.add(top, BorderLayout.NORTH); root.add(tabs); root.add(new JLabel("Data: " + HOME), BorderLayout.SOUTH);
        frame.setContentPane(root); frame.setSize(1150,700); frame.setLocationRelativeTo(null); frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); frame.setVisible(true);
        if (!settings.getProperty("repository", "").isBlank()) checkUpdates(false);
    }
    JMenuBar createMenuBar() {
        var menuBar = new JMenuBar();
        var settingsButton = new JButton("Settings");
        settingsButton.setMnemonic(java.awt.event.KeyEvent.VK_S);
        settingsButton.setBorderPainted(false);
        settingsButton.setContentAreaFilled(false);
        settingsButton.setMargin(new Insets(3, 10, 3, 10));
        settingsButton.addActionListener(e -> configureUpdates());
        menuBar.add(settingsButton);
        var help = new JMenu("Help");
        help.setMnemonic(java.awt.event.KeyEvent.VK_H);
        updateMenuItem.setMnemonic(java.awt.event.KeyEvent.VK_U);
        updateMenuItem.addActionListener(e -> checkUpdates(true));
        help.add(updateMenuItem);
        help.addSeparator();
        var about = new JMenuItem("About RealtorDesk");
        about.setMnemonic(java.awt.event.KeyEvent.VK_A);
        about.addActionListener(e -> showAbout());
        help.add(about);
        menuBar.add(help);
        return menuBar;
    }
    void showAbout() {
        var information = new JTextArea("RealtorDesk\nVersion " + VERSION
                + "\n\nA desktop organizer for individual realtors."
                + "\nManage clients, properties, calls, follow-ups and paperwork."
                + "\n\nYour records and attachments are stored locally on this computer."
                + "\nData folder: " + HOME
                + "\n\nProject and releases:\nhttps://github.com/Mr-Cee/RealtorDesk");
        information.setEditable(false);
        information.setOpaque(false);
        information.setFont(UIManager.getFont("Label.font"));
        information.setLineWrap(true);
        information.setWrapStyleWord(true);
        information.setColumns(46);
        information.setCaretPosition(0);
        JOptionPane.showMessageDialog(frame, information, "About RealtorDesk", JOptionPane.INFORMATION_MESSAGE);
    }
    JPanel records(String type, String[] columns, String sql, Runnable add) {
        var panel = new JPanel(new BorderLayout(8,8));
        var model = new DefaultTableModel(columns,0) { public boolean isCellEditable(int r,int c) { return false; } };
        var table = new JTable(model); table.setAutoCreateRowSorter(true); table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); table.setRowHeight(28);
        Runnable refresh = () -> safe(() -> { model.setRowCount(0); try(var s=db.createStatement();var rs=s.executeQuery(sql)) { while(rs.next()) { Object[] row=new Object[columns.length]; for(int i=0;i<row.length;i++) row[i]=rs.getObject(i+1); model.addRow(row); } } });
        var buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        var create = new JButton("Add " + type); create.addActionListener(e -> { add.run(); refresh.run(); }); buttons.add(create);
        var reload = new JButton("Refresh"); reload.addActionListener(e -> refresh.run()); buttons.add(reload);
        if(type.equals("clients")) { var edit=new JButton("Edit selected"); edit.addActionListener(e -> { if(table.getSelectedRow()>=0) { editClient(((Number)model.getValueAt(table.convertRowIndexToModel(table.getSelectedRow()),0)).longValue()); refresh.run(); } }); buttons.add(edit); }
        if(type.equals("properties") || type.equals("documents")) {
            var open=new JButton(type.equals("properties")?"Open map":"Open document");
            open.addActionListener(e -> safe(() -> { if(table.getSelectedRow()<0) return; int r=table.convertRowIndexToModel(table.getSelectedRow()); if(type.equals("properties")) Desktop.getDesktop().browse(URI.create("https://www.google.com/maps/search/?api=1&query="+URLEncoder.encode(model.getValueAt(r,2).toString(), java.nio.charset.StandardCharsets.UTF_8))); else Desktop.getDesktop().open(Path.of(model.getValueAt(r,3).toString()).toFile()); })); buttons.add(open);
        }
        panel.add(buttons,BorderLayout.NORTH); panel.add(new JScrollPane(table)); refresh.run(); return panel;
    }
    void editClient(Long id) { safe(() -> {
        String[] values={"","","","Lead",""};
        if(id!=null) try(var p=db.prepareStatement("SELECT name,phone,email,status,notes FROM clients WHERE id=?")) { p.setLong(1,id); try(var r=p.executeQuery()) { if(r.next()) for(int i=0;i<5;i++) values[i]=r.getString(i+1); } }
        var fields=new JTextField[5]; var form=new JPanel(new GridLayout(0,2,8,8)); String[] labels={"Name *","Phone","Email","Status","Notes"};
        for(int i=0;i<5;i++) { fields[i]=new JTextField(values[i],30); form.add(new JLabel(labels[i])); form.add(fields[i]); }
        if(JOptionPane.showConfirmDialog(frame,form,"Client",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION) return;
        if(fields[0].getText().isBlank()) throw new Exception("Client name is required.");
        try(var p=db.prepareStatement(id==null?"INSERT INTO clients(name,phone,email,status,notes) VALUES(?,?,?,?,?)":"UPDATE clients SET name=?,phone=?,email=?,status=?,notes=? WHERE id=?")) { for(int i=0;i<5;i++) p.setString(i+1,fields[i].getText().trim()); if(id!=null)p.setLong(6,id); p.executeUpdate(); }
    }); }
    Choice chooseClient() throws Exception {
        var choices=new ArrayList<Choice>(); try(var s=db.createStatement();var r=s.executeQuery("SELECT id,name FROM clients ORDER BY name")) { while(r.next()) choices.add(new Choice(r.getLong(1),r.getString(2))); }
        if(choices.isEmpty()) throw new Exception("Add a client first.");
        return (Choice)JOptionPane.showInputDialog(frame,"Client","Select client",JOptionPane.QUESTION_MESSAGE,null,choices.toArray(),choices.get(0));
    }
    String[] form(String title,String... labels) {
        var panel=new JPanel(new GridLayout(0,2,8,8)); var fields=new JTextField[labels.length];
        for(int i=0;i<labels.length;i++) { panel.add(new JLabel(labels[i])); fields[i]=new JTextField(30); panel.add(fields[i]); }
        if(JOptionPane.showConfirmDialog(frame,panel,title,JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return null;
        return Arrays.stream(fields).map(f->f.getText().trim()).toArray(String[]::new);
    }
    void addProperty() { safe(() -> { var c=chooseClient();if(c==null)return; var v=form("Property","Address *","Status","Notes");if(v==null)return;if(v[0].isBlank())throw new Exception("Address is required."); execute("INSERT INTO properties(client_id,address,status,notes) VALUES(?,?,?,?)",c.id,v[0],v[1],v[2]); }); }
    void addCall() { safe(() -> { var c=chooseClient();if(c==null)return;var v=form("Log call","Follow-up date (YYYY-MM-DD, optional)","Call notes");if(v==null)return;if(!v[0].isBlank())LocalDate.parse(v[0]);execute("INSERT INTO calls(client_id,called,followup,notes) VALUES(?,?,?,?)",c.id,LocalDateTime.now().toString(),v[0],v[1]); }); }
    void addDocument() { safe(() -> { var c=chooseClient();if(c==null)return;var picker=new JFileChooser();if(picker.showOpenDialog(frame)!=JFileChooser.APPROVE_OPTION)return;var source=picker.getSelectedFile().toPath();var dest=HOME.resolve("documents").resolve(UUID.randomUUID()+"-"+source.getFileName());Files.copy(source,dest);try { execute("INSERT INTO documents(client_id,name,path,added) VALUES(?,?,?,?)",c.id,source.getFileName().toString(),dest.toString(),LocalDate.now().toString()); } catch(Exception e) { Files.deleteIfExists(dest); throw e; } }); }
    void execute(String sql,Object... args) throws Exception { try(var p=db.prepareStatement(sql)) { for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);p.executeUpdate(); } }
    void configureUpdates() { safe(() -> { String repo=JOptionPane.showInputDialog(frame,"Public GitHub repository (owner/repository)",settings.getProperty("repository",""));if(repo==null)return;repo=repo.trim();if(!repo.isEmpty()&&!repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))throw new Exception("Use owner/repository format.");settings.setProperty("repository",repo);try(var out=Files.newOutputStream(HOME.resolve("settings.properties"))) {settings.store(out,"RealtorDesk");}if(!repo.isEmpty())checkUpdates(true); }); }
    static boolean newer(String tag) {
        try { return Updater.compareVersions(tag, VERSION)>0; } catch(IllegalArgumentException e) { return false; }
    }
    void checkUpdates(boolean manual) {
        if(updateBusy) return;
        updateBusy=true; updateMenuItem.setEnabled(false); updateMenuItem.setText("Checking...");
        String repo=settings.getProperty("repository", "Mr-Cee/RealtorDesk");
        new SwingWorker<Optional<Updater.Update>,Void>() {
            protected Optional<Updater.Update> doInBackground() throws Exception {
                try(var client=Updater.client()) { return Updater.latest(client,repo,VERSION); }
            }
            protected void done() {
                finishUpdate();
                try {
                    var result=get();
                    if(result.isEmpty()) { if(manual)JOptionPane.showMessageDialog(frame,"You are up to date ("+VERSION+").");return; }
                    var update=result.get();
                    if(JOptionPane.showConfirmDialog(frame,"Version "+update.version()+" is available (installed: "+VERSION+").\nDownload the update?","Update available",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION) downloadUpdate(update);
                } catch(Exception e) { if(manual) updateError(e); }
            }
        }.execute();
    }
    void finishUpdate() { updateBusy=false;updateMenuItem.setEnabled(true);updateMenuItem.setText("Check for updates"); }
    void updateError(Exception error) {
        Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();
        JOptionPane.showMessageDialog(frame,cause.getMessage(),"Update could not complete",JOptionPane.ERROR_MESSAGE);
    }
    void downloadUpdate(Updater.Update update) {
        final Path installFolder;
        try { installFolder=Updater.installationFolder(); } catch(Exception e) { updateError(e);return; }
        updateBusy=true;updateMenuItem.setEnabled(false);updateMenuItem.setText("Downloading...");
        var dialog=new JDialog(frame,"Downloading RealtorDesk "+update.version(),true);
        var bar=new JProgressBar(0,100);bar.setStringPainted(true);
        var panel=new JPanel(new BorderLayout(10,10));panel.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
        panel.add(new JLabel("Downloading and verifying update. Please wait..."),BorderLayout.NORTH);panel.add(bar);
        dialog.setContentPane(panel);dialog.setSize(430,135);dialog.setLocationRelativeTo(frame);dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        var worker=new SwingWorker<Path,Void>() {
            protected Path doInBackground() throws Exception {
                try(var client=Updater.client()) { return Updater.download(client,update,Path.of(System.getProperty("java.io.tmpdir")),this::setProgress); }
            }
            protected void done() {
                dialog.dispose();finishUpdate();
                try {
                    Path installer=get();
                    if(JOptionPane.showConfirmDialog(frame,"Download verified. Install version "+update.version()+" now?\nRealtorDesk will close, update in its current folder, and reopen.\nYour client records and attachments will be kept.","Install and restart",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION) {Files.deleteIfExists(installer);Files.deleteIfExists(installer.getParent());return;}
                    var helper=Updater.launchInstaller(installer,installFolder);
                    if(helper.waitFor(800,java.util.concurrent.TimeUnit.MILLISECONDS)) throw new Exception("The update helper could not start. Your current version is still open.");
                    try { db.close(); } catch(Exception e) { helper.destroy();throw e; }
                    frame.dispose();System.exit(0);
                } catch(Exception e) { updateError(e); }
            }
        };
        worker.addPropertyChangeListener(e -> {if("progress".equals(e.getPropertyName()))bar.setValue((Integer)e.getNewValue());});
        worker.execute();
        dialog.setVisible(true);
    }
    interface Work { void run() throws Exception; }
    void safe(Work work) { try { work.run(); } catch(Exception e) { JOptionPane.showMessageDialog(frame,e.getMessage(),"Unable to complete action",JOptionPane.ERROR_MESSAGE); } }
}
