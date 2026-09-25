package fr.cnrs.lacito.liftpatchbox.validation;

/**
 * Whether a selector must select exactly one component, or only states a
 * condition (Part 1, section 5.1.1).
 *
 * <p>The distinction governs which predicates are admitted and whether a
 * selection strategy is required, and it is decided by position alone: by the
 * axis of the link in the concise syntax, by the clause keyword in the reference
 * one, and by the presence of a multiplicity keyword in both.</p>
 */
public enum SelectorKind {

    /**
     * A unique selector: a command selector, or a parent selector on the strict
     * axis. It must use exactly one selection strategy and carries no additional
     * predicate.
     */
    UNIQUE,

    /**
     * A filtering selector: the parent side of an existential link, a step inside
     * a {@code has} predicate, or a target step marked {@code each} / {@code all} /
     * {@code *}. It may carry any predicate and may match zero, one or several
     * components.
     */
    FILTERING;

    /**
     * Whether this is the unique kind.
     *
     * @return {@code true} for {@link #UNIQUE}
     */
    public boolean isUnique() {
        return this == UNIQUE;
    }
}
