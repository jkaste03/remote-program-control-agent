import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import command.CommandAction;
import manager.ProcessManager;

public class Agent {
    private static final String SHORTCUT_FOLDER = "C:\\RemoteServerControlAgent\\program-shortcuts";

    public static void main(String[] args) throws Exception {
        ProcessManager manager = new ProcessManager(Paths.get(SHORTCUT_FOLDER));

        // Demo: start og stopp (eksempel). I det virkelige større programmet vil man
        // bare kalle manager.startProgram(...) og manager.stopProgram(...).
        String demoShortcut = "start.bat.lnk";

        manager.handleCommand(demoShortcut, CommandAction.START);
        manager.handleCommand("paintdotnet.exe.lnk", CommandAction.START);

        // Simuler ventetid for demo (70s)
        TimeUnit.SECONDS.sleep(70);

        manager.handleCommand(demoShortcut, CommandAction.STOP);
        manager.handleCommand("paintdotnet.exe.lnk", CommandAction.STOP);
    }
}