package fr.cnrs.lacito.liftpatchbox.ast;

import fr.cnrs.lacito.liftpatchbox.error.SourcePosition;

/**
 * One qualified value inside a multi-language literal (Part 1, section 5.5.1),
 * written {@code lang: "text"}.
 *
 * @param language the language code
 * @param text     the string value
 * @param position where the pair was written
 */
public record LangText(String language, String text, SourcePosition position) {

    /**
     * A qualified value at an unknown position.
     *
     * @param language the language code
     * @param text     the string value
     * @return the pair
     */
    public static LangText of(String language, String text) {
        return new LangText(language, text, SourcePosition.UNKNOWN);
    }

    @Override
    public String toString() {
        return language + ": \"" + text + "\"";
    }
}
