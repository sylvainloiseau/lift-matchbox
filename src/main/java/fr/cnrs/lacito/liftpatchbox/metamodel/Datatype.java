package fr.cnrs.lacito.liftpatchbox.metamodel;

/**
 * The datatype of a property, as declared by the metamodel of Appendix A.
 *
 * <p>The datatype decides which operators a predicate may use on the property
 * (Part 1, section 5.1.2), whether a language qualifier is admitted (section
 * 4.4), and what shape a value must take — in particular that a
 * {@link #REFERENCE} is written as a chain and never as a bare string (Part 2,
 * section 10).</p>
 */
public enum Datatype {

    /** An ordinary string value. */
    STRING,

    /** An integer value, written without quotes. */
    INTEGER,

    /** The id of an {@code entry} or a {@code sense}, written as a chain or a label. */
    REFERENCE,

    /** A URL, compared and matched as an ordinary string; never validated. */
    URL,

    /** A map from a language code to a string: the multitext of section 4.2. */
    MULTITEXT;

    /**
     * Parse the {@code datatype} value of the metamodel document.
     *
     * @param s the value
     * @return the matching datatype
     * @throws IllegalArgumentException if the value names no datatype
     */
    public static Datatype parse(String s) {
        return switch (s) {
            case "string" -> STRING;
            case "integer" -> INTEGER;
            case "reference" -> REFERENCE;
            case "url" -> URL;
            case "multitext" -> MULTITEXT;
            default -> throw new IllegalArgumentException("Unknown datatype: " + s);
        };
    }

    /**
     * Whether this datatype is the multitext, the one non-scalar datatype.
     *
     * @return {@code true} for {@link #MULTITEXT}
     */
    public boolean isMultitext() {
        return this == MULTITEXT;
    }

    /**
     * Whether the regular-expression operators {@code ~} and {@code ~i} apply to
     * this datatype (Part 1, section 5.1.2).
     *
     * @return {@code true} for every datatype but {@link #INTEGER} and {@link #REFERENCE}
     */
    public boolean admitsRegex() {
        return this != INTEGER && this != REFERENCE;
    }

    /**
     * The spelling this datatype has in the metamodel document.
     *
     * @return the lower-case name
     */
    public String jsonName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
