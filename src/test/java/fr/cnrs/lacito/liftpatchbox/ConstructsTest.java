package fr.cnrs.lacito.liftpatchbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.liftpatchbox.engine.Plan;
import fr.cnrs.lacito.liftpatchbox.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The constructs that organize a script rather than change one component:
 * blocks, labels, language scoping, embedded initializers, atomicity and plan
 * mode (Part 1, sections 2.1 and 6.3; Part 2, sections 11 and 12).
 */
@DisplayName("Constructs")
class ConstructsTest extends PatchTestSupport {

    @BeforeEach
    void setUp() {
        newDictionary();
    }

    @Nested
    @DisplayName("blocks")
    class Blocks {

        @Test
        @DisplayName("a header supplies the parent of every command in the body")
        void aHeaderSuppliesTheParent() {
            assertEffects(
                apply("""
                    entry[form@tww = "memi"] {
                      create sense(gloss@en = "puppy")
                      create note(type = "general", text@en = "checked")
                    }
                    """),
                "created:sense@2", "set:gloss@en=puppy",
                "created:note@null", "set:type=general", "set:text@en=checked");
        }

        @Test
        @DisplayName("blocks nest, each supplying its own parent")
        void blocksNest() {
            assertEffects(
                apply("""
                    entry[form@tww = "memi"] {
                      create sense(gloss@en = "puppy") {
                        set definition@en = "A young dog"
                        set value = "Noun" on category
                      }
                    }
                    """),
                "created:sense@2", "set:gloss@en=puppy",
                "set:definition@en=A young dog", "set:value=Noun");
        }

        @Test
        @DisplayName("a header is an operation and takes the index preceding its body")
        void aHeaderIsAnOperation() {
            Plan plan = apply("""
                entry[form@tww = "memi"] {
                  create sense(gloss@en = "puppy")
                }
                """);
            assertEquals(1, plan.operations().get(0).index());
            assertEquals("select", plan.operations().get(0).verb());
            assertEquals(2, plan.operations().get(1).index());
        }

        @Test
        @DisplayName("a header label is visible after the closing brace")
        void aHeaderLabelOutlivesTheBlock() {
            assertEffects(
                apply("""
                    entry[form@tww = "memi"] as $memi {
                      create sense(gloss@en = "puppy")
                    }
                    create note(type = "general", text@en = "checked") under $memi
                    """),
                "created:sense@2", "set:gloss@en=puppy",
                "created:note@null", "set:type=general", "set:text@en=checked");
        }

        @Test
        @DisplayName("refuses an upsert in the scope of a create")
        void refusesAnUpsertInACreationBlock() {
            refused(ErrorCode.CANNOT_UPSERT_ENSURE_OR_UPDATE_IN_CREATION_BLOCK,
                """
                create entry(form@tww = "mimi") {
                  upsert sense(gloss@en = "lizard")
                }
                """);
        }

        @Test
        @DisplayName("refuses a create entry in a block body, an entry having no parent")
        void refusesACreateEntryInABlockBody() {
            refused(ErrorCode.ILLEGAL_PARENT,
                """
                entry[form@tww = "memi"] {
                  create entry(form@tww = "mimi")
                }
                """);
        }
    }

    @Nested
    @DisplayName("labels")
    class Labels {

        @Test
        @DisplayName("ensure binds a label a later command uses as a step")
        void ensureBindsALabel() {
            assertEffects(
                apply("""
                    ensure entry[form@tww = "memi"] as $memi
                    create sense(gloss@en = "puppy") under $memi
                    """),
                "created:sense@2", "set:gloss@en=puppy");
        }

        @Test
        @DisplayName("a label names a component the script has just created")
        void aLabelNamesAFreshComponent() {
            // Without a label the new entry cannot be referred to at all: its `id` is
            // system-generated and the script does not know it, and its form is a
            // homophone of nothing but is still not a selector the script chose
            // (Part 1, section 6.3).
            Plan plan = apply("""
                create entry(form@tww = "mimi") as $new
                create relation(type = "synonym", target = $new)
                  under entry[form@tww = "memi"]
                """);
            assertEquals(2, plan.summary().componentsCreated());
            String target = plan.allEffects().stream()
                .filter(e -> "target".equals(e.property()))
                .map(fr.cnrs.lacito.liftpatchbox.engine.Effect::newValue)
                .findFirst().orElseThrow();
            assertEquals(entry("tww", "mimi").getId().orElseThrow(), target);
        }

        @Test
        @DisplayName("refuses an unbound label")
        void refusesAnUnboundLabel() {
            refused(ErrorCode.UNKNOWN_LABEL,
                "create sense(gloss@en = \"puppy\") under $nowhere");
        }

        @Test
        @DisplayName("refuses re-binding a visible name")
        void refusesADuplicateLabel() {
            refused(ErrorCode.DUPLICATE_LABEL,
                """
                ensure entry[form@tww = "memi"] as $e
                ensure entry[form@tww = "memi"] as $e
                """);
        }
    }

    @Nested
    @DisplayName("language scoping")
    class LanguageScoping {

        @Test
        @DisplayName("language-default changes which language an unqualified assignment writes")
        void languageDefaultChangesTheLanguage() {
            assertEffects(
                apply("""
                    language-default meta = "fr"
                    create sense(gloss = "chiot") under entry[form@tww = "memi"]
                    """),
                "created:sense@2", "set:gloss@fr=chiot");
        }

        @Test
        @DisplayName("a with header binds defaults for its body and is not an operation")
        void aWithHeaderBindsForItsBody() {
            Plan plan = apply("""
                with object = "tpi", meta = "fr" {
                  create entry(form = "pikpik") as $m
                  create sense(gloss = "lezard") under $m
                }
                """);
            assertEffects(plan,
                "created:entry@null", "set:form@tpi=pikpik",
                "created:sense@1", "set:gloss@fr=lezard");
            assertEquals(1, plan.operations().get(0).index());
        }

        @Test
        @DisplayName("a with binding ends at the closing brace")
        void aWithBindingEndsAtTheBrace() {
            assertEffects(
                apply("""
                    with meta = "fr" {
                      create sense(gloss = "chiot") under entry[form@tww = "memi"]
                    }
                    create sense(gloss = "puppy") under entry[form@tww = "memi"]
                    """),
                "created:sense@2", "set:gloss@fr=chiot",
                "created:sense@3", "set:gloss@en=puppy");
        }

        @Test
        @DisplayName("language-create adds a language and is an operation")
        void languageCreateIsAnOperation() {
            assertEffects(
                apply("""
                    language-create object = "hui"
                    create entry(form@hui = "mimi")
                    """),
                "language:object:hui", "created:entry@null", "set:form@hui=mimi");
            assertTrue(dictionary.getObjectLanguageManager().hasLanguage("hui"));
        }

        @Test
        @DisplayName("refuses a language the dictionary already has")
        void refusesAnExistingLanguage() {
            refused(ErrorCode.LANGUAGE_ALREADY_EXISTS, "language-create object = \"tww\"");
        }

        @Test
        @DisplayName("refuses a default that names a language the dictionary does not have")
        void refusesAnUnknownDefault() {
            refused(ErrorCode.NO_SUCH_META_LANGUAGE, "language-default meta = \"de\"");
        }
    }

    @Nested
    @DisplayName("embedded initializers")
    class EmbeddedInitializers {

        @Test
        @DisplayName("create the children the outer command's initializer list names")
        void createTheChildren() {
            assertEffects(
                applyConcise(
                    "c e(\"mimi\", s(\"lizard\", x(\"a mimi jefi\", o(\"literal\", \"I saw it\"))))"),
                "created:entry@null", "set:form@tww=mimi",
                "created:sense@1", "set:gloss@en=lizard",
                "created:example@1", "set:text@tww=a mimi jefi",
                "created:translation@null", "set:type=literal", "set:text@en=I saw it");
        }
    }

    @Nested
    @DisplayName("atomicity")
    class Atomicity {

        @Test
        @DisplayName("a failing second command undoes the first")
        void aFailingCommandUndoesTheFirst() {
            refused(ErrorCode.AMBIGUOUS_REFERENCE,
                """
                create sense(gloss@en = "puppy") under entry[form@tww = "memi"]
                create sense(gloss@en = "piglet") under entry[form@tww = "mami"]
                """);
            assertEquals(1, entry("tww", "memi").getSenses().size());
        }

        @Test
        @DisplayName("a language created by a failing script is not created")
        void aLanguageIsRolledBackToo() {
            refused(ErrorCode.AMBIGUOUS_REFERENCE,
                """
                language-create object = "hui"
                create entry(form@hui = "mami")
                create sense(gloss@en = "piglet") under entry[form@tww = "mami"]
                """);
            assertFalse(dictionary.getObjectLanguageManager().hasLanguage("hui"));
        }

        @Test
        @DisplayName("a script with a static error changes nothing at all")
        void aStaticErrorChangesNothing() {
            refused(ErrorCode.MISSING_PARENT_CLAUSE,
                """
                create sense(gloss@en = "puppy") under entry[form@tww = "memi"]
                create sense(gloss@en = "piglet")
                """);
            assertEquals(1, entry("tww", "memi").getSenses().size());
        }

        @Test
        @DisplayName("a failing command inside a block rolls the block back")
        void aBlockRollsBack() {
            refused(ErrorCode.CANNOT_CREATE_DUPLICATE,
                """
                entry[form@tww = "memi"] {
                  create sense(gloss@en = "puppy")
                  create sense(gloss@en = "puppy")
                }
                """);
            assertEquals(1, entry("tww", "memi").getSenses().size());
        }
    }

    @Nested
    @DisplayName("plan mode")
    class PlanMode {

        @Test
        @DisplayName("reports the effects and changes nothing")
        void reportsTheEffectsAndChangesNothing() {
            Plan plan = plan("create sense(gloss@en = \"puppy\") under entry[form@tww = \"memi\"]");
            assertEffects(plan, "created:sense@2", "set:gloss@en=puppy");
            assertEquals(1, entry("tww", "memi").getSenses().size());
        }

        @Test
        @DisplayName("reports an error without applying the commands before it")
        void reportsAnErrorWithoutApplying() {
            Plan plan = plan("""
                create sense(gloss@en = "puppy") under entry[form@tww = "memi"]
                create sense(gloss@en = "piglet") under entry[form@tww = "mami"]
                """);
            assertFalse(plan.isOk());
            assertEquals(ErrorCode.AMBIGUOUS_REFERENCE, plan.error().code());
            assertEquals(1, entry("tww", "memi").getSenses().size());
        }

        @Test
        @DisplayName("emits a plan document whose summary counts what the script would do")
        void emitsAPlanDocument() {
            Plan plan = plan("create sense(gloss@en = \"puppy\") under entry[form@tww = \"memi\"]");
            String json = plan.toJson();
            assertTrue(json.contains("\"liftpatchPlan\": \"1.0\""), json);
            assertTrue(json.contains("\"status\": \"ok\""), json);
            assertTrue(json.contains("\"componentsCreated\": 1"), json);
            assertEquals(1, plan.summary().componentsCreated());
        }

        @Test
        @DisplayName("warns when no line of a document was recognized as a command")
        void warnsWhenNothingWasRecognized() {
            Plan plan = box.plan(dictionary,
                "create sense(gloss@en = \"puppy\") under entry[form@tww = \"memi\"]",
                fr.cnrs.lacito.liftpatchbox.ast.Syntax.CONCISE, "<test>");
            assertTrue(plan.isOk());
            assertEffects(plan);
            assertEquals("NO_COMMAND_RECOGNIZED", plan.warnings().get(0).code().name());
        }
    }
}
