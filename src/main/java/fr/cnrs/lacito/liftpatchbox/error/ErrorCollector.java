package fr.cnrs.lacito.liftpatchbox.error;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Accumulates the errors and warnings of one validation pass.
 *
 * <p>Static validation does not stop at the first defect: Part 2, section 12.3
 * requires an implementation to report <em>every</em> static error of a script
 * before applying any command. A collector is what makes that possible, and it
 * logs each error as it is recorded so that a run is diagnosable from the log
 * alone.</p>
 */
public final class ErrorCollector {

    private static final Logger LOGGER =
        Logger.getLogger(ErrorCollector.class.getName());

    /**
     * An empty collector.
     */
    public ErrorCollector() {
    }

    private final List<LiftPatchError> errors = new ArrayList<>();
    private final List<LiftPatchWarning> warnings = new ArrayList<>();

    /**
     * Record an error.
     *
     * @param error the error, never {@code null}
     */
    public void add(LiftPatchError error) {
        errors.add(error);
        LOGGER.log(Level.SEVERE, error::format);
    }

    /**
     * Record an error built from its parts.
     *
     * @param code           the normative error code
     * @param message        a human-readable explanation
     * @param position       where in the script the error was raised
     * @param operationIndex the 1-based operation index, or 0
     */
    public void add(ErrorCode code, String message, SourcePosition position, int operationIndex) {
        add(new LiftPatchError(code, message, position, operationIndex));
    }

    /**
     * Record a warning.
     *
     * @param warning the warning, never {@code null}
     */
    public void add(LiftPatchWarning warning) {
        warnings.add(warning);
        LOGGER.log(Level.WARNING, warning::format);
    }

    /**
     * Whether at least one error has been recorded.
     *
     * @return {@code true} when {@link #errors()} is not empty
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * The errors recorded so far, in the order in which they were found.
     *
     * @return an unmodifiable view
     */
    public List<LiftPatchError> errors() {
        return Collections.unmodifiableList(errors);
    }

    /**
     * The warnings recorded so far, in the order in which they were found.
     *
     * @return an unmodifiable view
     */
    public List<LiftPatchWarning> warnings() {
        return Collections.unmodifiableList(warnings);
    }

    /**
     * Throw a {@link LiftPatchException} carrying every recorded error, if there is any.
     *
     * @throws LiftPatchException when {@link #hasErrors()} is {@code true}
     */
    public void throwIfAnyError() {
        if (hasErrors()) {
            throw new LiftPatchException(errors);
        }
    }
}
