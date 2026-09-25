package fr.cnrs.lacito.liftpatchbox.ast;

import java.util.ArrayList;
import java.util.List;

/**
 * A whole script: an optional pragma and a sequence of items.
 *
 * <p>A script is the unit of atomicity (Part 2, section 12.2): it either
 * succeeds completely or leaves the dictionary exactly as it was.</p>
 *
 * @param pragma          the version pragma, or {@code null} when the script declares none
 * @param items           the top-level items, in source order
 * @param syntax          the surface syntax the script was parsed with
 * @param source          the name of the source, for diagnostics and plan documents
 * @param recognizedLines how many lines were recognized as commands, which is what
 *                        the {@code NO_COMMAND_RECOGNIZED} warning is based on
 */
public record Script(
    Pragma pragma,
    List<Item> items,
    Syntax syntax,
    String source,
    int recognizedLines
) {

    /**
     * Canonical constructor, taking an unmodifiable copy of the items.
     *
     * @param pragma          the version pragma, or {@code null}
     * @param items           the top-level items
     * @param syntax          the surface syntax
     * @param source          the name of the source
     * @param recognizedLines how many lines were recognized as commands
     */
    public Script {
        items = List.copyOf(items);
    }

    /**
     * The version this script targets, which is {@code 1.0} when no pragma is written.
     *
     * @return the version string
     */
    public String version() {
        return pragma == null ? "1.0" : pragma.version();
    }

    /**
     * Every command of the script, blocks included, flattened in source order.
     *
     * <p>A block header that is a command is included at the position it occupies,
     * before the commands of its body, which is the numbering rule of Part 2,
     * section 12.4.1.</p>
     *
     * @return the commands, in source order
     */
    public List<Command> allCommands() {
        List<Command> out = new ArrayList<>();
        collectCommands(items, out);
        return out;
    }

    private static void collectCommands(List<Item> items, List<Command> out) {
        for (Item i : items) {
            switch (i) {
                case Command c -> out.add(c);
                case Block b -> {
                    if (b.header() instanceof BlockHeader.Anchor a) {
                        out.add(a.command());
                    }
                    collectCommands(b.body(), out);
                }
                case Directive unused -> {
                    // Directives are not commands, whether or not they are operations.
                }
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (pragma != null) {
            sb.append(pragma).append(System.lineSeparator());
        }
        for (Item i : items) {
            sb.append(i).append(System.lineSeparator());
        }
        return sb.toString();
    }
}
