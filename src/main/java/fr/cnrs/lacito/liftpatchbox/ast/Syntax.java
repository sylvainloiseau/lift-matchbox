package fr.cnrs.lacito.liftpatchbox.ast;

/**
 * The two surface syntaxes of the LiftPatch language.
 *
 * <p>They share one semantics and one command model: a script parsed with either
 * of them yields the same {@link Script}. The constant is kept on the script so
 * that plan mode can report {@code script.syntax}, which Part 2, section 12.4.1
 * requires to be always present and always compared.</p>
 */
public enum Syntax {

    /** The verbose, explicit reference syntax of Part 2. */
    REFERENCE("LiftPatchRef", "liftpatch"),

    /** The concise, line-oriented syntax of Part 3. */
    CONCISE("LiftPatchShort", "liftpatchs");

    private final String pragmaName;
    private final String fileExtension;

    Syntax(String pragmaName, String fileExtension) {
        this.pragmaName = pragmaName;
        this.fileExtension = fileExtension;
    }

    /**
     * The name this syntax has in the {@code syntax=} pragma attribute and in a
     * plan document.
     *
     * @return {@code "LiftPatchRef"} or {@code "LiftPatchShort"}
     */
    public String pragmaName() {
        return pragmaName;
    }

    /**
     * The file extension this specification recommends for this syntax.
     *
     * @return {@code "liftpatch"} or {@code "liftpatchs"}, without the dot
     */
    public String fileExtension() {
        return fileExtension;
    }

    /**
     * Parse the value of the {@code syntax=} pragma attribute.
     *
     * @param name the attribute value
     * @return the matching syntax, or {@code null} when the name is neither
     */
    public static Syntax fromPragmaName(String name) {
        for (Syntax s : values()) {
            if (s.pragmaName.equals(name)) {
                return s;
            }
        }
        return null;
    }

    /**
     * Guess the syntax from a file name, using the recommended extensions.
     *
     * @param fileName the file name
     * @return {@link #CONCISE} for a {@code .liftpatchs} file, {@link #REFERENCE}
     *         for a {@code .liftpatch} file, {@code null} for anything else
     */
    public static Syntax fromFileName(String fileName) {
        String lower = fileName.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".liftpatchs")) {
            return CONCISE;
        }
        if (lower.endsWith(".liftpatch")) {
            return REFERENCE;
        }
        return null;
    }
}
