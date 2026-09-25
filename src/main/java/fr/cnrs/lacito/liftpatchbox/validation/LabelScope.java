package fr.cnrs.lacito.liftpatchbox.validation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The lexical scope of labels (Part 1, section 6.3).
 *
 * <p>A label is visible from its binding command to the end of the enclosing
 * block, or to the end of the script when it is bound at top level. Re-binding a
 * visible name is {@code DUPLICATE_LABEL} and using an unbound one is
 * {@code UNKNOWN_LABEL}, both static errors, which is why the scope is tracked
 * during validation rather than at run time.</p>
 *
 * <p>The scope records the component type each label is bound to, which is what
 * makes {@code INVALID_TARGET} a static error: the type of the component a label
 * denotes is known from the command that bound it.</p>
 *
 * <p>A label bound by a block <em>header</em> belongs to the enclosing scope, not
 * to the body's, so that it remains visible after the closing brace. Callers bind
 * a header label with {@link #bind} before {@link #push}.</p>
 */
public final class LabelScope {

    private final Deque<Map<String, String>> frames = new ArrayDeque<>();

    /**
     * A scope with one frame, the top-level one.
     */
    public LabelScope() {
        frames.push(new HashMap<>());
    }

    /**
     * Enter a nested scope, as a block does.
     */
    public void push() {
        frames.push(new HashMap<>());
    }

    /**
     * Leave the innermost scope, unbinding every label it bound.
     */
    public void pop() {
        frames.pop();
    }

    /**
     * Whether a name is bound anywhere in the visible scopes.
     *
     * @param name the label name, without its {@code $}
     * @return {@code true} when a visible command has bound it
     */
    public boolean isBound(String name) {
        return frames.stream().anyMatch(f -> f.containsKey(name));
    }

    /**
     * Bind a label in the innermost scope.
     *
     * @param name          the label name, without its {@code $}
     * @param componentType the component type the label denotes, or {@code null}
     *                      when it is not known
     */
    public void bind(String name, String componentType) {
        frames.peek().put(name, componentType == null ? "" : componentType);
    }

    /**
     * The component type a bound label denotes.
     *
     * @param name the label name, without its {@code $}
     * @return the component type, or empty when the label is unbound or its type
     *         is not known
     */
    public Optional<String> typeOf(String name) {
        for (Map<String, String> frame : frames) {
            String type = frame.get(name);
            if (type != null) {
                return type.isEmpty() ? Optional.empty() : Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
