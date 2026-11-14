import java.sql.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.math.*;
import java.util.*;

final class ReportStore {
    record Range(LocalDate from,LocalDate to) {
        Range {if((from==null)!=(to==null)||from!=null&&from.isAfter(to))throw new IllegalArgumentException("Choose a valid start and end date.");}
        boolean includes(LocalDate date){return from==null||date!=null&&!date.isBefore(from)&&!date.isAfter(to);}
        static Range all(){return new Range(null,null);}
        static Range month(int year,int month){var m=YearMonth.of(year,month);return new Range(m.atDay(1),m.atEndOfMonth());}
        static Range quarter(int year,int quarter){if(quarter<1||quarter>4)throw new IllegalArgumentException("Invalid quarter");var first=YearMonth.of(year,(quarter-1)*3+1);return new Range(first.atDay(1),first.plusMonths(2).atEndOfMonth());}
        static Range year(int year){return new Range(LocalDate.of(year,1,1),LocalDate.of(year,12,31));}
        public String toString(){return from==null?"All time":from+" through "+to+" (inclusive)";}
    }
    record Row(String group,Double startOffer,long startOfferCount,Double offerClose,long offerCloseCount,Double startClose,long startCloseCount,BigDecimal price,long homes) { }
    static class Bucket {
        String label;long[] sums=new long[3],counts=new long[3];BigDecimal cents=BigDecimal.ZERO;long homes;
        Bucket(String label){this.label=label;}
        void interval(int slot,LocalDate start,LocalDate end,Range range){if(start!=null&&end!=null&&!end.isBefore(start)&&range.includes(end)){sums[slot]+=ChronoUnit.DAYS.between(start,end);counts[slot]++;}}
        Double average(int slot){return counts[slot]==0?null:(double)sums[slot]/counts[slot];}
        boolean empty(){return homes==0&&Arrays.stream(counts).sum()==0;}
        Row row(){return new Row(label,average(0),counts[0],average(1),counts[1],average(2),counts[2],homes==0?null:cents.movePointLeft(2).divide(BigDecimal.valueOf(homes),2,RoundingMode.HALF_UP),homes);}
    }
    static class Client {LocalDate start,offer,close;Set<String> cities=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);}
    static LocalDate date(String raw){try{return raw==null||raw.isBlank()?null:LocalDate.parse(raw);}catch(java.time.format.DateTimeParseException e){return null;}}
    static String city(String city,String state){String name=PropertyAddress.clean(city),region=PropertyAddress.clean(state).toUpperCase(Locale.ROOT);return name.isEmpty()?"Unspecified city":name+(region.isEmpty()?"":", "+region);}
    static Bucket bucket(Map<String,Bucket> buckets,String name){return buckets.computeIfAbsent(name.toLowerCase(Locale.ROOT),key->new Bucket(name));}
    static java.util.List<Row> run(Connection db,Range range,boolean byCity)throws SQLException {
        var buckets=new TreeMap<String,Bucket>();if(!byCity)bucket(buckets,"Overall");var clients=new LinkedHashMap<Long,Client>();
        try(var s=db.createStatement();var rows=s.executeQuery("SELECT c.id,c.date_start,c.date_offer,c.date_close,p.city,p.state FROM clients c LEFT JOIN properties p ON p.client_id=c.id ORDER BY c.id,p.id")) {
            while(rows.next()) {
                long id=rows.getLong(1);Client client=clients.get(id);
                if(client==null){client=new Client();client.start=date(rows.getString(2));client.offer=date(rows.getString(3));client.close=date(rows.getString(4));clients.put(id,client);}
                client.cities.add(city(rows.getString(5),rows.getString(6)));
            }
        }
        for(var client:clients.values())for(String group:byCity?client.cities:Set.of("Overall")) {
            var b=bucket(buckets,group);b.interval(0,client.start,client.offer,range);b.interval(1,client.offer,client.close,range);b.interval(2,client.start,client.close,range);
        }
        try(var s=db.createStatement();var rows=s.executeQuery("SELECT p.city,p.state,p.closed_price_cents,c.date_close FROM properties p LEFT JOIN clients c ON c.id=p.client_id WHERE LOWER(TRIM(p.status)) IN ('sold','closed') AND p.closed_price_cents IS NOT NULL AND p.closed_price_cents>=0")) {
            while(rows.next())if(range.includes(date(rows.getString(4)))){var b=bucket(buckets,byCity?city(rows.getString(1),rows.getString(2)):"Overall");b.cents=b.cents.add(BigDecimal.valueOf(rows.getLong(3)));b.homes++;}
        }
        return buckets.values().stream().filter(b->!byCity||!b.empty()).map(Bucket::row).toList();
    }
}
