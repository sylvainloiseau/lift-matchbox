package fr.cnrs.lacito.liftpatchbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.liftpatchbox.ast.Syntax;
import fr.cnrs.lacito.liftpatchbox.engine.Plan;
import fr.cnrs.lacito.liftpatchbox.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The LiftPatchShort concise syntax (Part 3): its recognition rule, its
 * abbreviations, its axes, its indented blocks and the {@code p /path} idiom.
 *
 * <p>The concise syntax has no semantics of its own: every command it expresses
 * expands into one reference-syntax command. The tests therefore check the
 * expansion by checking its effects, and several of them write the same operation
 * in both syntaxes and assert that the two agree.</p>
 */
@DisplayName("Concise syntax")
class ConciseSyntaxTest extends PatchTestSupport {

    @BeforeEach
    void setUp() {
        newDictionary();
    }

    @Nested
    @DisplayName("recognizing a command line")
    class Recognition {

        @Test
        @DisplayName("a prose line beginning with a command letter is prose")
        void proseIsProse() {
            assertEffects(
                applyConcise("""
                    c est une erreur fréquente
                    e e[f="pig"] is prose too, since a bare step is not a path
                    c /memi/puppy
                    """),
                "created:sense@2", "set:gloss@en=puppy");
        }

        @Test
        @DisplayName("a line beginning with a backslash is never a command")
        void aBackslashEscapesALine() {
            Plan plan = applyConcise("\\c /memi/puppy\n");
            assertEffects(plan);
            assertEquals("NO_COMMAND_RECOGNIZED", plan.warnings().get(0).code().name());
        }

        @Test
        @DisplayName("a trailing comment is stripped, and an ordinal is not one")
        void stripsATrailingComment() {
            assertEffects(
                applyConcise("d /e[f=\"mami\", hn=1]!s[g=\"pig\"] x#1   # the first example\n"),
                "deleted:example@1");
        }

        @Test
        @DisplayName("a declared sigil decides which lines are commands")
        void aSigilDecidesWhichLinesAreCommands() {
            assertEffects(
                applyConcise("""
                    %liftpatch 1.0 sigil=">"
                    c /memi/puppy is prose here, since the line carries no sigil
                    > c /memi/puppy
                    """),
                "created:sense@2", "set:gloss@en=puppy");
        }

        @Test
        @DisplayName("a line that matches the rule but does not parse is an error, never prose")
        void aMistypedCommandIsAnError() {
            refusedConcise(ErrorCode.SYNTAX_ERROR, "c /e[f=\"memi\" s(g=\"puppy\")\n");
        }
    }

    @Nested
    @DisplayName("paths and axes")
    class PathsAndAxes {

        @Test
        @DisplayName("every written link of a parent path is existential")
        void everyWrittenLinkIsExistential() {
            assertEffects(applyConcise("s /e[f=\"mami\"]/s[g=\"taro\"]/c (v = \"Noun\")"),
                "set:value=Noun");
        }

        @Test
        @DisplayName("the link between the path and the target is strict")
        void theLinkToTheTargetIsStrict() {
            refusedConcise(ErrorCode.AMBIGUOUS_REFERENCE, "d /e[f=\"mami\"] s[g=\"taro\"]");
        }

        @Test
        @DisplayName("the strict axis is written ! and admits a pseudo-property above it")
        void theStrictAxisAdmitsAPseudoProperty() {
            assertEffects(applyConcise("d /e[f=\"mami\", hn=1]!s[g=\"pig\"] x#1"),
                "deleted:example@1");
        }

        @Test
        @DisplayName("an existential path over two homophones is ambiguous")
        void anExistentialPathOverHomophonesIsAmbiguous() {
            refusedConcise(ErrorCode.AMBIGUOUS_REFERENCE, "d /mami/pig x#1");
        }

        @Test
        @DisplayName("the direct target of a delete is exactly one step")
        void theTargetIsOneStep() {
            refusedConcise(ErrorCode.TARGET_MUST_BE_A_SINGLE_STEP, "d /mami/pig");
        }

        @Test
        @DisplayName("a property command may write its immediate parent as a separate step")
        void aPropertyCommandMayWriteItsParentSeparately() {
            applyConcise("c /memi n(y=\"general\", t@en = \"recorded at Yakoro\")");
            assertEffects(
                applyConcise("u /memi n^general (t@en = \"recorded at Yakoro, 2025\")"),
                "replaced:text@en:recorded at Yakoro->recorded at Yakoro, 2025");
        }
    }

    @Nested
    @DisplayName("abbreviated steps")
    class AbbreviatedSteps {

        @Test
        @DisplayName("depth 1 is an entry designated by its form")
        void depthOneIsAnEntryForm() {
            assertEffects(applyConcise("d /memi"), "deleted:entry@null");
        }

        @Test
        @DisplayName("depth 2 is a sense designated by its gloss")
        void depthTwoIsASenseGloss() {
            assertEffects(applyConcise("e /memi dog"));
        }

        @Test
        @DisplayName("depth 3 is an example designated by its text")
        void depthThreeIsAnExampleText() {
            assertEffects(
                applyConcise("d /e[f=\"mami\", hn=1]!s[g=\"pig\"] \"a mami jefi\""),
                "deleted:example@1");
        }

        @Test
        @DisplayName("there is no abbreviation below the third depth")
        void thereIsNoAbbreviationBelowTheThird() {
            refusedConcise(ErrorCode.ABBREVIATED_STEP_NOT_ALLOWED_HERE,
                "d /mami/pig/\"a mami jefi\" foo");
        }

        @Test
        @DisplayName("a quoted token is always an abbreviated step")
        void aQuotedTokenIsAlwaysAbbreviated() {
            refusedConcise(ErrorCode.NOT_FOUND,
                "d /e[f=\"mami\", hn=1]!s[g=\"pig\"] \"c\"");
        }

        @Test
        @DisplayName("a bare singleton name is never an abbreviated step")
        void aBareSingletonNameIsASingletonStep() {
            refusedConcise(ErrorCode.SINGLETON_CANNOT_BE_CREATED_OR_DELETED,
                "d /e[f=\"mami\", hn=1]!s[g=\"pig\"] c");
        }

        @Test
        @DisplayName("a language suffix qualifies an abbreviated value")
        void aLanguageSuffixQualifiesTheValue() {
            assertEffects(applyConcise("c e(\"pikpik\"@tpi)"),
                "created:entry@null", "set:form@tpi=pikpik");
        }
    }

    @Nested
    @DisplayName("unnamed initializer arguments")
    class UnnamedArguments {

        @Test
        @DisplayName("map to the properties the metamodel declares, in order")
        void mapToTheDeclaredProperties() {
            assertEffects(
                applyConcise("c /e[f=\"memi\"]/s[g=\"dog\"] f(\"editorial\", \"To be checked\")"),
                "created:field@null", "set:type=editorial", "set:text@en=To be checked");
        }

        @Test
        @DisplayName("are refused beyond the number the component type allows")
        void areRefusedBeyondTheDeclaredNumber() {
            refusedConcise(ErrorCode.UNNAMED_ARGUMENT_NOT_ALLOWED,
                "c /e[f=\"memi\"] s(\"puppy\", \"extra\")");
        }

        @Test
        @DisplayName("are refused on a component type that declares none")
        void areRefusedOnATypeWithNone() {
            refusedConcise(ErrorCode.UNNAMED_ARGUMENT_NOT_ALLOWED,
                "c /e[f=\"memi\"] r(\"synonym\")");
        }
    }

    @Nested
    @DisplayName("indented blocks")
    class IndentedBlocks {

        @Test
        @DisplayName("anchor their commands to the component of the line above")
        void anchorToTheLineAbove() {
            assertEffects(
                applyConcise("""
                    c e("mimi")
                      c s("lizard")
                        s (d@en = "A lizard")
                    """),
                "created:entry@null", "set:form@tww=mimi",
                "created:sense@1", "set:gloss@en=lizard",
                "set:definition@en=A lizard");
        }

        @Test
        @DisplayName("let a relative path start at the block's component")
        void letARelativePathStartAtTheBlockComponent() {
            assertEffects(
                applyConcise("""
                    e /e[f="mami", hn=1]
                      d s[g="pig"] x#1
                    """),
                "deleted:example@1");
        }

        @Test
        @DisplayName("close when a blank line ends the command group")
        void closeAtTheEndOfTheGroup() {
            assertEffects(
                applyConcise("""
                    c e("mimi")
                      c s("lizard")

                    c e("momo")
                    """),
                "created:entry@null", "set:form@tww=mimi",
                "created:sense@1", "set:gloss@en=lizard",
                "created:entry@null", "set:form@tww=momo");
        }

        @Test
        @DisplayName("refuse a delete as an anchor, its component being gone")
        void refuseADeleteAsAnAnchor() {
            refusedConcise(ErrorCode.DELETE_CANNOT_BE_AN_ANCHOR,
                """
                d /memi
                  c s("puppy")
                """);
        }

        @Test
        @DisplayName("refuse a tab in the indentation")
        void refuseATabInTheIndentation() {
            refusedConcise(ErrorCode.SYNTAX_ERROR, "c e(\"mimi\")\n\tc s(\"lizard\")\n");
        }
    }

    @Nested
    @DisplayName("the p /path idiom")
    class UpsertIdiom {

        @Test
        @DisplayName("creates the entry and the sense when no match exists")
        void createsWhenNoMatchExists() {
            assertEffects(applyConcise("p /mimi/lizard"),
                "created:entry@null", "set:form@tww=mimi",
                "created:sense@1", "set:gloss@en=lizard");
        }

        @Test
        @DisplayName("changes nothing when the pair already exists")
        void changesNothingWhenThePairExists() {
            assertEffects(applyConcise("p /mami/taro"));
        }

        @Test
        @DisplayName("extends to the third abbreviated step, the example text")
        void extendsToTheExample() {
            assertEffects(applyConcise("p /mimi/lizard/\"a mimi jefi\""),
                "created:entry@null", "set:form@tww=mimi",
                "created:sense@1", "set:gloss@en=lizard",
                "created:example@1", "set:text@tww=a mimi jefi");
        }

        @Test
        @DisplayName("gives a new meaning a new entry rather than a homophone that means something else")
        void givesANewMeaningANewEntry() {
            assertEffects(applyConcise("p /mami/lizard"),
                "created:entry@null", "set:form@tww=mami",
                "created:sense@1", "set:gloss@en=lizard");
            assertEquals(3, entries("tww", "mami").size());
        }

        @Test
        @DisplayName("refuses a path whose steps are not abbreviated")
        void refusesANonAbbreviatedPath() {
            refusedConcise(ErrorCode.CONSTRUCTOR_REQUIRED, "p /e[f=\"mami\"]/s[g=\"pig\"]");
        }

        @Test
        @DisplayName("refuses a single step, which upserts an entry with no disambiguation")
        void refusesASingleStep() {
            refusedConcise(ErrorCode.UPSERT_ENTRY_WITHOUT_DISAMBIGUATION, "p /mami");
        }
    }

    @Nested
    @DisplayName("agreement with the reference syntax")
    class AgreementWithTheReferenceSyntax {

        @Test
        @DisplayName("the same operation written in both syntaxes has the same effects")
        void bothSyntaxesAgree() {
            Plan concise = box.plan(dictionary,
                "s /e[f=\"mami\", hn=1]!s[g=\"pig\"]/c (v = \"Verb\")",
                Syntax.CONCISE, "<concise>");
            Plan reference = box.plan(dictionary, """
                set value = "Verb"
                  on category
                  of sense[gloss@en = "pig"]
                  of entry[form@tww = "mami", hn = 1]
                """, Syntax.REFERENCE, "<reference>");
            assertEquals(1, concise.summary().propertiesSet());
            assertEquals(concise.allEffects().size(), reference.allEffects().size());
            assertTrue(concise.isOk() && reference.isOk());
        }

        @Test
        @DisplayName("a concise move with a to clause matches the reference two-under form")
        void moveAgrees() {
            assertEffects(
                applyConcise("m /e[f=\"mami\", hn=1]!s[g=\"pig\"] x#1 "
                    + "to /e[f=\"mami\", hn=1]!s[g=\"pork\"] at end"),
                "moved:example:1->1");
        }
    }
}
