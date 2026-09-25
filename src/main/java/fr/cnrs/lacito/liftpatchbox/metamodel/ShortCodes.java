package fr.cnrs.lacito.liftpatchbox.metamodel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The one-letter component and property codes of the concise syntax (Part 3,
 * sections 2.2 and 2.3), and their expansion to the reference-syntax names.
 *
 * <p>Anywhere a one-letter code is expected the full reference-syntax name may be
 * written instead, and the two forms may be mixed in the same command. Both
 * lookups therefore accept a code and a full name alike.</p>
 *
 * <p>Property codes are ambiguous across component types by design: {@code t} is
 * {@code text} everywhere except on a {@code trait}, where {@code type} would be
 * spelled {@code y} — the tables below are global, and the property is then
 * resolved against the component type the step names, which is what makes
 * {@code f("editorial", "To be checked")} and {@code a[y="edit", v="todo"]}
 * unambiguous.</p>
 */
public final class ShortCodes {

    private static final Map<String, String> COMPONENTS = new LinkedHashMap<>();
    private static final Map<String, String> PROPERTIES = new LinkedHashMap<>();

    static {
        COMPONENTS.put("e", "entry");
        COMPONENTS.put("s", "sense");
        COMPONENTS.put("x", "example");
        COMPONENTS.put("y", "etymology");
        COMPONENTS.put("v", "variant");
        COMPONENTS.put("r", "relation");
        COMPONENTS.put("i", "illustration");
        COMPONENTS.put("m", "media");
        COMPONENTS.put("p", "pronunciation");
        COMPONENTS.put("l", "reversal");
        COMPONENTS.put("t", "trait");
        COMPONENTS.put("a", "annotation");
        COMPONENTS.put("n", "note");
        COMPONENTS.put("f", "field");
        COMPONENTS.put("o", "translation");
        COMPONENTS.put("c", "category");

        PROPERTIES.put("f", "form");
        PROPERTIES.put("m", "morpheme");
        PROPERTIES.put("d", "definition");
        PROPERTIES.put("g", "gloss");
        PROPERTIES.put("t", "text");
        PROPERTIES.put("s", "source");
        PROPERTIES.put("a", "target");
        PROPERTIES.put("u", "url");
        PROPERTIES.put("l", "label");
        PROPERTIES.put("r", "transcription");
        PROPERTIES.put("y", "type");
        PROPERTIES.put("v", "value");
        PROPERTIES.put("o", "comment");
        PROPERTIES.put("w", "when");
        PROPERTIES.put("h", "who");
    }

    private ShortCodes() {
    }

    /**
     * Expand a component code or full component name to the reference-syntax name.
     *
     * @param token the one-letter code or the full name
     * @return the component type name, or empty when the token is neither
     */
    public static Optional<String> componentName(String token) {
        String full = COMPONENTS.get(token);
        if (full != null) {
            return Optional.of(full);
        }
        return COMPONENTS.containsValue(token) ? Optional.of(token) : Optional.empty();
    }

    /**
     * Expand a property code or full property name to the reference-syntax name.
     *
     * <p>Pseudo-property names — {@code id}, {@code hn}, {@code has-gloss} — keep
     * their reference spelling and are returned unchanged, since letters are
     * reserved for component and property names (Part 3, section 4.3).</p>
     *
     * @param token the one-letter code or the full name
     * @return the property name, or empty when the token is neither
     */
    public static Optional<String> propertyName(String token) {
        String full = PROPERTIES.get(token);
        if (full != null) {
            return Optional.of(full);
        }
        if (PROPERTIES.containsValue(token)) {
            return Optional.of(token);
        }
        return switch (token) {
            case "id", "hn", "has-gloss", "index" -> Optional.of(token);
            default -> Optional.empty();
        };
    }

    /**
     * The component code of a component type name, for rendering a command back
     * into the concise syntax.
     *
     * @param componentName the full component type name
     * @return the one-letter code, or empty when the type has none
     */
    public static Optional<String> componentCode(String componentName) {
        return COMPONENTS.entrySet().stream()
            .filter(e -> e.getValue().equals(componentName))
            .map(Map.Entry::getKey)
            .findFirst();
    }

    /**
     * The property code of a property name, for rendering a command back into the
     * concise syntax.
     *
     * @param propertyName the full property name
     * @return the one-letter code, or empty when the property has none
     */
    public static Optional<String> propertyCode(String propertyName) {
        return PROPERTIES.entrySet().stream()
            .filter(e -> e.getValue().equals(propertyName))
            .map(Map.Entry::getKey)
            .findFirst();
    }
}
