import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import command.CommandAction;
import manager.ProcessManager;

public class Agent {
    private static final String SHORTCUT_FOLDER = "C:\\RemoteServerControlAgent\\program-shortcuts";

    public static void main(String[] args) throws Exception {
        ProcessManager manager = new ProcessManager(Paths.get(SHORTCUT_FOLDER));

        // Demo: start og stopp (eksempel). I det virkelige større programmet vil man
        // bare kalle manager.startProgram(...) og manager.stopProgram(...).
        String demoShortcut = "paintdotnet.exe.lnk";

        manager.handleCommand(demoShortcut, CommandAction.START);

        // Simuler ventetid for demo (30s)
        TimeUnit.SECONDS.sleep(30);

        manager.handleCommand(demoShortcut, CommandAction.STOP);
    }
}
