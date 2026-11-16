import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

/** Workspace navigation remains backed by the original tabs and their refresh events. */
final class AppShell extends JPanel {
    static final String[] DESCRIPTIONS={"People, relationships, and your next opportunity.","Listings, transactions, and every detail that matters.","Client paperwork, organized and easy to find.","A clear view of what needs your attention.","Understand your business, one insight at a time."};
    final JTabbedPane tabs;final JLabel heading=new JLabel(),description=new JLabel();
    final ButtonGroup navigation=new ButtonGroup();final java.util.List<JToggleButton> links=new java.util.ArrayList<>();
    static JTabbedPane tabs(){return new JTabbedPane(){public void updateUI(){setUI(new BasicTabbedPaneUI(){protected int calculateTabAreaHeight(int placement,int runs,int maxHeight){return 0;}protected Insets getContentBorderInsets(int placement){return new Insets(0,0,0,0);}protected void paintTabArea(Graphics g,int placement,int selected){}protected void paintContentBorder(Graphics g,int placement,int selected){}});}};}
    AppShell(JTabbedPane tabs,String version,String folder){
        super(new BorderLayout(0,0));this.tabs=tabs;
        var sidebar=new JPanel(new BorderLayout(0,24));sidebar.setPreferredSize(new Dimension(190,0));sidebar.setBorder(BorderFactory.createEmptyBorder(26,16,20,16));
        var brand=new JPanel(new BorderLayout(10,4));var logo=new JLabel(new NavigationIcon(5,30));brand.add(logo,BorderLayout.WEST);var wordmark=new JLabel("RealtorDesk");wordmark.setFont(wordmark.getFont().deriveFont(Font.BOLD,19));brand.add(wordmark);sidebar.add(brand,BorderLayout.NORTH);
        var rail=new JPanel();rail.setLayout(new BoxLayout(rail,BoxLayout.Y_AXIS));var caption=new JLabel("WORKSPACE");caption.setFont(caption.getFont().deriveFont(Font.BOLD,10));caption.setBorder(BorderFactory.createEmptyBorder(4,12,16,0));rail.add(caption);
        for(int i=0;i<tabs.getTabCount();i++){
            final int index=i;var link=new JToggleButton(tabs.getTitleAt(i),new NavigationIcon(i,20)){
                protected void paintComponent(Graphics g){var a=ModernUI.graphics(g);boolean chosen=isSelected();Color accent=ModernUI.color("Button.focus");if(chosen||getModel().isRollover()){a.setColor(ModernUI.blend(ModernUI.color("Panel.background"),accent,chosen?.13f:.06f));a.fillRoundRect(0,0,getWidth(),getHeight(),10,10);}a.dispose();super.paintComponent(g);}
            };
            link.setHorizontalAlignment(SwingConstants.LEFT);link.setContentAreaFilled(false);link.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));link.setFont(link.getFont().deriveFont(Font.BOLD,14));link.setMaximumSize(new Dimension(Integer.MAX_VALUE,46));link.setAlignmentX(Component.LEFT_ALIGNMENT);link.setToolTipText(tabs.getTitleAt(i)+" (Ctrl+"+(i+1)+")");link.getAccessibleContext().setAccessibleDescription("Open "+tabs.getTitleAt(i));link.addActionListener(e->tabs.setSelectedIndex(index));navigation.add(link);links.add(link);rail.add(link);rail.add(Box.createVerticalStrut(6));
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_1+i,InputEvent.CTRL_DOWN_MASK),"section"+i);getActionMap().put("section"+i,new AbstractAction(){public void actionPerformed(ActionEvent e){tabs.setSelectedIndex(index);links.get(index).requestFocusInWindow();}});
        }
        sidebar.add(rail);var footer=new JPanel(new GridLayout(0,1,0,7));var local=new JLabel("●  Local workspace");local.setFont(local.getFont().deriveFont(12f));local.setToolTipText(folder);footer.add(local);var release=new JLabel("Version "+version);release.setFont(release.getFont().deriveFont(11f));footer.add(release);sidebar.add(footer,BorderLayout.SOUTH);add(sidebar,BorderLayout.WEST);
        var workspace=new JPanel(new BorderLayout(0,22));workspace.setBorder(BorderFactory.createEmptyBorder(28,26,20,26));var header=new JPanel(new BorderLayout(8,8));heading.setFont(heading.getFont().deriveFont(Font.BOLD,28));description.setFont(description.getFont().deriveFont(13f));header.add(heading,BorderLayout.NORTH);header.add(description);workspace.add(header,BorderLayout.NORTH);
        var card=new JPanel(new BorderLayout());card.putClientProperty("workspaceSurface",true);card.setBorder(BorderFactory.createCompoundBorder(new ModernUI.RoundedBorder(14,1,1),BorderFactory.createEmptyBorder(12,12,12,12)));card.add(tabs);workspace.add(card);add(workspace);
        tabs.addChangeListener(e->updateHeading());updateHeading();ModernUI.polish(this);
    }
    void updateHeading(){int selected=tabs.getSelectedIndex();if(selected<0)return;heading.setText(tabs.getTitleAt(selected));description.setText(DESCRIPTIONS[Math.min(selected,DESCRIPTIONS.length-1)]);links.get(selected).setSelected(true);repaint();}
    public void updateUI(){super.updateUI();SwingUtilities.invokeLater(()->{if(description!=null)description.setForeground(ModernUI.color("Label.disabledForeground"));});}
    static class NavigationIcon implements Icon {
        final int type,size;NavigationIcon(int type,int size){this.type=type;this.size=size;}
        public int getIconWidth(){return size;}public int getIconHeight(){return size;}
        public void paintIcon(Component c,Graphics g,int x,int y){var a=ModernUI.graphics(g);a.translate(x,y);a.scale(size/24.0,size/24.0);a.setColor(c instanceof AbstractButton button&&button.isSelected()?ModernUI.color("Button.focus"):ModernUI.color("Label.foreground"));a.setStroke(new BasicStroke(1.6f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            switch(type){
                case 0 -> {a.drawOval(8,3,8,8);a.drawArc(4,13,16,14,0,180);a.drawLine(4,20,4,21);a.drawLine(20,20,20,21);}
                case 1,5 -> {a.drawPolyline(new int[]{3,12,21},new int[]{11,3,11},3);a.drawRoundRect(5,10,14,11,2,2);a.drawRect(10,15,4,6);}
                case 2 -> {a.drawRoundRect(5,3,14,18,2,2);a.drawLine(8,9,16,9);a.drawLine(8,13,16,13);a.drawLine(8,17,13,17);}
                case 3 -> {a.drawRoundRect(4,3,16,18,3,3);a.drawPolyline(new int[]{8,11,16},new int[]{12,15,9},3);}
                case 4 -> {a.drawLine(4,21,21,21);a.drawRoundRect(5,13,3,8,1,1);a.drawRoundRect(11,8,3,13,1,1);a.drawRoundRect(17,3,3,18,1,1);}
            }a.dispose();}
    }
}
