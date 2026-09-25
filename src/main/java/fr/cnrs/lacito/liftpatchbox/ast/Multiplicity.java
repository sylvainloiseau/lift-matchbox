package fr.cnrs.lacito.liftpatchbox.ast;

/**
 * The multiplicity keyword a command may write immediately before its target
 * step (Part 1, section 5.6).
 *
 * <p>{@code each} and {@code all} are synonyms with identical semantics; the
 * concise syntax writes both as the marker {@code *} appended to the command
 * letter. The spelling is kept so that a command can be rendered back into the
 * text the writer used.</p>
 */
public enum Multiplicity {

    /** {@code each}, which reads better with {@code set}, {@code update} and {@code clear}. */
    EACH("each"),

    /** {@code all}, which reads better with {@code delete}. */
    ALL("all"),

    /** The concise marker {@code *}, which stands for either keyword. */
    STAR("*");

    private final String spelling;

    Multiplicity(String spelling) {
        this.spelling = spelling;
    }

    /**
     * The text this multiplicity was written with.
     *
     * @return {@code "each"}, {@code "all"} or {@code "*"}
     */
    public String spelling() {
        return spelling;
    }

    /**
     * Parse a multiplicity keyword.
     *
     * @param s the keyword, {@code "each"}, {@code "all"} or {@code "*"}
     * @return the matching constant, or {@code null} when {@code s} is {@code null}
     * @throws IllegalArgumentException if {@code s} is not one of the three spellings
     */
    public static Multiplicity parse(String s) {
        if (s == null) {
            return null;
        }
        return switch (s) {
            case "each" -> EACH;
            case "all" -> ALL;
            case "*" -> STAR;
            default -> throw new IllegalArgumentException("Not a multiplicity keyword: " + s);
        };
    }
}
