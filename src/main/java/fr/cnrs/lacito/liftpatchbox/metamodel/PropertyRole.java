package fr.cnrs.lacito.liftpatchbox.metamodel;

/**
 * The six property roles of Part 1, section 5.4.1.
 *
 * <p>Every property of the metamodel has exactly one role, obtained from three
 * declarations — membership in the natural identity property set, {@code required
 * at creation}, and the datatype. The roles form a partition, so the
 * applicability table yields a single verdict for each (component, property)
 * pair.</p>
 */
public enum PropertyRole {

    /** Identity, scalar. */
    R1(true, true, false),
    /** Identity, multitext. */
    R2(true, true, true),
    /** Required, non-identity, scalar. */
    R3(false, true, false),
    /** Required, non-identity, multitext. */
    R4(false, true, true),
    /** Optional, non-identity, scalar. */
    R5(false, false, false),
    /** Optional, non-identity, multitext. */
    R6(false, false, true);

    private final boolean identity;
    private final boolean required;
    private final boolean multitext;

    PropertyRole(boolean identity, boolean required, boolean multitext) {
        this.identity = identity;
        this.required = required;
        this.multitext = multitext;
    }

    /**
     * Classify a property by the three declarations the role is derived from.
     *
     * @param identity  whether the property belongs to the natural identity property set
     * @param required  whether the metamodel declares it required at creation
     * @param multitext whether its datatype is the multitext
     * @return the single role matching those three
     */
    public static PropertyRole of(boolean identity, boolean required, boolean multitext) {
        for (PropertyRole r : values()) {
            if (r.identity == identity && r.required == required && r.multitext == multitext) {
                return r;
            }
        }
        // An identity property is always required in practice, but the metamodel
        // does not forbid the combination, so classify it with the identity roles.
        return multitext ? R2 : R1;
    }

    /**
     * Whether this role is one of the identity roles, R1 and R2.
     *
     * @return {@code true} for an identity property
     */
    public boolean isIdentity() {
        return identity;
    }

    /**
     * Whether this role is one of the required roles, R1 to R4.
     *
     * @return {@code true} for a property required at creation
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * Whether this role is one of the multitext roles, R2, R4 and R6.
     *
     * @return {@code true} for a multitext property
     */
    public boolean isMultitext() {
        return multitext;
    }

    /**
     * Whether a unique selector admits a predicate on a property of this role.
     *
     * <p>Only the identity roles take part in strategy S3; {@code entry.form} is
     * the one exception, being strategy S4, and is handled by the caller which
     * knows the component type.</p>
     *
     * @return {@code true} for R1 and R2
     */
    public boolean allowedInUniqueSelector() {
        return identity;
    }
}
