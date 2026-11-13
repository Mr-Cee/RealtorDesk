import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntConsumer;

/** Public GitHub release discovery, verified downloads and external installer handoff. */
final class Updater {
    static final long MAX_SIZE = 512L * 1024 * 1024;
    record Update(String version, String name, URI url, long size, String sha256) { }

    static HttpClient client() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }
    static void validateRepository(String repository) {
        if (repository == null || !repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))
            throw new IllegalArgumentException("Use a GitHub repository in owner/repository format.");
    }
    static int compareVersions(String first, String second) {
        String a = first.startsWith("v") ? first.substring(1) : first;
        String b = second.startsWith("v") ? second.substring(1) : second;
        String pattern = "(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)";
        if (!a.matches(pattern) || !b.matches(pattern)) throw new IllegalArgumentException("Unsupported release version.");
        String[] left = a.split("\\."), right = b.split("\\.");
        for (int i=0;i<3;i++) {
            int result = new java.math.BigInteger(left[i]).compareTo(new java.math.BigInteger(right[i]));
            if (result != 0) return result;
        }
        return 0;
    }
    static Optional<Update> latest(HttpClient client, String repository, String current) throws Exception {
        validateRepository(repository);
        var request = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + repository + "/releases/latest"))
                .timeout(Duration.ofSeconds(25)).header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28").header("User-Agent", "RealtorDesk/" + current).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) throw new IOException("No published release was found. Check the repository setting or try again later.");
        if (response.statusCode() == 403 || response.statusCode() == 429) throw new IOException("GitHub is limiting update checks. Please try again later.");
        if (response.statusCode() != 200) throw new IOException("GitHub returned HTTP " + response.statusCode());
        return parseRelease(JsonParser.parseString(response.body()).getAsJsonObject(), repository, current);
    }
    static Optional<Update> parseRelease(JsonObject release, String repository, String current) throws IOException {
        validateRepository(repository);
        if (release.get("draft").getAsBoolean() || release.get("prerelease").getAsBoolean()) return Optional.empty();
        String tag = release.get("tag_name").getAsString();
        if (compareVersions(tag, current) <= 0) return Optional.empty();
        String version = tag.startsWith("v") ? tag.substring(1) : tag;
        String name = "RealtorDesk-" + version + ".msi";
        for (JsonElement entry : release.getAsJsonArray("assets")) {
            JsonObject asset = entry.getAsJsonObject();
            if (!name.equals(asset.get("name").getAsString())) continue;
            String expected = "https://github.com/" + repository + "/releases/download/" + tag + "/" + name;
            URI url = URI.create(asset.get("browser_download_url").getAsString());
            if (!expected.equals(url.toString())) throw new IOException("The release installer has an unexpected download address.");
            long size = asset.get("size").getAsLong();
            if (size <= 0 || size > MAX_SIZE) throw new IOException("The release installer has an invalid size.");
            String digest = asset.has("digest") && !asset.get("digest").isJsonNull() ? asset.get("digest").getAsString() : "";
            if (!digest.matches("sha256:[a-fA-F0-9]{64}")) throw new IOException("GitHub has not supplied a SHA-256 hash for this installer. Try again later.");
            return Optional.of(new Update(version, name, url, size, digest.substring(7).toLowerCase(Locale.ROOT)));
        }
        throw new IOException("The newer release does not contain the Windows installer " + name + ".");
    }
    static boolean allowedDownloadHost(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null
                && (uri.getPort() == -1 || uri.getPort() == 443)
                && ("github.com".equalsIgnoreCase(uri.getHost()) || "release-assets.githubusercontent.com".equalsIgnoreCase(uri.getHost()));
    }
    static Path download(HttpClient client, Update update, Path temporaryRoot, IntConsumer progress) throws Exception {
        Path directory = Files.createTempDirectory(temporaryRoot, "RealtorDesk-update-");
        Path partial = directory.resolve(update.name() + ".part");
        try {
            URI url = update.url();
            for (int redirects=0;redirects<6;redirects++) {
                if (!allowedDownloadHost(url)) throw new IOException("The installer download redirected to an untrusted address.");
                var request = HttpRequest.newBuilder(url).timeout(Duration.ofMinutes(5))
                        .header("User-Agent", "RealtorDesk").build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream body = response.body()) {
                    int status = response.statusCode();
                    if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                        url = url.resolve(response.headers().firstValue("Location").orElseThrow(() -> new IOException("Missing download redirect.")));
                        continue;
                    }
                    if (status != 200) throw new IOException("Installer download returned HTTP " + status);
                    var timeout = Executors.newSingleThreadScheduledExecutor(r -> { var t=new Thread(r,"update-timeout");t.setDaemon(true);return t; });
                    var alarm = timeout.schedule(() -> { try { body.close(); } catch(IOException ignored) {} }, 5, TimeUnit.MINUTES);
                    try { copyVerified(body, partial, update.size(), update.sha256(), progress); }
                    finally { alarm.cancel(false); timeout.shutdownNow(); }
                    Path installer = directory.resolve(update.name());
                    Files.move(partial, installer);
                    return installer;
                }
            }
            throw new IOException("Too many download redirects.");
        } catch (Exception e) {
            Files.deleteIfExists(partial);
            Files.deleteIfExists(directory);
            throw e;
        }
    }
    static void copyVerified(InputStream input, Path output, long expectedSize, String expectedHash, IntConsumer progress) throws Exception {
        if (expectedSize <= 0 || expectedSize > MAX_SIZE || !expectedHash.matches("[a-fA-F0-9]{64}")) throw new IOException("Invalid installer metadata.");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long count=0; int previous=-1;
        try (var file = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[64*1024];
            for (int length; (length=input.read(buffer))!=-1;) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Download canceled.");
                count += length;
                if (count > expectedSize) throw new IOException("Installer download is larger than expected.");
                file.write(buffer,0,length); digest.update(buffer,0,length);
                int percent=(int)(count*100/expectedSize);
                if(percent!=previous) { progress.accept(percent); previous=percent; }
            }
        } catch (Exception e) { Files.deleteIfExists(output); throw e; }
        String actual=HexFormat.of().formatHex(digest.digest());
        if (count != expectedSize || !actual.equalsIgnoreCase(expectedHash)) {
            Files.deleteIfExists(output);
            throw new IOException("Installer verification failed. Nothing was installed; please retry the download.");
        }
    }
    static Path installationFolder() throws Exception {
        Path jar = Path.of(Updater.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path app = jar.getParent();
        Path root = app == null ? null : app.getParent();
        if (root == null || !app.getFileName().toString().equals("app") || !Files.exists(root.resolve("runtime/bin/javaw.exe")))
            throw new IOException("Install this version using its MSI first. In-app installation is available from the installed program.");
        return root.toAbsolutePath();
    }
    static String psLiteral(String value) { return "'" + value.replace("'", "''") + "'"; }
    static String installerScript(Path installer, Path installFolder, long pid) {
        String msi = installer.toAbsolutePath().toString(), folder = installFolder.toAbsolutePath().toString();
        if (msi.contains("\"") || folder.contains("\"") || msi.contains("\n") || folder.contains("\n")) throw new IllegalArgumentException("Invalid installer path.");
        String args = "/i \"" + msi + "\" /passive /norestart INSTALLFOLDER=\"" + folder + "\" /l*v \"" + msi + ".log\"";
        String launcher = installFolder.resolve("RealtorDesk.exe").toString();
        return "$ErrorActionPreference='Stop'\n"
                + "try {\nWait-Process -Id " + pid + " -ErrorAction SilentlyContinue\n"
                + "$setup=Start-Process -FilePath (Join-Path $env:WINDIR 'System32\\msiexec.exe') -ArgumentList " + psLiteral(args) + " -Wait -PassThru\n"
                + "if ($setup.ExitCode -notin @(0,3010)) { throw ('Installer failed with code '+$setup.ExitCode+'. Log: '+" + psLiteral(msi+".log") + ") }\n"
                + "Start-Process -FilePath " + psLiteral(launcher) + " -WorkingDirectory " + psLiteral(folder) + " -WindowStyle Hidden\n"
                + "} catch { Add-Type -AssemblyName System.Windows.Forms; [System.Windows.Forms.MessageBox]::Show($_.Exception.Message,'RealtorDesk update') | Out-Null }\n";
    }
    static Process launchInstaller(Path installer, Path folder) throws Exception {
        String script = installerScript(installer, folder, ProcessHandle.current().pid());
        String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        Path powershell = Path.of(System.getenv("WINDIR"), "System32/WindowsPowerShell/v1.0/powershell.exe");
        return new ProcessBuilder(powershell.toString(), "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-EncodedCommand", encoded)
                .redirectOutput(installer.resolveSibling("updater-output.log").toFile()).redirectErrorStream(true).start();
    }
}
