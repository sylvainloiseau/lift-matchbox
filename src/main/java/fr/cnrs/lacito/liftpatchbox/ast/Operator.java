package fr.cnrs.lacito.liftpatchbox.ast;

/**
 * The comparison operators of a predicate (Part 1, section 5.1.2).
 *
 * <p>Only {@link #EQ} is admitted in a unique selector; the other three are
 * filtering-only, because they are by construction not uniquely identifying.</p>
 */
public enum Operator {

    /** {@code =}: the property has the value. Allowed in any selector. */
    EQ("=", false),

    /** {@code !=}: the property is set and its value differs. Filtering selectors only. */
    NE("!=", true),

    /** {@code ~}: the value matches the PCRE regular expression. Filtering selectors only. */
    MATCH("~", true),

    /** {@code ~i}: as {@code ~}, with Unicode simple case folding. Filtering selectors only. */
    MATCH_IGNORE_CASE("~i", true);

    private final String spelling;
    private final boolean filteringOnly;

    Operator(String spelling, boolean filteringOnly) {
        this.spelling = spelling;
        this.filteringOnly = filteringOnly;
    }

    /**
     * The text this operator is written with.
     *
     * @return the operator spelling
     */
    public String spelling() {
        return spelling;
    }

    /**
     * Whether this operator is restricted to filtering selectors.
     *
     * @return {@code true} for every operator but {@link #EQ}
     */
    public boolean isFilteringOnly() {
        return filteringOnly;
    }

    /**
     * Whether this operator is one of the two regular-expression operators, which
     * do not apply to every datatype.
     *
     * @return {@code true} for {@link #MATCH} and {@link #MATCH_IGNORE_CASE}
     */
    public boolean isRegex() {
        return this == MATCH || this == MATCH_IGNORE_CASE;
    }

    /**
     * Parse an operator spelling.
     *
     * @param s the spelling
     * @return the matching constant
     * @throws IllegalArgumentException if {@code s} is not an operator spelling
     */
    public static Operator parse(String s) {
        for (Operator o : values()) {
            if (o.spelling.equals(s)) {
                return o;
            }
        }
        throw new IllegalArgumentException("Not a comparison operator: " + s);
    }
}
