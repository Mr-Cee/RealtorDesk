import java.awt.*;
import java.sql.Connection;
import java.util.*;
import javax.swing.*;

final class PropertyEditor {
    static PropertyStore.Property edit(Component parent,Connection db,PropertyStore.Property original,java.util.List<PropertyStore.Property> drafts) {
        PropertyAddress initial=original==null?PropertyAddress.legacy(""):original.parts()==null?PropertyAddress.legacy(original.address()):original.parts();
        String[] values={initial.number(),initial.direction(),initial.street(),initial.type(),initial.unit(),initial.city(),initial.state(),initial.zip(),original==null?"":original.status(),original==null?"":original.notes()};
        String[] labels={"Street number *","Direction","Street name *","Street type","Unit / Apt","City *","State *","ZIP code *","Status","Notes"};
        var fields=new JTextField[values.length];var form=new JPanel(new GridLayout(0,2,8,8));
        for(int i=0;i<fields.length;i++){fields[i]=new JTextField(values[i],28);var label=new JLabel(labels[i]);label.setLabelFor(fields[i]);form.add(label);form.add(fields[i]);}
        if(original!=null){var previous=new JTextField(original.address(),28);previous.setEditable(false);form.add(new JLabel("Address on file"));form.add(previous);}
        while(JOptionPane.showConfirmDialog(parent,form,"Property",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION) {
            try {
                var address=new PropertyAddress(fields[0].getText(),fields[1].getText(),fields[2].getText(),fields[3].getText(),fields[4].getText(),fields[5].getText(),fields[6].getText(),fields[7].getText());address.validate();
                var candidate=new PropertyStore.Property(original==null?null:original.id(),original==null?null:original.clientId(),address.formatted(),fields[8].getText().trim(),fields[9].getText().trim(),address);
                if(Duplicates.confirm(parent,"properties",Duplicates.properties(db,candidate,drafts.stream().filter(p->p!=original).toList())))return candidate;
            }catch(Exception e){JOptionPane.showMessageDialog(parent,e.getMessage(),"Check property",JOptionPane.WARNING_MESSAGE);}
        }return null;
    }
}
