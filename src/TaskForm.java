import java.awt.*;
import javax.swing.*;

/** Compact task editor: labels stay narrow and single-line fields keep natural heights. */
final class TaskForm extends JPanel implements Scrollable {
    private int row;
    TaskForm(){super(new GridBagLayout());setBorder(BorderFactory.createEmptyBorder(8,8,8,8));}
    void field(String name,JComponent component) {
        var label=new JLabel(name);label.setLabelFor(component);
        var left=constraints(0);left.weightx=0;left.fill=GridBagConstraints.NONE;left.anchor=GridBagConstraints.NORTHWEST;add(label,left);
        var right=constraints(1);right.weightx=1;right.fill=GridBagConstraints.HORIZONTAL;right.anchor=GridBagConstraints.NORTHWEST;
        component.setMinimumSize(new Dimension(0,component.getPreferredSize().height));add(component,right);row++;
    }
    void options(JCheckBox completed,JCheckBox remind){var left=constraints(0);left.weightx=0;add(completed,left);var right=constraints(1);right.weightx=1;right.fill=GridBagConstraints.HORIZONTAL;add(remind,right);row++;}
    private GridBagConstraints constraints(int column){var c=new GridBagConstraints();c.gridx=column;c.gridy=row;c.insets=new Insets(6,6,6,6);c.anchor=GridBagConstraints.WEST;return c;}
    JScrollPane scrollPane(Rectangle screen) {
        var scroll=new JScrollPane(this,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setPreferredSize(new Dimension(Math.min(640,Math.max(280,screen.width-120)),Math.min(getPreferredSize().height+4,Math.max(220,screen.height-180))));
        scroll.getVerticalScrollBar().setUnitIncrement(16);return scroll;
    }
    public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
    public int getScrollableUnitIncrement(Rectangle visible,int orientation,int direction){return 16;}
    public int getScrollableBlockIncrement(Rectangle visible,int orientation,int direction){return Math.max(16,visible.height-16);}
    public boolean getScrollableTracksViewportWidth(){return true;}
    public boolean getScrollableTracksViewportHeight(){return false;}
}
