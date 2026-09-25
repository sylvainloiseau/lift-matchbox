package fr.cnrs.lacito.liftpatchbox.error;

/**
 * A position in the source of a script, used to point an error or a plan
 * operation back at the text the user wrote.
 *
 * <p>Lines and columns are 1-based, which is what an editor displays. The
 * constant {@link #UNKNOWN} stands for a position that no token carries, such as
 * the position of a script-level error.</p>
 *
 * @param line   the 1-based line number, or 0 when unknown
 * @param column the 1-based column number, or 0 when unknown
 */
public record SourcePosition(int line, int column) {

    /** The position of something that has none, such as a whole-script error. */
    public static final SourcePosition UNKNOWN = new SourcePosition(0, 0);

    /**
     * A position at the first column of the given line.
     *
     * @param line the 1-based line number
     * @return the position of the start of that line
     */
    public static SourcePosition ofLine(int line) {
        return new SourcePosition(line, 1);
    }

    /**
     * Whether this position points at a known place in the source.
     *
     * @return {@code true} when the line number is known
     */
    public boolean isKnown() {
        return line > 0;
    }

    @Override
    public String toString() {
        return isKnown() ? "line " + line + ", column " + column : "unknown position";
    }
}
