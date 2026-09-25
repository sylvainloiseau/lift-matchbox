package fr.cnrs.lacito.liftpatchbox.parser;

import fr.cnrs.lacito.liftpatchbox.error.ErrorCode;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchError;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchException;
import fr.cnrs.lacito.liftpatchbox.error.SourcePosition;

/**
 * Decoding of the string literals of both surface syntaxes.
 *
 * <p>The escaping rules of Part 1, section 1.2 are exhaustive, and this class is
 * the one place that implements them:</p>
 *
 * <ul>
 *   <li>in a double-quoted string, {@code \"} is a double quote and {@code \\} a
 *       backslash, and these two are the <em>only</em> escape sequences: any
 *       other character after a backslash is a {@code SYNTAX_ERROR}, so that no
 *       sequence is silently reinterpreted later;</li>
 *   <li>in a single-quoted string, {@code ''} is a single quote and a backslash
 *       is an ordinary character with no escaping role;</li>
 *   <li>a raw line break is forbidden inside a string in both syntaxes, which the
 *       lexer rules already refuse.</li>
 * </ul>
 */
public final class Literals {

    private Literals() {
    }

    /**
     * Decode a quoted string literal.
     *
     * @param literal  the literal including its quotes, as the lexer matched it
     * @param position where the literal was written, for the error message
     * @return the string content, with escapes resolved
     * @throws LiftPatchException with {@link ErrorCode#SYNTAX_ERROR} if a
     *         double-quoted string contains a backslash followed by anything
     *         other than {@code "} or {@code \}
     */
    public static String unquote(String literal, SourcePosition position) {
        if (literal.length() < 2) {
            return literal;
        }
        char quote = literal.charAt(0);
        String body = literal.substring(1, literal.length() - 1);
        return quote == '\'' ? unquoteSingle(body) : unquoteDouble(body, position);
    }

    private static String unquoteSingle(String body) {
        return body.replace("''", "'");
    }

    private static String unquoteDouble(String body, SourcePosition position) {
        StringBuilder sb = new StringBuilder(body.length());
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (i + 1 >= body.length()) {
                throw syntaxError("a string may not end with a lone backslash", position);
            }
            char esc = body.charAt(++i);
            if (esc == '"' || esc == '\\') {
                sb.append(esc);
            } else {
                throw syntaxError(
                    "in a double-quoted string, \\\" and \\\\ are the only escape sequences; "
                        + "found \\" + esc,
                    position);
            }
        }
        return sb.toString();
    }

    private static LiftPatchException syntaxError(String message, SourcePosition position) {
        return new LiftPatchException(
            LiftPatchError.of(ErrorCode.SYNTAX_ERROR, message, position));
    }
}
