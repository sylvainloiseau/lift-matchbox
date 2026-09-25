package fr.cnrs.lacito.liftpatchbox.metamodel;

/**
 * One pseudo-property, as declared by the {@code pseudoProperties} section of
 * Appendix A.
 *
 * <p>Pseudo-properties do not belong to the data: they are selection devices, and
 * their availability per command is given by the applicability tables of Part 1,
 * section 5.4. A {@link PseudoPropertyKind#CHILD_PREDICATE} carries no datatype;
 * what it is written with is given by {@code argument} in the document, and the
 * equivalent general form of a shorthand by {@code expandsTo}.</p>
 *
 * @param name       the pseudo-property name, such as {@code "hn"}
 * @param kind       what sort of selection device it is
 * @param datatype   the datatype of its value, or {@code null} for a child predicate
 * @param qualifier  the language kind it admits, or {@code null} when it admits none
 * @param scope      an informative description of where it applies
 * @param expandsTo  the equivalent general form of a shorthand, or {@code null}
 */
public record PseudoPropertyDef(
    String name,
    PseudoPropertyKind kind,
    Datatype datatype,
    LanguageKind qualifier,
    String scope,
    String expandsTo
) {

    /**
     * Whether this pseudo-property accepts a language key {@code @L}.
     *
     * <p>Only {@code has-gloss} does, being a shorthand for a predicate over a
     * multitext (Part 1, section 4.4).</p>
     *
     * @return {@code true} when a language key may be written on it
     */
    public boolean acceptsLanguageKey() {
        return qualifier != null;
    }
}
