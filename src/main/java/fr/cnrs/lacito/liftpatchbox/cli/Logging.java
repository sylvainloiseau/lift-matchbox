package fr.cnrs.lacito.liftpatchbox.cli;

import java.io.PrintStream;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

/**
 * The logging setup of the command-line tool.
 *
 * <p>The library logs through {@code java.util.logging} and configures nothing of
 * its own, which is what an embedding application expects. The tool, which owns
 * the process, installs a single handler on the library's root logger and sets
 * the level from its {@code --verbose} and {@code --quiet} options.</p>
 *
 * <p>The three levels correspond to three readerships:</p>
 *
 * <ul>
 *   <li>{@code SEVERE} and {@code WARNING} — every error and warning, with its
 *       code and the line of the script it was raised on. Always shown;</li>
 *   <li>{@code INFO} — what a run did: one line per phase, and the summary of
 *       components created per type and operations per verb;</li>
 *   <li>{@code FINE} — how the commands were built: how many items were parsed,
 *       how many static errors were found, how many mutations were journalled.</li>
 * </ul>
 */
public final class Logging {

    /** The root logger of the library, which every class of it logs through. */
    public static final String ROOT = "fr.cnrs.lacito.liftpatchbox";

    private Logging() {
    }

    /**
     * Install a handler on the library's logger and set its level.
     *
     * @param level  the level to log at
     * @param stream where to write, typically {@code System.err}
     */
    public static void configure(Level level, PrintStream stream) {
        Logger root = Logger.getLogger(ROOT);
        for (Handler existing : root.getHandlers()) {
            root.removeHandler(existing);
        }
        Handler handler = new StreamHandler(stream, new PlainFormatter()) {
            @Override
            public synchronized void publish(LogRecord record) {
                super.publish(record);
                flush();
            }
        };
        handler.setLevel(level);
        root.addHandler(handler);
        root.setLevel(level);
        root.setUseParentHandlers(false);
    }

    /**
     * A one-line formatter: the level, then the message. The class and method names
     * the default formatter prints are noise for a lexicographer reading why a
     * script was refused.
     */
    private static final class PlainFormatter extends Formatter {

        @Override
        public String format(LogRecord record) {
            String level = switch (record.getLevel().getName()) {
                case "SEVERE" -> "error";
                case "WARNING" -> "warning";
                case "INFO" -> "";
                default -> "debug";
            };
            String message = formatMessage(record);
            return (level.isEmpty() ? "" : level + ": ") + message + System.lineSeparator();
        }
    }
}
