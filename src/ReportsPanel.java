import java.awt.*;
import java.sql.Connection;
import java.time.*;
import java.time.format.TextStyle;
import java.util.Locale;
import javax.swing.*;
import javax.swing.table.*;

final class ReportsPanel extends JPanel {
    final Connection db;
    final JComboBox<String> period=new JComboBox<>(new String[]{"All time","Monthly","Quarterly","Yearly","Custom range"});
    final JComboBox<String> grouping=new JComboBox<>(new String[]{"Overall","By city"});
    final JComboBox<String> month=new JComboBox<>();
    final JComboBox<String> quarter=new JComboBox<>(new String[]{"Q1 (Jan-Mar)","Q2 (Apr-Jun)","Q3 (Jul-Sep)","Q4 (Oct-Dec)"});
    final JSpinner year=new JSpinner(new SpinnerNumberModel(LocalDate.now().getYear(),1,9999,1));
    final DatePicker from=new DatePicker(LocalDate.now().withDayOfYear(1).toString(),"Report start date"),to=new DatePicker(LocalDate.now().toString(),"Report end date");
    final JLabel status=new JLabel();
    final DefaultTableModel model=new DefaultTableModel(new String[]{"Group / City","Start to Offer (days)","Clients (S-O)","Offer to Close (days)","Clients (O-C)","Start to Close (days)","Clients (S-C)","Avg Closed Price","Sold homes"},0) {
        public boolean isCellEditable(int r,int c){return false;}
        public Class<?> getColumnClass(int c){return c==0?String.class:c==1||c==3||c==5?Double.class:c==7?java.math.BigDecimal.class:Long.class;}
    };
    ReportsPanel(Connection db) {
        super(new BorderLayout(8,8));this.db=db;
        for(Month m:Month.values())month.addItem(m.getDisplayName(TextStyle.FULL,Locale.getDefault()));month.setSelectedIndex(LocalDate.now().getMonthValue()-1);quarter.setSelectedIndex((LocalDate.now().getMonthValue()-1)/3);year.setEditor(new JSpinner.NumberEditor(year,"#"));
        var filters=new JPanel(new FlowLayout(FlowLayout.LEFT));filters.add(new JLabel("Period:"));filters.add(period);filters.add(new JLabel("Month:"));filters.add(month);filters.add(new JLabel("Quarter:"));filters.add(quarter);filters.add(new JLabel("Year:"));filters.add(year);filters.add(new JLabel("Group:"));filters.add(grouping);
        var dates=new JPanel(new FlowLayout(FlowLayout.LEFT));dates.add(new JLabel("From:"));dates.add(from);dates.add(new JLabel("To:"));dates.add(to);var run=new JButton("Run report");run.addActionListener(e->refresh());dates.add(run);
        var header=new JPanel(new GridLayout(0,1));header.add(filters);header.add(dates);add(header,BorderLayout.NORTH);
        var table=new JTable(model);table.setAutoCreateRowSorter(true);table.setRowHeight(28);table.setFillsViewportHeight(true);table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for(int i=0;i<table.getColumnCount();i++)table.getColumnModel().getColumn(i).setPreferredWidth(i==0?170:i==7?150:135);
        table.setDefaultRenderer(Double.class,new DefaultTableCellRenderer(){protected void setValue(Object value){setHorizontalAlignment(SwingConstants.CENTER);setText(value==null?"—":String.format(Locale.getDefault(),"%.1f",value));}});
        table.setDefaultRenderer(java.math.BigDecimal.class,new DefaultTableCellRenderer(){protected void setValue(Object value){setHorizontalAlignment(SwingConstants.CENTER);setText(value==null?"—":java.text.NumberFormat.getCurrencyInstance(Locale.US).format(value));}});
        add(new JScrollPane(table));
        var explanation=new JTextArea("Timings count each client once overall (once per city when grouped). Prices count each Sold/Closed property with a recorded Closed Price.\nRange uses Offer date for Start to Offer; Close date for the other metrics. Property closing dates come from the linked client's Key Dates.\nMissing or reversed date pairs are excluded. Sold properties without a linked Close date appear only in All time. A dash means no qualifying data.\nCity groups use linked property cities; clients without one appear under Unspecified city. Clients in multiple cities can appear in multiple city groups.");
        explanation.setEditable(false);explanation.setOpaque(false);explanation.setFont(UIManager.getFont("Label.font"));explanation.setLineWrap(true);explanation.setWrapStyleWord(true);explanation.setRows(5);
        var footer=new JPanel(new BorderLayout(4,4));footer.add(status,BorderLayout.NORTH);footer.add(explanation);add(footer,BorderLayout.SOUTH);
        period.addActionListener(e->updateControls());updateControls();refresh();
    }
    static void enabled(Component component,boolean enabled){component.setEnabled(enabled);if(component instanceof Container container)for(var child:container.getComponents())enabled(child,enabled);}
    void updateControls(){int p=period.getSelectedIndex();month.setEnabled(p==1);quarter.setEnabled(p==2);enabled(year,p>=1&&p<=3);enabled(from,p==4);enabled(to,p==4);}
    ReportStore.Range range(){int y=(Integer)year.getValue();return switch(period.getSelectedIndex()){case 1->ReportStore.Range.month(y,month.getSelectedIndex()+1);case 2->ReportStore.Range.quarter(y,quarter.getSelectedIndex()+1);case 3->ReportStore.Range.year(y);case 4->{if(from.date().isEmpty()||to.date().isEmpty())throw new IllegalArgumentException("Choose both From and To dates.");yield new ReportStore.Range(LocalDate.parse(from.date()),LocalDate.parse(to.date()));}default->ReportStore.Range.all();};}
    void refresh(){try{var range=range();var rows=ReportStore.run(db,range,grouping.getSelectedIndex()==1);model.setRowCount(0);for(var row:rows)model.addRow(new Object[]{row.group(),row.startOffer(),row.startOfferCount(),row.offerClose(),row.offerCloseCount(),row.startClose(),row.startCloseCount(),row.price(),row.homes()});status.setText("Report: "+range+" | "+grouping.getSelectedItem()+(rows.isEmpty()?" | No matching records":""));}catch(Exception e){JOptionPane.showMessageDialog(this,e.getMessage(),"Report could not run",JOptionPane.WARNING_MESSAGE);}}
}
