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
        var create=new JButton("New property");create.addActionListener(e->{var p=PropertyEditor.edit(this,db,null,drafts());if(p!=null)model.addElement(p);});buttons.add(create);
        var edit=new JButton("Edit property");edit.addActionListener(e->{int i=list.getSelectedIndex();if(i<0)return;var p=PropertyEditor.edit(this,db,model.get(i),drafts());if(p!=null)model.set(i,p);});buttons.add(edit);
        var detach=new JButton("Detach");detach.addActionListener(e->{int i=list.getSelectedIndex();if(i>=0)model.remove(i);});buttons.add(detach);
        add(buttons,BorderLayout.SOUTH);
    }
    java.util.List<PropertyStore.Property> drafts(){return Collections.list(model.elements());}
    interface Work{void run()throws Exception;}
    void safe(Work work){try{work.run();}catch(Exception e){JOptionPane.showMessageDialog(this,e.getMessage(),"Properties",JOptionPane.ERROR_MESSAGE);}}
}
