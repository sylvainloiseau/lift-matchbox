package fr.cnrs.lacito.liftpatchbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.liftpatchbox.error.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The three property commands: {@code set}, {@code update} and {@code clear}
 * (Part 2, section 9), together with the language-qualifier rules of Part 1,
 * section 5.5 and the decision table of section 9.3.1.
 */
@DisplayName("Property commands")
class PropertyCommandsTest extends PatchTestSupport {

    @BeforeEach
    void setUp() {
        newDictionary();
    }

    @Nested
    @DisplayName("set")
    class Set {

        @Test
        @DisplayName("creates the value when the property is unset")
        void createsTheValue() {
            assertEffects(
                apply("""
                    set definition@fr = "Un animal"
                      on sense[gloss@en = "pig"]
                      of entry[form@tww = "mami", hn = 1]
                    """),
                "set:definition@fr=Un animal");
        }

        @Test
        @DisplayName("replaces the value when the property is set")
        void replacesTheValue() {
            assertEffects(
                apply("""
                    set definition@en = "A pig"
                      on sense[gloss@en = "pig"]
                      of entry[form@tww = "mami", hn = 1]
                    """),
                "replaced:definition@en:A four-legged terrestrial animal->A pig");
        }

        @Test
        @DisplayName("uses the default meta language when no qualifier is written")
        void usesTheDefaultLanguage() {
            assertEffects(
                apply("""
                    set definition = "A pig"
                      on sense[gloss@en = "pig"]
                      of entry[form@tww = "mami", hn = 1]
                    """),
                "replaced:definition@en:A four-legged terrestrial animal->A pig");
        }

        @Test
        @DisplayName("writes a singleton's property, which needs no selector")
        void writesASingletonProperty() {
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
        @DisplayName("writes a singleton's property on a host that has none yet")
        void writesASingletonPropertyOnAHostThatHasNone() {
            assertEffects(
                apply("""
                    set value = "Noun"
                      on category
                      of sense[gloss@en = "taro"]
                      of entry[form@tww = "mami", hn = 2]
                    """),
                "set:value=Noun");
        }

        @Test
        @DisplayName("applies an assignment list as the sequence of its assignments")
        void appliesAnAssignmentList() {
            assertEffects(
                apply("""
                    set definition@en = "A pig", definition@fr = "Un animal"
                      on sense[gloss@en = "pig"]
                      of entry[form@tww = "mami", hn = 1]
                    """),
                "replaced:definition@en:A four-legged terrestrial animal->A pig",
                "set:definition@fr=Un animal");
        }

        @Test
        @DisplayName("expands a multi-language literal into its qualified assignments")
        void expandsAMultiLanguageLiteral() {
            assertEffects(
                apply("""
                    set definition = { en: "A pig", fr: "Un cochon" }
                      on sense[gloss@en = "pig"]
                      of entry[form@tww = "mami", hn = 1]
                    """),
                "replaced:definition@en:A four-legged terrestrial animal->A pig",
                "set:definition@fr=Un cochon");
        }

        @Test
        @DisplayName("applies to every matched component when marked each")
        void appliesToEveryMatchedComponent() {
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
        @DisplayName("refuses the wildcard, which assigns no single value")
        void refusesTheWildcard() {
            refused(ErrorCode.WILDCARD_NOT_ALLOWED,
                """
                set definition@* = "A pig"
                  on sense[gloss@en = "pig"]
                  of entry[form@tww = "mami", hn = 1]
                """);
        }

        @Test
        @DisplayName("refuses a language the dictionary does not declare")
        void refusesAnUnknownLanguage() {
            refused(ErrorCode.NO_SUCH_META_LANGUAGE,
                """
                set definition@de = "Ein Schwein"
                  on sense[gloss@en = "pig"]
                  of entry[form@tww = "mami", hn = 1]
                """);
        }

        @Test
        @DisplayName("checks the uniqueness invariant when it writes an identity property")
        void checksTheUniquenessInvariant() {
            refused(ErrorCode.CANNOT_CREATE_DUPLICATE,
                """
                set gloss@en = "pork"
                  on sense[gloss@en = "pig"]
                  of entry[form@tww = "mami", hn = 1]
                """);
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("replaces a value that is set")
        void replacesAValueThatIsSet() {
            assertEffects(
                apply("""
                    update definition@en = "A revised animal"
                      on sense[gloss@en = "pig"]
                      of entry[form@tww = "mami", hn = 1]
                    """),
                "replaced:definition@en:A four-legged terrestrial animal->A revised animal");
        }

        @Test
        @DisplayName("tests the qualified value, not the property as a whole")
        void testsTheQualifiedValue() {
            refused(ErrorCode.UNSET_QUALIFIED_PROPERTY,
                """
                update definition@fr = "Un animal"
                  on sense[gloss@en = "pig"]
                  of entry[form@tww = "mami", hn = 1]
                """);
        }

        @Test
        @DisplayName("refuses a scalar property that has no value")
        void refusesAnUnsetScalar() {
            refused(ErrorCode.UNSET_PROPERTY,
                """
                update value = "Noun"
                  on category
                  of sense[gloss@en = "taro"]
                  of entry[form@tww = "mami", hn = 2]
                """);
        }
    }

    @Nested
    @DisplayName("clear")
    class Clear {

        @Test
        @DisplayName("removes one qualified value when another remains")
        void removesOneQualifiedValue() {
            assertEffects(
                apply("""
                    clear gloss@fr
                      on sense[gloss@en = "pig"]
                      of entry[form@tww = "mami", hn = 1]
                    """),
                "removed:gloss@fr=cochon");
        }

        @Test
        @DisplayName("refuses to empty a required multitext")
        void refusesToEmptyARequiredMultitext() {
            refused(ErrorCode.CANNOT_CLEAR_REQUIRED_MULTITEXT,
                """
                clear gloss@en
                  on sense[gloss@en = "taro"]
                  of entry[form@tww = "mami", hn = 2]
                """);
        }

        @Test
        @DisplayName("refuses the wildcard on a required multitext, whatever the state")
        void refusesTheWildcardOnARequiredMultitext() {
            refused(ErrorCode.CANNOT_CLEAR_REQUIRED_MULTITEXT,
                """
                clear gloss@*
                  on sense[gloss@en = "pig"]
                  of entry[form@tww = "mami", hn = 1]
                """);
        }

        @Test
        @DisplayName("removes every language of an optional multitext with the wildcard")
        void removesEveryLanguageOfAnOptionalMultitext() {
            assertEffects(
                apply("""
                    clear definition@*
                      on sense[gloss@en = "pig"]
                      of entry[form@tww = "mami", hn = 1]
                    """),
                "removed:definition@en=A four-legged terrestrial animal");
        }

        @Test
        @DisplayName("requires an explicit qualifier on a multitext")
        void requiresAnExplicitQualifier() {
            refused(ErrorCode.MISSING_LANGUAGE_QUALIFIER,
                """
                clear definition
                  on sense[gloss@en = "pig"]
                  of entry[form@tww = "mami", hn = 1]
                """);
        }

        @Test
        @DisplayName("removes a scalar value and leaves the component")
        void removesAScalarValue() {
            assertEffects(
                apply("""
                    clear value
                      on category
                      of sense[gloss@en = "pig"]
                      of entry[form@tww = "mami", hn = 1]
                    """),
                "removed:value=Noun");
            assertTrue(senses(entries("tww", "mami").get(0), "en", "pig").size() == 1);
        }

        @Test
        @DisplayName("refuses a scalar identity property, which may never become unset")
        void refusesAScalarIdentityProperty() {
            refused(ErrorCode.CANNOT_CLEAR_IDENTITY_PROPERTY,
                """
                clear type
                  on relation#1
                  of entry[form@tww = "memi"]
                """);
        }

        @Test
        @DisplayName("refuses a qualified value that is not set")
        void refusesAnUnsetQualifiedValue() {
            refused(ErrorCode.UNSET_QUALIFIED_PROPERTY,
                """
                clear definition@fr
                  on sense[gloss@en = "pig"]
                  of entry[form@tww = "mami", hn = 1]
                """);
        }
    }

    @Nested
    @DisplayName("property availability")
    class Availability {

        @Test
        @DisplayName("refuses a property the metamodel does not define on the component type")
        void refusesAnUndefinedProperty() {
            refused(ErrorCode.PROPERTY_DOES_NOT_EXIST_ON_COMPONENT_TYPE,
                """
                set transcription@tww = "mami"
                  on sense[gloss@en = "pig"]
                  of entry[form@tww = "mami", hn = 1]
                """);
        }

        @Test
        @DisplayName("refuses a language key on a scalar property")
        void refusesALanguageKeyOnAScalar() {
            refused(ErrorCode.LANG_KEY_NOT_SUPPORTED_ON_SCALAR,
                """
                set value@en = "Noun"
                  on category
                  of sense[gloss@en = "pig"]
                  of entry[form@tww = "mami", hn = 1]
                """);
        }

        @Test
        @DisplayName("refuses the same qualified property twice in one command")
        void refusesADuplicateProperty() {
            refused(ErrorCode.DUPLICATE_PROPERTY,
                """
                set definition@en = "A pig", definition@en = "Another pig"
                  on sense[gloss@en = "pig"]
                  of entry[form@tww = "mami", hn = 1]
                """);
        }

        @Test
        @DisplayName("keeps the languages of a multitext independent of one another")
        void keepsLanguagesIndependent() {
            apply("""
                set definition@fr = "Un animal"
                  on sense[gloss@en = "pig"]
                  of entry[form@tww = "mami", hn = 1]
                """);
            var sense = senses(entries("tww", "mami").get(0), "en", "pig").get(0);
            assertEquals(Optional.of("A four-legged terrestrial animal"),
                definition(sense, "en"));
            assertEquals(Optional.of("Un animal"), definition(sense, "fr"));
        }
    }
}
