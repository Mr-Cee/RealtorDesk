import java.awt.*;
import java.awt.geom.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.*;
import javax.swing.plaf.basic.*;
import javax.swing.table.*;

/** Lightweight, bundled desktop styling. Colors always follow the selected palette. */
public final class ModernUI {
    private ModernUI(){}
    static Color color(String key){return UIManager.getColor(key);}
    static Color blend(Color a,Color b,float amount){return new Color((int)(a.getRed()*(1-amount)+b.getRed()*amount),(int)(a.getGreen()*(1-amount)+b.getGreen()*amount),(int)(a.getBlue()*(1-amount)+b.getBlue()*amount));}
    static Graphics2D graphics(Graphics g){var copy=(Graphics2D)g.create();copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);return copy;}
    static void install(AppTheme theme){
        UIManager.put("ButtonUI",ButtonUI.class.getName());UIManager.put("ToggleButtonUI",ButtonUI.class.getName());
        UIManager.put("ComboBoxUI",ComboUI.class.getName());UIManager.put("ScrollBarUI",ScrollUI.class.getName());UIManager.put("CheckBoxUI",CheckUI.class.getName());
        UIManager.put("OptionPaneUI",OptionUI.class.getName());
        UIManager.put("Button.border",new RoundedBorder(8,8,14));UIManager.put("ToggleButton.border",new RoundedBorder(8,8,14));
        for(String key:new String[]{"TextField.border","FormattedTextField.border","PasswordField.border"})UIManager.put(key,new RoundedBorder(8,8,10));
        UIManager.put("ComboBox.border",new RoundedBorder(8,3,8));
        UIManager.put("CheckBox.icon",new CheckIcon());UIManager.put("CheckBox.textIconGap",8);
        UIManager.put("ScrollPane.border",new RoundedBorder(10,1,1));
        UIManager.put("MenuBar.border",new BorderUIResource.EmptyBorderUIResource(5,16,5,16));
        UIManager.put("MenuBar.background",AppTheme.color(theme.input));UIManager.put("MenuItem.border",new BorderUIResource.EmptyBorderUIResource(7,12,7,12));
        UIManager.put("Table.gridColor",AppTheme.color(blend(theme.input,theme.border,.55f)));
        UIManager.put("TableHeader.cellBorder",new BorderUIResource.EmptyBorderUIResource(12,12,12,12));
        UIManager.put("TableHeader.background",AppTheme.color(blend(theme.background,theme.input,.4f)));
        UIManager.put("TableHeader.font",new FontUIResource("Segoe UI",Font.BOLD,12));
        UIManager.put("Table.focusCellHighlightBorder",new BorderUIResource.EmptyBorderUIResource(0,12,0,12));
        UIManager.put("Table.cellNoFocusBorder",new BorderUIResource.EmptyBorderUIResource(0,12,0,12));
        UIManager.put("Table.showVerticalLines",false);UIManager.put("Table.rowHeight",38);
        UIManager.put("TextArea.margin",new InsetsUIResource(8,10,8,10));
        UIManager.put("OptionPane.messageAreaBorder",new BorderUIResource.EmptyBorderUIResource(8,8,12,8));
        UIManager.put("OptionPane.buttonAreaBorder",new BorderUIResource.EmptyBorderUIResource(12,8,8,8));
        UIManager.put("TitledBorder.border",new RoundedBorder(10,10,12));UIManager.put("TitledBorder.titleColor",AppTheme.color(theme.muted));
    }
    static void polish(Component component){
        if(component instanceof JTable table){table.setRowHeight(40);table.setShowVerticalLines(false);table.setShowHorizontalLines(true);table.setIntercellSpacing(new Dimension(0,1));table.getTableHeader().setReorderingAllowed(false);
            for(int i=0;i<table.getColumnCount();i++){var column=table.getColumnModel().getColumn(i);String name=table.getColumnName(i);if(name.equals("Status"))column.setCellRenderer(new StatusRenderer());
                if(table.getAutoResizeMode()!=JTable.AUTO_RESIZE_OFF)column.setPreferredWidth(switch(name){case "Task","Step","Filename"->280;case "Address","Property","Properties"->260;case "Notes","Email"->200;case "Client","Status"->150;case "Date Last Communicated"->185;case "Phone"->145;case "Closed Price","Commission"->140;case "Priority","Documents"->95;default->120;});
            }
            if(table.getParent() instanceof JViewport viewport&&viewport.getParent() instanceof JScrollPane pane)pane.setColumnHeaderView(table.getTableHeader());
        }
        if(component instanceof JPanel panel){for(Component parent=panel;parent!=null;parent=parent.getParent())if(parent instanceof JComponent jc&&Boolean.TRUE.equals(jc.getClientProperty("workspaceSurface"))){panel.setBackground(AppTheme.color(color("TextField.background")));break;}}
        if(component instanceof JScrollPane pane){pane.getVerticalScrollBar().setUnitIncrement(20);}
        if(component instanceof JPanel panel&&panel.getLayout() instanceof FlowLayout flow){if(panel.getComponentCount()>=3&&!(flow instanceof WrapLayout)){panel.setLayout(new WrapLayout(flow.getAlignment(),8,7));panel.addComponentListener(new java.awt.event.ComponentAdapter(){public void componentResized(java.awt.event.ComponentEvent e){if(panel.getParent() instanceof JComponent parent)parent.revalidate();}});}}
        if(component instanceof Container container)for(Component child:container.getComponents())polish(child);
    }
    static class StatusRenderer extends DefaultTableCellRenderer {
        StatusRenderer(){setHorizontalAlignment(SwingConstants.CENTER);}
        protected void paintComponent(Graphics g){Color background=getBackground();g.setColor(background);g.fillRect(0,0,getWidth(),getHeight());String text=getText();if(text==null||text.isBlank())return;
            var a=graphics(g);Color ink=getForeground();int w=Math.min(getWidth()-12,getFontMetrics(getFont()).stringWidth(text)+20);a.setColor(blend(background,ink,.09f));a.fillRoundRect((getWidth()-w)/2,7,w,Math.max(16,getHeight()-14),18,18);a.dispose();setOpaque(false);super.paintComponent(g);setOpaque(true);
        }
    }
    public static class RoundedBorder extends AbstractBorder implements UIResource {
        final int radius,vertical,horizontal;
        RoundedBorder(int radius,int vertical,int horizontal){this.radius=radius;this.vertical=vertical;this.horizontal=horizontal;}
        public Insets getBorderInsets(Component c){return new Insets(vertical,horizontal,vertical,horizontal);}
        public Insets getBorderInsets(Component c,Insets i){i.set(vertical,horizontal,vertical,horizontal);return i;}
        public void paintBorder(Component c,Graphics g,int x,int y,int width,int height){
            if(c instanceof AbstractButton button&&!button.isBorderPainted())return;
            var a=graphics(g);a.setColor(color(c.hasFocus()?"Button.focus":"Separator.foreground"));a.setStroke(new BasicStroke(c.hasFocus()?1.6f:1f));a.draw(new RoundRectangle2D.Float(x+.8f,y+.8f,width-1.6f,height-1.6f,radius,radius));a.dispose();
        }
    }
    public static class ButtonUI extends BasicButtonUI {
        public static ComponentUI createUI(JComponent c){return new ButtonUI();}
        protected void installDefaults(AbstractButton b){super.installDefaults(b);b.setOpaque(false);b.setRolloverEnabled(true);b.setIconTextGap(8);}
        boolean primary(AbstractButton b){String text=b.getText();return Boolean.TRUE.equals(b.getClientProperty("primary"))||(text!=null&&(text.startsWith("Add ")||text.equals("Run report")||text.equals("Use template")));}
        public void paint(Graphics g,JComponent c){var b=(AbstractButton)c;var a=graphics(g);boolean primary=primary(b);Color background=primary?color("Button.focus"):color("Button.background");
            if(b.isSelected()&&b.isContentAreaFilled())background=color("Table.selectionBackground");
            if(b.getModel().isRollover()||b.getModel().isPressed())background=blend(background,color("Label.foreground"),b.getModel().isPressed()?.12f:.05f);
            if(b.isContentAreaFilled()){a.setColor(background);a.fillRoundRect(0,0,b.getWidth(),b.getHeight(),8,8);}a.dispose();super.paint(g,c);
        }
        protected void paintText(Graphics g,AbstractButton b,Rectangle r,String text){Color old=g.getColor();if(b.isEnabled()&&(primary(b)||(b.isSelected()&&b.isContentAreaFilled())))g.setColor(color("Table.selectionForeground"));else g.setColor(b.isEnabled()?b.getForeground():color("Button.disabledText"));
            var fm=g.getFontMetrics();javax.swing.plaf.basic.BasicGraphicsUtils.drawStringUnderlineCharAt(g,text,b.getDisplayedMnemonicIndex(),r.x,r.y+fm.getAscent());g.setColor(old);}
        protected void paintButtonPressed(Graphics g,AbstractButton b){}
        protected void paintFocus(Graphics g,AbstractButton b,Rectangle view,Rectangle text,Rectangle icon){var a=graphics(g);a.setColor(color(primary(b)&&b.isContentAreaFilled()?"Table.selectionForeground":"Button.focus"));a.setStroke(new BasicStroke(2));a.drawRoundRect(3,3,b.getWidth()-7,b.getHeight()-7,8,8);a.dispose();}
    }
    public static class CheckUI extends BasicCheckBoxUI {
        public static ComponentUI createUI(JComponent c){return new CheckUI();}
        protected void installDefaults(AbstractButton b){super.installDefaults(b);b.setOpaque(false);}
        public Icon getDefaultIcon(){return new CheckIcon();}
    }
    public static class ComboUI extends BasicComboBoxUI {
        public static ComponentUI createUI(JComponent c){return new ComboUI();}
        public Dimension getMinimumSize(JComponent c){var d=super.getMinimumSize(c);return new Dimension(d.width,Math.max(34,d.height));}
        protected JButton createArrowButton(){var b=new JButton(new Icon(){public int getIconWidth(){return 12;}public int getIconHeight(){return 12;}public void paintIcon(Component c,Graphics g,int x,int y){var a=graphics(g);a.setColor(c.isEnabled()?color("Label.foreground"):color("Label.disabledForeground"));a.setStroke(new BasicStroke(1.6f));a.drawLine(x+2,y+4,x+6,y+8);a.drawLine(x+6,y+8,x+10,y+4);a.dispose();}});b.setBorder(BorderFactory.createEmptyBorder(0,6,0,6));b.setContentAreaFilled(false);return b;}
        public void paintCurrentValueBackground(Graphics g,Rectangle bounds,boolean focus){g.setColor(comboBox.isEnabled()?color("ComboBox.background"):color("ComboBox.disabledBackground"));g.fillRect(bounds.x,bounds.y,bounds.width,bounds.height);}
    }
    public static class ScrollUI extends BasicScrollBarUI {
        public static ComponentUI createUI(JComponent c){return new ScrollUI();}
        protected Dimension getMinimumThumbSize(){return new Dimension(8,32);}
        protected JButton createDecreaseButton(int direction){return empty();}protected JButton createIncreaseButton(int direction){return empty();}
        JButton empty(){var b=new JButton();b.setPreferredSize(new Dimension(0,0));b.setMinimumSize(new Dimension(0,0));b.setMaximumSize(new Dimension(0,0));return b;}
        public Dimension getPreferredSize(JComponent c){return new Dimension(12,12);}
        protected void paintTrack(Graphics g,JComponent c,Rectangle r){g.setColor(color("ScrollPane.background"));g.fillRect(r.x,r.y,r.width,r.height);}
        protected void paintThumb(Graphics g,JComponent c,Rectangle r){var a=graphics(g);a.setColor(blend(color("Label.disabledForeground"),color("ScrollPane.background"),isThumbRollover()?.15f:.5f));a.fillRoundRect(r.x+3,r.y+2,Math.max(3,r.width-6),Math.max(4,r.height-4),6,6);a.dispose();}
    }
    public static class OptionUI extends BasicOptionPaneUI {
        public static ComponentUI createUI(JComponent c){return new OptionUI();}
        protected void installComponents(){super.installComponents();polish(optionPane);}
    }
    static class CheckIcon implements Icon,UIResource {
        public int getIconWidth(){return 18;}public int getIconHeight(){return 18;}
        public void paintIcon(Component c,Graphics g,int x,int y){var b=(AbstractButton)c;var a=graphics(g);a.translate(x,y);a.setColor(color(b.isSelected()?"Button.focus":"TextField.background"));a.fillRoundRect(1,1,16,16,5,5);a.setColor(color(b.hasFocus()?"Button.focus":"Separator.foreground"));a.drawRoundRect(1,1,16,16,5,5);if(b.isSelected()){a.setColor(color("Table.selectionForeground"));a.setStroke(new BasicStroke(1.8f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));a.drawLine(5,9,8,12);a.drawLine(8,12,13,6);}a.dispose();}
    }
}
