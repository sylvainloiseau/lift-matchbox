package fr.cnrs.lacito.liftpatchbox.error;

/**
 * One warning reported by validation or by plan mode.
 *
 * @param code           the warning code
 * @param message        a human-readable explanation; never compared by conformance tests
 * @param position       where in the script the warning applies, or
 *                       {@link SourcePosition#UNKNOWN} for a document-level warning
 * @param operationIndex the 1-based operation index, or 0 for a document-level warning
 */
public record LiftPatchWarning(
    WarningCode code,
    String message,
    SourcePosition position,
    int operationIndex
) {

    /**
     * A document-level warning, carrying neither a position nor an operation index.
     *
     * @param code    the warning code
     * @param message a human-readable explanation
     * @return the warning
     */
    public static LiftPatchWarning document(WarningCode code, String message) {
        return new LiftPatchWarning(code, message, SourcePosition.UNKNOWN, 0);
    }

    /**
     * A one-line rendering of this warning, suitable for a log or a terminal.
     *
     * @return the code, the position and the message
     */
    public String format() {
        StringBuilder sb = new StringBuilder("warning [").append(code).append(']');
        if (position.isKnown()) {
            sb.append(" at ").append(position);
        }
        sb.append(": ").append(message);
        return sb.toString();
    }

    @Override
    public String toString() {
        return format();
    }
}
