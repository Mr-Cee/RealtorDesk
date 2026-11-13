import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;

public class ThemeTest {
    static int checks;
    static void check(boolean value){if(!value)throw new AssertionError();checks++;}
    static double luminance(Color color) {
        double[] rgb={color.getRed()/255.0,color.getGreen()/255.0,color.getBlue()/255.0};
        for(int i=0;i<3;i++)rgb[i]=rgb[i]<=0.04045?rgb[i]/12.92:Math.pow((rgb[i]+0.055)/1.055,2.4);
        return rgb[0]*0.2126+rgb[1]*0.7152+rgb[2]*0.0722;
    }
    static double contrast(Color a,Color b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+0.05)/(Math.min(x,y)+0.05);}
    public static void main(String[] args) throws Exception {
        check(AppTheme.values().length==7);check(AppTheme.fromId(null)==AppTheme.CLASSIC);check(AppTheme.fromId("unknown-future-theme")==AppTheme.CLASSIC);
        Path folder=Files.createTempDirectory("RealtorDesk-theme-test-");Path file=folder.resolve("settings.properties");
        try {
            check(AppSettings.load(file).isEmpty());
            for(var theme:AppTheme.values()) {
                var preferences=new Properties();preferences.setProperty("repository","Mr-Cee/RealtorDesk");preferences.setProperty("theme",theme.id);preferences.setProperty("other-preference","keep");
                AppSettings.save(file,preferences);
                // A fresh load models a new application process, with no in-memory settings retained.
                var reopened=AppSettings.load(file);check(AppTheme.fromId(reopened.getProperty("theme"))==theme);
                check(reopened.getProperty("repository").equals("Mr-Cee/RealtorDesk"));check(reopened.getProperty("other-preference").equals("keep"));
            }
            try(var files=Files.list(folder)){check(files.count()==1);}
        } finally {Files.deleteIfExists(file);Files.delete(folder);}
        SwingUtilities.invokeAndWait(() -> {
            var sheet=new BufferedImage(1200,930,BufferedImage.TYPE_INT_RGB);var graphics=sheet.createGraphics();
            graphics.setColor(new Color(218,224,231));graphics.fillRect(0,0,1200,930);
            int index=0;
            for(var theme:AppTheme.values()) {
                theme.apply();
                check(contrast(theme.text,theme.background)>=4.5);check(contrast(theme.text,theme.input)>=4.5);check(contrast(theme.selectedText,theme.selection)>=4.5);
                var panel=sample(theme);panel.setSize(388,298);layout(panel);
                var image=new BufferedImage(388,298,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();panel.paint(g);g.dispose();
                graphics.drawImage(image,(index%3)*400+6,(index/3)*310+6,null);index++;
            }
            // Refresh existing controls after changing from a dark palette back to light.
            AppTheme.MIDNIGHT.apply();var panel=new JPanel();var text=new JTextField("Client");var table=new JTable(1,1);panel.add(text);panel.add(table);
            AppTheme.CLASSIC.apply();SwingUtilities.updateComponentTreeUI(panel);
            check(text.getBackground().equals(AppTheme.CLASSIC.input));check(text.getForeground().equals(AppTheme.CLASSIC.text));check(table.getSelectionForeground().equals(AppTheme.CLASSIC.selectedText));
            graphics.dispose();
            try {Path path=Path.of("build/theme-preview.png");Files.createDirectories(path.getParent());ImageIO.write(sheet,"png",path.toFile());}catch(Exception e){throw new RuntimeException(e);}
        });
        System.out.println("PASS: "+checks+" theme persistence, contrast and live-switch checks");
    }
    static JPanel sample(AppTheme theme) {
        var root=new JPanel(new BorderLayout(6,6));root.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        var top=new JPanel(new BorderLayout());var title=new JLabel(theme.label);title.setFont(title.getFont().deriveFont(Font.BOLD,17));top.add(title,BorderLayout.NORTH);
        var bar=new JMenuBar();bar.add(new JButton("Settings"));var help=new JMenu("Help");help.add(new JMenuItem("Check for updates"));help.add(new JMenuItem("About RealtorDesk"));bar.add(help);top.add(bar,BorderLayout.SOUTH);root.add(top,BorderLayout.NORTH);
        var content=new JPanel(new BorderLayout(6,6));var fields=new JPanel(new BorderLayout(6,6));fields.add(new JTextField("Taylor Morgan"));fields.add(new JComboBox<String>(new String[]{"Active client","Lead"}),BorderLayout.EAST);content.add(fields,BorderLayout.NORTH);
        var table=new JTable(new Object[][]{{"Taylor Morgan","Buyer"},{"Alex Smith","Seller"},{"Jamie Lee","Lead"}},new String[]{"Client","Status"});table.setRowHeight(25);table.setFillsViewportHeight(true);table.setRowSelectionInterval(0,0);
        check(table.getBackground().equals(theme.input));check(table.getForeground().equals(theme.text));check(table.getSelectionBackground().equals(theme.selection));
        var scroll=new JScrollPane(table);scroll.setColumnHeaderView(table.getTableHeader());content.add(scroll);var tabs=new JTabbedPane();tabs.add("Clients",content);tabs.add("Properties",new JPanel());root.add(tabs);
        var buttons=new JPanel(new FlowLayout(FlowLayout.LEFT,6,0));buttons.add(new JButton("Add client"));buttons.add(new JButton("Refresh"));root.add(buttons,BorderLayout.SOUTH);return root;
    }
    static void layout(Container parent){parent.doLayout();for(Component child:parent.getComponents())if(child instanceof Container container)layout(container);}
}
