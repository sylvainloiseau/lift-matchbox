package fr.cnrs.lacito.liftpatchbox.cli;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftpatchbox.LiftPatchBox;
import fr.cnrs.lacito.liftpatchbox.ast.Script;
import fr.cnrs.lacito.liftpatchbox.ast.Syntax;
import fr.cnrs.lacito.liftpatchbox.engine.Plan;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchError;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchException;
import fr.cnrs.lacito.liftpatchbox.metamodel.Metamodel;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * The command-line tool: patch a LIFT dictionary with a LiftPatch script.
 *
 * <pre>{@code
 * # patch an existing dictionary
 * java -jar lift-patchbox.jar edits.liftpatchs tww.lift tww-patched.lift --syntax concise
 *
 * # start from an empty dictionary
 * java -jar lift-patchbox.jar edits.liftpatch new.lift
 *
 * # see what a script would do, and change nothing
 * java -jar lift-patchbox.jar edits.liftpatch tww.lift out.lift --plan
 * }</pre>
 *
 * <p>The tool always validates before it writes, and never writes a partially
 * applied dictionary: a script either succeeds completely or leaves the output
 * file unwritten (Part 2, section 12.2).</p>
 */
@Command(
    name = "lift-patchbox",
    mixinStandardHelpOptions = true,
    version = "lift-patchbox 0.1-SNAPSHOT (LiftPatch 1.0)",
    sortOptions = false,
    description = "Apply a LiftPatch script to a LIFT dictionary.",
    footer = {
        "",
        "The surface syntax is taken from the script's %%liftpatch pragma when it",
        "declares one, then from --syntax, then from the file extension",
        "(.liftpatch for the reference syntax, .liftpatchs for the concise one)."
    }
)
public final class Main implements Callable<Integer> {

    private static final Logger LOGGER = Logger.getLogger(Logging.ROOT + ".cli");

    /** The exit status of a run that applied, or would apply, cleanly. */
    public static final int EXIT_OK = 0;
    /** The exit status of a script that was refused. */
    public static final int EXIT_SCRIPT_ERROR = 1;
    /** The exit status of a failure that is not the script's fault, such as an unreadable file. */
    public static final int EXIT_IO_ERROR = 2;

    @Parameters(
        index = "0",
        paramLabel = "<dsl-commands-file>",
        description = "The LiftPatch script to apply.")
    private Path commands;

    @Parameters(
        index = "1..*",
        arity = "1..2",
        paramLabel = "<lift-file>",
        description = {
            "Either <input-lift-file> <output-lift-file>, which patches an",
            "existing dictionary, or <output-lift-file> alone, which patches",
            "a new empty one."
        })
    private Path[] dictionaries;

    @Option(
        names = {"-s", "--syntax"},
        paramLabel = "<syntax>",
        description = "The surface syntax of the script: ${COMPLETION-CANDIDATES}.")
    private SyntaxOption syntax;

    @Option(
        names = {"-p", "--plan"},
        description = "Report what the script would do and change nothing (plan mode).")
    private boolean planOnly;

    @Option(
        names = "--json",
        description = "Print the plan document as JSON rather than as prose.")
    private boolean json;

    @Option(
        names = "--object-language",
        paramLabel = "<code>",
        description = "The default object language, overriding the dictionary's first one.")
    private String objectLanguage;

    @Option(
        names = "--meta-language",
        paramLabel = "<code>",
        description = "The default meta language, overriding the dictionary's first one.")
    private String metaLanguage;

    @Option(
        names = "--metamodel",
        paramLabel = "<file>",
        description = "An extended metamodel to validate against, as a JSON document.")
    private Path metamodelFile;

    @Option(
        names = {"-v", "--verbose"},
        description = "Log how the commands are built, not only what they do.")
    private boolean verbose;

    @Option(
        names = {"-q", "--quiet"},
        description = "Log errors and warnings only.")
    private boolean quiet;

    /** The two spellings of the {@code --syntax} option. */
    public enum SyntaxOption {
        /** The verbose reference syntax of Part 2. */
        reference(Syntax.REFERENCE),
        /** The concise, line-oriented syntax of Part 3. */
        concise(Syntax.CONCISE);

        private final Syntax syntax;

        SyntaxOption(Syntax syntax) {
            this.syntax = syntax;
        }

        /**
         * The surface syntax this option names.
         *
         * @return the syntax
         */
        public Syntax syntax() {
            return syntax;
        }
    }

    /**
     * The entry point.
     *
     * @param args the command-line arguments
     */
    public static void main(String[] args) {
        System.exit(new CommandLine(new Main()).execute(args));
    }

    @Override
    public Integer call() {
        Logging.configure(
            verbose ? Level.FINE : quiet ? Level.WARNING : Level.INFO, System.err);

        Path input = dictionaries.length == 2 ? dictionaries[0] : null;
        Path output = dictionaries[dictionaries.length - 1];

        try {
            LiftPatchBox box = new LiftPatchBox(loadMetamodel())
                .withDefaultObjectLanguage(objectLanguage)
                .withDefaultMetaLanguage(metaLanguage);

            // A parse error is raised before the validator runs and is the one error
            // the library does not log itself, so it is logged here. Everything else
            // is logged where it is raised, and is not repeated below.
            Script script;
            try {
                script = box.parseFile(commands, syntax == null ? null : syntax.syntax());
            } catch (LiftPatchException e) {
                for (LiftPatchError error : e.errors()) {
                    LOGGER.log(Level.SEVERE, error::format);
                }
                System.err.println("The dictionary was left unchanged.");
                return EXIT_SCRIPT_ERROR;
            }
            LiftDictionary dictionary = openDictionary(input);

            Plan plan = planOnly ? box.plan(dictionary, script) : box.apply(dictionary, script);
            report(plan);
            if (!plan.isOk()) {
                return EXIT_SCRIPT_ERROR;
            }
            if (!planOnly) {
                dictionary.save(output.toFile());
                System.out.println("Wrote " + output);
            }
            return EXIT_OK;
        } catch (LiftPatchException e) {
            // The validator and the executor log every error they raise, so it is
            // not repeated here; only the outcome is stated.
            System.err.println("The dictionary was left unchanged.");
            return EXIT_SCRIPT_ERROR;
        } catch (Exception e) {
            System.err.println("error: " + e.getMessage());
            return EXIT_IO_ERROR;
        }
    }

    private Metamodel loadMetamodel() throws java.io.IOException {
        if (metamodelFile == null) {
            return Metamodel.standard();
        }
        return Metamodel.load(Files.readString(metamodelFile, StandardCharsets.UTF_8));
    }

    /**
     * Load the input dictionary, or build an empty one when only an output path was
     * given.
     */
    private LiftDictionary openDictionary(Path input) throws Exception {
        if (input != null) {
            return LiftDictionary.loadDictionaryFromFile(new File(input.toString()));
        }
        String object = objectLanguage == null ? "tww" : objectLanguage;
        String meta = metaLanguage == null ? "en" : metaLanguage;
        System.out.println("Starting from an empty dictionary with object language `"
            + object + "` and meta language `" + meta + "`");
        return LiftDictionary.makeBuilder()
            .withObjectLanguages(object)
            .withMetaLanguages(meta)
            .build();
    }

    /**
     * Print the outcome: the plan document as JSON when asked, and otherwise the
     * errors, the warnings and the summary a lexicographer reads.
     */
    private void report(Plan plan) {
        if (json) {
            System.out.println(plan.toJson());
            return;
        }
        // The errors and warnings a plan carries were logged as they were raised,
        // so they are not printed again here.
        if (!plan.isOk()) {
            System.err.println(planOnly
                ? "The script would fail; nothing was changed."
                : "The dictionary was left unchanged.");
            return;
        }
        if (!plan.danglingReferences().isEmpty()) {
            System.out.println(plan.danglingReferences().size()
                + " reference(s) would be left dangling:");
            plan.danglingReferences().forEach(d -> System.out.println(
                "  " + d.componentType() + "." + d.property() + " -> " + d.value()
                    + " (broken by operation " + d.operationIndex() + ", a " + d.verb() + ")"));
        }
        System.out.println(planOnly ? "Plan (nothing was changed):" : "Applied:");
        System.out.println(plan.summary().format());
    }
}
