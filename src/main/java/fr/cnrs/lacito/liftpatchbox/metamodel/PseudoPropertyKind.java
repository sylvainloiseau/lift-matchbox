package fr.cnrs.lacito.liftpatchbox.metamodel;

/**
 * What sort of selection device a pseudo-property is, as declared by the
 * {@code kind} field of the {@code pseudoProperties} section of Appendix A.
 *
 * <p>A validator dispatches on the kind rather than on the pseudo-property's
 * name, so that an extended metamodel adding a device of an existing kind needs
 * no change to the validation code.</p>
 */
public enum PseudoPropertyKind {

    /** A persistent identifier: {@code id}. */
    IDENTIFIER,

    /** A dictionary-assigned disambiguator: {@code hn}. */
    LOOKUP_KEY,

    /** An ordinal: {@code ordinal}, written {@code #n}. */
    POSITION,

    /** A type key: {@code typeKey}, written {@code ^t}. */
    KEY,

    /** A condition on the children of the component: {@code has} and {@code has-gloss}. */
    CHILD_PREDICATE;

    /**
     * Parse the {@code kind} value of the metamodel document.
     *
     * @param s the value
     * @return the matching kind
     * @throws IllegalArgumentException if the value names no kind
     */
    public static PseudoPropertyKind parse(String s) {
        return switch (s) {
            case "identifier" -> IDENTIFIER;
            case "lookupKey" -> LOOKUP_KEY;
            case "position" -> POSITION;
            case "key" -> KEY;
            case "childPredicate" -> CHILD_PREDICATE;
            default -> throw new IllegalArgumentException("Unknown pseudo-property kind: " + s);
        };
    }
}
