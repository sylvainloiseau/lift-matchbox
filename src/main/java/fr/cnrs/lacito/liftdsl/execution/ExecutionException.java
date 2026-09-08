package fr.cnrs.lacito.liftdsl.execution;

/** Reports a failure while applying a validated command to a dictionary. */
public final class ExecutionException extends RuntimeException {
    /**
     * Creates an execution exception with an underlying cause.
     *
     * @param message
     *            human-readable explanation
     * @param cause
     *            underlying failure
     */
    public ExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates an execution exception without an underlying cause.
     *
     * @param message
     *            human-readable explanation
     */
    public ExecutionException(String message) {
        super(message);
    }
}
