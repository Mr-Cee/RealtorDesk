import java.nio.file.*;

/** Runs only in the release workflow: exercises the public updater without installing anything. */
public class ReleaseDownloadCheck {
    public static void main(String[] args) throws Exception {
        String repository=args[0], expected=args[1];
        try(var client=Updater.client()) {
            Updater.Update update=null;
            Exception last=null;
            for(int attempt=0;attempt<6;attempt++) {
                try {
                    update=Updater.latest(client,repository,"0.0.0").orElseThrow();
                    if(!expected.equals(update.version())) throw new Exception("Latest release is not "+expected);
                    break;
                } catch(Exception e) { last=e;update=null;if(attempt<5)Thread.sleep(2000); }
            }
            if(update==null)throw new Exception("Published release could not be discovered",last);
            Path root=Files.createTempDirectory("RealtorDesk-public-release-check-");
            try {
                Path installer=Updater.download(client,update,root,p->{});
                try { if(Files.size(installer)!=update.size())throw new AssertionError("Downloaded size mismatch"); }
                finally {Files.delete(installer);Files.delete(installer.getParent());}
                System.out.println("PASS: public GitHub release "+update.version()+" discovered, downloaded and SHA-256 verified using the bundled runtime");
            } finally {Files.delete(root);}
        }
    }
}
