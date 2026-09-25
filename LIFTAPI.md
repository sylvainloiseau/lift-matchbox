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
