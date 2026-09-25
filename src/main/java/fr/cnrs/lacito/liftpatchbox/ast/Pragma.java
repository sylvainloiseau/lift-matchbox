package fr.cnrs.lacito.liftpatchbox.ast;

import fr.cnrs.lacito.liftpatchbox.error.SourcePosition;
import java.util.Map;

/**
 * The optional version pragma {@code %liftpatch 1.0} and its named attributes
 * (Part 1, section 1.1).
 *
 * <p>Three attributes are defined in version 1.0: {@code sigil}, which declares
 * the line prefix that marks a command in a concise document; {@code metamodel},
 * which names the metamodel the script requires; and {@code syntax}, which
 * declares which of the two surface syntaxes the script is written in. Any other
 * attribute is rejected with {@code UNKNOWN_PRAGMA_ATTRIBUTE}.</p>
 *
 * @param version    the declared version, such as {@code "1.0"}
 * @param attributes the named attributes, in the order written
 * @param position   where the pragma was written
 */
public record Pragma(String version, Map<String, String> attributes, SourcePosition position) {

    /** The attribute declaring the concise-syntax line sigil. */
    public static final String SIGIL = "sigil";
    /** The attribute naming the required metamodel. */
    public static final String METAMODEL = "metamodel";
    /** The attribute declaring the surface syntax. */
    public static final String SYNTAX = "syntax";

    /**
     * Canonical constructor, taking an unmodifiable copy of the attributes.
     *
     * @param version    the declared version
     * @param attributes the named attributes
     * @param position   where the pragma was written
     */
    public Pragma {
        attributes = Map.copyOf(attributes);
    }

    /**
     * The value of the {@code sigil} attribute.
     *
     * @return the sigil, or {@code null} when none is declared
     */
    public String sigil() {
        return attributes.get(SIGIL);
    }

    /**
     * The value of the {@code metamodel} attribute.
     *
     * @return the required metamodel identifier, or {@code null} when none is declared
     */
    public String metamodel() {
        return attributes.get(METAMODEL);
    }

    /**
     * The surface syntax the {@code syntax} attribute declares.
     *
     * @return the syntax, or {@code null} when the attribute is absent or unrecognized
     */
    public Syntax syntax() {
        return Syntax.fromPragmaName(attributes.get(SYNTAX));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("%liftpatch ").append(version);
        attributes.forEach((k, v) -> sb.append(' ').append(k).append("=\"").append(v).append('"'));
        return sb.toString();
    }
}
