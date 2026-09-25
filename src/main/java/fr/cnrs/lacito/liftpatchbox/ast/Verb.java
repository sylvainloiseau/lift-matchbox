package fr.cnrs.lacito.liftpatchbox.ast;

/**
 * The eight verbs of the language, with their concise one-letter codes.
 *
 * <p>The five {@linkplain #isComponentCommand() component commands} target a
 * component, the three property commands target a property of one. The matrix of
 * preconditions and postconditions is given in Part 2, section 7.1.</p>
 */
public enum Verb {

    /** Creates a new component; fails if the identity already exists. */
    CREATE("create", "c", true),
    /** Creates the component if it is missing, resolves it otherwise. */
    UPSERT("upsert", "p", true),
    /** Asserts that a component exists; creates and modifies nothing. */
    ENSURE("ensure", "e", true),
    /** Removes a component with its descendants. */
    DELETE("delete", "d", true),
    /** Changes the position or the parent of an ordered component. */
    MOVE("move", "m", true),
    /** Creates a property value, or replaces it if it is present. */
    SET("set", "s", false),
    /** Replaces an existing property value; never creates one. */
    UPDATE("update", "u", false),
    /** Removes property values without removing the component. */
    CLEAR("clear", "l", false);

    private final String keyword;
    private final String code;
    private final boolean componentCommand;

    Verb(String keyword, String code, boolean componentCommand) {
        this.keyword = keyword;
        this.code = code;
        this.componentCommand = componentCommand;
    }

    /**
     * The LiftPatchRef keyword of this verb.
     *
     * @return the keyword, such as {@code "upsert"}
     */
    public String keyword() {
        return keyword;
    }

    /**
     * The LiftPatchShort one-letter code of this verb.
     *
     * @return the code, such as {@code "p"} for {@code upsert}
     */
    public String code() {
        return code;
    }

    /**
     * Whether this verb targets a component rather than a property.
     *
     * @return {@code true} for the five component commands
     */
    public boolean isComponentCommand() {
        return componentCommand;
    }

    /**
     * Whether this verb admits a multiplicity keyword on its target step
     * (Part 1, section 5.6).
     *
     * @return {@code true} for {@code delete}, {@code set}, {@code update} and {@code clear}
     */
    public boolean allowsMultiplicity() {
        return this == DELETE || this == SET || this == UPDATE || this == CLEAR;
    }

    /**
     * Parse a LiftPatchRef verb keyword.
     *
     * @param keyword the keyword
     * @return the matching verb, or {@code null} when the word is not a verb
     */
    public static Verb fromKeyword(String keyword) {
        for (Verb v : values()) {
            if (v.keyword.equals(keyword)) {
                return v;
            }
        }
        return null;
    }

    /**
     * Parse a LiftPatchShort command letter.
     *
     * @param code the one-letter code
     * @return the matching verb, or {@code null} when the letter is not a command code
     */
    public static Verb fromCode(String code) {
        for (Verb v : values()) {
            if (v.code.equals(code)) {
                return v;
            }
        }
        return null;
    }
}
