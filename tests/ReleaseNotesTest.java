import java.nio.file.*;
import java.util.Properties;

public class ReleaseNotesTest {
    static int checks;
    static void check(boolean value) { if (!value) throw new AssertionError(); checks++; }
    public static void main(String[] args) throws Exception {
        Path folder = Files.createTempDirectory("RealtorDesk-notes-test-");
        Path file = folder.resolve("settings.properties");
        try {
            var settings = new Properties(); settings.setProperty("theme", "ocean");
            check(ReleaseNotes.shouldShow(settings, "0.1.10"));
            ReleaseNotes.markSeen(file, settings, "0.1.10");
            check(!ReleaseNotes.shouldShow(settings, "0.1.10"));
            var reopened = AppSettings.load(file);
            check(!ReleaseNotes.shouldShow(reopened, "0.1.10"));
            check(reopened.getProperty("theme").equals("ocean"));
            check(ReleaseNotes.shouldShow(reopened, "0.1.11"));
            ReleaseNotes.markSeen(file, reopened, "0.1.11");
            check(!ReleaseNotes.shouldShow(AppSettings.load(file), "0.1.11"));
            // A failed write must not suppress the popup on the next startup.
            var failed = new Properties(); failed.setProperty(ReleaseNotes.SEEN_VERSION, "0.1.9");
            try { ReleaseNotes.markSeen(file.resolve("invalid"), failed, "0.1.10"); throw new AssertionError(); }
            catch (java.io.IOException expected) { check(ReleaseNotes.shouldShow(failed, "0.1.10")); }
            String notes = ReleaseNotes.text();
            check(notes.lines().findFirst().orElse("").equals("RealtorDesk " + BuildInfo.VERSION));
            check(notes.contains("What's new"));
            check(notes.contains("Search documents"));
            check(notes.equals(Files.readString(Path.of("release-notes.txt"))));
            try (var files = Files.list(folder)) { check(files.count() == 1); }
        } finally { Files.deleteIfExists(file); Files.delete(folder); }
        System.out.println("PASS: " + checks + " release notes checks");
    }
}
