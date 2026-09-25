package fr.cnrs.lacito.liftpatchbox.ast;

import fr.cnrs.lacito.liftpatchbox.error.SourcePosition;

/**
 * The right-hand side of an assignment, of an initializer or of a predicate.
 *
 * <p>The five shapes are those of the grammar of Appendix C: a string, an
 * integer, a multi-language literal, a chain and a label. The last two are the
 * only legal values of a property of datatype {@code reference}, which is never
 * written as a bare string (Part 2, section 10).</p>
 */
public sealed interface Value
    permits Value.Str, Value.Num, Value.Multitext, Value.ChainRef, Value.LabelRef {

    /**
     * Where the value was written.
     *
     * @return the source position
     */
    SourcePosition position();

    /**
     * A quoted string, possibly carrying the language suffix the concise syntax
     * allows on an abbreviated value ({@code "mami"@tww}, Part 3, section 6.3).
     *
     * @param text     the string content, with escapes already resolved
     * @param language the language written after the string, or {@code null}
     * @param position where the value was written
     */
    record Str(String text, String language, SourcePosition position) implements Value {

        /**
         * An unqualified string at an unknown position.
         *
         * @param text the string content
         * @return the value
         */
        public static Str of(String text) {
            return new Str(text, null, SourcePosition.UNKNOWN);
        }

        @Override
        public String toString() {
            return language == null ? "\"" + text + "\"" : "\"" + text + "\"@" + language;
        }
    }

    /**
     * An unquoted integer, the only form an {@code integer} property and the
     * {@code hn} pseudo-property accept.
     *
     * @param value    the integer
     * @param position where the value was written
     */
    record Num(long value, SourcePosition position) implements Value {

        /**
         * An integer at an unknown position.
         *
         * @param value the integer
         * @return the value
         */
        public static Num of(long value) {
            return new Num(value, SourcePosition.UNKNOWN);
        }

        @Override
        public String toString() {
            return Long.toString(value);
        }
    }

    /**
     * A multi-language literal {@code { lang: "text", ... }}, pure surface sugar
     * for the sequence of the corresponding single-qualifier assignments.
     *
     * @param entries  the qualified values, in the order written
     * @param position where the literal was written
     */
    record Multitext(java.util.List<LangText> entries, SourcePosition position) implements Value {

        /**
         * Canonical constructor, taking an unmodifiable copy of the entries.
         *
         * @param entries  the qualified values
         * @param position where the literal was written
         */
        public Multitext {
            entries = java.util.List.copyOf(entries);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("{ ");
            for (int i = 0; i < entries.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(entries.get(i));
            }
            return sb.append(" }").toString();
        }
    }

    /**
     * A value chain: a component designated by a chain of steps, which is how a
     * property of datatype {@code reference} is written.
     *
     * @param chain the chain, which must resolve to exactly one component
     */
    record ChainRef(Chain chain) implements Value {

        @Override
        public SourcePosition position() {
            return chain.position();
        }

        @Override
        public String toString() {
            return chain.toString();
        }
    }

    /**
     * A label {@code $name}, the only way to designate a component the running
     * script has just created.
     *
     * @param name     the label name, without the leading {@code $}
     * @param position where the label was written
     */
    record LabelRef(String name, SourcePosition position) implements Value {

        /**
         * A label reference at an unknown position.
         *
         * @param name the label name, without the leading {@code $}
         * @return the value
         */
        public static LabelRef of(String name) {
            return new LabelRef(name, SourcePosition.UNKNOWN);
        }

        @Override
        public String toString() {
            return "$" + name;
        }
    }
}
