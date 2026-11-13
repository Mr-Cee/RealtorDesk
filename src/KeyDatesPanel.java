import java.awt.*;
import javax.swing.*;

final class KeyDatesPanel extends JPanel {
    final DatePicker start,offer,close;
    KeyDatesPanel(KeyDates dates) {
        super(new GridLayout(0,2,8,8));setBorder(BorderFactory.createTitledBorder("Key Dates"));
        start=new DatePicker(dates.start(),"Start date");offer=new DatePicker(dates.offer(),"Offer date");close=new DatePicker(dates.close(),"Close date");
        add(new JLabel("Start"));add(start);add(new JLabel("Offer"));add(offer);add(new JLabel("Close"));add(close);
    }
    KeyDates dates(){return new KeyDates(start.date(),offer.date(),close.date());}
}
