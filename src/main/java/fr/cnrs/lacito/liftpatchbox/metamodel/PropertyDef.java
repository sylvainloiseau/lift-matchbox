package fr.cnrs.lacito.liftpatchbox.metamodel;

/**
 * One property of one component type, as declared by the metamodel of Appendix A.
 *
 * <p>A property is unambiguously named by the pair (component type, property
 * name): several component types define a property called {@code type}, and they
 * are different properties.</p>
 *
 * @param componentType the name of the component type the property belongs to
 * @param name          the property name, as both surface syntaxes spell it in full
 * @param datatype      the datatype
 * @param qualifier     the language kind of a multitext, or {@code null} for a scalar
 * @param required      whether the property must be initialized at creation
 * @param identity      whether the property belongs to the natural identity property set
 */
public record PropertyDef(
    String componentType,
    String name,
    Datatype datatype,
    LanguageKind qualifier,
    boolean required,
    boolean identity
) {

    /**
     * The role of this property, in the sense of Part 1, section 5.4.1.
     *
     * @return one of the six roles R1 to R6
     */
    public PropertyRole role() {
        return PropertyRole.of(identity, required, datatype.isMultitext());
    }

    /**
     * Whether this property accepts a language key {@code @L} and the wildcard {@code @*}.
     *
     * <p>Exactly the multitext properties do, and it is the {@code qualifier}
     * declaration that says so (Part 1, section 4.4).</p>
     *
     * @return {@code true} for a multitext property
     */
    public boolean acceptsLanguageKey() {
        return datatype.isMultitext();
    }

    /**
     * A rendering of this property for a diagnostic message.
     *
     * @return {@code componentType.name}
     */
    public String qualifiedName() {
        return componentType + "." + name;
    }
}
