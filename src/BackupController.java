import java.awt.*;
import java.nio.file.*;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.Callable;
import javax.swing.*;

final class BackupController {
    final JFrame frame;final Connection db;final Path data,settingsFile;final Properties settings;
    final javax.swing.Timer timer;String fingerprint;
    BackupController(JFrame frame,Connection db,Path data,Path settingsFile,Properties settings){this.frame=frame;this.db=db;this.data=data;this.settingsFile=settingsFile;this.settings=settings;timer=new javax.swing.Timer(600000,e->automatic());}
    void start(){timer.start();frame.addWindowListener(new java.awt.event.WindowAdapter(){public void windowClosed(java.awt.event.WindowEvent e){timer.stop();}});}
    void stop(){timer.stop();}
    <T> T work(String title,Callable<T> action)throws Exception {
        var dialog=new JDialog(frame,title,true);dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);var panel=new JPanel(new BorderLayout(8,8));panel.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));panel.add(new JLabel("Please wait while RealtorDesk copies and verifies your data."),BorderLayout.NORTH);var bar=new JProgressBar();bar.setIndeterminate(true);panel.add(bar);dialog.setContentPane(panel);dialog.pack();dialog.setLocationRelativeTo(frame);
        var result=new java.util.concurrent.atomic.AtomicReference<T>();var failure=new java.util.concurrent.atomic.AtomicReference<Exception>();
        new SwingWorker<T,Void>(){protected T doInBackground()throws Exception{return action.call();}protected void done(){try{result.set(get());}catch(Exception e){Throwable cause=e;while(cause.getCause()!=null)cause=cause.getCause();failure.set(cause instanceof Exception ex?ex:new Exception(cause));}finally{dialog.dispose();}}}.execute();dialog.setVisible(true);if(failure.get()!=null)throw failure.get();return result.get();
    }
    void automatic(){if(!Boolean.parseBoolean(settings.getProperty("automaticBackups","true")))return;for(Window window:Window.getWindows())if(window instanceof Dialog dialog&&dialog.isModal()&&dialog.isVisible())return;
        try{String current=BackupStore.fingerprint(db,data);if(current.equals(fingerprint))return;work("Automatic backup",()->{BackupStore.create(db,data,BackupStore.name(data,true),true);BackupStore.retainAutomatic(data);return null;});fingerprint=BackupStore.fingerprint(db,data);}catch(Exception e){JOptionPane.showMessageDialog(frame,"Automatic backup failed: "+e.getMessage(),"Backups",JOptionPane.WARNING_MESSAGE);}
    }
    boolean manage(){
        while(true)try {
            var automatic=new JCheckBox("Automatic backup every 10 minutes when data changes",Boolean.parseBoolean(settings.getProperty("automaticBackups","true")));
            var information=new JPanel(new GridLayout(0,1,4,4));information.add(automatic);information.add(new JLabel("Keep 5 automatic backups. Manual backups are kept until you delete them."));information.add(new JLabel("Database, documents and saved files are included. Folder: "+data.resolve("backups")));
            int choice=JOptionPane.showOptionDialog(frame,information,"Backups and restore",JOptionPane.DEFAULT_OPTION,JOptionPane.PLAIN_MESSAGE,null,new String[]{"Create backup","Restore backup","Open folder","Close"},"Create backup");
            var updated=new Properties();updated.putAll(settings);updated.setProperty("automaticBackups",Boolean.toString(automatic.isSelected()));AppSettings.save(settingsFile,updated);settings.clear();settings.putAll(updated);
            if(choice<0||choice==3)return false;
            if(choice==2){Files.createDirectories(data.resolve("backups"));Desktop.getDesktop().open(data.resolve("backups").toFile());continue;}
            var picker=new JFileChooser(data.resolve("backups").toFile());picker.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("RealtorDesk backup","rdbackup"));
            if(choice==0){picker.setSelectedFile(BackupStore.name(data,false).toFile());if(picker.showSaveDialog(frame)!=JFileChooser.APPROVE_OPTION)continue;Path path=picker.getSelectedFile().toPath();if(!path.toString().endsWith(".rdbackup"))path=Path.of(path+".rdbackup");Path archive=path;work("Creating backup",()->BackupStore.create(db,data,archive));fingerprint=BackupStore.fingerprint(db,data);JOptionPane.showMessageDialog(frame,"Backup saved:\n"+archive);}
            else {
                if(picker.showOpenDialog(frame)!=JFileChooser.APPROVE_OPTION)continue;Path archive=picker.getSelectedFile().toPath();var destination=new JFileChooser(data.toFile());destination.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);destination.setDialogTitle("Choose a separate empty folder for restored data");if(destination.showOpenDialog(frame)!=JFileChooser.APPROVE_OPTION)continue;
                if(JOptionPane.showConfirmDialog(frame,"Restore this backup and reload the app?\n\n"+archive+"\n\nCurrent data will be retained at:\n"+data,"Restore backup",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.YES_OPTION)continue;
                work("Verifying and restoring backup",()->{BackupStore.create(db,data,BackupStore.name(data,false));return BackupStore.restore(archive,destination.getSelectedFile().toPath(),data,settingsFile,settings);});return true;
            }
        }catch(Exception e){JOptionPane.showMessageDialog(frame,e.getMessage(),"Backup or restore failed",JOptionPane.ERROR_MESSAGE);}
    }
}
