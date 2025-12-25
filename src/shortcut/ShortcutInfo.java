package shortcut;

import java.io.File;

public class ShortcutInfo {
    private final String targetPath;
    private final String arguments;
    private final String workingDirectory;

    public ShortcutInfo(String targetPath, String arguments, String workingDirectory) {
        this.targetPath = targetPath == null ? "" : targetPath;
        this.arguments = arguments == null ? "" : arguments;
        this.workingDirectory = workingDirectory == null ? "" : workingDirectory;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public String getArguments() {
        return arguments;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public File getTargetFile() {
        return new File(targetPath);
    }
}
