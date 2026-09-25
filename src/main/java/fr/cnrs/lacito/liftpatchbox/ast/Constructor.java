package fr.cnrs.lacito.liftpatchbox.ast;

import fr.cnrs.lacito.liftpatchbox.error.SourcePosition;
import java.util.ArrayList;
import java.util.List;

/**
 * A component type with its parenthesized initializer list: what {@code create}
 * and {@code upsert} are written with.
 *
 * <p>Parentheses describe a component being created and initialized; square
 * brackets select an existing one. A constructor therefore never carries an
 * ordinal or a type key, which are selection devices (Part 1, section 6.1,
 * rule 9).</p>
 *
 * @param componentType the component type name, spelled in full
 * @param initializers  the parenthesized list, in the order written
 * @param position      where the constructor was written
 */
public record Constructor(
    String componentType,
    List<Initializer> initializers,
    SourcePosition position
) {

    /**
     * Canonical constructor, taking an unmodifiable copy of the initializer list.
     *
     * @param componentType the component type name
     * @param initializers  the parenthesized list
     * @param position      where the constructor was written
     */
    public Constructor {
        initializers = List.copyOf(initializers);
    }

    /**
     * A constructor with no initializer, at an unknown position.
     *
     * @param componentType the component type name
     * @return the constructor
     */
    public static Constructor of(String componentType) {
        return new Constructor(componentType, List.of(), SourcePosition.UNKNOWN);
    }

    /**
     * The property assignments of the list, which are what actually initialize the
     * component.
     *
     * @return the assignments, in the order written
     */
    public List<Initializer.Assignment> assignments() {
        List<Initializer.Assignment> out = new ArrayList<>();
        for (Initializer i : initializers) {
            if (i instanceof Initializer.Assignment a) {
                out.add(a);
            }
        }
        return out;
    }

    /**
     * The {@code has} and {@code has-gloss} predicates of the list: the
     * disambiguation part of an {@code upsert}.
     *
     * @return the predicates, in the order written
     */
    public List<Predicate> conditions() {
        List<Predicate> out = new ArrayList<>();
        for (Initializer i : initializers) {
            if (i instanceof Initializer.Condition c) {
                out.add(c.predicate());
            }
        }
        return out;
    }

    /**
     * The embedded component creations of the list.
     *
     * @return the embedded constructors, in the order written
     */
    public List<Constructor> embedded() {
        List<Constructor> out = new ArrayList<>();
        for (Initializer i : initializers) {
            if (i instanceof Initializer.Embedded e) {
                out.add(e.constructor());
            }
        }
        return out;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(componentType).append('(');
        for (int i = 0; i < initializers.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(initializers.get(i));
        }
        return sb.append(')').toString();
    }
}
