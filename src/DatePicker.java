import java.awt.*;
import java.time.*;
import java.time.format.TextStyle;
import java.util.Locale;
import javax.swing.*;

/** Optional date field with a month calendar; the value changes only on confirmation. */
final class DatePicker extends JPanel {
    private LocalDate value;
    private final JTextField display=new JTextField(12);
    DatePicker(String initial) {
        super(new FlowLayout(FlowLayout.LEFT,4,0));
        value=initial==null||initial.isBlank()?null:LocalDate.parse(initial);
        display.setEditable(false);display.setToolTipText("Choose the date you last communicated with this client.");
        var choose=new JButton("Choose date...");choose.addActionListener(e->choose());
        var clear=new JButton("Clear");clear.addActionListener(e->setDate(null));
        add(display);add(choose);add(clear);setDate(value);
    }
    String date(){return value==null?"":value.toString();}
    void setDate(LocalDate date){value=date;display.setText(date());}
    private void choose() {
        var calendar=new CalendarPanel(value);
        if(JOptionPane.showConfirmDialog(this,calendar,"Date Last Communicated",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION)
            setDate(calendar.selected);
    }
    static final class CalendarPanel extends JPanel {
        LocalDate selected;
        final JComboBox<String> month=new JComboBox<>();
        final JSpinner year;
        final JPanel days=new JPanel(new GridLayout(0,7,3,3));
        CalendarPanel(LocalDate initial) {
            super(new BorderLayout(8,8));selected=initial==null?LocalDate.now():initial;
            for(Month m:Month.values())month.addItem(m.getDisplayName(TextStyle.FULL,Locale.getDefault()));
            month.setSelectedIndex(selected.getMonthValue()-1);
            year=new JSpinner(new SpinnerNumberModel(selected.getYear(),1,9999,1));
            year.setEditor(new JSpinner.NumberEditor(year,"#"));
            var navigation=new JPanel(new FlowLayout());var previous=new JButton("<");var next=new JButton(">");
            previous.setToolTipText("Previous month");next.setToolTipText("Next month");
            previous.addActionListener(e->navigate(-1));next.addActionListener(e->navigate(1));
            navigation.add(previous);navigation.add(month);navigation.add(year);navigation.add(next);
            month.addActionListener(e->render());year.addChangeListener(e->render());
            var today=new JButton("Today");today.addActionListener(e->{selected=LocalDate.now();showMonth(YearMonth.from(selected));});
            add(navigation,BorderLayout.NORTH);add(days,BorderLayout.CENTER);add(today,BorderLayout.SOUTH);render();
        }
        YearMonth displayed(){return YearMonth.of((Integer)year.getValue(),month.getSelectedIndex()+1);}
        void showMonth(YearMonth target){year.setValue(target.getYear());month.setSelectedIndex(target.getMonthValue()-1);render();}
        void navigate(int delta){YearMonth target=displayed().plusMonths(delta);if(target.getYear()>=1&&target.getYear()<=9999)showMonth(target);}
        void render() {
            days.removeAll();
            for(DayOfWeek day:DayOfWeek.values()){var label=new JLabel(day.getDisplayName(TextStyle.SHORT,Locale.getDefault()),SwingConstants.CENTER);days.add(label);}
            YearMonth shown=displayed();int offset=shown.atDay(1).getDayOfWeek().getValue()-1;
            for(int i=0;i<42;i++) {
                int number=i-offset+1;
                if(number<1||number>shown.lengthOfMonth()){days.add(new JLabel());continue;}
                LocalDate date=shown.atDay(number);var day=new JToggleButton(Integer.toString(number),date.equals(selected));
                day.setToolTipText(date.toString());day.addActionListener(e->{selected=date;render();});days.add(day);
            }
            days.revalidate();days.repaint();
        }
    }
}
