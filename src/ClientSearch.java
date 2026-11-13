import java.text.Normalizer;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;

final class ClientSearch {
    static String normalize(String value){return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT);}
    static void attach(JTable table,JTextField search,JLabel count) {
        @SuppressWarnings("unchecked") var sorter=(TableRowSorter<TableModel>)table.getRowSorter();
        Runnable updateCount=()->{
            int total=table.getModel().getRowCount(),visible=table.getRowCount();
            count.setText(visible+" of "+total+" clients"+(visible==0&&total>0?". No matches.":""));
        };
        Runnable filter=()->{
            String query=normalize(search.getText()).trim();
            if(query.isEmpty())sorter.setRowFilter(null);
            else {
                String[] words=query.split("\\s+");
                sorter.setRowFilter(new RowFilter<TableModel,Integer>() {
                    public boolean include(Entry<? extends TableModel,? extends Integer> row) {
                        for(String word:words) {
                            boolean found=false;
                            for(int column=1;column<row.getValueCount();column++) {
                                Object value=row.getValue(column);String cell=normalize(value==null?"":value.toString());
                                if(cell.contains(word)){found=true;break;}
                                if(word.matches("[+()0-9.\\-]+")) {
                                    String digits=word.replaceAll("\\D","");
                                    if(digits.length()>=3&&cell.replaceAll("\\D","").contains(digits)){found=true;break;}
                                }
                            }
                            if(!found)return false;
                        }
                        return true;
                    }
                });
            }
            updateCount.run();
        };
        search.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){filter.run();}public void removeUpdate(DocumentEvent e){filter.run();}public void changedUpdate(DocumentEvent e){filter.run();}});
        sorter.addRowSorterListener(e->updateCount.run());
        table.getModel().addTableModelListener(e->updateCount.run());
        search.setToolTipText("Search names, seller address, phone, email, client type, communication date, status or notes. Use multiple words to narrow results.");
        updateCount.run();
    }
}
