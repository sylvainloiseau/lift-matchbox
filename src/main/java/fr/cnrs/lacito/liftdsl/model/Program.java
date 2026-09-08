package fr.cnrs.lacito.liftdsl.model;
import java.util.*;

/** Immutable ordered collection of parsed Lift-DSL commands. */
public final class Program {
    private final List<Command> commands;
    /**
     * Creates a program from command objects.
     *
     * @param commands commands in source order
     * @throws NullPointerException if {@code commands} is null
     */
    public Program(List<Command> commands){this.commands=List.copyOf(commands);}

    /** @return the immutable command list in source order */
    public List<Command> commands(){return commands;}
}
