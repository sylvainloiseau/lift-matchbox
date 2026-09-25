package fr.cnrs.lacito.liftpatchbox.ast;

/**
 * The two axes that link a step to its parent (Part 1, section 5.1.1).
 *
 * <p>Each axis has exactly one keyword spelling in LiftPatchRef and exactly one
 * operator spelling in LiftPatchShort, and neither spelling depends on where the
 * link stands.</p>
 */
public enum Axis {

    /**
     * The strict-parent axis, written {@code of} / {@code under} / {@code on} in
     * LiftPatchRef and {@code !} in LiftPatchShort. The parent selector is a
     * unique selector and must resolve to exactly one component.
     */
    STRICT("of", "!"),

    /**
     * The existential-parent axis, written {@code within} in LiftPatchRef and
     * {@code /} in LiftPatchShort. The parent selector is a filtering selector,
     * and only the result of the whole chain must be unique.
     */
    EXISTENTIAL("within", "/");

    private final String keyword;
    private final String operator;

    Axis(String keyword, String operator) {
        this.keyword = keyword;
        this.operator = operator;
    }

    /**
     * The LiftPatchRef keyword spelling of this axis.
     *
     * @return {@code "of"} or {@code "within"}
     */
    public String keyword() {
        return keyword;
    }

    /**
     * The LiftPatchShort operator spelling of this axis.
     *
     * @return {@code "!"} or {@code "/"}
     */
    public String operator() {
        return operator;
    }
}
