package fr.cnrs.lacito.liftpatchbox;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.LiftEntry;
import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Loads the Tuwuli dictionary the tests run against.
 *
 * <p>It is a real field dictionary of about nineteen hundred entries, with
 * {@code tww} as its object language and {@code en} and {@code tpi} as its meta
 * languages. Running against it, rather than against the small fixture of
 * Appendix D alone, is what exercises the parts of the implementation that only
 * a real dictionary reaches: entries with several senses, senses with several
 * glosses, homophones, notes keyed by type, traits, and a grammatical-info range
 * with a hundred parts of speech.</p>
 *
 * <p>Loading it takes about a second, so the tests that need it share one
 * instance and undo their own changes, which the language's atomicity makes
 * straightforward: a failing script leaves the dictionary exactly as it was.
 * Tests that must mutate it load a copy of their own.</p>
 */
public final class TuwuliDictionary {

    private static final String RESOURCE = "/dictionary/tww/lift20250717.lift";

    /** The object language of the dictionary. */
    public static final String OBJECT_LANGUAGE = "tww";
    /** The meta language the tests write in. */
    public static final String META_LANGUAGE = "en";

    private static LiftDictionary shared;

    private TuwuliDictionary() {
    }

    /**
     * Whether the dictionary is on the test classpath.
     *
     * @return {@code true} when the resource is present
     */
    public static boolean available() {
        return TuwuliDictionary.class.getResource(RESOURCE) != null;
    }

    /**
     * The path of the dictionary on disk.
     *
     * @return the path
     */
    public static Path path() {
        URL url = Objects.requireNonNull(
            TuwuliDictionary.class.getResource(RESOURCE),
            "Missing test dictionary " + RESOURCE);
        return Path.of(url.getPath());
    }

    /**
     * A freshly loaded dictionary, which the caller may mutate freely.
     *
     * @return a new dictionary
     * @throws Exception if the file cannot be read or parsed
     */
    public static LiftDictionary load() throws Exception {
        return LiftDictionary.loadDictionaryFromFile(new File(path().toString()));
    }

    /**
     * The dictionary shared by the tests that leave it unchanged.
     *
     * @return the shared instance, loaded on first use
     * @throws Exception if the file cannot be read or parsed
     */
    public static synchronized LiftDictionary shared() throws Exception {
        if (shared == null) {
            shared = load();
        }
        return shared;
    }

    /**
     * The entries carrying a qualified form.
     *
     * <p>This scans the entry list rather than calling
     * {@code LiftDictionary.getEntryByForm}, which compares a {@code Form}'s
     * {@code textProperty} instead of its plain text and therefore matches nothing
     * at all on a dictionary loaded from XML, where the text lives in the form's
     * text-span tree. The library itself never uses that method; the tests would
     * otherwise be checking a defect of the dependency rather than the patch.</p>
     *
     * @param dictionary the dictionary to scan
     * @param language   the object language
     * @param form       the form
     * @return the matching entries, in dictionary order
     */
    public static List<LiftEntry> entriesByForm(
        LiftDictionary dictionary, String language, String form
    ) {
        return dictionary.getLiftDictionaryRegistry().getEntries().stream()
            .filter(e -> e.getForms().containsLang(language))
            .filter(e -> form.equals(
                e.getForms().getForm(language).orElseThrow().toPlainText()))
            .toList();
    }

    /**
     * A patch box configured with the dictionary's languages, as every test uses it.
     *
     * @return the configured front door
     */
    public static LiftPatchBox box() {
        return new LiftPatchBox()
            .withDefaultObjectLanguage(OBJECT_LANGUAGE)
            .withDefaultMetaLanguage(META_LANGUAGE);
    }
}
