package fr.cnrs.lacito.liftpatchbox.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import fr.cnrs.lacito.liftpatchbox.ast.Block;
import fr.cnrs.lacito.liftpatchbox.ast.Command;
import fr.cnrs.lacito.liftpatchbox.ast.Script;
import fr.cnrs.lacito.liftpatchbox.ast.Syntax;
import org.junit.jupiter.api.Test;

/** A first end-to-end check that both grammars parse and both visitors build. */
class ParserSmokeTest {

    private final ScriptParser parser = new ScriptParser();

    private Script ref(String text) {
        return parser.parse(text, Syntax.REFERENCE, "<test>");
    }

    private Script concise(String text) {
        return parser.parse(text, Syntax.CONCISE, "<test>");
    }

    @Test
    void parsesAReferenceCreate() {
        Script s = ref("create sense(gloss@en = \"pig\") under entry[form@tww = \"mami\"]");
        assertEquals(1, s.items().size());
        Command.Create c = assertInstanceOf(Command.Create.class, s.items().get(0));
        assertEquals("sense", c.constructor().componentType());
        assertEquals("entry", ((fr.cnrs.lacito.liftpatchbox.ast.Step.Component)
            c.parent().head()).componentType());
        System.out.println(c);
    }

    @Test
    void parsesAReferenceBlock() {
        Script s = ref("""
            entry[form@tww = "mami"] {
              create sense(gloss@en = "pig") {
                set definition@en = "An animal"
                set value = "Noun" on category
              }
            }
            """);
        Block b = assertInstanceOf(Block.class, s.items().get(0));
        assertEquals(1, b.body().size());
        System.out.println(b);
    }

    @Test
    void parsesAConciseCreate() {
        Script s = concise("c /e[f=\"mami\"] s(g=\"pig\")");
        Command.Create c = assertInstanceOf(Command.Create.class, s.items().get(0));
        assertEquals("sense", c.constructor().componentType());
        System.out.println(c);
    }

    @Test
    void parsesConciseAbbreviations() {
        Script s = concise("c /mami/pig x(\"a mami jefi\")");
        Command.Create c = assertInstanceOf(Command.Create.class, s.items().get(0));
        System.out.println(c);
        assertEquals("example", c.constructor().componentType());
    }

    @Test
    void parsesTheUpsertIdiom() {
        Script s = concise("p /mimi/lizard");
        Block b = assertInstanceOf(Block.class, s.items().get(0));
        System.out.println(b);
    }

    @Test
    void parsesAnIndentedBlock() {
        Script s = concise("""
            c e("mimi")
              c s("lizard")
                s (d@en = "A lizard")
            """);
        Block b = assertInstanceOf(Block.class, s.items().get(0));
        System.out.println(b);
        assertNotNull(b.header());
    }

    @Test
    void ignoresProse() {
        Script s = concise("""
            c est une erreur fréquente
            e e[f="pig"] is prose too, since a bare step is not a path
            c /memi/puppy
            """);
        assertEquals(1, s.items().size());
        System.out.println(s.items().get(0));
    }

    @Test
    void parsesTheWorkedExample() {
        Script s = concise("c e(\"mami\", s(\"pig\", x(\"A mami jefi\", o(\"literal\", \"I shot a pig\"))))");
        System.out.println(s.items().get(0));
    }
}
