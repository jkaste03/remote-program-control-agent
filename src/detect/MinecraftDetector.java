package detect;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Locale;
import java.util.Scanner;

public class MinecraftDetector {
    public static boolean isMinecraftServer(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".jar")) {
            return true;
        }
        if (name.endsWith(".bat") || name.endsWith(".cmd") || name.endsWith(".sh")) {
            try (Scanner scanner = new Scanner(file)) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine().toLowerCase(Locale.ROOT);
                    if (line.contains("java") && line.contains(".jar"))
                        return true;
                    if (line.contains("fabric") || line.contains("minecraft") || line.contains("paper")
                            || line.contains("spigot"))
                        return true;
                }
            } catch (FileNotFoundException e) {
                return false;
            }
        }
        return false;
    }
}
