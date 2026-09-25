package fr.cnrs.lacito.liftpatchbox;

import static org.junit.jupiter.api.Assertions.assertEquals;

import fr.cnrs.lacito.liftpatchbox.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Selection: the seven strategies of Part 1, section 5.1.3, the two axes of
 * section 5.1.1, the pseudo-properties of section 5.3, and the multiplicity
 * keywords of section 5.6.
 *
 * <p>The rule the whole of this area turns on is that the language never silently
 * chooses between several matches: a selector required to be unique that matches
 * two components is an error, and a script that wants several must say so.</p>
 */
@DisplayName("Selection")
class SelectionTest extends PatchTestSupport {

    @BeforeEach
    void setUp() {
        newDictionary();
    }

    @Nested
    @DisplayName("strategies")
    class Strategies {

        @Test
        @DisplayName("S2: a persistent identifier selects on its own")
        void selectsByIdentifier() {
            assertEffects(apply("set form = { tww: \"mamio\" } on entry[id = \"e-1\"]"),
                "replaced:form@tww:mami->mamio");
        }

        @Test
        @DisplayName("S3: the natural identity of a non-entry component type")
        void selectsByNaturalIdentity() {
            assertEffects(
                apply("""
                    set value = "Verb"
                      on category
                      of sense[gloss@en = "dog"]
                      of entry[form@tww = "memi"]
                    """),
                "set:value=Verb");
        }

        @Test
        @DisplayName("S3 refined by a has predicate on a component type that is not an entry")
        void refinesTheNaturalIdentityWithHas() {
            assertEffects(
                apply("""
                    set value = "Verb"
                      on category
                      of sense[gloss@en = "pig", has example[text@tww = "a mami jefo"]]
                      of entry[form@tww = "mami", hn = 1]
                    """),
                "replaced:value:Noun->Verb");
        }

        @Test
        @DisplayName("S4: a form alone is ambiguous over homophones and is never resolved silently")
        void refusesAnAmbiguousForm() {
            refused(ErrorCode.AMBIGUOUS_REFERENCE,
                "create sense(gloss@en = \"piglet\") under entry[form@tww = \"mami\"]");
        }

        @Test
        @DisplayName("S4 refined by hn")
        void refinesTheFormWithHn() {
            assertEffects(
                apply("create sense(gloss@en = \"piglet\") "
                    + "under entry[form@tww = \"mami\", hn = 2]"),
                "created:sense@3", "set:gloss@en=piglet");
        }

        @Test
        @DisplayName("S4 refined by has-gloss")
        void refinesTheFormWithHasGloss() {
            assertEffects(
                apply("create sense(gloss@en = \"piglet\") "
                    + "under entry[form@tww = \"mami\", has-gloss@en = \"taro\"]"),
                "created:sense@3", "set:gloss@en=piglet");
        }

        @Test
        @DisplayName("S5: an ordinal on an ordered component type")
        void selectsByOrdinal() {
            assertEffects(
                apply("""
                    set text@tpi = "mi shutim pik"
                      on example#1
                      of sense[gloss@en = "pig"]
                      of entry[form@tww = "mami", hn = 1]
                    """),
                "set:text@tpi=mi shutim pik");
        }

        @Test
        @DisplayName("S6: a type key on a typed component type")
        void selectsByTypeKey() {
            apply("create note(type = \"general\", text@en = \"recorded at Yakoro\") "
                + "under entry[form@tww = \"memi\"]");
            assertEffects(
                apply("set text@en = \"recorded at Yakoro, 2025\" "
                    + "on note^general of entry[form@tww = \"memi\"]"),
                "replaced:text@en:recorded at Yakoro->recorded at Yakoro, 2025");
        }

        @Test
        @DisplayName("S6 is a spelling of S3, and the two resolve identically")
        void theTypeKeyIsASpellingOfTheNaturalIdentity() {
            apply("create note(type = \"general\", text@en = \"first\") "
                + "under entry[form@tww = \"memi\"]");
            assertEffects(
                apply("set text@en = \"second\" on note[type = \"general\"] "
                    + "of entry[form@tww = \"memi\"]"),
                "replaced:text@en:first->second");
        }

        @Test
        @DisplayName("S7: a singleton is named by its component type alone")
        void selectsASingletonByItsName() {
            assertEffects(
                apply("""
                    set value = "Verb"
                      on category
                      of sense[gloss@en = "pig"]
                      of entry[form@tww = "mami", hn = 1]
                    """),
                "replaced:value:Noun->Verb");
        }

        @Test
        @DisplayName("refuses a selector that uses no strategy")
        void refusesAnIncompleteSelector() {
            refused(ErrorCode.INCOMPLETE_SELECTOR,
                "delete relation[type = \"synonym\"] under entry[form@tww = \"memi\"]");
        }

        @Test
        @DisplayName("refuses a selector that combines two strategies")
        void refusesTwoStrategies() {
            refused(ErrorCode.DUPLICATE_SELECTOR,
                "delete sense[id = \"s-1\", gloss@en = \"pig\"] "
                    + "under entry[form@tww = \"mami\", hn = 1]");
        }

        @Test
        @DisplayName("refuses a predicate added to a complete strategy")
        void refusesAnAddedPredicate() {
            refused(ErrorCode.PREDICATE_NOT_ALLOWED_IN_UNIQUE_SELECTOR,
                """
                set definition@fr = "Un animal"
                  on sense[gloss@en = "pig", definition@en = "A four-legged terrestrial animal"]
                  of entry[form@tww = "mami", hn = 1]
                """);
        }

        @Test
        @DisplayName("refuses hn written without a form")
        void refusesHnAlone() {
            refused(ErrorCode.HN_CANNOT_BE_USED_ALONE, "delete entry[hn = 1]");
        }

        @Test
        @DisplayName("an hn that matches nothing is NOT_FOUND, not a code of its own")
        void anUnmatchedHnIsNotFound() {
            refused(ErrorCode.NOT_FOUND, "ensure entry[form@tww = \"mami\", hn = 7]");
        }
    }

    @Nested
    @DisplayName("axes")
    class Axes {

        @Test
        @DisplayName("within resolves through an ambiguous ancestor when one path survives")
        void resolvesThroughAnAmbiguousAncestor() {
            assertEffects(
                apply("""
                    set value = "Verb"
                      on category
                      of sense[gloss@en = "taro"]
                      within entry[form@tww = "mami"]
                    """),
                "set:value=Verb");
        }

        @Test
        @DisplayName("within fails when two candidate paths survive")
        void failsWhenTwoPathsSurvive() {
            refused(ErrorCode.AMBIGUOUS_REFERENCE,
                """
                set value = "Verb"
                  on category
                  of sense[gloss@en = "pig"]
                  within entry[form@tww = "mami"]
                """);
        }

        @Test
        @DisplayName("of requires the parent selector to be unique on its own")
        void ofRequiresAUniqueParent() {
            refused(ErrorCode.AMBIGUOUS_REFERENCE,
                """
                set value = "Verb"
                  on category
                  of sense[gloss@en = "taro"]
                  of entry[form@tww = "mami"]
                """);
        }

        @Test
        @DisplayName("refuses a chain that does not reach the root")
        void refusesAnIncompleteChain() {
            refused(ErrorCode.INCOMPLETE_ANCESTOR_CHAIN,
                "set value = \"Verb\" on category of sense[gloss@en = \"pig\"]");
        }

        @Test
        @DisplayName("refuses a chain that skips a level of the hierarchy")
        void refusesASkippedLevel() {
            refused(ErrorCode.ILLEGAL_PARENT,
                """
                create note(type = "x", text@en = "y")
                  under example[text@tww = "a mami jefi"]
                  of sense[gloss@en = "pig"]
                  of entry[form@tww = "mami", hn = 1]
                """);
        }
    }

    @Nested
    @DisplayName("filtering predicates")
    class FilteringPredicates {

        @Test
        @DisplayName("a regular expression filters a marked target step")
        void filtersWithARegularExpression() {
            assertEffects(
                apply("""
                    delete all example[text@tww ~ "jefi$"]
                      under sense[gloss@en = "pig"]
                      of entry[form@tww = "mami", hn = 1]
                    """),
                "deleted:example@1");
        }

        @Test
        @DisplayName("a case-insensitive match folds case")
        void foldsCase() {
            assertEffects(
                apply("""
                    delete all example[text@tww ~i "A MAMI JEFI"]
                      under sense[gloss@en = "pig"]
                      of entry[form@tww = "mami", hn = 1]
                    """),
                "deleted:example@1");
        }

        @Test
        @DisplayName("exists and absent are exact complements")
        void existsAndAbsentAreComplements() {
            assertEffects(
                apply("""
                    clear definition@en
                      on each sense[exists(definition@en)]
                      of entry[form@tww = "mami", hn = 1]
                    """),
                "removed:definition@en=A four-legged terrestrial animal");
            assertEffects(
                apply("""
                    set definition@en = "added"
                      on each sense[absent(definition@en)]
                      of entry[form@tww = "mami", hn = 1]
                    """),
                "set:definition@en=added", "set:definition@en=added");
        }

        @Test
        @DisplayName("a singleton step inside a has predicate may carry a predicate list")
        void filtersOnASingletonInsideHas() {
            assertEffects(
                apply("""
                    set definition@fr = "Un animal"
                      on sense[gloss@en = "pig", has category[value = "Noun"]]
                      of entry[form@tww = "mami", hn = 1]
                    """),
                "set:definition@fr=Un animal");
        }

        @Test
        @DisplayName("refuses a filtering-only operator in a unique selector")
        void refusesAFilteringOperatorInAUniqueSelector() {
            refused(ErrorCode.PREDICATE_NOT_ALLOWED_IN_UNIQUE_SELECTOR,
                "delete sense[gloss@en != \"pig\"] under entry[form@tww = \"memi\"]");
        }

        @Test
        @DisplayName("refuses a pseudo-property in a filtering selector")
        void refusesAPseudoPropertyInAFilter() {
            refused(ErrorCode.PSEUDO_PROPERTY_NOT_ALLOWED_IN_FILTER,
                """
                set definition@fr = "Un animal"
                  on sense[gloss@en = "pig"]
                  within entry[form@tww = "mami", hn = 1]
                """);
        }

        @Test
        @DisplayName("refuses a regular expression on a reference property")
        void refusesARegexOnAReference() {
            refused(ErrorCode.OPERATOR_NOT_APPLICABLE_TO_DATATYPE,
                "delete all relation[target ~ entry[form@tww = \"mami\", hn = 1]] "
                    + "under entry[form@tww = \"memi\"]");
        }
    }

    @Nested
    @DisplayName("multiplicity")
    class Multiplicity {

        @Test
        @DisplayName("each applies to every matched component in document order")
        void appliesInDocumentOrder() {
            assertEffects(
                apply("""
                    set value = "Verb"
                      on each category
                      of sense[exists(gloss@en)]
                      of entry[form@tww = "mami", hn = 1]
                    """),
                "replaced:value:Noun->Verb", "replaced:value:Noun->Verb");
        }

        @Test
        @DisplayName("a parent step above a marked step keeps its own rules")
        void aParentAboveAMarkedStepKeepsItsRules() {
            refused(ErrorCode.AMBIGUOUS_REFERENCE,
                """
                delete all example[text@tww ~ "jef"]
                  under sense[gloss@en = "pig"]
                  of entry[form@tww = "mami"]
                """);
        }

        @Test
        @DisplayName("refuses a multiplicity keyword on a verb that operates on one component")
        void refusesMultiplicityOnCreate() {
            refusedConcise(ErrorCode.MULTIPLICITY_NOT_ALLOWED,
                "c* /e[f=\"memi\"] s(g=\"puppy\")");
        }
    }

    @Nested
    @DisplayName("references")
    class References {

        @Test
        @DisplayName("a reference property is compared to a chain in a selector")
        void comparesAReferenceToAChain() {
            assertEffects(
                apply("""
                    set type = "see-also"
                      on relation[type = "synonym", target = entry[form@tww = "mami", hn = 1]]
                      of entry[form@tww = "memi"]
                    """),
                "replaced:type:synonym->see-also");
        }

        @Test
        @DisplayName("refuses a bare string as a reference value")
        void refusesABareString() {
            refused(ErrorCode.REFERENCE_VALUE_MUST_BE_A_CHAIN,
                "set type = \"see-also\" on relation[type = \"synonym\", target = \"e-1\"] "
                    + "of entry[form@tww = \"memi\"]");
        }

        @Test
        @DisplayName("refuses a reference target that is neither an entry nor a sense")
        void refusesAnInvalidTarget() {
            refused(ErrorCode.INVALID_TARGET,
                """
                set target = example[text@tww = "a mami jefi"] of sense[gloss@en = "pig"]
                  of entry[form@tww = "mami", hn = 1]
                  on relation#1
                  of entry[form@tww = "memi"]
                """);
        }

        @Test
        @DisplayName("rewrites a reference by naming the component it should point at")
        void rewritesAReference() {
            assertEffects(
                apply("""
                    set target = entry[form@tww = "mami", hn = 2]
                      on relation#1
                      of entry[form@tww = "memi"]
                    """),
                "replaced:target:e-1->e-2");
            assertEquals(3, dictionary.getLiftDictionaryRegistry().getEntries().size());
        }
    }
}
