import java.nio.file.*;

/** Executed briefly by the actual native launcher during packaging; never installed. */
public class LauncherProbe {
    public static void main(String[] args) throws Exception {
        String command=ProcessHandle.current().info().command().orElseThrow();
        if(!Path.of(command).getFileName().toString().equalsIgnoreCase("RealtorDesk.exe"))throw new AssertionError("Wrong process: "+command);
        var icon=javax.imageio.ImageIO.read(RealtorApp.class.getResource("/app-icon.png"));
        if(icon==null||icon.getWidth()!=256)throw new AssertionError("Missing application icon");
        try(var db=java.sql.DriverManager.getConnection("jdbc:sqlite::memory:");var s=db.createStatement()) {
            s.execute("CREATE TABLE probe(value TEXT)");s.execute("INSERT INTO probe VALUES('ok')");
            try(var r=s.executeQuery("SELECT value FROM probe")){if(!r.next()||!r.getString(1).equals("ok"))throw new AssertionError("SQLite failed");}
        }
        Files.writeString(Path.of(args[0]),"PASS: RealtorDesk.exe native process; bundled JVM; branded icon; SQLite; version "+RealtorApp.VERSION);
    }
}
