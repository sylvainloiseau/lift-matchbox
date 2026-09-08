package fr.cnrs.lacito.liftdsl.api;
import fr.cnrs.lacito.liftdsl.validation.ValidationError;

/** Reports a validation failure discovered against dictionary state. */
public final class DictionaryValidationException extends RuntimeException {
    /**
     * Creates a dictionary validation exception.
     *
     * @param code structured validation category
     * @param message human-readable explanation
     */
    public DictionaryValidationException(ValidationError.Code code, String message){super(message);this.code=code;}
    private final ValidationError.Code code;

    /** @return the structured validation category */
    public ValidationError.Code code(){return code;}
}
