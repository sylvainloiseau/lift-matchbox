package fr.cnrs.lacito.liftpatchbox.ast;

import fr.cnrs.lacito.liftpatchbox.error.SourcePosition;
import java.util.List;

/**
 * One command of a script: the unit that resolves a component and, for all but
 * {@code ensure}, changes the dictionary.
 *
 * <p>Both surface syntaxes produce the same command objects. A LiftPatchShort
 * path, written ancestor first, is reversed by its visitor into the child-first
 * {@link Chain} the reference syntax writes, so that everything downstream —
 * validation, resolution, execution, plan mode — is written once.</p>
 *
 * <p>Every command carries the source text it was built from, in
 * {@link #sourceText()}, which is what plan mode echoes back to the reader, and
 * its position, which is what an error message points at.</p>
 */
public sealed interface Command extends Item
    permits Command.Create, Command.Upsert, Command.Ensure, Command.Delete,
            Command.Move, Command.Set, Command.Update, Command.Clear {

    /**
     * The verb of this command.
     *
     * @return the verb
     */
    Verb verb();

    /**
     * The source text this command was parsed from, for plan mode and diagnostics.
     *
     * @return the text, or {@code null} for a command built through the fluent API
     */
    String sourceText();

    /**
     * The same command with its source text replaced.
     *
     * @param text the text to record
     * @return a copy carrying {@code text}
     */
    Command withSourceText(String text);

    /**
     * {@code create COMPONENT(initializers) [under PARENT] [at POSITION] [as $LABEL]}.
     *
     * @param constructor the component being created, with its initializer list
     * @param parent      the parent chain, or {@code null} for an entry or inside a block
     * @param at          the position clause, or {@code null} for the default {@code at end}
     * @param label       the label bound to the created component, or {@code null}
     * @param sourceText  the source text
     * @param position    where the command begins
     */
    record Create(
        Constructor constructor,
        Chain parent,
        AtClause at,
        String label,
        String sourceText,
        SourcePosition position
    ) implements Command {

        @Override
        public Verb verb() {
            return Verb.CREATE;
        }

        @Override
        public Create withSourceText(String text) {
            return new Create(constructor, parent, at, label, text, position);
        }

        @Override
        public String toString() {
            return render("create", constructor.toString(), parent, "under", at, label);
        }
    }

    /**
     * {@code upsert COMPONENT(initializers) [under PARENT] [at POSITION] [as $LABEL]}.
     *
     * @param constructor the component being upserted, with its match, disambiguation and assignment parts
     * @param parent      the parent chain, or {@code null} for an entry or inside a block
     * @param at          the position clause, which applies only if the create branch runs
     * @param label       the label bound to the created or resolved component, or {@code null}
     * @param sourceText  the source text
     * @param position    where the command begins
     */
    record Upsert(
        Constructor constructor,
        Chain parent,
        AtClause at,
        String label,
        String sourceText,
        SourcePosition position
    ) implements Command {

        @Override
        public Verb verb() {
            return Verb.UPSERT;
        }

        @Override
        public Upsert withSourceText(String text) {
            return new Upsert(constructor, parent, at, label, text, position);
        }

        @Override
        public String toString() {
            return render("upsert", constructor.toString(), parent, "under", at, label);
        }
    }

    /**
     * {@code ensure COMPONENT[SELECTOR] [under PARENT] [as $LABEL]}.
     *
     * @param target     the step asserted to resolve to exactly one component
     * @param parent     the parent chain, or {@code null} for an entry or inside a block
     * @param label      the label bound to the asserted component, or {@code null}
     * @param sourceText the source text
     * @param position   where the command begins
     */
    record Ensure(
        Step target,
        Chain parent,
        String label,
        String sourceText,
        SourcePosition position
    ) implements Command {

        @Override
        public Verb verb() {
            return Verb.ENSURE;
        }

        @Override
        public Ensure withSourceText(String text) {
            return new Ensure(target, parent, label, text, position);
        }

        @Override
        public String toString() {
            return render("ensure", target.toString(), parent, "under", null, label);
        }
    }

    /**
     * {@code delete [all] COMPONENT[SELECTOR] [under PARENT]}.
     *
     * @param target     the step naming the component or components to delete
     * @param parent     the parent chain, or {@code null} for an entry or inside a block
     * @param sourceText the source text
     * @param position   where the command begins
     */
    record Delete(
        Step target,
        Chain parent,
        String sourceText,
        SourcePosition position
    ) implements Command {

        @Override
        public Verb verb() {
            return Verb.DELETE;
        }

        @Override
        public Delete withSourceText(String text) {
            return new Delete(target, parent, text, position);
        }

        @Override
        public String toString() {
            return render("delete", target.toString(), parent, "under", null, null);
        }
    }

    /**
     * {@code move COMPONENT[SELECTOR] under SOURCE-PARENT [under DESTINATION-PARENT] at POSITION}.
     *
     * @param target            the ordered step naming the component to move
     * @param sourceParent      the chain of the parent the component currently sits under
     * @param destinationParent the chain of the new parent, or {@code null} for a same-parent move
     * @param at                the mandatory position clause
     * @param sourceText        the source text
     * @param position          where the command begins
     */
    record Move(
        Step target,
        Chain sourceParent,
        Chain destinationParent,
        AtClause at,
        String sourceText,
        SourcePosition position
    ) implements Command {

        @Override
        public Verb verb() {
            return Verb.MOVE;
        }

        @Override
        public Move withSourceText(String text) {
            return new Move(target, sourceParent, destinationParent, at, text, position);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("move ").append(target);
            if (sourceParent != null) {
                sb.append(" under ").append(sourceParent);
            }
            if (destinationParent != null) {
                sb.append(" under ").append(destinationParent);
            }
            if (at != null) {
                sb.append(' ').append(at);
            }
            return sb.toString();
        }
    }

    /**
     * {@code set ASSIGNMENT {, ASSIGNMENT} [on [each] PARENT]}.
     *
     * @param assignments the assignment list, in the order written
     * @param target      the chain naming the component written on, or {@code null} inside a block
     * @param sourceText  the source text
     * @param position    where the command begins
     */
    record Set(
        List<Assignment> assignments,
        Chain target,
        String sourceText,
        SourcePosition position
    ) implements Command {

        /**
         * Canonical constructor, taking an unmodifiable copy of the assignment list.
         *
         * @param assignments the assignment list
         * @param target      the chain naming the component written on
         * @param sourceText  the source text
         * @param position    where the command begins
         */
        public Set {
            assignments = List.copyOf(assignments);
        }

        @Override
        public Verb verb() {
            return Verb.SET;
        }

        @Override
        public Set withSourceText(String text) {
            return new Set(assignments, target, text, position);
        }

        @Override
        public String toString() {
            return renderProperty("set", assignments, target);
        }
    }

    /**
     * {@code update ASSIGNMENT {, ASSIGNMENT} [on [each] PARENT]}.
     *
     * @param assignments the assignment list, in the order written
     * @param target      the chain naming the component written on, or {@code null} inside a block
     * @param sourceText  the source text
     * @param position    where the command begins
     */
    record Update(
        List<Assignment> assignments,
        Chain target,
        String sourceText,
        SourcePosition position
    ) implements Command {

        /**
         * Canonical constructor, taking an unmodifiable copy of the assignment list.
         *
         * @param assignments the assignment list
         * @param target      the chain naming the component written on
         * @param sourceText  the source text
         * @param position    where the command begins
         */
        public Update {
            assignments = List.copyOf(assignments);
        }

        @Override
        public Verb verb() {
            return Verb.UPDATE;
        }

        @Override
        public Update withSourceText(String text) {
            return new Update(assignments, target, text, position);
        }

        @Override
        public String toString() {
            return renderProperty("update", assignments, target);
        }
    }

    /**
     * {@code clear PROPERTY {, PROPERTY} [on [each] PARENT]}.
     *
     * @param properties the property list, in the order written
     * @param target     the chain naming the component cleared, or {@code null} inside a block
     * @param sourceText the source text
     * @param position   where the command begins
     */
    record Clear(
        List<PropertyRef> properties,
        Chain target,
        String sourceText,
        SourcePosition position
    ) implements Command {

        /**
         * Canonical constructor, taking an unmodifiable copy of the property list.
         *
         * @param properties the property list
         * @param target     the chain naming the component cleared
         * @param sourceText the source text
         * @param position   where the command begins
         */
        public Clear {
            properties = List.copyOf(properties);
        }

        @Override
        public Verb verb() {
            return Verb.CLEAR;
        }

        @Override
        public Clear withSourceText(String text) {
            return new Clear(properties, target, text, position);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("clear ");
            for (int i = 0; i < properties.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(properties.get(i));
            }
            if (target != null) {
                sb.append(" on ").append(target);
            }
            return sb.toString();
        }
    }

    // ------------------------------------------------------- shared rendering

    private static String render(
        String verb, String head, Chain parent, String parentKeyword, AtClause at, String label
    ) {
        StringBuilder sb = new StringBuilder(verb).append(' ').append(head);
        if (parent != null) {
            sb.append(' ').append(parentKeyword).append(' ').append(parent);
        }
        if (at != null) {
            sb.append(' ').append(at);
        }
        if (label != null) {
            sb.append(" as $").append(label);
        }
        return sb.toString();
    }

    private static String renderProperty(String verb, List<Assignment> assignments, Chain target) {
        StringBuilder sb = new StringBuilder(verb).append(' ');
        for (int i = 0; i < assignments.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(assignments.get(i));
        }
        if (target != null) {
            sb.append(" on ").append(target);
        }
        return sb.toString();
    }
}
