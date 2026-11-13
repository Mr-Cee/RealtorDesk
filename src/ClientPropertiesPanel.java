import java.awt.*;
import java.sql.Connection;
import java.util.*;
import javax.swing.*;

/** Draft property changes are committed together with the client, never by the buttons. */
final class ClientPropertiesPanel extends JPanel {
    final DefaultListModel<PropertyStore.Property> model=new DefaultListModel<>();
    final JList<PropertyStore.Property> list=new JList<>(model);
    final Connection db;
    ClientPropertiesPanel(Connection db,Long clientId)throws Exception {
        super(new BorderLayout());this.db=db;setBorder(BorderFactory.createTitledBorder("Linked properties"));
        if(clientId!=null)for(var p:PropertyStore.list(db,clientId))model.addElement(p);
        list.setVisibleRowCount(3);add(new JScrollPane(list));var buttons=new JPanel(new FlowLayout(FlowLayout.LEFT));
        var attach=new JButton("Attach existing");attach.addActionListener(e->safe(()->{
            var available=new ArrayList<>(PropertyStore.list(db,null));available.removeIf(p->drafts().stream().anyMatch(d->Objects.equals(d.id(),p.id())));
            if(available.isEmpty()){JOptionPane.showMessageDialog(this,"No unassigned properties. Create one here or on the Properties tab.");return;}
            var chosen=(PropertyStore.Property)JOptionPane.showInputDialog(this,"Choose an unassigned property","Attach property",JOptionPane.QUESTION_MESSAGE,null,available.toArray(),available.get(0));if(chosen!=null)model.addElement(chosen);
        }));buttons.add(attach);
        var create=new JButton("New property");create.addActionListener(e->{var p=edit(this,null);if(p!=null)model.addElement(p);});buttons.add(create);
        var edit=new JButton("Edit property");edit.addActionListener(e->{int i=list.getSelectedIndex();if(i<0)return;var p=edit(this,model.get(i));if(p!=null)model.set(i,p);});buttons.add(edit);
        var detach=new JButton("Detach");detach.addActionListener(e->{int i=list.getSelectedIndex();if(i>=0)model.remove(i);});buttons.add(detach);
        add(buttons,BorderLayout.SOUTH);
    }
    java.util.List<PropertyStore.Property> drafts(){return Collections.list(model.elements());}
    static PropertyStore.Property edit(Component parent,PropertyStore.Property original) {
        var address=new JTextField(original==null?"":original.address(),30);var status=new JTextField(original==null?"":original.status(),30);var notes=new JTextField(original==null?"":original.notes(),30);
        var form=new JPanel(new GridLayout(0,2,8,8));form.add(new JLabel("Address *"));form.add(address);form.add(new JLabel("Status"));form.add(status);form.add(new JLabel("Notes"));form.add(notes);
        while(JOptionPane.showConfirmDialog(parent,form,"Property",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION){if(address.getText().isBlank()){JOptionPane.showMessageDialog(parent,"Enter a property address.");continue;}return new PropertyStore.Property(original==null?null:original.id(),original==null?null:original.clientId(),address.getText().trim(),status.getText().trim(),notes.getText().trim());}return null;
    }
    interface Work{void run()throws Exception;}
    void safe(Work work){try{work.run();}catch(Exception e){JOptionPane.showMessageDialog(this,e.getMessage(),"Properties",JOptionPane.ERROR_MESSAGE);}}
}
