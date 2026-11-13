import java.text.Normalizer;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;

final class ClientSearch {
    static String normalize(String value){return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT);}
    static void attach(JTable table,JTextField search,JLabel count) {
        int[] columns=java.util.stream.IntStream.range(1,table.getModel().getColumnCount()).toArray();
        attach(table,search,count,"clients","","Search names, seller address, phone, email, client type, communication date, status or notes. Use multiple words to narrow results.",columns);
    }
    static void attach(JTable table,JTextField search,JLabel count,String noun,String emptyMessage,String tooltip,int... columns) {
        @SuppressWarnings("unchecked") var sorter=(TableRowSorter<TableModel>)table.getRowSorter();
        Runnable updateCount=()->{
            int total=table.getModel().getRowCount(),visible=table.getRowCount();
            count.setText(total==0&&!emptyMessage.isEmpty()?emptyMessage:visible+" of "+total+" "+noun+(visible==0&&total>0?". No matches.":""));
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
                            for(int column:columns) {
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
        search.setToolTipText(tooltip);
        updateCount.run();
    }
}
