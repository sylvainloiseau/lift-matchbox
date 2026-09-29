# Implementation instruction for the AI Agent

## General mission

You are a team of senior developpers, expert in the java language, in charge of developping a java library called `liftpatchbox` that will implement a complete parser for the LiftPatch domain specific language (DSL).

You will read the file "specification.md" in this directory, which provides the formal specification of the two surface syntaxes of the LiftPatch DSL. You are in charge of implementing a library able to parse these two languages, create the command list, the execution plan, read an input dictionary (or create an empty one), apply the mutation command, and save the result dictionary.

## Technical instruction

- You will create a maven project in the current repository. The groupId is "fr.cnrs.lacito" and the artifactId is "lift-patchbox".

- The java code will be
located in the root package `fr.cnrs.lacito.liftpatchbox` and sub-packages.

- You will generate two ANTLR grammars (lexer and parser), one for the LiftPatchRef syntax and one for the LiftPatchShort syntax. Keep the lexer and parser simple, validation will be performed latter.
- you will create two visitors (one for each grammar) for receiving the result of the ANTLR parse and create java objects representing the command, their argument, their chaining, etc. For creating the commands java object, you will provide a fluent API. Both the visitor for LiftPatchRef and the visitor for LiftPatchShort will use this fluent API. The fluid API could also be used programmatically. In the visitor, you will get the line number or each token provided by the ANTLR parser and store it into the object created in order to help debugging later.
- You will use the `liftapi` for all operation on the dictionary itself, including marshalling and unmarshalling the dictionary. The methods and classes provided by the liftapi allow for actual interaction with a dictionary (lookup, creation, supression, movement...). The liftapi library is is available in the ../DictionaryEditor/lift-api directory. You will explore this codebase to understand how to use it. If you cannot access the codebase, you will stop completely and report to me that you cannot access it.
- You will create a semantic validation step, for validating all constraints between component, properties, command, parent chain, etc. as mentioned in the specification.md file
- You will create a dictionary validation step, checking that requirement regarding existence or non existence of object in the dictionary is satisfied. For instance, if a create command create a component whereas a component with the same identity properties already exist under the same parent, it should raise an error.
- you will create a commit strategy for the command set, allowing not to modify the dictionary before everything is validated.
- You will create an error management system, dealing which each error case encoutered while executing the plan mode (or dry run) of the specification. Shoud one or several errors occur, a detailled error message should be outputed and logged in the logger, providing a clear and explicit explanation of the error, and indicating the line number, in the command file, where the error occur.
- You will use the `java.util.logging` package for creating a logging system. According to the level (SEVERE, etc.) set, it will output at the command line different level of detail of information about the building of the command, then, after the command had been applied, it will print a summary of the number of component created for each component type, the number of operation for each command type.
- You will add a documentation on all java class, public method, and package, following standard java convention for the documentation of parameter, return value, and exception.
- You will generate a test suite using Junit. The test classes will be in the standard location (src/test/java). You will read the dictionary located at "src/test/resources.dictionary/tww/lift20250717.lift" using "fr.cnrs.lacito.liftapi.LiftDictionary.loadDictionaryFromFile(File f)", which will return a LiftDictionary object. You test suite could use all the valid commands in the file specification.md, i.e. commands starting with "```LiftPatchShort" for test concise syntax and commands starting with "```LiftPatchRef" for testing the reference syntax. You will group this command in test according to the aspect of the language tested (component, construct...). Set programmatically the default object lang to "tww" and the default meta language to "en" for the commands to be run on this dictionary.
- you will add a command line entry point, using the picocli library for handling command line argument, that will allows to run the library from the command line. For instance with the syntax: `java -jar target/lift-patchbox-0.1-SNAPSHOT.jar \
  <dsl-commands-file> \
  <input-lift-file> \
  <output-lift-file>` where the output-lift-file will contains the modified dictionary, patched with the commands <dls-command-file>. An option with values "concise"|"reference" should also be offered for allowing the user to explain which syntax she or he use.
