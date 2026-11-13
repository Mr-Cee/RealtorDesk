import java.sql.*;
import java.util.*;
import java.awt.Component;
import javax.swing.*;

final class Duplicates {
    static String phone(String value){String digits=value==null?"":value.replaceAll("\\D","");return digits.length()==11&&digits.startsWith("1")?digits.substring(1):digits;}
    static java.util.List<String> clients(Connection db,Long id,String first,String last,String phone,String email)throws SQLException {
        var found=new ArrayList<String>();String name=PropertyAddress.norm(ClientStore.fullName(first,last)),digits=phone(phone),mail=PropertyAddress.clean(email).toLowerCase(Locale.ROOT);
        try(var s=db.createStatement();var r=s.executeQuery("SELECT id,name,phone,email FROM clients")){while(r.next()) {
            if(Objects.equals(id,r.getLong(1)))continue;var reasons=new ArrayList<String>();
            if(!name.isEmpty()&&name.equals(PropertyAddress.norm(r.getString(2))))reasons.add("same name");
            if(digits.length()>=7&&digits.equals(phone(r.getString(3))))reasons.add("same phone");
            if(!mail.isEmpty()&&mail.equals(PropertyAddress.clean(r.getString(4)).toLowerCase(Locale.ROOT)))reasons.add("same email");
            if(!reasons.isEmpty())found.add(r.getString(2)+" | "+Objects.toString(r.getString(3),"")+" | "+Objects.toString(r.getString(4),"")+" (#"+r.getLong(1)+"): "+String.join(", ",reasons));
        }}return found;
    }
    static boolean same(PropertyStore.Property a,PropertyStore.Property b) {
        var left=a.parts()==null?PropertyAddress.legacy(a.address()):a.parts();var right=b.parts()==null?PropertyAddress.legacy(b.address()):b.parts();
        return left.complete()&&right.complete()?left.key().equals(right.key()):PropertyAddress.norm(a.address()).equals(PropertyAddress.norm(b.address()));
    }
    static java.util.List<String> properties(Connection db,PropertyStore.Property candidate,java.util.List<PropertyStore.Property> drafts)throws SQLException {
        var found=new LinkedHashSet<String>();
        try(var s=db.createStatement();var r=s.executeQuery("SELECT "+PropertyStore.COLUMNS+" FROM properties")){while(r.next()){var item=PropertyStore.read(r);if(!Objects.equals(candidate.id(),item.id())&&same(candidate,item))found.add(item.address()+" (#"+item.id()+")");}}
        for(var item:drafts)if(item!=candidate&&(candidate.id()==null||!Objects.equals(candidate.id(),item.id()))&&same(candidate,item))found.add(item.address()+" (in client editor)");return new ArrayList<>(found);
    }
    static boolean confirm(Component parent,String kind,java.util.List<String> found) {
        if(found.isEmpty())return true;
        var text=new JTextArea("Possible duplicate "+kind+":\n\n"+String.join("\n",found)+"\n\nSave this record anyway?",12,65);text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);text.setCaretPosition(0);
        return JOptionPane.showConfirmDialog(parent,new JScrollPane(text),"Possible duplicates",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)==JOptionPane.YES_OPTION;
    }
}
