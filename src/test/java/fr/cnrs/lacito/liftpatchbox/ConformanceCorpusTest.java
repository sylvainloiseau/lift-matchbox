package fr.cnrs.lacito.liftpatchbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftpatchbox.ast.Syntax;
import fr.cnrs.lacito.liftpatchbox.engine.Effect;
import fr.cnrs.lacito.liftpatchbox.engine.Plan;
import fr.cnrs.lacito.liftpatchbox.error.ErrorCode;
import fr.cnrs.lacito.liftpatchbox.error.ErrorKind;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchError;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchException;
import fr.cnrs.lacito.liftpatchbox.metamodel.Json;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs the conformance corpus of Appendix D.
 *
 * <p>The corpus is the part of the specification that can be run, and it is
 * therefore the part that decides, in practice, what the language is. Each case
 * gives a dictionary, a script and an expected outcome, and an implementation
 * claims conformance to LiftPatch 1.0 by passing every one of them.</p>
 *
 * <p>The comparison is exactly the one Appendix D.1 defines: for an {@code ok}
 * case, the concatenated effects restricted to the ten comparable fields, plus
 * the warnings and the dangling references when the case states them; for an
 * {@code error} case, the code, its kind and the operation it was raised on, and
 * the dictionary unchanged afterwards. Messages and paths are never compared.</p>
 */
class ConformanceCorpusTest {

    private static final String RESOURCE = "/fr/cnrs/lacito/liftpatchbox/corpus.json";

    /**
     * One dynamic test per case, so that a failure names the case that failed.
     *
     * @return the tests, in corpus order
     */
    @TestFactory
    Stream<DynamicTest> conformanceCorpus() {
        List<Object> cases = Json.parseArray(read());
        return cases.stream().map(raw -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> testCase = (Map<String, Object>) raw;
            String id = Json.string(testCase, "id");
            String description = Json.string(testCase, "description");
            return DynamicTest.dynamicTest(id + " — " + description, () -> run(testCase));
        });
    }

    private void run(Map<String, Object> testCase) {
        String id = Json.string(testCase, "id");
        String script = Json.string(testCase, "script");
        Syntax syntax = "LiftPatchShort".equals(Json.string(testCase, "syntax"))
            ? Syntax.CONCISE
            : Syntax.REFERENCE;
        Map<String, Object> expect = Json.object(testCase, "expect");

        LiftDictionary dictionary = StandardFixture.build();
        LiftPatchBox box = new LiftPatchBox()
            .withDefaultObjectLanguage("tww")
            .withDefaultMetaLanguage("en");

        if ("error".equals(Json.string(expect, "status"))) {
            runErrorCase(id, box, dictionary, script, syntax, expect);
            return;
        }
        runOkCase(id, box, dictionary, script, syntax, expect);
    }

    private void runErrorCase(
        String id, LiftPatchBox box, LiftDictionary dictionary,
        String script, Syntax syntax, Map<String, Object> expect
    ) {
        ErrorCode expected = ErrorCode.valueOf(Json.string(expect, "code"));
        ErrorKind expectedKind = "static".equals(Json.string(expect, "kind"))
            ? ErrorKind.STATIC : ErrorKind.DYNAMIC;
        int expectedIndex = Json.integer(expect, "commandIndex", 0);
        int entriesBefore = dictionary.getLiftDictionaryRegistry().getEntries().size();

        LiftPatchError actual;
        try {
            Plan plan = box.apply(dictionary, script, syntax, id);
            actual = plan.error();
            if (actual == null) {
                fail(id + ": expected " + expected + " but the script applied cleanly");
                return;
            }
        } catch (LiftPatchException e) {
            actual = e.firstError();
        }

        assertEquals(expected, actual.code(), id + ": wrong error code (" + actual.message() + ")");
        assertEquals(expectedKind, actual.kind(), id + ": wrong error kind");
        if (expectedIndex > 0) {
            assertEquals(expectedIndex, actual.operationIndex(), id + ": wrong operation index");
        }
        // The dictionary must be unchanged: a failed script leaves it exactly as it was.
        assertEquals(entriesBefore, dictionary.getLiftDictionaryRegistry().getEntries().size(),
            id + ": the dictionary was left changed by a failing script");
    }

    private void runOkCase(
        String id, LiftPatchBox box, LiftDictionary dictionary,
        String script, Syntax syntax, Map<String, Object> expect
    ) {
        Plan plan;
        try {
            plan = box.apply(dictionary, script, syntax, id);
        } catch (LiftPatchException e) {
            fail(id + ": expected success but got " + e.firstError().format());
            return;
        }

        assertEquals(render(expectedEffects(expect)), render(plan.allEffects()),
            id + ": effects differ");

        List<String> expectedWarnings = new ArrayList<>();
        for (Object raw : Json.array(expect, "warnings")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> warning = (Map<String, Object>) raw;
            expectedWarnings.add(Json.string(warning, "code"));
        }
        assertEquals(expectedWarnings,
            plan.warnings().stream().map(w -> w.code().name()).toList(),
            id + ": warnings differ");

        List<String> expectedDangling = new ArrayList<>();
        for (Object raw : Json.array(expect, "danglingReferences")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dangling = (Map<String, Object>) raw;
            expectedDangling.add(Json.string(dangling, "property")
                + "=" + Json.string(dangling, "value"));
        }
        assertEquals(expectedDangling,
            plan.danglingReferences().stream()
                .map(d -> d.property() + "=" + d.value()).toList(),
            id + ": dangling references differ");

        String rerun = Json.string(expect, "rerun");
        if ("noEffect".equals(rerun)) {
            Plan second = box.apply(dictionary, script, syntax, id + " (rerun)");
            assertEquals(List.of(), render(second.allEffects()),
                id + ": the second run was expected to change nothing");
        }
    }

    /**
     * The comparable fields of an effect, as Appendix D.1 lists them exhaustively.
     * The remaining fields are informative and are never compared.
     */
    private static List<String> render(List<Effect> effects) {
        List<String> out = new ArrayList<>(effects.size());
        for (Effect e : effects) {
            out.add(e.kind() + "|" + n(e.componentType()) + "|" + n(e.property()) + "|"
                + n(e.language()) + "|" + n(e.languageKind()) + "|" + n(e.oldValue()) + "|"
                + n(e.newValue()) + "|" + n(e.position()) + "|" + n(e.fromPosition()) + "|"
                + n(e.toPosition()));
        }
        return out;
    }

    private static List<Effect> expectedEffects(Map<String, Object> expect) {
        List<Effect> out = new ArrayList<>();
        for (Object raw : Json.array(expect, "effects")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> e = (Map<String, Object>) raw;
            out.add(new Effect(
                Effect.Kind.valueOf(Json.string(e, "kind")),
                Json.string(e, "componentType"),
                Json.string(e, "property"),
                Json.string(e, "language"),
                Json.string(e, "languageKind"),
                Json.string(e, "oldValue"),
                Json.string(e, "newValue"),
                boxed(e, "position"),
                boxed(e, "fromPosition"),
                boxed(e, "toPosition"),
                null, null));
        }
        return out;
    }

    private static Integer boxed(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number n ? n.intValue() : null;
    }

    private static String n(Object o) {
        return o == null ? "-" : o.toString();
    }

    private static String read() {
        try (InputStream in = ConformanceCorpusTest.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing corpus resource " + RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the conformance corpus", e);
        }
    }
}
