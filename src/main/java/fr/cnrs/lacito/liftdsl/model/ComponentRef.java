package fr.cnrs.lacito.liftdsl.model;

import java.util.*;

/** Identifies a component type, its selector, and its optional ancestor chain. */
public final class ComponentRef {
    private final String type;
    private final Selector selector;
    private final ComponentRef parent;

    /**
     * Creates a component reference.
     *
     * @param type
     *            component type
     * @param selector
     *            selector; an empty selector is replaced with a new empty selector
     * @param parent
     *            optional immediate ancestor reference
     *
     * @throws NullPointerException
     *             if {@code type} is null
     */
    public ComponentRef(String type, Selector selector, ComponentRef parent) {
        this.type = Objects.requireNonNull(type);
        this.selector = selector == null ? new Selector() : selector;
        this.parent = parent;
    }

    /** @return the component type */
    public String type() {
        return type;
    }

    /** @return the selector applied to this component */
    public Selector selector() {
        return selector;
    }

    /** @return the optional parent reference */
    public Optional<ComponentRef> parent() {
        return Optional.ofNullable(parent);
    }
}
