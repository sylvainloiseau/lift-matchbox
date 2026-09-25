package fr.cnrs.lacito.liftpatchbox.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a concise command line into its top-level whitespace-separated tokens.
 *
 * <p><em>Top level</em> means outside every bracket, parenthesis, brace and
 * quoted string, which is the definition Appendix C.2 gives and which is what
 * makes the parse deterministic: the space inside {@code e[f="mami", hn=1]} and
 * the ones inside {@code v[y="dialectal", a = /memi]} do not split a token, so
 * {@code d /e[f="mami", hn=1] v[y="dialectal", a = /memi]} has a one-step parent
 * path and a one-step target.</p>
 *
 * <p>The same scan is what strips a trailing comment: a {@code #} outside a
 * quoted string, at the start of the line or preceded by whitespace, and not
 * immediately followed by a digit, opens a comment that runs to the end of the
 * line. The last condition is what keeps {@code at before #2} an ordinal rather
 * than a comment.</p>
 */
public final class TopLevelSplitter {

    private TopLevelSplitter() {
    }

    /**
     * One token of a command line, with the column it starts at.
     *
     * @param text   the token text
     * @param column the 1-based column of its first character in the physical line
     */
    public record Token(String text, int column) {

        @Override
        public String toString() {
            return text;
        }
    }

    /**
     * Split a line into its top-level whitespace-separated tokens.
     *
     * @param line        the line, with any comment already removed
     * @param baseColumn  the 1-based column the line's first character occupies in
     *                    the physical line, so that token columns are absolute
     * @return the tokens, in order; empty for a blank line
     */
    public static List<Token> split(String line, int baseColumn) {
        List<Token> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int startColumn = 0;
        int depth = 0;
        char quote = 0;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (quote != 0) {
                current.append(c);
                if (c == quote) {
                    // A doubled single quote is an escaped quote, not a terminator.
                    if (quote == '\'' && i + 1 < line.length() && line.charAt(i + 1) == '\'') {
                        current.append('\'');
                        i++;
                    } else {
                        quote = 0;
                    }
                } else if (c == '\\' && quote == '"' && i + 1 < line.length()) {
                    current.append(line.charAt(++i));
                }
                continue;
            }

            switch (c) {
                case '"', '\'' -> {
                    quote = c;
                    if (current.isEmpty()) {
                        startColumn = baseColumn + i;
                    }
                    current.append(c);
                }
                case '[', '(', '{' -> {
                    depth++;
                    if (current.isEmpty()) {
                        startColumn = baseColumn + i;
                    }
                    current.append(c);
                }
                case ']', ')', '}' -> {
                    depth--;
                    if (current.isEmpty()) {
                        startColumn = baseColumn + i;
                    }
                    current.append(c);
                }
                case ' ', '\t' -> {
                    if (depth > 0) {
                        current.append(c);
                    } else if (!current.isEmpty()) {
                        tokens.add(new Token(current.toString(), startColumn));
                        current.setLength(0);
                    }
                }
                default -> {
                    if (current.isEmpty()) {
                        startColumn = baseColumn + i;
                    }
                    current.append(c);
                }
            }
        }
        if (!current.isEmpty()) {
            tokens.add(new Token(current.toString(), startColumn));
        }
        return tokens;
    }

    /**
     * Remove a trailing comment from a line.
     *
     * @param line the physical line
     * @return the line up to, but not including, the comment marker; unchanged
     *         when the line carries no comment
     */
    public static String stripComment(String line) {
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    if (quote == '\'' && i + 1 < line.length() && line.charAt(i + 1) == '\'') {
                        i++;
                    } else {
                        quote = 0;
                    }
                } else if (c == '\\' && quote == '"' && i + 1 < line.length()) {
                    i++;
                }
                continue;
            }
            switch (c) {
                case '"', '\'' -> quote = c;
                case '#' -> {
                    boolean precededBySpace = i == 0
                        || line.charAt(i - 1) == ' '
                        || line.charAt(i - 1) == '\t';
                    boolean followedByDigit = i + 1 < line.length()
                        && Character.isDigit(line.charAt(i + 1));
                    if (precededBySpace && !followedByDigit) {
                        return line.substring(0, i);
                    }
                }
                default -> {
                    // An ordinary character.
                }
            }
        }
        return line;
    }
}
