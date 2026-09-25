package fr.cnrs.lacito.liftpatchbox.model;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The undo journal that makes a script atomic.
 *
 * <p>Part 2, section 12.2 makes the whole script the unit of atomicity, and
 * requires an implementation that cannot offer a transactional dictionary to
 * simulate one. This journal is that simulation: every mutation the model adapter
 * performs pushes the action that undoes it, and a failure replays the stack in
 * reverse, leaving the dictionary exactly as it was before the script started.</p>
 *
 * <p>A journal is used in preference to working on a copy of the dictionary
 * because a LIFT dictionary is large — tens of megabytes is ordinary — and
 * copying it for every script would dominate the cost of running one.</p>
 */
public final class Journal {

    private static final Logger LOGGER = Logger.getLogger(Journal.class.getName());

    /**
     * An empty journal, recording from the start.
     */
    public Journal() {
    }

    private final Deque<Runnable> undo = new ArrayDeque<>();

    /**
     * Record the action that undoes a mutation just performed.
     *
     * @param action the inverse action, never {@code null}
     */
    public void record(Runnable action) {
        undo.push(action);
    }

    /**
     * How many mutations are currently undoable.
     *
     * @return the size of the undo stack
     */
    public int size() {
        return undo.size();
    }

    /**
     * Undo every recorded mutation, most recent first.
     */
    public void rollback() {
        LOGGER.log(Level.FINE, () -> "Rolling back " + undo.size() + " mutation(s)");
        while (!undo.isEmpty()) {
            undo.pop().run();
        }
    }

    /**
     * Discard the journal, making every recorded mutation permanent.
     */
    public void commit() {
        LOGGER.log(Level.FINE, () -> "Committing " + undo.size() + " mutation(s)");
        undo.clear();
    }

    /**
     * A mark that {@link #rollbackTo(int)} can return to, so that a block can be
     * rolled back without rolling back the script around it.
     *
     * @return the current depth of the undo stack
     */
    public int mark() {
        return undo.size();
    }

    /**
     * Undo every mutation recorded since a mark.
     *
     * @param mark a value returned by {@link #mark()}
     */
    public void rollbackTo(int mark) {
        while (undo.size() > mark) {
            undo.pop().run();
        }
    }
}
