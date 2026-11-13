import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;

public class UpdaterTest {
    static int passed;
    static void check(boolean condition) { if(!condition)throw new AssertionError();passed++; }
    interface Attempt { void run() throws Exception; }
    static void rejects(Attempt attempt) throws Exception {
        try { attempt.run(); } catch(IOException|IllegalArgumentException e) {passed++;return;}
        throw new AssertionError("Expected rejection");
    }
    static String hash(byte[] content) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));}
    static JsonObject release(byte[] bytes) throws Exception {
        var release=new JsonObject();release.addProperty("tag_name","v1.2.0");release.addProperty("draft",false);release.addProperty("prerelease",false);
        var asset=new JsonObject();asset.addProperty("name","RealtorDesk-1.2.0.msi");asset.addProperty("size",bytes.length);
        asset.addProperty("digest","sha256:"+hash(bytes));asset.addProperty("browser_download_url","https://github.com/Mr-Cee/RealtorDesk/releases/download/v1.2.0/RealtorDesk-1.2.0.msi");
        var assets=new JsonArray();assets.add(asset);release.add("assets",assets);return release;
    }
    public static void main(String[] args) throws Exception {
        // GitHub TLS needs the EC provider in the trimmed, bundled Java runtime.
        check(Security.getProvider("SunEC")!=null);
        var ec=KeyPairGenerator.getInstance("EC");ec.initialize(256);check(ec.generateKeyPair()!=null);
        check(Updater.compareVersions("v1.10.0","1.2.9")>0);
        check(Updater.compareVersions("1.2.0","1.2.0")==0);
        check(Updater.compareVersions("0.9.9","1.0.0")<0);
        rejects(()->Updater.compareVersions("1.2.0-beta","1.0.0"));
        rejects(()->Updater.compareVersions("01.2.0","1.0.0"));
        rejects(()->Updater.validateRepository("owner/repo/../../other"));
        byte[] bytes="test installer bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var json=release(bytes);var update=Updater.parseRelease(json,"Mr-Cee/RealtorDesk","1.1.0").orElseThrow();
        check(Updater.parseRelease(json,"Mr-Cee/RealtorDesk","1.2.0").isEmpty());
        check(Updater.parseRelease(json,"Mr-Cee/RealtorDesk","2.0.0").isEmpty());
        var bad=json.deepCopy();bad.addProperty("prerelease",true);check(Updater.parseRelease(bad,"Mr-Cee/RealtorDesk","1.1.0").isEmpty());
        bad=json.deepCopy();bad.getAsJsonArray("assets").get(0).getAsJsonObject().remove("digest");final var noHash=bad;
        rejects(()->Updater.parseRelease(noHash,"Mr-Cee/RealtorDesk","1.1.0"));
        bad=json.deepCopy();bad.getAsJsonArray("assets").get(0).getAsJsonObject().addProperty("browser_download_url","https://evil.example/setup.msi");final var wrongUrl=bad;
        rejects(()->Updater.parseRelease(wrongUrl,"Mr-Cee/RealtorDesk","1.1.0"));
        check(!Updater.allowedDownloadHost(URI.create("http://github.com/test")));
        check(!Updater.allowedDownloadHost(URI.create("https://github.com.evil.example/test")));
        check(!Updater.allowedDownloadHost(URI.create("https://user@github.com/test")));
        Path root=Files.createTempDirectory("realtordesk-updater-tests-");
        try {
            var progress=new ArrayList<Integer>();
            try(var client=new FakeClient(bytes,302,"https://release-assets.githubusercontent.com/test")) {
                Path file=Updater.download(client,update,root,progress::add);check(Arrays.equals(Files.readAllBytes(file),bytes));
                check(progress.get(progress.size()-1)==100);Files.delete(file);Files.delete(file.getParent());
            }
            try(var client=new FakeClient("corrupt".getBytes(),200,null)) { rejects(()->Updater.download(client,update,root,x->{})); }
            try(var client=new FakeClient(bytes,302,"https://evil.example/setup.msi")) { rejects(()->Updater.download(client,update,root,x->{})); }
            try(var client=new FakeClient(bytes,404,null)) { rejects(()->Updater.download(client,update,root,x->{})); }
            try(var files=Files.list(root)) {check(files.count()==0);}
            Path output=root.resolve("bad.msi");
            rejects(()->Updater.copyVerified(new ByteArrayInputStream(bytes),output,bytes.length-1,hash(bytes),x->{}));check(!Files.exists(output));
            rejects(()->Updater.copyVerified(new ByteArrayInputStream(bytes),output,bytes.length,"0".repeat(64),x->{}));check(!Files.exists(output));
            String script=Updater.installerScript(Path.of("C:/Temp/O'Brien/update.msi"),Path.of("C:/Apps/O'Brien Realtor"),12345);
            check(script.contains("Wait-Process -Id 12345"));check(script.contains("O''Brien"));check(script.contains("/passive /norestart INSTALLFOLDER="));
            check(script.contains("@(0,3010)"));check(script.indexOf("Wait-Process")<script.indexOf("msiexec.exe"));
            Files.writeString(root.resolve("handoff.ps1"),script);
            // Parse the real handoff without executing any installation or launching an app.
            if(System.getProperty("os.name").startsWith("Windows")) {
                var parser=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-Command",
                    "$tokens=$null;$errors=$null;[System.Management.Automation.Language.Parser]::ParseFile('"+root.resolve("handoff.ps1").toString().replace("'","''")+"',[ref]$tokens,[ref]$errors)|Out-Null;if($errors.Count){$errors|Out-String|Write-Error;exit 1}")
                    .redirectErrorStream(true).start();
                String result=new String(parser.getInputStream().readAllBytes());check(parser.waitFor()==0);if(!result.isBlank())System.out.println(result);
            }
            Files.delete(root.resolve("handoff.ps1"));
        } finally { Files.delete(root); }
        try(var db=java.sql.DriverManager.getConnection("jdbc:sqlite::memory:");var s=db.createStatement()) {
            s.execute("CREATE TABLE clients(id INTEGER PRIMARY KEY,name TEXT)");s.execute("INSERT INTO clients(name) VALUES('Test')");
            try(var r=s.executeQuery("SELECT name FROM clients")){check(r.next()&&r.getString(1).equals("Test"));}
        }
        System.out.println("PASS: "+passed+" updater, installer-handoff and SQLite checks");
    }
    static class FakeClient extends HttpClient {
        final byte[] bytes;final int status;final String location;int requests;
        FakeClient(byte[] bytes,int status,String location){this.bytes=bytes;this.status=status;this.location=location;}
        @SuppressWarnings("unchecked") public <T> HttpResponse<T> send(HttpRequest request,HttpResponse.BodyHandler<T> handler) {
            int code=requests++==0?status:200;
            return new HttpResponse<T>() {
                public int statusCode(){return code;}
                public HttpRequest request(){return request;}
                public Optional<HttpResponse<T>> previousResponse(){return Optional.empty();}
                public HttpHeaders headers(){return HttpHeaders.of(location==null?Map.of():Map.of("location",List.of(location)),(a,b)->true);}
                public T body(){return (T)new ByteArrayInputStream(bytes);}
                public Optional<SSLSession> sslSession(){return Optional.empty();}
                public URI uri(){return request.uri();}
                public HttpClient.Version version(){return HttpClient.Version.HTTP_1_1;}
            };
        }
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r,HttpResponse.BodyHandler<T> h){throw new UnsupportedOperationException();}
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r,HttpResponse.BodyHandler<T> h,HttpResponse.PushPromiseHandler<T> p){throw new UnsupportedOperationException();}
        public Optional<CookieHandler> cookieHandler(){return Optional.empty();}
        public Optional<Duration> connectTimeout(){return Optional.empty();}
        public Redirect followRedirects(){return Redirect.NEVER;}
        public Optional<ProxySelector> proxy(){return Optional.empty();}
        public SSLContext sslContext(){try{return SSLContext.getDefault();}catch(Exception e){throw new RuntimeException(e);}}
        public SSLParameters sslParameters(){return new SSLParameters();}
        public Optional<Authenticator> authenticator(){return Optional.empty();}
        public Version version(){return Version.HTTP_1_1;}
        public Optional<Executor> executor(){return Optional.empty();}
        public void close(){}
    }
}
