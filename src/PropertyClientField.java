import java.sql.*;
import java.util.Objects;
import java.util.ArrayList;
import java.awt.*;
import javax.swing.*;
import javax.swing.event.*;

/** Client assignment is part of the property form, with IDs kept behind the labels. */
final class PropertyClientField extends JComboBox<PropertyClientField.Choice> {
    record Choice(Long id,String label,String searchText){public String toString(){return label;}}
    final JTextField search=new JTextField(18);
    final JLabel count=new JLabel();
    final java.util.List<Choice> choices=new ArrayList<>();
    PropertyClientField(Connection db,Long existing)throws SQLException {
        var unassigned=new Choice(null,"Unassigned","");choices.add(unassigned);addItem(unassigned);Choice selected=null;
        try(var s=db.createStatement();var r=s.executeQuery("SELECT id,name,phone,email FROM clients ORDER BY name,id")) {
            while(r.next()) {
                String contact=Objects.toString(r.getString(3),"");if(contact.isBlank())contact=Objects.toString(r.getString(4),"");
                var item=new Choice(r.getLong(1),r.getString(2)+(contact.isBlank()?"":" | "+contact)+" (#"+r.getLong(1)+")",r.getString(2)+" "+Objects.toString(r.getString(3),"")+" "+Objects.toString(r.getString(4),""));choices.add(item);addItem(item);
                if(Objects.equals(existing,item.id()))selected=item;
            }
        }
        if(existing!=null&&selected==null)throw new SQLException("The linked client no longer exists. Refresh the property list.");
        if(selected!=null)setSelectedItem(selected);
        setMaximumRowCount(12);
        search.setToolTipText("Filter clients by name, phone or email, then choose a matching client below.");
        search.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){filter();}public void removeUpdate(DocumentEvent e){filter();}public void changedUpdate(DocumentEvent e){filter();}});
        filter();
        setToolTipText("Keep the current client, choose another client, or leave Unassigned.");
    }
    Long clientId(){return ((Choice)getSelectedItem()).id();}
    JPanel searchPanel() {
        var panel=new JPanel(new BorderLayout(4,0));var actions=new JPanel(new FlowLayout(FlowLayout.RIGHT,4,0));var clear=new JButton("Clear");
        clear.addActionListener(e->{search.setText("");search.requestFocusInWindow();});actions.add(clear);actions.add(count);panel.add(search);panel.add(actions,BorderLayout.EAST);return panel;
    }
    void filter() {
        Choice selected=(Choice)getSelectedItem();var model=new DefaultComboBoxModel<Choice>();model.addElement(choices.get(0));
        String query=ClientSearch.normalize(search.getText()).trim();int matches=0;
        for(var item:choices)if(item.id()!=null&&matches(item.searchText(),query)){model.addElement(item);matches++;}
        // Filtering must never assign the first match or detach a client hidden by the query.
        model.setSelectedItem(selected==null?choices.get(0):selected);setModel(model);
        count.setText(matches+" of "+(choices.size()-1)+" clients"+(matches==0?". No matches.":""));
    }
    static boolean matches(String text,String query) {
        String normalized=ClientSearch.normalize(text);
        for(String word:query.split("\\s+")) {
            if(normalized.contains(word))continue;
            String digits=word.replaceAll("\\D","");
            if(word.matches("[+()0-9.\\-]+")&&digits.length()>=3&&normalized.replaceAll("\\D","").contains(digits))continue;
            return false;
        }return true;
    }
}
