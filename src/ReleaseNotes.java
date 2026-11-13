import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Properties;
import javax.swing.*;
import java.awt.*;

final class ReleaseNotes {
    static final String SEEN_VERSION = "releaseNotesSeenVersion";
    static boolean shouldShow(Properties settings, String version) {
        return !version.equals(settings.getProperty(SEEN_VERSION));
    }
    static String text() throws IOException {
        try (var input = ReleaseNotes.class.getResourceAsStream("/release-notes.txt")) {
            if (input == null) throw new IOException("Release notes are missing from this installation.");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    static void markSeen(Path file, Properties settings, String version) throws IOException {
        var saved = new Properties(); saved.putAll(settings); saved.setProperty(SEEN_VERSION, version);
        AppSettings.save(file, saved);
        settings.setProperty(SEEN_VERSION, version);
    }
    static void show(Component parent, Path file, Properties settings, String version) {
        try {
            var notes = new JTextArea(text(), 20, 62);
            notes.setEditable(false); notes.setLineWrap(true); notes.setWrapStyleWord(true);
            notes.setFont(UIManager.getFont("Label.font")); notes.setCaretPosition(0);
            JOptionPane.showMessageDialog(parent, new JScrollPane(notes), "What's New - RealtorDesk " + version, JOptionPane.INFORMATION_MESSAGE);
            markSeen(file, settings, version);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(parent, "Unable to load or remember release notes: " + e.getMessage(), "What's New", JOptionPane.WARNING_MESSAGE);
        }
    }
}
