package fr.cnrs.lacito.liftpatchbox.ast;

import fr.cnrs.lacito.liftpatchbox.error.SourcePosition;
import fr.cnrs.lacito.liftpatchbox.metamodel.LanguageKind;

/**
 * A scope directive: {@code language-default} or {@code language-create}
 * (Part 1, sections 2.1.1 and 2.1.3).
 *
 * <p>The two differ in one respect that matters to the execution model:
 * {@code language-default} binds a name for the rest of a scope and changes
 * nothing, so it is not an operation and takes no index in a plan document,
 * while {@code language-create} changes the dictionary, is an operation, and
 * carries a {@code languageCreated} effect.</p>
 *
 * @param create   {@code true} for {@code language-create}, {@code false} for {@code language-default}
 * @param kind     the language kind bound or created
 * @param language the language code
 * @param position where the directive was written
 */
public record Directive(boolean create, LanguageKind kind, String language, SourcePosition position)
    implements Item {

    /**
     * Whether this directive is an operation in the sense of Part 2, section 12.4.1.
     *
     * @return {@code true} for {@code language-create}, which changes the dictionary
     */
    public boolean isOperation() {
        return create;
    }

    @Override
    public String toString() {
        return (create ? "language-create " : "language-default ")
            + kind.jsonName() + " = \"" + language + "\"";
    }
}
