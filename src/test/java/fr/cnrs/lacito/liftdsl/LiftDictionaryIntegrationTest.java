package fr.cnrs.lacito.liftdsl;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftapi.model.AbstractIdentifiable;
import fr.cnrs.lacito.liftdsl.api.LiftDictionaryValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class LiftDictionaryIntegrationTest {
    private static final String DICTIONARY_RESOURCE = "dictionaries/tww/lift20250717.lift";
    private LiftDictionary dictionary;
    private LiftDsl dsl;

    @BeforeEach
    void loadDictionary() throws Exception {
        File dictionaryFile = new File(
                Objects.requireNonNull(getClass().getClassLoader().getResource(DICTIONARY_RESOURCE),
                        "Test dictionary resource is not available: " + DICTIONARY_RESOURCE).toURI());
        dictionary = LiftDictionary.loadDictionaryFromFile(dictionaryFile);
        dsl = new LiftDsl();
    }

    @Test
    void loadsDictionaryAndUsesRequestedDefaultLanguages() {
        assertTrue(dictionary.getObjectLanguageManager().hasLanguage("tww"));
        assertTrue(dictionary.getMetaLanguageManager().hasLanguage("en"));

        String form = uniqueForm("tww", "dsl-default");
        dsl.execute("language-default object = \"tww\"; " + "language-default meta = \"en\"; "
                + "create entry(form = \"" + form + "\"); "
                + "create sense(gloss = \"dsl-default-gloss\") under entry[form@tww = \"" + form + "\"]", dictionary);

        LiftEntry entry = dictionary.getEntryByForm("tww", form).get(0);
        assertTrue(entry.getForms().containsLang("tww"));
        assertEquals("dsl-default-gloss",
                entry.getSenses().get(0).getGlosses().getForm("en").orElseThrow().toPlainText());
    }

    @Test
    void createsEntrySenseAndExampleWithExplicitLanguages() {
        String form = uniqueForm("tww", "dsl-create");
        dsl.execute("create entry(form@tww = \"" + form + "\"); "
                + "create sense(gloss@en = \"dsl-gloss\") under entry[form@tww = \"" + form + "\"]; "
                + "create example(text@tww = \"dsl-example\") under sense[gloss@en = \"dsl-gloss\"] of entry[form@tww = \""
                + form + "\"]", dictionary);

        LiftEntry entry = dictionary.getEntryByForm("tww", form).get(0);
        assertEquals(1, entry.getSenses().size());
        assertEquals(1, entry.getSenses().get(0).getExamples().size());
    }

    @Test
    void rollsBackEarlierMutationsWhenLaterCommandFails() {
        String form = uniqueForm("tww", "dsl-rollback");

        assertThrows(RuntimeException.class,
                () -> dsl.execute("create entry(form@tww = \"" + form + "\"); "
                        + "create sense(gloss@en = \"dsl-rollback-gloss\") under entry[form@tww = \"" + form + "\"]; "
                        + "create note(type = \"unsupported-by-test\") under entry[form@tww = \"" + form + "\"]",
                        dictionary));

        assertTrue(dictionary.getEntryByForm("tww", form).isEmpty());
    }

    @Test
    void rejectsAmbiguousOrMissingDictionaryReferences() {
        assertThrows(RuntimeException.class,
                () -> dsl.execute("delete entry[form@tww = \"definitely-not-present\"]", dictionary));
        assertThrows(RuntimeException.class,
                () -> dsl.execute(
                        "create sense(gloss@en = \"orphan\") under entry[form@tww = \"definitely-not-present\"]",
                        dictionary));
    }

    @Test
    void withinDisambiguatesEfeHomophonesByTheirSenseGloss() {
        var doorCommand = dsl
                .parse("set definition = \"within-door\" on " + "sense[gloss = \"door\"] within entry[form = \"efe\"]")
                .commands().get(0);
        var skinCommand = dsl
                .parse("set definition = \"within-skin\" on " + "sense[gloss = \"skin\"] within entry[form = \"efe\"]")
                .commands().get(0);

        var validator = new LiftDictionaryValidator(dictionary);
        AbstractIdentifiable doorSense = (AbstractIdentifiable) validator.resolve(doorCommand.target()).get(0);
        AbstractIdentifiable skinSense = (AbstractIdentifiable) validator.resolve(skinCommand.target()).get(0);

        assertNotEquals(doorSense.getId().orElseThrow(), skinSense.getId().orElseThrow(),
                "within must keep the two homophone entries separate");

        dsl.execute(
                "language-default object = \"tww\"; language-default meta = \"en\"; "
                        + "set definition = \"within-door\" on sense[gloss = \"door\"] within entry[form = \"efe\"]",
                dictionary);
        dsl.execute(
                "language-default object = \"tww\"; language-default meta = \"en\"; "
                        + "set definition = \"within-skin\" on sense[gloss = \"skin\"] within entry[form = \"efe\"]",
                dictionary);

        assertEquals("within-door", ((fr.cnrs.lacito.liftapi.model.LiftSense) doorSense).getDefinition().getForm("en")
                .orElseThrow().toPlainText());
        assertEquals("within-skin", ((fr.cnrs.lacito.liftapi.model.LiftSense) skinSense).getDefinition().getForm("en")
                .orElseThrow().toPlainText());
    }

    private String uniqueForm(String language, String prefix) {
        String form;
        int suffix = 0;
        do {
            form = prefix + "-" + System.nanoTime() + "-" + suffix++;
        } while (!dictionary.getEntryByForm(language, form).isEmpty());
        return form;
    }

}
