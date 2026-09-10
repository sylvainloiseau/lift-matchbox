# Annex 3: Implementation instruction for the IA Agent

## General mission

You are a team of developpers in charge of developping a java library called `liftpatchbox` that will implement a complete parser for the LiftPatch DSL language.

- You will read the file "specification.md" in this directory, that provides the formal specification of the two surface syntaxes of the Lift-Patch. You are in charge of implementing a library able to parse these two languages, create the command list, read an input dictionary (or create an empty one), apply the mutation command, and save the result dictionary.

## Technical instruction

- You will create a maven project in the current repository. The groupId is "fr.cnrs.lacito" and the artifactId is "lift-patchbox".

- The java code will be
located in the root package `fr.cnrs.lacito.liftpatch` and sub-packages.

- You will generate two ANTLR grammar (lexer and parser), one for the LiftPatchRef syntax and one for the LiftPatchShort syntax. Keep the lexer and parser simple, validation will be performed latter.
- You will use the `java.util.logging` package for creating a logging system. According to the level (SEVERE, etc.) set, it will output at the command line different level of detail of information about the building of the command, then, after the command had been applied, it will print a summary of the number of component created for each component type, the number of operation for each command type.
- you will create visitors for receiving the result of the ANTLR parse and create java objects representing the command, their argument, their chaining, etc. For creating the commands java object, you will provide a fluent API. Both the visitor for LiftPatchRef and the visitor for LiftPatchShort will use this fluent API. The fluid API could also be used programmatically. In the visitor, you will get the line number or each token provided by the ANTLR parser and store it into the object created.
- You will use the `liftapi` for all operation on the dictionary itself, including marshalling and unmarshalling the dictionary. The liftapi library method is described below, in section "## The liftapi library for accessing a Lift Dictionary". The methods and classes provided by the liftapi allow for actual interaction with a dictionary (lookup, creation, supression...)
- you will create a commit strategy for all command, allowing not to modify the dictionary before everything is validated as much as possible
- You will create a semantic validation step, for validating all constraints between component, properties, command, parent chain, etc. as mentioned in the specification.md file
- You will create a dictionary validation step, checking that requirement regarding existence or non existence of object in the dictionary is satisfied. For instance, if a create command create a component whereas a component with the same identity properties already exist under the same parent, it should raise an error.
- You will create an error management system, dealing which each error case mentionned in the specification. When error occur, a detailled error message should be outputed and logged in the logger, providing a clear and explicit explanation of the error, and indicating the line number, in the command file, where the error occur. If the commands were built programmatically, using the fluent API, no error line can be provided.
- You will add a documentation on all java class, public method, and package, following standard java convention for the documentation of parameter, return value, and exception.
- You will generate a test suite using Junit. The test classes will be in the standard location (src/test/java). You will read the dictionary located at "src/test/resources.dictionary/tww/lift20250717.lift" using "fr.cnrs.lacito.liftapi.LiftDictionary.loadDictionaryFromFile(File f)", which will return a LiftDictionary object. You test suite could use all the valid commands in the file specification.md, i.e. commands starting with "```LiftPatchShort" for test concise syntax and commands starting with "```LiftPatchRef" for testing the reference syntax. You will group this command in test according to the aspect of the language tested (component, construct...). Set programmatically the default object lang to "tww" and the default meta language to "en" for the commands to be run on this dictionary.
- you will add a command line entry point, using the picocli library for handling command line argument, that will allows to run the library from the command line. For instance with the syntax: `java -jar target/lift-patchbox-0.1-SNAPSHOT.jar \
  <dsl-commands-file> \
  <input-lift-file> \
  <output-lift-file>` where the output-lift-file will contains the modified dictionary, patched with the commands <dls-command-file>. An option with values "concise"|"reference" should also be offered for allowing the user to explain which syntax she or he use.

## The liftapi library for accessing a Lift Dictionary


In order to generate an implementation of this DSL specification you will need to connect to a lift dictionary, in order to query, create object, get languages, etc.

For refering to a dictionary you will use the existing `liftapi` library, that model a lift dictionary and offer query and creation methods.

The liftapi library is a maven library that is avaible in the local maven repository under the coordinate: fr.cnrs.lacito:lift-api (version: 0.1-SNAPSHOT)

The liftdsl library will be provided with a LiftDictionary (in package fr.cnrs.lacito.liftapi.LiftDictionary) instance.

This instance offers the following methods.

### Lang

1/ the method getMetaLanguageManager() and getObjectLanguageManager():
    - each return a different fr.cnrs.lacito.liftapi.LiftDictionary.LiftDictionaryLanguagesManager instance
    - this instance offers getLanguages() method that return a Set of the language name (string)
    - this instance offers a getDefaultLanguage() that return the name of the first language in the set (object or meta language) (string)

- dictionary component can be created using

### Component class

Each component class name is prefixed with "Lift-": LiftSense, LiftEntry, LiftNote...

They are in the fr.cnrs.lacito.liftapi.model package.

- Each scalar field can be updated with the set<Property> method and read with get<Property> method.
  - setting null will clear the property
- For each multitext property, a get<Propery> will return a Multitext instance.
  - the Multitext class offer the following methods:
  - `containsLang(String lang)` will return a boolean indicating within a sub-entry exist for this lang.
  - `getForm(String lang)` will give the form (sub-entry) (or throw an exception if no sub-entry exist for the lang) for this lang for this multitext property
  - `removeForm(String lang)` will remove the form (sub-entry) (or throw an exception if no sub-entry exist for the lang) for this lang for this multitext property
  - `getLangs()` return a `Set<String>` listing the lang actually existing in the sub-entries.
- for each component child type, the following methods exist:
  - `add<Component>(component)` for example, Sense class offers addExample(LiftExample example)
  - get<Component>s for example, Sense class offers getExamples(), wich return a List<LiftExample>
  - `<components>Property()`, for example `Sense` class offers an `examplesProperty()` method that return a `ListProperty` in `javafx.beans.property` package, that can be iterated.

### Component creation

the method `getComponentBuilder()` return an instance of `DictionaryComponentBuilderFactory` (in package `fr.cnrs.lacito.liftapi.builder`)
  - this instance contains method for creating builder with fluent API for each component type: for instance the method `entry()` will create an EntryBuilder instance, `sense()` will create a `SenseBuilder`.
  - All builder instances are located in the same package fr.cnrs.lacito.liftapi.builder.
  - all builders allow to set properties with the with<Property> method:
    - for instance, `senseBuilder.withCategory(String category)` set the category
    - for multitext, two arguments are needed: `senseBuilder.withGlos(String lang, String gloss)` in order to add a qualified gloss.
  - all builders allow to register a child component with add<ComponentType> method:
    - for instance, `entryBuilder.addSense(LiftSense senseBuilder.build())` will add a sense
  - all builder create the final object with `build()`
    - for instance, `senseBuilder.build()` create a Sense object.

### Lookup

The LiftDictionary class offers `getEntryByForm(String lang, String form)`, that will return the `List<LiftEntry>` of entries having the corresponding form for the corresponding languages. Iteration on Sense, Note, Field, Variant, etc. can then be done from the LiftEntry objects.

- The `LiftDictionary` class also offers a `getLiftDictionaryRegistry()` methods that return a `LiftDictionaryRegistry` instance (in the same package). This class offers the following methods:
  - `getEntryOrSenseByLiftId(String ID)` return an AbstractIdentifiable, i.e. a superclass of LiftSense and LiftObject, or throw an exception if no entry or sense component has this ID.
  - `getEntries()`: return a `ObservableList<LiftEntry>`: a list of all entries

### Removing component

The LiftDictionaryRegistry instance returned by LiftDictionary  .getLiftDictionaryRegistry() also offers :

 - `removeFromDictionary(AbstractLiftRoot node)`: remove a component. `AbstractLiftRoot` (in package
   `fr.cnrs.lacito.liftapi.model`) is the superclass of all component type. This
   methods will remove a component, cut the link parent/child (so that the node
   will not be its parent child's list, for example a sense removed will not be
   seen anymore from its parent LiftEntry instance.)
