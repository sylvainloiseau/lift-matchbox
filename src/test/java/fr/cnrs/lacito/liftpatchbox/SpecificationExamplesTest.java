package fr.cnrs.lacito.liftpatchbox;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.liftpatchbox.ast.Script;
import fr.cnrs.lacito.liftpatchbox.error.ErrorCode;
import fr.cnrs.lacito.liftpatchbox.error.ErrorCollector;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchError;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Parses and statically validates every fenced example of the specification.
 *
 * <p>This is the check Appendix D.4 asks for — "every normative example of Parts
 * 1 to 3 SHOULD appear in the corpus, with its stated outcome. An example that no
 * case covers is an example nobody has checked" — applied to the examples the
 * corpus does not cover. It does not run them against a dictionary, because most
 * of them speak of entries no fixture has; what it checks is that each one parses
 * and is free of static errors, which is the class of defect the specification
 * says keeps surviving review by hand.</p>
 *
 * <p>A handful of examples are deliberately illegal: the specification prints
 * them next to the error code they raise, to show what the rule refuses. Those
 * are listed in {@link #DELIBERATELY_ILLEGAL} with the code each is expected to
 * raise, and the test asserts that they <em>do</em> raise it — so they are
 * checked, not skipped.</p>
 */
class SpecificationExamplesTest {

    /**
     * Examples the specification prints in order to show what is refused, keyed by
     * the line of their opening fence, with the code each one is expected to raise.
     */
    private static final Map<Integer, ErrorCode> DELIBERATELY_ILLEGAL = Map.of(
        // 5.2: a sense selected on the strict axis by two qualified glosses.
        869, ErrorCode.DUPLICATE_SELECTOR,
        // 5.3.2.2: an ambiguous entry that the child selector must not disambiguate.
        1120, ErrorCode.AMBIGUOUS_REFERENCE,
        // 7.3.1: a unique selector carrying a predicate beyond its strategy.
        1771, ErrorCode.PREDICATE_NOT_ALLOWED_IN_UNIQUE_SELECTOR,
        // Part 3, 5.6: the block's last line, annotated with the code it raises.
        4210, ErrorCode.MISSING_LANGUAGE_QUALIFIER
    );

    private final LiftPatchBox box = new LiftPatchBox()
        .withDefaultObjectLanguage("tww")
        .withDefaultMetaLanguage("en");

    /**
     * One dynamic test per fenced example, named by its line in the specification.
     *
     * @return the tests, in document order
     */
    @TestFactory
    Stream<DynamicTest> everyFencedExampleIsStaticallyValid() {
        Assumptions.assumeTrue(SpecificationExamples.available(),
            "specification.md is not readable from the working directory");
        return SpecificationExamples.all().stream()
            .filter(e -> !e.script().isBlank())
            .filter(e -> !e.isElided())
            .map(example -> DynamicTest.dynamicTest(
                example.label() + " — " + example.firstLine(),
                () -> check(example)));
    }

    private void check(SpecificationExamples.Example example) {
        ErrorCode expected = DELIBERATELY_ILLEGAL.get(example.line());

        Script script;
        try {
            script = box.parse(example.script(), example.syntax(), example.label());
        } catch (LiftPatchException e) {
            if (expected != null) {
                assertTrue(codes(e.errors()).contains(expected),
                    example.label() + ": expected " + expected + " but got " + codes(e.errors()));
                return;
            }
            throw new AssertionError(
                example.label() + " does not parse: " + e.firstError().format()
                    + System.lineSeparator() + example.script(), e);
        }

        ErrorCollector errors = box.validate(script);
        if (expected != null) {
            // A statically illegal example must be refused here; a dynamically
            // illegal one parses and validates, and fails only against a dictionary.
            if (expected.isStatic()) {
                assertTrue(codes(errors.errors()).contains(expected),
                    example.label() + ": expected " + expected + " but got "
                        + codes(errors.errors()));
            }
            return;
        }
        assertFalse(errors.hasErrors(),
            example.label() + " is not statically valid: "
                + errors.errors().stream().map(LiftPatchError::format).toList()
                + System.lineSeparator() + example.script());
    }

    private static Set<ErrorCode> codes(List<LiftPatchError> errors) {
        return errors.stream().map(LiftPatchError::code)
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
