package fr.cnrs.lacito.liftpatchbox.parser;

import fr.cnrs.lacito.liftpatchbox.ast.Verb;
import fr.cnrs.lacito.liftpatchbox.error.ErrorCode;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchError;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchException;
import fr.cnrs.lacito.liftpatchbox.error.SourcePosition;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a mixed document into the sequence of its LiftPatchShort command lines.
 *
 * <p>This is the part of the concise syntax that no token grammar can express,
 * and that Appendix C.2 explicitly leaves to a separate pass:</p>
 *
 * <ul>
 *   <li>the <strong>recognition rule</strong> of Part 3, section 1.1: a line is a
 *       command only when a command letter, optionally followed by {@code *}, is
 *       followed by whitespace and by a second token of one of five constrained
 *       shapes. A one-letter prefix alone is deliberately not enough, so that
 *       ordinary prose beginning with "c " is not mistaken for a command;</li>
 *   <li><strong>sigil mode</strong>: when the pragma declares a sigil, a line is a
 *       command if and only if it begins, after optional whitespace, with that
 *       character, and the indentation is then measured from the sigil;</li>
 *   <li><strong>grouping and indentation</strong>: a command group is a maximal
 *       run of command, directive and comment lines, ended by a blank or prose
 *       line; indentation relates commands inside one group only, which is what
 *       makes the construct safe in a document where commands and prose
 *       alternate;</li>
 *   <li><strong>escaping</strong>: a line beginning with a backslash is never a
 *       command, and the backslash is not part of the text.</li>
 * </ul>
 *
 * <p>A line that matches the recognition rule but does not parse is an error and
 * never prose: silently demoting a mistyped command would drop an intended edit
 * without telling anyone.</p>
 */
public final class ShortLinePreprocessor {

    /** The eight command letters, in the order Part 3, section 2.1 lists them. */
    private static final String COMMAND_LETTERS = "cdmpeusl";

    private final String source;

    /**
     * A preprocessor for a document read from the named source.
     *
     * @param source the source name, used in diagnostics
     */
    public ShortLinePreprocessor(String source) {
        this.source = source;
    }

    /**
     * One line recognized as carrying a command, a directive or the pragma.
     *
     * @param kind       what the line carries
     * @param verb       the verb, for a command line; {@code null} otherwise
     * @param star       whether the command letter carried the multiplicity marker
     * @param body       the text after the command letter and its marker, comment stripped
     * @param indent     the number of spaces before the command letter
     * @param lineNumber the 1-based physical line number
     * @param bodyColumn the 1-based column the body starts at
     * @param groupId    the index of the command group this line belongs to
     * @param rawText    the whole line, comment stripped, for plan mode
     */
    public record Recognized(
        Kind kind,
        Verb verb,
        boolean star,
        String body,
        int indent,
        int lineNumber,
        int bodyColumn,
        int groupId,
        String rawText
    ) {

        /** What a recognized line carries. */
        public enum Kind {
            /** One of the eight commands. */
            COMMAND,
            /** A {@code language-default} or {@code language-create} directive. */
            DIRECTIVE,
            /** The version pragma. */
            PRAGMA
        }

        /**
         * The position of the start of the command body.
         *
         * @return the source position
         */
        public SourcePosition position() {
            return new SourcePosition(lineNumber, bodyColumn);
        }
    }

    /**
     * The result of a scan: the recognized lines and the sigil that was in force.
     *
     * @param lines          the recognized lines, in document order
     * @param sigil          the declared sigil, or {@code null} when none was declared
     * @param pragmaLine     the recognized pragma line, or {@code null}
     * @param totalLines     how many physical lines the document has
     */
    public record Scan(List<Recognized> lines, String sigil, Recognized pragmaLine, int totalLines) {

        /**
         * Canonical constructor, taking an unmodifiable copy of the lines.
         *
         * @param lines      the recognized lines
         * @param sigil      the declared sigil, or {@code null}
         * @param pragmaLine the recognized pragma line, or {@code null}
         * @param totalLines how many physical lines the document has
         */
        public Scan {
            lines = List.copyOf(lines);
        }

        /**
         * How many lines were recognized as commands, which is what the
         * {@code NO_COMMAND_RECOGNIZED} warning is based on.
         *
         * @return the number of command lines
         */
        public int commandCount() {
            return (int) lines.stream().filter(l -> l.kind() == Recognized.Kind.COMMAND).count();
        }
    }

    /**
     * Scan a document.
     *
     * @param document the whole document text
     * @return the recognized lines, the sigil, and the pragma
     * @throws LiftPatchException with {@link ErrorCode#SYNTAX_ERROR} when a command
     *         line is indented with a tab, or when a pragma declaring a sigil is not
     *         the first non-blank line
     */
    public Scan scan(String document) {
        String[] physical = document.split("\\R", -1);
        String sigil = findSigil(physical);
        List<Recognized> out = new ArrayList<>();
        Recognized pragmaLine = null;
        int group = 0;
        boolean inGroup = false;
        boolean sawCommand = false;

        for (int i = 0; i < physical.length; i++) {
            int lineNumber = i + 1;
            String raw = physical[i];
            String line = TopLevelSplitter.stripComment(raw);
            String trimmed = line.strip();

            if (trimmed.isEmpty()) {
                // A blank line, or a line that was only a comment, ends nothing:
                // a comment line belongs to the group, a blank line ends it.
                if (raw.strip().isEmpty()) {
                    inGroup = false;
                }
                continue;
            }

            Recognized recognized = recognize(line, lineNumber, sigil, group, sawCommand);
            if (recognized == null) {
                // Prose ends the current group (Part 3, section 3.3, rule 1).
                inGroup = false;
                continue;
            }
            if (recognized.kind() == Recognized.Kind.PRAGMA) {
                if (pragmaLine == null) {
                    pragmaLine = recognized;
                }
                continue;
            }
            if (!inGroup) {
                group++;
                inGroup = true;
                recognized = new Recognized(
                    recognized.kind(), recognized.verb(), recognized.star(), recognized.body(),
                    recognized.indent(), recognized.lineNumber(), recognized.bodyColumn(),
                    group, recognized.rawText());
            }
            sawCommand = true;
            out.add(recognized);
        }
        return new Scan(out, sigil, pragmaLine, physical.length);
    }

    /**
     * Find the sigil a pragma declares. A pragma declaring one must be the first
     * non-blank line, because the sigil is what decides which lines are commands at
     * all, and that decision cannot depend on a pragma only a command-aware scan
     * would find.
     */
    private String findSigil(String[] physical) {
        for (String raw : physical) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            if (!line.startsWith("%liftpatch")) {
                return null;
            }
            int at = line.indexOf("sigil=");
            if (at < 0) {
                return null;
            }
            int q = at + "sigil=".length();
            if (q >= line.length()) {
                return null;
            }
            char quote = line.charAt(q);
            if (quote != '"' && quote != '\'') {
                return null;
            }
            int close = line.indexOf(quote, q + 1);
            if (close < 0 || close == q + 1) {
                throw syntaxError("the sigil pragma attribute must declare one character",
                    SourcePosition.ofLine(1));
            }
            return line.substring(q + 1, close);
        }
        return null;
    }

    /**
     * Apply the recognition rule to one line.
     *
     * @return the recognized line, or {@code null} when the line is prose
     */
    private Recognized recognize(
        String line, int lineNumber, String sigil, int group, boolean sawCommand
    ) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
            i++;
        }
        if (i >= line.length()) {
            return null;
        }
        // A line beginning with a backslash is never a command; the backslash is
        // not part of the text (Part 3, section 1.1).
        if (line.charAt(i) == '\\') {
            return null;
        }

        int indentStart;
        if (sigil != null) {
            if (!line.startsWith(sigil, i)) {
                return null;
            }
            indentStart = i + sigil.length();
        } else {
            indentStart = 0;
        }

        // The indentation is the whitespace between the sigil -- or the start of
        // the line, when none is declared -- and the command letter.
        int j = indentStart;
        int indent = 0;
        while (j < line.length() && (line.charAt(j) == ' ' || line.charAt(j) == '\t')) {
            if (line.charAt(j) == '\t') {
                throw syntaxError(
                    "a tab may not be used to indent a command line: the width of a tab is a "
                        + "property of the reader's editor, and the parent of an edit may not "
                        + "depend on it",
                    new SourcePosition(lineNumber, j + 1));
            }
            indent++;
            j++;
        }
        if (j >= line.length()) {
            return null;
        }
        String rest = line.substring(j);

        if (rest.startsWith("%liftpatch")) {
            if (sawCommand) {
                throw syntaxError("the version pragma must precede the first command line",
                    new SourcePosition(lineNumber, j + 1));
            }
            return new Recognized(Recognized.Kind.PRAGMA, null, false, rest,
                indent, lineNumber, j + 1, group, line.strip());
        }
        if (isDirective(rest)) {
            return new Recognized(Recognized.Kind.DIRECTIVE, null, false, rest,
                indent, lineNumber, j + 1, group, line.strip());
        }

        char letter = rest.charAt(0);
        if (COMMAND_LETTERS.indexOf(letter) < 0) {
            return null;
        }
        int k = 1;
        boolean star = false;
        if (k < rest.length() && rest.charAt(k) == '*') {
            star = true;
            k++;
        }
        if (k >= rest.length() || (rest.charAt(k) != ' ' && rest.charAt(k) != '\t')) {
            return null;
        }
        while (k < rest.length() && (rest.charAt(k) == ' ' || rest.charAt(k) == '\t')) {
            k++;
        }
        if (k >= rest.length()) {
            return null;
        }
        String body = rest.substring(k);
        if (sigil == null && !secondTokenLooksLikeACommand(body, indent > 0)) {
            return null;
        }
        Verb verb = Verb.fromCode(String.valueOf(letter));
        return new Recognized(Recognized.Kind.COMMAND, verb, star, body,
            indent, lineNumber, j + k + 1, group, line.strip());
    }

    private static boolean isDirective(String rest) {
        for (String prefix : new String[] {"language-default ", "language-create "}) {
            if (rest.startsWith(prefix)) {
                String tail = rest.substring(prefix.length()).stripLeading();
                if (tail.startsWith("object") || tail.startsWith("meta")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The second half of the recognition rule: the shape of the token that follows
     * the command letter.
     *
     * @param body     the text after the command letter and its marker
     * @param indented whether the line is indented, which admits two further shapes
     * @return whether the token is one of the five admitted shapes
     */
    private static boolean secondTokenLooksLikeACommand(String body, boolean indented) {
        char first = body.charAt(0);
        if (first == '/' || first == '$') {
            return true;
        }
        if (first == '(') {
            // A property command with no path, admitted only on an indented line.
            return indented;
        }
        int i = 0;
        while (i < body.length() && isBareChar(body.charAt(i))) {
            i++;
        }
        if (i == 0 || i >= body.length()) {
            return false;
        }
        char after = body.charAt(i);
        if (after == '(') {
            // A constructor with no parent path, which only `c` and `p` accept; the
            // verb check is a semantic one and is left to the validator.
            return true;
        }
        // A relative path, or the bare target step of a `d` or an `e`, admitted
        // only on an indented line.
        return indented && (after == '[' || after == '#' || after == '^');
    }

    private static boolean isBareChar(char c) {
        return Character.isLetter(c) || Character.isDigit(c)
            || c == '_' || c == '-' || c == '.';
    }

    private LiftPatchException syntaxError(String message, SourcePosition position) {
        return new LiftPatchException(
            LiftPatchError.of(ErrorCode.SYNTAX_ERROR, message + " (in " + source + ")", position));
    }
}
