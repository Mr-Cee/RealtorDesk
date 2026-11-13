import java.math.*;
import java.text.NumberFormat;
import java.util.Locale;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;

final class PropertyPrice {
    static Long parse(String input) {
        String value=input==null?"":input.trim();if(value.isEmpty())return null;
        if(value.startsWith("$"))value=value.substring(1).trim();
        if(!value.matches("(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]{1,2})?"))throw new IllegalArgumentException("Enter a nonnegative price with up to two decimal places, such as 425000 or $425,000.00. Leave blank if not closed.");
        try{return new BigDecimal(value.replace(",","")).movePointRight(2).longValueExact();}
        catch(ArithmeticException e){throw new IllegalArgumentException("The price is too large.");}
    }
    static String editable(Long cents){return cents==null?"":BigDecimal.valueOf(cents,2).toPlainString();}
    static String formatted(Long cents){return cents==null?"":NumberFormat.getCurrencyInstance(Locale.US).format(BigDecimal.valueOf(cents,2));}
    static void configureTable(JTable table) {
        ClientTableActions.hideId(table);
        table.getColumnModel().getColumn(table.convertColumnIndexToView(4)).setCellRenderer(new DefaultTableCellRenderer(){
            {setHorizontalAlignment(SwingConstants.RIGHT);}
            protected void setValue(Object value){setText(formatted(value==null?null:((Number)value).longValue()));}
        });
    }
}
