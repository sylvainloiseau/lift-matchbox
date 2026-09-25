package fr.cnrs.lacito.liftpatchbox.metamodel;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One component type, as declared by the metamodel of Appendix A.
 *
 * @param name              the component type name, such as {@code "sense"}
 * @param kind              the component kind, which decides position, ordinals and {@code move}
 * @param children          the component type names this type admits as children, in declaration order
 * @param naturalIdentity   the natural identity property set {@code I(T)}, in the metamodel's order
 * @param unnamedArguments  the ordered properties the unnamed concise initializer arguments map to
 * @param properties        the properties of this type, by name
 */
public record ComponentTypeDef(
    String name,
    ComponentKind kind,
    List<String> children,
    List<String> naturalIdentity,
    List<String> unnamedArguments,
    Map<String, PropertyDef> properties
) {

    /**
     * Canonical constructor, taking unmodifiable copies of the collections.
     *
     * @param name             the component type name
     * @param kind             the component kind
     * @param children         the admissible child type names
     * @param naturalIdentity  the natural identity property set
     * @param unnamedArguments the unnamed-argument mapping
     * @param properties       the properties by name
     */
    public ComponentTypeDef {
        children = List.copyOf(children);
        naturalIdentity = List.copyOf(naturalIdentity);
        unnamedArguments = List.copyOf(unnamedArguments);
        properties = Map.copyOf(properties);
    }

    /**
     * Look a property up by name.
     *
     * @param propertyName the property name, spelled in full
     * @return the property, or empty when this component type does not define it
     */
    public Optional<PropertyDef> property(String propertyName) {
        return Optional.ofNullable(properties.get(propertyName));
    }

    /**
     * Whether this component type admits the named type as a child.
     *
     * @param childType the candidate child component type name
     * @return {@code true} when the metamodel relates the two as parent and child
     */
    public boolean admitsChild(String childType) {
        return children.contains(childType);
    }

    /**
     * Whether the natural identity property set of this type is empty, which makes
     * the uniqueness invariant vacuous for it (Part 1, section 5.2).
     *
     * @return {@code true} for {@code entry} and {@code category}
     */
    public boolean hasEmptyIdentity() {
        return naturalIdentity.isEmpty();
    }

    /**
     * The properties this type declares required at creation.
     *
     * @return the required property names, in no particular order
     */
    public List<String> requiredProperties() {
        return properties.values().stream()
            .filter(PropertyDef::required)
            .map(PropertyDef::name)
            .sorted()
            .toList();
    }

    /**
     * Whether the same-type children of one parent live in an ordered list.
     *
     * @return {@code true} when the kind is {@link ComponentKind#ORDERED}
     */
    public boolean isOrdered() {
        return kind == ComponentKind.ORDERED;
    }

    /**
     * Whether the same-type children of one parent live in a map keyed by type.
     *
     * @return {@code true} when the kind is {@link ComponentKind#TYPED}
     */
    public boolean isTyped() {
        return kind == ComponentKind.TYPED;
    }

    /**
     * Whether a host has exactly one child of this type, always.
     *
     * @return {@code true} when the kind is {@link ComponentKind#SINGLETON}
     */
    public boolean isSingleton() {
        return kind == ComponentKind.SINGLETON;
    }
}
