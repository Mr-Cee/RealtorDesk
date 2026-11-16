import java.awt.*;
import java.awt.image.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;

final class ModernUITest {
    static int checks;static void check(boolean value){if(!value)throw new AssertionError("Modern UI check "+checks);checks++;}
    static int run()throws Exception {
        SwingUtilities.invokeAndWait(()->{try(var db=java.sql.DriverManager.getConnection("jdbc:sqlite::memory:");var s=db.createStatement()){
            s.execute("CREATE TABLE clients(id INTEGER PRIMARY KEY,name TEXT,phone TEXT,email TEXT,date_start TEXT,date_offer TEXT,date_close TEXT)");
            s.execute("CREATE TABLE properties(id INTEGER PRIMARY KEY,client_id INTEGER,address TEXT,mls_number TEXT,city TEXT,state TEXT,status TEXT,closed_price_cents INTEGER)");
            s.execute("INSERT INTO clients VALUES(1,'Jamie Smith','907-555-0100','jamie@example.test','2026-01-01','2026-01-20','2026-02-20')");
            s.execute("INSERT INTO properties VALUES(1,1,'12 Oak Street','MLS-1024','Anchorage','AK','Closed',42500000)");TaskStore.migrate(db);TaskStore.save(db,new TaskStore.Task(null,"Call Jamie about inspection",1L,1L,"2026-11-20","High","",false,true,null));
            for(var theme:AppTheme.values()){
                theme.apply();var tabs=AppShell.tabs();for(String section:new String[]{"Clients","Properties","Documents","Tasks","Reports"})tabs.add(section,section.equals("Tasks")?new TasksPanel(db):section.equals("Reports")?new ReportsPanel(db):sample(section));
                var shell=new AppShell(tabs,"0.2.0","Example data folder");shell.setSize(1360,760);layout(shell);
                check(shell.heading.getText().equals("Clients"));shell.links.get(3).doClick();check(tabs.getSelectedIndex()==3);check(shell.heading.getText().equals("Tasks"));check(shell.links.get(3).isSelected());
                shell.getActionMap().get("section1").actionPerformed(null);check(tabs.getSelectedIndex()==1);check(shell.links.get(1).isSelected());
                var table=find(tabs.getComponentAt(1),JTable.class);check(table.getRowHeight()==40);check(!table.getShowVerticalLines());check(table.getRowSorter()!=null);
                var search=find(tabs.getComponentAt(1),JTextField.class);search.setText("Oak");check(search.getText().equals("Oak"));check(search.getBorder() instanceof ModernUI.RoundedBorder);
                var combo=new JComboBox<>(new String[]{"Lead","Closed"});check(combo.getUI() instanceof ModernUI.ComboUI);var button=new JButton("Add client");check(button.getUI() instanceof ModernUI.ButtonUI);
                shell.setSize(1100,680);layout(shell);var toolbar=find(tabs.getComponentAt(1),JButton.class).getParent();check(toolbar.getLayout() instanceof WrapLayout);check(toolbar.getHeight()>=toolbar.getPreferredSize().height);
                check(table.getColumnModel().getColumn(2).getCellRenderer() instanceof ModernUI.StatusRenderer);
                shell.links.get(4).doClick();layout(shell);layout(shell);check(find(tabs.getComponentAt(4),JButton.class)!=null);check(((ReportsPanel)tabs.getComponentAt(4)).model.getRowCount()==1);
                if(theme==AppTheme.CLASSIC||theme==AppTheme.MIDNIGHT||theme==AppTheme.OCEAN){shell.setSize(1360,820);shell.links.get(0).doClick();layout(shell);save(shell,"build/modern-"+theme.id+".png");if(theme==AppTheme.CLASSIC){shell.setSize(1100,680);shell.links.get(4).doClick();layout(shell);layout(shell);save(shell,"build/modern-reports.png");shell.links.get(3).doClick();layout(shell);save(shell,"build/modern-tasks.png");}}
            }
            AppTheme.CLASSIC.apply();
        }catch(Exception e){throw new RuntimeException(e);}});return checks;
    }
    static void save(JComponent c,String path)throws Exception{var image=new BufferedImage(c.getWidth(),c.getHeight(),BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();c.printAll(g);g.dispose();ImageIO.write(image,"png",Path.of(path).toFile());}
    static JPanel sample(String section){
        var panel=new JPanel(new BorderLayout(8,12));var toolbar=new JPanel(new FlowLayout(FlowLayout.LEFT));for(String title:new String[]{"Add client","Refresh","Edit selected","Delete selected","View details","Checklist"})toolbar.add(new JButton(title));toolbar.add(new JCheckBox("Include closed",true));
        var header=new JPanel(new BorderLayout(0,12));header.add(toolbar,BorderLayout.NORTH);var search=new JTextField(25);header.add(DocumentsPanel.searchBar("Search clients:",search),BorderLayout.SOUTH);panel.add(header,BorderLayout.NORTH);
        var table=new JTable(new Object[][]{{"Jamie Smith","Buyer","Looking for Home","12 Oak Street","(907) 555-0100"},{"Alex Morgan","Seller","Offered","20 Pine Avenue","(907) 555-0120"},{"Taylor Lee","Buyer","Lead","","(907) 555-0140"},{"Jordan Rivera","Seller","Closed","55 Meadow Lane","(907) 555-0150"}},new String[]{"Client","Type","Status","Property","Phone"});table.setFillsViewportHeight(true);table.setAutoCreateRowSorter(true);var scroll=new JScrollPane(table);scroll.setColumnHeaderView(table.getTableHeader());panel.add(scroll);var footer=new JLabel("4 of 4 clients");footer.setBorder(BorderFactory.createEmptyBorder(8,2,2,2));panel.add(footer,BorderLayout.SOUTH);return panel;
    }
    static <T> T find(Component root,Class<T> type){if(type.isInstance(root))return type.cast(root);if(root instanceof Container c)for(Component child:c.getComponents()){T result=find(child,type);if(result!=null)return result;}return null;}
    static void layout(Container root){root.doLayout();for(Component child:root.getComponents())if(child instanceof Container container)layout(container);}
}
