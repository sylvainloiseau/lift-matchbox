# lift-patchbox

A complete language for editing a dictionary into LIFT format through commands.

Instead of opening Elan or SIL Fieldwork for entering lexical information, type directly your fieldnote / idea using this command language, or convert questionaire/spreadsheet into such commands:

```
##
## creating quickly new entry + a new gloss
## c = create, e = entry, s = sense
## the entry should not exist -- to avoid error, see below
##
c e("mami", s("pig"))
c e("efe", s("door"))
c e("o", s("child"))

## If you need to add more info:
c e("mami", s("pig")) as $mami


## or use the block construct:
create entry[form="mami"] {
   create sense[gloss="pig"] {
      create example[text="a mami jefi"] {
          create translation[type="free", text="I shoot a pig"]
      }
   }
}

## Adding a new interesting example on an existing entry>sense
### Long reference syntax:
create exemple(text="A mamimo jefimwij")
   under sense[gloss="pig"] within entry[form="mami"]
   {
   create transation(type="litteral", text="I shot a pig in particular")
}
### Or use the concise syntax:

set value = "Noun" on category of sense[gloss="pig"] of entry[form="mami"]

A very usefull construct
p /mami/pig         # - create new mami entry with the sense "pig"
                    #   if no mami entry exist
                    #   or if none of the mami entry has the sense "pig"
                    # - do nothing if a mami entry exist with sense "pig".
```


## Overview

LIFT dictionary is the dictionary format used by SIL Fieldworks and Elan.

While SIL Fieldwork offer a convenient GUI for working on the dictionary, it is
tedious to insert with the GUI long list of lexical information collected in
spreadsheet, with short notation on digital or manuscript notebooks, etc.

This library implements a domain-specific language (DSL) for creating, modifying, deleting, and moving structural nodes and data fields in dictionaries following the LIFT (Lexicon Interchange Format) model.

It allows the user to notate concisely lexical information, such as :

```
# set (s) the definition on the sense "pig" of the entry "mami"
s /"mami"/"pig" (definition="A four-legged large terrestrial animal")

# assert the existence of a lexical unit of form "mami" and gloss "pig", create it if it does not exist
# quotes are not necessary for string without special character
p /mami/pig 
```

Such commands, that can be listed in a file, can be used to enrich an existing
LIFT dictionary. These short notations can also be generated easily from
spreadsheet or other format.

The complete DSL language allows to create/update/delete any kind of nodes in the LIFT data model using either a concise or a reference language.

### Reference syntax

## Command-line usage

The Maven build produces an executable JAR:

```bash
mvn package
```

`target/lift-patchbox-0.1-SNAPSHOT-jar-with-dependencies.jar` is self-contained.

```bash
# patch an existing dictionary
java -jar target/lift-patchbox-0.1-SNAPSHOT-jar-with-dependencies.jar \
  edits.liftpatchs tww.lift tww-patched.lift --syntax concise

# start from an empty dictionary: only the output path is given
java -jar target/lift-patchbox-0.1-SNAPSHOT-jar-with-dependencies.jar \
  edits.liftpatch new.lift

# see what a script would do, and change nothing
java -jar target/lift-patchbox-0.1-SNAPSHOT-jar-with-dependencies.jar \
  edits.liftpatch tww.lift out.lift --plan

# emit the plan document of section 12.4.1 as JSON
java -jar target/lift-patchbox-0.1-SNAPSHOT-jar-with-dependencies.jar \
  edits.liftpatch tww.lift out.lift --plan --json
```

The surface syntax is decided in this order: the script's `%liftpatch` pragma
when it declares `syntax=`, then `--syntax`, then the file extension —
`.liftpatch` for the reference syntax and `.liftpatchs` for the concise one.
Getting it wrong is otherwise silent, which is why a document in which not one
line was recognized as a command is reported with a `NO_COMMAND_RECOGNIZED`
warning.

Other options:

| Option | What it does |
|---|---|
| `--plan`, `-p` | Report what the script would do and change nothing. |
| `--json` | Print the plan document as JSON rather than as prose. |
| `--object-language`, `--meta-language` | Set the default languages, overriding the dictionary's first ones. |
| `--metamodel <file>` | Validate against an extended metamodel (Appendix A). |
| `--verbose`, `-v` / `--quiet`, `-q` | Log how the commands are built / errors and warnings only. |

The tool always validates before it writes, and never writes a partially applied
dictionary: a script either succeeds completely or leaves the output file
unwritten.

## Programmatic usage

```java
LiftDictionary dictionary = LiftDictionary.loadDictionaryFromFile(new File("tww.lift"));

LiftPatchBox box = new LiftPatchBox()
    .withDefaultObjectLanguage("tww")
    .withDefaultMetaLanguage("en");

Plan plan = box.plan(dictionary, script, Syntax.CONCISE, "edits.liftpatchs");
if (plan.isOk()) {
    box.apply(dictionary, script, Syntax.CONCISE, "edits.liftpatchs");
    dictionary.save(new File("tww-patched.lift"));
}
```

Commands can also be built without writing a script at all, through the same
fluent API the two parsers use:

```java
Script script = LiftPatch.script(Syntax.REFERENCE)
    .add(LiftPatch.create("sense")
            .init("gloss", "en", "pig")
            .under(LiftPatch.chain(LiftPatch.step("entry").eq("form", "tww", "mami")))
            .as("pig"))
    .add(LiftPatch.set()
            .assign("definition", "en", "A four-legged terrestrial animal")
            .on(LiftPatch.chain(LiftPatch.label("pig"))))
    .build();
```

A command built that way is checked by the same validator, against the same
metamodel, and is rejected with the same error codes as a parsed one.

## How the code is laid out

| Package | What it holds |
|---|---|
| `metamodel` | The normative metamodel of Appendix A, loaded from the very JSON document the specification prints, and the short-code tables of the concise syntax. |
| `ast` | One immutable command model shared by both syntaxes, and `LiftPatch`, the fluent API that builds it. |
| `parser` | The two ANTLR grammars, the concise-syntax preprocessor, and the two visitors. |
| `validation` | Every error that depends only on the script text and the metamodel, collected rather than thrown. |
| `engine` | Selector resolution, execution, the plan document, the run summary. |
| `model` | The one place that knows how the metamodel maps onto the `lift-api` dictionary model, and the journal that makes a script atomic. |
| `error` | The codes of Appendix B, with their kinds. |
| `cli` | The command-line tool. |

Both surface syntaxes produce the same command objects: a LiftPatchShort path,
written ancestor first, is reversed into the child-first chain the reference
syntax writes, and the concise abbreviations are expanded. Everything
downstream — validation, resolution, execution, plan mode — is therefore written
once.

## Building

```bash
mvn clean package     # generates the parsers, compiles, tests, builds the JARs
mvn test              # the test suite alone
mvn javadoc:javadoc   # the API documentation, under target/reports/apidocs
```

The build needs `fr.cnrs.lacito:lift-api:0.1-SNAPSHOT` in the local Maven
repository. The ANTLR sources are `src/main/antlr4/.../LiftPatchRef.g4` and
`LiftPatchShort.g4`; `mvn generate-sources` regenerates the parsers into
`target/generated-sources/antlr4` on its own, so there is nothing to check in.

## Tests

| Suite | What it checks |
|---|---|
| `ConformanceCorpusTest` | The 80 cases of Appendix D, run exactly as D.1 defines the comparison. |
| `SpecificationExamplesTest` | Every fenced `LiftPatchRef` and `LiftPatchShort` example of `specification.md` parses and is statically valid. |
| `ComponentCommandsTest`, `PropertyCommandsTest` | The eight verbs, grouped by verb. |
| `SelectionTest` | The seven selection strategies, the two axes, the pseudo-properties, multiplicity, references. |
| `ConstructsTest` | Blocks, labels, language scoping, embedded initializers, atomicity, plan mode. |
| `ConciseSyntaxTest` | Line recognition, abbreviations, indented blocks, the `p /path` idiom, and agreement with the reference syntax. |
| `TuwuliDictionaryTest` | A real 1925-entry field dictionary: patch, save, reload, roll back. |

## The metamodel and the dictionary model

Every component type, every property and every parentage the metamodel of
Appendix A declares has a home in the `lift-api` object model this library
writes through, so no script is ever refused for want of somewhere to put its
result. Two declarations of the metamodel exist to keep that true, and both are
part of the language rather than workarounds:

- **`trait.type` is read-only** (specification, section 4.5). The dictionary
  model fixes a trait's definition when the trait is built, and that definition
  is both the key the trait is stored under and what gives its value a datatype.
  `set`, `update` and `clear` on it therefore raise `PROPERTY_IS_READ_ONLY`, a
  static error; to give a trait another type, delete it and create it again. It
  is the only read-only property, and `writable: false` in Appendix A is what
  declares it, so an extended metamodel may declare others.
- **A `reversal` holds only sub-reversals, and its natural identity is
  `type + form`.** The dictionary model gives a reversal forms, a type and
  nested reversals — no reference, and no trait, annotation or field.

## DSL language reference

`specification.md` is the single source of truth: the data model and semantics
(Part 1), the reference syntax (Part 2), the concise syntax (Part 3), the
normative metamodel (Appendix A), the error codes (Appendix B), the grammars
(Appendix C) and the conformance corpus (Appendix D).

## License

This project is part of the LIFT ecosystem and follows its licensing terms.

## Contact

For questions or issues, refer to the LIFT project documentation or create an
issue in the repository.
