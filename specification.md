
# LiftPatch: a Mutation Language (DSL) for the Lift datamodel

This document defines a command DSL, called *LiftPatch*, for creating, updating, deleting, upserting, and moving components and properties in a dictionary based on the LIFT data model.

The Lift data model defines the structure of descriptive linguistics dictionary, made of entry, sense, example, trait, note, field, relation, etc.

The *LiftPatch* language is not a serialization format: it is a mutation command language for updating the content. The language preserves the LIFT component hierarchy while making component identity, parentage, property availability, creation, selection, and mutation semantics explicit.

A LIFT dictionary is a tree-like structure; it contains *components*, such as
entry, sense, or example, which are nodes in the tree, and *properties* that
are attached to the nodes and have datatypes such as the form of an entry, the
gloss and the definition of a sense, etc.

## 1. Design principles

1. Every operation has an explicit verb: `create`, `ensure`, `set`, `upsert`, `update`, `clear`, `delete`, or `move`.
2. Parentheses describe a component being created and initialized with natural identity properties.
3. Square brackets select an existing component.
4. Multiple matches of a component during lookup are always errors (except in `within` clauses where intermediate non-unique matches are permitted). The language never silently selects the first match.
5. `set` creates or replaces a property value.
6. `update` replaces an existing property and never creates it.
7. `clear` removes property values without removing the parent component.
8. `create` creates a new structural component with its mandatory natural property or properties.
9. `delete` removes structural components.
10. `move` moves a structural component either to another position among its siblings or under another parent.
11. `upsert` creates a missing component and applies its initializers, or resolves an existing component.
12. `ensure` checks whether a component exists and fails otherwise.
13. A command or atomic block either succeeds completely or has no effect.

### The two liftPatch language syntax

The liftPatch language comes with two surface syntaxes. The two surface
syntaxes have exactly the same semantics. They differ only in surface syntax:

1/ The first syntax, the *LiftPatch reference syntax* (short:
*LiftPatchRef*).

It is a verbose and explicit language.

LiftPatch reference commands are contained in a script file, "Lift patch reference script". This file contains only:

- commands that start on their own line with one of the eight verbs (set, upsert, update, clear, delete, ensure, create, move).
- comment: line starting with `#`

2/ The second syntax, the *LiftPatch short language* (short: *LiftPatchShort*) is more concise.

It is intended for lexicographers expressing lexical information to be ingested in a dictionary.

The LiftPatchShort language is expressed on a single line. It can be mixed with other content and non-LiftPatchShort commands in a file. Only lines starting with one of the command verbs (or its single-letter abbreviation), followed by whitespace, are LiftPatchShort command lines.

### Structure of this document

- Part 1 describes the LIFT dictionary data model and the semantics of the LiftPatch language
- Part 2 describes the LiftPatch reference syntax
- Part 3 describes the LiftPatchShort surface syntax

# Part 1. Lift Dictionary and LiftPatch DSL Language semantics

## 2 LIFT Dictionary

A lift dictionary is an ordered list of Entry components.

Since a lift dictionary is not a monolingual dictionary, it also has:

- an ordered list of object languages, i.e. one or more languages that are described in the dictionary. These are, for instance, the languages represented in the entry or in the example. The list cannot be empty.
- an ordered list of meta languages, i.e. one or more languages that are used to describe the object language. These are, for instance, the languages used in the gloss, the definition, or the translation of the example. The list cannot be empty.

## 2.1. Default languages

At any moment, there is always a default meta language and a default object language that the command can use if a required language is not specified.

The resolution is as follows:

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

The two following commands allow the default language to be set from a Lift-DSL script:

```text
language-default object = "tww"
language-default meta = "en"
```

New languages cannot be declared that way. A meta (resp. object) language name
referred to by this command must exist in the dictionary's meta (respectively, object) language list. `NOT_SUCH_META_LANGUAGE` (resp. `NOT_SUCH_OBJECT_LANGUAGE`) should be raised if the language name mentioned in the command is not found.

These defaults apply from their position in the file until overridden:

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

The `language-create` command allows the creation of a new language in the dictionary. It takes `object` or `meta` as a subcommand and the language code as its value. An error is thrown if the language already exists in the dictionary. Examples:

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
 
This table is exhaustive.

- An entry is a top-level component.
- Other components are non-top-level components.

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

All parents can have multiple child components of the same type. For
instance, a `sense` component can have multiple `example` children. The children
can be selected by their position in the parent's list of children (see below section
"5.3.3 Ordinal selectors").

## 4. Lift Properties

A **property** is a LIFT datafield such as `form`, `gloss`, or `definition`, that is attached on a component instance.

### 4.1 Property availability table

The following table lists the properties. Each row gives a property name; the following columns indicate:
- The `Component` column: on which components the property is available
- The `Property` column: name of the property. Several components may have a property with the same name. Therefore, a property is unambiguously referred to by (component name, property name).
- The `Datatype` column: which is the datatype of the property:
  - Scalar properties:
    - String,
    - Integer,
    - Reference (a string containing the ID of an entry or sense),
    - URL (a string containing an URL).
  - Non scalar: multitext, which is a Map containing several strings associated to keys that are a lang code (more on this below, section "4.2. The multitext datatype")
- `Qualifier`: a multitext property value is qualified if a language name is also specified. A multitext property may contain several language-qualified values, but each language may occur at most once.
  - `O` — object language qualifier required or defaultable, for example `form@tww`.
  - `M` — meta language qualifier required or defaultable, for example `gloss@en`.
  - `—` — no language qualifier.
- `Required at creation`: indicate whether this property must be initialized with a value at creation. For a multitext, this means that at least one qualified value must be set.
- `Natural identity`: indicate if this property participates in the *natural identity* of the component. The natural identity allows to unambiguously select a single component, amongst its sibling same-type component instances, under a given parent. A component can have one property defining its natural identity, or several properties (max two): in that case, the natural identity is the combination of the value of the two properties. When a multitext is part of a natural identity, it means that any of its qualified values can be used for the natural identity, not all its values.

The table is exhaustive and normative for semantic validation.

| Component | Property | Datatype | Qualifier | Required at creation | Natural identity |
|---|---|---|---|---:|---:|
| Entry | `form` | multitext | O | yes, at least one qualified value | no |
| Entry | `morpheme` | string | — | no | no |
| Sense | `gloss` | multitext | M | yes, at least one qualified value | yes: one qualified value |
| Sense | `definition` | multitext | M | no | no |
| Sense | `category` | string | — | no | no |
| Example | `text` | multitext | O | yes, at least one qualified value | yes: one qualified value |
| Etymology | `form` | multitext | O | yes, at least one qualified value | part of `type + form` |
| Etymology | `gloss` | multitext | M | no | no |
| Etymology | `source` | multitext | M | no | no |
| Etymology | `type` | string | — | yes | part of `type + form` |
| Variant | `type` | string | — | yes | part of `type + target` |
| Variant | `target` | reference | — | yes | part of `type + target` |
| Relation | `type` | string | — | yes | part of `type + target` |
| Relation | `target` | reference | — | yes | part of `type + target` |
| Reversal | `form` | multitext | O | no | no |
| Reversal | `type` | string | — | yes | part of `type + target` |
| Reversal | `target` | reference | — | yes | part of `type + target` |
| Illustration | `url` | URL | — | yes | yes |
| Illustration | `label` | multitext | M | no | no |
| Media | `url` | URL | — | yes | yes |
| Media | `label` | multitext | M | no | no |
| Pronunciation | `transcription` | multitext | O | yes, at least one qualified value | yes: one qualified value |
| Trait | `type` | string | — | yes | yes |
| Trait | `value` | string | — | yes | no |
| Annotation | `type` | string | — | yes | part of `type + value` |
| Annotation | `value` | string | — | yes | part of `type + value` |
| Annotation | `comment` | multitext | M | no | no |
| Annotation | `when` | string | — | no | no |
| Annotation | `who` | string | — | no | no |
| Note | `type` | string | — | yes | yes |
| Note | `text` | multitext | M | yes, at least one qualified value | no |
| Field | `type` | string | — | yes | yes |
| Field | `text` | multitext | M | yes, at least one qualified value | no |
| Translation | `type` | string | — | yes | yes |
| Translation | `text` | multitext | M | yes, at least one qualified value | no |

### 4.2 The multitext datatype

The multitext datatype is a map of String values sub-entries, each value being
associated with a distinct language (as the keys of a map). A multitext cannot
have multiple string values for the same language. The name of a multitext value
+ the name of a language, selecting one of its sub-entry, is called a "qualified
property", its value is a "qualified property value".

A Lift Dictionary has two sets of languages: Object languages (languages that are
described and appear on field such as form, example text, etc.) and Meta
languages (language in which the linguistic description is given, and that
appear on gloss, translation, etc.)

Each multitext property is either associated with object languages or meta
languages. For instance the `form` property is associated with object languages,
while the `gloss` property is associated with meta languages.

The sub-entries of a `form` value can be associated only with language codes drawn
from the object language list of the dictionary, while all `gloss` sub-entries
must be associated with codes from the meta language of the dictionary.

A `qualified property value` is a property name + a lang name, refering to a
concrete sub-entry.

On all multitext properties, setting a language that does not exist in the
corresponding dictionary language list (either meta, or object language) should
be rejected with an ILLEGAL_LANGUAGE error.

- It is not required that all languages (metalanguages or object languages)
  existing in the dictionary have a corresponding lang-qualified value in a meta language
  multitext property or a object language multitext property. 
- Therefore, when a multitext property is a required property of a component, it
  means that at least a value for one language is given. It does not imply that
  all languages must have a value.

### 4.3 Cardinality

- scalar property cardinality (i.e. string, reference, url): only one value for each scalar property may be present on a given component
- Multitext property cardinality: one string per lang. 
  - There can be one sub-entry per lang value
  - This means that the `form` property of an entry can have multiple
    values for different languages, but cannot have multiple values for the same
    language.
  - Similarly, a sense cannot have multiple `glosses` for the same language, but
    can have multiple `gloss` for different languages.

Each component has only one ID.

### 4.4 Syntax for refering to qualified values of multitext property

The availability of keys is property-specific: some properties have a `lang`
key, some have a `type` key, and some can have both.

Lang are selected with the suffix `@<lang>`.

For instance:

```text
form@tww
gloss@en
transcription@tww
```

`@<lang>` can appears only on multitext propertie names.

The `@lang` key allow to refer to a qualified string value of a multitext property
value, i.e. a "sub-entries".

The following properties support a language key:

```text
form
definition
gloss
text
source
label
transcription
```

The following properties do not support a language key, their are scalar values:

```text
hn
type
morpheme
category
target
url
```

The validator MUST reject a combination of a scalar property with a language keys with error 'LANG_KEY_NOT_SUPPORTED_ON_SCALAR'. For example, the following are invalid:

```text
category@en
```

## 5. Selecting component using component identity properties in selectors and commands

### 5.1 Selector syntax

Square brackets always select existing objects. It never creates an object. Parents are not implicitly created by a selector. Parent creation is explicit.

We distinguish:

- a *parent selector*: it is a selector in a `of`, `under`, `in` and `within` clause.
- a *command selector*: it is the selector of the component directly targeted by a delete, ensure or move command.

The selector may contain: 

- for Entry and Sense: an ID  (see section "5.3.1 IDs")
- for all components, the identity property or the combination of two identity properties, depending on the component type (see "5.2 Identity properties")
- For entry only: the qualified form + the has-gloss pseudo-predicate, as described in section "5.3.2".
- for all components but entry: an index selector that selects the component relative to its position among similar-kind components under its parent (see "5.3.3 Ordinal selector")
- for selector under `within` only, the selector can contain any of this property without constraint (see section "7.3")

It must contain only one of these four possible selector strategies. If several are given (for instance, an index and an ID, or an ID and the two identity properties), the validator MUST reject the selector with a 'DUPLICATE_SELECTOR' error.

Example:

```text
sense[ID = "pig-44"]
sense[gloss@en = "pig"]
variant[type="dialectal", target="pig-44"]
media[url="htt://www.example.org/Image.png"]
entry[form@tww = "mami", has-gloss@en="pig"]
example[index = 1]
```

A selector resolves to a set. The following rules are mandatory:

1. An empty result raises `NOT_FOUND`.
2. More than one result raises `AMBIGUOUS_REFERENCE`.
3. A command requiring one object must receive exactly one result.
4. Component type and parent type compatibility are checked before dictionary lookup.
5. Selector comparisons use the declared language and type.

### 5.2 Identity properties

As already stated in section "4.1", the natural identity property uniquely
identify a component under its parent. It means that under a given parent, a
a component can be uniquely identified and selected using the identity property
or the combination of two identity properties listed in the table above in section "4.1". For
instance, under a given entry, no two senses can have the same gloss value (for
any meta language). Under different parents, however, two senses can have the
same qualified gloss.

Some components have one identity property, for instance `Sense`. Some components
have two identity properties, for instance `Variant`: this is the combination of
the two values that allows to uniquely identify the component under a given parent.

These identity properties are not a persistent, invariant identity. The gloss of a sense can be changed. They are all natural identity properties.

As can be seen in section "4.1", the Entry component has no identity property. Pseudo-property for the Entry component is discussed below.

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

When a qualified property is given as a lookup predicate, for instance
`gloss@en` in the following example, then a sense will match if it has a
sub-entry for this language on the gloss property and if the qualified value for
this language is the same as the value given in the lookup predicate ('pig' in
the following example), it will not take into account the fact that other
sub-entries for other languages exist or not.

```LiftPathRef
set field^review@en = "My field"
on example[index = 1]
of sense[gloss@en = "pig"]
of entry[form@tww = "mami"]
```

#### Identity and multitext property

When a multitext property is a natural identity property, the uniqueness means that, when iterating on the relevant language set languages, for components having a value value set for this language and this property, there is no duplicate. Unset qualified value does not count in the identity checking.

### 5.3 Pseudo-properties used for selecting component

These pseudo properties are defined. They do not participate in component identity.

| Name | Kind | Allowed use |
|---|---|---|
| `id` | system-managed identifier | selector and references only |
| `hn` | computed entry-disambiguation key | entry selector only, with qualified `form` |
| `has-gloss@M` | selector predicate | entry selector only |
| `index` | positional selector | non-entry selector only |

They are described in the following subsections.

#### 5.3.1 IDs

In a dictionary, entry and sense also have an `ID`. This ID is a persistent
identity. The IDs are created automatically by the system, they can be referred
to but not created manually, updated, upserted, deleted or cleared. They are
globally unique, stable across moves, are not reused after deletion of a
component. It can be used as a lookup predicates.

Example:

```text
sense[ID = "pig-44"]
```

IDs are set automatically during creation and therefore cannot be set,
initialized during creation, or upsert. The IDs are system-managed and always
present after initialization.

Here is a normative summary of the ID rule:

- The ID cannot be set, deleted, initialized during creation, or cleared.
- The ID can be used as a lookup predicate on parent selector (in `of`, `under`, `on` and `within` clause)
- The ID can be used as a lookup predicate with the `move` and `delete` command.


#### 5.3.2 The case of entry: disambiguation of homophone entries with 'hn' and 'has-gloss'

Homophones are pervasive in language and therefore in dictionary entries. Since
entries are not grouped in small sets under parents, but are all directly under
the root, they are not easy to select.

The 'form' property CAN be used alone in a selector for an entry, but if there are several matches (i.e. homophones), an error 'AMBIGUOUS_REFERENCE' will be raised. If there is only one match, the selector succeeds.

For Entry, only the ID is a property that can uniquely identify an instance. However, IDs are arbitrary and not very human-readable. IDs CAN be used in a selector, but are not a satisfying solution from a practical point of view.

For practical purposes, two pseudo-properties are offered that can be expressed
together with the 'form' property to disambiguate homophone entries and select
uniquely an entry. Since these are an selecting mechanism, these two pseudo-properties:
- can be used only in selectors,
- can be used in a upsert command (but are used only in the select branch, while they have no effect in its create branch),
- in ensure command.

They cannot be used with the `create` command. They are not natural identity properties, since they refer to the context outside of the entry itself.

1/ The first pseudo-property is the homophone number (hn).

All homophonous entries share the same qualified form, but have a different
homophone number ('hn').

The homophone number ('hn') is not a natural identity property: on a given
entry, it depends on the number of other entries with the same form, which is not
a natural property of the entry itself. However, the form + the homophone number
('hn') allows to uniquely select an entry in the dictionary at any given state of the dictionary: two entries can have
the same qualified form, but no two entries can have the same value for both
the qualified form and the homophone number ('hn'). It is a contextual lookup key rather than an identity property.

The value of an hn pseudo-property is an Integer, without quotes.

The homophone number ('hn') cannot be set by any initializer: they are managed internally by the dictionary.

Using the 'hn' property with a create command or a create branch of an upsert command will result in an error 'ILLEGAL_USE_OF_HN'.

In a selector, 'hn' cannot be used alone, but always together with the form property:

```LiftPathRef
set field^review@en = "My field"
on example[index = 1]
of sense[gloss@en = "pig"]
of entry[form@tww = "mami", hn = 1]
```

If 'hn' is used alone, without the form property, an error 'HN_CANNOT_BE_USED_ALONE' will be raised.

- 'hn' are 1-indexed.
- hn are automatically assigned by the dictionary
- numbers are generated by entry creation order
- if an hn value greater than the actual number of homophones in the homophone set is give, an "HN_OUT_OF_BOUND" exception is raised.
- for a given entry with a given 'hn', the same 'hn' value is valid whatever qualified form is referred to, i.e. whatever the language.
- 'hn' can be automatically reassigned by the dictionary, depending on the values of the form of the entry (set, cleared, updated).
   - as long as no qualified form value is changed or added in an entry, the 'hn' value is guaranteed to remain the same.
   - a change in the form of an entry can result in a different 'hn' value being assigned, and that new value will be used for subsequent lookups even with the qualified form property that were existing before the change.

For instance, let's consider an entry with hn = 1 and form@tww = "mami" and form@tpi = "pik". The same 'hn' is valid with form@tww:

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
the lang `tww` and `tpi` in order to refer to this entry.

In other word, if an entry as form in several languages, and that there at least
one of its qualified forms that is in a homophone set, its homophone number is the size of the
greatest homophone set + 1.

2/ In order to deal with homophone issues, a pseudo-property 'has-gloss' is also
defined.

The 'has-gloss' pseudo-property value must be qualified with a language code (or with implicit default language) and
its value must be a string matching the qualified gloss values of one of the
entry senses. 

```text
entry[
  form@tww = "efe",
  has-gloss@en = "door"
]
```

without explicit language code:

```text
entry[
  form@tww = "efe",
  has-gloss = "door" # equivalent to has-gloss@<default-meta-language>
]
```

The predicate means:

1. Select entries whose `form@tww` equals `"efe"`.
2. Retain entries having at least one child sense whose `gloss@en` equals
   `"door"`.

The (<form> + sense child with <gloss>) combination SHOULD uniquely select an
entry in the majority of cases, since having two homophone forms with the same meaning is
most probably not a desiderable linguistic analysis. However, it is not
enforced. As for any selection operation, if the combination of a qualified form
value and a has-gloss pseudo-property select several matches, an
'AMBIGUOUS_REFERENCE' error MUST be raised. If no entry matches, the error is `NOT_FOUND`.

has-gloss cannot be used alone, without a form, to select an entry. If `has-gloss` is used alone, a `HAS_GLOSS_CANNOT_BE_USED_ALONE` exception is raised.

In the following example, without has-gloss, the child (sense) selector will not
implicitly disambiguate an ambiguous parent (entry) selector. The fact that the
command refers to a sense with gloss `"pig"` must not silently determine which
entry is intended. Therefore the following command must fail if several entries
have `form@tww = "mami"`, even if there is only one entry with `form@tww =
"mami"` and that have a sense with `gloss@en = "door"`:

```LiftPathRef

create example(
  text@tww = "a mami jefi"
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

Instead, `has-gloss` could be used in such a case to disambiguate:

```LiftPathRef
create example(
  text@tww = "a mami jefi"
)
under sense[
  gloss@en = "pig"
]
of entry[
  form@tww = "mami",
  has-gloss@en = "pig"
]
```

Another option could be to use `within`, as described in section "7.1" below:

```LiftPathRef
create example(
  text@tww = "a mami jefi"
)
under sense[
  gloss@en = "pig"
]
within entry[
  form@tww = "mami",
]
```

- it does not participate in entry identity;
- it may be combined with other entry predicates;
- if several entries satisfy it, the selector remains ambiguous and an `AMBIGUOUS_REFERENCE` error is raised.

The `has-gloss` pseudo predicate cannot be set by any initializer. Therefore, it can be used only in selection operations (as lookup predicates), together with the form property, in the ensure command and the select branch of upsert, but CANNOT be used with the create command.

Using the 'has-gloss' property with a create command will result in an error 'ILLEGAL_USE_OF_HAS_GLOSS'.

#### 5.3.3 Ordinal selectors

Another pseudo-property is `index`; its value is an integer, without quotes.

It is an order-dependent selector, depending on the order of the node amongst its sibling.

`index` can be used with any component type but entry.

In the following example, the second sense in the sense list is selected:

```LiftPathRef
create example(
  text@tww = "a mami jefi"
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

index cannot be set by any initializer. Therefore, they can used only in selection operations (as lookup predicates), with an ensure command, the selector branch of an upsert command, the move command, but not in a create command.

In order to change an index, use the move command.

### 5.5 Command applicability table

The following table is normative.

| Property kind | `delete` | `move` | `create` | `upsert` | `ensure` | on parent selector (in `under`, `on`, `within` clause)| `set` | `update` | `clear` |
|---|---|---|---|---|---|---|---|---|---|
| Required identity property | required | required | required | required | required | allowed | allowed, subject to uniqueness | allowed, subject to uniqueness | only if the component remains valid |
| Optional natural property | allowed | allowed | allowed | allowed; uses set semantics | forbidden | allowed only if part of identity | allowed | allowed if present | allowed |
| Required non-identity property | required | required | required | allowed | forbidden | not allowed unless explicitly defined | allowed | allowed | only if another value remains |
| Optional scalar property | forbidden | forbidden | allowed | allowed | forbidden | allowed only if declared identity | allowed | allowed if present | allowed |
| Optional multitext property | forbidden | forbidden | one or more qualifiers | one or more qualifiers | forbidden | one qualifier only if identity | one qualifier | one qualifier | one qualifier or all qualifiers |
| System-managed ID | allowed (for component where IDs are supported) alone | allowed (for component where IDs are supported) alone | forbidden | forbidden | forbidden | allowed (for component where IDs are supported) | forbidden | forbidden | forbidden |
| `hn `| allowed only with `form` | allowed only with `form` | forbidden | forbidden as initializer | forbidden | allowed only with `form` | not as a property target | not as a property target | forbidden |
| `has-gloss` | allowed with `form` | allowed with `form` | forbidden | forbidden as initializer | forbidden | entry selector only, allowed with `form` | forbidden | forbidden | forbidden |
| `index` | as sole selector only | as sole selector only | forbidden | forbidden | forbidden | as sole selector only | forbidden | forbidden | forbidden |

Here `allowed` does not mean that every property is valid on every component. Availability is first determined by the property availability table.

### 5.6 Qualifier rules

The language defines three distinct forms.

#### 5.6.1 Qualified assignment

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

#### 5.6.2 Qualified selection

For selectors, an identity multitext property must use exactly one qualified value:

```text
sense[gloss@en = "pig"]
example[text@tww = "The dog ran"]
```

An unqualified multitext identity property use the default object or meta language:

```text
sense[gloss = "pig"]    # equivalent to gloss@<default-meta-language>
```

#### 5.6.3 Clearing

`clear` have separate semantics:

```text
clear definition@en   # remove one language value
clear definition      # remove all language values
```

The default language is **not** applied to an unqualified `clear`.

For a required multitext property:

```text
clear gloss@en
```

is legal only if another qualified `gloss` value remains. And, for a required multitext property, unqualified reference such as:

```text
clear gloss
```

is never legal since it will empty the property. Then:

> A required multitext property may lose individual language values, but may never become empty.

## 6. Initializers and creation syntax

The parenthesized values are initializers. They do not select an existing
component. On the contrary, square brackets select existing components, they do
not create new ones.

Multiple property initializers, separated by commas, are allowed (note that the
following example must fail if several homophones entries have the form "mami"
for the lang "tww"):

```LiftPathRef
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

#### 6.1 Creation and initializer rules

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

# Part 2. The `LiftPatchRef` LiftPatch reference syntax

## 7. Core command syntax

There are five *component commands* (commands directly targeting a component):

- create
- move
- delete
- ensure
- upsert

`entry` does not have an "under PARENT" clause since an entry is at the dictionary root. `entry` cannot be moved.

The syntax for each case is:

```text
create entry(initializers)
create COMPONENT(initializers) under PARENT [at POSITION]

upsert entry(initializers)
upsert COMPONENT(initializers) under PARENT

delete entry[SELECTOR]
delete COMPONENT[SELECTOR] under PARENT

move COMPONENT[SELECTOR] under COMPONENT[SELECTOR] at POSITION
move COMPONENT[SELECTOR] at POSITION

ensure COMPONENT(initializers) under PARENT
```

'move' and `ensure` cannot target an `entry` component.

The three *property commands* (command directly targeting a property):

```text
set PROPERTY = VALUE on PARENT
update PROPERTY = VALUE on PARENT
clear PROPERTY on PARENT
```

The language distinguishes the following cases:

- `create COMPONENT(...)` requires that the component does not already exist
  (according to the identity properties provided); if it does, the error
  'DUPLICATE_COMPONENT' is raised;
- `upsert COMPONENT(...)` creates or resolves the component;
- `set PROPERTY = VALUE` creates the property if absent and replaces
  its value if present;
- `update PROPERTY = VALUE` requires an existing property and replaces
  its value; if the property is not already set, an "UNSET_PROPERTY" error is
  raised;
- `clear PROPERTY` removes the selected property value(s); if the
  property is not already set, an "UNSET_PROPERTY" error is raised.

For all *property commands*, if the command refers to a property that does not exist on its parent type according to the table in section "4.", an error 'PROPERTY_DOES_NOT_EXIST_ON_COMPONENT_TYPE' is raised.

### 7.1 Selecting direct parent with `under` or `on`

For all eight commands, PARENT refers to the parent component of the targeted property or component. The parent is identified by either the `under` or the `on` keyword, then a mandatory component name, and then a mandatory `[SELECTOR]`.

- `under` identifies the immediate parent of a component directly targeted by a `component command` (create, move, upsert, ensure, delete)
  - there is no `under` clause when the target of the command is an entry, since an entry is at the root of the hierarchy and has no parent
- `on` identifies the immediate parent of a property directly targeted by a property command (set, update, clear)

### 7.2 Selecting other ancestor with `of`

If the component selected by `under` or `on` is not an entry, `of` clause(s) must be used.

- An additional `of` clause can qualify that parent with its own parent
- Multiple `of` clauses can be chained together. Every `of` selector must be a parent of the previous component.
- either the component after `on` or `under`, or the component after the last `of` clause must be an entry
- skipped ancestors are not allowed

Here are some examples:

- creating an entry makes no reference to a parent:

```LiftPathRef
create entry(form@tww = "mami")
```

Create a sense under an existing entry:

```LiftPathRef
create sense(gloss@en = "pig")
  under entry[form@tww = "mami"]
```

```LiftPathRef
set definition@en =
  "A four-legged terrestrial animal"
on sense[gloss@en = "pig"]
of entry[form@tww = "mami"]
```

```LiftPathRef
update definition@en =
  "A revised four-legged terrestrial animal"
on sense[gloss@en = "pig"]
of entry[form@tww = "mami"]
```

```LiftPathRef
update form@tww = "memi"
  on entry[form@tww = "mami"]
```

```LiftPathRef
delete sense[gloss@en = "pig"]
  under entry[form@tww = "mami"]
```

`set` creates or replaces the selected property:

```LiftPathRef
set definition@en =
  "A definition"
on sense[gloss@en = "pig"]
of entry[form@tww = "mami"]
```

An empty string is a valid property value. It does not mean deletion:

```LiftPathRef
set category = ""
  on sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

To remove a property, use `clear`:

```LiftPathRef
clear category
  on sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

The following strict update requires the property to exist:

```LiftPathRef
update definition@en =
  "A revised definition"
on sense[gloss@en = "pig"]
of entry[form@tww = "mami"]
```

### 7.3 Replacing `of` with `within` for existential filtering semantics

While each `of` clause explicitly selects a component among its siblings, `within` allows for a existential filtering strategy. The logic is : *Keep parent P if ∃ child C in P such that C matches selector S.*

`within` allow for intermediate multiplicity, but the final evaluation of a within chain must result in a single child component, otherwise an `AMBIGUOUS_REFERENCE` is raised. If no component is selected, an `NOT_FOUND` error is raised.

- with `of`, a component is selected if it satisfies its own selectors. As mentioned, the selector must contains all the identity properties so that the component is selected unambiguously.
- with `within`, a component is selected if:
  - first, it belongs to one or more components selected (the selector at the right of `within` can contain any number of properties, identity property or not, that allows for several matches)
  - secondly, it is the only member in the previous match set that has the specified child (mentioned in selector at the left of the `within` selector).

For instance, in the following example, a `sense` is mentioned after a `within` clause.
- it is then allowed to use any property as a selector, for instance the category property that is not an identity property
- several senses can then be matched
- if only one of those has a child that matches the component to the left of the `within` clause, i.e. an example that has the text "a mami jefi", then the `within` succeeds. If several senses have a matching child, then an `AMBIGUOUS_REFERENCE` error is raised. If no sense satisfies its selector, or if no sense has a matching child, then a `NOT_FOUND` is raised.

```LiftPathRef
create note(
  type = "special",
  text@en = "Very important"
)
under example[text = "a mami jefi"]
within sense[category = "Verb"]
of entry[form@tww = "mami", hn=2]
```

#### Syntax

The `within` clause chains a PARENT component (to the right) with a child component (to the left). Several `within` clauses can be consecutive.

Its syntax is:

```text
CHILD[SELECTOR] within PARENT[SELECTOR]
```

when consecutive `within` clause occurs, the first node is a child of the second, which in turn is the child of the third:

```text
CHILD[SELECTOR] within PARENT[SELECTOR]
within PARENT[SELECTOR]
```

`within` may appear exactly where an `of` clause may appear. In the following syntax schema:
- `within` select a set of one or more ancestors;
- then it filter these ancestor to those having the child component describe at its left (in the `under` clause)
- if only one subtree remains, we proceed to the command
- COMMAND operates on the target component

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

```LiftPathRef
create note(
  type = "special",
  text@en = "Very important"
)
under sense[gloss@en = "foo"]
within entry[form@tww = "mami"]
```

`within` can coexiste and alternate with `of`. In any case, the succession of `of` and `within` always describes a chain of child/parent succession.

A *chain of `within`*, more generally, is a sequence of (one or more) CHILD-PARENT links joined by `within` without an interleaving `of`. For instance, the following example contains two chains of `within` separated by an `of` clause. The first contains one `within`; the second chain contains two `within` clauses:

```text
COMMAND
under IMMEDIATE_PARENT
within NEXT_PARENT
of NEXT_PARENT
within NEXT_PARENT
within NEXT_PARENT
```

#### Properties allowed in a `within` selectors

The selector of a `within` clause may contain any property of the component, not only the identity properties, since a `within` clause is not responsible for uniquely and unambiguously selecting one component alone. This excludes the pseudo-predicates that are not directly manageable: 'hn', 'has-gloss', 'index', 'ID'.

#### Selection algorithm

With a `within` clause chain, the selection follows the following algorithm:

1. The resolver uses the selector of the component selected by the last `within` clause.
   - if it matches no component, 'NOT_FOUND' is raised.
   - if it must match *one or more components*, the resolver keeps this set of components. This is completely different from the `of` clause, which must match exactly one component.
2. the validator then considers the selector at the left of the `within` clause.
   - This selector is used to filter out the previously selected subtrees: only the subtrees whose last child has a child of the corresponding kind that matches the selector is kept. Here, the last selected child is the subtree root.
   - if no subtrees are found, a 'NOT_FOUND' is raised.
3. Step 2 is repeated with the other `within` clauses in the clause chain (in reverse declaration order): the validator considers the selector at the left of the previous clause, and it must applied it to the child found during the previous step. When these parents do not have the corresponding child, the corresponding subtree rooted at the beginning of the `within` clause chain is filtered out.
4. When the last `within` clause is executed, exactly one subtree must be left. If, at the end, more than one subtree remains, an 'AMBIGUOUS_REFERENCE' is raised.

Here is an example. Let's focus on the following example.

```LiftPathRef
update value = "Animals"
under trait(type="semantic domain")
of translation(text="I shoot a pig")
of example[text="a mami jefi"]
within sense[gloss="pig"]
within entry[form="mami"]
```

In the previous example:

- The resolver starts with the last `within` chain, which contains:

```text
of example[text="a mami jefi"]
within sense[category="Noun"]
within entry[form="mami"]
```

- according to 1, the resolver starts with the last `within` clause: it selects all entries matching `entry[form="mami"]`. If no entry matches, it raises 'NOT_FOUND'. Suppose that four entries have this form.
- according to 2, it then moves to the left of the `within` clause, which has a selector containing `sense[gloss="pig"]`. It applies this filter to the previously selected entries. Suppose that two entries have a sense child with a category "Noun".
- according to 3, step 2 is repeated with the left of this last `within`, i.e. the selector `example[text="a mami jefi"]`. For the two senses selected at the end of the previous step, we look for an example with the given text. If one sense has this example, then the corresponding subtree rooted at an entry (the beginning of the `within` chain) is kept. If several senses have such an example, then an `AMBIGUOUS_REFERENCE` exception is raised. If no sense has such an example, then a `NOT_FOUND` is raised.

#### Difference between `of` and `within`

An `of` clause identifies the immediate parent of the preceding component in a chain where each step is uniquely and unambiguously selected.

```LiftPathRef
set definition@en = "A definition"
on sense[gloss@en = "foo"]
of entry[form@tww = "mami"]
```

A `within` clause describes a chain of parent-child relations with potentially partial information (not sufficient to uniquely select the node) on each step, the resolver being in charge of finding a complete chain that satisfies all the constraints:

```LiftPathRef
set definition@en = "A definition"
on example[index = 1]
of sense[category = "Noun"]
within entry[form@tww = "mami"]
```


#### Existential filtering with the selected child

When a `within` clause is used, the parent selector is evaluated together with the already selected descendant path.

For example:

```LiftPathRef
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

This is useful when several entries have the same form. The `within` clause therefore acts as a existential filtering constraint that filter a set of parents with condition on their descendants:

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

```LiftPathRef
create note(type = "special", text@en = "Very important")
under sense[gloss@en = "foo"]
within entry[form@tww = "mami"]
```

This command does not create the entry or the sense. If either component is absent, the command fails.

#### Blocks

`within` is not allowed inside a block.

#### Assessment

The `within` clause solves a real problem: selecting a child component while ensuring that its containing entry is the intended homophone entry. It makes a relationship such as:

```text
entry[form = "mami"] containing sense[gloss = "foo"]
```

expressible without silently using a child selector to disambiguate an ambiguous entry.

1. Use `of` for an explicit, immediate parent chain.
2. Use `within` for a parent constraint.
3. `within` is an existential child constraint: the parent must contain at least one matching descendant.
4. Resolve the complete constrained path before executing the command.
5. Never let `within` create missing ancestors.
6. Multiple matching `within` chains produce `AMBIGUOUS_REFERENCE`.

## 8. Commands operating on components

### 8.1 Create

The `create` command always creates a new component and does not reinterpret the
properties as a component selector.

Properties required for the creation of a component are defined in the "Required at creation" column in the property table in section "4. Lift properties".

Here are examples of `create` commands that create new components:

```LiftPathRef
create note(type = "sociolinguistics", text@en = "A sociolinguistic note")
  under entry[form@tww = "mami"]
```

```LiftPathRef
create field(type = "borrowing", text@en = "yes")
  [gloss@en = "pig"]
  of entry[form@tpi = "pik"]
```

```LiftPathRef
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
section "5.2 identity properties". It means that, under the same parent, two
senses are identical if they have the same qualified gloss; two examples are
identical if they have the same qualified text, two variants are identical if
they have the same type and target.

The same multitext can be referred twice with a different language qualifier.

```text
upsert sense(
  gloss@en = "pig",
  gloss@fr = "porc"
)
```

#### The special case of entry

For entry, a component with the same value for the `form` property may exist; the following should
work, even if an entry already exists with the same qualified form:

```LiftPathRef
create entry(form@tww = "mami")
```

#### to POSITION clause

The create command allows an optional "at POSITION" clause, which specifies the
index at which to insert the component under the parent list of
same-type-components.

Allowed positions are:

```text
at beginning
at end
at index <n>
```

Example:

```LiftPathRef
create sense(gloss@en = "pig")
  under entry[form@tww = "mami"]
  at beginning
```

- The index is 1-based. 
- The valid insertion range is 1..count+1. If the index given is < 1 or greater than the number of already existing same-type components + 1, an error 'INDEX_OUT_OF_BOUNDS' is raised.

### 8.2 Upsert

`upsert` select a component or create it if it does not exist:

```LiftPathRef
upsert sense(gloss@en = "pig")
  under entry[form@tww = "mami", hn="1"]
```

The logic of upsert is:
- It creates the sense if no matching sense exists (it is the create branch) and resolves the existing
  sense otherwise (the select branch).
- A matching sense is a sense having the same values for its identity property or properties.
- An upsert command fails with 'MISSING_IDENTITY_PROPERTY' if it does not have all the identity properties required for this component type as arguments (qualified text for an example, type + target for a variant, etc.).
- Once the component is created or selected with its identity properties, all the other properties will be
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

In order to resolve the identity of the component, the upsert command uses the
identity properties mentioned in section "5.2 identity properties".

Special rule with entry and sense:

- with entry and sense: `upsert` cannot use an ID since an ID cannot be set. Using an ID with upsert fails with the error `ID_NOT_ALLOWED_ON_UPSERT`.
- with entry, `upsert` can use either `hn` or `has-gloss`, both in conjunction with form. `hn` and `has-gloss` will be used for the selector branch of upsert. For the create branch, only form will be used and a new entry, possibly a homophone of an existing one, will be created.

### 8.3 Ensure

`ensure` is equivalent to an idempotent component upsert without changing
existing properties. Ensure can refer only to the component's identity
properties. If a property that does not belong to the set of identity properties
for this component is present, a 'NON_IDENTITY_PROPERTY' error is raised. It
means that the following is rejected as 'NON_IDENTITY_PROPERTY', since category
is not a sense component identity property:

```
ensure sense(
  gloss@en = "pig",
  category = "Noun"
)
under entry[form@tww = "mami"]
```

`ensure` cannot be used with an `entry` since an entry as no natural identity property.

### 8.4 Delete a component

Deleting a component also deletes its descendants. For instance, the following
command will delete the sense as well as all the component descendants:

```LiftPathRef
delete sense[gloss@en = "pig"]
  under entry[form@tww = "mami"]
```

Deleting an entry requires no parent clause:

```LiftPathRef
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

```LiftPathRef
move example[index = 1]
  of sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
  at end
```

When moving within the same parent, the index corresponds to a position after the moved component has been removed. The following moves the second element to the third position:
  
```LiftPathRef
move example[index = 2]
  of sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
  at index 3
```

2/ When the move command has an `under` clause, the destination is the component targeted by the `under` clause:

```LiftPathRef
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
- the move will created a duplicate amongst the sibling set according to natural identity properties. The validation must iterate on pre-existing siblings and check that none has the same values than the new candidate sibling for the identity properties of this type of component. If a duplicate would be created, an error 'CANNOT_CREATE_DUPLICATE' must be raised. For multitext natural identity property, it means iterating on the relevant language set languages and check that, for the multitext having a value set for this language, there is no duplicate.

The selectors will raise an exception if:

- the source does not exist;
- the destination does not exist.

The source and destination are resolved before the move is applied.

Moving changes position and parentage, never identity.

## 9. Commands operating on properties

### 9.1 Set

`set` is the normal property assignment operation. It creates the target if
absent and replaces its value if present:

```LiftPathRef
set category = "Noun"
  on sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

Or:

```LiftPathRef
set text@en =
  "A sociolinguistic note"
on note(type = "sociolinguistic")
of sense[gloss@en = "pig"]
of entry[form@tww = "mami"]
```

`set` never creates a missing parent component. Parent creation must be
explicitly requested with `ensure` or performed by a component `upsert`.

`set` cannot be used without specifying a language for a language-qualified property (i.e. a
multitext). There is always a default language (according to the rules given
below), and therefore the language can be omitted. However, it does not mean
that set will operate without language qualification.

For instance, the following `set` operation will assign a value in the current
default language (say, "en"), it will not assign the definition to a generic,
not language-specific definition:

```LiftPathRef
set definition = "A definition"
on sense[gloss@en = "pig"]
of entry[form@tww = "mami"]
```

The previous command will set the value of the qualified value `definition@en`,
it will not change any other qualified value existing on that property (say,
`definition@fr`).

If the `set` command target one of the natural identity properties of the component, the validation must iterate on siblings and check that none has the same values than the new candidate sibling for the identity properties of this type of component. If a duplicate would be created, an error 'CANNOT_CREATE_DUPLICATE' must be raised. When the targeted natural identity property is a multitext, it means to check the values qualified with the same languages on the other sibling components.

### 9.2 Update

Update requires an existing target:

```LiftPathRef
update definition@en =
  "A four-legged animal"
  on sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

or:

```LiftPathRef
update text@en = "A revised note"
on note(type="review")
of sense[gloss@en = "pig"]
of entry[form@tww = "mami"]
```

- If the property does not exist on the parent component type according to the table in section "4.", an error 'PROPERTY_DOES_NOT_EXIST_ON_COMPONENT_TYPE' is raised.
- If the property was not already set, an 'UNSET_PROPERTY' error is raised.

The same rule as above (under "set") regarding language applies: lang can be
implicit.

In the following example, the default language is used in order to qualify the definition property:

```LiftPathRef
update definition =
  "A four-legged animal"
  on sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

If the `update` command target one of the natural identity properties of the component, the validation must iterate on siblings and check that none has the same values than the new candidate sibling for the identity properties of this type of component. If a duplicate would be created, an error 'CANNOT_CREATE_DUPLICATE' must be raised. When the targeted natural identity property is a multitext, it means to check the values qualified with the same languages on the other sibling components.

### 9.3 Clear

#### Clear for non-identity properties

`clear` removes property values while retaining the parent component:

```LiftPathRef
clear definition@en
  on sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

For a multitext property, omitting the language removes all language values:

```LiftPathRef
clear definition
  on sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

`clear` is the only command that may intentionally target multiple
qualified property values. Component selectors and `set`/`update` targets must
resolve to exactly one qualified value. An unqualified `clear` may then remove
several matching values, however it never removes the parent component.

- If a qualified property value doesn't exist with the given language, the error
is `QUALIFIED_PROPERTY_NOT_FOUND`.
- when the multitext property is not an identity property, if the `clear` command refers to the unqualified property and that property has no sub-entries, `clear` fails with 'EMPTY_MULTITEXT'.

`clear` on scalar properties removes the property value:

```
clear url
on illustration[...]
```

```LiftPathRef
clear category
  on sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

If a scalar property doesn't exist (is not set) on the given component, the
error is `UNSET_PROPERTY`.

#### Clear for required scalar properties

Scalar properties listed in the component identity properties cannot be cleared. Other scalar properties can be cleared.

#### Clear for required multitext properties

The `clear` command can also be used on a required property.

In the case of a required multitext property, clear cannot remove the last
value. A qualified value of a required multitext property may be cleared if at
least one qualified value remains; the complete property may not be cleared. It
means that:

1/ the following command will raise a `CANNOT_CLEAR_REQUIRED_MULTITEXT` error if the `tww` form
is the only sub-entry: 

```text
clear form@tww
  on entry[form@tww = "mami"]
```

It will successfully remove the value for the `tww` form if there is also a form
in another language.

2/ with a multitext property that is required on a component, the clear
command cannot be used without lang selector, as it will remove the value for
all languages and leave the multitext property empty. The following two commands
raise `CANNOT_CLEAR_REQUIRED_MULTITEXT`:

```text
clear form
  on entry[form@tww = "mami"]
```

and:

```text
clear gloss
  on sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

These commands raise `CANNOT_CLEAR_REQUIRED_MULTITEXT`.

It means that clear command does not use the implicit language.

## 10. References to other components

Properties of datatype "reference" store the ID of the referenced component.

1/ Within command, the value is set by referring to the target component, with selector:

```LiftPathRef
set target = entry[form@tww = "memi"]
on relation[type = "synonym"]
of entry[form@tww = "mami"]
```

A relation to a sense may be written:

```LiftPathRef
set target =
  sense[gloss@en = "pig"]
  of entry[form@tww = "memi"]
on relation[type = "associated-sense"]
of entry[form@tww = "mami"]
```

The LiftPatchRef DSL is not in charge of managing the integrity of the reference. It means that the LiftPatchRef DSL will not check, when a component is deleted, if it creates an invalid reference elsewhere. This is the responsability of the dictionary management system.

2/ within selectors, the string value of the target ID can be used directly:

```text
variant[type="dialectal", target="pig-44"]
```

- The stored value is the internal ID of the target.
- The validator verifies that the target exists and has an allowed component
  type. `NO_SUCH_TARGET` is raised if the target cannot be resolved.
- The validator verifies that the target is an entry or a sense. Otherwise,
  `INVALID_TARGET` is raised.
- The validator verifies that the target is not the parent of the source
  component itself.
- The validator verifies that no two identical Relation or Variant components exist on the
  same parent component (i.e. with the same type and the same
  target). 

These rules regarding reference apply to every target property on:

- variants;
- relations;
- reversals.

When an identical component with the same type and the same target already exists,
the creation of a Variant, Relation, or Reversal component should fail with 'DUPLICATE_REFERENCE'.

## 11. Block syntax

Blocks provide convenient syntax for related operations while preserving explicit component construction.

A curly brace block is a syntactic construct that groups related commands together, and anchors them to a common parent

A block consists of
- the *block header* which is given before the curly brace.
  - the block header is either:
    - a component with a selector
    - a `create` or `upsert` command with a component and its selector 
- the *block body* which is given inside the curly brace. The block body contains either:
  - another block: a block can be nested
  - one or several commands. Those commands have neither an `under` nor an `on` clause, since the parent is given by the block header.

The next example has a block header that contains a component with selector. It will fail with 'AMBIGUOUS_REFERENCE' or 'NOT_FOUND' if the selector failed.

```LiftPathRef
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

```LiftPathRef
create entry(form@tww = "mami") {
  create sense(gloss@en = "pig") {
    set definition@en = "A four-legged terrestrial animal"
    set category = "Noun"
    create example(text@tww = "a mami jefi") 
  }
}
```

An upsert, ensure or update cannot be in the scope of a `create` command, be it at the direct upper level or indirectly related. For instance, in the following command, an upsert command is illegally in the scope of a `create` command at the direct upper level:

```LiftPathRef
create entry(form@tww = "mami") {
  upsert sense(gloss@en = "pig") {
    set definition@en = "A four-legged terrestrial animal"
    set category = "Noun"
    create example(text@tww = "a mami jefi") 
  }
}
```

Such situation should raise 'CANNOT_UPSERT_OR_ENSURE_IN_CREATION_BLOCK'.


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

# Part 3. The *LiftPatchShort* LiftPatch Concise surface syntax

This section describes an alternate concise syntax for practical purposes, LiftPatchShort.

This concise syntax allows only for a subset of the reference syntax described above.

Its underlying semantic is exactly the same.

This concise syntax must be described in a separate formal (BNF) grammar.

This surface syntax is line-oriented: a command is on a single line.

It is intended for lexicographers who frequently create entries, senses, examples, and their properties in a text document containing both commands and ordinary prose. The parser must then distinguish LiftPatchShort concise command line from ordinary prose paragraphs.

Every valid surface command MUST have an unambiguous expansion into one reference-language command. All the semantic principles of the reference syntax MUST be followed. The difference between LIFT-DSL and LIFT-Short-DSL are surface syntax differences only.

Every unspecified rule or semantic constraint in this concise language specification is inherited from the reference syntax specification. For instance
- required properties on initializers
- existential filtering and resolution rules for within clause
- a within chain as a whole must select one match, but some of its components may select several matches.

## 1. Design goals

The concise syntax should:

1. Be concise for common lexicographic operations.
2. Be normally expressible on one physical line.
3. Be easy to recognize in a mixed text document.
4. Follow the same semantics exactly as the reference syntax uses.
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

followed by a whitespace character.

For reliable extraction from ordinary prose, commands MUST begin at the
beginning of a line, i.e. should be preceded by a new line character. A command
continues until the end of that physical line.

The concise syntax does not support multiline commands. A long or complex operation must use the reference language instead.

## 2. Single-letter designations of commands, components and properties

In concise syntax, commands, components, and properties are designated by a single-letter code. The semantics, however, remain exactly the same.

Some commands, components, and properties can be abbreviated by the same code (for instance, e = `ensure` command and `entry` component). However, the same letter is never used for two commands, two properties, or two components. The context always allows to disambiguate whether we are referring to a command, a component, or a property.

### 2.1. Command short codes

The commands are the same as in the LIFT-DSL language. They are represented by a single letter, called a "short code", in the following table:

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

The components are the same as in the LIFT-DSL language. They are referred to by a single letter.  The letters are:

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

The properties on components are the same as in LIFT-DSL language, they are refered to with a single letter code:

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

Here is the general command syntax for each command.

1/ For components:

*create*: the created component is created under the last selected path component.

```text
c <parent_path> <component_type>(<initializer>)
```

an entry has a special syntax since it is created without a parent path:

```text
c e(<initializer>)
```

*upsert*: the upserted component is matched or created under the last selected path component:

```
p <parent_path> <component_type>(<initializers>)
p e(<initializers>)   # for entry
```

*delete*: the component represented by the last step of the path is deleted:

```
d <path>
```

Following this rule, for entry a command as the following form:

```
d /e[selector] # for entry
```

*move*: the last step of the path is moved to a different position or, if a destination path is expressed, under another parent (the last step of the destination path) at the specified position:

```
m <source-path> [<destination-path>] at <position>
```

*ensure*:

```
e <parent_path> <component_type>[selector]
```

Following this rule, for entry a command as the following form:

```
e /e[selector]
```

2/ For properties: the property is between parentheses, with only its name (for clear) or its name and its value separated by "=" (for set and update).

*set*:

```text
s <path> (<property> = <value>)
```

*update*

```text
u <path> (<property> = <value>)
```

*clear*:

```text
l <path> (<property>)
```

Example:

1/ Create a sense with gloss "pig" (in default meta language) under the existing entry with form "mami" (in default object language):

```LiftPathShort
c /e[f="mami"] s(g="pig") 
```

2/ Delete an existing sense with gloss "pig" (in default meta language) under the existing entry  with form "mami" (in default object language):

```LiftPathShort
d /e[f="mami"] s[g="pig"]
```

3/ Update the property "category" of a sense:

```LiftPathShort
u /e[f="mami"]/s[g="pig"] (c = "Verb")
```

4/ Clear the property "category" of a sense:

```LiftPathShort
l /e[f="mami"]/s[g="pig"] (c)
```

5/ Set the property "category" of a sense:

```LiftPathShort
s /e[f="mami"]/s[g="pig"] (c = "Noun")
```

## 4 Path specification

A path starts with a slash and is a sequence of slash-separated steps.

Each step is made of a letter indicating the component type and one or two selectors between square brackets.

The semantics of the relation between the steps of a path are those of the `within` keyword in the reference language (by contrast, the direct target of a create, upsert, delete, or ensure command is always linked with its parent, the last step of the path, using `under`, as in the reference language).

This means that the following path:

```Path
/e[f="mami"]/s[g="pig"]
```

can be translated, in the reference language:

```Fragment
s[g="pig"]
within  e[f="mami"]
```

Since the steps in the path are linked according to the semantics of `within`, then, in the previous example, the sense can be silently chosen from several entries with a form "mami", as in the reference specification.

And with a `create` command, where the path select a sense and the initializers create an example under it:

```LiftPathShort
c /e[f="mami"]/s[g="pig"] x(t="A mami jefi")
```

translates into:

```LiftPathRef
create example(text="A mami jefi")
under sense[gloss="pig"]
within entry[form="mami"]
```

the ordinal pseudo-property in the reference syntax `[index=2]` is expressed with
a single integer in the concise syntax:

```LiftPathShort
d /e[f="mami"]/s[g="pig"]/x[1]
```

The preceding example is therefore equivalent to:

```LiftPathRef
delete example[index = 1]
  within sense[gloss@en = "pig"]
  within entry[form@tww = "mami"]
```

As with the reference syntax, the index selector cannot occur together with any other selectors in the square brackets.

## Command syntax details

### Create, upsert, ensure: commands with initializers

Commands with initializer are identical to the initializer + `under` in the reference language.

```LiftPathShort
c /e[f="mami"] s(g="pig")
```

This translates into

```
create sense(gloss="pig") under
entry[form="mami"]
```

The path identifies the parent of the component created.

As in the reference language:

- ensure (e) requires all identity properties and does not allow non-identity initializers (and raises an exception)
- upsert (p) requires all identity properties
- The initializer initializes the property or properties that are required for the creation of a component in the reference syntax. If two properties are required, they are separated by a comma:

```LiftPathShort
c /e[f="mami"]/s[g="pig"] n(y="sociolinguistics", t="my note")
```

Translate into:

```
create note(type="sociolinguistics", text="my note")
under sense(gloss="pig")
within entry[form="mami"]
```

### Delete

The `delete` command deletes the last step of the path.

This command:

```LiftPathShort
d /e[f="mami"] s[g="pig"]
```

This translate into:

```LiftPathRef
delete sense[gloss="pig"] under
  entry[form="mami"]
```

This command:

```LiftPathShort
d /e[f="mami"]/s[g="pig"] x[t="a mami jefi"]
```

This translate into:

```LiftPathRef
delete example[text="a mami jefi"]
  under sense[gloss="pig"]
  within entry[form="mami"]
```

As stated earlier, the steps in the path are linked according to the semantics of `within`.

### Move

```LiftPathShort
m /e[f="mami"]/s[g="pig"] at index 1
```

Translate in:

```LiftPathRef
move sense[gloss="pig"] within entry[form="mami"] at index 1
```

When `move` has a second path, it is equivalent to a move with an under clause in the reference syntax: it moves towards another parent.

Then, the following: 

```LiftPathShort
m /e[f="mami"]/s[g="pig"]/x[1] /e[f="mami"]/s[g="large animal"] at end
```

is equivalent to:

```LiftPathRef
move example[index = 1]
  within sense[gloss@en = "pig"]
  within entry[form@tww = "mami"]
  under sense[gloss@en = "large_animal"]
  within entry[form@tww = "mami"]
  at end
```

`beginning`, `end`, and `index n` have exactly the reference-language semantics,

### Set

```LiftPathShort
s /e[f="mami"]/s[g="pig"] (c = "Noun")
```

Is equivalent to:

```LiftPathRef
set category = "Noun" 
  on sense[gloss="pig"]
  within entry[form="mami"]
```

### Update

```LiftPathShort
u /e[f="mami"]/s[g="pig"] (c = "Verb")
```

is equivalent to

```LiftPathRef
update category = "Verb" 
  on sense[gloss="pig"]
  within entry[form="mami"]
```

### Clear

```LiftPathShort
l /e[f="mami"]/s[g="pig"] (c)
```

is equivalent to

```LiftPathRef
clear category
  on sense[gloss="pig"]
  within entry[form="mami"]
```

## Simplified path and initializers

### droping component and field name for entry's form and sense's gloss

1/ If the first step of a path is a single string between single or quotes, then it is the form of an entry:

```
/"mami"
```

is then equivalent to

```
/e[f="mami"]
```

which is in turn equivalent to (in reference syntax):

```
entry[form="mami"]
```

If the form of the entry contains no whitespace or any of the following special characters {/\"'$[]()[]}, it can even be noted without quotes:

```
/mami
```

Then, the following command create an entry with form "mami" in the default object language:

```
c /mami
```

2/ if the second step of a path is a single string between quotes, then it is the gloss of a sense:

```
/"mami"/"pig"
```

This is then equivalent to

```
/e[f="mami"]/s[g="pig"]
```

which is in turn equivalent to (in reference syntax):

```
sense[gloss="pig"]
within  entry[form="mami"]
```

Again, if the gloss of the sense contains no whitespace or any of the following special characters {/\"'$[]()[]}, it can even be noted without quotes:

```
/mami/pig
```

Then, the following command create a sense with gloss "pig" in the default meta language:

```
c /mami/pig
```

### Droping property name in initializer

The following initializers can drop the property name under the following conditions:

#### Entry

If an `entry` initializer has no property name and equal sign before the assigned string, then it is the entry form

```
c e("mami")
```

This is equivalent to:

```
c e(f="mami")
```

i.e., in reference syntax:

```
create entry(form="mami")
```

#### Sense

If a `sense` initializer has no field name and equal sign before the assigned string, then it is the sense gloss:

Then the following:

```
p /"mami" s("pig")
```

equals:

```
p /e[f="mami"] s(g="pig")
```

which translates, in the reference syntax, as:

```
upsert sense(gloss="pig")
under entry[form="mami"]
```

#### Example

If an `example` initializer has a string without property name it is the example text.

Then the following:

```
c /"mami"/"pig" x("a mami jefi")
```

is equivalent to:

```
c /"mami"/"pig" x(t="a mami jefi")
```

which translates, in reference syntax, as:

```
create example(text="a mami jefi")
  under sense[gloss="pig"]
  within entry[form="mami"]

```

#### Default argument for other initializer

The following table show how many unamed string argument are allowed for the initializers of the different component type, and to which properties they map

| Component | Number of unamed argument | Property mapping |
|--|--|--|
| Trait | 2 | type, value |
| Note | 2 | type, text |
| Translation | 2 | type, text |
| Field | 2 | type, text |

This means that in the following example for example, the field initializer creates a field with type "editorial" and value "To be checked":

```
c /"mami"/"pig" f("editorial", "To be checked")
```

## Embedding initializer

An embedded component creation is allowed *into* a component initalizer for creating a child on the fly.

For instance, in the following example, the creation construct "o(t="I shot a pig", y="literal")" with a component letter, parentheses, and initalizer, is embedded in the example initializer. The translation created by the embedded initializer is created and added to its parent.

```
c /e["mami"]/s["pig"] x(t="a mami jefi", o(t="I shot a pig", y="literal"))
```

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

Embedded creation rules also include:

- Embedded child creation is atomic with the parent command;
- an embedded creation uses the semantics of `create` (meaning that an error is raised if the child already exists)
- nested embedded initializers are allowed

## An idiosyncratic construct

The following `upsert` construct, without a constructor after the path,
means:

- check if an entry with form "mami" -- and having a sense with gloss "pig" -- exist;
- if yes, do nothing;
- if no (i.e. if no "mami" entry exists, or if one or several entries exist but without the sense "pig"):
  - create a new entry "mami" and create a new sense "pig" on it.

```
p /"mami"/"pig"
```

Since, in this, entry form and sense gloss have no withespace or special character, it can be write:

```
p /mami/pig
```


## A final example

Using implicit field name and embedded initializers, considere the following command:

```
c e("mami", s("pig", x("A mami jefi", o("literal", "I shot a pig")))
```

It should be translated into:

```
create entry(form="mami")

create sense(gloss="pig") under entry[form="mami"]

create example(text="A mami jefi")
  under sense[gloss="pig"]
  within entry[form="mami"]

create translation(text="I shot a pig", type="literal")
  under example[text="A mami jefi"]
  within sense[gloss="pig"]
  within entry[form="mami"]

```
