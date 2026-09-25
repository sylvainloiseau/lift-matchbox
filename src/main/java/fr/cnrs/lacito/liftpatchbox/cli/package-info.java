/**
 * The command-line tool.
 *
 * <p>{@link fr.cnrs.lacito.liftpatchbox.cli.Main} takes a script, an optional
 * input dictionary and an output path, and applies the one to the other. It
 * always validates before it writes, and never writes a partially applied
 * dictionary: a script either succeeds completely or leaves the output file
 * unwritten.</p>
 *
 * <p>{@link fr.cnrs.lacito.liftpatchbox.cli.Logging} installs the process-wide
 * logging the tool owns. The library itself configures no logging of its own,
 * which is what an embedding application expects; it logs through
 * {@code java.util.logging} and leaves the handlers to whoever owns the
 * process.</p>
 */
package fr.cnrs.lacito.liftpatchbox.cli;
