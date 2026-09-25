package fr.cnrs.lacito.liftpatchbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.Form;
import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftapi.model.LiftSense;
import fr.cnrs.lacito.liftpatchbox.ast.Syntax;
import fr.cnrs.lacito.liftpatchbox.engine.Effect;
import fr.cnrs.lacito.liftpatchbox.engine.Plan;
import fr.cnrs.lacito.liftpatchbox.error.ErrorCode;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchError;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchException;
import java.util.List;
import java.util.Optional;

/**
 * What the behaviour tests are written with: one dictionary per test, and three
 * ways of stating what a script should do.
 *
 * <p>Each test gets a fresh copy of the {@code "standard"} fixture of Appendix
 * D.2, so that tests do not have to undo one another's work and can be read in
 * any order. The fixture is built in memory and costs nothing; the tests that
 * need a real field dictionary use {@link TuwuliDictionary} instead.</p>
 */
public abstract class PatchTestSupport {

    /** The dictionary this test runs against, rebuilt before each test. */
    protected LiftDictionary dictionary;

    /** The front door, configured with the fixture's languages. */
    protected LiftPatchBox box;

    /**
     * Rebuild the fixture. JUnit calls this before each test through the
     * {@code @BeforeEach} of the concrete class.
     */
    protected void newDictionary() {
        dictionary = StandardFixture.build();
        box = new LiftPatchBox()
            .withDefaultObjectLanguage("tww")
            .withDefaultMetaLanguage("en");
    }

    // ===================================================================
    // Running
    // ===================================================================

    /**
     * Apply a reference-syntax script and return what it did.
     *
     * @param script the script
     * @return the plan document
     */
    protected Plan apply(String script) {
        return box.apply(dictionary, script, Syntax.REFERENCE, "<test>");
    }

    /**
     * Apply a concise-syntax script and return what it did.
     *
     * @param script the script
     * @return the plan document
     */
    protected Plan applyConcise(String script) {
        return box.apply(dictionary, script, Syntax.CONCISE, "<test>");
    }

    /**
     * Plan a reference-syntax script without changing anything.
     *
     * @param script the script
     * @return the plan document
     */
    protected Plan plan(String script) {
        return box.plan(dictionary, script, Syntax.REFERENCE, "<test>");
    }

    // ===================================================================
    // Asserting
    // ===================================================================

    /**
     * Assert that a reference-syntax script is refused with a given code, and that
     * the dictionary is left unchanged.
     *
     * @param expected the code the script must raise
     * @param script   the script
     * @return the error, so that a test may look at its position or message
     */
    protected LiftPatchError refused(ErrorCode expected, String script) {
        return refused(expected, script, Syntax.REFERENCE);
    }

    /**
     * Assert that a concise-syntax script is refused with a given code.
     *
     * @param expected the code the script must raise
     * @param script   the script
     * @return the error
     */
    protected LiftPatchError refusedConcise(ErrorCode expected, String script) {
        return refused(expected, script, Syntax.CONCISE);
    }

    private LiftPatchError refused(ErrorCode expected, String script, Syntax syntax) {
        int entriesBefore = dictionary.getLiftDictionaryRegistry().getEntries().size();
        try {
            box.apply(dictionary, script, syntax, "<test>");
        } catch (LiftPatchException e) {
            assertEquals(expected, e.code(),
                "wrong code (" + e.firstError().message() + ")");
            assertEquals(entriesBefore, dictionary.getLiftDictionaryRegistry().getEntries().size(),
                "a refused script must leave the dictionary unchanged");
            return e.firstError();
        }
        fail("expected " + expected + " but the script applied cleanly: " + script);
        return null;
    }

    /**
     * Assert the effects of a plan, rendered as the comparable fields of
     * Appendix D.1.
     *
     * @param plan     the plan
     * @param expected the effects, each written {@code kind:detail}
     */
    protected static void assertEffects(Plan plan, String... expected) {
        assertEquals(List.of(expected), render(plan.allEffects()));
    }

    private static List<String> render(List<Effect> effects) {
        return effects.stream().map(e -> switch (e.kind()) {
            case componentCreated -> "created:" + e.componentType() + "@" + e.position();
            case componentDeleted -> "deleted:" + e.componentType() + "@" + e.position();
            case componentMoved -> "moved:" + e.componentType() + ":"
                + e.fromPosition() + "->" + e.toPosition();
            case propertySet -> "set:" + qualified(e) + "=" + e.newValue();
            case propertyReplaced -> "replaced:" + qualified(e) + ":"
                + e.oldValue() + "->" + e.newValue();
            case propertyRemoved -> "removed:" + qualified(e) + "=" + e.oldValue();
            case languageCreated -> "language:" + e.languageKind() + ":" + e.language();
        }).toList();
    }

    private static String qualified(Effect e) {
        return e.language() == null ? e.property() : e.property() + "@" + e.language();
    }

    // ===================================================================
    // Reading the dictionary back
    // ===================================================================

    /**
     * The entries carrying a qualified form.
     *
     * @param language the object language
     * @param form     the form
     * @return the matching entries, in dictionary order
     */
    protected List<LiftEntry> entries(String language, String form) {
        return TuwuliDictionary.entriesByForm(dictionary, language, form);
    }

    /**
     * The one entry carrying a qualified form, failing when there is not exactly one.
     *
     * @param language the object language
     * @param form     the form
     * @return the entry
     */
    protected LiftEntry entry(String language, String form) {
        List<LiftEntry> found = entries(language, form);
        assertEquals(1, found.size(), "expected exactly one entry with " + language + ":" + form);
        return found.get(0);
    }

    /**
     * The senses of an entry whose gloss in a language is a given string.
     *
     * @param entry    the entry
     * @param language the meta language
     * @param gloss    the gloss
     * @return the matching senses
     */
    protected static List<LiftSense> senses(LiftEntry entry, String language, String gloss) {
        return entry.getSenses().stream()
            .filter(s -> s.getGlosses().getForm(language)
                .map(Form::toPlainText).filter(gloss::equals).isPresent())
            .toList();
    }

    /**
     * One qualified value of a sense's definition.
     *
     * @param sense    the sense
     * @param language the meta language
     * @return the definition, or empty when it is unset for that language
     */
    protected static Optional<String> definition(LiftSense sense, String language) {
        return sense.getDefinition().getForm(language).map(Form::toPlainText);
    }

    /**
     * The grammatical category of a sense.
     *
     * @param sense the sense
     * @return the part of speech, or empty when the sense has none
     */
    protected static Optional<String> category(LiftSense sense) {
        return sense.getGrammaticalInfo()
            .map(g -> g.getGramInfoValue())
            .map(f -> f.getId());
    }
}
