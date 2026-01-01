package process;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.function.Consumer;

import manager.ManagedProcess;

public class ProcessOutputLogger implements Runnable {
    private final ManagedProcess mp;
    private final String name;
    private final Runnable onPauseDetected;
    private final Consumer<String> onLine;

    private static final String PAUSE_MARKER = "press any key to continue";

    public ProcessOutputLogger(ManagedProcess mp, String name, Runnable onPauseDetected, Consumer<String> onLine) {
        this.mp = mp;
        this.name = name;
        this.onPauseDetected = onPauseDetected;
        this.onLine = onLine;
    }

    @Override
    public void run() {
        StringBuilder buffer = new StringBuilder();
        boolean pauseHandled = false;

        try (InputStreamReader reader = new InputStreamReader(mp.getProcess().getInputStream())) {
            int ch;
            while ((ch = reader.read()) != -1) {
                char c = (char) ch;
                if (c == '\r') {
                    continue; // normalize CRLF
                }

                if (c == '\n') {
                    emitLine(buffer);
                    buffer.setLength(0);
                } else {
                    buffer.append(c);
                    if (!pauseHandled && containsPause(buffer)) {
                        pauseHandled = true;
                        if (onPauseDetected != null) {
                            onPauseDetected.run();
                        }
                        // Stop reading further to avoid emitting the pause prompt line
                        break;
                    }
                }
            }

            // Emit any trailing characters on EOF
            if (buffer.length() > 0 && !pauseHandled) {
                emitLine(buffer);
            }

        } catch (IOException ignored) {
            // Swallow; logger should not crash agent
        }
    }

    private void emitLine(StringBuilder buffer) {
        if (buffer.length() > 0) {
            System.out.println("[" + name + "] " + buffer);
            if (onLine != null) {
                onLine.accept(buffer.toString());
            }
        }
    }

    private boolean containsPause(CharSequence seq) {
        return seq.toString().toLowerCase().contains(PAUSE_MARKER);
    }
}
