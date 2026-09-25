package fr.cnrs.lacito.liftpatchbox.metamodel;

/**
 * The component kind of Part 1, section 3.1: how the same-type children of one
 * parent are held, and how one of them is designated.
 *
 * <p>Every rule about position, ordinals, type keys, {@code move} and the
 * {@code at} clause follows from this one declaration, and a validator dispatches
 * on the kind rather than on the component name.</p>
 */
public enum ComponentKind {

    /**
     * The children live in an ordered list whose order the script controls. An
     * ordered component is the only kind that takes an ordinal {@code #n}, an
     * {@code at POSITION} clause, and a {@code move}.
     */
    ORDERED,

    /**
     * The children live in a map keyed by their {@code type} property, which is
     * therefore the whole of their natural identity. A typed component is
     * designated by a type key {@code ^t}, has no position, and cannot be moved.
     */
    TYPED,

    /**
     * Not a collection at all: a host has exactly one child of that type, it
     * always has one, and it never has two. A singleton is designated by its
     * component type name alone, and is never created, deleted or moved.
     */
    SINGLETON;

    /**
     * Parse the {@code componentKind} value of the metamodel document.
     *
     * @param s the value, one of {@code "ordered"}, {@code "typed"} or {@code "singleton"}
     * @return the matching kind
     * @throws IllegalArgumentException if the value names no kind
     */
    public static ComponentKind parse(String s) {
        return switch (s) {
            case "ordered" -> ORDERED;
            case "typed" -> TYPED;
            case "singleton" -> SINGLETON;
            default -> throw new IllegalArgumentException("Unknown componentKind: " + s);
        };
    }

    /**
     * The spelling this kind has in the metamodel document.
     *
     * @return {@code "ordered"}, {@code "typed"} or {@code "singleton"}
     */
    public String jsonName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
