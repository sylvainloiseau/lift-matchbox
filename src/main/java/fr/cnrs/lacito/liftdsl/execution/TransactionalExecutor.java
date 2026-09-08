package fr.cnrs.lacito.liftdsl.execution;

import fr.cnrs.lacito.liftapi.*;
import fr.cnrs.lacito.liftapi.builder.*;
import fr.cnrs.lacito.liftapi.model.*;
import fr.cnrs.lacito.liftdsl.api.*;
import fr.cnrs.lacito.liftdsl.model.*;
import fr.cnrs.lacito.liftdsl.validation.*;
import java.util.*;

/**
 * Executes a validated program atomically.
 * <p>
 * Every supported mutation records an inverse operation. If a later command fails, inverse operations are replayed in
 * reverse order to restore the dictionary state as far as the underlying lift-api permits.
 * </p>
 */
public final class TransactionalExecutor {
    private final LiftDictionary dictionary;
    private final List<Runnable> undo = new ArrayList<>();
    private String objectLanguage;
    private String metaLanguage;

    /**
     * Creates an executor for one dictionary.
     *
     * @param dictionary
     *            dictionary to mutate
     *
     * @throws NullPointerException
     *             if {@code dictionary} is null
     */
    public TransactionalExecutor(LiftDictionary dictionary) {
        this.dictionary = Objects.requireNonNull(dictionary);
    }

    /**
     * Validates and executes a complete program as one transaction.
     *
     * @param program
     *            program to apply
     *
     * @throws NullPointerException
     *             if {@code program} is null
     * @throws RuntimeException
     *             if validation or execution fails; recorded mutations are rolled back
     */
    public void execute(Program program) {
        new SemanticValidator().validate(program);
        LiftDictionaryValidator validator = new LiftDictionaryValidator(dictionary);
        undo.clear();
        objectLanguage = preferredObjectLanguage();
        metaLanguage = preferredMetaLanguage();
        try {
            for (Command c : program.commands())
                execute(c, validator);
        } catch (RuntimeException e) {
            rollback();
            throw e;
        } finally {
            undo.clear();
        }
    }

    private void execute(Command c, LiftDictionaryValidator validator) {
        if (c.kind() == Command.Kind.LANGUAGE_DEFAULT) {
            setDefaultLanguage(c);
            return;
        }
        if (c.kind() == Command.Kind.SET || c.kind() == Command.Kind.UPDATE || c.kind() == Command.Kind.CLEAR) {
            applyProperty(c, validator);
            return;
        }
        if (c.kind() == Command.Kind.DELETE) {
            AbstractLiftRoot node = validator.resolve(c.target()).get(0);
            dictionary.getLiftDictionaryRegistry().removeFromDictionary(node);
            return;
        }
        if (c.kind() == Command.Kind.CREATE || c.kind() == Command.Kind.UPSERT || c.kind() == Command.Kind.ENSURE)
            create(c, validator);
        for (Command child : c.children())
            execute(child, validator);
    }

    private void create(Command c, LiftDictionaryValidator validator) {
        AbstractLiftRoot resolvedParent = null;
        if (c.target() != null)
            resolvedParent = validator.resolve(c.target()).get(0);
        final AbstractLiftRoot parent = resolvedParent;
        List<PropertyValue> v = c.values();
        if ("entry".equals(c.componentType())) {
            EntryBuilder b = dictionary.getComponentBuilder().entry();
            for (PropertyValue p : v)
                if ("form".equals(p.name()))
                    b.withForm(p.language() != null ? p.language() : defaultObject(), p.value());
            LiftEntry e = b.build();
            undo.add(() -> dictionary.getLiftDictionaryRegistry().removeFromDictionary(e));
            return;
        }
        if (parent == null)
            throw new DictionaryValidationException(ValidationError.Code.NOT_FOUND,
                    "A non-entry component requires a parent");
        if ("sense".equals(c.componentType()) && parent instanceof LiftEntry) {
            SenseBuilder b = dictionary.getComponentBuilder().sense((LiftEntry) parent);
            for (PropertyValue p : v) {
                if ("gloss".equals(p.name()))
                    b.withGloss(p.language() != null ? p.language() : defaultMeta(), p.value());
                if ("definition".equals(p.name()))
                    b.withDefinition(p.language() != null ? p.language() : defaultMeta(), p.value());
            }
            LiftSense n = b.build();
            undo.add(() -> ((LiftEntry) parent).getSenses().remove(n));
            return;
        }
        if ("example".equals(c.componentType()) && parent instanceof LiftSense) {
            ExampleBuilder b = dictionary.getComponentBuilder().example((LiftSense) parent);
            for (PropertyValue p : v)
                if ("text".equals(p.name()))
                    b.withExample(p.language() != null ? p.language() : defaultObject(), p.value());
            LiftExample n = b.build();
            undo.add(() -> ((LiftSense) parent).getExamples().remove(n));
            return;
        }
        throw new ExecutionException("Unsupported component integration: " + c.componentType());
    }

    private void applyProperty(Command c, LiftDictionaryValidator validator) {
        AbstractLiftRoot node = validator.resolve(c.target()).get(0);
        PropertyValue p = c.property().get();
        if (node instanceof LiftEntry && "form".equals(p.name()))
            set(((LiftEntry) node).getForms(), p);
        else if (node instanceof LiftSense && "gloss".equals(p.name()))
            set(((LiftSense) node).getGlosses(), p);
        else if (node instanceof LiftSense && "definition".equals(p.name()))
            set(((LiftSense) node).getDefinition(), p);
        else if (node instanceof LiftExample && "text".equals(p.name()))
            set(((LiftExample) node).getExample(), p);
        else
            throw new ExecutionException("Unsupported property integration: " + p.name());
    }

    private void set(MultiText t, PropertyValue p) {
        String lang = p.language() != null ? p.language() : defaultLanguageFor(p.name());
        String old = t.getForm(lang).map(Form::toPlainText).orElse(null);
        if (p.value() == null)
            t.removeForm(lang);
        else
            t.add(new Form(lang, p.value()));
        undo.add(() -> {
            if (old == null) {
                if (t.containsLang(lang))
                    t.removeForm(lang);
            } else
                t.add(new Form(lang, old));
        });
    }

    private String defaultObject() {
        return objectLanguage;
    }

    private String defaultMeta() {
        return metaLanguage;
    }

    private String language(List<PropertyValue> v) {
        return v.isEmpty() ? null : v.get(0).language();
    }

    private String language(PropertyValue p) {
        return p.language() != null ? p.language() : defaultObject();
    }

    private String defaultLanguageFor(String property) {
        return "gloss".equals(property) || "definition".equals(property) || "comment".equals(property)
                || "label".equals(property) || "source".equals(property) ? defaultMeta() : defaultObject();
    }

    private void rollback() {
        for (int i = undo.size() - 1; i >= 0; i--)
            try {
                undo.get(i).run();
            } catch (RuntimeException ignored) {
            }
    }

    private String preferredObjectLanguage() {
        return dictionary.getObjectLanguageManager().hasLanguage("tww") ? "tww"
                : dictionary.getObjectLanguageManager().getLanguages().stream().findFirst()
                        .orElseThrow(() -> new ExecutionException("Dictionary has no object language"));
    }

    private String preferredMetaLanguage() {
        return dictionary.getMetaLanguageManager().hasLanguage("en") ? "en"
                : dictionary.getMetaLanguageManager().getLanguages().stream().findFirst()
                        .orElseThrow(() -> new ExecutionException("Dictionary has no meta language"));
    }

    private void setDefaultLanguage(Command command) {
        String value = command.language().orElseThrow(() -> new ExecutionException("Missing default language"));
        if ("object".equals(command.languageSet().orElse(null))) {
            if (!dictionary.getObjectLanguageManager().hasLanguage(value))
                throw new DictionaryValidationException(ValidationError.Code.ILLEGAL_LANGUAGE,
                        "Unknown object language: " + value);
            String old = objectLanguage;
            objectLanguage = value;
            undo.add(() -> objectLanguage = old);
        } else if ("meta".equals(command.languageSet().orElse(null))) {
            if (!dictionary.getMetaLanguageManager().hasLanguage(value))
                throw new DictionaryValidationException(ValidationError.Code.ILLEGAL_LANGUAGE,
                        "Unknown meta language: " + value);
            String old = metaLanguage;
            metaLanguage = value;
            undo.add(() -> metaLanguage = old);
        } else
            throw new ExecutionException("Unknown language set: " + command.languageSet().orElse(""));
    }
}
