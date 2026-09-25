package fr.cnrs.lacito.liftpatchbox.ast;

import fr.cnrs.lacito.liftpatchbox.error.SourcePosition;
import java.util.ArrayList;
import java.util.List;

/**
 * A chain of steps linking a component to its ancestors.
 *
 * <p>A chain is always stored <strong>child first, ancestors after</strong>,
 * which is the order LiftPatchRef writes it in. A LiftPatchShort path, written
 * ancestor first, is reversed by its visitor, so that the two syntaxes produce
 * one command model. {@code axes.get(i)} is the axis that links
 * {@code steps.get(i)} to its parent {@code steps.get(i + 1)}, so the axis list
 * is always one shorter than the step list.</p>
 *
 * @param steps the steps, child first; never empty
 * @param axes  the axis of each link, of size {@code steps.size() - 1}
 */
public record Chain(List<Step> steps, List<Axis> axes) {

    /**
     * Canonical constructor, taking unmodifiable copies and checking the two lists agree.
     *
     * @param steps the steps, child first
     * @param axes  the axis of each link
     * @throws IllegalArgumentException if {@code steps} is empty, or if {@code axes}
     *         does not have exactly one element fewer than {@code steps}
     */
    public Chain {
        steps = List.copyOf(steps);
        axes = List.copyOf(axes);
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("A chain has at least one step");
        }
        if (axes.size() != steps.size() - 1) {
            throw new IllegalArgumentException(
                "A chain of " + steps.size() + " steps has " + (steps.size() - 1)
                    + " axes, not " + axes.size());
        }
    }

    /**
     * A chain made of a single step, which therefore has no axis.
     *
     * @param step the only step
     * @return the chain
     */
    public static Chain of(Step step) {
        return new Chain(List.of(step), List.of());
    }

    /**
     * The child-most step, the one the chain designates.
     *
     * @return the first step
     */
    public Step head() {
        return steps.get(0);
    }

    /**
     * The root-most step, which a complete chain requires to be an {@code entry}
     * or a label.
     *
     * @return the last step
     */
    public Step root() {
        return steps.get(steps.size() - 1);
    }

    /**
     * The number of steps.
     *
     * @return the chain length, at least 1
     */
    public int length() {
        return steps.size();
    }

    /**
     * The axis linking the step at {@code index} to its parent.
     *
     * @param index the 0-based index of the child step
     * @return the axis, or {@code null} when {@code index} is the root-most step
     */
    public Axis axisAbove(int index) {
        return index < axes.size() ? axes.get(index) : null;
    }

    /**
     * The chain obtained by dropping the child-most step, that is the chain of the
     * parent of this one.
     *
     * @return the parent chain, or {@code null} when this chain has a single step
     */
    public Chain parentChain() {
        if (steps.size() == 1) {
            return null;
        }
        return new Chain(steps.subList(1, steps.size()), axes.subList(1, axes.size()));
    }

    /**
     * The chain obtained by prefixing a new child-most step.
     *
     * @param step the new head
     * @param axis the axis linking it to the current head
     * @return the extended chain
     */
    public Chain prepend(Step step, Axis axis) {
        List<Step> s = new ArrayList<>(steps.size() + 1);
        s.add(step);
        s.addAll(steps);
        List<Axis> a = new ArrayList<>(axes.size() + 1);
        a.add(axis);
        a.addAll(axes);
        return new Chain(s, a);
    }

    /**
     * Where the chain begins, taken from its head step.
     *
     * @return the source position of the head
     */
    public SourcePosition position() {
        return head().position();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(steps.get(0).toString());
        for (int i = 1; i < steps.size(); i++) {
            sb.append(' ').append(axes.get(i - 1).keyword()).append(' ').append(steps.get(i));
        }
        return sb.toString();
    }
}
