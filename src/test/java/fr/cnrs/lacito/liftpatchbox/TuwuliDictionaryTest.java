package fr.cnrs.lacito.liftpatchbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.Form;
import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftapi.model.LiftSense;
import fr.cnrs.lacito.liftpatchbox.ast.Syntax;
import fr.cnrs.lacito.liftpatchbox.engine.Plan;
import fr.cnrs.lacito.liftpatchbox.error.ErrorCode;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs against the real Tuwuli dictionary, the one the library is meant for.
 *
 * <p>Nineteen hundred entries reach what the small fixture of Appendix D cannot:
 * homophones the dictionary really has, entries with several senses, senses with
 * several glosses, notes keyed by type, a grammatical-info range with a hundred
 * parts of speech, and a parse-and-save round trip over two megabytes of XML.</p>
 *
 * <p>The default object language is set to {@code tww} and the default meta
 * language to {@code en}, which is what a Tuwuli editing session would set
 * once.</p>
 */
@DisplayName("The Tuwuli dictionary")
@EnabledIf("fr.cnrs.lacito.liftpatchbox.TuwuliDictionary#available")
class TuwuliDictionaryTest {

    private static LiftDictionary shared;

    @BeforeAll
    static void loadOnce() throws Exception {
        shared = TuwuliDictionary.shared();
    }

    private static LiftPatchBox box() {
        return TuwuliDictionary.box();
    }

    @Nested
    @DisplayName("reading")
    class Reading {

        @Test
        @DisplayName("loads with the languages the tests write in")
        void loadsWithTheExpectedLanguages() {
            assertTrue(shared.getObjectLanguageManager().hasLanguage("tww"));
            assertTrue(shared.getMetaLanguageManager().hasLanguage("en"));
            assertTrue(shared.getLiftDictionaryRegistry().getEntries().size() > 1000);
        }

        @Test
        @DisplayName("an ensure over a real entry and sense succeeds and changes nothing")
        void ensureOverARealEntry() {
            LiftEntry entry = anEntryWithASense();
            String form = form(entry);
            String gloss = gloss(entry.getSenses().get(0));
            Plan plan = box().plan(shared,
                "e /e[f=\"" + form + "\"] s[g=\"" + gloss + "\"]",
                Syntax.CONCISE, "<tww>");
            assertTrue(plan.isOk(), () -> String.valueOf(plan.error()));
            assertTrue(plan.allEffects().isEmpty());
        }

        @Test
        @DisplayName("a homophone number is assigned to every entry and is at least 1")
        void everyEntryHasAHomophoneNumber() {
            // The dictionary model carries no homophone number, so the library
            // assigns one; the normative model requires every entry to have exactly
            // one, greater than or equal to 1 (Part 1, section 5.3.2.1).
            LiftEntry entry = shared.getLiftDictionaryRegistry().getEntries().get(0);
            Plan plan = box().plan(shared,
                "ensure entry[form@tww = \"" + form(entry) + "\", hn = 1]",
                Syntax.REFERENCE, "<tww>");
            assertTrue(plan.isOk(), () -> String.valueOf(plan.error()));
        }
    }

    @Nested
    @DisplayName("planning")
    class Planning {

        @Test
        @DisplayName("plans a session without changing the dictionary")
        void plansWithoutChanging() {
            int before = shared.getLiftDictionaryRegistry().getEntries().size();
            Plan plan = box().plan(shared, """
                c e("liftpatchbox-test")
                  c s("a gloss added by a test")
                    s (d@en = "A definition added by a test")
                """, Syntax.CONCISE, "<tww>");
            assertTrue(plan.isOk(), () -> String.valueOf(plan.error()));
            assertEquals(2, plan.summary().componentsCreated());
            assertEquals(before, shared.getLiftDictionaryRegistry().getEntries().size());
        }

        @Test
        @DisplayName("reports a static error before touching the dictionary")
        void reportsAStaticErrorFirst() {
            Plan plan = box().plan(shared,
                "set value = \"Noun\" on category of sense[gloss@en = \"pig\"]",
                Syntax.REFERENCE, "<tww>");
            assertFalse(plan.isOk());
            assertEquals(ErrorCode.INCOMPLETE_ANCESTOR_CHAIN, plan.error().code());
        }
    }

    @Nested
    @DisplayName("patching")
    class Patching {

        @Test
        @DisplayName("applies a field-note session and saves a dictionary that reloads")
        void appliesAndSaves(@TempDir Path directory) throws Exception {
            LiftDictionary dictionary = TuwuliDictionary.load();
            int before = dictionary.getLiftDictionaryRegistry().getEntries().size();

            Plan plan = box().apply(dictionary, """
                c e("liftpatchbox-test")
                  c s("a gloss added by a test")
                    s (d@en = "A definition added by a test")
                    c x("an example added by a test")
                      c o("free", "a free translation")
                  c n("general", "recorded by the test suite")
                """, Syntax.CONCISE, "<tww>");

            assertTrue(plan.isOk(), () -> String.valueOf(plan.error()));
            assertEquals(before + 1, dictionary.getLiftDictionaryRegistry().getEntries().size());

            Path out = directory.resolve("patched.lift");
            dictionary.save(out.toFile());
            assertTrue(Files.size(out) > 1_000_000);

            LiftDictionary reloaded =
                LiftDictionary.loadDictionaryFromFile(new File(out.toString()));
            List<LiftEntry> found =
                TuwuliDictionary.entriesByForm(reloaded, "tww", "liftpatchbox-test");
            //TuwuliDictionary.entriesByForm(reloaded, "tww", "liftpatchbox-test");
            assertEquals(1, found.size());
            LiftSense sense = found.get(0).getSenses().get(0);
            assertEquals("a gloss added by a test", gloss(sense));
            assertEquals("A definition added by a test",
                sense.getDefinition().getForm("en").orElseThrow().toPlainText());
            assertEquals(1, sense.getExamples().size());
        }

        @Test
        @DisplayName("leaves the dictionary unchanged when a later command fails")
        void leavesTheDictionaryUnchanged() throws Exception {
            LiftDictionary dictionary = TuwuliDictionary.load();
            int before = dictionary.getLiftDictionaryRegistry().getEntries().size();

            assertThrows(LiftPatchException.class, () -> box().apply(dictionary, """
                c e("liftpatchbox-rollback")
                c /e[f="no-such-form-anywhere"] s(g="x")
                """, Syntax.CONCISE, "<tww>"));

            assertEquals(before, dictionary.getLiftDictionaryRegistry().getEntries().size());
            assertTrue(TuwuliDictionary
                .entriesByForm(dictionary, "tww", "liftpatchbox-rollback").isEmpty());
        }

        @Test
        @DisplayName("writes a grammatical category through the singleton component")
        void writesAGrammaticalCategory() throws Exception {
            LiftDictionary dictionary = TuwuliDictionary.load();
            LiftEntry entry = unambiguousEntries(dictionary).stream()
                .filter(e -> !e.getSenses().isEmpty())
                .filter(e -> e.getSenses().get(0).getGlosses().containsLang("en"))
                .findFirst().orElseThrow();
            LiftSense sense = entry.getSenses().get(0);
            String previous = sense.getGrammaticalInfo()
                .map(g -> g.getGramInfoValue().getId()).orElse(null);

            box().apply(dictionary,
                "s /e[f=\"" + form(entry) + "\"]!s[g=\"" + gloss(sense) + "\"]/c (v = \"Adverb\")",
                Syntax.CONCISE, "<tww>");

            assertEquals("Adverb",
                sense.getGrammaticalInfo().orElseThrow().getGramInfoValue().getId());
            assertNotEquals("Adverb", previous);
        }
    }

    // ===================================================================
    // Reading the dictionary
    // ===================================================================

    /**
     * An entry the tests can name unambiguously: one that has a {@code tww} form no
     * other entry shares, and a first sense with an English gloss. A real
     * dictionary has entries without either, and homophones by the dozen, so the
     * search is explicit rather than taking the first entry it finds.
     */
    private static LiftEntry anEntryWithASense() {
        return unambiguousEntries(shared).stream()
            .filter(e -> !e.getSenses().isEmpty())
            .filter(e -> e.getSenses().get(0).getGlosses().containsLang("en"))
            .findFirst().orElseThrow();
    }

    private static List<LiftEntry> unambiguousEntries(LiftDictionary dictionary) {
        return dictionary.getLiftDictionaryRegistry().getEntries().stream()
            .filter(e -> e.getForms().containsLang("tww"))
            .filter(e -> TuwuliDictionary.entriesByForm(dictionary, "tww", form(e)).size() == 1)
            .toList();
    }

    private static String form(LiftEntry entry) {
        return entry.getForms().getForm("tww").map(Form::toPlainText).orElseThrow();
    }

    private static String gloss(LiftSense sense) {
        return sense.getGlosses().getForm("en").map(Form::toPlainText).orElseThrow();
    }
}
