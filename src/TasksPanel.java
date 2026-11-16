import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.time.*;
import javax.swing.*;
import javax.swing.table.*;

final class TasksPanel extends JPanel {
    final Connection db;final DefaultTableModel model=new DefaultTableModel(new String[]{"ID","Task","Due","Priority","Client","Property","Status"},0){public boolean isCellEditable(int r,int c){return false;}};
    final JTable table=new JTable(model);final JTextField search=new JTextField(23);final JLabel count=new JLabel();final JCheckBox completed=new JCheckBox("Include completed"),dueOnly=new JCheckBox("Due / overdue only");
    final javax.swing.Timer timer;
    TasksPanel(Connection db) {
        super(new BorderLayout(8,8));this.db=db;table.setAutoCreateRowSorter(true);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);table.setRowHeight(28);table.setFillsViewportHeight(true);ClientTableActions.hideId(table);
        var actions=new JPanel(new FlowLayout(FlowLayout.LEFT));var add=new JButton("Add task");add.addActionListener(e->edit(null));actions.add(add);var edit=new JButton("Edit selected");edit.addActionListener(e->{Long id=selected();if(id!=null)edit(id);});actions.add(edit);
        var done=new JButton("Mark complete");done.addActionListener(e->safe(()->{Long id=selected();if(id!=null){TaskStore.complete(db,id);refresh();}}));actions.add(done);
        var delete=new JButton("Delete task");delete.addActionListener(e->safe(()->{Long id=selected();if(id!=null&&JOptionPane.showConfirmDialog(this,"Delete task: "+TaskStore.load(db,id).title()+"?","Delete task",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION){TaskStore.delete(db,id);refresh();}}));actions.add(delete);
        var refresh=new JButton("Refresh");refresh.addActionListener(e->refresh());actions.add(refresh);actions.add(completed);actions.add(dueOnly);completed.addActionListener(e->refresh());dueOnly.addActionListener(e->refresh());
        var header=new JPanel(new BorderLayout());header.add(actions,BorderLayout.NORTH);header.add(DocumentsPanel.searchBar("Search tasks:",search),BorderLayout.SOUTH);add(header,BorderLayout.NORTH);add(new JScrollPane(table));var footer=new JPanel(new GridLayout(0,1,2,2));footer.add(count);footer.add(new JLabel("Reminders appear once per day for due/overdue tasks while the app is open."));add(footer,BorderLayout.SOUTH);
        ClientSearch.attach(table,search,count,"tasks","No tasks in this view.","Search task, date, priority, client or property.",1,2,3,4,5,6);
        ClientTableActions.install(table,id->edit(id),this::refresh);timer=new javax.swing.Timer(60000,e->checkReminders());refresh();
    }
    Long selected(){int row=table.getSelectedRow();return row<0?null:((Number)model.getValueAt(table.convertRowIndexToModel(row),0)).longValue();}
    void refresh(){safe(()->{model.setRowCount(0);String sql="SELECT t.id,t.title,t.due_date,t.priority,c.name,p.address,t.completed FROM tasks t LEFT JOIN clients c ON c.id=t.client_id LEFT JOIN properties p ON p.id=t.property_id WHERE 1=1"+(completed.isSelected()?"":" AND t.completed=0")+(dueOnly.isSelected()?" AND t.due_date<>'' AND t.due_date<='"+LocalDate.now()+"'":"")+" ORDER BY t.completed,CASE WHEN t.due_date='' OR t.due_date IS NULL THEN 1 ELSE 0 END,t.due_date,t.id";try(var s=db.createStatement();var rows=s.executeQuery(sql)){while(rows.next())model.addRow(new Object[]{rows.getLong(1),rows.getString(2),rows.getString(3),rows.getString(4),rows.getString(5),rows.getString(6),rows.getInt(7)==0?"Open":"Completed"});}});}
    void edit(Long id){safe(()->{
        var old=id==null?new TaskStore.Task(null,"",null,null,"","Normal","",false,true,null):TaskStore.load(db,id);
        var title=new JTextField(old.title(),30);var due=new DatePicker(old.due(),"Task due date");var priority=new JComboBox<>(new String[]{"Low","Normal","High"});priority.setSelectedItem(old.priority());var notes=new JTextArea(old.notes(),4,30);notes.setLineWrap(true);notes.setWrapStyleWord(true);
        var client=new PropertyClientField(db,old.clientId());var reminder=new JCheckBox("Remind on due date and while overdue",old.remind());var finished=new JCheckBox("Completed",old.completed());
        var property=new JTextField(28);property.setEditable(false);Long[] propertyId={old.propertyId()};if(propertyId[0]!=null)property.setText(PropertyStore.load(db,propertyId[0]).address());
        var select=new JButton("Choose");select.addActionListener(e->safe(()->{Long chosen=chooseProperty();if(chosen!=null){propertyId[0]=chosen;property.setText(PropertyStore.load(db,chosen).address());}}));var clear=new JButton("Clear");clear.addActionListener(e->{propertyId[0]=null;property.setText("");});var propertyRow=new JPanel(new BorderLayout(4,0));propertyRow.add(property);var propertyActions=new JPanel(new FlowLayout(FlowLayout.RIGHT,2,0));propertyActions.add(select);propertyActions.add(clear);propertyRow.add(propertyActions,BorderLayout.EAST);
        if(ChecklistStore.linked(db,id)){select.setEnabled(false);clear.setEnabled(false);property.setToolTipText("This task belongs to this property's checklist.");}
        var form=new TaskForm();form.field("Title *",title);form.field("Due date (optional)",due);form.field("Priority",priority);form.clientField(client);form.field("Property (optional)",propertyRow);form.field("Notes",new JScrollPane(notes));form.options(finished,reminder);
        var scroll=form.scrollPane(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds());
        while(JOptionPane.showConfirmDialog(this,scroll,"Task",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION){try{TaskStore.save(db,new TaskStore.Task(id,title.getText(),client.clientId(),propertyId[0],due.date(),(String)priority.getSelectedItem(),notes.getText(),finished.isSelected(),reminder.isSelected(),null));refresh();return;}catch(IllegalArgumentException problem){JOptionPane.showMessageDialog(this,problem.getMessage(),"Check task",JOptionPane.WARNING_MESSAGE);}}
    });}
    Long chooseProperty()throws Exception {
        var model=new DefaultTableModel(new String[]{"ID","Address","MLS #","Client"},0){public boolean isCellEditable(int r,int c){return false;}};try(var s=db.createStatement();var rows=s.executeQuery("SELECT p.id,p.address,p.mls_number,c.name FROM properties p LEFT JOIN clients c ON c.id=p.client_id ORDER BY p.address")){while(rows.next())model.addRow(new Object[]{rows.getLong(1),rows.getString(2),rows.getString(3),rows.getString(4)});}
        var table=new JTable(model);table.setAutoCreateRowSorter(true);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);table.setRowHeight(28);ClientTableActions.hideId(table);var search=new JTextField(25);var count=new JLabel();ClientSearch.attach(table,search,count,"properties","No properties yet.","Search address, MLS or client.",1,2,3);var panel=new JPanel(new BorderLayout());panel.add(DocumentsPanel.searchBar("Search properties:",search),BorderLayout.NORTH);panel.add(new JScrollPane(table));panel.add(count,BorderLayout.SOUTH);panel.setPreferredSize(new Dimension(650,350));
        while(JOptionPane.showConfirmDialog(this,panel,"Choose property",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION){int row=table.getSelectedRow();if(row>=0)return ((Number)model.getValueAt(table.convertRowIndexToModel(row),0)).longValue();JOptionPane.showMessageDialog(this,"Select a property first.");}return null;
    }
    void checkReminders(){for(Window window:Window.getWindows())if(window instanceof Dialog dialog&&dialog.isModal()&&dialog.isVisible())return;safe(()->{var tasks=TaskStore.reminders(db,LocalDate.now());if(tasks.isEmpty())return;var text=new JTextArea(tasks.stream().map(t->t.due()+"  "+t.title()+" ["+t.priority()+"]").collect(java.util.stream.Collectors.joining("\n")),12,65);text.setEditable(false);text.setCaretPosition(0);JOptionPane.showMessageDialog(this,new JScrollPane(text),"Tasks due or overdue",JOptionPane.INFORMATION_MESSAGE);TaskStore.acknowledged(db,tasks,LocalDate.now());});}
    void start(JFrame frame){timer.start();frame.addWindowListener(new WindowAdapter(){public void windowClosed(WindowEvent e){timer.stop();}});SwingUtilities.invokeLater(this::checkReminders);}
    interface Work{void run()throws Exception;}void safe(Work work){try{work.run();}catch(Exception e){JOptionPane.showMessageDialog(this,e.getMessage(),"Tasks",JOptionPane.ERROR_MESSAGE);}}
}
