package fr.cnrs.lacito.liftpatchbox.engine;

/**
 * One effect a command has on the dictionary.
 *
 * <p>The shape is normative: Part 2, section 12.4.1 fixes the seven {@code kind}
 * values and the fields each of them carries, and Appendix D.1 states exactly
 * which fields a conformance comparison looks at — {@code kind},
 * {@code componentType}, {@code property}, {@code language},
 * {@code languageKind}, {@code oldValue}, {@code newValue}, {@code position},
 * {@code fromPosition} and {@code toPosition}. The remaining fields of an effect
 * are informative and are never compared, which is why {@code fromParent} and
 * {@code toParent} are rendered as paths rather than kept here.</p>
 *
 * @param kind          which of the seven effects this is
 * @param componentType the component type, for the three component effects
 * @param property      the property name, for the three property effects
 * @param language      the language code of a qualified value, {@code null} for a scalar
 * @param languageKind  the language kind, for {@code languageCreated} only
 * @param oldValue      the value before the command, {@code null} where there was none
 * @param newValue      the value after the command, {@code null} where there is none
 * @param position      the 1-based position, for {@code componentCreated} and {@code componentDeleted}
 * @param fromPosition  the position before a move
 * @param toPosition    the position after a move
 * @param fromParent    an informative path to the source parent of a move
 * @param toParent      an informative path to the destination parent of a move
 */
public record Effect(
    Kind kind,
    String componentType,
    String property,
    String language,
    String languageKind,
    String oldValue,
    String newValue,
    Integer position,
    Integer fromPosition,
    Integer toPosition,
    String fromParent,
    String toParent
) {

    /** The seven effect kinds of Part 2, section 12.4.1. */
    public enum Kind {
        /** A component appears under the operation's parent. */
        componentCreated,
        /** A component disappears. */
        componentDeleted,
        /** A component changes position, parent, or both. */
        componentMoved,
        /** A value is written where there was none. */
        propertySet,
        /** A value is written over an existing one. */
        propertyReplaced,
        /** A value is removed. */
        propertyRemoved,
        /** A language is added to one of the dictionary's language lists. */
        languageCreated
    }

    /**
     * A {@code componentCreated} effect.
     *
     * @param componentType the component type created
     * @param position      the position it occupies once created, or {@code null}
     * @return the effect
     */
    public static Effect created(String componentType, Integer position) {
        return new Effect(Kind.componentCreated, componentType, null, null, null,
            null, null, position, null, null, null, null);
    }

    /**
     * A {@code componentDeleted} effect.
     *
     * @param componentType the component type deleted
     * @param position      the position it occupied before the command, or {@code null}
     * @return the effect
     */
    public static Effect deleted(String componentType, Integer position) {
        return new Effect(Kind.componentDeleted, componentType, null, null, null,
            null, null, position, null, null, null, null);
    }

    /**
     * A {@code componentMoved} effect.
     *
     * @param componentType the component type moved
     * @param fromPosition  its position before the command
     * @param toPosition    its position after the command
     * @param fromParent    an informative path to the source parent
     * @param toParent      an informative path to the destination parent
     * @return the effect
     */
    public static Effect moved(
        String componentType, Integer fromPosition, Integer toPosition,
        String fromParent, String toParent
    ) {
        return new Effect(Kind.componentMoved, componentType, null, null, null,
            null, null, null, fromPosition, toPosition, fromParent, toParent);
    }

    /**
     * A {@code propertySet} or {@code propertyReplaced} effect, chosen by whether
     * the property had a value.
     *
     * <p>Writing a value equal to the one already there is still a change of state
     * performed by the command, and yields a {@code propertyReplaced} whose old and
     * new values are equal: plan mode reports a command that ran.</p>
     *
     * @param property the property written
     * @param language the language code, or {@code null} for a scalar
     * @param oldValue the value before the command, or {@code null}
     * @param newValue the value after the command
     * @return the effect
     */
    public static Effect written(
        String property, String language, String oldValue, String newValue
    ) {
        return new Effect(
            oldValue == null ? Kind.propertySet : Kind.propertyReplaced,
            null, property, language, null, oldValue, newValue, null, null, null, null, null);
    }

    /**
     * A {@code propertyRemoved} effect.
     *
     * @param property the property cleared
     * @param language the language code, or {@code null} for a scalar
     * @param oldValue the value before the command
     * @return the effect
     */
    public static Effect removed(String property, String language, String oldValue) {
        return new Effect(Kind.propertyRemoved, null, property, language, null,
            oldValue, null, null, null, null, null, null);
    }

    /**
     * A {@code languageCreated} effect.
     *
     * @param languageKind the language kind the code was added to
     * @param language     the language code
     * @return the effect
     */
    public static Effect languageCreated(String languageKind, String language) {
        return new Effect(Kind.languageCreated, null, null, language, languageKind,
            null, null, null, null, null, null, null);
    }
}
