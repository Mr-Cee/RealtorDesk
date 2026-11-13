import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.function.IntConsumer;

/** App-owned temporary cache. Pending installers never expire just because installation was deferred. */
final class UpdateCache {
    static final String DIRECTORY="RealtorDesk-updates";
    static final Duration LOG_RETENTION=Duration.ofDays(7);
    static Path root(Path temp) throws IOException {
        Path root=temp.toAbsolutePath().normalize().resolve(DIRECTORY);
        Files.createDirectories(root);
        if(Files.isSymbolicLink(root)||!root.toRealPath().getParent().equals(temp.toRealPath()))throw new IOException("Unsafe update cache location.");
        return root;
    }
    static String key(Updater.Update update) throws Exception {
        String identity=update.url()+"\n"+update.sha256()+"\n"+update.size();
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)));
        return "v"+update.version()+"-"+hash.substring(0,16);
    }
    static Path entry(Path temp,Updater.Update update) throws Exception {
        if(!update.name().equals("RealtorDesk-"+update.version()+".msi"))throw new IOException("Invalid installer filename.");
        Updater.compareVersions(update.version(),"0.0.0");
        Path root=root(temp),entry=root.resolve(key(update));Files.createDirectories(entry);
        if(Files.isSymbolicLink(entry)||!entry.toRealPath().getParent().equals(root.toRealPath()))throw new IOException("Unsafe update cache entry.");
        return entry;
    }
    record Guard(FileChannel channel,FileLock lock) implements AutoCloseable {
        public void close() throws IOException {try{lock.release();}finally{channel.close();}}
    }
    static Guard lock(Path entry) throws IOException {
        Path path=entry.resolve(".cache.lock");
        if(Files.isSymbolicLink(path))throw new IOException("Unsafe cache lock.");
        var channel=FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        try {
            var lock=channel.tryLock();
            if(lock==null)throw new IOException("Another RealtorDesk instance is using this update. Try again shortly.");
            return new Guard(channel,lock);
        } catch(Exception e) {channel.close();throw new IOException("Another RealtorDesk instance is using this update. Try again shortly.",e);}
    }
    static boolean installing(Path entry) {
        try {
            Path marker=entry.resolve("installing.pid");
            if(Files.isSymbolicLink(marker))return true;
            if(!Files.exists(marker))return false;
            long pid=Long.parseLong(Files.readString(marker).trim());
            return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
        } catch(Exception e) {return true;}
    }
    static boolean verified(Path file,Updater.Update update) throws Exception {
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)!=update.size())return false;
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        try(var input=Files.newInputStream(file)) {byte[] bytes=new byte[64*1024];for(int n;(n=input.read(bytes))!=-1;)hash.update(bytes,0,n);}
        return HexFormat.of().formatHex(hash.digest()).equalsIgnoreCase(update.sha256());
    }
    static void remember(Path entry,Updater.Update update) throws IOException {
        var json=new JsonObject();json.addProperty("format","RealtorDesk-update-cache-v1");
        json.addProperty("version",update.version());json.addProperty("name",update.name());json.addProperty("url",update.url().toString());
        json.addProperty("size",update.size());json.addProperty("sha256",update.sha256());
        Path file=entry.resolve("update.json"),pending=entry.resolve("update.json.tmp");
        if(Files.isSymbolicLink(file)||Files.isSymbolicLink(pending))throw new IOException("Unsafe cache metadata.");
        Files.writeString(pending,json.toString(),StandardCharsets.UTF_8);
        Files.move(pending,file,StandardCopyOption.REPLACE_EXISTING);
    }
    static Updater.Update read(Path entry) throws Exception {
        Path file=entry.resolve("update.json");if(Files.isSymbolicLink(file))throw new IOException("Unsafe cache metadata.");
        var json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        if(!"RealtorDesk-update-cache-v1".equals(json.get("format").getAsString()))throw new IOException("Unknown cache metadata.");
        var update=new Updater.Update(json.get("version").getAsString(),json.get("name").getAsString(),URI.create(json.get("url").getAsString()),json.get("size").getAsLong(),json.get("sha256").getAsString());
        Updater.compareVersions(update.version(),"0.0.0");
        if(!update.name().equals("RealtorDesk-"+update.version()+".msi")||!update.sha256().matches("[a-f0-9]{64}")||update.size()<=0||update.size()>Updater.MAX_SIZE||!key(update).equals(entry.getFileName().toString()))throw new IOException("Invalid cache metadata.");
        return update;
    }
    static Optional<Path> find(Updater.Update update,Path temp) throws Exception {
        Path entry=entry(temp,update);
        try(var guard=lock(entry)) {
            if(installing(entry))throw new IOException("This update is already being installed. Wait for installation to finish.");
            Path file=entry.resolve(update.name());
            if(verified(file,update)){remember(entry,update);return Optional.of(file);}
            // A damaged or interrupted download cannot be reused.
            Files.deleteIfExists(file);
            return Optional.empty();
        }
    }
    static Path prepare(java.net.http.HttpClient client,Updater.Update update,Path temp,IntConsumer progress) throws Exception {
        Path entry=entry(temp,update);
        try(var guard=lock(entry)) {
            if(installing(entry))throw new IOException("This update is already being installed. Wait for installation to finish.");
            Path file=entry.resolve(update.name());
            if(verified(file,update)){remember(entry,update);progress.accept(100);return file;}
            Files.deleteIfExists(file);
            // Reuse a verified installer left by a pre-cache version, if present.
            try(var old=Files.newDirectoryStream(temp,"RealtorDesk-update-*")) {
                for(Path directory:old) {
                    if(!safeChild(temp,directory))continue;
                    Path candidate=directory.resolve(update.name());
                    if(verified(candidate,update)){Files.copy(candidate,file);remember(entry,update);progress.accept(100);return file;}
                }
            }
            Path downloaded=Updater.download(client,update,entry,progress);
            Files.move(downloaded,file);Files.delete(downloaded.getParent());
            remember(entry,update);return file;
        }
    }
    static boolean safeChild(Path parent,Path child) throws IOException {
        return Files.isDirectory(child,LinkOption.NOFOLLOW_LINKS)&&!Files.isSymbolicLink(child)
                &&child.toRealPath().getParent().equals(parent.toRealPath());
    }
    static void removeTree(Path allowedParent,Path directory) throws IOException {
        if(!safeChild(allowedParent,directory))throw new IOException("Refusing to clean an unsafe update directory.");
        Path real=directory.toRealPath();
        // Validate every resolved path before deleting anything; never traverse a link to another folder.
        java.util.List<Path> paths;
        try(var walk=Files.walk(directory)){paths=walk.sorted(Comparator.reverseOrder()).toList();}
        for(Path path:paths)if(Files.isSymbolicLink(path)||!path.toRealPath().startsWith(real))throw new IOException("Refusing to follow a link during update cleanup.");
        for(Path path:paths)Files.deleteIfExists(path);
    }
    static boolean old(Path file,Instant now) throws IOException {
        return Files.getLastModifiedTime(file,LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(now.minus(LOG_RETENTION));
    }
    static void cleanup(Path temp,String installedVersion,Instant now) throws IOException {
        Path root=root(temp);
        try(var entries=Files.newDirectoryStream(root)) {
            for(Path entry:entries) {
                try {
                    if(!entry.getFileName().toString().matches("v[0-9]+\\.[0-9]+\\.[0-9]+-[a-f0-9]{16}")||!safeChild(root,entry))continue;
                    boolean wasOld=old(entry,now);
                    boolean remove=false;
                    try(var guard=lock(entry)) {
                        if(installing(entry))continue;
                        Updater.Update update=null;
                        try {update=read(entry);}catch(Exception ignored) { }
                        if(update!=null)remove=Updater.compareVersions(update.version(),installedVersion)<=0;
                        else {
                            String version=entry.getFileName().toString().substring(1).split("-",2)[0];
                            boolean hasInstaller=Files.isRegularFile(entry.resolve("RealtorDesk-"+version+".msi"),LinkOption.NOFOLLOW_LINKS);
                            remove=Updater.compareVersions(version,installedVersion)<=0||(!hasInstaller&&wasOld);
                        }
                        if(!remove)try(var files=Files.newDirectoryStream(entry)) {
                            for(Path file:files) {
                                String name=file.getFileName().toString();
                                if(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)&&(name.endsWith(".log")||name.equals("install-failed.txt"))&&old(file,now))Files.deleteIfExists(file);
                                if(name.equals("installing.pid"))Files.deleteIfExists(file);
                                if(name.startsWith("RealtorDesk-update-")&&safeChild(entry,file)&&old(file,now))removeTree(entry,file);
                            }
                        }
                    }
                    if(remove)removeTree(root,entry);
                } catch(Exception ignored) { /* Locked or incomplete caches are retried at the next startup/check. */ }
            }
        }
        // Older releases used separate random directories without manifests.
        try(var entries=Files.newDirectoryStream(temp,"RealtorDesk-update-*")) {
            for(Path entry:entries)try {
                if(!safeChild(temp,entry))continue;
                boolean remove=false,hasInstaller=false,unknown=false;
                try(var files=Files.newDirectoryStream(entry)) {
                    for(Path file:files) {
                        String name=file.getFileName().toString();
                        if(name.matches("RealtorDesk-[0-9]+\\.[0-9]+\\.[0-9]+\\.msi")) {
                            hasInstaller=true;
                            String version=name.substring("RealtorDesk-".length(),name.length()-4);
                            if(Updater.compareVersions(version,installedVersion)<=0)remove=true;
                            else {remove=false;unknown=true;}
                        } else if(!name.equals("updater-output.log")&&!name.matches("RealtorDesk-[0-9]+\\.[0-9]+\\.[0-9]+\\.msi(\\.log|\\.part)"))unknown=true;
                    }
                }
                if(!unknown&&(remove||(!hasInstaller&&old(entry,now))))removeTree(temp,entry);
            }catch(Exception ignored) { }
        }
    }
    static void cleanupQuietly(Path temp,String installedVersion) {
        try {cleanup(temp,installedVersion,Instant.now());}catch(Exception ignored) { }
    }
}
