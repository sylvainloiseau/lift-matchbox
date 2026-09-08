package fr.cnrs.lacito.liftdsl;

import fr.cnrs.lacito.liftdsl.validation.ValidationError;
import fr.cnrs.lacito.liftdsl.validation.SemanticValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SemanticValidatorTest {
    private final LiftDsl dsl = new LiftDsl();

    @Test
    void rejectsUnknownProperties() {
        assertThrows(ValidationError.class, () -> new SemanticValidator()
                .validate(dsl.parse("set unknown = \"value\" on entry[form@tww = \"mami\"]")));
    }

    @Test
    void rejectsMissingRequiredSenseInitializer() {
        assertThrows(ValidationError.class, () -> new SemanticValidator()
                .validate(dsl.parse("create sense(category = \"Noun\") under entry[form@tww = \"mami\"]")));
    }

    @Test
    void rejectsIllegalParentRelationship() {
        assertThrows(ValidationError.class, () -> new SemanticValidator()
                .validate(dsl.parse("create example(text@tww = \"example\") under entry[form@tww = \"mami\"]")));
    }
}
