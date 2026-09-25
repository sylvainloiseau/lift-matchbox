package fr.cnrs.lacito.liftpatchbox;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.builder.SenseBuilder;
import fr.cnrs.lacito.liftpatchbox.metamodel.Json;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Builds the {@code "standard"} fixture of Appendix D.2 in memory.
 *
 * <p>The fixture is deliberately built around the hard cases — two homophonous
 * entries, a sense with two glosses, a sense with two examples, and an inbound
 * reference — and most cases of the conformance corpus run against it. It is read
 * from the very JSON document the specification prints, so that a change to the
 * fixture in the specification is picked up by editing one resource.</p>
 *
 * <p>Two shapes of the fixture are normative and are honoured here: a singleton
 * child is written as an object rather than as a list, and a sense for which the
 * fixture states no {@code category} still has one, whose {@code value} is
 * unset.</p>
 */
public final class StandardFixture {

    private static final String RESOURCE =
        "/fr/cnrs/lacito/liftpatchbox/fixture-standard.json";

    private StandardFixture() {
    }

    /**
     * Build a fresh dictionary holding the standard fixture.
     *
     * @return a new dictionary, independent of every other one this method returned
     */
    public static LiftDictionary build() {
        Map<String, Object> doc = Json.parseObject(read());
        LiftDictionary dictionary = LiftDictionary.makeBuilder()
            .withObjectLanguages(Json.strings(doc, "objectLanguages").toArray(String[]::new))
            .withMetaLanguages(Json.strings(doc, "metaLanguages").toArray(String[]::new))
            .build();

        for (Object raw : Json.array(doc, "entries")) {
            addEntry(dictionary, cast(raw));
        }
        return dictionary;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object o) {
        return (Map<String, Object>) o;
    }

    private static void addEntry(LiftDictionary dictionary, Map<String, Object> entry) {
        var builder = dictionary.getComponentBuilder().entry();
        String id = Json.string(entry, "id");
        if (id != null) {
            builder.withId(id);
        }
        Json.object(entry, "form").forEach((lang, text) ->
            builder.withForm(lang, String.valueOf(text)));

        for (Object raw : Json.array(entry, "senses")) {
            Map<String, Object> sense = cast(raw);
            builder.addSense(s -> fillSense(s, sense));
        }
        for (Object raw : Json.array(entry, "relations")) {
            Map<String, Object> relation = cast(raw);
            builder.addRelation(
                Json.string(relation, "type"), Json.string(relation, "target"));
        }
        builder.build();
    }

    private static void fillSense(SenseBuilder builder, Map<String, Object> sense) {
        String id = Json.string(sense, "id");
        if (id != null) {
            builder.withId(id);
        }
        Json.object(sense, "gloss").forEach((lang, text) ->
            builder.withGloss(lang, String.valueOf(text)));
        Json.object(sense, "definition").forEach((lang, text) ->
            builder.withDefinition(lang, String.valueOf(text)));

        Map<String, Object> category = Json.object(sense, "category");
        String value = Json.string(category, "value");
        if (value != null) {
            builder.withPartOfSpeech(value);
        }

        for (Object raw : Json.array(sense, "examples")) {
            Map<String, Object> example = cast(raw);
            builder.addExample(x -> {
                Json.object(example, "text").forEach((lang, text) ->
                    x.withExample(lang, String.valueOf(text)));
                for (Object rawTranslation : Json.array(example, "translations")) {
                    Map<String, Object> translation = cast(rawTranslation);
                    String type = Json.string(translation, "type");
                    Json.object(translation, "text").forEach((lang, text) ->
                        x.addTranslation(type, lang, String.valueOf(text)));
                }
            });
        }
    }

    private static String read() {
        try (InputStream in = StandardFixture.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing fixture resource " + RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the standard fixture", e);
        }
    }

    /**
     * The object languages the fixture declares, in order.
     *
     * @return the language codes
     */
    public static List<String> objectLanguages() {
        return Json.strings(Json.parseObject(read()), "objectLanguages");
    }

    /**
     * The meta languages the fixture declares, in order.
     *
     * @return the language codes
     */
    public static List<String> metaLanguages() {
        return Json.strings(Json.parseObject(read()), "metaLanguages");
    }
}
