package fr.cnrs.lacito.liftpatchbox.ast;

import fr.cnrs.lacito.liftpatchbox.error.SourcePosition;

/**
 * One condition inside a selector. The predicates of a list are combined by
 * conjunction: a component matches the step when it matches every predicate.
 *
 * <p>The shapes are those of the table of Part 1, section 5.1.2, together with
 * the pseudo-property predicates of section 5.3. Which of them a given selector
 * admits depends on whether the selector is a unique or a filtering one, and is
 * decided by the semantic validator, not here.</p>
 */
public sealed interface Predicate
    permits Predicate.Comparison, Predicate.Existence, Predicate.Has,
            Predicate.Id, Predicate.Hn, Predicate.HasGloss, Predicate.Index {

    /**
     * Where the predicate was written.
     *
     * @return the source position
     */
    SourcePosition position();

    /**
     * {@code p = V}, {@code p != V}, {@code p ~ V} or {@code p ~i V}.
     *
     * @param property the property compared
     * @param operator the comparison operator
     * @param value    the value compared against
     * @param position where the predicate was written
     */
    record Comparison(PropertyRef property, Operator operator, Value value, SourcePosition position)
        implements Predicate {

        @Override
        public String toString() {
            return property + " " + operator.spelling() + " " + value;
        }
    }

    /**
     * {@code exists(p)} or {@code absent(p)}, which are exact complements.
     *
     * @param property the property asked about
     * @param absent   {@code true} for {@code absent(p)}, {@code false} for {@code exists(p)}
     * @param position where the predicate was written
     */
    record Existence(PropertyRef property, boolean absent, SourcePosition position)
        implements Predicate {

        @Override
        public String toString() {
            return (absent ? "absent(" : "exists(") + property + ")";
        }
    }

    /**
     * {@code has STEP}: the component has at least one direct child matching the
     * step, which is itself a filtering selector. {@code has} predicates may be
     * nested and repeated.
     *
     * @param step     the child step that must be matched
     * @param position where the predicate was written
     */
    record Has(Step step, SourcePosition position) implements Predicate {

        @Override
        public String toString() {
            return "has " + step;
        }
    }

    /**
     * {@code id = "..."}: the persistent identifier, available on {@code entry}
     * and {@code sense} only, and forbidden in a filtering selector.
     *
     * @param id       the identifier
     * @param position where the predicate was written
     */
    record Id(String id, SourcePosition position) implements Predicate {

        @Override
        public String toString() {
            return "id = \"" + id + "\"";
        }
    }

    /**
     * {@code hn = N}: the homophone number, which refines strategy S4 and is
     * written together with a qualified {@code form}.
     *
     * @param value    the homophone number, as written
     * @param position where the predicate was written
     */
    record Hn(long value, SourcePosition position) implements Predicate {

        @Override
        public String toString() {
            return "hn = " + value;
        }
    }

    /**
     * {@code has-gloss@L = V}: the {@code entry}-only shorthand for
     * {@code has sense[gloss@L = V]}.
     *
     * @param language the meta language code, or {@code null} for the default one
     * @param value    the gloss looked for
     * @param position where the predicate was written
     */
    record HasGloss(String language, String value, SourcePosition position) implements Predicate {

        @Override
        public String toString() {
            return "has-gloss" + (language == null ? "" : "@" + language) + " = \"" + value + "\"";
        }
    }

    /**
     * {@code index = n}: the deprecated predicate alias of the ordinal {@code #n}.
     *
     * <p>It is kept for compatibility and, being a predicate rather than a step
     * suffix, it can be written beside another one — a combination that is a
     * {@code DUPLICATE_SELECTOR} rather than a syntax error.</p>
     *
     * @param value    the 1-based position
     * @param position where the predicate was written
     */
    record Index(long value, SourcePosition position) implements Predicate {

        @Override
        public String toString() {
            return "index = " + value;
        }
    }
}
