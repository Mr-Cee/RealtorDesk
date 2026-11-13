import java.sql.*;
import java.util.Objects;
import javax.swing.*;

/** Client assignment is part of the property form, with IDs kept behind the labels. */
final class PropertyClientField extends JComboBox<PropertyClientField.Choice> {
    record Choice(Long id,String label){public String toString(){return label;}}
    PropertyClientField(Connection db,Long existing)throws SQLException {
        addItem(new Choice(null,"Unassigned"));Choice selected=null;
        try(var s=db.createStatement();var r=s.executeQuery("SELECT id,name,phone,email FROM clients ORDER BY name,id")) {
            while(r.next()) {
                String contact=Objects.toString(r.getString(3),"");if(contact.isBlank())contact=Objects.toString(r.getString(4),"");
                var item=new Choice(r.getLong(1),r.getString(2)+(contact.isBlank()?"":" | "+contact)+" (#"+r.getLong(1)+")");addItem(item);
                if(Objects.equals(existing,item.id()))selected=item;
            }
        }
        if(existing!=null&&selected==null)throw new SQLException("The linked client no longer exists. Refresh the property list.");
        if(selected!=null)setSelectedItem(selected);
        setToolTipText("Keep the current client, choose another client, or leave Unassigned.");
    }
    Long clientId(){return ((Choice)getSelectedItem()).id();}
}
