import java.sql.*;
import java.time.*;
import java.math.*;
import javax.swing.*;

final class ReportTest {
    static int checks;
    static void check(boolean value){if(!value)throw new AssertionError("Report check "+checks);checks++;}
    static void client(Connection db,long id,String start,String offer,String close)throws SQLException {
        try(var p=db.prepareStatement("INSERT INTO clients(id,date_start,date_offer,date_close) VALUES(?,?,?,?)")){p.setLong(1,id);p.setString(2,start);p.setString(3,offer);p.setString(4,close);p.executeUpdate();}
    }
    static void property(Connection db,Long owner,String city,String state,String status,Long price)throws SQLException {
        try(var p=db.prepareStatement("INSERT INTO properties(client_id,city,state,status,closed_price_cents) VALUES(?,?,?,?,?)")){if(owner==null)p.setNull(1,Types.INTEGER);else p.setLong(1,owner);p.setString(2,city);p.setString(3,state);p.setString(4,status);if(price==null)p.setNull(5,Types.BIGINT);else p.setLong(5,price);p.executeUpdate();}
    }
    static int run()throws Exception {
        check(ReportStore.Range.month(2024,2).to().equals(LocalDate.of(2024,2,29)));
        check(ReportStore.Range.quarter(2026,1).from().equals(LocalDate.of(2026,1,1)));check(ReportStore.Range.quarter(2026,4).to().equals(LocalDate.of(2026,12,31)));
        check(ReportStore.Range.year(2026).to().equals(LocalDate.of(2026,12,31)));check(ReportStore.Range.all().includes(null));check(!ReportStore.Range.month(2026,1).includes(null));
        try{new ReportStore.Range(LocalDate.of(2026,2,1),LocalDate.of(2026,1,1));throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}
        try(var db=DriverManager.getConnection("jdbc:sqlite::memory:");var s=db.createStatement()) {
            s.execute("CREATE TABLE clients(id INTEGER PRIMARY KEY,date_start TEXT,date_offer TEXT,date_close TEXT)");s.execute("CREATE TABLE properties(id INTEGER PRIMARY KEY,client_id INTEGER,city TEXT,state TEXT,status TEXT,closed_price_cents INTEGER)");
            var empty=ReportStore.run(db,ReportStore.Range.all(),false).get(0);check(empty.startOffer()==null);check(empty.price()==null);check(empty.homes()==0);check(ReportStore.run(db,ReportStore.Range.all(),true).isEmpty());
            client(db,1,"2026-01-01","2026-01-11","2026-01-31");client(db,2,"2026-01-01","2026-01-21","2026-02-20");client(db,3,"2026-03-10","2026-03-01","2026-03-20");client(db,4,"",null,"bad date");
            property(db,1L,"Anchorage","AK","Closed",10000000L);property(db,1L,"ANCHORAGE","ak","Sold",30000000L);property(db,1L,"Juneau","AK","Active",90000000L);
            property(db,2L,"Juneau","AK"," closed ",20000000L);property(db,2L,"Juneau","AK","Sold",null);
            property(db,null,"Anchorage","AK","Sold",40000000L);property(db,null,"","","Closed",0L);property(db,null,"Juneau","AK","Active",100000000L);
            var overall=ReportStore.run(db,ReportStore.Range.all(),false).get(0);
            check(overall.startOfferCount()==2);check(overall.startOffer()==15.0);check(overall.offerCloseCount()==3);check(overall.offerClose()==23.0);check(overall.startCloseCount()==3);check(overall.startClose()==30.0);
            check(overall.homes()==5);check(overall.price().compareTo(new BigDecimal("200000.00"))==0);
            var groups=ReportStore.run(db,ReportStore.Range.all(),true);var anchorage=groups.stream().filter(r->r.group().equalsIgnoreCase("Anchorage, AK")).findFirst().orElseThrow();
            check(anchorage.startOfferCount()==1);check(anchorage.startOffer()==10.0);check(anchorage.homes()==3);check(anchorage.price().compareTo(new BigDecimal("266666.67"))==0);
            var juneau=groups.stream().filter(r->r.group().equals("Juneau, AK")).findFirst().orElseThrow();check(juneau.startOfferCount()==2);check(juneau.startOffer()==15.0);check(juneau.homes()==1);
            var january=ReportStore.run(db,ReportStore.Range.month(2026,1),false).get(0);check(january.startOfferCount()==2);check(january.offerCloseCount()==1);check(january.startClose()==30.0);check(january.homes()==2);check(january.price().compareTo(new BigDecimal("200000.00"))==0);
            var february=ReportStore.run(db,ReportStore.Range.month(2026,2),false).get(0);check(february.startOfferCount()==0);check(february.startOffer()==null);check(february.offerClose()==30.0);check(february.startClose()==50.0);check(february.homes()==1);
            var singleDay=ReportStore.run(db,new ReportStore.Range(LocalDate.of(2026,1,31),LocalDate.of(2026,1,31)),false).get(0);check(singleDay.homes()==2);check(singleDay.startOfferCount()==0);
            SwingUtilities.invokeAndWait(()->{
                var panel=new ReportsPanel(db);check(panel.period.getSelectedItem().equals("All time"));check(panel.model.getRowCount()==1);check(!panel.month.isEnabled());
                panel.period.setSelectedItem("Monthly");panel.year.setValue(2026);panel.month.setSelectedIndex(1);panel.refresh();check(panel.model.getValueAt(0,8).equals(1L));check(panel.range().to().equals(LocalDate.of(2026,2,28)));
                panel.period.setSelectedItem("Quarterly");panel.quarter.setSelectedIndex(0);check(panel.range().to().equals(LocalDate.of(2026,3,31)));
                panel.period.setSelectedItem("Yearly");check(panel.range().from().equals(LocalDate.of(2026,1,1)));
                panel.period.setSelectedItem("Custom range");panel.from.setDate(LocalDate.of(2026,1,31));panel.to.setDate(LocalDate.of(2026,1,31));panel.refresh();check(panel.model.getValueAt(0,8).equals(2L));
                panel.period.setSelectedItem("All time");panel.grouping.setSelectedIndex(1);panel.refresh();check(panel.model.getRowCount()==3);check(panel.status.getText().contains("By city"));
            });
        }return checks;
    }
}
