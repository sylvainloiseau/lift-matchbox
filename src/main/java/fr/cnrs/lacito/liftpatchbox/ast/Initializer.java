package fr.cnrs.lacito.liftpatchbox.ast;

import fr.cnrs.lacito.liftpatchbox.error.SourcePosition;

/**
 * One element of the parenthesized list of a {@code create} or an {@code upsert}.
 *
 * <p>Three shapes occur. A {@link Assignment} initializes a property; a
 * {@link Condition} is one of the {@code has} / {@code has-gloss} predicates that
 * only an {@code upsert} parenthesis admits, where it is a matching condition and
 * never an assignment (Part 2, section 8.2.1); an {@link Embedded} is the
 * embedded component creation of Part 3, section 7.</p>
 */
public sealed interface Initializer
    permits Initializer.Assignment, Initializer.Condition, Initializer.Embedded {

    /**
     * Where the initializer was written.
     *
     * @return the source position
     */
    SourcePosition position();

    /**
     * {@code property = value}, the ordinary initializer.
     *
     * @param property the property initialized
     * @param value    the value assigned
     * @param position where the initializer was written
     */
    record Assignment(PropertyRef property, Value value, SourcePosition position)
        implements Initializer {

        /**
         * An assignment at an unknown position.
         *
         * @param property the property initialized
         * @param value    the value assigned
         * @return the initializer
         */
        public static Assignment of(PropertyRef property, Value value) {
            return new Assignment(property, value, SourcePosition.UNKNOWN);
        }

        @Override
        public String toString() {
            return property + " = " + value;
        }
    }

    /**
     * A {@code has} or {@code has-gloss} predicate inside an {@code upsert}
     * parenthesis: the disambiguation part {@code D} of Part 2, section 8.2.1.
     *
     * @param predicate the predicate
     */
    record Condition(Predicate predicate) implements Initializer {

        @Override
        public SourcePosition position() {
            return predicate.position();
        }

        @Override
        public String toString() {
            return predicate.toString();
        }
    }

    /**
     * An embedded component creation, which creates a child on the fly and is
     * atomic with the command that contains it.
     *
     * @param constructor the embedded constructor
     */
    record Embedded(Constructor constructor) implements Initializer {

        @Override
        public SourcePosition position() {
            return constructor.position();
        }

        @Override
        public String toString() {
            return constructor.toString();
        }
    }
}
