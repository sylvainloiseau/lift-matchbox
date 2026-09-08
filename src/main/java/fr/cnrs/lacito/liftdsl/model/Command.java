package fr.cnrs.lacito.liftdsl.model;

import java.util.*;

/** Immutable normalized representation of one Lift-DSL command. */
public final class Command {
    /** Supported structural, property, and language commands. */
    public enum Kind {
        CREATE, UPSERT, ENSURE, DELETE, MOVE, SET, UPDATE, CLEAR, LANGUAGE_DEFAULT
    }

    private final Kind kind;
    private final String componentType;
    private final ComponentRef target;
    private final List<PropertyValue> values;
    private final PropertyValue property;
    private final String languageSet;
    private final String language;
    private final String position;
    private final Integer index;
    private final List<Command> children;

    private Command(Builder b) {
        kind = b.kind;
        componentType = b.componentType;
        target = b.target;
        values = List.copyOf(b.values);
        property = b.property;
        languageSet = b.languageSet;
        language = b.language;
        position = b.position;
        index = b.index;
        children = List.copyOf(b.children);
    }

    /** @return the command kind */
    public Kind kind() {
        return kind;
    }

    /** @return the component type operated on, or null for property commands */
    public String componentType() {
        return componentType;
    }

    /** @return the component target or parent reference, or null */
    public ComponentRef target() {
        return target;
    }

    /** @return immutable component initializer values */
    public List<PropertyValue> values() {
        return values;
    }

    /** @return the property assignment, when this is a property command */
    public Optional<PropertyValue> property() {
        return Optional.ofNullable(property);
    }

    /** @return the language set named by a language-default command */
    public Optional<String> languageSet() {
        return Optional.ofNullable(languageSet);
    }

    /** @return the language value named by a language-default command */
    public Optional<String> language() {
        return Optional.ofNullable(language);
    }

    /** @return the optional insertion position */
    public Optional<String> position() {
        return Optional.ofNullable(position);
    }

    /** @return the optional numeric insertion index */
    public OptionalInt index() {
        return index == null ? OptionalInt.empty() : OptionalInt.of(index);
    }

    /** @return immutable nested commands */
    public List<Command> children() {
        return children;
    }

    /**
     * @param kind
     *            command kind @return a builder for that kind
     */
    public static Builder builder(Kind kind) {
        return new Builder(kind);
    }

    public static final class Builder {
        private final Kind kind;
        private String componentType, languageSet, language, position;
        private Integer index;
        private ComponentRef target;
        private PropertyValue property;
        private final List<PropertyValue> values = new ArrayList<>();
        private final List<Command> children = new ArrayList<>();

        private Builder(Kind kind) {
            this.kind = Objects.requireNonNull(kind);
        }

        /**
         * @param v
         *            component type @return this builder
         */
        public Builder component(String v) {
            componentType = v;
            return this;
        }

        /**
         * @param v
         *            target reference @return this builder
         */
        public Builder target(ComponentRef v) {
            target = v;
            return this;
        }

        /**
         * @param v
         *            initializer value @return this builder
         */
        public Builder add(PropertyValue v) {
            values.add(v);
            return this;
        }

        /**
         * @param v
         *            property assignment @return this builder
         */
        public Builder property(PropertyValue v) {
            property = v;
            return this;
        }

        /**
         * @param v
         *            language set name @return this builder
         */
        public Builder languageSet(String v) {
            languageSet = v;
            return this;
        }

        /**
         * @param v
         *            language value @return this builder
         */
        public Builder language(String v) {
            language = v;
            return this;
        }

        /**
         * @param v
         *            textual insertion position @return this builder
         */
        public Builder position(String v) {
            position = v;
            return this;
        }

        /**
         * @param v
         *            numeric insertion index @return this builder
         */
        public Builder index(Integer v) {
            index = v;
            return this;
        }

        /**
         * @param v
         *            nested command @return this builder
         */
        public Builder child(Command v) {
            children.add(v);
            return this;
        }

        /** @return an immutable command built from the current values */
        public Command build() {
            return new Command(this);
        }
    }
}
