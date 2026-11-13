import java.io.*;
import java.nio.file.*;
import java.util.Properties;

final class AppSettings {
    static Properties load(Path file) throws IOException {
        var settings=new Properties();
        if(Files.exists(file))try(var input=Files.newInputStream(file)){settings.load(input);}
        return settings;
    }
    static void save(Path file,Properties settings) throws IOException {
        Path parent=file.toAbsolutePath().getParent();Files.createDirectories(parent);
        Path temporary=Files.createTempFile(parent,"settings-",".tmp");
        try {
            try(var output=Files.newOutputStream(temporary)){settings.store(output,"RealtorDesk preferences");}
            try {Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temporary);}
    }
}
