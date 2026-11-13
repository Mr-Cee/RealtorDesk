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
    record AvailableUpdate(Updater.Update update,Path cachedInstaller) { }
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
        settings.putAll(AppSettings.load(HOME.resolve("settings.properties")));
        AppTheme.fromId(settings.getProperty("theme")).apply();
        java.util.concurrent.CompletableFuture.runAsync(() -> cleanupInstalledUpdates(Path.of(System.getProperty("java.io.tmpdir"))));
        db = DriverManager.getConnection("jdbc:sqlite:" + HOME.resolve("realtor.db"));
        try (var s = db.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("CREATE TABLE IF NOT EXISTS clients(id INTEGER PRIMARY KEY,name TEXT NOT NULL,phone TEXT,email TEXT,status TEXT,notes TEXT)");
            s.execute("CREATE TABLE IF NOT EXISTS properties(id INTEGER PRIMARY KEY,client_id INTEGER REFERENCES clients(id),address TEXT NOT NULL,status TEXT,notes TEXT)");
            s.execute("CREATE TABLE IF NOT EXISTS calls(id INTEGER PRIMARY KEY,client_id INTEGER NOT NULL REFERENCES clients(id),called TEXT,followup TEXT,notes TEXT)");
            s.execute("CREATE TABLE IF NOT EXISTS documents(id INTEGER PRIMARY KEY,client_id INTEGER NOT NULL REFERENCES clients(id),name TEXT,path TEXT,added TEXT)");
        }
        ClientStore.migrate(db);
        var icon=javax.imageio.ImageIO.read(RealtorApp.class.getResource("/app-icon.png"));
        var icons=new ArrayList<Image>();
        for(int size:new int[]{16,32,48,64,128,256})icons.add(icon.getScaledInstance(size,size,Image.SCALE_SMOOTH));
        frame.setIconImages(icons);
        frame.setJMenuBar(createMenuBar());
        var tabs = new JTabbedPane();
        tabs.add("Clients", records("clients", new String[]{"ID","First name","Last name","Phone","Email","Client type","Status","Notes"}, "SELECT id,first_name,last_name,phone,email,client_type,status,notes FROM clients", () -> editClient(null)));
        tabs.add("Properties", records("properties", new String[]{"ID","Client","Address","Status","Notes"}, "SELECT p.id,c.name,p.address,p.status,p.notes FROM properties p LEFT JOIN clients c ON c.id=p.client_id", this::addProperty));
        tabs.add("Calls & follow-ups", records("calls", new String[]{"ID","Client","Called","Follow-up","Notes"}, "SELECT x.id,c.name,x.called,x.followup,x.notes FROM calls x JOIN clients c ON c.id=x.client_id ORDER BY x.followup", this::addCall));
        var documents=new DocumentsPanel(db,HOME.resolve("documents"));
        tabs.add("Documents",documents);
        tabs.addChangeListener(e->{if(tabs.getSelectedComponent()==documents)documents.refreshVisible();});
        var root = new JPanel(new BorderLayout(10,10));
        root.setBorder(BorderFactory.createEmptyBorder(14,14,14,14));
        var top = new JPanel(new BorderLayout());
        top.add(new JLabel("RealtorDesk — Clients, homes and conversations"), BorderLayout.WEST);
        root.add(top, BorderLayout.NORTH); root.add(tabs); root.add(new JLabel("Data: " + HOME), BorderLayout.SOUTH);
        frame.setContentPane(root); frame.setSize(1150,700); frame.setLocationRelativeTo(null); frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); frame.setVisible(true);
        SwingUtilities.invokeLater(() -> {
            if (ReleaseNotes.shouldShow(settings, VERSION)) showReleaseNotes();
            if (!settings.getProperty("repository", "").isBlank()) checkUpdates(false);
        });
    }
    JMenuBar createMenuBar() {
        var menuBar = new JMenuBar();
        var settingsButton = new JButton("Settings");
        settingsButton.setMnemonic(java.awt.event.KeyEvent.VK_S);
        settingsButton.setBorderPainted(false);
        settingsButton.setContentAreaFilled(false);
        settingsButton.setMargin(new Insets(3, 10, 3, 10));
        settingsButton.addActionListener(e -> showSettings());
        menuBar.add(settingsButton);
        var help = new JMenu("Help");
        SwingUtilities.updateComponentTreeUI(updateMenuItem);
        help.setMnemonic(java.awt.event.KeyEvent.VK_H);
        updateMenuItem.setMnemonic(java.awt.event.KeyEvent.VK_U);
        updateMenuItem.addActionListener(e -> checkUpdates(true));
        help.add(updateMenuItem);
        var whatsNew = new JMenuItem("What's New");
        whatsNew.addActionListener(e -> showReleaseNotes());
        help.add(whatsNew);
        help.addSeparator();
        var about = new JMenuItem("About RealtorDesk");
        about.setMnemonic(java.awt.event.KeyEvent.VK_A);
        about.addActionListener(e -> showAbout());
        help.add(about);
        menuBar.add(help);
        return menuBar;
    }
    void showReleaseNotes() {
        ReleaseNotes.show(frame, HOME.resolve("settings.properties"), settings, VERSION);
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
        var table = new JTable(model); table.setAutoCreateRowSorter(true); table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); table.setRowHeight(28);table.setFillsViewportHeight(true);
        Runnable refresh = () -> safe(() -> { model.setRowCount(0); try(var s=db.createStatement();var rs=s.executeQuery(sql)) { while(rs.next()) { Object[] row=new Object[columns.length]; for(int i=0;i<row.length;i++) row[i]=rs.getObject(i+1); model.addRow(row); } } });
        var buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        var create = new JButton("Add " + type); create.addActionListener(e -> { add.run(); refresh.run(); }); buttons.add(create);
        var reload = new JButton("Refresh"); reload.addActionListener(e -> refresh.run()); buttons.add(reload);
        if(type.equals("clients")) {
            Runnable editSelected=ClientTableActions.install(table,this::editClient,refresh);
            var edit=new JButton("Edit selected");edit.addActionListener(e -> editSelected.run());buttons.add(edit);
            table.setToolTipText("Double-click a client to edit their details.");
            var search=new JTextField(23);var label=new JLabel("Search clients:");label.setLabelFor(search);buttons.add(label);buttons.add(search);
            var clear=new JButton("Clear");clear.addActionListener(e->{search.setText("");search.requestFocusInWindow();});buttons.add(clear);
            var count=new JLabel();count.setBorder(BorderFactory.createEmptyBorder(4,4,4,4));panel.add(count,BorderLayout.SOUTH);
            ClientSearch.attach(table,search,count);
        }
        if(type.equals("properties")) {
            var open=new JButton("Open map");
            open.addActionListener(e -> safe(() -> {if(table.getSelectedRow()<0)return;int row=table.convertRowIndexToModel(table.getSelectedRow());Desktop.getDesktop().browse(URI.create("https://www.google.com/maps/search/?api=1&query="+URLEncoder.encode(model.getValueAt(row,2).toString(),java.nio.charset.StandardCharsets.UTF_8)));}));buttons.add(open);
        }
        panel.add(buttons,BorderLayout.NORTH); panel.add(new JScrollPane(table)); refresh.run(); return panel;
    }
    void editClient(Long id) { safe(() -> {
        String[] values={"","","","","Lead",""};
        ClientStore.Type selectedType=ClientStore.Type.UNSPECIFIED;
        String previousName="";
        if(id!=null) {var client=ClientStore.load(db,id);values=new String[]{client.firstName(),client.lastName(),client.phone(),client.email(),client.status(),client.notes()};selectedType=client.type();previousName=client.name();}
        var clientType=new JComboBox<ClientStore.Type>(ClientStore.Type.values());clientType.setSelectedItem(selectedType);
        var fields=new JTextField[6]; var form=new JPanel(new GridLayout(0,2,8,8)); String[] labels={"First name","Last name","Phone","Email","Status","Notes"};
        for(int i=0;i<6;i++) {
            if(i==4){form.add(new JLabel("Client type"));form.add(clientType);}
            fields[i]=new JTextField(values[i],30);form.add(new JLabel(labels[i]));form.add(fields[i]);
        }
        if(id!=null&&(previousName.contains(",")||previousName.trim().split("(?U)\\s+").length>2)) {
            var original=new JTextField(previousName,30);original.setEditable(false);form.add(new JLabel("Full name on file"));form.add(original);
        }
        if(JOptionPane.showConfirmDialog(frame,form,"Client",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION) return;
        ClientStore.save(db,id,fields[0].getText(),fields[1].getText(),fields[2].getText().trim(),fields[3].getText().trim(),fields[4].getText().trim(),fields[5].getText().trim(),(ClientStore.Type)clientType.getSelectedItem());
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
    void execute(String sql,Object... args) throws Exception { try(var p=db.prepareStatement(sql)) { for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);p.executeUpdate(); } }
    void showSettings() {
        AppTheme previous=AppTheme.fromId(settings.getProperty("theme"));
        var theme=new JComboBox<AppTheme>(AppTheme.values());theme.setSelectedItem(previous);
        var repository=new JTextField(settings.getProperty("repository","Mr-Cee/RealtorDesk"),30);
        var form=new JPanel(new GridBagLayout());
        var layout=new GridBagConstraints();layout.insets=new Insets(6,6,6,6);layout.anchor=GridBagConstraints.WEST;
        layout.gridx=0;layout.gridy=0;form.add(new JLabel("Theme"),layout);
        layout.gridx=1;layout.fill=GridBagConstraints.HORIZONTAL;layout.weightx=1;form.add(theme,layout);
        layout.gridx=0;layout.gridy=1;layout.gridwidth=2;form.add(new JLabel("Preview a theme, then Save to keep it. Cancel restores your previous theme."),layout);
        layout.gridy=2;layout.gridwidth=1;layout.weightx=0;form.add(new JLabel("Update repository"),layout);
        layout.gridx=1;layout.weightx=1;form.add(repository,layout);
        layout.gridx=0;layout.gridy=3;layout.gridwidth=2;form.add(new JLabel("GitHub owner/repository. Leave blank to turn off startup checks."),layout);
        theme.addActionListener(e -> ((AppTheme)theme.getSelectedItem()).apply());
        boolean saved=false;
        try {
            while(true) {
                Object[] choices={"Save","Cancel"};
                int result=JOptionPane.showOptionDialog(frame,form,"Settings",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE,null,choices,choices[0]);
                if(result!=0)return;
                String repo=repository.getText().trim();
                if(!repo.isEmpty()&&!repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
                    JOptionPane.showMessageDialog(frame,"Use owner/repository format for GitHub.","Check settings",JOptionPane.ERROR_MESSAGE);continue;
                }
                var updated=new Properties();updated.putAll(settings);updated.setProperty("repository",repo);
                updated.setProperty("theme",((AppTheme)theme.getSelectedItem()).id);
                AppSettings.save(HOME.resolve("settings.properties"),updated);
                settings.clear();settings.putAll(updated);saved=true;return;
            }
        } catch(Exception e){JOptionPane.showMessageDialog(frame,e.getMessage(),"Settings could not be saved",JOptionPane.ERROR_MESSAGE);}
        finally {if(!saved)previous.apply();}
    }
    static boolean newer(String tag) {
        try { return Updater.compareVersions(tag, VERSION)>0; } catch(IllegalArgumentException e) { return false; }
    }
    void checkUpdates(boolean manual) {
        if(updateBusy) return;
        updateBusy=true; updateMenuItem.setEnabled(false); updateMenuItem.setText("Checking...");
        String repo=settings.getProperty("repository", "Mr-Cee/RealtorDesk");
        new SwingWorker<Optional<AvailableUpdate>,Void>() {
            protected Optional<AvailableUpdate> doInBackground() throws Exception {
                Path temp=Path.of(System.getProperty("java.io.tmpdir"));cleanupInstalledUpdates(temp);
                try(var client=Updater.client()) {
                    var latest=Updater.latest(client,repo,VERSION);
                    if(latest.isEmpty())return Optional.empty();
                    var update=latest.get();return Optional.of(new AvailableUpdate(update,UpdateCache.find(update,temp).orElse(null)));
                }
            }
            protected void done() {
                finishUpdate();
                try {
                    var result=get();
                    if(result.isEmpty()) { if(manual)JOptionPane.showMessageDialog(frame,"You are up to date ("+VERSION+").");return; }
                    var available=result.get();var update=available.update();
                    if(available.cachedInstaller()!=null) {installUpdate(update,available.cachedInstaller());return;}
                    if(JOptionPane.showConfirmDialog(frame,"Version "+update.version()+" is available (installed: "+VERSION+").\nDownload the update?","Update available",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION) downloadUpdate(update);
                } catch(Exception e) { if(manual) updateError(e); }
            }
        }.execute();
    }
    void cleanupInstalledUpdates(Path temp) {
        try {Updater.installationFolder();UpdateCache.cleanupQuietly(temp,VERSION);}catch(Exception ignored) { }
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
                try(var client=Updater.client()) { return UpdateCache.prepare(client,update,Path.of(System.getProperty("java.io.tmpdir")),this::setProgress); }
            }
            protected void done() {
                dialog.dispose();finishUpdate();
                try {
                    installUpdate(update,get());
                } catch(Exception e) { updateError(e); }
            }
        };
        worker.addPropertyChangeListener(e -> {if("progress".equals(e.getPropertyName()))bar.setValue((Integer)e.getNewValue());});
        worker.execute();
        dialog.setVisible(true);
    }
    void installUpdate(Updater.Update update,Path installer) {
        try {
            Path folder=Updater.installationFolder();
            Object[] choices={"Install and restart","Not now"};
            if(JOptionPane.showOptionDialog(frame,"Version "+update.version()+" is downloaded and verified. Install now?\nRealtorDesk will close, update in its current folder, and reopen.\nChoose Not now to keep the installer for the next update check.","Update ready",JOptionPane.YES_NO_OPTION,JOptionPane.QUESTION_MESSAGE,null,choices,choices[0])!=0)return;
            var helper=Updater.launchInstaller(installer,folder);
            if(helper.waitFor(800,java.util.concurrent.TimeUnit.MILLISECONDS))throw new Exception("The update helper could not start. The installer is saved for another attempt.");
            try {db.close();}catch(Exception e){helper.destroy();throw e;}
            frame.dispose();System.exit(0);
        }catch(Exception e){updateError(e);}
    }
    interface Work { void run() throws Exception; }
    void safe(Work work) { try { work.run(); } catch(Exception e) { JOptionPane.showMessageDialog(frame,e.getMessage(),"Unable to complete action",JOptionPane.ERROR_MESSAGE); } }
}
