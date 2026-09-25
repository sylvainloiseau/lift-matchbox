package fr.cnrs.lacito.liftpatchbox;

import fr.cnrs.lacito.liftpatchbox.ast.Syntax;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts the fenced examples of {@code specification.md}.
 *
 * <p>Appendix D states the second purpose of a machine-readable corpus: every
 * example written in Parts 1 to 3 is meant to be a legal script with a stated
 * outcome, and illegal ones keep surviving several reviews before being caught by
 * hand — a {@code create} written with square brackets, a selector giving half of
 * a natural identity, a trailing comma in a predicate list. Running the examples
 * makes that class of defect mechanical to detect.</p>
 *
 * <p>Only the two fences the specification uses for runnable scripts are picked
 * up: {@code ```LiftPatchRef} and {@code ```LiftPatchShort}. Fences marked
 * {@code text}, {@code json}, {@code ebnf}, {@code Path} or {@code Fragment} are
 * grammar sketches, path fragments and data, not scripts, and are left alone.</p>
 */
public final class SpecificationExamples {

    private SpecificationExamples() {
    }

    /**
     * One fenced example, with where it was written.
     *
     * @param syntax the surface syntax the fence declares
     * @param script the script text
     * @param line   the 1-based line of the opening fence in {@code specification.md}
     */
    public record Example(Syntax syntax, String script, int line) {

        /**
         * A short label naming the example by its position in the specification.
         *
         * @return the label, such as {@code "LiftPatchRef @ line 1791"}
         */
        public String label() {
            return syntax.pragmaName() + " @ line " + line;
        }

        /**
         * Whether the example elides part of a selector, as in
         * {@code delete all example[...] under sense[gloss@en = "pig"]}.
         *
         * <p>Such a block is an illustration of a shape rather than a script: the
         * three dots stand for a predicate list the surrounding prose is not about.
         * It is not runnable, and running it would only report the elision.</p>
         *
         * @return {@code true} when the script contains an elided selector
         */
        public boolean isElided() {
            return script.contains("[...]") || script.contains("[…]");
        }

        /**
         * The first line of the script, for a test name that fits on one line.
         *
         * @return the first non-blank line, truncated
         */
        public String firstLine() {
            for (String line : script.split("\\R")) {
                if (!line.isBlank()) {
                    return line.length() > 70 ? line.substring(0, 67) + "..." : line;
                }
            }
            return "";
        }
    }

    /**
     * Where the specification lives, relative to the project root.
     *
     * <p>The document is the project's own source of truth and is not copied into
     * the test resources, so that a test can never be run against a stale copy of
     * it.</p>
     */
    public static Path specification() {
        Path direct = Path.of("specification.md");
        return Files.exists(direct) ? direct : Path.of("..", "specification.md");
    }

    /**
     * Whether the specification can be read from where the tests are running.
     *
     * @return {@code true} when the document is present
     */
    public static boolean available() {
        return Files.exists(specification());
    }

    /**
     * Every fenced LiftPatchRef and LiftPatchShort example, in document order.
     *
     * @return the examples
     */
    public static List<Example> all() {
        List<Example> out = new ArrayList<>();
        String[] lines = read().split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            Syntax syntax = fenceSyntax(lines[i].strip());
            if (syntax == null) {
                continue;
            }
            int start = i + 1;
            StringBuilder body = new StringBuilder();
            int j = start;
            while (j < lines.length && !lines[j].strip().equals("```")) {
                body.append(lines[j]).append('\n');
                j++;
            }
            out.add(new Example(syntax, body.toString(), i + 1));
            i = j;
        }
        return out;
    }

    private static Syntax fenceSyntax(String fence) {
        return switch (fence) {
            case "```LiftPatchRef" -> Syntax.REFERENCE;
            case "```LiftPatchShort" -> Syntax.CONCISE;
            default -> null;
        };
    }

    private static String read() {
        try {
            return Files.readString(specification(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read specification.md", e);
        }
    }
}
