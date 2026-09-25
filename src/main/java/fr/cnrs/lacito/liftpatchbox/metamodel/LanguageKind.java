package fr.cnrs.lacito.liftpatchbox.metamodel;

import fr.cnrs.lacito.liftpatchbox.error.ErrorCode;

/**
 * The two language kinds a LIFT dictionary keeps a list of (Part 1, section 2).
 *
 * <p>A multitext property is associated with exactly one of them by its
 * {@code qualifier} in the metamodel: {@code form} takes an object language,
 * {@code gloss} a meta language. Scalar properties are associated with
 * neither.</p>
 */
public enum LanguageKind {

    /** A language the dictionary describes, such as the one of {@code entry.form}. */
    OBJECT(ErrorCode.NO_SUCH_OBJECT_LANGUAGE),

    /** A language the dictionary describes <em>in</em>, such as the one of {@code sense.gloss}. */
    META(ErrorCode.NO_SUCH_META_LANGUAGE);

    private final ErrorCode noSuchLanguage;

    LanguageKind(ErrorCode noSuchLanguage) {
        this.noSuchLanguage = noSuchLanguage;
    }

    /**
     * Parse the {@code qualifier} value of the metamodel document.
     *
     * @param s the value, {@code "object"}, {@code "meta"} or {@code null}
     * @return the matching kind, or {@code null} for a scalar property
     * @throws IllegalArgumentException if the value names no language kind
     */
    public static LanguageKind parse(String s) {
        if (s == null) {
            return null;
        }
        return switch (s) {
            case "object" -> OBJECT;
            case "meta" -> META;
            default -> throw new IllegalArgumentException("Unknown language kind: " + s);
        };
    }

    /**
     * The error code raised when a language code of this kind is not in the
     * dictionary's list for that kind.
     *
     * @return {@link ErrorCode#NO_SUCH_OBJECT_LANGUAGE} or {@link ErrorCode#NO_SUCH_META_LANGUAGE}
     */
    public ErrorCode noSuchLanguageError() {
        return noSuchLanguage;
    }

    /**
     * The spelling this kind has in a script and in the metamodel document.
     *
     * @return {@code "object"} or {@code "meta"}
     */
    public String jsonName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
