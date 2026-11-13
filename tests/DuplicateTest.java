import java.sql.*;
import java.util.*;

final class DuplicateTest {
    static int checks;
    static void check(boolean value){if(!value)throw new AssertionError("Duplicate check "+checks);checks++;}
    static PropertyStore.Property property(PropertyAddress address){return new PropertyStore.Property(null,null,address.formatted(),"Listed","Notes",address);}
    static int run()throws Exception {
        var plain=new PropertyAddress("12","","Main","","","Juneau","ak","99801");plain.validate();check(plain.direction().isEmpty());check(plain.type().isEmpty());
        for(int missing:new int[]{0,2,5,6,7}) {
            String[] f={"12","","Main","","","Juneau","AK","99801"};f[missing]="";
            try{new PropertyAddress(f[0],f[1],f[2],f[3],f[4],f[5],f[6],f[7]).validate();throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}
        }
        var a=new PropertyAddress("123","North","Oak","Street","2","Anchorage","ak","99501");
        var b=new PropertyAddress("123","N.","OAK","St.","2","ANCHORAGE","AK","99501-1234");
        check(a.key().equals(b.key()));check(Duplicates.same(property(a),property(b)));
        check(!Duplicates.same(property(a),property(new PropertyAddress("123","N","Oak","St","3","Anchorage","AK","99501"))));
        check(!Duplicates.same(property(a),property(new PropertyAddress("124","N","Oak","St","2","Anchorage","AK","99501"))));
        try(var db=DriverManager.getConnection("jdbc:sqlite::memory:");var s=db.createStatement()) {
            s.execute("CREATE TABLE clients(id INTEGER PRIMARY KEY,name TEXT NOT NULL,phone TEXT,email TEXT,status TEXT,notes TEXT)");ClientStore.migrate(db);
            ClientStore.save(db,null,"Renee","Smith","(907) 555-0100","renee@example.test","Active","",ClientStore.Type.SELLER);
            ClientStore.save(db,null,"Alex","Brown","","","Lead","",ClientStore.Type.BUYER);
            check(Duplicates.clients(db,null,"RENEE","SMITH","","").size()==1);
            check(Duplicates.clients(db,null,"Different","Person","+1 9075550100","").size()==1);
            check(Duplicates.clients(db,null,"Different","Person",""," RENEE@example.test ").size()==1);
            check(Duplicates.clients(db,1L,"Renee","Smith","9075550100","renee@example.test").isEmpty());
            check(Duplicates.clients(db,null,"New","Person","","").isEmpty());
            check(Duplicates.clients(db,null,"New","Person","","ren.ee@example.test").isEmpty());
            s.execute("CREATE TABLE properties(id INTEGER PRIMARY KEY,client_id INTEGER REFERENCES clients(id),address TEXT NOT NULL,status TEXT,notes TEXT)");
            s.execute("INSERT INTO properties VALUES(5,1,'123 North Oak Street Unit 2, Anchorage, AK 99501','Listed','Keep legacy notes')");
            s.execute("INSERT INTO properties VALUES(6,NULL,'Incomplete legacy address','Lead','Keep unknown')");
            PropertyStore.migrate(db);PropertyStore.migrate(db);
            var legacy=PropertyStore.load(db,5);check(legacy.address().equals("123 North Oak Street Unit 2, Anchorage, AK 99501"));check(legacy.parts().key().equals(a.key()));
            check(legacy.notes().equals("Keep legacy notes"));check(legacy.clientId()==1L);
            check(PropertyStore.load(db,6).address().equals("Incomplete legacy address"));check(!PropertyStore.load(db,6).parts().complete());
            check(Duplicates.properties(db,property(b),java.util.List.of()).size()==1);
            var self=new PropertyStore.Property(5L,1L,b.formatted(),"Listed","",b);check(Duplicates.properties(db,self,java.util.List.of()).isEmpty());
            check(Duplicates.properties(db,property(plain),java.util.List.of()).isEmpty());
            check(Duplicates.properties(db,property(plain),java.util.List.of(property(plain))).size()==1);
            PropertyStore.save(db,property(plain));var saved=PropertyStore.list(db,null).stream().filter(p->p.parts().complete()).findFirst().orElseThrow();
            check(saved.parts().equals(plain));check(saved.address().equals("12 Main, Juneau, AK 99801"));
            PropertyStore.saveClient(db,2L,()->ClientStore.save(db,2L,"Alex","Brown","","","Lead","",ClientStore.Type.BUYER),java.util.List.of(saved));
            check(PropertyStore.list(db,2L).get(0).parts().equals(plain));
            PropertyStore.saveClient(db,2L,()->ClientStore.save(db,2L,"Alex","Brown","","","Lead","",ClientStore.Type.BUYER),java.util.List.of());
            check(PropertyStore.load(db,saved.id()).parts().equals(plain));
            // Deletion is scoped to the reviewed client link; reassigned properties require a new warning.
            try{PropertyStore.delete(db,5L,null);throw new AssertionError();}catch(SQLException expected){check(PropertyStore.load(db,5L).clientId()==1L);}
            PropertyStore.delete(db,5L,1L);check(PropertyStore.list(db,1L).isEmpty());check(ClientStore.load(db,1L).name().equals("Renee Smith"));
            PropertyStore.delete(db,saved.id(),null);
            try{PropertyStore.load(db,saved.id());throw new AssertionError();}catch(SQLException expected){checks++;}
            try{PropertyStore.delete(db,saved.id(),null);throw new AssertionError();}catch(SQLException expected){checks++;}
        }
        return checks;
    }
}
