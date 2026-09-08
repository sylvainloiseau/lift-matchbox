package fr.cnrs.lacito.liftdsl;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftdsl.execution.TransactionalExecutor;
import fr.cnrs.lacito.liftdsl.model.Program;
import fr.cnrs.lacito.liftdsl.parser.DslParser;
import fr.cnrs.lacito.liftdsl.parser.ReferenceRenderer;
import java.util.Objects;

/**
 * Main entry point for parsing, normalizing, rendering, and executing Lift-DSL
 * programs.
 *
 * <p>The class deliberately keeps the public API small: callers provide source
 * text and either receive an immutable program model, expanded reference
 * syntax, or apply the program to a dictionary.</p>
 */
public final class LiftDsl {
    private final DslParser parser=new DslParser();

    /**
     * Parses a reference-syntax Lift-DSL source.
     *
     * @param source reference Lift-DSL source text
     * @return parsed immutable program
     * @throws fr.cnrs.lacito.liftdsl.validation.ValidationError if the source is invalid
     */
    public Program parse(String source){return parser.parse(source);}

    /**
     * Parses line-oriented concise Lift-DSL source.
     *
     * @param source concise Lift-DSL source text
     * @return parsed immutable program
     * @throws fr.cnrs.lacito.liftdsl.validation.ValidationError if the source is invalid
     */
    public Program parseConcise(String source){return parser.parseConcise(source);}

    /**
     * Expands concise commands into reference-style clauses.
     *
     * @param source concise Lift-DSL source text
     * @return normalized reference Lift-DSL text
     * @throws fr.cnrs.lacito.liftdsl.validation.ValidationError if the source is invalid
     */
    public String expandConcise(String source){return new ReferenceRenderer().render(parseConcise(source));}

    /**
     * Parses and executes a reference-syntax program transactionally.
     *
     * @param source reference Lift-DSL source text
     * @param dictionary dictionary to mutate
     * @throws NullPointerException if {@code dictionary} is null
     * @throws RuntimeException if validation or execution fails; mutations are rolled back
     */
    public void execute(String source,LiftDictionary dictionary){new TransactionalExecutor(Objects.requireNonNull(dictionary)).execute(parse(source));}

    /**
     * Parses and executes concise commands transactionally.
     *
     * @param source concise Lift-DSL source text
     * @param dictionary dictionary to mutate
     * @throws NullPointerException if {@code dictionary} is null
     * @throws RuntimeException if validation or execution fails; mutations are rolled back
     */
    public void executeConcise(String source,LiftDictionary dictionary){new TransactionalExecutor(Objects.requireNonNull(dictionary)).execute(parseConcise(source));}
}
