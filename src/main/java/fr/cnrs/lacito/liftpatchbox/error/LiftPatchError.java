package fr.cnrs.lacito.liftpatchbox.error;

import java.util.Objects;

/**
 * One error raised by the validation or the execution of a script.
 *
 * <p>An error carries the normative {@link ErrorCode} — the only part a
 * conformance comparison relies on (Part 2, section 12.4.1) — together with a
 * free-prose message, the position in the source, and the 1-based index of the
 * operation that raised it.</p>
 *
 * @param code           the normative error code
 * @param message        a human-readable explanation; never compared by conformance tests
 * @param position       where in the script the error was raised
 * @param operationIndex the 1-based operation index (section 12.4.1), or 0 for a
 *                       script-level error such as a pragma error
 */
public record LiftPatchError(
    ErrorCode code,
    String message,
    SourcePosition position,
    int operationIndex
) {

    /**
     * Canonical constructor, rejecting a null code or message.
     *
     * @param code           the normative error code, never {@code null}
     * @param message        a human-readable explanation, never {@code null}
     * @param position       where in the script the error was raised; {@link SourcePosition#UNKNOWN} if nowhere
     * @param operationIndex the 1-based operation index, or 0
     * @throws NullPointerException if {@code code}, {@code message} or {@code position} is {@code null}
     */
    public LiftPatchError {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(position, "position");
    }

    /**
     * An error with no operation index, for a failure carried by the script as a whole.
     *
     * @param code     the normative error code
     * @param message  a human-readable explanation
     * @param position where in the script the error was raised
     * @return the error
     */
    public static LiftPatchError of(ErrorCode code, String message, SourcePosition position) {
        return new LiftPatchError(code, message, position, 0);
    }

    /**
     * The kind of this error, taken from its code.
     *
     * @return {@link ErrorKind#STATIC} or {@link ErrorKind#DYNAMIC}
     */
    public ErrorKind kind() {
        return code.kind();
    }

    /**
     * A one-line rendering of this error, suitable for a log or a terminal.
     *
     * @return the code, the position, the operation index and the message
     */
    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append(code.kind() == ErrorKind.STATIC ? "static error " : "error ");
        sb.append('[').append(code).append(']');
        if (position.isKnown()) {
            sb.append(" at ").append(position);
        }
        if (operationIndex > 0) {
            sb.append(" (operation ").append(operationIndex).append(')');
        }
        sb.append(": ").append(message);
        return sb.toString();
    }

    @Override
    public String toString() {
        return format();
    }
}
