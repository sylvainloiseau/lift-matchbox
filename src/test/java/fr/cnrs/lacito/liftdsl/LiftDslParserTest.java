package fr.cnrs.lacito.liftdsl;

import fr.cnrs.lacito.liftdsl.model.*;
import fr.cnrs.lacito.liftdsl.validation.ValidationError;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LiftDslParserTest {
    private final LiftDsl dsl = new LiftDsl();

    @Test
    void parsesReferenceComponentAndPropertyCommands() {
        Program program = dsl.parse(
            "create entry(form@tww = \"mami\"); " +
            "create sense(gloss@en = \"pig\") under entry[form@tww = \"mami\"]; " +
            "set definition@en = \"A definition\" on sense[gloss@en = \"pig\"] of entry[form@tww = \"mami\"]; " +
            "clear definition@en on sense[gloss@en = \"pig\"] of entry[form@tww = \"mami\"]"
        );

        assertEquals(4, program.commands().size());
        assertEquals(Command.Kind.CREATE, program.commands().get(0).kind());
        assertEquals(Command.Kind.CLEAR, program.commands().get(3).kind());
    }

    @Test
    void parsesAllReferenceCommandKinds() {
        Program program = dsl.parse(
            "create entry(form@tww = \"mami\"); " +
            "upsert sense(gloss@en = \"pig\") under entry[form@tww = \"mami\"]; " +
            "ensure sense(gloss@en = \"animal\") under entry[form@tww = \"mami\"]; " +
            "delete sense[gloss@en = \"pig\"] under entry[form@tww = \"mami\"]; " +
            "move sense[gloss@en = \"pig\"] at end; " +
            "set category = \"Noun\" on sense[gloss@en = \"pig\"] of entry[form@tww = \"mami\"]; " +
            "update category = \"Verb\" on sense[gloss@en = \"pig\"] of entry[form@tww = \"mami\"]; " +
            "clear category on sense[gloss@en = \"pig\"] of entry[form@tww = \"mami\"]"
        );

        assertEquals(8, program.commands().size());
        assertEquals(Command.Kind.MOVE, program.commands().get(4).kind());
        assertEquals(Command.Kind.UPDATE, program.commands().get(6).kind());
    }

    @Test
    void expandsConciseCommandsToReferenceSyntax() {
        String expanded = dsl.expandConcise("c /e[f=\"mami\"] s(g=\"pig\")");

        assertEquals("create sense(g = \"pig\") under entry[f = \"mami\"]", expanded);
    }

    @Test
    void rejectsNullAndMalformedSources() {
        assertThrows(ValidationError.class, () -> dsl.parse(null));
        assertThrows(ValidationError.class, () -> dsl.parse("create"));
        assertThrows(ValidationError.class, () -> dsl.parseConcise("create /e"));
    }
}
