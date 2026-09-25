package fr.cnrs.lacito.liftpatchbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.liftapi.model.LiftSense;
import fr.cnrs.lacito.liftpatchbox.error.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The five component commands: {@code create}, {@code upsert}, {@code ensure},
 * {@code delete} and {@code move} (Part 2, sections 8.1 to 8.5).
 *
 * <p>The tests are grouped by verb, and each group covers what Part 2, section
 * 7.1 calls the verb's preconditions and postconditions: what it does when the
 * component is absent, what it does when it is present, and which of the three
 * component kinds it accepts.</p>
 */
@DisplayName("Component commands")
class ComponentCommandsTest extends PatchTestSupport {

    @BeforeEach
    void setUp() {
        newDictionary();
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("creates a component under an unambiguous parent")
        void createsUnderAnUnambiguousParent() {
            assertEffects(
                apply("create sense(gloss@en = \"puppy\") under entry[form@tww = \"memi\"]"),
                "created:sense@2", "set:gloss@en=puppy");
            assertEquals(2, entry("tww", "memi").getSenses().size());
        }

        @Test
        @DisplayName("refuses a duplicate identity key among same-type siblings")
        void refusesADuplicateIdentityKey() {
            refused(ErrorCode.CANNOT_CREATE_DUPLICATE,
                "create sense(gloss@en = \"dog\") under entry[form@tww = \"memi\"]");
        }

        @Test
        @DisplayName("creates a homophone, entry having an empty natural identity")
        void createsAHomophone() {
            assertEffects(apply("create entry(form@tww = \"mami\")"),
                "created:entry@null", "set:form@tww=mami");
            assertEquals(3, entries("tww", "mami").size());
        }

        @Test
        @DisplayName("writes one effect per initializer, in the order written")
        void writesOneEffectPerInitializer() {
            assertEffects(
                apply("create note(type = \"general\", text@en = \"checked\") "
                    + "under entry[form@tww = \"memi\"]"),
                "created:note@null", "set:type=general", "set:text@en=checked");
        }

        @Test
        @DisplayName("places the component where the at clause says")
        void placesTheComponentWhereTheAtClauseSays() {
            assertEffects(
                apply("create sense(gloss@en = \"puppy\") under entry[form@tww = \"memi\"] "
                    + "at beginning"),
                "created:sense@1", "set:gloss@en=puppy");
            assertEquals("puppy",
                entry("tww", "memi").getSenses().get(0).getGlosses().getForm("en")
                    .orElseThrow().toPlainText());
        }

        @Test
        @DisplayName("places the component after a named sibling")
        void placesTheComponentAfterANamedSibling() {
            assertEffects(
                apply("""
                    create example(text@tww = "a mami jefu")
                      under sense[gloss@en = "pig"]
                      of entry[form@tww = "mami", hn = 1]
                      at after example[text@tww = "a mami jefi"]
                    """),
                "created:example@2", "set:text@tww=a mami jefu");
        }

        @Test
        @DisplayName("refuses a singleton, which comes into existence with its host")
        void refusesASingleton() {
            refused(ErrorCode.SINGLETON_CANNOT_BE_CREATED_OR_DELETED,
                "create category(value = \"Noun\") under sense[gloss@en = \"dog\"] "
                    + "of entry[form@tww = \"memi\"]");
        }

        @Test
        @DisplayName("refuses an at clause on a typed component, which has no position")
        void refusesAnAtClauseOnATypedComponent() {
            refused(ErrorCode.COMPONENT_NOT_ORDERED,
                "create note(type = \"general\", text@en = \"x\") "
                    + "under entry[form@tww = \"memi\"] at beginning");
        }

        @Test
        @DisplayName("refuses a missing required property")
        void refusesAMissingRequiredProperty() {
            refused(ErrorCode.MISSING_REQUIRED_PROPERTY,
                "create note(type = \"general\") under entry[form@tww = \"memi\"]");
        }
    }

    @Nested
    @DisplayName("upsert")
    class Upsert {

        @Test
        @DisplayName("select branch: the component exists, only the assignments apply")
        void selectBranchAppliesOnlyTheAssignments() {
            assertEffects(
                apply("upsert sense(gloss@en = \"taro\", definition@en = \"An edible root\") "
                    + "under entry[form@tww = \"mami\", hn = 2]"),
                "set:definition@en=An edible root");
        }

        @Test
        @DisplayName("create branch: the component does not exist")
        void createBranchCreatesTheComponent() {
            assertEffects(
                apply("upsert sense(gloss@en = \"yam\", definition@en = \"An edible root\") "
                    + "under entry[form@tww = \"mami\", hn = 2]"),
                "created:sense@3", "set:gloss@en=yam", "set:definition@en=An edible root");
        }

        @Test
        @DisplayName("is idempotent: a second run changes nothing")
        void isIdempotent() {
            apply("upsert sense(gloss@en = \"taro\") under entry[form@tww = \"mami\", hn = 2]");
            assertEffects(
                apply("upsert sense(gloss@en = \"taro\") under entry[form@tww = \"mami\", hn = 2]"));
        }

        @Test
        @DisplayName("refuses an entry with nothing to tell the homophones apart")
        void refusesAnEntryWithoutDisambiguation() {
            refused(ErrorCode.UPSERT_ENTRY_WITHOUT_DISAMBIGUATION,
                "upsert entry(form@tww = \"mami\")");
        }

        @Test
        @DisplayName("takes the select branch on an entry a has-gloss predicate finds")
        void selectsAnEntryByItsGloss() {
            assertEffects(apply("upsert entry(form@tww = \"mami\", has-gloss@en = \"taro\")"));
            assertEquals(2, entries("tww", "mami").size());
        }

        @Test
        @DisplayName("takes the create branch on an entry no has-gloss predicate finds")
        void createsAnEntryNoPredicateFinds() {
            assertEffects(apply("upsert entry(form@tww = \"mami\", has-gloss@en = \"yam\")"),
                "created:entry@null", "set:form@tww=mami");
            assertEquals(3, entries("tww", "mami").size());
        }

        @Test
        @DisplayName("refuses an initializer list that does not cover the natural identity")
        void refusesAnIncompleteIdentity() {
            refused(ErrorCode.MISSING_IDENTITY_PROPERTY,
                "upsert relation(type = \"synonym\") under entry[form@tww = \"memi\"]");
        }

        @Test
        @DisplayName("refuses an id, which a component that does not exist yet has none of")
        void refusesAnId() {
            refused(ErrorCode.ID_NOT_ALLOWED_ON_UPSERT,
                "upsert sense(gloss@en = \"taro\", id = \"s-3\") "
                    + "under entry[form@tww = \"mami\", hn = 2]");
        }
    }

    @Nested
    @DisplayName("ensure")
    class Ensure {

        @Test
        @DisplayName("succeeds and changes nothing when the component exists")
        void succeedsAndChangesNothing() {
            assertEffects(
                apply("ensure sense[gloss@en = \"taro\"] under entry[form@tww = \"mami\", hn = 2]"));
        }

        @Test
        @DisplayName("fails when the component is absent")
        void failsWhenTheComponentIsAbsent() {
            refused(ErrorCode.NOT_FOUND,
                "ensure sense[gloss@en = \"yam\"] under entry[form@tww = \"mami\", hn = 2]");
        }

        @Test
        @DisplayName("is the one component command a singleton accepts")
        void acceptsASingleton() {
            assertEffects(apply("ensure category under sense[gloss@en = \"dog\"] "
                + "of entry[form@tww = \"memi\"]"));
        }

        @Test
        @DisplayName("refuses parentheses, which create")
        void refusesParentheses() {
            refused(ErrorCode.SYNTAX_ERROR,
                "ensure sense(gloss@en = \"taro\") under entry[form@tww = \"mami\", hn = 2]");
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("removes a component with its descendants")
        void removesAComponentWithItsDescendants() {
            assertEffects(
                apply("delete sense[gloss@en = \"dog\"] under entry[form@tww = \"memi\"]"),
                "deleted:sense@1");
            assertTrue(entry("tww", "memi").getSenses().isEmpty());
        }

        @Test
        @DisplayName("removes an entry, which needs no parent clause")
        void removesAnEntry() {
            assertEffects(apply("delete entry[form@tww = \"memi\"]"), "deleted:entry@null");
            assertTrue(entries("tww", "memi").isEmpty());
        }

        @Test
        @DisplayName("reports the positions of the original list, not of the shrinking one")
        void reportsThePositionsOfTheOriginalList() {
            assertEffects(
                apply("""
                    delete all example[text@tww ~ "jef"]
                      under sense[gloss@en = "pig"]
                      of entry[form@tww = "mami", hn = 1]
                    """),
                "deleted:example@1", "deleted:example@2");
        }

        @Test
        @DisplayName("marked, matching nothing, succeeds and warns")
        void markedMatchingNothingSucceedsAndWarns() {
            var plan = apply("""
                delete all example[text@tww ~ "^draft"]
                  under sense[gloss@en = "pork"]
                  of entry[form@tww = "mami", hn = 1]
                """);
            assertEffects(plan);
            assertEquals(1, plan.warnings().size());
            assertEquals("COMMAND_APPLIES_TO_NO_COMPONENT", plan.warnings().get(0).code().name());
        }

        @Test
        @DisplayName("refuses a singleton, which disappears only with its host")
        void refusesASingleton() {
            refused(ErrorCode.SINGLETON_CANNOT_BE_CREATED_OR_DELETED,
                "delete category under sense[gloss@en = \"pig\"] "
                    + "of entry[form@tww = \"mami\", hn = 1]");
        }

        @Test
        @DisplayName("deletes a referenced component and reports the dangling reference")
        void deletesAReferencedComponent() {
            var plan = apply("delete entry[form@tww = \"mami\", hn = 1]");
            assertEffects(plan, "deleted:entry@null");
            assertEquals(1, plan.danglingReferences().size());
            assertEquals("target", plan.danglingReferences().get(0).property());
            assertEquals("e-1", plan.danglingReferences().get(0).value());
        }
    }

    @Nested
    @DisplayName("move")
    class Move {

        @Test
        @DisplayName("changes the position of a component among its siblings")
        void changesThePosition() {
            assertEffects(
                apply("""
                    move example#1
                      under sense[gloss@en = "pig"]
                      of entry[form@tww = "mami", hn = 1]
                      at end
                    """),
                "moved:example:1->2");
        }

        @Test
        @DisplayName("changes the parent of a component")
        void changesTheParent() {
            assertEffects(
                apply("""
                    move example#1
                      under sense[gloss@en = "pig"] of entry[form@tww = "mami", hn = 1]
                      under sense[gloss@en = "pork"] of entry[form@tww = "mami", hn = 1]
                      at end
                    """),
                "moved:example:1->1");
            List<LiftSense> pork = entries("tww", "mami").stream()
                .flatMap(e -> senses(e, "en", "pork").stream())
                .toList();
            assertEquals(1, pork.size());
            assertEquals(1, pork.get(0).getExamples().size());
        }

        @Test
        @DisplayName("refuses the position the component already occupies")
        void refusesTheCurrentPosition() {
            refused(ErrorCode.MOVING_TO_CURRENT_POSITION,
                """
                move example#1
                  under sense[gloss@en = "pig"]
                  of entry[form@tww = "mami", hn = 1]
                  at beginning
                """);
        }

        @Test
        @DisplayName("refuses to make a component its own ancestor")
        void refusesSelfAncestry() {
            refused(ErrorCode.SELF_ANCESTOR,
                """
                move sense[gloss@en = "pig"]
                  under entry[form@tww = "mami", hn = 1]
                  under sense[gloss@en = "pig"] of entry[form@tww = "mami", hn = 1]
                  at end
                """);
        }

        @Test
        @DisplayName("refuses a typed component, which has no position to change")
        void refusesATypedComponent() {
            refused(ErrorCode.COMPONENT_NOT_ORDERED,
                """
                move translation[type = "free"]
                  under example#1 of sense[gloss@en = "pig"] of entry[form@tww = "mami", hn = 1]
                  at beginning
                """);
        }

        @Test
        @DisplayName("refuses to appear in a block body")
        void refusesToAppearInABlockBody() {
            refused(ErrorCode.MOVE_NOT_ALLOWED_IN_BLOCK,
                """
                sense[gloss@en = "pig"] of entry[form@tww = "mami", hn = 1] {
                  move example#1 at end
                }
                """);
        }
    }
}
