package fr.cnrs.lacito.liftpatchbox.metamodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal JSON reader, sufficient for the two JSON documents this library
 * consumes: the normative metamodel of Appendix A and the conformance corpus of
 * Appendix D.
 *
 * <p>It exists so that the metamodel can be loaded from the very document the
 * specification prints, without adding a dependency on a JSON library to a
 * project whose only other dependencies are the dictionary model, the parser
 * runtime and the command-line framework. It supports the whole of RFC 8259
 * except for the two features neither document uses: it accepts any numeric
 * literal but represents integral values as {@code Long} and the rest as
 * {@code Double}, and it does not preserve duplicate object keys.</p>
 *
 * <p>Values are mapped as follows: an object to a {@link LinkedHashMap} (so that
 * key order is preserved for readable diagnostics), an array to a {@link List},
 * a string to a {@link String}, a number to a {@link Long} or a {@link Double},
 * a boolean to a {@link Boolean}, and {@code null} to {@code null}.</p>
 */
public final class Json {

    private final String src;
    private int pos;

    private Json(String src) {
        this.src = src;
    }

    /**
     * Parse a JSON document.
     *
     * @param text the document
     * @return the parsed value, mapped as described in the class documentation
     * @throws IllegalArgumentException if the text is not well-formed JSON
     */
    public static Object parse(String text) {
        Json p = new Json(text);
        p.skipWhitespace();
        Object value = p.readValue();
        p.skipWhitespace();
        if (p.pos < p.src.length()) {
            throw p.error("trailing content after the top-level value");
        }
        return value;
    }

    /**
     * Parse a JSON document expected to be an object.
     *
     * @param text the document
     * @return the parsed object as a map
     * @throws IllegalArgumentException if the text is not a well-formed JSON object
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object v = parse(text);
        if (!(v instanceof Map)) {
            throw new IllegalArgumentException("Expected a JSON object at the top level");
        }
        return (Map<String, Object>) v;
    }

    /**
     * Parse a JSON document expected to be an array.
     *
     * @param text the document
     * @return the parsed array as a list
     * @throws IllegalArgumentException if the text is not a well-formed JSON array
     */
    @SuppressWarnings("unchecked")
    public static List<Object> parseArray(String text) {
        Object v = parse(text);
        if (!(v instanceof List)) {
            throw new IllegalArgumentException("Expected a JSON array at the top level");
        }
        return (List<Object>) v;
    }

    // ------------------------------------------------------------- accessors

    /**
     * Read a nested object member.
     *
     * @param object the containing map
     * @param key    the member name
     * @return the member as a map, or an empty map when the member is absent or null
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Map<String, Object> object, String key) {
        Object v = object.get(key);
        return v instanceof Map ? (Map<String, Object>) v : Collections.emptyMap();
    }

    /**
     * Read a nested array member.
     *
     * @param object the containing map
     * @param key    the member name
     * @return the member as a list, or an empty list when the member is absent or null
     */
    @SuppressWarnings("unchecked")
    public static List<Object> array(Map<String, Object> object, String key) {
        Object v = object.get(key);
        return v instanceof List ? (List<Object>) v : Collections.emptyList();
    }

    /**
     * Read an array member whose elements are all strings.
     *
     * @param object the containing map
     * @param key    the member name
     * @return the strings, in order; empty when the member is absent
     */
    public static List<String> strings(Map<String, Object> object, String key) {
        List<String> out = new ArrayList<>();
        for (Object o : array(object, key)) {
            out.add(String.valueOf(o));
        }
        return out;
    }

    /**
     * Read a string member.
     *
     * @param object the containing map
     * @param key    the member name
     * @return the member as a string, or {@code null} when absent or JSON null
     */
    public static String string(Map<String, Object> object, String key) {
        Object v = object.get(key);
        return v == null ? null : String.valueOf(v);
    }

    /**
     * Read a boolean member.
     *
     * @param object       the containing map
     * @param key          the member name
     * @param defaultValue the value to return when the member is absent
     * @return the member as a boolean, or {@code defaultValue}
     */
    public static boolean bool(Map<String, Object> object, String key, boolean defaultValue) {
        Object v = object.get(key);
        return v instanceof Boolean b ? b : defaultValue;
    }

    /**
     * Read an integer member.
     *
     * @param object       the containing map
     * @param key          the member name
     * @param defaultValue the value to return when the member is absent
     * @return the member as an int, or {@code defaultValue}
     */
    public static int integer(Map<String, Object> object, String key, int defaultValue) {
        Object v = object.get(key);
        return v instanceof Number n ? n.intValue() : defaultValue;
    }

    // ---------------------------------------------------------------- parser

    private Object readValue() {
        if (pos >= src.length()) {
            throw error("unexpected end of document");
        }
        char c = src.charAt(pos);
        return switch (c) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't' -> readLiteral("true", Boolean.TRUE);
            case 'f' -> readLiteral("false", Boolean.FALSE);
            case 'n' -> readLiteral("null", null);
            default -> readNumber();
        };
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        expect('{');
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            map.put(key, readValue());
            skipWhitespace();
            char c = next();
            if (c == '}') {
                return map;
            }
            if (c != ',') {
                throw error("expected ',' or '}' in an object");
            }
        }
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        expect('[');
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(readValue());
            skipWhitespace();
            char c = next();
            if (c == ']') {
                return list;
            }
            if (c != ',') {
                throw error("expected ',' or ']' in an array");
            }
        }
    }

    private String readString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= src.length()) {
                throw error("unterminated string");
            }
            char c = src.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char esc = next();
            switch (esc) {
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case '/' -> sb.append('/');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    if (pos + 4 > src.length()) {
                        throw error("truncated \\u escape");
                    }
                    sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                    pos += 4;
                }
                default -> throw error("unknown escape \\" + esc);
            }
        }
    }

    private Object readNumber() {
        int start = pos;
        if (peek() == '-' || peek() == '+') {
            pos++;
        }
        boolean fractional = false;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c >= '0' && c <= '9') {
                pos++;
            } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                fractional = fractional || c == '.' || c == 'e' || c == 'E';
                pos++;
            } else {
                break;
            }
        }
        String text = src.substring(start, pos);
        if (text.isEmpty()) {
            throw error("expected a value");
        }
        return fractional ? (Object) Double.valueOf(text) : (Object) Long.valueOf(text);
    }

    private Object readLiteral(String literal, Object value) {
        if (!src.startsWith(literal, pos)) {
            throw error("expected " + literal);
        }
        pos += literal.length();
        return value;
    }

    private void skipWhitespace() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
            pos++;
        }
    }

    private char peek() {
        return pos < src.length() ? src.charAt(pos) : '\0';
    }

    private char next() {
        if (pos >= src.length()) {
            throw error("unexpected end of document");
        }
        return src.charAt(pos++);
    }

    private void expect(char c) {
        if (next() != c) {
            pos--;
            throw error("expected '" + c + "'");
        }
    }

    private IllegalArgumentException error(String message) {
        int line = 1;
        for (int i = 0; i < Math.min(pos, src.length()); i++) {
            if (src.charAt(i) == '\n') {
                line++;
            }
        }
        return new IllegalArgumentException("Malformed JSON at line " + line + ": " + message);
    }
}
