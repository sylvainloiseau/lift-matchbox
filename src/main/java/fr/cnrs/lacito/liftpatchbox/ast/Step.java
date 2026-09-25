package fr.cnrs.lacito.liftpatchbox.ast;

import fr.cnrs.lacito.liftpatchbox.error.SourcePosition;
import java.util.List;

/**
 * One step of a chain: a component type together with the conditions it must
 * satisfy, or a label standing for a component already bound.
 *
 * <p>Which of the three component-step forms of Part 1, section 5.1.1 a step
 * takes — a predicate list, an ordinal {@code #n} or a type key {@code ^t} — is
 * decided by the component kind the metamodel declares, and is therefore checked
 * by the validator rather than by the parser. A step carries all three slots and
 * at most one of them is filled.</p>
 */
public sealed interface Step permits Step.Component, Step.Label {

    /**
     * Where the step was written.
     *
     * @return the source position
     */
    SourcePosition position();

    /**
     * The multiplicity keyword written immediately before this step, if any.
     *
     * @return {@code each}, {@code all}, {@code *}, or {@code null}
     */
    Multiplicity multiplicity();

    /**
     * A step naming a component type.
     *
     * @param componentType the component type name, spelled in full
     * @param predicates    the predicate list between square brackets; empty when none is written
     * @param ordinal       the ordinal {@code #n}, or {@code null}
     * @param typeKey       the type key {@code ^t}, or {@code null}
     * @param multiplicity  the multiplicity keyword, or {@code null}
     * @param position      where the step was written
     */
    record Component(
        String componentType,
        List<Predicate> predicates,
        Integer ordinal,
        String typeKey,
        Multiplicity multiplicity,
        SourcePosition position
    ) implements Step {

        /**
         * Canonical constructor, taking an unmodifiable copy of the predicate list.
         *
         * @param componentType the component type name
         * @param predicates    the predicate list
         * @param ordinal       the ordinal, or {@code null}
         * @param typeKey       the type key, or {@code null}
         * @param multiplicity  the multiplicity keyword, or {@code null}
         * @param position      where the step was written
         */
        public Component {
            predicates = List.copyOf(predicates);
        }

        /**
         * A bare step naming only its component type, which is the spelling of a
         * singleton step (strategy S7).
         *
         * @param componentType the component type name
         * @return the step, at an unknown position
         */
        public static Component bare(String componentType) {
            return new Component(componentType, List.of(), null, null, null, SourcePosition.UNKNOWN);
        }

        /**
         * Whether the step carries a predicate list between square brackets.
         *
         * @return {@code true} when at least one predicate is written
         */
        public boolean hasSelector() {
            return !predicates.isEmpty();
        }

        /**
         * Whether the step uses none of the three selection devices, which is what
         * a singleton step looks like.
         *
         * @return {@code true} when there is no predicate, no ordinal and no type key
         */
        public boolean isBare() {
            return predicates.isEmpty() && ordinal == null && typeKey == null;
        }

        /**
         * The same step with a multiplicity keyword attached.
         *
         * @param m the keyword, or {@code null} to remove it
         * @return a copy carrying {@code m}
         */
        public Component withMultiplicity(Multiplicity m) {
            return new Component(componentType, predicates, ordinal, typeKey, m, position);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (multiplicity != null) {
                sb.append(multiplicity.spelling()).append(' ');
            }
            sb.append(componentType);
            if (ordinal != null) {
                sb.append('#').append(ordinal);
            } else if (typeKey != null) {
                sb.append('^').append(typeKey);
            } else if (!predicates.isEmpty()) {
                sb.append('[');
                for (int i = 0; i < predicates.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(predicates.get(i));
                }
                sb.append(']');
            }
            return sb.toString();
        }
    }

    /**
     * A step that is a label reference {@code $name}.
     *
     * <p>A label denotes exactly one component, so it is a complete selection
     * strategy on its own (S1), and an existential join below one reduces to the
     * strict one.</p>
     *
     * @param name         the label name, without the leading {@code $}
     * @param multiplicity always {@code null}; a label denotes one component
     * @param position     where the label was written
     */
    record Label(String name, Multiplicity multiplicity, SourcePosition position) implements Step {

        /**
         * A label step at an unknown position.
         *
         * @param name the label name, without the leading {@code $}
         * @return the step
         */
        public static Label of(String name) {
            return new Label(name, null, SourcePosition.UNKNOWN);
        }

        @Override
        public String toString() {
            return "$" + name;
        }
    }
}
