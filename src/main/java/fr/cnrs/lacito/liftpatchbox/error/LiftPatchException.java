package fr.cnrs.lacito.liftpatchbox.error;

import java.util.List;

/**
 * The exception every failure of a LiftPatch script is reported with.
 *
 * <p>It carries the {@link LiftPatchError}s that caused the failure: one for a
 * dynamic error, which aborts the script at the first failing command, and
 * possibly many for the static validation pass, which reports every static error
 * of a script before applying anything (Part 2, section 12.3).</p>
 */
public class LiftPatchException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<LiftPatchError> errors;

    /**
     * An exception carrying a single error.
     *
     * @param error the error, never {@code null}
     */
    public LiftPatchException(LiftPatchError error) {
        this(List.of(error));
    }

    /**
     * An exception carrying a list of errors, as the static validation pass produces.
     *
     * @param errors the errors, never {@code null} and never empty
     * @throws IllegalArgumentException if {@code errors} is empty
     */
    public LiftPatchException(List<LiftPatchError> errors) {
        super(buildMessage(errors));
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("A LiftPatchException must carry at least one error");
        }
        this.errors = List.copyOf(errors);
    }

    private static String buildMessage(List<LiftPatchError> errors) {
        if (errors.size() == 1) {
            return errors.get(0).format();
        }
        StringBuilder sb = new StringBuilder(errors.size() + " errors:");
        for (LiftPatchError e : errors) {
            sb.append(System.lineSeparator()).append("  - ").append(e.format());
        }
        return sb.toString();
    }

    /**
     * The errors that caused this failure, in the order in which they were found.
     *
     * @return an unmodifiable, non-empty list
     */
    public List<LiftPatchError> errors() {
        return errors;
    }

    /**
     * The first error, which is the only one for a dynamic failure.
     *
     * @return the first error of {@link #errors()}
     */
    public LiftPatchError firstError() {
        return errors.get(0);
    }

    /**
     * The code of the first error, which is what a conformance case compares.
     *
     * @return the code of {@link #firstError()}
     */
    public ErrorCode code() {
        return firstError().code();
    }
}
