package fr.cnrs.lacito.liftpatchbox.ast;

import fr.cnrs.lacito.liftpatchbox.error.SourcePosition;

/**
 * A reference to a property, optionally carrying a language qualifier.
 *
 * <p>Three shapes are distinguished, because the language treats them
 * differently (Part 1, sections 4.4 and 5.5):</p>
 *
 * <ul>
 *   <li>{@code p} — no qualifier. On a multitext this means the applicable
 *       default language everywhere but in {@code exists} / {@code absent} and in
 *       {@code clear}, where it means the property as a whole;</li>
 *   <li>{@code p@L} — one named language;</li>
 *   <li>{@code p@*} — the wildcard, which denotes every language for which the
 *       multitext has a value, and is allowed only in {@code clear} and in a
 *       filtering predicate.</li>
 * </ul>
 *
 * @param name     the property name, spelled in full
 * @param language the language code, or {@code null} when none is written
 * @param wildcard whether the qualifier written is {@code @*}
 * @param position where the reference was written
 */
public record PropertyRef(String name, String language, boolean wildcard, SourcePosition position) {

    /**
     * A property reference with no qualifier.
     *
     * @param name the property name
     * @return the reference, at an unknown position
     */
    public static PropertyRef of(String name) {
        return new PropertyRef(name, null, false, SourcePosition.UNKNOWN);
    }

    /**
     * A property reference qualified by a language.
     *
     * @param name     the property name
     * @param language the language code
     * @return the reference, at an unknown position
     */
    public static PropertyRef of(String name, String language) {
        return new PropertyRef(name, language, false, SourcePosition.UNKNOWN);
    }

    /**
     * A property reference qualified by the wildcard {@code @*}.
     *
     * @param name the property name
     * @return the reference, at an unknown position
     */
    public static PropertyRef wildcard(String name) {
        return new PropertyRef(name, null, true, SourcePosition.UNKNOWN);
    }

    /**
     * Whether a qualifier — a language code or the wildcard — is written.
     *
     * @return {@code true} when the reference carries an {@code @} suffix
     */
    public boolean hasQualifier() {
        return language != null || wildcard;
    }

    /**
     * The same reference with its position replaced.
     *
     * @param p the new position
     * @return a copy carrying {@code p}
     */
    public PropertyRef at(SourcePosition p) {
        return new PropertyRef(name, language, wildcard, p);
    }

    @Override
    public String toString() {
        if (wildcard) {
            return name + "@*";
        }
        return language == null ? name : name + "@" + language;
    }
}
