package fr.cnrs.lacito.liftpatchbox.parser;

import fr.cnrs.lacito.liftpatchbox.ast.Pragma;
import fr.cnrs.lacito.liftpatchbox.ast.Script;
import fr.cnrs.lacito.liftpatchbox.ast.Syntax;
import fr.cnrs.lacito.liftpatchbox.error.ErrorCode;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchError;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchException;
import fr.cnrs.lacito.liftpatchbox.error.SourcePosition;
import fr.cnrs.lacito.liftpatchbox.metamodel.Metamodel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

/**
 * The entry point for turning a script into the command model.
 *
 * <p>It dispatches on the surface syntax and applies the version pragma rules of
 * Part 1, section 1.1. The syntax is decided in this order: the {@code syntax=}
 * pragma attribute, when the script declares one; otherwise the syntax the caller
 * asked for; otherwise the file extension, {@code .liftpatch} for the reference
 * syntax and {@code .liftpatchs} for the concise one.</p>
 *
 * <p>Reading a script with the wrong syntax is silent rather than loud — a
 * reference script read as a concise document matches no command line and applies
 * nothing — which is why plan mode reports {@code NO_COMMAND_RECOGNIZED} for a
 * document in which not one line was recognized, and why declaring the syntax on
 * the pragma is worth doing.</p>
 */
public final class ScriptParser {

    private static final Logger LOGGER = Logger.getLogger(ScriptParser.class.getName());

    /** The language version this implementation supports. */
    public static final String SUPPORTED_VERSION = "1.0";

    private final Metamodel metamodel;

    /**
     * A parser validating against the standard metamodel of Appendix A.
     */
    public ScriptParser() {
        this(Metamodel.standard());
    }

    /**
     * A parser validating against a given metamodel.
     *
     * @param metamodel the metamodel; an extended one may declare additional
     *                  component types and properties
     */
    public ScriptParser(Metamodel metamodel) {
        this.metamodel = metamodel;
    }

    /**
     * Parse a script held in a string.
     *
     * @param text    the script text
     * @param syntax  the syntax to parse it with, or {@code null} to take the one
     *                the pragma declares and fail if it declares none
     * @param source  the source name, used in diagnostics and plan documents
     * @return the script
     * @throws LiftPatchException when the script does not parse, or declares an
     *         unsupported version, metamodel or pragma attribute
     */
    public Script parse(String text, Syntax syntax, String source) {
        Syntax declared = declaredSyntax(text);
        Syntax effective = declared != null ? declared : syntax;
        if (effective == null) {
            throw new LiftPatchException(LiftPatchError.of(
                ErrorCode.SYNTAX_ERROR,
                "the surface syntax of " + source + " is not declared by a pragma and was not "
                    + "given; pass --syntax, use a .liftpatch or .liftpatchs extension, or write "
                    + "%liftpatch 1.0 syntax=\"LiftPatchRef\"",
                SourcePosition.UNKNOWN));
        }
        LOGGER.log(Level.FINE, () ->
            "Parsing " + source + " as " + effective.pragmaName());

        Script script = effective == Syntax.REFERENCE
            ? parseReference(text, source)
            : new ShortScriptParser(source, metamodel).parse(text);
        checkPragma(script.pragma());
        return script;
    }

    /**
     * Parse a script held in a file.
     *
     * @param file   the file to read, in UTF-8
     * @param syntax the syntax to parse it with, or {@code null} to take it from
     *               the pragma or from the file extension
     * @return the script
     * @throws IOException        if the file cannot be read
     * @throws LiftPatchException when the script does not parse
     */
    public Script parseFile(Path file, Syntax syntax) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        Syntax effective = syntax != null
            ? syntax
            : Syntax.fromFileName(file.getFileName().toString());
        return parse(text, effective, file.getFileName().toString());
    }

    private Script parseReference(String text, String source) {
        LiftPatchRefLexer lexer = new LiftPatchRefLexer(CharStreams.fromString(text));
        CollectingErrorListener listener = new CollectingErrorListener();
        lexer.removeErrorListeners();
        lexer.addErrorListener(listener);
        LiftPatchRefParser parser = new LiftPatchRefParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(listener);
        LiftPatchRefParser.ScriptContext tree = parser.script();
        if (listener.hasErrors()) {
            throw new LiftPatchException(listener.errors());
        }
        return new RefScriptVisitor(source).buildScript(tree);
    }

    /**
     * Read the {@code syntax=} attribute of the pragma without parsing the script,
     * which cannot be done before the syntax is known.
     */
    private static Syntax declaredSyntax(String text) {
        for (String raw : text.split("\\R", -1)) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (!line.startsWith("%liftpatch")) {
                // A pragma may be preceded by blank lines, comments and prose, so the
                // scan continues; it stops at the first command-looking line, which
                // no pragma may follow.
                if (line.startsWith("\\")) {
                    continue;
                }
                int at = line.indexOf("%liftpatch");
                if (at < 0) {
                    continue;
                }
                line = line.substring(at);
            }
            int at = line.indexOf("syntax=");
            if (at < 0) {
                return null;
            }
            int q = at + "syntax=".length();
            if (q >= line.length()) {
                return null;
            }
            char quote = line.charAt(q);
            int close = line.indexOf(quote, q + 1);
            return close < 0 ? null : Syntax.fromPragmaName(line.substring(q + 1, close));
        }
        return null;
    }

    /**
     * Apply the three pragma rules of Part 1, section 1.1: the version must be
     * supported, the attributes must be known, and a required metamodel must be the
     * one that is loaded.
     */
    private void checkPragma(Pragma pragma) {
        if (pragma == null) {
            return;
        }
        if (!SUPPORTED_VERSION.equals(pragma.version())) {
            throw new LiftPatchException(LiftPatchError.of(
                ErrorCode.UNSUPPORTED_LANGUAGE_VERSION,
                "this implementation supports LiftPatch " + SUPPORTED_VERSION
                    + ", and the script declares " + pragma.version(),
                pragma.position()));
        }
        for (String name : pragma.attributes().keySet()) {
            if (!name.equals(Pragma.SIGIL)
                && !name.equals(Pragma.METAMODEL)
                && !name.equals(Pragma.SYNTAX)) {
                throw new LiftPatchException(LiftPatchError.of(
                    ErrorCode.UNKNOWN_PRAGMA_ATTRIBUTE,
                    "the version pragma of LiftPatch 1.0 carries `sigil`, `metamodel` and "
                        + "`syntax`, and no other attribute; found `" + name + "`",
                    pragma.position()));
            }
        }
        String required = pragma.metamodel();
        if (required != null && !required.equals(metamodel.metamodelId())) {
            throw new LiftPatchException(LiftPatchError.of(
                ErrorCode.UNSUPPORTED_METAMODEL,
                "the script requires the metamodel `" + required + "`, and the one loaded is `"
                    + (metamodel.metamodelId() == null ? "1.0" : metamodel.metamodelId()) + "`",
                pragma.position()));
        }
    }
}
