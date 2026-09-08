package fr.cnrs.lacito.liftdsl.model;

import java.util.*;

/** Stores the predicates used to select one component from a dictionary. */
public final class Selector {
    private final Map<String, String> predicates = new LinkedHashMap<>();
    private Integer index;

    /**
     * Adds or replaces a named selector predicate.
     *
     * @param name
     *            predicate name
     * @param language
     *            optional language qualifier
     * @param value
     *            expected predicate value
     *
     * @return this selector for fluent construction
     */
    public Selector predicate(String name, String language, String value) {
        predicates.put(name + (language == null ? "" : "@" + language), value);
        return this;
    }

    /**
     * Sets the ordinal selector.
     *
     * @param value
     *            one-based sibling index
     *
     * @return this selector for fluent construction
     */
    public Selector index(Integer value) {
        index = value;
        return this;
    }

    /** @return an unmodifiable view of named predicates */
    public Map<String, String> predicates() {
        return Collections.unmodifiableMap(predicates);
    }

    /** @return the ordinal index when this is an index selector */
    public OptionalInt index() {
        return index == null ? OptionalInt.empty() : OptionalInt.of(index);
    }

    /** @return true when no predicate or index has been specified */
    public boolean isEmpty() {
        return predicates.isEmpty() && index == null;
    }

    /**
     * Returns a diagnostic representation of this selector.
     *
     * @return predicate or index representation
     */
    @Override
    public String toString() {
        return index == null ? predicates.toString() : "[index=" + index + "]";
    }
}
