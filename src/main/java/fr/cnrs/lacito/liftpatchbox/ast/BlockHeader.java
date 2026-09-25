package fr.cnrs.lacito.liftpatchbox.ast;

import fr.cnrs.lacito.liftpatchbox.error.SourcePosition;
import fr.cnrs.lacito.liftpatchbox.metamodel.LanguageKind;
import java.util.Map;

/**
 * What stands before the opening brace of a block (Part 2, section 11), or, in
 * the concise syntax, the anchor line of an indented block (Part 3, section 3.3).
 *
 * <p>A header either designates the component that becomes the parent of the
 * body — as a selector chain, a {@code create}, an {@code upsert} or an
 * {@code ensure} — or, in the case of {@link With}, binds default languages
 * without designating anything.</p>
 */
public sealed interface BlockHeader permits BlockHeader.Selector, BlockHeader.Anchor, BlockHeader.With {

    /**
     * Where the header begins.
     *
     * @return the source position
     */
    SourcePosition position();

    /**
     * The label the header binds, if any.
     *
     * <p>A header label is bound in the <em>enclosing</em> scope, not in the
     * body's, so that later commands can reach the component the block was about
     * without selecting it again.</p>
     *
     * @return the label name without its {@code $}, or {@code null}
     */
    String label();

    /**
     * A header that is a bare selector chain, asserting nothing and creating
     * nothing. This form has no concise spelling (Part 3, section 1.2).
     *
     * @param chain    the chain designating the parent of the body
     * @param label    the label bound to it, or {@code null}
     * @param position where the header begins
     */
    record Selector(Chain chain, String label, SourcePosition position) implements BlockHeader {

        @Override
        public String toString() {
            return chain + (label == null ? "" : " as $" + label);
        }
    }

    /**
     * A header that is itself a command: a {@code create}, an {@code upsert} or an
     * {@code ensure}, whose component becomes the parent of the body.
     *
     * @param command the heading command
     */
    record Anchor(Command command) implements BlockHeader {

        /**
         * Canonical constructor.
         *
         * <p>Every verb is admitted here, including the two that may not head a
         * block: a {@code delete}, whose component is gone by the time the indented
         * commands would run, and a {@code move}, which names two parents while a
         * block header supplies one. Both are refused by the semantic validator,
         * with {@code DELETE_CANNOT_BE_AN_ANCHOR} and
         * {@code MOVE_NOT_ALLOWED_IN_BLOCK}, so that the writer is told which rule
         * they broke rather than seeing the parser give up.</p>
         *
         * @param command the heading command
         */
        public Anchor {
        }

        @Override
        public SourcePosition position() {
            return command.position();
        }

        @Override
        public String label() {
            return switch (command) {
                case Command.Create c -> c.label();
                case Command.Upsert u -> u.label();
                case Command.Ensure e -> e.label();
                default -> null;
            };
        }

        @Override
        public String toString() {
            return command.toString();
        }
    }

    /**
     * A {@code with} header, which binds default languages for the body and
     * designates no parent; the parent is then the one of the enclosing block, if
     * any (Part 1, section 2.1.2).
     *
     * @param bindings the language kinds bound, and the code each is bound to
     * @param position where the header begins
     */
    record With(Map<LanguageKind, String> bindings, SourcePosition position) implements BlockHeader {

        /**
         * Canonical constructor, taking an unmodifiable copy of the bindings.
         *
         * @param bindings the language kinds bound and their codes
         * @param position where the header begins
         */
        public With {
            bindings = Map.copyOf(bindings);
        }

        @Override
        public String label() {
            return null;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("with ");
            boolean first = true;
            for (Map.Entry<LanguageKind, String> e : bindings.entrySet()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append(e.getKey().jsonName()).append(" = \"").append(e.getValue()).append('"');
            }
            return sb.toString();
        }
    }
}
