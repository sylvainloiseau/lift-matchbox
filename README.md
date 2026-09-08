# lift-patchbox

A Java library for parsing and executing DSL commands to mutate LIFT dictionary
data, a dictionary format used for linguistic description of lexical data.

## Overview

LIFT dictionary is the dictionary format used by SIL Fieldworks and Elan.

While SIL Fieldwork offer a convenient GUI for working on the dictionary, it is
tedious to insert with the GUI long list of lexical information collected in
spreadsheet, with short notation on digital or manuscript notebooks, etc.

This library implements a domain-specific language (DSL) for creating, modifying, deleting, and moving structural nodes and data fields in dictionaries following the LIFT (Lexicon Interchange Format) model.

It allows the user to notate concisely lexical information, such as :

```
u /"mami"/"pig"
```

for asserting the existence of a lexical unit of form "mami" and gloss "pig".
Such commands, that can be listed in a file, can be used to enrich an existing
LIFT dictionary. These short notations can also be generated easily from
spreadsheet or other format.

The complete DSL language allows to create/update/delete any kind of nodes in the LIFT data model using either a concise or a reference language.

### Reference syntax

## Command-line usage

The Maven package contains an executable JAR with the Lift-DSL CLI:

```bash
# Create and patch a new empty dictionary
java -jar target/lift-patchbox-0.1-SNAPSHOT.jar \
  [--syntax reference|concise] \
  <dsl-commands-file> \
  <output-lift-file>

# Load, patch, and save an existing dictionary
java -jar target/lift-patchbox-0.1-SNAPSHOT.jar \
  [--syntax reference|concise] \
  <dsl-commands-file> \
  <input-lift-file> \
  <output-lift-file>
  ```

When only the output path is supplied, the CLI creates an empty dictionary
with `tww` as its object language and `en` as its meta language.

The syntax defaults to `reference`. Use `--syntax concise` for the
line-oriented concise syntax. Existing input dictionaries are loaded with
`LiftDictionary.loadDictionaryFromFile(File)`, patched transactionally, and
saved to the output path.


## Building

### 1. Build with Maven

```bash
mvn clean compile
```

This will:
- Generate ANTLR lexer and parser classes from `LiftDsl.g4`
- Compile all Java source files

### 2. Run Tests

```bash
mvn test
```

### 3. Build JAR

```bash
mvn package
```

Creates `target/lift-patchbox-0.1-SNAPSHOT.jar`

### Programmatic Usage

```java
```

## DSL Language Reference

See `specification.md` for complete language specification, including:

- Path syntax (short form, long form, indexed nodes)
- Command types (Upsert, Create, Delete, Move, Update)
- Language and type modifiers
- Homophone handling
- Semantic constraints

### Generating ANTLR Classes

If you modify `LiftDsl.g4`, regenerate the parser:

```bash
mvn antlr4:antlr4
```

This updates the generated files in `target/generated-sources/antlr4`.

## License

This project is part of the LIFT ecosystem and follows its licensing terms.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit changes
4. Push to the branch
5. Open a pull request

## Contact

For questions or issues, refer to the LIFT project documentation or create an issue in the repository.
