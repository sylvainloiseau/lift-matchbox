package fr.cnrs.lacito.liftpatchbox;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftpatchbox.ast.Script;
import fr.cnrs.lacito.liftpatchbox.ast.Syntax;
import fr.cnrs.lacito.liftpatchbox.engine.Executor;
import fr.cnrs.lacito.liftpatchbox.engine.Plan;
import fr.cnrs.lacito.liftpatchbox.error.ErrorCollector;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchException;
import fr.cnrs.lacito.liftpatchbox.metamodel.LanguageKind;
import fr.cnrs.lacito.liftpatchbox.metamodel.Metamodel;
import fr.cnrs.lacito.liftpatchbox.model.Journal;
import fr.cnrs.lacito.liftpatchbox.model.LiftModel;
import fr.cnrs.lacito.liftpatchbox.parser.ScriptParser;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The library's front door: parse a script, validate it, and either plan it or
 * apply it to a dictionary.
 *
 * <pre>{@code
 * LiftDictionary dictionary = LiftDictionary.loadDictionaryFromFile(new File("tww.lift"));
 * LiftPatchBox box = new LiftPatchBox()
 *     .withDefaultObjectLanguage("tww")
 *     .withDefaultMetaLanguage("en");
 *
 * Plan plan = box.plan(dictionary, script, Syntax.CONCISE, "edits.liftpatchs");
 * if (plan.isOk()) {
 *     box.apply(dictionary, script, Syntax.CONCISE, "edits.liftpatchs");
 * }
 * }</pre>
 *
 * <p>The three phases are always run in the same order, and each is available on
 * its own for a caller that needs it: parsing, which yields the command model;
 * static validation, which reports every error that depends only on the script
 * and the metamodel and, when there is one, stops before the dictionary is
 * touched; and execution, which resolves against the dictionary and applies the
 * commands in one transaction.</p>
 */
public final class LiftPatchBox {

    private static final Logger LOGGER = Logger.getLogger(LiftPatchBox.class.getName());

    private final Metamodel metamodel;
    private String defaultObjectLanguage;
    private String defaultMetaLanguage;

    /**
     * A front door validating against the standard metamodel of Appendix A.
     */
    public LiftPatchBox() {
        this(Metamodel.standard());
    }

    /**
     * A front door validating against a given metamodel.
     *
     * @param metamodel the metamodel; an extended one may declare additional
     *                  component types and properties
     */
    public LiftPatchBox(Metamodel metamodel) {
        this.metamodel = metamodel;
    }

    /**
     * Set the default object language, overriding the dictionary's first one.
     *
     * <p>Part 1, section 2.1 makes the first language of the dictionary's list the
     * fallback default. A caller that knows better — a test fixture, a project
     * whose language list is not in the order the editor thinks in — states it
     * here, and a {@code language-default} directive or a {@code with} header in
     * the script still overrides it.</p>
     *
     * @param language the language code, or {@code null} to use the dictionary's first
     * @return this instance
     */
    public LiftPatchBox withDefaultObjectLanguage(String language) {
        this.defaultObjectLanguage = language;
        return this;
    }

    /**
     * Set the default meta language, overriding the dictionary's first one.
     *
     * @param language the language code, or {@code null} to use the dictionary's first
     * @return this instance
     */
    public LiftPatchBox withDefaultMetaLanguage(String language) {
        this.defaultMetaLanguage = language;
        return this;
    }

    /**
     * The metamodel this instance validates against.
     *
     * @return the metamodel
     */
    public Metamodel metamodel() {
        return metamodel;
    }

    // ===================================================================
    // Parsing
    // ===================================================================

    /**
     * Parse a script held in a string.
     *
     * @param text   the script text
     * @param syntax the syntax to parse it with, or {@code null} to take it from the pragma
     * @param source the source name, used in diagnostics and plan documents
     * @return the command model
     * @throws LiftPatchException when the script does not parse
     */
    public Script parse(String text, Syntax syntax, String source) {
        return new ScriptParser(metamodel).parse(text, syntax, source);
    }

    /**
     * Parse a script held in a file.
     *
     * @param file   the file, read as UTF-8
     * @param syntax the syntax to parse it with, or {@code null} to take it from the
     *               pragma or from the file extension
     * @return the command model
     * @throws IOException        if the file cannot be read
     * @throws LiftPatchException when the script does not parse
     */
    public Script parseFile(Path file, Syntax syntax) throws IOException {
        return new ScriptParser(metamodel).parseFile(file, syntax);
    }

    // ===================================================================
    // Validation
    // ===================================================================

    /**
     * Run the static validation pass.
     *
     * @param script the command model
     * @return the collected errors and warnings; empty when the script is free of
     *         static errors
     */
    public ErrorCollector validate(Script script) {
        return new fr.cnrs.lacito.liftpatchbox.validation.SemanticValidator(metamodel)
            .validate(script);
    }

    // ===================================================================
    // Planning and applying
    // ===================================================================

    /**
     * Validate a script and resolve it against a dictionary without committing
     * anything: the plan mode of Part 2, section 12.4.
     *
     * @param dictionary the dictionary to plan against
     * @param script     the command model
     * @return the plan document, whose status says whether the script would apply cleanly
     */
    public Plan plan(LiftDictionary dictionary, Script script) {
        return run(dictionary, script, true);
    }

    /**
     * Validate a script and apply it to a dictionary in one transaction.
     *
     * @param dictionary the dictionary to patch
     * @param script     the command model
     * @return the plan document describing what was applied
     * @throws LiftPatchException when the script has a static error, in which case
     *                            nothing is applied, or when a command fails, in
     *                            which case everything already applied is rolled back
     */
    public Plan apply(LiftDictionary dictionary, Script script) {
        Plan plan = run(dictionary, script, false);
        if (!plan.isOk()) {
            throw new LiftPatchException(
                plan.staticErrors().isEmpty()
                    ? java.util.List.of(plan.error())
                    : plan.staticErrors());
        }
        LOGGER.log(Level.INFO, () -> "Applied " + script.source() + ": "
            + plan.summary().componentsCreated() + " component(s) created, "
            + plan.summary().componentsDeleted() + " deleted, "
            + plan.summary().propertiesSet() + " property value(s) written");
        LOGGER.log(Level.FINE, () -> plan.summary().format());
        return plan;
    }

    /**
     * Parse, validate and apply a script in one call.
     *
     * @param dictionary the dictionary to patch
     * @param text       the script text
     * @param syntax     the syntax to parse it with, or {@code null}
     * @param source     the source name
     * @return the plan document describing what was applied
     */
    public Plan apply(LiftDictionary dictionary, String text, Syntax syntax, String source) {
        return apply(dictionary, parse(text, syntax, source));
    }

    /**
     * Parse, validate and plan a script in one call.
     *
     * @param dictionary the dictionary to plan against
     * @param text       the script text
     * @param syntax     the syntax to parse it with, or {@code null}
     * @param source     the source name
     * @return the plan document
     */
    public Plan plan(LiftDictionary dictionary, String text, Syntax syntax, String source) {
        return plan(dictionary, parse(text, syntax, source));
    }

    private Plan run(LiftDictionary dictionary, Script script, boolean planOnly) {
        ErrorCollector errors = validate(script);
        if (errors.hasErrors()) {
            // A script containing a static error changes nothing, and every static
            // error is reported before any command is applied (section 12.3).
            return Plan.ofStaticErrors(script.source(), script.syntax(), metamodel.version(),
                errors.errors(), errors.warnings());
        }
        Journal journal = new Journal();
        LiftModel model = new LiftModel(dictionary, metamodel, journal);
        Executor executor = new Executor(model, metamodel, journal);
        applyDefaultLanguages(executor, model);
        return executor.run(script, planOnly);
    }

    private void applyDefaultLanguages(Executor executor, LiftModel model) {
        if (defaultObjectLanguage != null
            && model.hasLanguage(LanguageKind.OBJECT, defaultObjectLanguage)) {
            executor.bindDefaultLanguage(LanguageKind.OBJECT, defaultObjectLanguage);
        }
        if (defaultMetaLanguage != null
            && model.hasLanguage(LanguageKind.META, defaultMetaLanguage)) {
            executor.bindDefaultLanguage(LanguageKind.META, defaultMetaLanguage);
        }
    }
}
