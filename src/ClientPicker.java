import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/** Searchable client chooser; no database changes happen in this dialog. */
final class ClientPicker extends JPanel {
    record Client(long id,String name,String phone,String email) { }
    final DefaultTableModel model=new DefaultTableModel(new String[]{"ID","Client","Phone","Email"},0){public boolean isCellEditable(int r,int c){return false;}};
    final JTable table=new JTable(model);
    final JTextField search=new JTextField(28);
    final JLabel count=new JLabel();
    final JButton add=new JButton("Add"),cancel=new JButton("Cancel");
    Client selected;
    Runnable close=()->{};
    static java.util.List<Client> clients(Connection db)throws SQLException {
        var result=new ArrayList<Client>();
        try(var s=db.createStatement();var r=s.executeQuery("SELECT id,name,phone,email FROM clients ORDER BY name COLLATE NOCASE,id")){while(r.next())result.add(new Client(r.getLong(1),r.getString(2),Objects.toString(r.getString(3),""),Objects.toString(r.getString(4),"")));}
        return result;
    }
    ClientPicker(java.util.List<Client> clients) {
        super(new BorderLayout(8,8));setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        table.setAutoCreateRowSorter(true);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);table.setRowHeight(28);table.setFillsViewportHeight(true);ClientTableActions.hideId(table);
        for(var client:clients)model.addRow(new Object[]{client.id(),client.name(),client.phone(),client.email()});
        ClientSearch.attach(table,search,count,"clients","No clients yet. Add a client on the Clients tab first.","Search client names, phone or email as you type.",1,2,3);
        add(DocumentsPanel.searchBar("Search clients:",search),BorderLayout.NORTH);add(new JScrollPane(table));
        var footer=new JPanel(new BorderLayout());footer.add(count);var buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));buttons.add(add);buttons.add(cancel);footer.add(buttons,BorderLayout.EAST);add(footer,BorderLayout.SOUTH);
        add.setEnabled(false);table.getSelectionModel().addListSelectionListener(e->add.setEnabled(table.getSelectedRow()>=0));
        add.addActionListener(e->addSelected());cancel.addActionListener(e->cancel());
        table.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){if(SwingUtilities.isLeftMouseButton(e)&&e.getClickCount()==2){int row=table.rowAtPoint(e.getPoint());if(row>=0){table.setRowSelectionInterval(row,row);addSelected();}}}});
        table.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"addClient");table.getActionMap().put("addClient",new AbstractAction(){public void actionPerformed(ActionEvent e){addSelected();}});
    }
    void addSelected(){int view=table.getSelectedRow();if(view<0)return;int row=table.convertRowIndexToModel(view);selected=new Client(((Number)model.getValueAt(row,0)).longValue(),(String)model.getValueAt(row,1),(String)model.getValueAt(row,2),(String)model.getValueAt(row,3));close.run();}
    void cancel(){selected=null;close.run();}
    static Client show(Component parent,Connection db)throws SQLException {
        var picker=new ClientPicker(clients(db));var dialog=new JDialog(SwingUtilities.getWindowAncestor(parent),"Add linked client",Dialog.ModalityType.APPLICATION_MODAL);
        picker.close=dialog::dispose;dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);dialog.setContentPane(picker);dialog.getRootPane().setDefaultButton(picker.add);
        dialog.getRootPane().registerKeyboardAction(e->picker.cancel(),KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setSize(720,450);dialog.setLocationRelativeTo(parent);SwingUtilities.invokeLater(()->picker.search.requestFocusInWindow());dialog.setVisible(true);return picker.selected;
    }
}
