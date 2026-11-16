import java.awt.*;
import java.sql.*;
import javax.swing.*;
import javax.swing.table.*;

final class ChecklistPanel extends JPanel {
    final Connection db;final long property;final JLabel progress=new JLabel();
    final DefaultTableModel model=new DefaultTableModel(new String[]{"ID","Step","Due","Status"},0){public boolean isCellEditable(int r,int c){return false;}};
    final JTable table=new JTable(model);
    ChecklistPanel(Connection db,long property){
        super(new BorderLayout(8,8));this.db=db;this.property=property;
        table.setAutoCreateRowSorter(true);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);table.setRowHeight(28);table.setFillsViewportHeight(true);ClientTableActions.hideId(table);
        var buttons=new JPanel(new GridLayout(0,3,6,6));
        button(buttons,"Use template",()->apply());button(buttons,"Add step",()->edit(null));button(buttons,"Edit step",()->{Long id=selected();if(id!=null)edit(id);});
        button(buttons,"Complete / reopen",()->{Long id=selected();if(id!=null){var t=TaskStore.load(db,id);TaskStore.save(db,new TaskStore.Task(t.id(),t.title(),t.clientId(),t.propertyId(),t.due(),t.priority(),t.notes(),!t.completed(),t.remind(),null));}});
        button(buttons,"Delete step",()->{Long id=selected();if(id!=null&&JOptionPane.showConfirmDialog(this,"Delete this step and its task?","Delete step",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION)TaskStore.delete(db,id);});
        button(buttons,"Templates",()->templates());button(buttons,"Refresh",()->{});
        var top=new JPanel(new BorderLayout());top.add(progress,BorderLayout.NORTH);top.add(buttons,BorderLayout.SOUTH);add(top,BorderLayout.NORTH);add(new JScrollPane(table));
        ClientTableActions.install(table,id->safe(()->edit(id)),()->safe(()->{}));setPreferredSize(new Dimension(850,420));safe(()->{});
    }
    void button(JPanel panel,String title,ChecklistStore.Work action){var b=new JButton(title);b.addActionListener(e->safe(action));panel.add(b);}
    Long selected(){int row=table.getSelectedRow();return row<0?null:((Number)model.getValueAt(table.convertRowIndexToModel(row),0)).longValue();}
    void refresh()throws SQLException {
        model.setRowCount(0);int complete=0;String name="No checklist yet. Choose a template or add a step.";
        try(var p=db.prepareStatement("SELECT name FROM property_checklists WHERE property_id=?")){p.setLong(1,property);try(var r=p.executeQuery()){if(r.next())name=r.getString(1);}}
        try(var p=db.prepareStatement("SELECT t.* FROM tasks t JOIN checklist_steps s ON s.task_id=t.id JOIN property_checklists c ON c.id=s.checklist_id WHERE c.property_id=? ORDER BY s.position,t.id")){p.setLong(1,property);try(var r=p.executeQuery()){while(r.next()){var t=TaskStore.read(r);if(t.completed())complete++;model.addRow(new Object[]{t.id(),t.title(),t.due(),t.completed()?"Completed":"Open"});}}}
        progress.setText(name+" — "+complete+" of "+model.getRowCount()+" steps completed");
    }
    void apply()throws Exception {
        if(ChecklistStore.checklist(db,property)!=null){JOptionPane.showMessageDialog(this,"This property already has a checklist. Add or edit its steps individually.");return;}
        var choices=ChecklistStore.templates(db);if(choices.isEmpty()){JOptionPane.showMessageDialog(this,"Create a template first using Templates.");return;}
        var chosen=(ChecklistStore.Template)JOptionPane.showInputDialog(this,"Choose a template. Steps will also appear in Tasks.\nAdd due dates to enable reminders.","Property checklist",JOptionPane.PLAIN_MESSAGE,null,choices.toArray(),choices.get(0));
        if(chosen!=null)ChecklistStore.apply(db,property,chosen);
    }
    void edit(Long id)throws Exception {
        var old=id==null?null:TaskStore.load(db,id);var title=new JTextField(old==null?"":old.title(),30);var due=new DatePicker(old==null?"":old.due(),"Checklist due date");
        var notes=new JTextArea(old==null?"":old.notes(),4,30);notes.setLineWrap(true);notes.setWrapStyleWord(true);var done=new JCheckBox("Completed",old!=null&&old.completed());var remind=new JCheckBox("Remind when due / overdue",old==null||old.remind());
        var form=new TaskForm();form.field("Step *",title);form.field("Due date (optional)",due);form.field("Notes",new JScrollPane(notes));form.options(done,remind);
        while(JOptionPane.showConfirmDialog(this,form.scrollPane(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds()),"Checklist step",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION){
            try{ChecklistStore.transaction(db,()->{
                if(id!=null)TaskStore.save(db,new TaskStore.Task(id,title.getText(),old.clientId(),old.propertyId(),due.date(),old.priority(),notes.getText(),done.isSelected(),remind.isSelected(),null));
                else {Long checklist=ChecklistStore.checklist(db,property);if(checklist==null){try(var p=db.prepareStatement("INSERT INTO property_checklists(property_id,name) VALUES(?,'Custom checklist')")){p.setLong(1,property);p.executeUpdate();}checklist=ChecklistStore.lastId(db);}
                    int position;try(var s=db.createStatement();var r=s.executeQuery("SELECT COALESCE(MAX(position),0)+1 FROM checklist_steps")){r.next();position=r.getInt(1);}
                    long task=ChecklistStore.add(db,checklist,property,title.getText(),due.date(),done.isSelected(),position);
                    var created=TaskStore.load(db,task);TaskStore.save(db,new TaskStore.Task(task,created.title(),created.clientId(),property,created.due(),created.priority(),notes.getText(),done.isSelected(),remind.isSelected(),null));
                }
            });return;}catch(IllegalArgumentException ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Check step",JOptionPane.WARNING_MESSAGE);}
        }
    }
    void templates()throws Exception {
        var list=new JList<ChecklistStore.Template>();list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);Runnable reload=()->{try{list.setListData(ChecklistStore.templates(db).toArray(ChecklistStore.Template[]::new));}catch(SQLException e){throw new RuntimeException(e);}};reload.run();
        var panel=new JPanel(new BorderLayout(8,8));panel.add(new JLabel("Changes affect future checklists. One step per line."),BorderLayout.NORTH);panel.add(new JScrollPane(list));var buttons=new JPanel(new FlowLayout());
        for(String action:new String[]{"New","Edit","Delete"}){var b=new JButton(action);buttons.add(b);b.addActionListener(e->safe(()->{var selected=list.getSelectedValue();if(!action.equals("New")&&selected==null)return;
            if(action.equals("Delete")){if(JOptionPane.showConfirmDialog(this,"Delete template? Existing checklists will be kept.","Delete template",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION){try(var p=db.prepareStatement("DELETE FROM checklist_templates WHERE id=?")){p.setLong(1,selected.id());p.executeUpdate();}}}
            else {var name=new JTextField(action.equals("New")?"":selected.name(),28);var steps=new JTextArea(action.equals("New")?"":selected.steps(),12,40);var editor=new TaskForm();editor.field("Name *",name);editor.field("Steps *",new JScrollPane(steps));
                while(JOptionPane.showConfirmDialog(this,editor.scrollPane(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds()),"Checklist template",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION){try{ChecklistStore.template(db,action.equals("New")?null:selected.id(),name.getText(),steps.getText());break;}catch(IllegalArgumentException|SQLException ex){JOptionPane.showMessageDialog(this,ex.getMessage());}}}
            reload.run();}));}
        panel.add(buttons,BorderLayout.SOUTH);panel.setPreferredSize(new Dimension(550,330));JOptionPane.showMessageDialog(this,panel,"Checklist templates",JOptionPane.PLAIN_MESSAGE);
    }
    void safe(ChecklistStore.Work action){try{action.run();refresh();}catch(Exception e){JOptionPane.showMessageDialog(this,e.getMessage(),"Checklist",JOptionPane.ERROR_MESSAGE);}}
}
