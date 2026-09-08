package fr.cnrs.lacito.liftdsl.model;

import java.util.Objects;

/** Represents one property assignment, including optional language and type qualifiers. */
public final class PropertyValue {
    private final String name;
    private final String language;
    private final String type;
    private final String value;

    /**
     * Creates a property value.
     *
     * @param name
     *            property name
     * @param language
     *            optional language qualifier
     * @param type
     *            optional type qualifier
     * @param value
     *            assigned value, or null for a clear operation
     *
     * @throws NullPointerException
     *             if {@code name} is null
     */
    public PropertyValue(String name, String language, String type, String value) {
        this.name = Objects.requireNonNull(name);
        this.language = language;
        this.type = type;
        this.value = value;
    }

    /** @return the unqualified property name */
    public String name() {
        return name;
    }

    /** @return the optional language qualifier, or null */
    public String language() {
        return language;
    }

    /** @return the optional type qualifier, or null */
    public String type() {
        return type;
    }

    /** @return the assigned value, or null when the property is cleared */
    public String value() {
        return value;
    }
}
