import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.*;
import javax.swing.plaf.metal.*;

enum AppTheme {
    CLASSIC("classic","Classic Light",0xF2F5F9,0xE5EBF2,0xFFFFFF,0x1F3044,0x607089,0x245C94,0x245C94,0xFFFFFF,0xB7C4D3),
    MIDNIGHT("midnight","Midnight (dark)",0x141B27,0x202D40,0x101722,0xE8EEF7,0xA8B5C7,0x6F9DD4,0x6F9DD4,0x0D1726,0x43556F),
    OCEAN("ocean","Ocean",0xEAF5FB,0xD7EAF4,0xF9FDFF,0x173B50,0x4B6C80,0x12617F,0x12617F,0xFFFFFF,0x9ABECE),
    FOREST("forest","Forest (dark)",0x172922,0x243B30,0x102019,0xE6F1E9,0xA6BDB0,0x8DBE9F,0x8DBE9F,0x102318,0x4D6B59),
    PLUM("plum","Plum (dark)",0x2B2034,0x3D2E48,0x211827,0xF1E8F6,0xBFAFCB,0xB99AD0,0xB99AD0,0x24152F,0x705A80),
    SANDSTONE("sandstone","Sandstone",0xF6F0E4,0xE9DECB,0xFFFCF6,0x423421,0x75654F,0x896434,0x896434,0xFFFFFF,0xC4AE8C),
    SLATE("slate","Slate (dark)",0x242A31,0x343D47,0x1B2127,0xE7ECF1,0xAAB4C0,0x8DADCC,0x8DADCC,0x141D28,0x596877);

    final String id,label;
    final Color background,surface,input,text,muted,accent,selection,selectedText,border;
    AppTheme(String id,String label,int background,int surface,int input,int text,int muted,int accent,int selection,int selectedText,int border) {
        this.id=id;this.label=label;this.background=new Color(background);this.surface=new Color(surface);this.input=new Color(input);
        this.text=new Color(text);this.muted=new Color(muted);this.accent=new Color(accent);this.selection=new Color(selection);
        this.selectedText=new Color(selectedText);this.border=new Color(border);
    }
    public String toString(){return label;}
    static AppTheme fromId(String id) {for(var theme:values())if(theme.id.equals(id))return theme;return CLASSIC;}
    static ColorUIResource color(Color color){return new ColorUIResource(color);}
    void apply() {
        if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("Themes must be applied on the Swing event thread.");
        AppTheme palette=this;
        MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme() {
            public String getName(){return palette.label;}
            protected ColorUIResource getPrimary1(){return color(border);}
            protected ColorUIResource getPrimary2(){return color(accent);}
            protected ColorUIResource getPrimary3(){return color(selection);}
            protected ColorUIResource getSecondary1(){return color(border);}
            protected ColorUIResource getSecondary2(){return color(muted);}
            protected ColorUIResource getSecondary3(){return color(surface);}
            public ColorUIResource getControlTextColor(){return color(text);}
            public ColorUIResource getSystemTextColor(){return color(text);}
            public ColorUIResource getUserTextColor(){return color(text);}
            public ColorUIResource getControlInfo(){return color(text);}
            public ColorUIResource getControlHighlight(){return color(surface.brighter());}
            public ColorUIResource getPrimaryControlHighlight(){return color(border);}
            public ColorUIResource getPrimaryControlInfo(){return color(text);}
            public ColorUIResource getWindowBackground(){return color(input);}
            public ColorUIResource getMenuForeground(){return color(text);}
            public ColorUIResource getMenuSelectedForeground(){return color(selectedText);}
            public ColorUIResource getHighlightedTextColor(){return color(selectedText);}
            public ColorUIResource getInactiveControlTextColor(){return color(muted);}
            public ColorUIResource getInactiveSystemTextColor(){return color(muted);}
            public ColorUIResource getMenuDisabledForeground(){return color(muted);}
            public ColorUIResource getFocusColor(){return color(accent);}
            public FontUIResource getControlTextFont(){return font(Font.PLAIN,13);}
            public FontUIResource getSystemTextFont(){return font(Font.PLAIN,13);}
            public FontUIResource getUserTextFont(){return font(Font.PLAIN,13);}
            public FontUIResource getMenuTextFont(){return font(Font.PLAIN,13);}
            public FontUIResource getWindowTitleFont(){return font(Font.BOLD,13);}
            public FontUIResource getSubTextFont(){return font(Font.PLAIN,11);}
            private FontUIResource font(int style,int size){return new FontUIResource("Segoe UI",style,size);}
        });
        try {UIManager.setLookAndFeel(new MetalLookAndFeel());}catch(UnsupportedLookAndFeelException e){throw new IllegalStateException(e);}
        put(background,"Panel.background","OptionPane.background","Viewport.background","ScrollPane.background","TabbedPane.background");
        put(surface,"Button.background","ToggleButton.background","MenuBar.background","Menu.background","MenuItem.background","PopupMenu.background","CheckBox.background","RadioButton.background","TableHeader.background","ComboBox.buttonBackground","TabbedPane.selected","ScrollBar.background","ScrollBar.track");
        put(input,"TextField.background","FormattedTextField.background","PasswordField.background","TextArea.background","TextPane.background","EditorPane.background","ComboBox.background","List.background","Table.background","ToolTip.background");
        put(text,"Label.foreground","Panel.foreground","OptionPane.messageForeground","Button.foreground","ToggleButton.foreground","CheckBox.foreground","RadioButton.foreground","MenuBar.foreground","Menu.foreground","MenuItem.foreground","ComboBox.foreground","TextField.foreground","FormattedTextField.foreground","PasswordField.foreground","TextArea.foreground","TextPane.foreground","EditorPane.foreground","Table.foreground","TableHeader.foreground","List.foreground","TabbedPane.foreground","ToolTip.foreground");
        put(muted,"Label.disabledForeground","Button.disabledText","MenuItem.disabledForeground","TextField.inactiveForeground","ComboBox.disabledForeground");
        put(surface,"TextField.inactiveBackground","ComboBox.disabledBackground");
        put(selection,"Table.selectionBackground","List.selectionBackground","TextField.selectionBackground","FormattedTextField.selectionBackground","PasswordField.selectionBackground","TextArea.selectionBackground","TextPane.selectionBackground","EditorPane.selectionBackground","Menu.selectionBackground","MenuItem.selectionBackground","ComboBox.selectionBackground");
        put(selectedText,"Table.selectionForeground","List.selectionForeground","TextField.selectionForeground","FormattedTextField.selectionForeground","PasswordField.selectionForeground","TextArea.selectionForeground","TextPane.selectionForeground","EditorPane.selectionForeground","Menu.selectionForeground","MenuItem.selectionForeground","ComboBox.selectionForeground","ProgressBar.selectionForeground");
        put(text,"TextField.caretForeground","FormattedTextField.caretForeground","PasswordField.caretForeground","TextArea.caretForeground","TextPane.caretForeground","EditorPane.caretForeground","ProgressBar.selectionBackground");
        put(accent,"ProgressBar.foreground","TabbedPane.focus","Button.focus","ScrollBar.thumb");
        put(background,"ProgressBar.background");put(border,"Table.gridColor","Separator.foreground","ScrollBar.thumbShadow","ScrollBar.thumbDarkShadow");
        UIManager.put("ToolTip.border",new BorderUIResource.LineBorderUIResource(border));
        var line=new BorderUIResource.LineBorderUIResource(border);
        var buttonBorder=new BorderUIResource.CompoundBorderUIResource(line,new BorderUIResource.EmptyBorderUIResource(5,12,5,12));
        UIManager.put("Button.border",buttonBorder);UIManager.put("ToggleButton.border",buttonBorder);
        var fieldBorder=new BorderUIResource.CompoundBorderUIResource(line,new BorderUIResource.EmptyBorderUIResource(4,6,4,6));
        UIManager.put("TextField.border",fieldBorder);UIManager.put("FormattedTextField.border",fieldBorder);UIManager.put("PasswordField.border",fieldBorder);
        UIManager.put("ScrollPane.border",line);UIManager.put("ComboBox.border",line);
        UIManager.put("PopupMenu.border",new BorderUIResource.CompoundBorderUIResource(line,new BorderUIResource.EmptyBorderUIResource(3,3,3,3)));
        UIManager.put("TableHeader.cellBorder",new BorderUIResource.CompoundBorderUIResource(line,new BorderUIResource.EmptyBorderUIResource(3,6,3,6)));
        UIManager.put("TabbedPane.tabInsets",new InsetsUIResource(7,12,7,12));
        UIManager.put("Button.margin",new InsetsUIResource(5,12,5,12));
        for(String key:new String[]{"Button.gradient","ToggleButton.gradient","CheckBox.gradient","MenuBar.gradient","TableHeader.gradient","ScrollBar.gradient"})UIManager.put(key,null);
        for(Window window:Window.getWindows()) {SwingUtilities.updateComponentTreeUI(window);window.invalidate();window.validate();window.repaint();}
    }
    private static void put(Color color,String... keys){for(String key:keys)UIManager.put(key,color(color));}
}
