import java.awt.event.*;
import java.util.function.LongConsumer;
import javax.swing.*;

final class ClientTableActions {
    /** Both edit entry points resolve the model ID, even after sorting, filtering or column reordering. */
    static Runnable install(JTable table,LongConsumer edit,Runnable refresh) {
        Runnable editSelected=() -> {
            int row=table.getSelectedRow();if(row<0)return;
            long id=((Number)table.getModel().getValueAt(table.convertRowIndexToModel(row),0)).longValue();
            edit.accept(id);refresh.run();
        };
        table.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent event) {
                if(!SwingUtilities.isLeftMouseButton(event)||event.getClickCount()!=2)return;
                int row=table.rowAtPoint(event.getPoint());if(row<0)return;
                table.setRowSelectionInterval(row,row);editSelected.run();
            }
        });
        return editSelected;
    }
}
