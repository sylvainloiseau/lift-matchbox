
# LIFT-DSL : a Lift Mutation Language

This document defines a command DSL for creating, updating, deleting, upserting, and moving components and properties in a dictionary based on the LIFT data model.

The language preserves the LIFT component hierarchy while making component identity, parentage, property availability, creation, selection, and mutation semantics explicit.

The dictionary is a tree-like structure; components are nodes in the tree,
properties are attached to the nodes and have datatype.

## 1. Design principles

1. Every operation has an explicit verb: `create`, `ensure`, `set`, `upsert`, `update`, `clear`, `delete`, or `move`.
2. Parentheses describe a component being created or initialized.
3. Square brackets select an existing component
4. Multiple matches of component during lookup are always errors. The language never silently selects the first match.
5. `set` creates or replaces a property value.
6. `upsert` creates a missing component and applies its initializers, or resolves an existing component.
7. `update` replaces an existing property and never creates it.
8. `clear` removes property values without removing the parent component.
9. `delete` removes structural components.
10. A command or atomic block either succeeds completely or has not effect.

## 2 LIFT Dictionary

A lift dictionary is an ordered list of Entry component.

Since a lift dictionary is not a monolingual dictionary, it also has :

- an ordered list of object-languages, i.e. one or more language(s) that is described in the dictionary. It is for instance the language(s) that are represented in the entry, or in the example. The list cannot be empty.
- an ordered list of meta-languages, i.e. one or more language(s) that are used to describe the object language. It is for instance the language(s) that are used in the gloss, the definition, or the translation of the example. The list cannot be empty.

## 2.1. Default languages

At any moment, there is always a default meta language and a default object language that the command can use if a required language is not specified.

The resolution is :

- if a "language-default" command is in scope, use its value;
- if there is only one Meta (resp. Object) language in the dictionary, use it;
- otherwise, use the first Meta (resp. Object) language in the language list returned by the dictionary.

The default language applies to

- selectors
- create constructor initializer
- ensure
- upsert identity
- set
- update
- has-gloss pseudo-property

It does not apply to clear, where omitting a language has a special meaning.

The two following commands allow to set the default language from a Lift-DSL script: 

```text
language-default object = "tww"
language-default meta = "en"
```

New languages cannot be declared that way. A meta (resp. object) language name
refered by this commands must exist in the dictionary meta (resp. object) language list. `NOT_SUCH_META_LANGUAGE` (resp. `NOT_SUCH_OBJECT_LANGUAGE`) should be raised if the language name mentioned in the command is not found.

They apply from their position in the file until overridden:

```text
language-default object = "tww"
language-default meta = "en"

set form = "mami"
on entry[form = "mami"]

set gloss = "pig"
  on sense[gloss = "pig"]
  of entry[form = "mami"]
```

### Create new languages

The `language-create` allow to create a new language in the dictionary. It take as a subcommand the string `object` or `meta` and as value the language code name. An error is thrown if the language already exist in the dictionary. Examples:

```text
language-create object = "tpi"
language-create meta = "fr"
```

## 3. LIFT components

A **component** is a structural LIFT node such as an entry, sense, or example. A component contains one or several properties.

| Component | LIFT-DSL name |
|---|---|
| Entry | `entry` |
| Sense | `sense` |
| Example | `example` |
| Etymology | `etymology` |
| Variant | `variant` |
| Relation | `relation` |
| Illustration | `illustration` |
| Media | `media` |
| Pronunciation | `pronunciation` |
| Reversal | `reversal` |
| Trait | `trait` |
| Annotation | `annotation` |
| Note | `note` |
| Field | `field` |
| Translation | `translation` |
 

- The entry is a top-level component.
- other components are non-top-level components.

The allowed parent-child relationships are:

| Parent | Allowed child components |
|---|---|
| Dictionary | `entry`|
| Entry | `sense`, `etymology`, `variant`, `relation`, `pronunciation`, `reversal`, `trait`, `annotation`, `note`, `field` |
| Sense | `sense`, `example`, `relation`, `illustration`, `reversal`, `trait`, `annotation`, `note`, `field` |
| Variant | `pronunciation`, `relation`, `trait`, `annotation`, `field` |
| Pronunciation | `media`, `trait`, `annotation`, `field` |
| Example | `translation`, `trait`, `annotation`, `field` |
| Etymology | `trait`, `annotation`, `field` |
| Relation | `trait`, `annotation`, `field` |
| Reversal | `trait`, `annotation`, `field` |
| Annotation | |
| Note | `annotation` |
| Field | `annotation` |
| Translation |  |

This table is exhaustive. A component may only be created or moved below a
parent listed in the table.

Creating a component under an illegal parent (or moving a component towards an
illegal parent) must fail with an 'ILLEGAL_PARENT' error.

All parents can have multiple children components of the same type. For
instance, a `sense` component can have multiple `example` children. The children
can be addressed by their position in the parent's list of children (see below
"Ordinal selector").

## 4. Lift Properties

A **property** is a LIFT datafield such as `form`, `gloss`, or `definition`. The following table list the properties. Each row give a property name, the following columms indicates:
- The `Component` column: on which component the property is available
- The `Datatype` column: which is the dataype of the property:
  - Scalar properties:
    - String,
    - Integer,
    - Reference (a string containing the ID of an entry or sense),
    - URL (a string containing an URL).
  - multitext, which is a Map containing several strings associated to keys that are a lang code (more on this below, § "3.1. The multitext datatype")
- The `language set` column: only applicable for property having `multitext` as datatype, in the previous column.
  - some multitext are for the representation of the object language(s) (the language(s) under description): their keys are drawn for the language code(s) of the object language(s).
  - some multitext are for the analysis, they are in meta-language(s): their keys are drawn for the language code(s) of the meta-language(s).
- The column `Required` indicates whether this property is required during the creation of the component. In the case of the multitext datatype, it means that at least one sub-entry (for one given language) must exist. In the case of the ID property, the property cannot be set explicitely: it is created automatically on object initialization.

The table is exhaustive and normative for semantic validation.

| Property | Component | Datatype | language set | Required | 
|---:|---|---|---:|---:|---:|
| `form` | Entry, Etymology | multitext | Object-language | Yes | 
| `morpheme` | Entry | String | | Not |
| `definition` | Sense | multitext | Meta-language | Not | 
| `gloss` | Sense, Etymology | multitext | Meta-language | Yes for sense |
| `category` | Sense | String |  | Not |
| `text` | Example| multitext | Object-language | Yes |
| `text` | Note, Field, Translation | multitext | Meta-language | Yes |
| `source` | Etymology | multitext | Meta-language | Not |
| `target` | Variant, Relation, Reversal | Reference |  | Yes |
| `url` | Illustration, Media | URL |  | Yes |
| `label` | Illustration, Media | multitext | Meta-language | Not |
| `transcription` | Pronunciation | multitext | Object-language | Yes |
| `type` | Variant, Relation, Reversal, Etymology, Trait, Annotation, Note, Field, Translation | String |  | Yes |
| `value` | Trait, Annotation | String |  | Yes |
| `comment` | Annotation | multitext | Meta-language | Not |
| `when` | Annotation | String |  | Not |
| `who` | Annotation | String |  | Not |

`hn` is short for "homophone number".

Entry and sense can also be refered by an ID. The IDs are created automatically by the system, they can be referred to but not created manually, updated, upserted, deleted or cleared.

### 4.1 The multitext datatype

The Multitext datatype means that a property of that type contains a map of
String values, each associated with a distinct language (as the keys of a map).
A Multitext value cannot have multiple string values for the same language.

A Lift Dictionary two sets of languages: Object languages (languages that are
described and appear on field such as form, example text, etc.) and Meta
languages (language in which the linguistic description is given, and that
appear on gloss, translation, etc.)

Each Multitext property is either associated with object languages or meta
languages. For instance the `form` property is associated with object languages,
while the `gloss` property is associated with meta languages.

The sub-entries of a `form` value can be associated only to language codes drawn
from the object language list of the dictionary, while all `gloss` sub-entries
must be associated with codes from the meta language of the dictionary.

A `qualified property value` is a property name + a lang name, refering to a
concrete sub-entry.

On all multitext properties, setting a language that does not exist in the
corresponding dictionary language list (either meta, or object language) should
be rejected with an ILLEGAL_LANGUAGE error.

- It is not required that all languages (metalanguages or object languages)
  existing in the dictionary have a corresponding lang-qualified value in a Meta-language
  multitext property or a Object-language multitext property. 
- Therefore, when a multitext property is a required property of a component, it
  means that at least a value for one language is given. It does not imply that
  all languages must have a value.

### 4.2 Cardinality

- scalar property cardinality (i.e. string, reference, url): only one value for each scalar property can be present on a given component
- Multitext property cardinality: one string per lang. 
  - There can be one sub-entry per lang value
  - These means that the `form` property of an entry can have multiple
    values for different languages, but cannot have multiple values for the same
    language.
  - Similarly, a sense cannot have multiple `gloss` for the same language, but
    can have multiple `gloss` for different languages.

Each component has only one ID.

### 4.3 Syntax for addressing qualified values of multitext property

The availability of keys is property-specific: some properties have a `lang`
key, some have a `type` key, and some can have both.

Lang are selected with the suffix `@<lang>`.

For instances:

```text
form@tww
gloss@en
transcription@tww
```

`@<lang>` can appears only on Multitext propertie names.

The keys allow to address the qualified string value of a multitext property
value, i.e. "sub-entries" that can be selected by language and type.

The following properties support a language:

```text
form
definition
gloss
text
source
label
transcription
```

The following properties support neither key, their are scalar string values:

```text
hn
type
morpheme
category
target
url
```

The validator MUST reject unsupported key. For example, these are
invalid:

```text
category@en
```

## 5. Component identity and selectors

### 5.1 Selection syntax

Square brackets always select existing objects. It never creates an object. Parents are not implicitly created by a selector. Parent creation is explicit.

The selector may contain: 

- for Entry and Sense: an ID  (see "5.2 IDs")
- for all components, the identity property or the combinaison of two identity properties, depending on the component type (see "5.3 Identity properties" and "5.3.1")
- For entry only: the qualified form + the has-gloss pseudo-predicate, as described in "5.4. Disambiguisation of homophones".
- for all components but entry: an index selector that select the component relatively to its position amongst similar-kind components under its parent (see "5.5. Ordinal selector")

It must contain only one of this four possible selector strategies. If several are given (for instance, an index and an ID, or an ID and the two identity properties), the validator MUST reject the selector with a 'DUPLICATE_SELECTOR' error.

Example:

```text
sense[ID = "pig-44"]
sense[gloss@en = "pig"]
variant[type="dialectal", target="pig-44"]
media[URL="htt://www.example.org/Image.png"]
entry[form@tww = "mami", has-gloss@en="pig"]
example[index = 1]
```

A selector resolves to a set. The following rules are mandatory:

1. An empty result raises `NOT_FOUND`.
2. More than one result raises `AMBIGUOUS_REFERENCE`.
3. A command requiring one object must receive exactly one result.
4. Component type and parent type compatibility are checked before dictionary lookup.
5. Selector comparisons use the declared language and type.

### 5.2 IDs

In a dictionary, entry and sense also have an `ID`. This ID is a persistent
identity. They are globally unique, stable across moves, are not reused after
deletion of a component. It can be used as a lookup predicates.

Example:

```text
sense[ID = "pig-44"]
```

IDs are set automatically during creation and therefore cannot be set,
initialized during creation, or upsert. The IDs are system-managed and always
present after initialization.

Here is a normative summary of the ID rule:

- The ID cannot be set, initialized during creation, or upsert, or cleared.
- The ID cannot be deleted
- The ID can be used as a lookup predicate

### 5.3 Identity properties

The following table give the identity property for each component.

The identity property uniquely identify a component under its parent. It means
that under a given parent, a component can be univoquely identified and selected
using the identity propery or the combination of two identity properties listed
in the table below. For instance, under a given entry, not two senses can have
the same gloss value (for any meta language). Under different parents, however,
two senses can have the same qualified gloss. 

Some component have one identity property, for instance sense. Some components
have two identity properties, for instance variant: this is the combination of
the two values that allows to uniquely address the component.

This identity properties are not a persistent, invariant identity. The gloss of a sense can be changed. They are all natural identity properties. 

The identity properties for the entry component is discussed in 5.3.1 below. 

Here is a normative summary of the identity property rules:

- They can be used as lookup predicate in selector in order to select a component under its parent
- They must be set in the initializer when creating a new component
- They can be updated
- They cannot be cleared
- They must be used in upsert command that either creates or select a component

| Component | Identity property |
|---|---|
| Sense | qualified gloss (= gloss + meta language) |
| Example | qualified text (= text + object language) |
| Variant | type + target |
| Relation | type + target |
| Etymology | type + form |
| Pronunciation | qualified transcription (= transcription + object language) |
| Reversal | type + target |
| Illustration | URL |
| Media | URL  |
| Note | Type |
| Field | Type |
| Annotation | Type |
| Translation | Type |
| Trait | Type |

In the context of selection (not the context of creation of a component), when
the identity property is a multitext, only *one* qualified property must be
given for the multitext identity property.
 
Therefore, the following example will be rejected with 'DUPLICATE_SELECTOR' because the sense is selected by two qualified values:

```text
set field^review@en =
  "My field"
on example[index = 1]
of sense[gloss@en = "pig", gloss@fr = "porc"]
of entry[form@tww = "mami"]
```

When a qualified property is given as an lookup predicate, for instance
`gloss@en` in the following example, then a sense will match if it has a
sub-entry for this language on the gloss property and if the qualified value for
this language is the same as the value given in the lookup predicate ('pig' in
the following example), it will not take into account the fact that other
sub-entry, for other language, exist or not.

```text
set field^review@en = "My field"
on example[index = 1]
of sense[gloss@en = "pig"]
of entry[form@tww = "mami"]
```

#### 5.3.1 The special case of entry: disambiguation of homophone entries

Homophones are pervasive in language and therefore in dictionary entries. Since
entries are not grouped in small sets under parents, but are all directly under
the root, they are not disambiguated by their parent.

The 'form' property CAN be used alone in a selector for an entry, but if there are several matches (ie homophones) an error 'AMBIGUOUS_REFERENCE' will be raised.

For Entry, only the ID is a natural property that can univoquely identify an
instance. Since ID is not very human-readable, for practical purposes, two
pseudo-properties are offered that can be mixed with the entry 'form' property
to disambiguate homophone entries and address univoquely an entry. These two
pseudo-properties can be used in selectors only, not with any of the commands
(create, set, update, upsert, clear). They are not natural identity properties,
since they refers to the context outside of the entry itself.

1/ The first pseudo-property is the homophone number (hn).

All homophonous entries share the same qualified form, but have a different
homophone number ('hn').

The homophone number ('hn') is not a natural identity property: on a given
entry, it depends on the number of other entry with the same form, which is not
a natural property of the entry itself. However, the form + the homophone number
('hn') allows to uniquelly address an entry in the dictionary: two entry can have
the same qualified form, but not two entries can have the same value for both
the qualified form and the homophone number ('hn'). It is a contextual lookup key rather than an identity property.

The homophone number ('hn') cannot be set by any initializer: they are managed internally by the dictionary. Therefore, they can used only in selection operations (as lookup predicates), together with the form property, but CANNOT be used with the initializer of a `create` command (it may appear in selectors used to locate a command’s parent.).

Using the 'hn' property with any of the above commands will result in an error 'ILLEGAL_USE_OF_HN'.

In a selector, 'hn' cannot be used alone, but always together with the form property :

```text
set field^review@en = "My field"
on example[index = 1]
of sense[gloss@en = "pig"]
of entry[form@tww = "mami", hn = 1]
```

If 'hn' is used alone, without the form property, an error 'HN_CANNOT_BE_USED_ALONE' will be raised.

- 'hn' are 1-indexed.
- hn are automatically assigned by the dictionary
- number are generated by entry creation order
- 'hn' can be reasigned, depending on the values of the form of the entry (set, cleared)
- for a given form with a given 'hn', the same 'hn' value is valid whatever the qualified form is referred to.
- as long as no qualified form values is changed or added in an entry, the 'hn' value is guaranteed to remain the same.
- However, a change in the form of an entry can result in a different 'hn' value being assigned, and that new value will be used for subsequent lookups even with the qualified form property that were existing before the change.

For instance, let's considere an entry with hn = 1 and form@tww = "mami" and form@tpi = "pik". The same 'hn' is valid with form@tww:

```text
entry[form@tww = "mami", hn = 1]
```

and for form form@tpi = "pik":

```text
entry[form@tpi = "pik", hn = 1]
```

However, if the form@en = "pig" is added to the entry, and if it happens that
there are already three homophones forms with form@en = "pig", then the hn value
will be reassigned to 4 for this entry. `hn = 4` should now be used even with
the lang tww and tpi in order to refer to this entry.

In other word, if an entry as form in several languages, and that there at least
one of its qualified that is in a homophone set, its homophone number is the size of the 
greatest homophone set + 1.

2/ In order to deal with homophone issues, a pseudo-property 'has-gloss' is also
defined.

The 'has-gloss' pseudo-property value must be qualified with a language code and
its value must be a string matching the qualified gloss values of one of the
entry senses. 

```text
entry[
  form@tww = "efe",
  has-gloss@en = "door"
]
```

The predicate means:

1. Select entries whose `form@tww` equals `"efe"`.
2. Retain entries having at least one child sense whose `gloss@en` equals
   `"door"`.

The (<form> + sense child with <gloss>) combination SHOULD uniquely select an
entry in the majority of cases, since having to form with the same meaning is
most probably not a desiderable linguistic analysis. However, it is not
enforced. As for any selection operation, if the combination of a qualified form
value and a has-gloss pseudo-property select several matches, an
'AMBIGUOUS_REFERENCE' error MUST be raised. If not entry matches, the error is `NOT_FOUND`. 


has-gloss cannot be used alone, without a form, to select an entry.

In the following example, on the contrary, the (sense) child selector will not
implicitly disambiguate an ambiguous parent (entry) selector. The fact that the
command refers to a sense with gloss `"pig"` must not silently determine which
entry is intended. Therefore the following command must fail if several entries
have `form@tww = "mami"`, even if there is only one entry with `form@tww =
"mami"` and that have a sense with `gloss@en = "door"`:

```text
create example(
  text@tww = "a mami jefi",
  translation@en = "I shot a pig"
)
under sense[
  gloss@en = "pig"
]
of entry[
  form@tww = "mami"
]
```

The error should be:

```text
AMBIGUOUS_REFERENCE
```

Instead, has-gloss should be used to disambiguate:

```text
create example(
  text@tww = "a mami jefi",
  translation@en = "I shot a pig"
)
under sense[
  gloss@en = "pig"
]
of entry[
  form@tww = "mami",
  has-gloss@en = "pig"
]
```

- `has-gloss` is valid only on entry selectors;
- it does not select the sense;
- it does not participate in entry identity;
- it may be combined with other entry predicates;
- if several entries satisfy it, the selector remains ambiguous and an `AMBIGUOUS_REFERENCE` error is raised.

The `has-gloss` pseudo predicate cannot be set by any initializer. Therefore, they can used only in selection operations (as lookup predicates), toghether with the form property, but CANNOT be used with the create command (it may appear in selectors used to locate a command’s parent.).

Using the 'has-gloss' property with a create command will result in an error 'ILLEGAL_USE_OF_HAS_GLOSS'.

### 5.4 Ordinal selectors

Component other than entry can also be referred by their index in the list of similar-kind
properties under its parent. Index has obviously no place in an initializer.

An ordinal selector such as `index = 2` is supported, but it is an
order-dependent selector and MUST NOT be treated as a persistent identity:

```text
create example(
  text@tww = "a mami jefi",
  translation@en = "I shot a pig"
)
under sense[
  index = 2
]
of entry[
  form@tww = "mami",
  has-gloss@en = "pig"
]
```

The ordinal selector start at 1.

The previous command will raise INDEX_OUT_OF_BOUNDS exception if there is not at
least two sense under that entry.

index cannot be mixed with any other property in a selector.

index cannot be set by any initializer. Therefore, they can used only in selection operations (as lookup predicates), but CANNOT be used with any command:

- set
- update
- create (as an initializer)
- upsert
- clear

In order to change an index, use the move command.

## 6. Initializers and creation syntax

The parenthesized values are initializers. They do not select an existing
component. On the contrary, square brackets select existing components, they do
not create new ones.

Multiple property initializers, separated by commas, are allowed (note that the
following example must fail if several homophones entries have the form "mami"
for the lang "tww"):

```text

create example(
  text@tww = "a mami jefi",
  text@tpi = "mi shutim pik"
)
under sense[
  gloss@en = "pig"
]
of entry[
  form@tww = "mami"
]
```

## 7. Core command syntax

There are five commands for components. Entry does not have a "under PARENT" clause. entry cannot be moved. The five commands for dealing with component are:

```text
create entry(initializers)
create COMPONENT(initializers) under PARENT [at POSITION]

upsert COMPONENT(initializers) under PARENT

delete entry[SELECTOR]
delete COMPONENT[SELECTOR] under PARENT

move COMPONENT[SELECTOR] under COMPONENT[SELECTOR] at POSITION
move COMPONENT[SELECTOR] at POSITION

ensure COMPONENT(initializers) under PARENT
```

upsert, move and ensure cannot apply to entry.

The three commands for dealing with properties are:

```text
set PROPERTY = VALUE on PARENT
update PROPERTY = VALUE on PARENT
clear PROPERTY on PARENT
```

The language distinguishes the following cases:

- `create COMPONENT(...)` requires that the component does not already exist
  (according to the identity properties provided) if it does, an error
  'DUPLICATE_COMPONENT' is raised);
- `upsert COMPONENT(...)` creates or resolves the component;
- `set PROPERTY = VALUE` creates the property if absent and replaces
  its value if present;
- `update PROPERTY = VALUE` requires an existing property and replaces
  its value; if the property is not already set, an "UNSET_PROPERTY" error is
  raised;
- `clear PROPERTY` removes the selected property value(s); if the
  property is not already set, an "UNSET_PROPERTY" error is raised.

As stated by these rules, entry have a special behavior since it is the root component:

- Entries are created directly under the implicit dictionary root; all other
components require an explicit parent.
- The delete command operate on entries (correctly selected), while deleting
  other components require a "under" clause.

For all property commands, if the command refers to a property that does not exist on its parent type according to the table in section "4.", an error 'PROPERTY_NOT_FOUND' is raised.

### Parent

On all eight commands, PARENT refers to the parent component. It is addressed by a component name and a mandatory `[SELECTOR]`. 

- `under` identifies the immediate parent of a component
- `on` identifies the immediate parent of a property
- An additional `of` clause can qualify that parent with its own parent
- Multiple `of` clauses can be chained together. Every `of` selector must be a parent of the previous component.
- the chain of `of` clause must end at a dictionary entry, unless an entry is
created, deleted or updated, in which case there is not `under` clause.
- skipped ancestor are not allowed

Here are some examples:

- creating an entry makes no reference to a parent:

```text
create entry(form@tww = "mami")
```

Create a sense under an existing entry:

```text
create sense(gloss@en = "pig")
  under entry[form@tww = "mami"]
```

```text
set definition@en =
  "A four-legged terrestrial animal"
on sense[gloss@en = "pig"]
of entry[form@tww = "mami"]
```

```text
update definition@en =
  "A revised four-legged terrestrial animal"
on sense[gloss@en = "pig"]
of entry[form@tww = "mami"]
```

```text
update form@tww = "memi"
  on entry[form@tww = "mami"]
```

```text
delete sense[gloss@en = "pig"]
  under entry[form@tww = "mami"]
```

`set` creates or replaces the selected property:

```text
set definition@en =
  "A definition"
on sense[gloss@en = "pig"]
of entry[form@tww = "mami"]
```

An empty string is a valid property value. It does not mean deletion:

```text
set category = ""
  on sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

To remove a property, use `clear`:

```text
clear category
  on sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

The following strict update requires the property to exist:

```text
update definition@en =
  "A revised definition"
on sense[gloss@en = "pig"]
of entry[form@tww = "mami"]
```

### 7.1 The `within` clause

#### Syntax

The `within` clause chains a PARENT component (to the right) with a child component (to the left). Several within clause can be consecutive.

Its syntax is:

```text
CHILD[SELECTOR] within PARENT[SELECTOR]
```

when consecutive within clause occurs, the first node is a child of the second, which in turn is the child of the third:

```text
CHILD[SELECTOR] within PARENT[SELECTOR]
within PARENT[SELECTOR]
```

It may appear exaclty where an `of` clause may appear. In the following command, COMMAND operate on a target component; `under IMMEDIATE_PARENT` then address the parent of this target, and `within` address the next parent:

```text
COMMAND
under IMMEDIATE_PARENT
within NEXT_PARENT
```

or, for property commands:

```text
COMMAND
on IMMEDIATE_PARENT
within NEXT_PARENT
```

For example:

```text
create note(
  type = "special",
  text@en = "Very important"
)
under sense[gloss@en = "foo"]
within entry[form@tww = "mami"]
```

`within` can coexiste and alternate with `of`. In any case, the succession of `of` and `within` always describe a chain of child/parent succession.

A *chain of `within`*, more generaly, is a sequence of (one or more) CHILD-PARENT linked by `within` without an interveaving `of`. FOr instance, the following contains two consecutive chain of within. The first contains one within the second chain contains two within:

```text
COMMAND
under IMMEDIATE_PARENT
within NEXT_PARENT
of NEXT_PARENT
within NEXT_PARENT
within NEXT_PARENT
```

#### properties allowed in a `within` selectors

The selector of a `within` clause may contains any property of the component, not only the identity properties, since a `within` clause is not in charge of uniquely addressing one component. This exclude the special or pseudo predicate that are not directly manageable: 'hn', 'has-gloss', 'index', 'ID'.

#### resolver algorithm

With a `within` clause chain, the resolver follow the following algorithm:

1. the resolver use the selector of the component selected by the last `within` clause.
   - if it match no component, 'NOT_FOUND' is raised.
   - if must match *one or more component*, the resolver keep this set of component. This is completely different from the `of` clause that must match exactly one component. 
2. the validator then considers the selector at the left of the `within` clause.
   - This selector is used to filter out the previously selected subtrees: only the subtree whose last child have a child of the corresponding kind and matching the selector are kept. Here, the the last child selected are the subtree root.
   - if no subrees are found, a 'NOT_FOUND' is raised.
3. The step 2 is repeated with other `within` clause in the clause chain. (in reverse declaration order) : the validor consider the selector at the left of this previous clause chain, and it must filter out the last child of each subtrees, i.e. the child found during the previous step. When these parents does not have the corresponding child, the corresponding subtree rooted at the begining of the within clause chain is filtered out.
4. When the last within clause is executed, exactly one subtree must be left. If, at the end, more than on subtree remains, a 'AMBIGUOUS_REFERENCE' is raised.

Here is an example. Let's focus on this construction

```text
COMMAND
under trait(value="semantic domain")
of translation(text="I shoot a pig")
of example[text="a mami jefi"]
within sense[gloss="pig"]
within entry[form="mami"]
```

- The resolver start with the last within chain, which contains:

```text
of example[text="a mami jefi"]
within sense[category="Noun"]
within entry[form="mami"]
```

- according to 1, the resolver starts with the last `within` clause: it select all entries matching `entry[form="mami"]`. If no entry match, it raises 'NOT_FOUND'. Let's say that 4 entries have this form.
- according to 2, it then move to the left of the within clause, with have a selector containig 'sense[gloss="pig"]'. It applies this filter to the previously selected, which are the entries. Let's say that two entries have a sense child with a category "Noun".
- according to 3, the step 2 is repeated with the left of this last within, i.e. the selector 'example[text="a mami jefi"]'. for the two sense selected at the end of the previous step, we look if they have an example with the given text. If one sense have this example, then the corresponding subtree rooted at an entry (the begining of the within chain) is kept. If several sense have such an example, then an 'AMBIGUOUS_REFERENCE' exception is raised. If no sense have such an example, then a 'NOT_FOUND' is raised.

#### Difference between `of` and `within`

An `of` clause identifies the immediate parent of the preceding component in a chain where each step is univoquely addressed.

```text
set definition@en = "A definition"
on sense[gloss@en = "foo"]
of entry[form@tww = "mami"]
```

A `within` clause describe a chain of parent-child relation with potentialy partial information (not sufficent to univoquely address the node) on each step, the resolver beeing in charge of finding a complete chain that satisfy all the constraint:

```text
set definition@en = "A definition"
on example[index = 1]
of sense[category = "Noun"]
within entry[form@tww = "mami"]
```


#### Unification with the selected child

When a `within` clause is used, the parent selector is evaluated together with the already selected descendant path.

For example:

```text
create example(
  text@tww = "a mami jefi"
)
under sense[gloss@en = "pig"]
within entry[form@tww = "mami"]
```

The entry is not selected solely by its form. It must also contain the sense selected by:

```text
sense[gloss@en = "pig"]
```

This is useful when several entries have the same form. The `within` clause therefore acts as a unification constraint:

```text
entry[form@tww = "mami"]
  ∋ sense[gloss@en = "pig"]
```

The parent selector does not independently select an unrelated sense elsewhere in the dictionary.

#### Cardinality

The complete constrained path must resolve to exactly one valid target for commands that require one target.

The following errors apply:

- `NOT_FOUND` — no ancestor satisfies the constraint in the `within` chain 
- `AMBIGUOUS_REFERENCE` — more than one complete ancestor/descendant path satisfies it;
- `ILLEGAL_PARENT` — the selected components cannot be related according to the component hierarchy;
- `DUPLICATE_SELECTOR` — the same ancestor constraint is specified more than once or conflicts with another selector strategy.

The ancestor is valid only if exactly one complete matching descendant path exists.

#### No implicit creation

A `within` clause is always a selector constraint. It never creates an ancestor or any intermediate component.

For example:

```text
create note(type = "special", text@en = "Very important")
under sense[gloss@en = "foo"]
within entry[form@tww = "mami"]
```

does not create the entry or the sense. If either component is absent, the command fails.

#### Blocks

within is not allowed inside block.

#### Assessment

The `within` clause solve a real problem: selecting a child component while ensuring that its containing entry is the intended homophone entry. It makes a relationship such as:

```text
entry[form = "mami"] containing sense[gloss = "foo"]
```

expressible without silently using a child selector to disambiguate an ambiguous entry.

I recommend these design decisions:

1. Use `of` for an explicit, immediate parent chain.
2. Use `within` for a parent constraint
3. Define `within` as an existential child constraint: the praent must contain at least one matching descendant.
4. Resolve the complete constrained path before executing the command.
5. Never let `within` create missing ancestors.
6. multiple matching `within` chain produce `AMBIGUOUS_REFERENCE`.

## 8. Commands operating on components

### 8.1 Create

The `create` command always creates a new component and does not reinterpret the
properties as a component selector.

Properties required for the creation of a component are defined in the column "required" in the property table under "4. Lift properties"

Here are examples of `create` commands that create new components:

```text
create note(type = "sociolinguistics", text@en = "A sociolinguistic notes")
  under entry[form@tww = "mami"]
```

```text
create field(type = "borrowing", text@en = "yes")
  [gloss@en = "pig"]
  of entry[form@tpi = "pik"]
```

```text
create trait(type = "CVpattern", value = "CVC")
  under entry[form@tpi = "pik"]
```

Creation fails with `DUPLICATE_COMPONENT` if the declared component identity
already exists. For instance, the following will form if there is already a
sense with the same qualified gloss under the same entry.

```text
create sense(gloss@en = "pig")
  under entry[form@tww = "mami"]
```

The duplicate detection works with the properties enumerated in the table in the
section "5.3 identity properties". It means that, under the same parent, two
senses are identical if they have the same qualified gloss; two examples are
identical if they have the same qualified text, two variants are identical if
they have the same type and target.

The same multitext identity property cannot be referred twice with a different
qualifier (lang). This is intended to prevent accidental creation of duplicate. It means that the following will raise a 'DUPLICATE_PROPERTY'
error:

```
upsert sense(
  gloss@en = "pig",
  gloss@fr = "porc"
)
```

#### The special case of entry

For entry only, a component with the same value for the identity property 'form' may exist; the following should
work, even if an entry already exists with the same qualified form:

```text
create entry(form@tww = "mami")
```

#### to POSITION clause

The create command allows an optional "to POSITION" clause, which specifies the
index at which to insert the component under the parent list of
same-type-components.

Allowed positions are:

```text
at beginning
at end
at index <n>
```

example:


```text
create sense(gloss@en = "pig")
  under entry[form@tww = "mami"]
  at beginning
```

- The index is 1-based. 
- The valid insertion range is 1..count+1. If the index given is < 1 or greater than the number of already existing same-type components + 1, an error 'INDEX_OUT_OF_BOUNDS' is raised.

### 8.2 Upsert and ensure

`upsert` is idempotent with respect to the identity property:

```text
upsert sense(gloss@en = "pig")
  under entry[form@tww = "mami", hn="1"]
```

- It creates the sense if not matching sense exists and resolves the existing
  sense otherwise.
- A matching sense is a sense having the same values for his identity property(ies)
- an upsert command fails if does not have as argument all the identity property required for this component type (qualified text for example, type + target for variant, etc.)
- `upsert` cannot make reference to ID since ID cannot be set
- `upsert` cannot make reference to the `hn` and `has-gloss` properties which cannot be set
-  with an upsert command, the identity property is either unchanged if
  a component match, or set on a newly created component if no component match
- Once the component created or selected, all the other properties will be
  created or updated as needed (i.e. with the "set" semantics). In the following example, the category property
  will be either created (if it does not exist) or updated to "Noun" (if it does
  exist).

```
upsert sense(
  gloss@en = "pig",
  category = "Noun"
)
under entry[form@tww = "mami"]
```

In order to resolve the identity of the component, the upsert command use the
identity properties mentioned in section "5.3 identity properties".

As with create, the same multitext property cannot be referred twice, even with a different qualifier (lang).

entry cannot be upserted since there is no natural property that provides a unique identifier for the entry. upserting an entry failed with 'ENTRY_CANNOT_BE_UPSERTED'.

`ensure` is equivalent to an idempotent component upsert without changing
existing properties. Ensure can refers only to property participating in the
component identity, as defined by the component's identity properties listed in
"5.1 Component identity". If a property that does not belong to the set of identity properties for this component is present, an 'NON_IDENTITY_PROPERTY' error is raised. It means that the following is rejected as 'NON_IDENTITY_PROPERTY', since category is not a sense component identity property:

```
ensure sense(
  gloss@en = "pig",
  category = "Noun"
)
under entry[form@tww = "mami"]
```

entry cannot be ensured since there is no natural property that provides a unique identifier for the entry. Ensuring an entry failed with 'ENTRY_CANNOT_BE_ENSURED'.

### 8.4 Delete a component

Deleting a component also deletes its descendants. For instance, the following
command will delete the sense as well as all the component descendants:

```text
delete sense[gloss@en = "pig"]
  under entry[form@tww = "mami"]
```

Deleting an entry requires no parent clause:

```text
delete entry[form@tww = "mami"]
```

Note that the previous command must raise an error if the entry does not exist ('NOT_FOUND') or if several entries match the selector ('AMBIGUOUS_REFERENCE').

### 8.5 Moving components

Allowed positions are:

```text
at beginning
at end
at index <n>
```

- The index is 1-based. 

1/ When the move command has no under clause, the destination is the same parent: the component is moved in its set of siblings.

```text
move example[index = 1]
  of sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
  at end
```

When moving within the same parent, the index corresponds to a position after the moved component has been removed. The following moves the second element to the third position:
  
```text
move example[index = 2]
  of sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
  at index 3
```

2/ When the move command has an under clause, the destination is the component targeted by the under clause:

```text
move example[index = 1]
  of sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
  under sense[gloss@en = "large_animal"]
  of entry[form@tww = "mami"]
  at end
```

A move fails with a structured error if:

- if the moved component is an entry, it fails with 'ENTRY_CANNOT_MOVE'
- moving to the current position raises 'MOVING_TO_CURRENT_POSITION'
- the destination cannot contain the source component type ('INCOMPATIBLE_DESTINATION')
- the move would make a component its own ancestor ('SELF_ANCESTOR');
- the requested position is outside the destination's valid range (an error 'INDEX_OUT_OF_BOUNDS' is raised):
  - lower than 1 
  - greater than the number of components in the destination + 1 for a different-parent move
  - greater than the number of components in the destination for a same-parent move

The selectors will raise exception if:

- the source does not exist 
- the destination does not exist;

The source and destination are resolved before the move is applied.

Moving changes position and parentage, never identity.

## 9. Commands operating on properties

### 9.1 Set

`set` is the normal property assignment operation. It creates the target if
absent and replaces its value if present:

```text
set category = "Noun"
  on sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

Or:

```text
set text@en =
  "A sociolinguistic note"
on note(type = "sociolinguistic")
of sense[gloss@en = "pig"]
of entry[form@tww = "mami"]
```

`set` never creates a missing parent component. Parent creation must be
explicitly requested with `ensure` or performed by a component `upsert`.

`set` cannot be used without lang specification on a property with lang (a
multitext). There is always a default language (according to the rules given
below), and therefore the language can then be omitted, it does not means
however that set will operate without language qualification.

For instance, the following `set` operation will assign a value in the current
default language (say, "en"), it will not assign the definition to a generic,
not language-specific definition:

```text
set definition = "A definition"
on sense[gloss@en = "pig"]
of entry[form@tww = "mami"]
```

The previous command will set the value of the qualified value `definition@en`,
it will not change any other qualified value existing on that property (say,
`definition@fr`).

### 9.2 Update

Update requires an existing target:

```text
update definition@en =
  "A four-legged animal"
  on sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

or :

```text
update text@en = "A revised note"
on note(type="review")
of sense[gloss@en = "pig"]
of entry[form@tww = "mami"]
```

If the property is absent, the error is `PROPERTY_NOT_FOUND`.

The same rule as above (under "set") regarding language applies: lang can be
implicit.

In the following example, the default language is used for addressing a specific
definition sub-entry:

```text
update definition =
  "A four-legged animal"
  on sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

### 9.3 Clear

#### Clear for non-identity properties

`clear` removes a property values while retaining the parent component:

```text
clear definition@en
  on sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

For a multitext property, omitting the language removes all language values:

```text
clear definition
  on sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

`clear` is the then only command that may intentionally target multiple
qualified property values. Component selectors and `set`/`update` targets must
resolve to exactly one qualified value. An unqualified `clear` may then remove
several matching values, however it never removes the parent component. 

- If a qualified property value doesn't exist with the given language, the error
is `QUALIFIED_PROPERTY_NOT_FOUND`.
- when the multitext properties is not an identity property,  if the clear command refer to the unqualified property and that the property has no sub-entries, clear fails with 'EMPTY_MULTITEXT'.

`clear` on scalar properties remove the property value:

```
clear url
on illustration[...]
```

```text
clear category
  on sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

If a scalar property doesn't exist (is not set) on the given component, the
error is `PROPERTY_NOT_SET`.

#### Clear for required scalar properties

Scalar properties listed in the component identity properties cannot be cleared. Other scalar properties can be cleared.

#### Clear for required Multitext properties

Clear command can also be called on a required property.

On the case of a required multitext property, clear cannot remove the last
value. A qualified value of a required multitext property may be cleared if at
least one qualified value remains; the complete property may not be cleared. It
means that:

1/ the following command will raises a `CANNOT_CLEAR_REQUIRED_MULTITEXT` error if the `tww` form
is the only sub-entry: 

```text
clear form@tww
  on entry[form@tww = "mami"]
```

It will successfully remove the value for the `tww` form if their is also a form
in another language.

2/ with a multitext property that is required on a component, the clear
command cannot be used without lang selector, as it will remove the value for
all languages and leave the Multitext property empty. The following two commands
raises `CANNOT_CLEAR_REQUIRED_MULTITEXT`:

```text
clear form
  on entry[form@tww = "mami"]
```

and :

```text
clear gloss
  on sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

These commands raise `CANNOT_CLEAR_REQUIRED_MULTITEXT`.

It means that clear command makes not use of the implicit language.

## 10. References to other components

Properties of datatype "reference" store the ID of the referenced component.

1/ Within command, the value is set by referring to the target component, with selector:

```text
set target = entry[form@tww = "memi"]
on relation[type = "synonym"]
of entry[form@tww = "mami"]
```

A relation to a sense may be written:

```text
set target =
  sense[gloss@en = "pig"]
  of entry[form@tww = "memi"]
on relation[type = "associated-sense"]
of entry[form@tww = "mami"]
```

2/ within selectors, the string value of the target ID can be used directly:

```text
variant[type="dialectal", target="pig-44"]
```

- the stored value is the internal ID of the target.
- The validator verifies that the target exists and has an allowed component
  type. `NO_SUCH_TARGET` is raised if the target cannot be resolved.
- The validator verifies that the target is an entry or a sense. Otherwise,
  `INVALID_TARGET` is raised.
- The validator verifies that the target is not the parent of the source
  component itself.
- The validator verifies that not two identical Relation or Variant exist on the
  same parent component (i.e. with the same type and the same
  target). 

These rules regarding reference apply to every target property on:

- variants;
- relations;
- reversals.

When an identical component with the same type and the same target already exist, the 
the creation of a component Variant, Relation or Reversal should fail with 'DUPLICATE_REFERENCE'

## 11. Block syntax

Blocks provide convenient syntax for related operations while preserving explicit component construction.

A curly brace block is a syntactic construct that groups related commands together, and anchor them to a common parent

A block is made of 
- the "block header" which is given before the curly brace.
  - the block header is either :
    - a component with selector
    - a command create / upsert with a component with selector 
- the "block body" which is given inside the curly brace. The block body contains either 
  -  another block : block can be nested
  -  commands. Those commands have not under or on clause, since the parent is given by the block header.

The next example has a block header that contains a component with selector. It will fail with 'AMBIGUOUS_REFERENCE' or 'NOT_FOUND' if the selector failed.

```text
entry[form@tww = "mami"] {
  create sense(gloss@en = "pig") {
    set definition@en = "A four-legged terrestrial animal"
    set category = "Noun"

    create example(text@tww = "a mami jefi") {
      create translation(
          type="free",
          text@en = "I shot a pig")
    }
  }
}
```

In the next example, the outer block header is a upsert command:

```text
create entry(form@tww = "mami") {
  create sense(gloss@en = "pig") {
    set definition@en = "A four-legged terrestrial animal"
    set category = "Noun"
    create example(text@tww = "a mami jefi") 
  }
}
```

An upsert, ensure or update cannot be in the scope of a `create` command, be it at direct upper level or indirectly related, since the . For instance, in the following command, un upsert command is illegaly in the scope of a create command in the direct upper level:

```text
create entry(form@tww = "mami") {
  upsert sense(gloss@en = "pig") {
    set definition@en = "A four-legged terrestrial animal"
    set category = "Noun"
    create example(text@tww = "a mami jefi") 
  }
}
```

Such situation should raise 'CANNOT_EXPECT_A


Block semantics are:

1. Resolve or create the component in the block header (with required-properties validation)
2. Make that component the explicit parent of child commands.
3. Execute child commands in source order.
4. Make preceding changes visible to subsequent commands in the same block.
5. Roll back the entire block if any child command fails.

Inside a component block, a property command that omits `under` uses the current
block component as its parent. An explicit `under` clause overrides this
implicit parent. Outside a block, `under` is mandatory for property commands.

Blocks are not loops, conditionals, or variables. They only provide lexical
nesting and an atomic transaction boundary.

# Annex 1: Normative table

## 1. Property availability table

**Qualifier notation**

- `O` — object-language qualifier required or defaultable, for example `form@tww`.
- `M` — meta-language qualifier required or defaultable, for example `gloss@en`.
- `—` — no language qualifier.
- A multitext property may contain several language-qualified values, but each language may occur at most once.

| Component | Property | Datatype | Qualifier | Required at creation | Natural identity |
|---|---|---|---|---:|---:|
| Entry | `form` | multitext | O | yes, at least one | no |
| Entry | `morpheme` | string | — | no | no |
| Sense | `gloss` | multitext | M | yes, at least one | yes: one qualified value |
| Sense | `definition` | multitext | M | no | no |
| Sense | `category` | string | — | no | no |
| Example | `text` | multitext | O | yes, at least one | yes: one qualified value |
| Etymology | `form` | multitext | O | yes, at least one | part of `type + form` |
| Etymology | `gloss` | multitext | M | no | no |
| Etymology | `source` | multitext | M | no | no |
| Etymology | `type` | string | — | yes | part of `type + form` |
| Variant | `type` | string | — | yes | part of `type + target` |
| Variant | `target` | reference | — | yes | part of `type + target` |
| Relation | `type` | string | — | yes | part of `type + target` |
| Relation | `target` | reference | — | yes | part of `type + target` |
| Reversal | `form` | multitext | O | yes, at least one | no |
| Reversal | `type` | string | — | yes | part of `type + target` |
| Reversal | `target` | reference | — | yes | part of `type + target` |
| Illustration | `url` | URL | — | yes | yes |
| Illustration | `label` | multitext | M | no | no |
| Media | `url` | URL | — | yes | yes |
| Media | `label` | multitext | M | no | no |
| Pronunciation | `transcription` | multitext | O | yes, at least one | yes: one qualified value |
| Trait | `type` | string | — | yes | yes |
| Trait | `value` | string | — | yes | no |
| Annotation | `type` | string | — | yes | yes |
| Annotation | `value` | string | — | yes | no |
| Annotation | `comment` | multitext | M | no | no |
| Annotation | `when` | string | — | no | no |
| Annotation | `who` | string | — | no | no |
| Note | `type` | string | — | yes | yes |
| Note | `text` | multitext | M | yes, at least one | no |
| Field | `type` | string | — | yes | yes |
| Field | `text` | multitext | M | yes, at least one | no |
| Translation | `type` | string | — | yes | yes |
| Translation | `text` | multitext | M | yes, at least one | no |

## 2. Command applicability table

The following table can be normative.

| Property kind | `create` | `upsert` | `ensure` | selector | `set` | `update` | `clear` |
|---|---|---|---|---|---|---|---|
| Required identity property | required | required | required | allowed | allowed, subject to uniqueness | allowed, subject to uniqueness | only if the component remains valid |
| Optional natural property | allowed | allowed; uses set semantics | forbidden | allowed only if part of identity | allowed | allowed if present | allowed |
| Required non-identity property | required | allowed | forbidden | not allowed unless explicitly defined | allowed | allowed | only if another value remains |
| Optional scalar property | allowed | allowed | forbidden | allowed only if declared identity | allowed | allowed if present | allowed |
| Optional multitext property | one or more qualifiers | one or more qualifiers | forbidden | one qualifier only if identity | one qualifier | one qualifier | one qualifier or all qualifiers |
| System-managed ID | forbidden | forbidden | forbidden | allowed where IDs are supported | forbidden | forbidden | forbidden |
| `hn` | forbidden | forbidden as initializer | forbidden | allowed only with qualified `form` | not as a property target | not as a property target | forbidden |
| `has-gloss` | forbidden | forbidden as initializer | forbidden | entry selector only | forbidden | forbidden | forbidden |
| `index` | forbidden | forbidden | forbidden | selector only | forbidden | forbidden | forbidden |

Here `allowed` does not mean that every property is valid on every component. Availability is first determined by the property availability table.

## 3. Qualifier rules

The language should define three distinct forms.

### Qualified assignment

For `create`, `upsert`, `set`, and `update`:

```text
set gloss@en = "pig"
set gloss = "pig"       # equivalent to gloss@<default-meta-language>
```

An omitted qualifier uses the applicable default language.

The following should be invalid:

```text
set category@en = "noun"
set target@en = "entry-42"
```

### Qualified selection

For selectors, an identity multitext property must use exactly one qualified value:

```text
sense[gloss@en = "pig"]
example[text@tww = "The dog ran"]
```

An unqualified multitext identity property should be rejected because it does not identify one language value:

```text
sense[gloss = "pig"]   # invalid selector
```

Alternatively, the language default could be applied, but this must be stated explicitly. For deterministic selectors, rejecting it is safer.

### Clearing

`clear` should have separate semantics:

```text
clear definition@en   # remove one language value
clear definition      # remove all language values
```

The default language must **not** be applied to an unqualified `clear`.

For a required multitext property:

```text
clear gloss@en
```

is legal only if another qualified `gloss` value remains. This makes the rule precise:

> A required multitext property may lose individual language values, but may never become empty.

## 4. Creation and initializer rules

For `create` and the creation branch of `upsert`:

1. Every required property must be present after initialization.
2. A required multitext property needs at least one qualified value.
3. Multiple different language qualifiers for the same multitext property are allowed:

   ```text
   create example(
     text@tww = "mami",
     text@tpi = "pik"
   )
   ```

4. Repeating the same qualified property is invalid:

   ```text
   create example(
     text@tww = "mami",
     text@tww = "different"
   )
   ```

   This should raise `DUPLICATE_PROPERTY`.
5. `ensure` accepts only natural identity properties.
6. `upsert` requires all natural identity properties and applies all other initializers with `set` semantics.
7. `create` checks duplicate natural identity after all initializers are resolved.

## 5. Suggested system-managed metadata

These values should not appear in the ordinary property table:

| Name | Kind | Allowed use |
|---|---|---|
| `id` | system-managed identifier | selector and references only |
| `hn` | computed entry-disambiguation key | entry selector only, with qualified `form` |
| `has-gloss@M` | selector predicate | entry selector only |
| `index` | positional selector | non-entry selector only |

This separation avoids treating `hn`, `index`, and `has-gloss` as mutable LIFT properties and removes several current contradictions.

# Annex 2: API to a lift dictionary for Lift-DSL implementation

A Lift-DSL library needs to connect to a lift dictionary, in order to query, create object, get languages, etc.

A liftapi exist, that model a lift dictionary and offer query and creation methods.

In order to generate an implementation of this Lift-DSL specification into a liftdsl java library, the following API entry points into an existing liftapi can be used:

- The liftdsl library will be provided with a LiftDictionary (in package fr.cnrs.lacito.liftapi.LiftDictionary) instance.

This instance offers the following method:

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
  - containsLang(String lang) will return a boolean indicating within a sub-entry exist for this lang.
  - getForm(String lang) will give the form (sub-entry) (or throw an exception if no sub-entry exist for the lang) for this lang for this multitext property
  - removeForm(String lang) will remove the form (sub-entry) (or throw an exception if no sub-entry exist for the lang) for this lang for this multitext property
  - getLangs() return a Set<String> listing the lang actually existing in the sub-entries.
- for each component child type, the following methods exist:
  - add<Component>(component) for example, Sense class offers addExample(LiftExample example)
  - get<Component>s for example, Sense class offers getExamples(), wich return a List<LiftExample>
  - <components>Property(), for example Sense class offers examplesProperty() that return a ListProperty in javafx.beans.property package, that can be iterated.

### Component creation

the method getComponentBuilder() return an instance of DictionaryComponentBuilderFactory (in package fr.cnrs.lacito.liftapi.builder)
  - this instance contains method for creating builder with fluent API for each component type: for instance the method entry() will create an EntryBuilder instance, sense() will create a SenseBuilder.
  - All builder instances are located in the same package fr.cnrs.lacito.liftapi.builder.
  - all builders allow to set properties with the with<Property> method:
    - for instance, senseBuilder.withCategory(String category) set the category
    - for multitext, two arguments are needed: senseBuilder.withGlos(String lang, String gloss) in order to add a qualified gloss.
  - all builders allow to register a child component with add<ComponentType> method:
    - for instance, entryBuilder.addSense(LiftSense senseBuilder.build()) will add a sense
  - all builder create the final object with build()
    - for instance, senseBuilder.build() create a Sense object.

### Lookup

The LiftDictionary class offers getEntryByForm(String lang, String form), that will return the List<LiftEntry> of entries having the corresponding form for the corresponding languages. Iteration on Sense, Note, Field, Variant, etc. can then be done from the LiftEntry objects.

- The LiftDictionary class also offers a getLiftDictionaryRegistry() methods that return a LiftDictionaryRegistry instance (in the same package). This class offers the following methods:
  - getEntryOrSenseByLiftId(String ID) return an AbstractIdentifiable, i.e. a superclass of LiftSense and LiftObject, or throw an exception if no entry or sense component has this ID.
  - ObservableList<LiftEntry> getEntries(): list of all entries

### Removing component

The LiftDictionaryRegistry instance returned by LiftDictionary  .getLiftDictionaryRegistry() also offers :

 - removeFromDictionary(AbstractLiftRoot node) : AbstractLiftRoot (in package fr.cnrs.lacito.liftapi.model) is the superclass of all component type. This methods will remove a component, cut the link parent/child (so that the node will not be its parent child's list, for example a sense removed will not be seen anymore from its parent LiftEntry instance.)

# Annex 3 : Concise surface syntax

This section describes an alternate concise syntax for practical purposes.

This concise syntax allows only for a subset of the reference syntax described above.

Its underlying semantic is exactly the same.

This concise syntax must be described in a separated formal (BNF) grammar and matched to the reference syntax after parsing.

This surface syntax is line-oriented: a command is on a single line.

It is intended for lexicographers who frequently create entries, senses, examples, and their properties in a text document containing both commands and ordinary prose. The parser must then distinguish LIFT-DSL concise command line from ordinary prose paragraphs.

Every valid surface command MUST have an unambiguous expansion into one reference-language command. All the semantics principles of the reference language MUST be followed. The difference between LIFT-DSL and LIFT-Short-DSL are surface syntax differences only.

Every unspecified rules or semantic constraint in this concise language specification is inherited from the reference language specification. For instance
- required properties on initializers
- unification and resolution rules for within clause
- a within chain as a whole must select one match, but some of its components may select several matches.

## 1. Design goals

The surface language should:

1. Be concise for common lexicographic operations.
2. Be normally expressible on one physical line.
3. Be easy to recognize in a mixed text document.
4. Follow the reference language's semantics exactly.
5. Never silently choose between several matching entries or senses.
6. Distinguish selection from creation.
7. Use LIFT-inspired one-letter component, property and attribute codes.

A surface command is recognized only when a first non-whitespace character at the beginning of a line is one of:

```text
c
d
m
p
e
u
s
l
```

followed by a whitespace character and a /

For reliable extraction from ordinary prose, commands SHOULD begin at the beginning of a paragraph or line. A command continues until the end of that physical line.

The surface language does not support multiline commands. A long or complex operation must use the reference language instead.

## 2. Single-letter designations of commands, components and properties

Commands, components and properties can be abbreviated by the same code (for instance e = ensure command and entry component). This is not an issue since the context always allow to disambiguate whether we are referring to a command, a component or a property.

### 2.1. Command short codes

The commands are the same as in LIFT-DSL language. They are designed by a single letter, called "short code" in the following table:

| short code | Meaning | Reference-language operation | Operate on |
|---|---|---|---|
| `c` | Create | `create` | Component |
| `d` | Delete | `delete` | Component |
| `m` | Move | `move` | Component |
| `p` | Upsert | `upsert` | Component |
| `e` | Ensure | `ensure` | Component |
| `u` | Update | `update` | Property |
| `s` | Set | `set` | Property |
| `l` | Clear | `clear` | Property |

The short code must be followed by whitespace.

The code for upsert is `p` because `u` is reserved for update.

### 2.2. Component short codes

The components are the same as in LIFT-DSL language. They are referred to by a single letter.  The letters are:

| Component | Concise LIFT-DSL code |
|---|---|
| Entry | `e` |
| Sense | `s` |
| Example | `x` |
| Etymology | `y` |
| Variant | `v` |
| Relation | `r` |
| Illustration | `i` |
| Media | `m` |
| Pronunciation | `p` |
| Reversal | `l` |
| Trait | `t` |
| Annotation | `a` |
| Note | `n` |
| Field | `f` |
| Translation | `o` |

### 2.3. Properties short codes

The properties on components are the same as in LIFT-DSL language, they are addressed with a single letter code:

| Property | Concise LIFT-DSL |
|---:|---|
| `form` | f |
| `morpheme` | m |
| `definition` | d |
| `gloss` | g |
| `category` | c |
| `text` | t |
| `source` | s |
| `target` | a |
| `url` | u |
| `label` | l |
| `transcription` | r |
| `type` | y |
| `value` | v |
| `comment` | o |
| `when` | w |
| `who` | h |

## 3. Command syntax

Here are the general command syntax for each command.

1/ For components:

Create: the final component initializer is created under the last selected path component.

```text
c <path>/ <component_type>(<initizializer>)
```

Upsert: the final upserted initializer is matched or created under the last selected path component:

```
p <path>/ <component_type>(<initializers>)
```

Delete: the component of the last step of the path is deleted:

```
d <path>
```

Move: the last step of the path is moved at a different position or, if a destination-path is expressed, under another parent (the last step of the destination-path) at the specified position:

```
m <source-path> [<destination-path>] at <position>
```

Ensure:

```
e <path> <component_type>(initializers)
```

2/ For properties: the property is between parenthesis, with only its name (for clear) or its name and its value separated by "=" (for set and update)).

Set:

```text
s <path> (<property> = <value>)
```

Update

```text
u <path> (<property> = <value>)
```

Clear:

```text
l <path> (<property>)
```

Example:

1/ Create a sense with gloss "pig" (in default meta language) under the existing entry  with form "mami" (in default object language) :

```text
c /e[f="mami"] s(gloss="pig") 
```

2/ Delete an existing sense with gloss "pig" (in default meta language) under the existing entry  with form "mami" (in default object language) :

```text
d /e[f="mami"] s[g="pig"]
```

3/ Update the property "category" of a sense:

```text
u /e[f="mami"]/s[g="pig"] (c = "Verb")
```

4/ Clear the property "category" of a sense:

```text
c /e[f="mami"]/s[g="pig"] (c)
```

5/ Set the property "category" of a sense:

```text
s /e[f="mami"]/s[g="pig"] (c = "Noun")
```

## 4 Path specification

A path starts with a slash and is a sequence of slash-separated steps.

Each step is made of a letter indicating the component type and one or two selectors between square brackets.

The semantic of the relation between the steps is this of the `within` keyword in the reference language (the direct target of a create, upsert, delete, ensure command is always linked with its parent with `under`, as in the reference language). The steps in the path are linked with 'within' semantic.

This means that the following:

```
/e[f="mami"]/s[g="pig"]
```

Means, in the reference language:

```
s[g="pig"]
within  e[f="mami"]
```

and that, in that example, the sense can silently choose between one of several entries with a form "mami", as in the reference specification.



And with a create command, where the path select a sense and the initializers create an example under it:

```
create /e[f="mami"]/s[g="pig"] x(t="A mami jefi")
```

translates into:

```
create example(text="A mami jefi")
under sense[gloss="pig"]
within example[form="mami"]
```

the pseudo property ordinal in the reference syntax `[index=2]` is expressed with
a single integer in the concise syntax:

```text
/e[f="mami"]/s[g="pig"]/x[1]
```

is equivalent to:

```text
example[index = 1]
  within sense[gloss@en = "pig"]
  within entry[form@tww = "mami"]
```

As with the reference syntax, the index selector cannot cooccur with any other selectors in the square brackets.

## Embedding initializer

Embedding initializer is allowed for creating child on the fly.

For instance, in:

```
c /e["mami"]/s["pig"] x(t="a mami jefi", o(t="I shot a pig", y="literal"))
```

the embedded initializer "(t="I shot a pig", y="literal")" is attached to `o`. The `o` here is then a *component* letter, since only components have initializers. `o` means then translation. The component created by the embedded initializer is then added to its parent. 

Embedded initializers necessarily translate into several commands:

```
create example(text="a mami jefi")
  under sense[gloss="pig"]
  within entry[form="mami"]

create translation(text="I shot a pig", type="literal")
  under example[text="a mami jefi"]
  within sense[gloss="pig"]
  within entry[form="mami"]
```

Recall that two examples cannot have the same text under the same sense (example text is its natural identity), so selecting by value is not ambiguous.

Embedded creation rules also includes:

- Embedded child creation is atomic with the parent command;
- an embedded creation use the semantic of create (it means that an error is raised if the child already exists)
- nested embedded initializers are allowed

## Command syntax details

### Create, upsert, ensure: commands with initializers

Command with initializer are identical to the initializer + `under` in the reference language.

```text
c /e[f="mami"] s(gloss="pig") 
```

Translate into

```
create sense(gloss="pig") under
entry[form="mami"]
```

The path identifies the parent of the component created.

As in reference language :

- ensure (e) does not allow non-identity initialiers (and raise an exception)
- upsert (p) requires all identity properties
- upsert and ensure are not applicable to entry (the concise syntax cannot be more permissive than the reference syntax)

### Delete

Delete command deletes the last step of the path.

This command:

```text
d /e[f="mami"] s[g="pig"]
```

Translate in:

```text
delete sense[gloss="pig"] under
  entry[form="mami"]
```

This command:

```text
d /e[f="mami"]/s[g="pig"] x[t="a mami jefi"]
```

Translate in:

```text
delete example[text="a mami jefi"]
  under sense[gloss="pig"]
  within entry[form="mami"]
```

As stated earlier, the step in the path are linked with 'within' semantic.

### Move

```text
m /e[f="mami"]/s[g="pig"] at index 1
```

Translate in:

```text
move sense[gloss="pig"] within entry[form="mami"] at index 1
```

when move has a second path, it is equivalent with a move with an under clause in the referent syntax: it moves towards another parent.

Then, the fllowing: 

```text
m /e[f="mami"]/s[g="pig"]/x[1] /e[f="mami"]/s[g="large animal"] at end
```

is equivalent to:

```text
move example[index = 1]
  within sense[gloss@en = "pig"]
  within entry[form@tww = "mami"]
  under sense[gloss@en = "large_animal"]
  within entry[form@tww = "mami"]
  at end
```

`beginning`, `end`, and `index n` have exactly the reference-language semantics,

### Set

```text
s /e[f="mami"]/s[g="pig"] (c = "Noun")
```

Is equivalent to:

```text
set category = "Noun" 
  on sense[gloss="pig"]
  within entry[form="mami"]
```

### Update

```text
u /e[f="mami"]/s[g="pig"] (c = "Verb")
```

is equivalent to

```text
update category = "Verb" 
  on sense[gloss="pig"]
  within entry[form="mami"]
```

### Clear

```text
l /e[f="mami"]/s[g="pig"] (c)
```

is equivalent to

```text
clear category
  on sense[gloss="pig"]
  within entry[form="mami"]
```



## Simplified path and initializers

Some more shortcut are :

1/ if the first step of a path is a single string between quotes, then it is the form of an entry:

```
/"mami"
```

is then equals to

```
/e[f="mami"]
```

which is in turn equals to (in reference syntax):

```
entry[form="mami"]
```

2/ if the second step of a path is a single string between quotes, then it is the gloss of a sense:

```
/"mami"/"pig"
```

is then equals to

```
/e[f="mami"]/s[g="pig"]
```

which is in turn equals to (in reference syntax):

```
sense[gloss="pig"]
within  entry[form="mami"]
```

3/ The following initializers can drop the field name under the following conditions:

- if an entry initializer has no field name and equal sign before the assigned string, then it is the entry form

```
c e("mami")
```

Is equivalent to:

```
c e(f="mami")
```

i.e., in reference syntax:

```
create entry(form="mami")
```
- if a sense initializer has no field name and equal sign before the assigned string, then it is the sense gloss:

Then the following:

```
p /"mami" s("pig")
```

equals:

```
p /e[f="mami"] s(g="pig")
```

which translates, in reference syntax, has:

```
upsert sense(gloss="pig")
under entry[form="mami"]
```

- if an example initializer has a string without field and then a translation initializer, then the first string is the text ; if a translation initializer has two bare strings, then it is the text and type in that order:

Then the following:

```
c /"mami"/"pig" x("a mami jefi", t("I shot a pig", "literal"))
```

equals:

```
c /"mami"/"pig" x(t="a mami jefi", o(t="I shot a pig", y="literal"))
```

which translates, in reference syntax, as:

```
create example(text="a mami jefi")
  under sense[gloss="pig"]
  within entry[form="mami"]

create translation(text="I shot a pig", type="literal")
  under example[text="a mami jefi"]
  within sense(gloss="pig")
  within entry[form="mami"]
```

## An idiosyncratic construct

Since entry cannot be upserted and ensured, it is uneasy to create new sense on existing entry. The following upsert construct, without constructor after the path,
means
- check if an entry with form "mami", having a sense with gloss "pig", exist;
- if yes, do nothing
- if no (i.e. if no "mami" entry exist, or if one or several entries exist but withoug the sense "pig") :
  - create a new entry "mami" and create a new sense "pig" on it.

```
u /"mami"/"pig"
```

## A final example

Using implicit field name and embedded initializers, the following 

```
c e("mami", s("pig", x("A mami jefi", o(t="I shot a pig", y="literal")))
```

Should be translated as:

```
create entry(form="mami")

create sense(gloss="pig") under entry[form="mami"]

create example(tex=t"A mami jefi")
  under sense[gloss="pig"]
  within entry[form="mami"]

create translation(text="I shot a pig", type="literal")
  under example[tex=t"A mami jefi"]
  within sense[gloss="pig"]
  within entry[form="mami"]

```
