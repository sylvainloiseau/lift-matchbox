package fr.cnrs.lacito.liftdsl.validation;

/** Reports a syntax or semantic problem in a parsed Lift-DSL program. */
public final class ValidationError extends RuntimeException {
    /** Categories of validation failures exposed by the DSL implementation. */
    public enum Code { SYNTAX_ERROR, UNKNOWN_COMPONENT, UNKNOWN_PROPERTY, ILLEGAL_PARENT, MISSING_REQUIRED_PROPERTY, DUPLICATE_PROPERTY, ILLEGAL_LANGUAGE, INVALID_SELECTOR, NOT_FOUND, AMBIGUOUS_REFERENCE, PROPERTY_NOT_FOUND, UNSET_PROPERTY, INVALID_TARGET }
    private final Code code;
    /**
     * Creates a validation error.
     *
     * @param code structured failure category
     * @param message human-readable explanation
     */
    public ValidationError(Code code, String message){super(message);this.code=code;}
    /** @return the structured validation category */
    public Code code(){return code;}
}
