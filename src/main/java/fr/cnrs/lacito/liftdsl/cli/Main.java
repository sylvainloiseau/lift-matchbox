package fr.cnrs.lacito.liftdsl.cli;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.FeatureSet;
import fr.cnrs.lacito.liftdsl.LiftDsl;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URI;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Command-line entry point for applying a Lift-DSL patch to a LIFT dictionary.
 * <p>
 * The command reads the DSL file, loads the input dictionary through
 * {@link LiftDictionary#loadDictionaryFromFile(java.io.File)}, executes the selected syntax, and writes the mutated
 * dictionary to the output path. When no input dictionary is supplied, an empty dictionary with {@code tww} as object
 * language and {@code en} as meta language is created.
 * </p>
 */
@Command(name = "lift-patchbox", mixinStandardHelpOptions = true, version = "lift-patchbox 0.1-SNAPSHOT", description = "Apply Lift-DSL commands to a LIFT dictionary.")
public final class Main implements Callable<Integer> {
    /** Supported source syntaxes. */
    public enum Syntax {
        concise, reference
    }

    @Option(names = { "-s",
            "--syntax" }, defaultValue = "reference", description = "DSL syntax used by the command file: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE})")
    private Syntax syntax;

    @Parameters(index = "0", description = "Path to the DSL command file.")
    private Path commandFile;

    @Parameters(index = "1..*", arity = "1..2", description = "Either <output-lift-file>, or <input-lift-file> <output-lift-file>.")
    private List<Path> dictionaryFiles;

    /**
     * Executes the command-line operation.
     *
     * @return zero on success
     *
     * @throws Exception
     *             if a file cannot be read, the dictionary cannot be loaded, the DSL fails validation or execution, or
     *             the output cannot be written
     */
    @Override
    public Integer call() throws Exception {
        requireRegularFile(commandFile, "DSL command file");

        String source = Files.readString(commandFile, StandardCharsets.UTF_8);
        LiftDictionary dictionary = loadDictionary();
        LiftDsl dsl = new LiftDsl();

        if (syntax == Syntax.concise) {
            dsl.executeConcise(source, dictionary);
        } else {
            dsl.execute(source, dictionary);
        }

        normalizeExternalRangeReferences(dictionary);
        dictionary.save(dictionaryFiles.get(dictionaryFiles.size() - 1).toFile());
        return 0;
    }

    /**
     * Loads the requested input dictionary or creates a new empty dictionary when only an output path was supplied.
     *
     * @return dictionary to patch
     *
     * @throws Exception
     *             if the supplied input dictionary cannot be loaded
     */
    private LiftDictionary loadDictionary() throws Exception {
        if (dictionaryFiles.size() == 1) {
            return LiftDictionary.makeBuilder().withObjectLanguages("tww").withMetaLanguages("en").build();
        }

        Path inputLiftFile = dictionaryFiles.get(0);
        requireRegularFile(inputLiftFile, "input LIFT file");
        return LiftDictionary.loadDictionaryFromFile(inputLiftFile.toFile());
    }

    /**
     * Rewrites external range references to file names so the Lift API writes auxiliary range files next to the
     * requested output document.
     *
     * @param dictionary
     *            dictionary that is about to be written
     */
    private static void normalizeExternalRangeReferences(LiftDictionary dictionary) {
        for (FeatureSet featureSet : dictionary.getHeader().getFeatureSets()) {
            featureSet.getHref().ifPresent(href -> {
                String fileName = href;
                try {
                    fileName = href.startsWith("file:") ? Paths.get(URI.create(href)).getFileName().toString()
                            : Paths.get(href).getFileName().toString();
                } catch (RuntimeException ignored) {
                    int separator = Math.max(href.lastIndexOf('/'), href.lastIndexOf('\\'));
                    if (separator >= 0 && separator + 1 < href.length()) {
                        fileName = href.substring(separator + 1);
                    }
                }
                featureSet.setHref(fileName);
            });
        }
    }

    /**
     * Starts Picocli and returns its exit status to the operating system.
     *
     * @param args
     *            command-line arguments
     */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    private static void requireRegularFile(Path path, String description) {
        if (path == null || !Files.isRegularFile(path)) {
            throw new CommandLine.ParameterException(new CommandLine(new Main()),
                    description + " does not exist or is not a regular file: " + path);
        }
    }
}
