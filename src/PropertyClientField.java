import java.awt.*;
import java.sql.*;
import javax.swing.*;

/** A pending assignment; the property editor commits it only when saved. */
final class PropertyClientField extends JPanel {
    final JTextField display=new JTextField(28);
    final JButton action=new JButton();
    final Connection db;
    private ClientPicker.Client selected;
    PropertyClientField(Connection db,Long existing)throws SQLException {
        super(new BorderLayout(6,0));this.db=db;display.setEditable(false);add(display);add(action,BorderLayout.EAST);
        if(existing!=null){selected=ClientPicker.clients(db).stream().filter(c->c.id()==existing).findFirst().orElseThrow(()->new SQLException("The linked client no longer exists. Refresh the property list."));}
        update();action.addActionListener(e->{if(selected!=null){assign(null);return;}try{var chosen=ClientPicker.show(this,db);if(chosen!=null)assign(chosen);}catch(SQLException problem){JOptionPane.showMessageDialog(this,problem.getMessage(),"Clients",JOptionPane.ERROR_MESSAGE);}});
    }
    Long clientId(){return selected==null?null:selected.id();}
    void assign(ClientPicker.Client client){selected=client;update();}
    private void update(){display.setText(selected==null?"":selected.name()+(selected.phone().isBlank()?"":" | "+selected.phone()));display.setCaretPosition(0);action.setText(selected==null?"Add":"Remove");display.setToolTipText(selected==null?"No linked client":selected.name()+" | "+selected.phone()+" | "+selected.email());}
}
