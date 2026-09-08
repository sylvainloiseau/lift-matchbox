package fr.cnrs.lacito.liftdsl.parser;

import fr.cnrs.lacito.liftdsl.model.Program;

/** Explicit facade for parsing reference and concise Lift-DSL surface syntaxes. */
public final class LiftDslParserFacade {
    private final DslParser delegate = new DslParser();

    /**
     * Parses reference syntax.
     *
     * @param source reference Lift-DSL source
     * @return parsed program
     * @throws fr.cnrs.lacito.liftdsl.validation.ValidationError if parsing fails
     */
    public Program parseReference(String source) { return delegate.parse(source); }

    /**
     * Parses concise syntax.
     *
     * @param source concise Lift-DSL source
     * @return parsed program
     * @throws fr.cnrs.lacito.liftdsl.validation.ValidationError if parsing fails
     */
    public Program parseConcise(String source) { return delegate.parseConcise(source); }
}
