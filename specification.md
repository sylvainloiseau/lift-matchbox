---
Author: Sylvain Loiseau
Last edit: 2026/09/13
Version: 1.0
Title: "LiftPatch: a mutation language for the LIFT data model"
---

# LiftPatch: a mutation language for the LIFT data model

This document defines a command domain specific language (DSL), called *LiftPatch*, for creating, updating, deleting, upserting, and moving components and properties in a dictionary based on the LIFT data model. The *LiftPatch* language is not a serialization format: it is a mutation command language for updating the dictionary content. The language preserves the LIFT component hierarchy while making component identity, parentage, property availability, creation, selection, and mutation semantics explicit.

The LIFT dictionary format is intended for the linguistic description of the
lexicon of a language. It a tree-like structure; it contains *components*, such
as `entry`, `sense`, or `example`, which are nodes in the tree, and *properties*
that are attached to the components, such as the `form` (on an `entry` component),  `gloss` or `definition` (on a `sense` component), etc.

## 1. Design principles

1. Every operation has an explicit verb: `create`, `ensure`, `set`, `upsert`, `update`, `clear`, `delete`, or `move`.
2. Parentheses describe a component being created and initialized with natural identity properties.
3. Square brackets select an existing component.
4. Multiple matches of a component during lookup are always errors, with two stated exceptions: in a `within` clause, where intermediate non-unique matches are permitted, and on a step carrying an explicit multiplicity keyword (`each`, `all`, or `*` in LiftPatchShort). The language never silently selects the first match.
5. `set` creates or replaces a property value.
6. `update` replaces an existing property and never creates it.
7. `clear` removes property values without removing the parent component.
8. `create` creates a new structural component with its mandatory natural property or properties.
9. `delete` removes structural components.
10. `move` moves a structural component either to another position among its siblings or under another parent.
11. `upsert` creates a missing component and applies its initializers, or resolves an existing component.
12. `ensure` **asserts** that a component exists. It never creates anything and never modifies anything; it fails if the component is absent.
13. A command, a block, and a whole script each either succeed completely or have no effect.
14. A command operates on exactly one component unless an explicit multiplicity keyword (`each`, `all`) is written. Multiplicity is never implicit.
15. A component created, resolved or asserted by a command may be given a label (`as $name`) so that later commands in the same script can refer to it without selecting it again.

### 1.1 Language version

A script MAY declare the version of the language it targets with a version pragma:

```text
%liftpatch 1.0
```

When present, the pragma MUST appear **before the first command** of the script.
Blank lines, comment lines and — in a LiftPatchShort document — ordinary prose
lines may precede it, so that a field-note file may open with a title. 

A pragma that declares a `sigil` (see below) MUST be the first non-blank line of the document, because the sigil is what decides which lines are commands at all, and that decision cannot depend on a pragma that only a command-aware scan would find.

The pragma has the same form in both surface syntaxes. A script without a pragma is processed as `1.0`. An implementation that does not support the declared version MUST reject the script with `UNSUPPORTED_LANGUAGE_VERSION`.

The pragma may carry named attributes after the version number, written
`name="value"` and separated by whitespace. Three are defined in version 1.0:

- `sigil`, meaningful in LiftPatchShort documents only, which declares the line prefix that marks a command (Part 3, section "1.1");
- `metamodel`, which names the metamodel the script requires (Appendix A). When it is present and does not name the metamodel the implementation has loaded, the script is rejected with `UNSUPPORTED_METAMODEL`. When it is absent, the script is validated against the metamodel of Appendix A;
- `syntax`, whose value is `"LiftPatchRef"` or `"LiftPatchShort"`, which declares which of the two surface syntaxes the script is written in. When it is present, the implementation MUST parse the script with the declared syntax; when it is absent, the implementation determines the syntax by other means (a file extension, a command-line option), and the value it used MUST be reported in the plan document (section "12.4.1").

```text
%liftpatch 1.0 sigil=">"
%liftpatch 1.0 metamodel="tww-project-2"
%liftpatch 1.0 syntax="LiftPatchShort"
```

An unknown attribute is rejected with `UNKNOWN_PRAGMA_ATTRIBUTE`.

The `syntax` attribute exists because the two surface syntaxes cannot be told
apart from the text alone, and because getting it wrong is silent: a LiftPatchRef
script read as a LiftPatchShort document matches no command line — every line
begins with a verb, not with a command letter followed by a constrained second
token — and is therefore read as pure prose, applying nothing and reporting
nothing. Two further rules close that hole:

- plan mode MUST report a warning for a document in which no line at all was recognized as a command (section "12.4");
- implementations SHOULD use the extension `.liftpatch` for a LiftPatchRef script and `.liftpatchs` for a LiftPatchShort document.

### 1.2 The two surface syntaxes

The liftPatch language comes with two syntaxes. The two 
syntaxes have exactly the same semantics. They differ only in surface syntax:

1/ The first syntax, the *LiftPatch reference syntax* (short:
*LiftPatchRef*).

It is a verbose and explicit language.

LiftPatch reference commands are contained in a script file, "Lift patch reference script". This file contains only:

- commands that start on their own line with one of the eight verbs (set, upsert, update, clear, delete, ensure, create, move).
- *block headers*, together with their opening `{` and their closing `}` line (see section "11").
- scope directives: `language-default`, `language-create`, and the `with` block (see section "2.1").
- the optional version pragma `%liftpatch 1.0` (see section "1.1").
- comments. A comment starts with an unquoted `#` and runs to the end of the physical line. A comment may occupy a whole line, or follow a command on the same line (*inline comment*). A `#` occurring inside a quoted string is an ordinary character, not a comment marker.

2/ The second syntax, the *LiftPatch short language* (short: *LiftPatchShort*) is more concise.

It is intended for lexicographers expressing lexical information to be ingested in a dictionary.

The LiftPatchShort commands are expressed on a single line. It can be mixed with other content and non-LiftPatchShort commands in a file.

LiftPatchShort commands are:

- lines starting with optional whitespace followed by one of the single-letter command abbreviations, followed by whitespace and by a second token of a constrained shape. The recognition rule is normative and is given in Part 3, section "1.1"; a one-letter prefix alone is deliberately *not* sufficient, so that ordinary prose beginning with "c " or "s " is not mistaken for a command.
- special instructions `language-default` and `language-create` 
- the optional version pragma `%liftpatch 1.0`, anywhere before the first command line of the document (section "1.1"). A pragma declaring a `sigil` is the one exception: it must be the first non-blank line.

In both syntaxes, strings are quoted by single or double quotes. The escaping
rules are the following, and they are exhaustive:

- in a **double-quoted** string, `\"` is a double quote and `\\` is a backslash. These two are the only escape sequences: any other character following a backslash is an error (`SYNTAX_ERROR`), so that no sequence is silently reinterpreted later;
- in a **single-quoted** string, `''` is a single quote and a backslash is an ordinary character with no escaping role;
- a raw line break is forbidden inside a string in both syntaxes; there is no escape for it in version 1.0, and a value containing a line break cannot be written. (LiftPatchRef allows a command to span several physical lines, but not a string.)

The `sigil` pragma attribute (section "1.1") is meaningful in LiftPatchShort
documents only. In a LiftPatchRef script it is accepted and **ignored**: it is
not an unknown attribute, and it does not make the script's lines subject to a
prefix rule, since LiftPatchRef commands are recognized by their verb.

### 1.3 Structure of this document

- Part 1 describes the LIFT dictionary data model and the semantics of the LiftPatch language.
- Part 2 describes the *LiftPatchRef* reference syntax.
- Part 3 describes the *LiftPatchShort* concise syntax.
- Appendix A gives the normative metamodel — component types, parentage, properties, required properties and natural identity — in a machine-readable form. Where a table of Part 1 and Appendix A diverge, Appendix A prevails.
- Appendix B lists every error code with its kind, static or dynamic, the warnings, and the codes that a conforming implementation must not raise.
- Appendix C gives the EBNF grammar of each of the two surface syntaxes.
- Appendix D gives the conformance corpus: a machine-readable set of cases, each with a dictionary, a script and the expected outcome, which an implementation must pass to claim conformance.

# Part 1. Data model and semantics

## 2. The LIFT dictionary

A lift dictionary is a list of `entry` components.

The order of that list is **maintained by the dictionary, not by LiftPatch**: it
is typically an insertion or a collation order, and this language neither reads
it nor changes it. Three rules of the language follow from this, and are stated
again where they apply:

- an `entry` takes no `at POSITION` clause when it is created or upserted (section "6.2");
- `move entry[...]` is a syntax error (section "8.5");
- an `entry` cannot be selected by an ordinal (section "5.3.3").

Below the root, a component type is **not** automatically positionable either.
How the same-type children of one parent are held — in an ordered list, in a map
keyed by their `type`, or not held as a collection at all — is a property of the
component type, declared by the metamodel, and it decides which selectors,
which positioning clauses and which verbs apply to it. The three *component
kinds* are defined in section "3.1", and every rule of this specification that
speaks of position, of ordinals, of type keys or of `move` is stated in terms of
them.

Since a lift dictionary is not a monolingual dictionary, it also has:

- an ordered list of object languages, i.e. one or more languages that are described in the dictionary. These are, for instance, the languages represented in the `entry` `form` or in the `example` `text`. The list cannot be empty.
- an ordered list of meta languages, i.e. one or more languages that are used to describe the object language. These are, for instance, the languages used in the `gloss` and the `definition` properties of a `sense`, or the `translation` of the `example`. The list cannot be empty.

### 2.1 Default languages

At any moment, there is always a default meta language and a default object language that a command can use if a required language is not specified.

The default meta language and the default object language are resolved
independently of each other. For a given language kind K (meta or object), the
resolution applies the following rule: *the default language of kind K is the
most recent binding for K that is in scope at this point of the script, whether
that binding comes from a `with` header (see "2.1.2 Lexically scoped defaults
with `with`"), from a `language-default K` directive, or from a `language-create
K` directive (a newly created language becomes the default for its kind, see
section "2.1.3"), the innermost scope being examined first; if no such binding is
in scope, the default language of kind K is the first language of that kind in
the language list returned by the dictionary.*

The fallback (the first language of that kind in the dictionary's list) is always
applicable, since neither language list can be empty. The resolution is
therefore total and deterministic.

"Most recent" is decided by source position, and the three binding forms are not
ranked against each other: a `language-default` written after a
`language-create` overrides it, and a `language-default` written inside a `with`
body overrides the `with` header for the rest of that body.

The default language applies to:

- selectors (including `within` selectors and `has` predicates);
- `create` initializers;
- `upsert` initializers (both branches);
- the selector of `ensure`;
- `set`;
- `update`;
- the `has-gloss` pseudo-predicate.

It does not apply to `clear`: on a multitext property, `clear` requires an
explicit qualifier, either a language code or the wildcard `@*` (see section
"5.5.3").

### 2.1.1 The `language-default` directive

The two following directives set the default language from a LiftPatch script:

```text
language-default object = "tww"
language-default meta = "en"
```

New languages cannot be declared that way. A meta (resp. object) language name
referred to by this directive must exist in the dictionary's meta (respectively, object) language list. `NO_SUCH_META_LANGUAGE` (resp. `NO_SUCH_OBJECT_LANGUAGE`) MUST be raised if the language name mentioned in the directive is not found.

The scope of a `language-default` directive is:

- the remainder of the enclosing block, if the directive occurs inside a block (see section "11"), including any nested block, and *not* beyond the closing brace of that block;
- otherwise, the remainder of the script file.

A directive occurring in an inner scope shadows any directive of the same kind
in an enclosing scope; when the inner scope ends, the enclosing binding is
restored. Within a single scope, a later directive overrides an earlier one:

```text
language-default object = "tww"
language-default meta = "en"

set form = "mammi"       # equivalent to form@<default-object-language>
on entry[form = "mami"]  # equivalent to form@<default-object-language>

set gloss = "piglet"         # equivalent to gloss@<default-meta-language>
  on sense[gloss = "pig"]    # equivalent to gloss@<default-meta-language>
  of entry[form = "mamio"]   # equivalent to form@<default-object-language>
```

### 2.1.2 Lexically scoped defaults with `with`

Because the scope of a `language-default` directive depends on its position in
the file, scripts that are reordered or concatenated can change meaning
silently. The `with` block binds defaults lexically and is the RECOMMENDED form:

```LiftPatchRef
with object = "tww", meta = "en" {
  create entry(form = "mami") {
    create sense(gloss = "pig")
  }
}
```

- A `with` block binds one or both language kinds. A kind that is not bound keeps the binding it has in the enclosing scope.
- The binding applies to every command in the block body, including nested blocks, and ends at the closing brace.
- A `with` block is a scope, and, like every block, also a transaction boundary (section "11"): it takes part in the atomicity of the enclosing block or script, and a failure inside its body rolls the body back and then fails the enclosing scope.
- The language code named by a `with` binding must exist in the corresponding dictionary language list; otherwise `NO_SUCH_META_LANGUAGE` or `NO_SUCH_OBJECT_LANGUAGE` is raised.

### 2.1.3 Creating new languages

The `language-create` directive creates a new language in the dictionary. It takes `object` or `meta` as a subcommand and the language code as its value. `LANGUAGE_ALREADY_EXISTS` is raised if the language already exists in the dictionary. Examples:

```text
language-create object = "tpi"
language-create meta = "fr"
```

A language created by `language-create K` becomes the default language for kind
K, with the same scope rules as a `language-default K` directive placed at the
same position. It can be overridden afterwards by a `language-default` directive
or by a `with` block.

Two further points, because `language-create` is the one directive that changes
the dictionary rather than only the state of the script:

- the language it adds is added to the dictionary's language list, not to a scope: it remains after the end of the enclosing block, and after the end of the script;
- it is nevertheless part of the script's transaction (section "12.2"). If any later command fails, the whole script is rolled back and the language is **not** created: "the dictionary is left exactly as it was before the script started" covers its language lists as well as its components.

## 3. Components and parentage

A **component** is a structural LIFT node such as an `entry`, `sense`, or `example`. A component contains one or several properties.

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
| Category | `category` |
 
This table is exhaustive.

- An `entry` is a top-level component.
- Other components are non-top-level components.

The allowed parent-child relationships are:

| Parent | Allowed child components |
|---|---|
| Dictionary | `entry`|
| Entry | `sense`, `etymology`, `variant`, `relation`, `pronunciation`, `reversal`, `trait`, `annotation`, `note`, `field` |
| Sense | `sense`, `example`, `relation`, `illustration`, `reversal`, `trait`, `annotation`, `note`, `field`, `category` |
| Variant | `pronunciation`, `relation`, `trait`, `annotation`, `field` |
| Pronunciation | `media`, `trait`, `annotation`, `field` |
| Example | `translation`, `trait`, `annotation`, `field` |
| Etymology | `trait`, `annotation`, `field` |
| Relation | `trait`, `annotation`, `field` |
| Reversal | `trait`, `annotation`, `field` |
| Trait | `annotation`, `field` |
| Illustration | `annotation`, `field` |
| Media | `annotation`, `field` |
| Annotation | |
| Note | `annotation` |
| Field | `annotation` |
| Translation |  |
| Category | `trait` |

This table is exhaustive: every component type of the component table appears as
a row, and a row with an empty right-hand cell denotes a component type that
cannot have any child. A component may only be created or moved below a
parent listed in the table.

Creating a component under an illegal parent (or moving a component towards an
illegal parent) must fail with an 'ILLEGAL_PARENT' error.

Most parents can have multiple child components of the same type. For
instance, a `sense` component can have multiple `example` children. How those
same-type children are held, and therefore how one of them is designated, is
decided by the *component kind* of the child type, defined in section "3.1".

The metamodel states which children a component type *may* have, and never how
many it must have: there is no minimum cardinality anywhere in this language,
with the single exception of a singleton child, which always exists (section
"3.1"). Consequently a component may legitimately be left with no children of a
type it allows — `delete all sense[...] under entry[...]` leaves an entry with no
sense, and that is not an error. Whether such a state is desirable lexicography
is a question for the editor, not for this language; whether the dictionary
management system accepts it is a question for that system.

### 3.1 Component kinds

Every component type has exactly one *component kind*, declared by the metamodel
(Appendix A, `componentKind`). The kinds form this hierarchy:

```text
- singleton components
- non-singleton components
  - ordered components
  - typed components
```

The kind answers one question — *how are the same-type children of one parent
held, and how is one of them designated?* — and every rule about position,
ordinals, type keys, `move` and the `at` clause follows from it.

| Kind | How the same-type children are held | Designated by | Component types |
|---|---|---|---|
| **ordered** | an ordered list, whose order the script controls | a selector, or the ordinal `#n` (section "5.3.3") | `sense`, `example`, `annotation`, `relation`, `reversal`, `variant`, `illustration`, `media`, `pronunciation`, `etymology` — and `entry`, with the reservations of section "2" |
| **typed** | a map keyed by the `type` property | a selector, or the type key `^t` (section "5.3.4") | `trait`, `note`, `field`, `translation` |
| **singleton** | not a collection: at most one, and it always exists | the component type name alone (strategy S7, section "5.1.3") | `category` |

**Ordered components.** These live in an ordered list of same-type siblings under
one parent, and that order is meaningful: the order of the `sense` children of an
entry, of the `example` children of a sense and of the `annotation` children of
anything is chosen by the lexicographer and carries editorial intent. An ordered
component is therefore the only kind that takes an ordinal, the only kind that
takes an `at POSITION` clause (section "6.2"), and the only kind that `move`
accepts (section "8.5").

`entry` is an ordered component whose list is maintained by the dictionary rather
than by this language; the three consequences are stated in section "2" and are
not repeated here.

**Typed components.** These do not live in an ordered list. They live in a map
whose key is their `type` property, which is why `type` is the whole of their
natural identity property set (section "5.2"): under one parent, no two `note`
children may share a `type`, and asking for "the `note` whose type is `general`"
always designates at most one component. There is no first or second `note`, so:

- an ordinal on a typed component raises `COMPONENT_NOT_ORDERED`, a static error;
- an `at POSITION` clause on a command creating a typed component raises `COMPONENT_NOT_ORDERED`;
- `move` on a typed component raises `COMPONENT_NOT_ORDERED`. A typed component is re-keyed by writing its `type`, not by moving it, and is re-parented by deleting it and creating it under the new parent;
- the type key `^t` (section "5.3.4") is the concise way to designate one, in both syntaxes: `note^general` is `note[type = "general"]`.

**Singleton components.** A singleton is not a collection at all: a host
component has exactly one child of that type, it always has one, and it never has
two. `category` is the only singleton of the current metamodel, and it exists on
`sense` only. The consequences are stated once here and referred to elsewhere:

- a singleton is **never created and never deleted**. `create category(...)`, `upsert category(...)`, `delete category` and `move category` all raise `SINGLETON_CANNOT_BE_CREATED_OR_DELETED`, a static error. It comes into existence with its host and disappears with it;
- a singleton is designated by **its component type name alone**, with no selector, no ordinal and no type key — strategy S7 of section "5.1.3". `category[value = "Noun"]` as a *unique* selector raises `SINGLETON_TAKES_NO_SELECTOR`, a static error, because there is nothing to choose among;
- as a **filtering** selector, however — inside a `has` predicate, or on the parent side of a `within` / `/` link — a singleton step may carry an ordinary predicate list like any other step (section "5.1.4"), since a filtering selector asks a question rather than making a choice: `sense[has category[value = "Verb"]]` is the way to ask which senses are verbs;
- a singleton has an empty natural identity property set, and the uniqueness invariant of section "5.2" is vacuous for it;
- its properties are ordinary properties, written and read by `set`, `update` and `clear` exactly as on any other component: `set value = "Noun" on category of sense[...]`.

A singleton always exists, but its properties need not be set. `category` always
exists on a sense; `category.value` may be unset, and a sense whose category
carries no value is a sense whose grammatical category has not been stated.
`clear value on category of sense[...]` returns it to that state.

**Which code is reported.** A step can break a kind rule and another rule at the
same time, so the order in which a validator reports is fixed, and it is this:

1. the **kind** of the component type is checked first, because it decides what the step may contain at all: `SINGLETON_CANNOT_BE_CREATED_OR_DELETED` for a verb a singleton does not accept, then `SINGLETON_TAKES_NO_SELECTOR`, `COMPONENT_NOT_ORDERED` and `COMPONENT_NOT_TYPED` for a device the kind does not admit;
2. only then are the ordinary selector rules applied — `PREDICATE_NOT_ALLOWED_IN_UNIQUE_SELECTOR`, `INCOMPLETE_SELECTOR`, `DUPLICATE_SELECTOR`, `PSEUDO_PROPERTY_NOT_ALLOWED_IN_FILTER`.

So `category[value = "Noun"]` in an `on` clause is
`SINGLETON_TAKES_NO_SELECTOR` and not `PREDICATE_NOT_ALLOWED_IN_UNIQUE_SELECTOR`,
even though the predicate would be refused on its own; and `move note^general …`
is `COMPONENT_NOT_ORDERED`, reported against the verb, rather than
`COMPONENT_NOT_TYPED`, which does not apply since `note` *is* typed. All of these
are static errors, so a script containing one changes nothing (section "12.3").

## 4. Properties

A **property** is a LIFT datafield such as `form`, `gloss`, or `definition`, that is attached on a component instance.

### 4.1 Property availability table

The following table lists the properties. Each row gives a property name; the following columns indicate:
- The `Component` column: on which components the property is available
- The `Property` column: name of the property. Several components may have a property with the same name. Therefore, a property is unambiguously referred to by (component name, property name).
- The `Datatype` column: which is the datatype of the property:
  - Scalar properties:
    - String,
    - Integer,
    - Reference (a string containing the ID of an `entry` or `sense`),
    - URL (a string containing an URL).
  - Non scalar: multitext, which is a Map containing several strings associated to keys that are a lang code (more on this below, section "4.2")
- `Qualifier`: a multitext property value is qualified if a language name is also specified. A multitext property may contain several language-qualified values, but each language may occur at most once.
  - `O` — object language qualifier required or defaultable, for example `form@tww`.
  - `M` — meta language qualifier required or defaultable, for example `gloss@en`.
  - `—` — no language qualifier.
- `Required at creation`: indicate whether this property must be initialized with a value at creation. For a multitext, this means that at least one qualified value must be set. On a **singleton** component type (section "3.1"), which is never created, every property is necessarily "no": there is no creation at which to require one.
- `Natural identity`: indicate if this property belongs to the *natural identity property set* of the component type, as defined in section "5.2". A component type has a natural identity property set of zero, one, or two properties. When a multitext belongs to that set, only *same-language* values are compared: a qualified value `p@L` of one component is compared with the qualified value `p@L` of a sibling for the same language `L`, never with a value in another language, and unset qualified values never take part in the comparison.

The table is exhaustive and normative for semantic validation. It is the
human-readable rendering of the normative metamodel given in "Appendix A. The
normative metamodel"; in case of divergence, Appendix A prevails.

| Component | Property | Datatype | Qualifier | Required at creation | Natural identity |
|---|---|---|---|---:|---:|
| Entry | `form` | multitext | O | yes, at least one qualified value | no |
| Entry | `morpheme` | string | — | no | no |
| Sense | `gloss` | multitext | M | yes, at least one qualified value | yes: one qualified value |
| Sense | `definition` | multitext | M | no | no |
| Category | `value` | string | — | no | no |
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
have multiple string values for the same language. The name of a multitext value + the name 
of a language, selecting one of its sub-entry, is called a "qualified
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

A `qualified property value` is a property name + a lang name, referring to a
concrete sub-entry.

On all multitext properties, setting a language that does not exist in the
corresponding dictionary language list should be rejected with
`NO_SUCH_OBJECT_LANGUAGE` (for an object-language multitext) or
`NO_SUCH_META_LANGUAGE` (for a meta-language multitext) — the same two codes as
for the `language-default` directive and the `with` block (section "2.1").

- It is not required that all languages (metalanguages or object languages)
  existing in the dictionary have a corresponding lang-qualified value in a meta language
  multitext property or an object language multitext property. 
- Therefore, when a multitext property is a required property of a component, it
  means that at least a value for one language is given. It does not imply that
  all languages must have a value.

### 4.3 Cardinality

- scalar property cardinality (i.e. string, reference, url): only one value for each scalar property may be present on a given component
- Multitext property cardinality: one string per lang. 
  - There can be one sub-entry per lang value
  - This means that the `form` property of an `entry` can have multiple
    values for different languages, but cannot have multiple values for the same
    language.
  - Similarly, a sense cannot have multiple `glosses` for the same language, but
    can have multiple `gloss` for different languages.

Each id-bearing component (`sense` and `entry`) has only one ID.

### 4.4 Language qualifiers

Whether a property accepts a language key is decided by its `Datatype` column in
the table of section "4.1": every multitext property accepts a language key, and
no scalar property does. That table, and Appendix A behind it, is the only list
of which property is which.

Lang are selected with the suffix `@<lang>`.

For instance:

```text
form@tww
gloss@en
transcription@tww
```

`@<lang>` can appears only on multitext property names.

The `@lang` key allows to refer to a qualified string value of a multitext property
value, i.e. a "sub-entries".

The pseudo-properties `hn`, `id`, the ordinal `#n` and the type key `^t`
(section "5.3") are not properties and never accept a language key. The pseudo-predicate `has-gloss` does accept
one, because it is a shorthand for a predicate over the multitext `gloss`
(section "5.3.2.2").

The validator MUST reject a combination of a scalar property with a language key with error `LANG_KEY_NOT_SUPPORTED_ON_SCALAR`. For example, the following are invalid:

```text
morpheme@en
value@en
```

#### 4.4.1 The `@*` wildcard qualifier

The qualifier `@*` denotes *every* language for which the multitext has a value.
It is allowed only where a set of qualified values is meaningful:

- in `clear`, where `clear definition@*` removes all the language values of `definition` (section "5.5.3");
- in a filtering selector predicate, where, writing `∃L` for "there is a language `L` for which `p` has a value":
  - `p@* = V` matches a component for which `∃L. p@L = V` — the value `V` is present for *at least one* language of `p`.
  - `p@* != V` matches a component for which `exists(p) ∧ ¬∃L. p@L = V` — `p` has at least one qualified value, and **none** of them is `V`. A component on which `p` is entirely unset does **not** match, exactly as with the scalar `p != V`, which also requires `p` to be set (section "5.1.2"). To match the unset case, write `absent(p)`; the language has no disjunction, so "unset or different from `V`" needs two commands.
  - `p@* ~ V` matches a component for which `∃L. p@L` matches the regular expression `V` — at least one qualified value of `p` matches. `p@* ~i V` is the same, case-insensitively.

`@*` is forbidden in `create`, `upsert`, `set` and `update` initializers and
targets, which must designate exactly one qualified value; the error is
`WILDCARD_NOT_ALLOWED`.

## 5. Selection

### 5.1 Selectors

Square brackets always select existing objects. They never create an object. Parents are not implicitly created by a selector. Parent creation is explicit.

#### 5.1.1 Steps, predicates and axes

A *step* denotes one component type together with the conditions it must
satisfy:

```text
STEP           ::= ORDERED-STEP | TYPED-STEP | SINGLETON-STEP
ORDERED-STEP   ::= COMPONENT-NAME ( '[' PREDICATE-LIST ']' | ORDINAL )
TYPED-STEP     ::= COMPONENT-NAME ( '[' PREDICATE-LIST ']' | TYPE-KEY )
SINGLETON-STEP ::= COMPONENT-NAME [ '[' PREDICATE-LIST ']' ]
ORDINAL        ::= '#' INTEGER
TYPE-KEY       ::= '^' ( BARE-WORD | STRING )
PREDICATE-LIST ::= PREDICATE { ',' PREDICATE }
```

Which of the three forms a step takes is decided by the **component kind** of
`COMPONENT-NAME` (section "3.1"), which the metamodel declares: the ordinal `#n`
belongs to ordered components, the type key `^t` to typed components, and the
bare component name — a step with nothing after it — to singletons. Using one on
the wrong kind is a static error: `COMPONENT_NOT_ORDERED` for an ordinal on a
type that is not ordered, `COMPONENT_NOT_TYPED` for a type key on a type that is
not typed, and `SINGLETON_TAKES_NO_SELECTOR` for a unique selector that gives a
singleton anything at all. The predicate list of a `SINGLETON-STEP` is available
in a filtering selector only (section "5.1.4").

The predicates of a list are combined by conjunction: a component matches the
step when it matches *every* predicate of the list. A step may also be replaced
by a label reference (`$name`, see section "6.3").

A *chain* links a step to its ancestors through an *axis*:

```text
CHAIN ::= STEP { AXIS STEP }
AXIS  ::= '!'    (strict parent, always; also written `of` / `under` / `on`)
        | '/'    (existential parent, always; also written `within`)
```

There are exactly **two axes**, each has exactly one keyword spelling used in
Part 2 and exactly one operator spelling used in Part 3, and neither spelling
depends on where the link stands:

| Axis | Keyword spelling (Part 2) | Operator spelling (Part 3) | Semantics |
|---|---|---|---|
| strict parent | `of` / `under` / `on` | `!` | section "7.3" |
| existential parent | `within` | `/` | section "7.4" |

The keyword and operator spellings may not be mixed inside one chain. A leading
`/` in LiftPatchShort is not an axis but the **root marker** of an absolute path
(Part 3, section "4"), and the strict link that joins a command's parent path to
its direct target is not written at all: it is the whitespace between the two
(Part 3, section "4.1"). One clarification:

- **The two syntaxes write chains in opposite directions.** A LiftPatchRef chain is written *child first, ancestors after*: `sense[gloss@en = "pig"] of entry[form@tww = "mami"]` names the sense, then its parent. A LiftPatchShort path is written *ancestor first, descendants after*: `/e[f="mami"]/s[g="pig"]` names the entry, then its child. The two are the same chain. Whenever this document says "the step to the left" or "the step that follows", it is speaking of one syntax only, and says which; the syntax-independent formulation is "the parent step" and "the child step".

We distinguish:

- *unique selector* (or *unique selection*): a selector that must match exactly one component. There are two sub-kinds:
  - a *parent selector*: the selector of a component named as the parent of another, in an `of`, `under`, `on` or `within` clause. In Part 3 a parent selector is a step of a command's *parent path*, and it is a unique selector exactly when its **child** link is the strict axis — that is, when the link to its child step is written `!`, or when it is the last step of the parent path, whose link to the direct target is strict and unwritten (Part 3, section "4.1");
  - a *command selector*: the selector of the component directly targeted by a `delete`, `ensure`, `move` or `set`/`update`/`clear` command, or by the select branch of an `upsert` command; the step of an `at before` / `at after` clause is also one (section "6.2.1"); in Part 3, the *direct-target step* that `d`, `e` and the source of `m` write after their parent path is a command selector (Part 3, section "4.1");
- a *filtering selector*: a selector that is not required to select a single component by itself. There are exactly three cases, and this list is exhaustive:
  - the selector of a component on the **parent** side of a `within` / `/` axis;
  - a step inside a `has` predicate;
  - the target step of a command carrying a multiplicity keyword — `each`, `all`, or `*` in LiftPatchShort (section "5.6").

#### 5.1.2 Predicates

| Form | Meaning | Allowed in |
|---|---|---|
| `p = V` | the property `p` has the value `V` | any selector |
| `p != V` | `p` is set and its value differs from `V` | filtering selectors only |
| `p ~ V` | `p` is set and its value matches the regular expression `V` | filtering selectors only |
| `p ~i V` | as `~`, case-insensitively | filtering selectors only |
| `exists(p)` | `p` has at least one value | filtering selectors only |
| `absent(p)` | `p` has no value | filtering selectors only |
| `has STEP` | the component has at least one child matching `STEP` | any selector, subject to section "5.3.2" |
| `id = V` | pseudo-property, see "5.3.1" | see "5.3" |
| `hn = N` | pseudo-property, see "5.3.2.1" | see "5.3" |
| `has-gloss@L = V` | shorthand for `has sense[gloss@L = V]`, see "5.3.2.2" | see "5.3" |

- On a multitext property, `p` must be written `p@L` (or `p@*`, section "4.4.1");
an omitted qualifier means the applicable default language
- On a scalar property, a qualifier is forbidden (`LANG_KEY_NOT_SUPPORTED_ON_SCALAR`).
- `exists(p)` and `absent(p)` are exact complements: for the same `p`, exactly one of them holds on any given component. They accept a scalar property as well as a multitext one, since "the trait that has no `value`" is as ordinary a query as "the sense that has no French definition":
  - on a **scalar** property, `exists(p)` means that the property has a value, and `absent(p)` that it has none. No qualifier may be written (`LANG_KEY_NOT_SUPPORTED_ON_SCALAR`);
  - on a **multitext** property written without a qualifier, `exists(p)` means that at least one qualified value exists, and `absent(p)` that none does;
  - on a **multitext** property written with a language qualifier, `exists(p@L)` means that a value exists for that language, and `absent(p@L)` that no value exists for it, whatever the other languages hold;
  - neither operator may be used with the wildcard `@*` (`WILDCARD_NOT_ALLOWED`): `exists(p@*)` would mean `exists(p)` and `absent(p@*)` would be ambiguous between "no language has a value" and "some language has none". Write `exists(p)` or `absent(p)` instead.
- An unqualified `exists` / `absent` on a multitext is the one place where an omitted qualifier does **not** mean the default language: the operator asks about the property as a whole. Write `exists(p@L)` to ask about one language.

**The regular-expression dialect.** `~` and `~i` use **PCRE** (Perl Compatible
Regular Expressions), with these three points fixed so that two implementations
agree on every script:

- the match is a **search, not a full match**: `text ~ "draft"` matches a value *containing* "draft". Anchor with `^` and `$` to require a full match;
- the subject is the single qualified value designated by the left-hand side, never the concatenation of several languages; with `p@*`, each qualified value is tested separately (section "4.4.1");
- `~i` applies **Unicode simple case folding**, not ASCII-only lowercasing, so that `~i` behaves on Tuwuli, German or Greek data as it does on English. The regular expression is matched against the value as stored, without any other normalization; a script that must be robust against composed and decomposed forms should normalize its data, not rely on the matcher.

**Which operator applies to which datatype.** An operator used on a datatype it
does not fit is rejected with `OPERATOR_NOT_APPLICABLE_TO_DATATYPE`. This is a
static error: the datatype of every property is given by the metamodel. The rule
is:

| Datatype | `=` | `!=` | `~`, `~i` | `exists`, `absent` |
|---|---|---|---|---|
| `string` | a quoted string | a quoted string | yes | yes |
| `url` | a quoted string | a quoted string | yes | yes |
| `integer` | an unquoted integer | an unquoted integer | no | yes |
| `reference` | a chain or a label, never a string (section "10") | a chain or a label | no | yes |
| multitext, qualified (`p@L`, `p@*`) | a quoted string | a quoted string | yes | yes |
| multitext, unqualified (`p`) | the default language applies | the default language applies | the default language applies | yes — and here alone the unqualified form asks about the property as a whole |

- A `url` is compared and matched as an ordinary string. This language performs **no** syntactic validation of URLs: `set url = "not a url"` is accepted, and reporting malformed URLs is the business of the dictionary management system, not of a mutation language. There is no error code for a malformed URL.
- `~` and `~i` on an `integer` or a `reference` raise `OPERATOR_NOT_APPLICABLE_TO_DATATYPE` rather than matching a string rendering of the value, because the rendering would be an implementation detail.
- The withdrawn code `OPERATOR_REQUIRE_A_MULTITEXT` (Appendix B.3) named a narrower version of this rule; `OPERATOR_NOT_APPLICABLE_TO_DATATYPE` replaces it and covers every datatype, while `exists()` and `absent()` remain applicable to all of them.

The predicates restricted to filtering selectors are excluded from unique
selection because they are, by construction, not uniquely identifying; using one
of them in a command selector or in a strict (`of`/`under`/`on`, `!`) parent
selector raises `PREDICATE_NOT_ALLOWED_IN_UNIQUE_SELECTOR`.

#### 5.1.3 Selection strategies for unique selection

A command selector, and a parent selector on the strict axis, are *unique
selector*: it must select exactly one component. To make this checkable statically, such
a selector MUST use exactly one of the following *selection strategies*. This
list is exhaustive.

| # | Strategy | Written as | Applicable to |
|---|---|---|---|
| S1 | Label | `$name` | any component created and labelled earlier in the script |
| S2 | Persistent identifier | `[id = "…"]` | `entry` and `sense` only |
| S3 | Natural identity | all the properties of the natural identity property set of the component type, each given exactly once; a multitext identity property with exactly one language qualifier; OPTIONALLY refined by one or more `has` predicates | every component type whose natural identity property set is non-empty |
| S4 | Entry lookup | a qualified `form` predicate, OPTIONALLY refined by **either** exactly one `hn` predicate **or** one or more `has` / `has-gloss` predicates | `entry` only |
| S5 | Ordinal | `#n` (deprecated alias: `[index = n]`) | **ordered** component types only, and not `entry` |
| S6 | Type key | `^t` | **typed** component types only |
| S7 | Singleton | the component type name alone, with nothing after it | **singleton** component types only |

Notes:

- S3 is not applicable to `entry`, whose natural identity property set is empty (section "5.2"); S4 replaces it. S4 is not applicable to any other component type.
- The refinements of S3 and S4 are part of the strategy, not separate strategies: `entry[form@tww = "mami", hn = 1]` uses one strategy, not two, and so does `sense[gloss@en = "pig", has example[text@tww ~ "jefi"]]`.
- A `has` refinement is available on **every** component type, not only on `entry`. It is admitted in a unique selector because `has` can only ever narrow a candidate set, so it cannot make a unique selector less unique; because it is the disambiguator this specification recommends over `hn` (section "5.3.2.1"); and because without it there is no way to tell two same-type siblings apart by their content when their identity keys are defined for different languages.
- `has-gloss` remains an `entry`-only shorthand (section "5.3.2.2"): on other component types, write the `has` predicate out.
- S4 without refinement may still resolve to several entries (homophones); this raises `AMBIGUOUS_REFERENCE` like any other ambiguous selection.
- The ordinal `#n` and the type key `^t` may not be combined with any predicate list, and no refinement applies to S1, S2, S5, S6 or S7.
- **S5, S6 and S7 follow the component kinds** (section "3.1") and are mutually exclusive: which of the three is available on a component type is decided by the metamodel, never by the command, and at most one of them ever is. Using the wrong one is `COMPONENT_NOT_ORDERED`, `COMPONENT_NOT_TYPED` or `SINGLETON_TAKES_NO_SELECTOR` (section "3.1"), never `INCOMPLETE_SELECTOR`. `entry` is the one component type for which none of the three is available — it is ordered, but by the dictionary rather than by this language (section "2") — and S4 is what selects it.
- **S6 is a spelling of S3, not a new lookup.** The natural identity property set of a typed component type is exactly `{type}` (section "3.1"), so `note^general` and `note[type = "general"]` are the same selector written two ways, and both are strategy-complete. `^t` is RECOMMENDED, being shorter and stating that the component type is a typed one; S3 remains available and is what a script writes when it wants the `type` predicate to sit beside a `has` refinement, which `^t` does not accept.
- **S7 is the absence of a selector**, and it is complete on its own: a singleton host has exactly one such child, so naming the type names the component. Since `category` is its only instance in the current metamodel, `on category of sense[...]` is the whole of it.

If a selector combines two strategies — for instance an `id` and a natural
identity property, or the deprecated `[index = n]` predicate together with any
other predicate — the validator MUST reject it with `DUPLICATE_SELECTOR`. If it
uses none — for instance a predicate list that does not cover the whole natural
identity property set, or a list made only of `has` predicates, which are a
refinement and never a strategy on their own — the validator MUST reject it with
`INCOMPLETE_SELECTOR`. A `SINGLETON-STEP` is the one step that uses no predicate
and is nevertheless complete, by S7; it never raises `INCOMPLETE_SELECTOR`.

`sense[gloss@en = "pig"]#2` and `note[text@en = "…"]^general` are syntax errors
(rejected by the parser).

A *filtering selector*, by contrast with an unique selector, is under none of these constraints: see section "5.1.4".

Example:

```text
sense[id = "pig-44"]
sense[gloss@en = "pig"]
variant[type="dialectal", target = sense[id = "pig-44"]]
media[url="http://www.example.org/Image.png"]
entry[form@tww = "mami", has-gloss@en="pig"]
entry[form@tww = "mami", has sense[gloss@en = "pig"]]
sense[gloss@en = "pig", has example[text@tww ~ "jefi"]]
example#1
note^general
trait^"CV pattern"
category
```

Of these, `entry[form@tww = "mami", has sense[...]]` is strategy S4 refined by a
`has` predicate, and `sense[gloss@en = "pig", has example[...]]` is strategy S3
refined by one, on a component type that is not an `entry`. The last three are
S5, S6 and S7: an ordinal on an ordered component, a type key on a typed one, and
the bare name of a singleton.

#### 5.1.4 Filtering selectors

A filtering selector — one of the three cases listed in section "5.1.1": the
parent side of a `within` / `/` link, a step inside a `has` predicate, or a
target step marked `each` / `all` / `*` — may contain:

- any property allowed on the component type, be it an identity property or not, in any number, with any of the predicate forms of section "5.1.2", and, on a multitext, any number of qualified values and the wildcard `@*`;
- no pseudo-property: `id`, `hn`, `has-gloss`, the ordinal `#n` and the type key `^t` are forbidden there, and raise `PSEUDO_PROPERTY_NOT_ALLOWED_IN_FILTER`. The `has` predicate is not a pseudo-property in this sense and is allowed, including nested.

The component kind (section "3.1") constrains a unique selector and does not
constrain a filtering one, because a filtering selector states a condition rather
than making a choice. Two consequences:

- a **typed** component may be filtered by any of its properties, `type` included, written out as an ordinary predicate: `within note[type = "general"]` and `has field[text@en ~ "^draft"]` are both legal. What is forbidden there is the *key* spelling `^t`, exactly as the ordinal `#n` is forbidden while a content predicate is not;
- a **singleton** step, which carries no selector when it is a unique selector (strategy S7), **may** carry an ordinary predicate list when it is a filtering one. This is the only way to ask a question about a singleton, and it is the idiom for querying the grammatical category of a sense:

```text
sense[has category[value = "Verb"]]
within category[value = "Noun"]
```

A filtering selector may match zero, one, or several components; what must be
unambiguous is the result of the whole chain, not the result of each step (see
section "7.4"). A filtering step on a singleton matches zero or one component,
never more, since the host has at most one.

#### 5.1.5 Resolution rules

A selector resolves to a set. The following rules are mandatory:

1. An empty result raises `NOT_FOUND`. The one exception is a target step carrying a multiplicity keyword (`each`, `all`, `*`), which succeeds on an empty result and applies the command to no component (section "5.6").
2. When a unique result is required, more than one result raises `AMBIGUOUS_REFERENCE`.
3. A command requiring one component must receive exactly one component, unless it carries an explicit multiplicity keyword (section "5.6").
4. Component type and parent type compatibility are checked before dictionary lookup.
5. Selector comparisons use the declared language and datatype.
6. Selectors are re-evaluated for every command, against the state left by the preceding commands (section "12.1").

### 5.2 Natural identity

**Definition.** For every component type `T`, the metamodel (section "4.1" and
Appendix A) defines a *natural identity property set* `I(T)` containing zero,
one, or two properties of `T`.

**Identity key.** Let `c` be a component of type `T` and `L` a language of the
relevant language set. The *identity key of `c` for `L`*, written `key_L(c)`, is
the tuple obtained by taking, for each property `p` of `I(T)` in the order of
the metamodel:

- the value of `p`, if `p` is scalar;
- the value of `p@L`, if `p` is a multitext.

`key_L(c)` is undefined when `I(T)` is empty, or when any of its multitext
components has no value for `L`. An undefined key never takes part in any
comparison.

**Uniqueness invariant.** Under a given parent, for every language `L`, no two
same-type siblings may have the same defined `key_L`. Every command that
creates, moves, or modifies a component MUST preserve this invariant, and MUST
raise `CANNOT_CREATE_DUPLICATE` when it would break it. Two senses may therefore
coexist under one entry with `gloss@en = "pig"` and `gloss@fr = "pig"`
respectively: the comparison is per language, and values in different languages
are never compared. Under different parents, two senses may freely have the same
qualified gloss.

**Consequences of an empty identity property set.** When `I(T)` is empty, the
uniqueness invariant is vacuous for `T`: duplicates among same-type siblings are
permitted, and no identity-based operation is available for `T`. Every rule of
this specification that requires "all the properties of the natural identity
property set" is therefore automatically inapplicable to such a component type,
and every rule that matches an existing component by identity key never matches
one. Two component types have an empty set in the current metamodel, for opposite
reasons: `entry`, because the dictionary may legitimately hold homophones — the
strategies available for selecting one are listed in section "5.1.3" (strategy
S4) and detailed in section "5.3.2" — and `category`, because it is a singleton
and there is never a second one to tell it apart from (strategy S7).

**Natural identity and the typed kind.** A **typed** component type (section
"3.1") is exactly a component type whose natural identity property set is the
single scalar property `type`. This is not a coincidence to be maintained by hand
in two places: the map-like behaviour of `trait`, `note`, `field` and
`translation` *is* the uniqueness invariant applied to `{type}`. Under one
parent, two `note` children may not share a `type`, which is what makes
`note[type = "general"]` — and its key spelling `note^general` — designate at most
one component without any further predicate.

**Natural, not persistent.** Identity properties are not a persistent,
invariant identity: the gloss of a sense can be changed, which changes its
identity key. The persistent identity is the `id` pseudo-property (section
"5.3.1").

In a **unique** selector (section "5.1.1"), a multitext identity property must be
given exactly one qualified value (section "5.5.2"), because strategy S3 is an
identity key for one language and two languages would be two keys. The
restriction bears on unique selectors only: in a **filtering** selector, a
multitext property may carry any number of qualified values and the wildcard
`@*`, whether it is an identity property or not (section "5.4.1", roles R2 and
R4). The following example is therefore rejected with `DUPLICATE_SELECTOR`, the
sense being selected — in an `of` clause, on the strict axis — by two qualified
values:

```text
set text = "My field"
on field[type="review"]
of example#1
of sense[gloss@en = "pig", gloss@fr = "porc"]
of entry[form@tww = "mami"]
```

When a qualified property is given as a lookup predicate, for instance
`gloss@en` in the following example, then a sense will match if it has a
sub-entry for this language on the gloss property and if the qualified value for
this language is the same as the value given in the lookup predicate ('pig' in
the following example), it will not take into account the fact that other
sub-entries for other languages exist or not.

```LiftPatchRef
set text = "My field"
on field[type="review"]
of example#1
of sense[gloss@en = "pig"]
of entry[form@tww = "mami"]
```

#### 5.2.1 Identity on a multitext property

When a multitext property is a natural identity property, the uniqueness means that, when for each language in the relevant language set (meta or object), in the group of components that have a value set for this language and this property, there is no duplicate. Unset qualified value does not count in the identity checking.

### 5.3 Pseudo-properties

These pseudo properties are defined. They do not participate in component identity.

| Name | Kind | Allowed use |
|---|---|---|
| `id` | system-managed persistent identifier | selector and reference values only |
| `hn` | dictionary-assigned entry-disambiguation key | entry selector only, with qualified `form` |
| `has-gloss@M` | selector predicate, shorthand for `has sense[gloss@M = …]` | entry selector only, with qualified `form` |
| `#n` (ordinal) | positional selector, deprecated alias `index` | ordered components other than `entry`, unique selectors only |
| `^t` (type key) | key selector, shorthand for `[type = "t"]` | typed components, unique selectors only |

All keywords, component names, property names and pseudo-property names of both
surface syntaxes are **lower-case and case-sensitive**. `id`, not `ID`; `entry`,
not `Entry`. Language codes and property values are compared literally, except
where a predicate explicitly asks for case-insensitive matching (`~i`).

They are described in the following subsections.

#### 5.3.1 IDs

In a dictionary, `entry` and `sense` also have an `id`. This `id` is a persistent
identity. The ids are created automatically by the system, they can be referred
to but not created manually, updated, upserted, deleted or cleared. They are
globally unique, stable across moves, and are not reused after deletion of a
component. An `id` can be used as a lookup predicate in a selector, except in a
filtering selector (`within`, `has`).

Example:

```text
sense[id = "pig-44"]
```

Ids are set automatically during creation and therefore cannot be set,
initialized during creation, or upserted. The ids are system-managed and always
present after initialization.

Here is a normative summary of the id rule:

- The `id` cannot be set, deleted, initialized during creation, or cleared.
- The `id` can be used as a lookup predicate in a command selector and in a parent selector on the strict axis (`of`, `under`, `on`, `!`); it is forbidden in a filtering selector (`within`, `has`).
- The `id` can be used as a lookup predicate with the `move` and `delete` commands.
- The `id` is the only way to designate a component by a value that the script did not itself choose; a component created by the running script is designated by a label instead (section "6.3").


#### 5.3.2 Disambiguating homophonous entries: `hn` and `has`

Homophones are pervasive in language and therefore in dictionary entries. Since
entries are not grouped in small sets under parents, but are all directly under
the root, they are not easy to select.

The `form` property CAN be used alone in a selector for an `entry`, but if there are several matches (i.e. homophones), an error 'AMBIGUOUS_REFERENCE' will be raised. If there is only one match, the selector succeeds.

For `entry`, only the `id` can uniquely identify an instance by itself. However, ids are arbitrary and not very human-readable. Ids CAN be used in a selector, but are not a satisfying solution from a practical point of view.

For practical purposes, two refinements of strategy S4 are offered, which are
expressed together with the `form` property in order to disambiguate homophone
entries and select an `entry` uniquely: the `hn` pseudo-property and the `has`
child-existence predicate (of which `has-gloss` is a shorthand).

They are a selecting mechanism only. They are not natural identity properties,
since they refer to a context outside of the `entry` itself: `hn` depends on the
other entries of the dictionary, and `has` depends on the children of the entry.
They are therefore never initializers, and never applied with `set` semantics.
Where each of them may appear is given once, in the table of section "5.4.2";
the two subsections below define what each of them means.

##### 5.3.2.1 The homophone number (`hn`)

All homophonous entries share the same qualified form but have a different
homophone number (`hn`), so that the pair (qualified form, `hn`) selects one
entry at any given state of the dictionary. The value is an integer, written
without quotes.

**Model.** For the purposes of this language, `hn` is an opaque lookup key
assigned by the dictionary. The normative model is minimal and deliberately
independent of how the dictionary computes it:

1. At any moment, every `entry` of the dictionary has exactly one `hn`, an integer greater than or equal to 1. An entry that has no homophone also has an `hn`.
2. At any moment, for a given qualified form value, no two entries have the same `hn`.
3. The value of `hn` is chosen by the dictionary. The language neither assigns it nor predicts it.

**Matching rule.** A predicate `hn = N` in an entry selector matches the entry
whose qualified `form` matches the accompanying `form` predicate and whose
dictionary-assigned `hn` is exactly `N`. Whether that entry has homophones is
irrelevant: it plays no part in the matching rule. 

**Usage rules.** Where `hn` may and may not appear is given by the table of
section "5.4.2"; using it elsewhere raises `COMMAND_NOT_ALLOWING_HN`, or
`PSEUDO_PROPERTY_NOT_ALLOWED_IN_FILTER` in a filtering selector. One of its
restrictions needs its own justification:

- `hn` is **forbidden in the initializer list of an `upsert`**, that is, as a predicate of the component being upserted. `upsert` has a create branch, and `hn` cannot be given to a component that does not exist yet: asking to create "the entry that is the second homophone" is meaningless. Either the entry with this `hn` exists, in which case `ensure` or a plain selector applies, or it does not, in which case `create` applies. Use `has`/`has-gloss` when an `upsert` on an entry must be disambiguated (section "8.2"). `hn` remains allowed in the *parent* selector of an `upsert` command (`upsert sense(...) under entry[form@tww = "mami", hn = 1]`), which is a pure selection.

In a selector, `hn` cannot be used alone, but always together with the `form` property:

```LiftPatchRef
set text@en = "My field"
on field[type="review"]
of example#1
of sense[gloss@en = "pig"]
of entry[form@tww = "mami", hn = 1]
```

- if `hn` is used alone, without the `form` property, an error `HN_CANNOT_BE_USED_ALONE` is raised. This is a static error.
- if the value given for `hn` is not an integer, or is lower than 1, an `ILLEGAL_HN` error is raised. This is a static error.
- if no entry matches the combination of the `form` predicate and the `hn` predicate, the ordinary selector error `NOT_FOUND` is raised (section "5.1.5", rule 1). This is a dynamic error. The `form` predicate and the `hn` predicate are one selector using one strategy (S4, section "5.1.3"), not two successive lookups, so "the form exists but not with this homophone number" is not a distinguishable condition: it is simply a selector that matched nothing.

**Stability caveat.** Because the dictionary may reassign `hn` when a form is
added or changed (see below), a script MUST NOT assume that an `hn` observed
before a command that modifies a `form` is still valid after it. Within a single
script execution, an `hn` is guaranteed to remain valid as long as no command of
that script modifies the `form` of any entry. Scripts that must be robust
against reassignment SHOULD disambiguate with `has` rather than with `hn`.

The remainder of this section is **informative**. Only the model (rules 1 to 3),
the matching rule, the usage rules and the error conditions given above are
normative. The exact algorithm of `hn` creation and management is not in the
scope of this DSL: it is managed by the dictionary library, and the DSL
implementation uses `hn` as a value read from the `entry`.

- numbers are generated by `entry` creation order, it means that, if no modification have been made to the form afterward, the order of hn reflect the chronological order of the creation of the `entry`.
- for a given `entry` with a given 'hn', the same 'hn' value is valid whatever qualified `form` is referred to, i.e. whatever the object language for which this entry has a form sub-entry. Therefore, if an `entry` is referred with a qualified `form` and a `hn`, the same `entry` will be returned if another existing qualified `form` is referred to. For instance, if an `entry` has the two forms `form@tww="mami"` and `form@tpi="pik"`, and if there is another existing entry with `form@tww="mami"` and another entry with `form@tpi="pik"`, then the same hn (say, 3), will return the same first entry, whether we use it with `form@tww="mami"` or with `form@tpi="pik"`.
- 'hn' can be automatically reassigned by the dictionary, depending on the values of the form of the `entry` (`set`, `clear`ed, `update`d).
   - as long as no qualified form value is changed or added in an `entry`, the 'hn' value is guaranteed to remain the same.
   - a change in the form of an entry can result in a different 'hn' value being assigned, and that new value will be used for subsequent lookups even with the qualified form property that were existing before the change.

For instance, let's consider an entry with hn = 1 and form@tww = "mami" and form@tpi = "pik". The same 'hn' is valid with form@tww:

```text
entry[form@tww = "mami", hn = 1]
```

and for form form@tpi = "pik":

```text
entry[form@tpi = "pik", hn = 1]
```

However, if the value `form@en = "pig"` is added to the `entry`, and if it
happens that there are already three homophones forms with form@en = "pig", then
the hn value will be reassigned to 4 for this entry. `hn = 4` should now be used
even with the lang `tww` and `tpi` in order to refer to this entry.

In other words, when at least one of the qualified forms of an entry belongs to a
homophone set, the dictionary assigns to that entry a homophone number that is
not yet in use in any of the homophone sets its qualified forms belong to.

In a homophone set in a given language, `hn` are not guaranteed to be contiguous.

##### 5.3.2.2 The `has` child-existence predicate and its `has-gloss` shorthand

In order to deal with homophone issues, a component may be selected by a
condition bearing on its children. The general form is the `has` predicate:

```text
has STEP
```

It is satisfied by a component having **at least one** direct child matching
`STEP`, where `STEP` is a filtering selector (section "5.1.4"). `has` predicates
may be nested, and several `has` predicates may appear in the same predicate
list; they are then combined by conjunction, each of them being satisfied
independently:

```text
entry[form@tww = "mami", has sense[has category[value = "Verb"], has example[text@tww ~ "jefi"]]]
```

`has` is an existential predicate: it filters the component it is attached to,
and it never designates the matched child. To operate *on* the child, use the
`within` axis (section "7.4"), of which `has` is the predicate counterpart:
`C within P[…]` selects `C`, whereas `P[has C]` selects `P`.

The most frequent case by far — an entry disambiguated by the gloss of one of
its senses — has a shorthand, the `has-gloss` pseudo-predicate:

```text
has-gloss@L = V     ≡     has sense[gloss@L = V]
```

The `has-gloss` pseudo-predicate value must be qualified with a language code (or use the implicit default meta language) and
its value must be a string matching the qualified gloss value of one of the
entry senses. Unlike the general `has` predicate, `has-gloss` is restricted to
`entry` selectors and must be accompanied by a `form` predicate. 

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
most probably not a desirable linguistic analysis. However, it is not
enforced. As for any selection operation, if the combination of a qualified form
value and a has-gloss pseudo-property selects several matches, an
'AMBIGUOUS_REFERENCE' error MUST be raised. If no entry matches, the error is `NOT_FOUND`.

has-gloss cannot be used alone, without a form, to select an entry. If `has-gloss` is used alone, a `HAS_GLOSS_CANNOT_BE_USED_ALONE` exception is raised.

In the following example, without has-gloss, the child (sense) selector will not
implicitly disambiguate an ambiguous parent (entry) selector. The fact that the
command refers to a sense with gloss `"pig"` must not silently determine which
entry is intended. Therefore the following command must fail if several entries
have `form@tww = "mami"`, even if there is only one entry with `form@tww =
"mami"` and that have a sense with `gloss@en = "door"`:

```LiftPatchRef

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

```LiftPatchRef
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

Another option could be to use `within`, that allows existential filtering, as described in section "7.4" below:

```LiftPatchRef
create example(
  text@tww = "a mami jefi"
)
under sense[
  gloss@en = "pig"
]
within entry[
  form@tww = "mami"
]
```

Where `has` and `has-gloss` may appear is given by the table of section "5.4.2";
using either with a command that does not allow it, `create` in particular,
raises `COMMAND_NOT_ALLOWING_HAS`, which is the only error code for that
condition. One consequence is specific to `upsert`: if its create branch runs,
`has` / `has-gloss` is not taken into account, and an `entry` is created with the
given form even if that produces a homophone (section "8.2.3").

#### 5.3.3 Ordinal selectors

A component may also be selected by its position. The ordinal is written as a
suffix on the step, introduced by `#`, and its value is an integer without
quotes:

```text
sense#2
example#1
```

Because the ordinal is a step suffix and not a predicate, it cannot be combined
with a predicate list in its `#n` form: `sense[gloss@en = "pig"]#2` is a syntax
error. Its deprecated alias `[index = n]` *is* a predicate, and can therefore be
written beside another one; that combination is not a syntax error but a
`DUPLICATE_SELECTOR`, since it states two selection strategies (section
"5.1.3").

- The ordinal counts **same-type siblings only**: `example#2` is the second `example` child of its parent, whatever other children the parent may have.
- The ordinal starts at 1.
- The ordinal can be used on an **ordered** component type (section "3.1") other than `entry`, and on no other. On a typed or a singleton component type it raises `COMPONENT_NOT_ORDERED`, a static error: `note#1` asks for the first of a collection that has no order, and `category#1` for the first of something there is only ever one of. On `entry` it raises the same code, the entry list being ordered by the dictionary and not by this language (section "2").
- The ordinal is not a property: it cannot be set by any initializer, cannot be the target of `set`, `update` or `clear`, cannot appear in a `create` initializer list, and is forbidden with `upsert` (a component that does not exist yet has no position). It can be used in the command selector of `delete`, `move` and `ensure`, and in a parent selector on the strict axis.
- The ordinal is forbidden in a filtering selector (`within`, `has`): `PSEUDO_PROPERTY_NOT_ALLOWED_IN_FILTER`.
- If the ordinal is lower than 1, `ILLEGAL_ORDINAL` is raised (static error). If it is greater than the number of same-type siblings, `INDEX_OUT_OF_BOUNDS` is raised (dynamic error).
- Ordinals are re-evaluated for each command, against the state of the dictionary left by the preceding commands. Two consecutive `delete example#1` under the same parent therefore delete two different components.

The predicate form `[index = n]` is accepted as a **deprecated** alias of `#n`,
with the same semantics. It will be removed in a future version and SHOULD NOT
be used in new scripts.

In the following example, the second sense in the sense list is selected:

```LiftPatchRef
create example(
  text@tww = "a mami jefi"
)
under sense#2
of entry[
  form@tww = "mami",
  has-gloss@en = "pig"
]
```

The previous command raises `INDEX_OUT_OF_BOUNDS` if there are not at least two
senses under that entry.

In order to change the position of a component, use the `move` command.

#### 5.3.4 Type selectors

A **typed** component (section "3.1") lives in a map keyed by its `type`
property, and is selected by its key. The *type key* is written as a suffix on
the step, introduced by `^`, and its value is either an unquoted name — a `name`
in LiftPatchRef, a bare word in LiftPatchShort (Part 3, section "6.1.1") — or a
quoted string:

```text
trait^editor
note^general
field^borrowing
translation^free
trait^"CV pattern"
```

`trait^editor` designates the `trait` child whose `type` is `"editor"`, exactly
as `example#1` designates the first `example` child. The two suffixes are
parallel devices for the two non-singleton kinds, and each is available on its
own kind only.

**The type key is a spelling of the natural-identity lookup.** `note^general` and
`note[type = "general"]` are the same selector and resolve identically; `^t` is
strategy S6 and the bracketed form is strategy S3 (section "5.1.3"). Nothing
depends on which is written, and `^t` is RECOMMENDED for its concision and
because it states, at the point of use, that the component type is a typed one.

- The type key is written **after** the component name and carries the whole selection: because it is a step suffix and not a predicate, it cannot be combined with a predicate list. `note[text@en = "…"]^general` is a syntax error, exactly as `sense[gloss@en = "pig"]#2` is.
- The value is compared **literally**, as an ordinary string equality on the `type` property. A type containing a space, a slash, a `^` or any other character the unquoted form does not admit is written between quotes. There is no regular-expression or case-insensitive form of `^t`: to match a type loosely, write the predicate out, `note[type ~i "^gen"]`, in a filtering selector.
- The type key can be used on a **typed** component type and on no other. On an ordered or a singleton component type it raises `COMPONENT_NOT_TYPED`, a static error: `example^free` and `category^noun` name a key where there is none.
- The type key is not a property: it cannot be set by any initializer, cannot be the target of `set`, `update` or `clear`, and cannot appear in a `create` or `upsert` initializer list. **It selects and never creates**, exactly like the ordinal. To create a typed component, write its `type` as an ordinary initializer: `create note(type = "general", text@en = "…")`. Writing `create note^general(...)` is a syntax error.
- The type key can be used in the command selector of `delete` and `ensure`, and in a parent selector on the strict axis. It cannot be used on `move`, which no typed component accepts at all (`COMPONENT_NOT_ORDERED`, section "8.5").
- The type key is forbidden in a filtering selector (`within`, `has`, or a step marked `each` / `all` / `*`): `PSEUDO_PROPERTY_NOT_ALLOWED_IN_FILTER`. Write the `type` predicate out there, `within note[type = "general"]`, which is legal and says the same thing (section "5.1.4").
- If no same-type sibling has that `type`, the ordinary `NOT_FOUND` is raised (section "5.1.5", rule 1). The uniqueness invariant guarantees that no more than one can, so a type key never raises `AMBIGUOUS_REFERENCE`.
- Type keys are re-evaluated for each command, against the state of the dictionary left by the preceding commands, exactly as ordinals are.

In the following example, the `note` whose type is `"etymology"` is given a new
text:

```LiftPatchRef
set text@en = "borrowed from Tok Pisin"
  on note^etymology
  of entry[form@tww = "mami", hn = 1]
```

which is the same command as:

```LiftPatchRef
set text@en = "borrowed from Tok Pisin"
  on note[type = "etymology"]
  of entry[form@tww = "mami", hn = 1]
```

Since a typed component has no position, there is no `move` and no `at` clause
for it: to change the key of a typed component, write its `type` with `set`,
which re-keys it in place and is checked against the uniqueness invariant like
any other identity assignment (section "5.2").

### 5.4 Command applicability

Applicability is decided in two independent steps, and each step has its own
table:

1. **Availability.** Is the property defined on this component type at all? This is decided by the metamodel (section "4.1" / Appendix A). A property that is not available on the component type raises `PROPERTY_DOES_NOT_EXIST_ON_COMPONENT_TYPE`, whatever the command.
2. **Applicability.** Given the *role* of that property on that component type, in which clause of which command may it appear? This is decided by the table of section "5.4.1" for real properties, and by the table of section "5.4.2" for pseudo-properties.

#### 5.4.1 Applicability by property role

Every property of the metamodel has exactly one role, obtained from three
metamodel columns — membership in the natural identity property set, `Required
at creation`, and `Datatype`. The six roles below form a partition: each
(component, property) pair matches exactly one row, so the table yields a single
verdict.

| Role | `create` initializer | `upsert` initializer | unique selector (the column of section "5.4.2": command selectors, including a property command's `on` step, and parent selectors on the `!` axis) | filtering selector (parent side of `within` / `/`, inside `has`, or a step marked `each` / `all` / `*`) | `set` | `update` | `clear` |
|---|---|---|---|---|---|---|---|
| **R1** identity, scalar | required | required | required (part of strategy S3) | allowed | allowed, subject to the uniqueness invariant | allowed, subject to the uniqueness invariant | forbidden — `CANNOT_CLEAR_IDENTITY_PROPERTY` |
| **R2** identity, multitext | required, at least one qualified value | required, at least one qualified value | required (part of strategy S3), exactly one qualified value | allowed, any number of qualified values, `@*` allowed | exactly one qualified value, subject to the uniqueness invariant | exactly one qualified value, subject to the uniqueness invariant | `@L` only, and only if another qualified value remains; `@*` forbidden |
| **R3** required, non-identity, scalar | required | required | forbidden | allowed | allowed | allowed if set | forbidden — `CANNOT_CLEAR_REQUIRED_PROPERTY` |
| **R4** required, non-identity, multitext | required, at least one qualified value | required, at least one qualified value | forbidden, except `entry.form`, which is strategy S4 | allowed, any number of qualified values, `@*` allowed | exactly one qualified value | exactly one qualified value | `@L` only, and only if another qualified value remains; `@*` forbidden |
| **R5** optional, non-identity, scalar | allowed | allowed, `set` semantics | forbidden | allowed | allowed | allowed if set | allowed |
| **R6** optional, non-identity, multitext | allowed, one or more qualified values | allowed, `set` semantics | forbidden | allowed, any number of qualified values, `@*` allowed | exactly one qualified value | exactly one qualified value | `@L` or `@*` |

Reading rules:

- "required" means: the command is rejected with `MISSING_REQUIRED_PROPERTY` (for `create` and `upsert`) or `INCOMPLETE_SELECTOR` (for a selector) if the property is absent.
- The kind rules of section "3.1" are checked before this table: on a **singleton** step in a unique position the whole selector is refused with `SINGLETON_TAKES_NO_SELECTOR`, whatever the role of the property written in it.
- "forbidden" in a selector column means that the predicate is rejected with `PREDICATE_NOT_ALLOWED_IN_UNIQUE_SELECTOR`: a unique selector uses exactly one selection strategy (section "5.1.3") and carries no additional predicate, the only admitted supplement being the `has` refinement of strategies S3 and S4 and the `hn` refinement of S4, both of which are part of the strategy rather than additions to it.
- R3 and R4 are "required" for `upsert` even though the select branch may make them unnecessary: which branch will run is not known statically, and the create branch must be able to satisfy section "6.1" rule 1.
- The `clear` column implements one invariant: **a property that the metamodel declares required may never become unset**, and an identity property may never become unset. See the decision table in section "9.3.1".

#### 5.4.2 Applicability of pseudo-properties and labels

Two columns of this table are easily confused and are kept apart deliberately:
the **unique selector** column governs the *step* that names the component a
command operates on, including the step of a property command's `on` clause,
while the last column governs only the *property* a property command writes. The
two have opposite verdicts — `set form = … on entry[id = "entry-42"]` is legal,
`set id = …` is not.

| | `create` initializer | `upsert` parenthesis | unique selector — the command selector of `ensure`, `delete`, `move` and of a property command's `on` step, the select branch of `upsert`, the step of `at before` / `at after`, and any parent selector on the `!` axis | filtering selector — parent side of `within` / `/`, inside `has`, or a step marked `each` / `all` / `*` | as the property **written** by `set` / `update` / `clear` |
|---|---|---|---|---|---|
| `id` | forbidden | forbidden — `ID_NOT_ALLOWED_ON_UPSERT` | allowed, alone, on `entry` and `sense` only | forbidden — `PSEUDO_PROPERTY_NOT_ALLOWED_IN_FILTER` | forbidden |
| `hn` | forbidden | forbidden in the initializer list — `COMMAND_NOT_ALLOWING_HN`; allowed in the parent selector | allowed on `entry` only, together with `form` | forbidden — `PSEUDO_PROPERTY_NOT_ALLOWED_IN_FILTER` | forbidden |
| `has-gloss` | forbidden — `COMMAND_NOT_ALLOWING_HAS` | select branch only, on `entry`, together with `form`; never applied in the create branch | allowed on `entry` only, together with `form` | forbidden — `PSEUDO_PROPERTY_NOT_ALLOWED_IN_FILTER` | forbidden |
| `has STEP` | forbidden — `COMMAND_NOT_ALLOWING_HAS` | select branch only, on any component type; never applied in the create branch | allowed, on any component type, as a refinement of strategy S3 or S4 (section "5.1.3") | allowed, including nested | forbidden |
| ordinal `#n` | forbidden | forbidden | allowed, alone, on an **ordered** component type other than `entry`; elsewhere `COMPONENT_NOT_ORDERED` | forbidden — `PSEUDO_PROPERTY_NOT_ALLOWED_IN_FILTER` | forbidden |
| type key `^t` | forbidden | forbidden | allowed, alone, on a **typed** component type; elsewhere `COMPONENT_NOT_TYPED` | forbidden — `PSEUDO_PROPERTY_NOT_ALLOWED_IN_FILTER` | forbidden |
| singleton step | not applicable: a singleton is never created | not applicable: a singleton is never upserted | required, alone, on a **singleton** component type; anything after the name is `SINGLETON_TAKES_NO_SELECTOR` | not applicable: in a filtering selector a singleton step carries an ordinary predicate list (section "5.1.4") | not applicable |
| label `$name` | not a predicate; introduced by `as $name` | not a predicate; introduced by `as $name` | allowed, as a whole step, including as the step of an `on` clause | forbidden | forbidden |

Four cells of this table are worth reading twice, because they are the ones that
are easy to get wrong and each is exercised by the conformance corpus:

- `id` and the ordinal are allowed as the step of an `on` clause: `set form = { tww: "mami" } on entry[id = "entry-42"]` and `set text@tpi = "…" on example#1 within sense[...]` are both legal (cases C-018 and C-031);
- so are the type key and the singleton step: `set text@en = "…" on note^general of entry[...]` and `set value = "Noun" on category of sense[...]` are the ordinary way to write on a typed and on a singleton component;
- none of them may be *written*: `set id = …`, `update hn = …`, `clear #2`, `set ^general = …` are all forbidden, whatever the component type. The `type` property of a typed component, by contrast, **is** an ordinary property and may be written: `set type = "general" on note^etymology` re-keys the note (section "5.3.4");
- a label is a whole step and never a predicate, so `on $pig` is legal and `[label = $pig]` does not exist.

### 5.5 Language qualifier rules

The language defines three distinct forms.

#### 5.5.1 Qualified assignment

For `create`, `upsert`, `set`, and `update`:

```text
set gloss@en = "pig"
set gloss = "pig"       # equivalent to gloss@<default-meta-language>
```

An omitted qualifier uses the applicable default language.

The following should be invalid:

```text
set value@en = "noun"
set target@en = "entry-42"
```

##### Multi-language literals

Assigning several languages of the same multitext property one by one is
verbose. A *multi-language literal* assigns several qualified values in one
place:

```text
MULTITEXT-LITERAL ::= '{' LANG ':' STRING { ',' LANG ':' STRING } '}'
```

```LiftPatchRef
set form = { tww: "mami", tpi: "pik" }
  on entry[id = "entry-42"]

create example(text = { tww: "a mami jefi", tpi: "mi shutim pik" })
  under sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

A multi-language literal is pure surface sugar: the command above is defined as
being exactly equivalent to the sequence of the corresponding single-qualifier
assignments, taken in the order of the literal, and it inherits all their rules
(availability, default languages, uniqueness invariant, duplicate detection).

- The property must be a multitext, otherwise `LANG_KEY_NOT_SUPPORTED_ON_SCALAR` is raised.
- The property name must be written **without a qualifier**: `form = { … }`, never `form@tww = { … }`. A qualified name raises `QUALIFIER_ON_MULTITEXT_LITERAL` — the literal carries its own language keys, and a qualifier in front of it can only contradict them. The wildcard form `form@* = { … }` raises `WILDCARD_NOT_ALLOWED`.
- Repeating a language inside one literal raises `DUPLICATE_PROPERTY`.
- A multi-language literal is allowed wherever an assignment is allowed (`create`, `upsert`, `set`, `update` initializers and targets). It is **not** a selector predicate.

#### 5.5.2 Qualified selection

In a **unique** selector (section "5.1.1"), an identity multitext property must
use exactly one qualified value:

```text
sense[gloss@en = "pig"]
example[text@tww = "The dog ran"]
```

An unqualified multitext identity property uses the default object or meta language:

```text
sense[gloss = "pig"]    # equivalent to gloss@<default-meta-language>
```

In a **filtering** selector the restriction does not apply: any multitext
property, identity or not, may carry several qualified values and the wildcard
`@*`, and the predicates are combined by conjunction like any others (sections
"5.1.4" and "5.4.1", roles R2, R4 and R6). Both of the following are legal:

```text
within sense[gloss@en = "pig", gloss@fr = "cochon"]    # filtering: both must match
within sense[gloss@* = "pig"]                          # filtering: some language is "pig"
```

#### 5.5.3 Qualifiers in `clear`

On a multitext property, `clear` requires an **explicit** qualifier: either a
language code, or the wildcard `@*`.

```text
clear definition@en   # remove one language value
clear definition@*    # remove all language values
clear definition      # ILLEGAL: MISSING_LANGUAGE_QUALIFIER
```

The qualifier is mandatory, so there is nothing for the default language to
apply to: `clear` is the one command to which section "2.1" does not extend, and
it needs no exception because it addresses no single language implicitly. On a
scalar property, no qualifier is allowed and none is needed:

```text
clear value
```

Which qualifier is *accepted* is a question of syntax, and is settled here.
Whether a syntactically valid `clear` then succeeds depends on the role of the
property and on its current state, and is settled by one normative table, the
decision table of section "9.3.1".

### 5.6 Multiplicity: `each` and `all`

By default every command operates on exactly one component, and any ambiguity is
an error. This makes a whole class of ordinary editorial operations
inexpressible — "give every sense of this entry the grammatical category Noun", "delete
every example of this sense". Rather than weakening the default, the language
lets a script *state* that it intends to operate on several components.

A multiplicity keyword may be written immediately before the **target step** of a
command:

```LiftPatchRef
delete all example[...] under sense[gloss@en = "pig"] of entry[form@tww = "mami"]

set value = "Noun"
  on each category
  of sense[...]
  of entry[form@tww = "mami"]
```

- `all` and `each` are synonyms and have identical semantics. `all` reads better with `delete`, `each` with `set`, `update` and `clear`.
- A multiplicity keyword is allowed **only** on the target step of `delete`, `set`, `update` and `clear`. It is forbidden on `create`, `upsert`, `ensure` and `move`, and on every parent step, where it raises `MULTIPLICITY_NOT_ALLOWED`.
- `each` / `all` are available on a **typed** component, whose same-type siblings are several even though they are unordered: `delete all note[type ~ "^draft"]` is legal and ordinary.
- On a **singleton** target step the keyword is written on the step, as everywhere else, but what it distributes over is the **host**: a host has exactly one such child, so "every category of these senses" can only mean "the category of every one of these senses". A marked singleton step therefore carries no filter of its own — it has nothing to filter — and makes the step naming its host the filtering selector instead. In `set value = "Noun" on each category of sense[...] of entry[...]`, the `sense[...]` step may match several senses, it is subject to the rules of section "5.1.4" rather than those of a parent selector, and the command writes on the category of each sense it matches. The steps above the host are unaffected and keep their own rules. This is the only construct in which the marked step and the filtering step are not the same step, and it exists because a singleton and its host are in one-to-one correspondence.
- A marked target step is a *filtering selector* (section "5.1.4"): it may carry any predicate, and it is not required to use a selection strategy. The steps above it in the chain are unaffected and keep their own rules.
- The marked step may match zero, one, or several components. **Matching none is not an error**: the command succeeds and applies to no component, producing no effect. This is the one place in the language where an empty result does not raise `NOT_FOUND` (section "5.1.5", rule 1), and it is deliberate: `all` and `each` say "however many there are", so a cleanup line such as `delete all example[text@tww ~ "^draft"]` must be runnable on a sense that happens to have no draft example, and a script that ends with such a line must be re-runnable. Raising `NOT_FOUND` here would make every `all` / `each` command require the editor to know the answer before asking the question.
- The exemption covers the marked step **only**. The steps above it keep their ordinary rules, so a missing or ambiguous *parent* still raises `NOT_FOUND` or `AMBIGUOUS_REFERENCE`: in `delete all example[...] under sense[gloss@en = "pig"] of entry[form@tww = "mami"]`, the sense and the entry must each exist and be unambiguous, and only the set of examples may be empty.
- The matched components are processed in document order: for a given parent, in the order of the same-type sibling list; parents themselves in document order.
- The command remains atomic: either every application succeeds, or the command has no effect. A command that applies to no component trivially succeeds.
- Every per-component rule still applies to every application. `delete all` therefore deletes each matched component with its descendants, and `set ... on each` must preserve the uniqueness invariant for every component it touches. This cuts both ways for `update`: `update definition@fr = "…" on each sense[...]` fails as a whole — and changes nothing — as soon as one matched sense has no French definition, since `update` requires its target to be set (section "9.2"). Use `set` for "wherever it applies", or narrow the step with `exists(definition@fr)`.

Without a multiplicity keyword, a target step matching more than one component
raises `AMBIGUOUS_REFERENCE`, unchanged.

## 6. Creation: initializers, position, labels

The parenthesized values are initializers. They do not select an existing
component. On the contrary, square brackets select existing components, they do
not create new ones.

Multiple property initializers, separated by commas, are allowed (note that the
following example must fail if several homophones entries have the form "mami"
for the lang "tww"):

```LiftPatchRef
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

### 6.1 Initializer rules

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
   
5. `ensure` has **no** initializer list: it is an assertion and takes a selector between square brackets (section "8.3"). Writing `ensure component(...)` is a syntax error.
6. `upsert` requires all the properties of the natural identity property set of the component type, and applies every other initializer with `set` semantics (section "8.2").
7. `create` checks the uniqueness invariant (section "5.2") after all initializers are resolved. A component type with an empty natural identity property set — `entry` in the current metamodel — has no invariant to check, and `create` never rejects it as a duplicate.
8. A **singleton** component type (section "3.1") has no initializer list at all, because it is never created: `create category(...)` and `upsert category(...)` raise `SINGLETON_CANNOT_BE_CREATED_OR_DELETED`, a static error. Its properties are given with `set` once its host exists.
9. The ordinal `#n` and the type key `^t` are selectors and never initializers: `create note^general(text@en = "…")` is a syntax error, and the `type` of a typed component is written as an ordinary initializer, `create note(type = "general", text@en = "…")` (sections "5.3.3" and "5.3.4").

### 6.2 The `at` position clause

A newly created component is inserted into the list of its same-type siblings.
The optional `at` clause states where:

```text
AT-CLAUSE ::= 'at' 'beginning'
            | 'at' 'end'
            | 'at' 'index' INTEGER
            | 'at' 'before' STEP
            | 'at' 'after' STEP
```

- The index is 1-based and counts same-type siblings only. The valid insertion range is `1..count+1`. An index **lower than 1** is decidable from the text of the command and raises `ILLEGAL_ORDINAL`, a static error, exactly as the ordinal `#0` does (section "5.3.3"); an index **above the upper bound** depends on the dictionary and raises `INDEX_OUT_OF_BOUNDS`, a dynamic error (see 8.5).
- `at before STEP` and `at after STEP` place the component relative to an existing same-type sibling. These forms are RECOMMENDED over `at index n`, which breaks as soon as the sibling list changes.
- **When the `at` clause is omitted, the component is created at the last position**, exactly as with `at end`.
- The `at` clause is available on `create`, on `upsert` (where it applies only if the create branch runs), on an embedded initializer, and on a command written inside a block. It is mandatory on `move` (section "8.5"), which has no default.
- The `at` clause is forbidden on `ensure`, which creates nothing.
- The `at` clause is forbidden on a command targeting an `entry` — `create entry(...) at ...` and `upsert entry(...) at ...` are syntax errors. The dictionary, not the script, orders the entry list (section "2"), so there is no position for the clause to designate.
- The `at` clause is available on **ordered** component types only (section "3.1"). On a **typed** component it raises `COMPONENT_NOT_ORDERED`, a static error — `create note(type = "general", text@en = "…") under entry[...] at beginning` names a position in a map — and on a **singleton** the question does not arise, since a singleton is never created at all.

#### 6.2.1 The step of `at before` / `at after`

The `at` clause is not a selector: it is a positioning clause of `create`,
`upsert` and `move`, and it does not designate the component the command
operates on. But it contains a `STEP`, and that step is resolved like any other
step, under rules that are stated here rather than in section "5.1" because they
apply nowhere else:

- The step is resolved **among the same-type siblings under the destination parent**, and nowhere else: it is not a chain, it takes no axis, and it never leaves that sibling list. Since the `at` clause is available on ordered component types only, that step always names an ordered component, and its usual strategy is the ordinal.
- Its component type, when written, must be the type of the component being placed; it may also be omitted in the concise syntax (Part 3, section "5.3"). A different type is a `SYNTAX_ERROR`: no parentage is in question — both components would be legal children of the same parent — and what is wrong is the command, which names a sibling list the component being placed does not belong to.
- It must resolve to **exactly one** sibling: `NOT_FOUND` if none matches, `AMBIGUOUS_REFERENCE` if several do.
- Because it must resolve uniquely, it obeys the rules of a *command selector* (section "5.1.3"): one selection strategy, no additional predicate, and none of the filtering-only predicates (`!=`, `~`, `~i`, `exists`, `absent`), which raise `PREDICATE_NOT_ALLOWED_IN_UNIQUE_SELECTOR`. The ordinal strategy is the usual one here: `at after #2`.
- On `move`, the step is resolved **before** the moved component is removed from its list. A step that denotes the moved component itself asks for the position it already occupies, and raises `MOVING_TO_CURRENT_POSITION`.

### 6.3 Labels

A command that creates or resolves a component may bind it to a *label*, so that
later commands refer to it directly instead of selecting it again:

```text
create COMPONENT(initializers) [under PARENT] [at POSITION] as $NAME
upsert COMPONENT(initializers) [under PARENT] [at POSITION] as $NAME
ensure COMPONENT[SELECTOR]     [under PARENT]               as $NAME
COMPONENT[SELECTOR] [of PARENT ...] as $NAME { ... }          # block header
```

```LiftPatchRef
create entry(form@tww = "mami") as $newEntry

create relation(type = "variant", target = $newEntry)
  under entry[form@tww = "memi"]
```

- A label name is written `$` followed by a letter and then letters, digits, `-` or `_`.
- A label is bound by a command or header that resolves or creates **exactly one** component, and by no other:
  - `create`, to the component it creates;
  - `upsert`, to the component it creates or resolves;
  - `ensure`, to the component it asserts. `ensure` resolves exactly one component and fails loudly otherwise, which is precisely the precondition one wants before reusing a name, so `ensure sense[gloss@en = "pig"] under entry[...] as $pig` both checks and names in one line;
  - a **block header**, to the component that heads the block — whether the header is a selector chain, a `create`, an `upsert` or an `ensure` (section "11"). A `with` header binds no label, since it designates no component.
- `delete`, `move`, `set`, `update` and `clear` bind no label. `delete` and `move` do not, because a label must keep denoting one component for the rest of its scope; the property commands do not, because they may carry `each`.
- A label may be used wherever a step is expected on the strict axis — as a command target, as a parent in `under`, `of` or `on` (in LiftPatchShort: as a path step, including the first), or as the value of a `reference` property. It is forbidden inside a filtering selector, where it would be pointless: a label already denotes exactly one component, so there is nothing to filter. For the same reason an existential join **below** a label reduces to the strict one: since the label denotes exactly one component, "some component matching the label" and "this component" are the same thing. `$pig/x[t="a mami jefi"]` is therefore legal, and means what `$pig!x[t="a mami jefi"]` means.
- A label denotes a component, not a selector: it is not re-resolved, and it keeps denoting the same component even if the properties used to create it are afterwards modified.
- Scope: a label is visible from its binding command to the end of the enclosing block, or to the end of the script if it is bound at top level. Re-binding a visible name raises `DUPLICATE_LABEL`; using an unbound name raises `UNKNOWN_LABEL`. Both are static errors.
- If the command that binds a label is rolled back, the label is unbound.

Labels close a real gap: after `create entry(form@tww = "mami")` in a dictionary
that already contains entries with this form, the new entry has a
system-generated `id` that the script does not know, an `hn` that the script
does not control, and a form that is ambiguous. Without a label it cannot be
referred to at all.

# Part 2. The LiftPatchRef reference syntax

## 7. Command structure

There are five *component commands* (commands directly targeting a component):

- create
- move
- delete
- ensure
- upsert

The syntax for each case is:

```text
create entry(initializers) [as $LABEL]
create COMPONENT(initializers) under PARENT [at POSITION] [as $LABEL]

upsert entry(initializers) [as $LABEL]
upsert COMPONENT(initializers) under PARENT [at POSITION] [as $LABEL]

delete [all] entry[SELECTOR]
delete [all] COMPONENT[SELECTOR] under PARENT

move COMPONENT[SELECTOR] under SOURCE-PARENT under DESTINATION-PARENT at POSITION
move COMPONENT[SELECTOR] under PARENT at POSITION

ensure entry[SELECTOR] [as $LABEL]
ensure COMPONENT[SELECTOR] under PARENT [as $LABEL]
```

`entry` differs from every other component type in three ways, all of which
follow from its position at the root and from the dictionary — not the script —
owning the order of the entry list (section "2"): it takes no `under` clause, it
takes no `at POSITION` clause, and `move entry[...]` is a syntax error.

`ensure` takes a **selector between square brackets**, not an initializer list:
it asserts, it never creates (section "8.3"). It may target any component type,
`entry` included.

The three *property commands* (command directly targeting a property):

```text
set ASSIGNMENT { ',' ASSIGNMENT } on [each] PARENT
update ASSIGNMENT { ',' ASSIGNMENT } on [each] PARENT
clear PROPERTY { ',' PROPERTY } on [each] PARENT

ASSIGNMENT ::= PROPERTY '=' VALUE
```

A property command carries one assignment in the common case, and may carry
several for the same parent, which is defined as the sequence of the
corresponding single-property commands (section "9.1.1").

### 7.1 The eight verbs: preconditions and postconditions

The five component commands differ only in what they require of the dictionary
before they run and in what they leave behind. Reading them as a matrix removes
any doubt about which one to use:

| Verb | Component absent | Component present | Creates | Modifies | Binds a label |
|---|---|---|---|---|---|
| `create` | creates it | `CANNOT_CREATE_DUPLICATE` | yes | no | yes |
| `upsert` | creates it, then applies the non-identity initializers | resolves it, then applies the non-identity initializers with `set` semantics | yes | yes | yes |
| `ensure` | `NOT_FOUND` | succeeds, changes nothing | no | no | yes |
| `delete` | `NOT_FOUND` | removes it with its descendants | no | yes | no |
| `move` | `NOT_FOUND` | changes its position or its parent | no | yes | no |

And for the three property commands:

| Verb | Property unset | Property set |
|---|---|---|
| `set` | creates the value | replaces the value |
| `update` | `UNSET_QUALIFIED_PROPERTY` / `UNSET_PROPERTY` | replaces the value |
| `clear` | see the decision table of section "9.3.1" | removes the value, subject to the invariants of section "5.5.3" |

"Component absent" and "component present" are decided differently by the two
groups of verbs, and the difference matters:

- for `create` and `upsert`, which are written with an **initializer list**, presence is decided by the **natural identity** of the component type (section "5.2"), against the same-type siblings under the resolved parent;
- for `ensure`, `delete` and `move`, which are written with a **selector**, presence is decided by that selector and by whichever selection strategy it uses (section "5.1.3") — which may be a label, an `id`, an ordinal, a type key, or an `hn`-refined form lookup, none of which is a natural identity. `delete sense[id = "s-41"]` finds its component or raises `NOT_FOUND` without consulting any identity key.

The matrix above is written for a non-singleton component type. A **singleton**
(section "3.1") is outside four of its five rows: `create`, `upsert`, `delete`
and `move` all raise `SINGLETON_CANNOT_BE_CREATED_OR_DELETED` on one, since it
neither comes into existence nor leaves it by any command. `ensure category` is
the one component command a singleton accepts, and it always succeeds and always
changes nothing, the component being present by construction. The three property
commands apply to a singleton exactly as to any other component.

Each verb is defined in full in sections "8" and "9".

For all *property commands*, if the command refers to a property that does not exist on its parent type according to the table in section "4.1", an error 'PROPERTY_DOES_NOT_EXIST_ON_COMPONENT_TYPE' is raised.

### 7.2 The immediate parent: `under` and `on`

For all eight commands, PARENT refers to the parent component of the targeted property or component. The parent is identified by either the `under` or the `on` keyword, then a mandatory component name, and then a mandatory `[SELECTOR]`.

- `under` identifies the immediate parent of a component directly targeted by a `component command` (create, move, upsert, ensure, delete)
  - there is no `under` clause when the target of the command is an entry, since an entry is at the root of the hierarchy and has no parent
- `on` identifies the immediate parent of a property directly targeted by a property command (set, update, clear)

The clause is **mandatory** in every other case, and a command that omits it is
rejected with `MISSING_PARENT_CLAUSE`, a static error: `create sense(gloss@en =
"pig")` and `set value = "Noun"` name no parent and there is nowhere for them
to act. The one place where the clause is omitted is the body of a block, whose
header supplies the parent (section "11").

### 7.3 Further ancestors: `of`

If the component selected by `under` or `on` is not an entry, further ancestor
clauses must be used, **`of` clauses or `within` clauses, in any combination**,
until an entry is reached. What is mandatory is that the chain of ancestors be
continued up to the root; which of the two keywords continues it is a separate
choice, made link by link, between strict selection (`of`, section "7.3") and
existential filtering (`within`, section "7.4").

- An additional `of` or `within` clause can qualify that parent with its own parent
- Multiple such clauses can be chained together, and the two keywords may alternate freely. Every `of` or `within` selector must be a parent of the previous component.
- either the component after `on` or `under`, or the component after the last clause of the chain must be an entry
- skipped ancestors are not allowed
- a chain that **stops before reaching an `entry`** is rejected with `INCOMPLETE_ANCESTOR_CHAIN`. This is a static error: it depends only on the component types written in the command and on the metamodel, not on the dictionary. `set value = "Noun" on category of sense[gloss@en = "pig"]`, which stops at the sense, is therefore rejected before any lookup;
- a chain in which two adjacent steps name component types that the metamodel does not relate as parent and child is rejected with `ILLEGAL_PARENT`, the code section "3" gives to every impossible parentage. **Skipping a level is this error, not the previous one**: in `on example[...] of entry[...]` the sense is missing, and what the command actually states is that an `example` is a child of an `entry`, which the hierarchy of section "3" forbids.
- `of`, `under` and `on` are the three keyword spellings of the same axis, the strict-parent axis, written `!` in Part 3 — or, between a command's parent path and its direct target, written as the whitespace that separates them (Part 3, section "4.1"). Which keyword is used depends only on the position in the command, never on the semantics: `under` before the immediate parent of a component command, `on` before the immediate parent of a property command, `of` before every further ancestor.

#### 7.3.1 An `of` clause followed by a `within` chain

An `of` (or `under`, or `on`) clause that is followed by one or more
`within` clauses does not change meaning: it still designates exactly one
component. What changes is *what it is applied to*. The rule is:

> A `within` chain and the `of`/`under`/`on` clause at its left form a single
> group, which is resolved as a whole. The uniqueness requirement of the
> `of`/`under`/`on` clause bears on the **result of the group**, not on the
> selector taken in isolation.

Written with explicit parentheses, the group is:

```text
of ( X within Y within Z )
```

`X`, `Y` and `Z` are each allowed to match several components; what the
`of` clause requires is that the group as a whole yield exactly one `X`. It
raises `AMBIGUOUS_REFERENCE` when two or more complete candidate paths remain,
and `NOT_FOUND` when none remains — the same two errors as an `of` clause with
no `within` after it.

When the `of` clause is not followed by a `within` clause, the group is reduced
to the clause itself, and the requirement falls back to what section "7.3" says:
the selector alone must select exactly one component, using one selection
strategy.

The single component produced by a group is then handed to whatever stands at
the left of that group — another `of` clause, an `on`/`under` clause, or the
command itself — which resolves it under the ordinary strict-parent rule.

**What the group rule does not relax.** It relaxes the *cardinality* of the
steps, and nothing else. The selector `X` of the `of`/`under`/`on` clause is
still a selector on the strict axis, and it keeps every rule of section "5.1.3": one
selection strategy, no additional predicate, and none of the filtering-only
predicates of section "5.1.2", which raise
`PREDICATE_NOT_ALLOWED_IN_UNIQUE_SELECTOR` there as anywhere else. Only the
selectors written on the *parent side* of a `within` — to its right, in this
syntax — are filtering selectors. So:

```LiftPatchRef
set definition@fr = "Un animal"
  on sense[gloss@en = "pig"]        # legal: strategy S3, may match once per candidate entry
  within entry[form@tww = "mami"]

set definition@fr = "Un animal"
  on sense[gloss@en = "pig", definition@en = "A four-legged terrestrial animal"]
                                    # PREDICATE_NOT_ALLOWED_IN_UNIQUE_SELECTOR
  within entry[form@tww = "mami"]
```

The first command reaches exactly one sense or raises `AMBIGUOUS_REFERENCE`,
depending on the dictionary; the second is rejected before the dictionary is
consulted at all.

Here are some examples:

- creating an entry makes no reference to a parent:

```LiftPatchRef
create entry(form@tww = "mami")
```

Create a sense under an existing entry:

```LiftPatchRef
create sense(gloss@en = "pig")
  under entry[form@tww = "mami"]
```

```LiftPatchRef
set definition@en =
  "A four-legged terrestrial animal"
on sense[gloss@en = "pig"]
of entry[form@tww = "mami"]
```

```LiftPatchRef
update definition@en =
  "A revised four-legged terrestrial animal"
on sense[gloss@en = "pig"]
of entry[form@tww = "mami"]
```

```LiftPatchRef
update form@tww = "memi"
  on entry[form@tww = "mami"]
```

```LiftPatchRef
delete sense[gloss@en = "pig"]
  under entry[form@tww = "mami"]
```

`set` creates or replaces the selected property:

```LiftPatchRef
set definition@en =
  "A definition"
on sense[gloss@en = "pig"]
of entry[form@tww = "mami"]
```

An empty string is a valid property value. It does not mean deletion:

```LiftPatchRef
set value = ""
  on category
  of sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

To remove a property, use `clear`:

```LiftPatchRef
clear value
  on category
  of sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

The following strict update requires the property to exist:

```LiftPatchRef
update definition@en =
  "A revised definition"
on sense[gloss@en = "pig"]
of entry[form@tww = "mami"]
```

### 7.4 Existential ancestors: `within`

While each `of` clause explicitly selects a component among its siblings,
`within` allows for an existential filtering strategy. The logic is: *keep parent
P if ∃ child C in P such that C matches selector S.*

The normative rule is the **existential join**:

> For a chain `C0 within C1 … within Cn`, construct the set of candidate paths
> `(c0, c1, …, cn)` such that each `ci` matches its selector and each `ci` is a
> direct child of `c(i+1)`. This is a join over the whole chain, not an
> independent selection of each component. The chain succeeds only when exactly
> one complete candidate path remains; it raises `NOT_FOUND` when none remains
> and `AMBIGUOUS_REFERENCE` when two or more remain.

Two consequences follow, and they are what distinguishes `within` from `of`:

- an individual `within` selector may match several components, and may use any property of the component, identity property or not (it is a *filtering selector*, section "5.1.4"). Only the result of the chain must be unique;
- a `within` clause never creates anything: it is a selector constraint, and a missing ancestor makes the command fail rather than be created (section "7.4.6").

One further error can end a `within` chain: `ILLEGAL_PARENT`, when two adjacent
components named in it cannot be related as parent and child according to the
hierarchy of section "3".


For instance, the following example contains a chain of two within clause 
- First, entry matching the given form are select. Several entries can be selected, say Entry-A, Entry-B and Entry-C.
- Then, only the entries having one (*or several*) sense whose `category` carries the given value are kept. If several senses match on a same entry, it creates several candidate-tree:
  - Lets say that Entry-C has no sense whose category has the value "Verb" and is ruled out.
  - Lets say that Entry-B has one such sense and is then kept. A candidate path Entry-B.Sense-A is kept.
  - Lets say that Entry-A has two such senses. Two candidate paths are kept: Entry-A.Sense-B and Entry-A.Sense-C
- There are now three candidate path 
- we considere now the left of the last `within`.
  - If one candidate path has an example with the required property, it succeed and this example is the parent of the created field.
  - If several candidate paths have an example with the required property, the within chain failed with `AMBIGUOUS_REFERENCE`
  - If no candidate path has an example with the required property, the within chain failed with `NOT_FOUND`.

```LiftPatchRef
create field(
  type = "special",
  text@en = "Very important"
)
under example[text = "a mami jefi"]
within sense[has category[value = "Verb"]]
within entry[form@tww = "mami"]
```

#### 7.4.1 Syntax

The `within` clause chains a PARENT component (to the right) with a child component (to the left). The child component to the left is the selector of a `under`, a `of` or a `on` clause. Several `within` clauses can be consecutive.

Its syntax is:

```text
(of|on|under) CHILD[SELECTOR]
within PARENT[SELECTOR]
```

when consecutive `within` clauses occur, the first node is a child of the second, which in turn is the child of the third:

```text
(of|on|under) CHILD[SELECTOR]
within PARENT[SELECTOR]
within PARENT[SELECTOR]
```

`within` may appear exactly where an `of` clause may appear. In the following syntax schema:
- `within` select a set of one or more ancestors;
- then it filters these ancestors down to those having the child component described at its left (in the `under` clause)
- if only one candidate path remains, we proceed to the command
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

```LiftPatchRef
create note(
  type = "special",
  text@en = "Very important"
)
under sense[gloss@en = "foo"]
within entry[form@tww = "mami"]
```

`within` can coexist and alternate with `of`. In any case, the succession of `of` and `within` always describes a chain of child/parent succession.

A *chain of `within`*, more generally, is a sequence of (one or more) CHILD-PARENT links joined by `within` without an interleaving `of`. For instance, the following example contains two chains of `within` separated by an `of` clause. The first contains one `within`; the second chain contains two `within` clauses:

```text
COMMAND
under IMMEDIATE_PARENT
within NEXT_PARENT
of NEXT_PARENT
within NEXT_PARENT
within NEXT_PARENT
```

#### 7.4.2 Selectors allowed in a `within` clause

The selector of a `within` clause is a *filtering selector*, defined once in
section "5.1.4": any property of the component, in any number, with any
predicate form, and no pseudo-property — `hn`, `has-gloss`, `id` and the ordinal
`#n` raise `PSEUDO_PROPERTY_NOT_ALLOWED_IN_FILTER` there.

#### 7.4.3 Resolution algorithm

Here is an example. Let's focus on the following example.

```LiftPatchRef
update value = "Animals"
on annotation[type="semantic domain", value="Animal"]
of field[type="free"]
of example[text="a mami jefi"]
within sense[has category[value = "Noun"]]
within entry[form="mami"]
```

(The natural identity property set of `annotation` is `type + value`, so the
selector must give both — strategy S3, section "5.1.3" — even though the command
then rewrites `value`.)

In the previous example:

- The resolver starts with the last group (section "7.3.1"), which contains:

```text
of example[text="a mami jefi"]
within sense[has category[value = "Noun"]]
within entry[form="mami"]
```

- the resolver starts with the last `within` clause: it selects all entries matching `entry[form="mami"]`. If no `entry` matches, it raises `NOT_FOUND`. Suppose that four entries have this form.
- it then moves to the left of that `within` clause, which has a selector containing `sense[has category[value = "Noun"]]`. It applies this filter to the previously selected entries. Suppose that two of the four entries have a `sense` child whose category carries the value "Noun". Two candidate paths remain. Had one entry carried two such senses, that entry would have contributed two candidate paths, not one.
- the step above is repeated with the left of this last `within`, i.e. the selector `example[text="a mami jefi"]`. For each candidate path, we look for an example with the given text under its sense. If exactly one candidate path yields such an example, it is kept. If several candidate paths yield one, an `AMBIGUOUS_REFERENCE` error is raised. If none does, a `NOT_FOUND` error is raised.
- the group is now resolved to exactly one `example`. **This single component is then handed to the clause at the left of the group, `of field[type="free"]`, which resolves it under the ordinary `of` rule** (section "7.3"): the field is looked up among the children of that one example, it must be selected unambiguously by its own selection strategy, and it raises `NOT_FOUND` or `AMBIGUOUS_REFERENCE` on its own.
- the same applies in turn to `on annotation[type="semantic domain", value="Animal"]`, resolved among the children of that one `field`. The `update` command is then applied to the `value` property of that one `annotation`.

The general rule is: a `within` chain is resolved as a group and yields exactly
one component; that component is then an ordinary, uniquely resolved parent for
everything standing at the left of the group.

#### 7.4.4 `of` compared with `within`

An `of` clause identifies the immediate parent of the preceding component in a chain of child-parent where each step is uniquely and unambiguously selected.

```LiftPatchRef
set definition@en = "A definition"
on sense[gloss@en = "foo"]
of entry[form@tww = "mami"]
```

A `within` clause describes a chain of child-parent with potentially partial information (not sufficient to uniquely select the component) on each step, the resolver being in charge of finding a complete chain that satisfies all the constraints:

```LiftPatchRef
set text@tpi = "mi shutim pik"
on example#1
within sense[has category[value = "Noun"]]
within entry[form@tww = "mami"]
```

#### 7.4.5 Filtering by the selected child

The parent of a `within` clause is evaluated together with the descendant path
already selected, and not on its own:

```LiftPatchRef
create example(
  text@tww = "a mami jefi"
)
under sense[gloss@en = "pig"]
within entry[form@tww = "mami"]
```

The entry is not selected solely by its form: it must also contain the sense
`sense[gloss@en = "pig"]`, which is what makes the command usable when several
entries share the form. In set notation, the clause reads:

```text
entry[form@tww = "mami"]
  ∋ sense[gloss@en = "pig"]
```

The sense, symmetrically, is looked for among the children of those entries, and
never elsewhere in the dictionary.

#### 7.4.6 No implicit creation

A `within` clause is a selector constraint, and selectors never create (section
"5.1"). In the following command, neither the entry nor the sense is created; if
either is absent, the command fails with `NOT_FOUND`:

```LiftPatchRef
create note(type = "special", text@en = "Very important")
under sense[gloss@en = "foo"]
within entry[form@tww = "mami"]
```

#### 7.4.7 `within` in a block header

A `within` chain may be used in the header of a block, where it selects the
parent of the commands in the block body (section "11").

## 8. Component commands

### 8.1 The `create` command

The `create` command always creates a new component and does not reinterpret the
properties as a component selector.

Properties required for the creation of a component are defined in the "Required at creation" column in the property table in section "4.1".

Here are examples of `create` commands that create new components:

```LiftPatchRef
create note(type = "sociolinguistics", text@en = "A sociolinguistic note")
  under entry[form@tww = "mami"]
```

```LiftPatchRef
create field(type = "borrowing", text@en = "yes")
  under sense[gloss@en = "pig"]
  of entry[form@tpi = "pik"]
```

```LiftPatchRef
create trait(type = "CVpattern", value = "CVC")
  under entry[form@tpi = "pik"]
```

Creation fails with `CANNOT_CREATE_DUPLICATE` if the declared component identity
already exists. For instance, the following will fail if there is already a
sense with the same qualified gloss under the same entry.

```text
create sense(gloss@en = "pig")
  under entry[form@tww = "mami"]
```

The duplicate detection works with the properties enumerated in the table in the
section "5.2". It means that, under the same parent, two
senses are identical if they have the same qualified gloss; two examples are
identical if they have the same qualified text, two variants are identical if
they have the same type and target.

The same multitext can be referred twice with a different language qualifier.

```text
upsert sense(
  gloss@en = "pig",
  gloss@fr = "porc"
)
under entry[form@tww = "mami", hn = 1]
```

**`create` and the component kinds.** `create` builds a component of an
**ordered** or a **typed** type, and nothing else:

- on an ordered type it inserts into the list of same-type siblings, at the position given by the `at` clause or at the end (section "6.2");
- on a typed type there is no position and no `at` clause (`COMPONENT_NOT_ORDERED`); the component is keyed by its `type`, which is a required initializer, and creating a second one with a `type` a sibling already has is the ordinary `CANNOT_CREATE_DUPLICATE`;
- on a **singleton** type it raises `SINGLETON_CANNOT_BE_CREATED_OR_DELETED`, a static error. `create category(value = "Noun") under sense[...]` is rejected before any lookup; the sense already has a category, and what the writer means is `set value = "Noun" on category of sense[...]`.

#### 8.1.1 Creating an `entry`

This is not an exception to the rule above but an instance of it. The natural
identity property set of `entry` is empty, so the uniqueness invariant of
section "5.2" is vacuous for entries and `create` has no duplicate to detect.
A component with the same value for the `form` property may therefore exist; the following
works, even if an entry already exists with the same qualified form:

```LiftPatchRef
create entry(form@tww = "mami")
```

#### 8.1.2 The `at POSITION` clause

The `create` command takes the optional `at POSITION` clause, which is defined
once, for every command that creates a component, in section "6.2", together
with its five positions, its default (`at end`), its errors, and the rules of the
step used by `at before` / `at after` (section "6.2.1"). Two examples of its use
with `create`:

```LiftPatchRef
create sense(gloss@en = "pig")
  under entry[form@tww = "mami"]
  at beginning
```

```LiftPatchRef
create example(text@tww = "a mami jefi")
  under sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
  at after example[text@tww = "a mami jefo"]
```

Nothing about the clause is specific to `create`, except that `create entry(...)`
does not take it at all (sections "2" and "6.2").

### 8.2 The `upsert` command

`upsert` select a component or create it if it does not exist:

```LiftPatchRef
upsert sense(gloss@en = "pig")
  under entry[form@tww = "mami", hn=1]
```

#### 8.2.1 The two branches

`upsert` is defined by two branches. Exactly one of them runs.

Let `T` be the component type, `P` the resolved parent, and let the parenthesized
list be split into three disjoint parts:

- `K` — the *match part*: the predicates bearing on the properties of the natural identity property set `I(T)`;
- `D` — the *disambiguation part*: the `has` and `has-gloss` predicates. `has` may be present for **any** component type; `has-gloss`, being an `entry`-only shorthand (section "5.3.2.2"), only for `entry`;
- `A` — the *assignment part*: every other initializer.

The command is then:

1. **Resolution.** Compute the set `M` of the children of `P` of type `T` that match `K` and `D`. For a multitext identity property given with several qualified values, a child matches when it agrees on *at least one* of the given qualified values; if two different children each match on a different qualified value, `AMBIGUOUS_REFERENCE` is raised. `AMBIGUOUS_REFERENCE` is likewise raised whenever `M` holds more than one component.
2. **Select branch** — `M` holds exactly one component `c`: no component is created. `A` is applied to `c` with `set` semantics.
3. **Create branch** — `M` is empty: a component of type `T` is created under `P`, at the position given by the `at` clause or at the end. `K` and `A` are applied as initializers. **`D` is not applied**: it is a matching condition only, never an assignment.

`D` exists precisely because `upsert` has a select branch: a predicate such as
`has-gloss@en = "pig"` is meaningful when looking for an existing entry, and
meaningless as an initializer, since the entry being created has no sense yet.
That asymmetry is intended, and it is the reason why pseudo-predicates are
admitted in an `upsert` parenthesis at all, while they are rejected in a
`create` parenthesis. The same reasoning applies below the entry: 

```LiftPatchRef
upsert sense(gloss@en = "pig", has example[text@tww ~ "jefi"], definition@en = "A four-legged terrestrial animal")
  under entry[form@tww = "mami", hn = 1]
```

resolves the sense whose gloss is "pig" **and** which already carries an example
mentioning "jefi", and creates a plain `sense(gloss@en = "pig", definition@en = "…")`
— with no example, since `D` is never applied — if no such sense exists.

One consequence of that last clause must be understood before using `D` on a
component type whose natural identity property set is **not** empty. `D` narrows
the match but takes no part in the creation, so a `D` that excludes an existing
sibling with the same identity key sends the command to the create branch, where
the uniqueness invariant then refuses it:

- under an entry that has a sense glossed "pig" **with** a "jefi" example, the command above takes the select branch and sets `definition@en`;
- under an entry that has a sense glossed "pig" **without** such an example, `M` is empty, the create branch runs, and it fails with `CANNOT_CREATE_DUPLICATE`, because two senses under one entry may not share `gloss@en`.

That is the correct outcome — the script asked for a sense that does not exist,
and the one that does exist cannot be duplicated — but it means `D` is a
*disambiguator* on types where several same-identity siblings are possible (i.e.
`entry`, section "8.2.3") and a *precondition* on every other type. Where a
precondition is what is wanted, `ensure` states it more clearly.

Further rules:

- An `upsert` command fails with `MISSING_IDENTITY_PROPERTY` if `K` does not cover the whole natural identity property set of `T` (qualified text for an example, type + target for a variant, etc.).
- Every property that the metamodel declares required must be present in `K ∪ A`, so that the create branch can satisfy rule 1 of section "6.1"; otherwise `MISSING_REQUIRED_PROPERTY` is raised. This is a static check: which branch will run is not known before execution.
- The `at` clause, if present, applies only to the create branch; it is ignored by the select branch. It is not available at all when `T` is `entry` (section "6.2").
- `as $LABEL`, if present, binds the created component in the create branch and the resolved component in the select branch.

In the following example, the `definition@en` property
  will be either created (if it does not exist) or replaced (if it does
  exist).

```
upsert sense(
  gloss@en = "pig",
  definition@en = "A four-legged terrestrial animal"
)
under entry[form@tww = "mami"]
```

The grammatical category of that sense is **not** written here: `category` is a
singleton component, not a property of `sense` (section "3.1"), and it is written
with a property command of its own, `set value = "Noun" on category of
sense[gloss@en = "pig"] of entry[form@tww = "mami"]`.

#### 8.2.2 `id` is not admitted

`upsert` cannot use an `id`, since an `id` cannot be set and a component that
does not exist yet has none. Using an `id` in an `upsert` parenthesis fails with
the error `ID_NOT_ALLOWED_ON_UPSERT`.

#### 8.2.3 Upserting an `entry`

`entry` has an empty natural identity property set, so the match part `K` of an
`upsert` on an entry is empty and the resolution of section "8.2.1" would match
nothing but the qualified `form` given as an ordinary initializer. Two rules
therefore apply:

- With `entry`, the match is performed on the qualified `form` values given in the parenthesis, refined by the disambiguation part `D` — one or more `has` / `has-gloss` predicates. `D` is used only in the select branch; if the create branch runs, only `form` and the other assignments are applied, and a new entry is created, possibly a homophone of an existing one.
- `hn` is not admitted in `D` (section "5.3.2.1"): it cannot be applied in the create branch, and unlike `has`, it makes the command fail rather than fall back to creation when the number does not exist.
- `upsert` on an `entry` with a `form` alone, and no `has` / `has-gloss` predicate, is rejected with `UPSERT_ENTRY_WITHOUT_DISAMBIGUATION`. Without a disambiguation predicate, the command could only ever create a new, possibly homophonous entry — which is exactly what `create` already does.

### 8.3 The `ensure` command

`ensure` is an **assertion**. It states that a component exists, and it fails
otherwise. It never creates anything, never modifies anything, and has no
initializer list:

```text
ensure entry[SELECTOR] [as $LABEL]
ensure COMPONENT[SELECTOR] under PARENT [as $LABEL]
```

Because it selects and does not create, `ensure` uses **square brackets**, like
every other selecting construct of the language, and it obeys exactly the rules
of a command selector (section "5.1.3"): one selection strategy, with its
refinements and nothing else.

```LiftPatchRef
ensure sense[gloss@en = "pig"]
  under entry[form@tww = "mami"]
```

```LiftPatchRef
ensure entry[form@tww = "mami", has-gloss@en = "pig"]
```

The ordinary selector errors apply, and nothing else: `NOT_FOUND` when nothing
matches, `AMBIGUOUS_REFERENCE` when several do, success and no change when
exactly one does (section "5.1.5"). Two points are specific to `ensure`:

- it accepts every selection strategy, including `id`, `hn`, `has` / `has-gloss`, the ordinal `#n`, the type key `^t` and the bare singleton step, and it may target any component type, `entry` included: since it does not create, the empty natural identity property set of `entry` is irrelevant to it. This holds for `hn` too: an `hn` that matches no entry raises `NOT_FOUND` like any other selector that matched nothing (section "5.3.2.1");
- it is the **one component command a singleton accepts** (section "3.1"): `ensure category under sense[...]` always succeeds and always changes nothing, the component being present by construction. The assertion is not useless — it still requires the *parent chain* to resolve to exactly one sense — but it says nothing about the singleton itself;
- it takes no `at` clause;
- it **binds a label** (`as $NAME`, section "6.3") when one is written. `ensure` resolves exactly one component, so the label is well defined, and asserting a precondition and naming its subject in the same line is the command's most useful form:

```LiftPatchRef
ensure entry[form@tww = "mami", has-gloss@en = "pig"] as $mami
ensure sense[gloss@en = "pig"] under $mami as $pig

set definition@en = "A four-legged terrestrial animal" on $pig
create example(text@tww = "a mami jefi") under $pig
```

Writing `ensure sense(gloss@en = "pig")` — with parentheses — is a **syntax
error**: parentheses create, and `ensure` does not. A command that creates a
component if it is missing is spelled `upsert`.

The typical use of `ensure` is to state a precondition at the top of a script,
so that the whole script is rejected before any change is made if the dictionary
is not in the expected state:

```LiftPatchRef
ensure entry[form@tww = "mami", has-gloss@en = "pig"]
ensure sense[gloss@en = "pig"] under entry[form@tww = "mami", has-gloss@en = "pig"]
```

### 8.4 The `delete` command

Deleting a component also deletes its descendants. For instance, the following
command will delete the sense as well as all the component descendants:

```LiftPatchRef
delete sense[gloss@en = "pig"]
  under entry[form@tww = "mami"]
```

Deleting an entry requires no parent clause:

```LiftPatchRef
delete entry[form@tww = "mami"]
```

Note that the previous command must raise an error if the entry does not exist (`NOT_FOUND`) or if several entries match the selector (`AMBIGUOUS_REFERENCE`).

To delete several components in one command, the multiplicity keyword `all` must
be written explicitly (section "5.6"):

```LiftPatchRef
delete all example[text@tww ~ "^draft"]
  under sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

The marked step is a filtering selector: it may carry any predicate, it may
match several components, and each matched component is deleted with its
descendants. Matching **none** is not an error: the command succeeds and deletes
nothing (section "5.6"), so the line above is safe to run on a sense that has no
draft example. The parent clauses are unaffected: the sense and the entry must
still exist and be unambiguous.

A **singleton** component cannot be deleted: `delete category under sense[...]`
raises `SINGLETON_CANNOT_BE_CREATED_OR_DELETED`, a static error. It disappears
only with its host, and deleting the host deletes it as a descendant like any
other child. To empty it rather than remove it, clear its properties:
`clear value on category of sense[...]`.

### 8.5 The `move` command

**`move` applies to ordered components only.** Moving is a change of position,
and only an ordered component type (section "3.1") has positions:

```text
move ORDERED-COMPONENT under PARENT [ under DESTINATION-PARENT ] at POSITION
```

A `move` whose target step names a **typed** component type raises
`COMPONENT_NOT_ORDERED`, a static error: `move note[type = "general"] …` asks to
reorder a map. A typed component is re-keyed by writing its `type` with `set`
(section "5.3.4"), and is re-parented by deleting it and creating it under the
new parent, which is a different operation with different effects and is written
out as such. A `move` whose target step names a **singleton** raises
`SINGLETON_CANNOT_BE_CREATED_OR_DELETED`; `move entry[...]` remains the syntax
error of section "2".

The `at POSITION` clause of section "6.2" is **mandatory** on `move`: a move with
no stated destination position would have no defined meaning, and `move` is the
one command with no sensible default (appending at the end is a real change of
order, not a neutral choice). A `move` without an `at` clause is a syntax error.
The five positions and the rules of the `at before` / `at after` step are those
of sections "6.2" and "6.2.1":

```LiftPatchRef
move example#3
  under sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
  at after example[text@tww = "a mami jefi"]
```


1/ When the move command has only one `under` clause, the destination is the same parent: the component is moved in its set of siblings.

```LiftPatchRef
move example#1
  under sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
  at end
```

When moving within the same parent, the index corresponds to a position after the moved component has been removed. The following moves the second element to the third position:
  
```LiftPatchRef
move example#2
  under sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
  at index 3
```

2/ When the move command has a second `under` clause, the destination is the component targeted by this second `under` clause:

```LiftPatchRef
move example#1
  under sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
  under sense[gloss@en = "large animal"]
  of entry[form@tww = "mami"]
  at end
```

A move fails with a structured error if:

- the moved component type is not an ordered one: `COMPONENT_NOT_ORDERED` for a typed one and `SINGLETON_CANNOT_BE_CREATED_OR_DELETED` for a singleton, both static
- moving to the current position raises `MOVING_TO_CURRENT_POSITION`
- the destination cannot contain the source component type: `ILLEGAL_PARENT`, the same code as for any other illegal parentage (section "3")
- the move would make a component its own ancestor (`SELF_ANCESTOR`);
- the requested position is **above** the destination's valid range, which raises `INDEX_OUT_OF_BOUNDS`:
  - greater than the number of components in the destination + 1 for a different-parent move
  - greater than the number of components in the destination for a same-parent move
- a position **lower than 1** is not a runtime condition at all: it is decidable from the text and raises the static `ILLEGAL_ORDINAL` (section "6.2").

Moving an `entry` is likewise not a runtime condition: `move entry[...]` is
rejected by the parser as a syntax error, since `entry` has no parent and no
sibling order to change.

A move also fails if it would break the uniqueness invariant of section "5.2"
under the destination parent: `CANNOT_CREATE_DUPLICATE`.

The source and destination are resolved before the move is applied, and either
raises `NOT_FOUND` or `AMBIGUOUS_REFERENCE` on its own if it does not designate
exactly one component.

Moving changes position and parentage, never identity.

## 9. Property commands

### 9.1 The `set` command

`set` is the normal property assignment operation. It creates the target if
absent and replaces its value if present:

```LiftPatchRef
set definition@en = "A four-legged terrestrial animal"
  on sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

Or, writing on a singleton component (section "3.1"):

```LiftPatchRef
set value = "Noun"
  on category
  of sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

Or:

```LiftPatchRef
set text@en =
  "A sociolinguistic note"
on note[type = "sociolinguistic"]
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

```LiftPatchRef
set definition = "A definition"
on sense[gloss@en = "pig"]
of entry[form@tww = "mami"]
```

The previous command will set the value of the qualified value `definition@en`,
it will not change any other qualified value existing on that property (say,
`definition@fr`).

When `set` targets a natural identity property, the uniqueness invariant of section "5.2" is checked against the other same-type siblings before the value is written, and `CANNOT_CREATE_DUPLICATE` is raised if the command would break it.

`set` accepts the multiplicity keyword `each` on its `on` clause (section
"5.6"), and a multi-language literal as its value (section "5.5.1"):

```LiftPatchRef
set value = "Noun"
  on each category
  of sense[gloss@en != "pig"]
  of entry[form@tww = "mami"]
```

The uniqueness invariant is then checked for every component the command
touches, and the command has no effect at all if it fails for any of them.

`set` never accepts the `@*` wildcard (`WILDCARD_NOT_ALLOWED`): assigning one
string to "all languages" is not a meaningful operation. `@*` is a reading and a
removing qualifier, not an assigning one (section "4.4.1"); to assign several
languages in one command, use a multi-language literal.

#### 9.1.1 Assignment lists

A property command may carry an **assignment list**, so that the properties of
one component are written in one command instead of one command per property:

```text
set PROPERTY = VALUE { ',' PROPERTY = VALUE } on [each] PARENT
update PROPERTY = VALUE { ',' PROPERTY = VALUE } on [each] PARENT
clear PROPERTY { ',' PROPERTY } on [each] PARENT
```

```LiftPatchRef
set definition@en = "A four-legged terrestrial animal",
    definition@fr = "Un animal terrestre à quatre pattes"
  on sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

An assignment list is surface sugar with no semantics of its own, exactly like
the multi-language literal of section "5.5.1", and the two may be combined
(`set form = { tww: "mami", tpi: "pik" }, morpheme = "stem"`):

- A command carrying an assignment list is defined as being **exactly equivalent to the sequence of the corresponding single-property commands**, in the order written, with the same verb and the same `on` clause. Every rule of the single-property command applies unchanged to each element: availability, default languages, `@*` restrictions, the uniqueness invariant, and the `update` and `clear` preconditions.
- The `on` clause is resolved **once**, before the first assignment, and the resolved component is shared by all of them. With `each`, the list is applied to every matched component, in the order of section "5.6".
- Naming the same qualified property twice in one list raises `DUPLICATE_PROPERTY`, whether it is written twice directly or once directly and once inside a multi-language literal.
- The command remains atomic, as every command is: if any element of the list fails, the whole command fails and none of its assignments is applied.
- A list may not mix verbs. To create one property and replace another under their respective preconditions, write two commands.

The same holds for `clear`, whose list is a list of property names rather than
of assignments:

```LiftPatchRef
clear definition@en, definition@fr
  on sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

### 9.2 The `update` command

Update requires an existing target:

```LiftPatchRef
update definition@en =
  "A four-legged animal"
  on sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

or:

```LiftPatchRef
update text@en = "A revised note"
on note[type="review"]
of sense[gloss@en = "pig"]
of entry[form@tww = "mami"]
```

- If the property does not exist on the parent component type according to the table in section "4.1", an error `PROPERTY_DOES_NOT_EXIST_ON_COMPONENT_TYPE` is raised.
- On a scalar property, if the property was not already set, an `UNSET_PROPERTY` error is raised.
- On a multitext property, `update` targets exactly one **qualified** value, and the test bears on that qualified value alone: if the property was not already set *for the language targeted by the command*, an `UNSET_QUALIFIED_PROPERTY` error is raised, **even when the property has values for other languages**. `update definition@en = …` therefore fails on a sense that has only a `definition@fr`; `set` is the command to use in that case.
- `update` never accepts the `@*` wildcard (`WILDCARD_NOT_ALLOWED`): it replaces one value, and replacing "all languages" with a single string is not a meaningful operation. To rewrite several languages at once, use a multi-language literal (section "5.5.1").

The same rule as above (under "set") regarding language applies: lang can be
implicit.

In the following example, the default language is used in order to qualify the definition property:

```LiftPatchRef
update definition =
  "A four-legged animal"
  on sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

`update` checks the uniqueness invariant exactly as `set` does (sections "5.2" and "9.1").

### 9.3 The `clear` command

`clear` removes property values while retaining the parent component:

```LiftPatchRef
clear definition@en
  on sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

For a multitext property, the wildcard qualifier `@*` removes all language
values:

```LiftPatchRef
clear definition@*
  on sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

`clear` is the only command that may intentionally target multiple qualified
property values: component selectors and `set` / `update` targets must resolve to
exactly one. A `@*` `clear` may remove several values, but it never removes the
parent component.

The qualifier rules — mandatory on a multitext, forbidden on a scalar, never
defaulted — are those of section "5.5.3". On a scalar property, `clear` removes
the value and takes no qualifier:

```LiftPatchRef
clear value
  on category
  of sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

`clear` accepts the multiplicity keyword `each` on its `on` clause:

```LiftPatchRef
clear definition@fr
  on each sense[exists(definition@fr)]
  of entry[form@tww = "mami"]
```

#### 9.3.1 Decision table

The following table is normative and exhaustive. It gives, for every
combination of property role (section "5.4.1"), qualifier, and current state of
the property, either the effect of the command or the error it raises. It is the
only place where the outcome of a `clear` is decided.

| Property role | Written as | State of the property | Result |
|---|---|---|---|
| Scalar, identity (R1) | `clear p` | any | `CANNOT_CLEAR_IDENTITY_PROPERTY` |
| Scalar, required non-identity (R3) | `clear p` | any | `CANNOT_CLEAR_REQUIRED_PROPERTY` |
| Scalar, optional (R5) | `clear p` | set | the value is removed |
| Scalar, optional (R5) | `clear p` | unset | `UNSET_PROPERTY` |
| Scalar, any role | `clear p@L` or `clear p@*` | any | `LANG_KEY_NOT_SUPPORTED_ON_SCALAR` |
| Multitext, any role | `clear p` | any | `MISSING_LANGUAGE_QUALIFIER` |
| Multitext, required or identity (R2, R4) | `clear p@L` | `L` is set, and at least one other language is set | the value for `L` is removed |
| Multitext, required or identity (R2, R4) | `clear p@L` | `L` is set and is the only one | `CANNOT_CLEAR_REQUIRED_MULTITEXT` |
| Multitext, required or identity (R2, R4) | `clear p@L` | `L` is not set | `UNSET_QUALIFIED_PROPERTY` |
| Multitext, required or identity (R2, R4) | `clear p@*` | any | `CANNOT_CLEAR_REQUIRED_MULTITEXT` |
| Multitext, optional (R6) | `clear p@L` | `L` is set | the value for `L` is removed |
| Multitext, optional (R6) | `clear p@L` | `L` is not set | `UNSET_QUALIFIED_PROPERTY` |
| Multitext, optional (R6) | `clear p@*` | at least one language is set | all the values are removed |
| Multitext, optional (R6) | `clear p@*` | no language is set | `UNSET_PROPERTY` |

Two invariants are behind the whole table:

1. A property that the metamodel declares required, and any identity property, may never become unset. `clear` is refused rather than allowed to produce a component that `create` would have rejected.
2. `clear` never removes a component. To remove a component, use `delete`.

#### 9.3.2 The table applied to real properties

The three cases a lexicographer meets most often, each naming the row of section
"9.3.1" that decides it:

| Command | Property role | Outcome |
|---|---|---|
| `clear url on illustration[...]` | `Illustration.url`: scalar, required, identity (R1) | `CANNOT_CLEAR_IDENTITY_PROPERTY` |
| `clear value on trait[...]` | `Trait.value`: scalar, required, non-identity (R3) | `CANNOT_CLEAR_REQUIRED_PROPERTY` |
| `clear value on category of sense[...]` | `Category.value`: scalar, optional (R5) | the value is removed, or `UNSET_PROPERTY` if it was already unset |

And the same for a required multitext, `Entry.form` (R4), where the outcome
depends on what remains:

```text
clear form@tww
  on entry[form@tww = "mami"]
```

removes the `tww` value if the entry also has a form in another language, and
raises `CANNOT_CLEAR_REQUIRED_MULTITEXT` if `tww` is the only one. Written
`clear form@*` it raises `CANNOT_CLEAR_REQUIRED_MULTITEXT` whatever the state,
since it would empty a required property; written `clear form`, with no
qualifier, it raises `MISSING_LANGUAGE_QUALIFIER` before the state is consulted
at all.

## 10. Reference properties

Properties of datatype "reference" store the id of the referenced component.

1/ In a command, the value is set by referring to the target component, with a
*value chain*:

```text
REFERENCE-VALUE ::= CHAIN | '$' LABEL
```

- A value chain is an ordinary chain (section "5.1.1"): it may be a single step, it may be extended with `of` clauses, and it may use the `within` axis exactly like any other chain. It must resolve to exactly one component, under the usual rules and the usual errors (`NOT_FOUND`, `AMBIGUOUS_REFERENCE`).
- A label (`$name`, section "6.3") may be used instead of a chain, and is the only way to refer to a component that the running script has just created.
- A **bare string is not a legal reference value**. `set target = "pig-44"` is rejected with `REFERENCE_VALUE_MUST_BE_A_CHAIN`. To assign a known id, select by id: `set target = sense[id = "pig-44"]`. One uniform rvalue category — "a component, designated by a chain or a label" — is thus the only form, and the id case is written explicitly rather than being inferred from the shape of a string. The same rule holds in a *selector*, where a `reference` property is likewise compared to a chain and never to a bare string; see 2/ below.
- A value chain ends at the first `on` keyword, which opens the command's own parent chain. `on` is therefore reserved and cannot be a component name.

```LiftPatchRef
set target = entry[form@tww = "memi"]
on relation[type = "synonym", target = entry[form@tww = "moni"]]
of entry[form@tww = "mami"]
```

**The selector of a `relation`, a `variant` or a `reversal` must name its current
target.** The natural identity property set of all three is `type + target`
(section "4.1"), so strategy S3 requires both properties, and a command that
rewrites a `target` has to say which link it is rewriting. Writing
`on relation[type = "synonym"]` alone is `INCOMPLETE_SELECTOR`: `type` is half an
identity key, and an entry may carry several `synonym` relations. Three spellings
are available, and the second is usually the shortest:

```LiftPatchRef
set target = entry[form@tww = "memi"]
  on relation[type = "synonym", target = entry[form@tww = "moni"]]   # by identity
  of entry[form@tww = "mami"]

set target = entry[form@tww = "memi"]
  on relation#1                                                      # by position
  of entry[form@tww = "mami"]

create relation(type = "synonym", target = entry[form@tww = "moni"])
  under entry[form@tww = "mami"] as $link                            # by label
set target = entry[form@tww = "memi"] on $link
```

Note that `relation`, `variant` and `reversal` carry no `id` (section "5.3.1"
gives one to `entry` and `sense` only), so strategy S2 is not available on them.

A relation to a sense may be written:

```LiftPatchRef
set target =
  sense[gloss@en = "pig"]
  of entry[form@tww = "memi"]
on relation#2
of entry[form@tww = "mami"]
```

The LiftPatchRef DSL is not in charge of managing the integrity of the reference. It means that the LiftPatchRef DSL will not check, when a component is deleted, if it creates an invalid reference elsewhere. This is the responsibility of the dictionary management system.

2/ In a selector, a `reference` property is compared to a **chain**, exactly as
it is assigned in a command — never to a bare string:

```text
variant[type="dialectal", target = entry[id = "pig-44"]]
```

- The predicate `target = CHAIN` holds when the component denoted by `CHAIN` is the very component the stored reference points to. Formally: the chain is resolved first, to exactly one component `c`, and the predicate then compares the stored value with the `id` of `c`.
- The chain is an ordinary chain (section "5.1.1") and obeys the ordinary rules: it may be a single step or an `of` / `within` chain, it must resolve to exactly one component, and it raises `NOT_FOUND` or `AMBIGUOUS_REFERENCE` on its own, *before* any component of the enclosing selector is examined. A label may be used instead: `target = $newEntry`.
- A bare string is rejected with `REFERENCE_VALUE_MUST_BE_A_CHAIN`, in a selector as in a command. The id case is written `target = sense[id = "pig-44"]`, which says what it means.
- The rule holds in every kind of selector — command selector, parent selector, filtering selector — and in `has` predicates.

The reason for requiring a chain on both sides is that a reference is a link
between components, not a string field. Comparing it to a quoted id made the
selector depend on an opaque, system-generated value, silently matched nothing
when the id was mistyped, and gave the language two different rvalue categories
for one datatype. With a chain, the mistyped case is reported (`NOT_FOUND`), and
the reader of a script sees which component is meant.

Two consequences are worth stating:

- selecting by a dangling reference is not expressible, and does not need to be: if the referenced component has been deleted, no chain denotes it. To find such components, an implementation's plan mode reports them (section "12.4");
- the chain in a selector is resolved against the state of the dictionary left by the preceding commands of the script (section "12.1"), like every other selector.

When a reference is **assigned**, the validator further verifies that:

- the value resolves to exactly one existing component. This is the ordinary resolution of a chain (section "5.1.5"), so a chain matching nothing raises `NOT_FOUND` and one matching several raises `AMBIGUOUS_REFERENCE`; a reference target needs no error code of its own;
- the component designated is an `entry` or a `sense`; otherwise `INVALID_TARGET` is raised. This is a **static** error, not a dynamic one: the component type is written in the step that designates it — the leftmost step of a LiftPatchRef chain, the last step of a LiftPatchShort path — or, for a label, is known from the command that bound it. `set target = example[text@tww = "a mami jefi"] of …` is rejected before the dictionary is consulted.

These rules apply to the `target` property of the three component types that
carry one: `variant`, `relation` and `reversal`. Since `type + target` is the
natural identity of all three, two of them with the same type and the same target
under one parent are duplicates, refused by the invariant of section "5.2" with
`CANNOT_CREATE_DUPLICATE`; nothing specific to references is involved.

## 11. Blocks

Blocks provide convenient construct for related operations while preserving explicit component construction.

A curly brace block is a syntactic construct that groups related commands together, and anchors them to a common parent.


A block consists of
- the *block header* which is given before the curly brace.
  - the block header is either:
    - a component with a selector, possibly extended by `of` / `within` clauses, and possibly with an `as $LABEL` binding
    - a `create` or `upsert` command with a component and its initializer list, possibly with an `at` clause and an `as $LABEL` binding
    - an `ensure` command with a component and its selector, possibly with an `as $LABEL` binding (the assertion is evaluated first; the asserted component is then the parent of the body)
    - a `with` directive (section "2.1.2"), which binds default languages for the body without designating a parent; the parent is then the one of the enclosing block, if any
- the *block body* which is given inside the curly brace. The block body contains either:
  - another block, with its header and body: block can be nested
  - one or several commands. Those commands have neither an `under` nor an `on` clause, since the parent is given by the block header. They may carry an `at` clause, an `as $LABEL` binding, and a multiplicity keyword, exactly as at top level.
  - `language-default` directives, whose scope is then the block (section "2.1.1").
  - Note that an `upsert`, `ensure` or `update` cannot be in the scope of a `create` command, be it at the direct upper level or indirectly related.
  - **`create entry(...)` cannot appear in a block body.** A block header supplies a parent, and an `entry` has none (section "2"); the two statements cannot both be honoured. The verdict is `ILLEGAL_PARENT`, the same code as for any other impossible parentage, and it is a static error. An entry is created at top level, and the block that fills it is then written with that `create entry` as its own header.
  - **`move` cannot appear in a block body** (`MOVE_NOT_ALLOWED_IN_BLOCK`, a static error). A `move` names two parents — the source parent and the destination parent — while a block header supplies exactly one, and a block body command takes no `under` clause with which to supply the other. Rather than give `move` a special dispensation to carry one `under` clause inside a block, the language keeps the rule simple: a `move` is written at top level, where both of its parents are visible in the command itself.

The semantic of the relation between the block header and the command it contains is that of `under` and `on`: the block header is the parent of the component or property targeted by the inner commands.

The next example has a block header that contains a component with selector. It will fail with 'AMBIGUOUS_REFERENCE' or 'NOT_FOUND' if the selector failed.

```LiftPatchRef
entry[form@tww = "mami"] {
  create sense(gloss@en = "pig") {
    set definition@en = "A four-legged terrestrial animal"
    set value = "Noun" on category

    create example(text@tww = "a mami jefi") {
      create translation(
          type="free",
          text@en = "I shot a pig")
    }
  }
}
```

The header can be a parent selector chain, with `of`:

```LiftPatchRef
sense[gloss="pig"]
of entry[form@tww = "mami"] {
    create example(text@tww = "a mami jefi") {
      create translation(
          type="free",
          text@en = "I shot a pig")
    }
}
```

as well as with `within`:

```LiftPatchRef
sense[gloss="pig"]
within entry[form@tww = "mami"] {
    create example(text@tww = "a mami jefi") {
      create translation(
          type="free",
          text@en = "I shot a pig")
    }
}
```


In the next example, the outer block header is a `upsert` command:

```LiftPatchRef
upsert entry(form@tww = "mami", has-gloss="pig") {
  upsert sense(gloss@en = "pig") {
    set definition@en = "A four-legged terrestrial animal"
    set value = "Noun" on category
    create example(text@tww = "a mami jefi") 
  }
}
```

The preceding command is very useful if the user want to create a new entry only if none of the existing entry with "form=mami" has the gloss given in "has-gloss". In other word, if an entry exist with that sense, we do nothing (appart updating the definition and the category), but if the sense does not exist, we do not want to create it on any of the existing entry having form="mami": we want to create it on a new entry.

This semantic of two embedded upsert cannot be expressed without the block syntax.

The restriction stated above — no `upsert`, `ensure` or `update` in the scope of
a `create` — is what forbids the following command: expecting a sense to exist
on an entry the same command has just created makes no sense.

```
create entry(form@tww = "mami") {
  upsert sense(gloss@en = "pig") {
    set definition@en = "A four-legged terrestrial animal"
    set value = "Noun" on category
    create example(text@tww = "a mami jefi") 
  }
}
```

Such situation should raise 'CANNOT_UPSERT_ENSURE_OR_UPDATE_IN_CREATION_BLOCK'.


Block semantics are:

1. Resolve, assert, or create the component in the block header (with required-properties validation).
2. Make that component the explicit parent of child commands.
3. Execute child commands in source order.
4. Make preceding changes visible to subsequent commands in the same block.
5. Roll back the entire block if any child command fails.

Inside a component block, a property command omits `on` and uses the current
block component as its parent. Outside a block, `on` is mandatory for property
commands, and `under` for every component command whose target is not an `entry`;
omitting it raises `MISSING_PARENT_CLAUSE` (section "7.2").

A block is also a *scope*: `language-default` directives and `as $LABEL`
bindings made inside it are visible until its closing brace and not beyond
(sections "2.1.1" and "6.3"). A label bound by the block **header** is bound in
the *enclosing* scope, not in the body's: it is visible inside the body, and it
remains visible after the closing brace — to the end of the enclosing block, or
to the end of the script for a top-level block. This is what makes a header
label useful, since its purpose is to let later commands reach the component the
block was about without selecting it again.

Blocks are not loops or conditionals. They provide lexical nesting, a scope for
defaults and labels, and a transaction boundary.

## 12. Execution model

This section applies to **both** surface syntaxes.

### 12.1 Order and visibility

- The commands of a script are executed in source order.
- Each command observes the state of the dictionary left by all the preceding commands of the same script. Selectors, ordinals and default languages are re-evaluated for every command against that state.
- This holds at every level: at top level, inside a block, and inside the body of a `with` directive.

### 12.2 Atomicity

The unit of atomicity is the **whole script**:

> A script either succeeds completely or leaves the dictionary unchanged. If any
> single command fails, every change already applied by the preceding commands
> of the same script is rolled back, and the dictionary is left exactly as it
> was before the script started.

- This applies to a LiftPatchRef script file and to a LiftPatchShort document alike. In a mixed document, the unit is the document: all its LiftPatchShort command lines, in order, form one transaction; the prose lines around them are ignored.
- Blocks are nested transaction boundaries inside that one: a failing child command rolls back its enclosing block, which — since the block itself then fails — rolls back the script.
- A failed script reports the failing command, its position in the source, and the error code.
- Nothing is committed before the last command of the script has succeeded. Implementations that cannot offer a transactional dictionary MUST simulate this, for instance by working on a copy and swapping it at the end, and MUST NOT offer a partial-application mode under the same command name.

### 12.3 Static and dynamic errors

Errors are of two kinds, and an implementation MUST report all the static errors
of a script before applying any command:

- **Static errors** depend only on the text of the script and on the metamodel: unknown component or property name, property not available on the component type, illegal parentage, wrong selection strategy, forbidden pseudo-property, missing required initializer, an operator applied to a datatype that does not admit it, a reference target that is neither an entry nor a sense, an ancestor chain that does not reach the root, a missing parent clause, `ILLEGAL_HN`, `ILLEGAL_ORDINAL`, `DUPLICATE_PROPERTY`, `DUPLICATE_LABEL`, `UNKNOWN_LABEL`, and every syntax error.
- **Dynamic errors** depend on the state of the dictionary: `NOT_FOUND`, `AMBIGUOUS_REFERENCE`, `CANNOT_CREATE_DUPLICATE`, `UNSET_PROPERTY`, `UNSET_QUALIFIED_PROPERTY`, `INDEX_OUT_OF_BOUNDS`, `MOVING_TO_CURRENT_POSITION`, `SELF_ANCESTOR`, the language-list errors of section "2.1", and the `clear` invariants.

Appendix B gives the kind of each error code.

### 12.4 Plan mode

An implementation MUST offer a **plan mode** (also called dry run), in which the
script is validated and resolved against the current dictionary but no change is
committed.

Plan mode reports, for each command in source order:

- the command, with its position in the source;
- the component it resolved to, identified by its `id` when it has one, and by its path from the root otherwise;
- the effect it would have: component created / deleted / moved (with the source and destination positions), property values set, replaced, or removed, with the old and the new value;
- for a command marked `each` or `all`, one entry per affected component;
- any error it would raise, with its code;
- the references that the script would leave dangling (see "12.4.2").

A script that reports no error in plan mode is guaranteed to be free of static
errors; it is not guaranteed to succeed later, since the dictionary may have
changed in between.

Plan mode MUST also report **warnings**, which are not errors and do not prevent
the script from being applied. Two are defined in version 1.0:

- `NO_COMMAND_RECOGNIZED` — not one line of the document was recognized as a command. In LiftPatchShort this is almost always a misdetected syntax: a LiftPatchRef script read as a LiftPatchShort document matches no command line and would otherwise apply nothing, silently (section "1.1"). Declaring `syntax=` on the pragma prevents the situation; the warning catches it when nobody did;
- `COMMAND_APPLIES_TO_NO_COMPONENT` — a command marked `each`, `all` or `*` whose target step matched nothing. It changes nothing by design (section "5.6"), and a plan that says so is how an editor learns that the cleanup line they wrote matches none of the data they thought it did.

Warnings are reported per document and per operation respectively, and are
carried by the plan document in a `warnings` array (section "12.4.1").

#### 12.4.1 The plan document

A prose rendering is what a lexicographer reads, but it is not comparable
between implementations. Plan mode MUST therefore also be able to emit its
result as a **plan document**, a single JSON object with the shape below. This
document is the normative artifact: two conforming implementations, given the
same script and the same dictionary, MUST emit plan documents that are equal
after normalization (object key order and whitespace are insignificant; array
order is significant).

```json
{
  "liftpatchPlan": "1.0",
  "script": { "source": "edits.liftpatch", "syntax": "LiftPatchRef", "commands": 3 },
  "metamodel": { "version": "1.0", "metamodelId": null },
  "status": "ok",
  "staticErrors": [],
  "warnings": [],
  "operations": [
    {
      "index": 1,
      "position": { "line": 4, "column": 1 },
      "command": "create sense(gloss@en = \"pig\") under entry[form@tww = \"mami\"]",
      "verb": "create",
      "target": {
        "componentType": "sense",
        "id": null,
        "path": "entry[id=\"e-17\"]/sense#3",
        "label": "$pig"
      },
      "parent": { "componentType": "entry", "id": "e-17", "path": "entry[id=\"e-17\"]" },
      "effects": [
        { "kind": "componentCreated", "componentType": "sense", "position": 3 },
        { "kind": "propertySet", "property": "gloss", "language": "en",
          "oldValue": null, "newValue": "pig" }
      ],
      "error": null
    },
    {
      "index": 2,
      "position": { "line": 9, "column": 1 },
      "command": "clear definition@fr on each sense[exists(definition@fr)] of entry[form@tww = \"mami\"]",
      "verb": "clear",
      "multiplicity": "each",
      "target": null,
      "applications": [
        {
          "target": { "componentType": "sense", "id": "s-41", "path": "entry[id=\"e-17\"]/sense#1" },
          "effects": [
            { "kind": "propertyRemoved", "property": "definition", "language": "fr",
              "oldValue": "Un animal", "newValue": null }
          ]
        }
      ],
      "error": null
    }
  ],
  "danglingReferences": [],
  "summary": {
    "componentsCreated": 1, "componentsDeleted": 0, "componentsMoved": 0,
    "propertiesSet": 1, "propertiesRemoved": 1, "languagesCreated": 0
  }
}
```

Normative points about the plan document:

- `status` is `"ok"` when the script would apply cleanly, `"error"` otherwise. A plan with `status: "error"` still lists every operation it could resolve, so that a reader sees the whole intent, and the first failing operation carries a non-null `error`.
- `staticErrors` lists every static error of the script (section "12.3"), each with its code and position. It is non-empty only when `status` is `"error"`, and when it is non-empty no operation is resolved.
- `warnings` lists the warnings of section "12.4", each as `{ "code": "...", "position": {...}, "operationIndex": n }`, with `position` and `operationIndex` null for a document-level warning such as `NO_COMMAND_RECOGNIZED`. A warning never changes `status`, which stays `"ok"` if the script would apply cleanly. `warnings` is compared like `staticErrors`, by code and by order.
- `script.syntax` is the surface syntax the script was parsed with, whether it was declared by the pragma attribute `syntax=` or determined by the implementation (section "1.1"); it is always present and always compared.
- **Operation numbering.** `index` is 1-based and counts operations across the whole script, flat, in source order, whatever the block nesting. The rule for what counts as an operation is: **anything that resolves a component or changes the dictionary.** So:
  - every command is an operation;
  - a **block header is an operation**, whether it is a selector chain, a `create`, an `upsert` or an `ensure`; it takes the index preceding its body's operations;
  - `language-create` is an operation, since it changes the dictionary and carries a `languageCreated` effect;
  - a `with` header and a `language-default` directive are **not** operations and take no index: they bind names for the rest of a scope and change nothing.

  The same numbering is what `commandIndex` refers to in the conformance corpus (Appendix D.1), so that a case can point at a command inside a block.
- `error`, where present, is an object `{ "code": "...", "message": "...", "position": {...} }`. `code` is one of Appendix B and is the only part an automated comparison relies on; `message` is free prose and MUST NOT be compared.
- `effects` uses exactly these seven `kind` values, each with exactly these fields. Because the plan document is the comparable artifact, the shape of an effect is normative, not illustrative:

| `kind` | Fields | Meaning |
|---|---|---|
| `componentCreated` | `componentType`, `position` | a component appears under the operation's `parent` |
| `componentDeleted` | `componentType`, `position` | a component disappears |
| `componentMoved` | `componentType`, `fromParent`, `fromPosition`, `toParent`, `toPosition` | a component changes position, parent, or both. `fromParent` and `toParent` are objects `{ "componentType": …, "id": …, "path": … }`, as the operation's `parent` field is; they are equal for a same-parent move. `fromPosition` is the position before the command runs, `toPosition` the position after it has run |
| `propertySet` | `property`, `language`, `oldValue` (always `null`), `newValue` | a value is written where there was none |
| `propertyReplaced` | `property`, `language`, `oldValue`, `newValue` | a value is written over an existing one |
| `propertyRemoved` | `property`, `language`, `oldValue`, `newValue` (always `null`) | a value is removed |
| `languageCreated` | `languageKind`, `language` | `language-create` adds a language to one of the dictionary's language lists (section "2.1.3") |

- Writing a value **equal** to the one already there is still a change of state performed by the command, and yields a `propertyReplaced` effect whose `oldValue` and `newValue` are equal. Plan mode does not compare values in order to suppress an effect: a command that ran is reported.

- **A `delete` reports exactly one `componentDeleted`**, for the component the command names, and **none for its descendants**, even though deleting a component deletes its descendants (section "8.4"). It reports no `propertyRemoved` for the properties of the deleted components either. The effect list describes what the *script* did, not the transitive closure of what disappeared: a plan that listed one line per descendant would bury a one-line command under fifty effects, and the descendants are recoverable from the dictionary, which the reader has. The same holds for a `move`: one `componentMoved`, never one per descendant.
- A `create` reports one `componentCreated` followed by one `propertySet` per qualified value written by its initializers, in the order in which the initializers are written.
- `language` is `null` for a scalar property, and is the language code for a qualified value. A `clear p@*` yields one `propertyRemoved` effect per language actually removed, in the dictionary's language-list order.
- `position`, `fromPosition` and `toPosition` are 1-based and count same-type siblings. On `componentCreated`, `position` is the position the component would occupy once created; on `componentDeleted`, it is the position the component occupies **before** the command runs, so that a `delete all` reports the positions of the original list rather than of the list as it shrinks. On `componentMoved`, `fromPosition` is read before the command runs and `toPosition` after. All three are `null` wherever there is no position to report: for an `entry`, whose list the language does not order (section "2"), and for a **typed** or a **singleton** component, which live in no ordered list at all (section "3.1"). `componentMoved` never carries a typed or a singleton component, since neither can be moved.
- `path` is a human-readable path from the root using the reference syntax; it is **informative**, and two implementations may differ in it. So are the `fromParent` and `toParent` objects of a `componentMoved` effect, which contain one. Which fields of an `effect` are compared is stated once, in Appendix D.1; `id`, `componentType`, `error.code` and `summary` are compared as well.
- A command marked `each` or `all` carries `applications` instead of `effects`, one element per affected component, in the execution order of section "5.6".
- Plan mode never writes to the dictionary, and it MUST be available for both surface syntaxes.

#### 12.4.2 Dangling references

The language does not maintain referential integrity (section "10"), but plan
mode MUST report where a script would break it. A **dangling reference** is a
`reference` property whose stored value, after the whole script has been applied,
would point at no existing component.

`danglingReferences` lists them, each as:

```json
{
  "component": { "componentType": "relation", "id": "r-9",
                 "path": "entry[id=\"e-20\"]/relation#1" },
  "property": "target",
  "value": "s-41",
  "cause": { "operationIndex": 3, "verb": "delete" }
}
```

- The list covers references broken by a `delete` of the referenced component or of one of its ancestors — the two ways a script can orphan a reference.
- Reporting is mandatory; refusing the script is not. A dangling reference is **not** an error: a lexicographer may legitimately delete a sense and repair its inbound references afterwards, possibly in another script. It is reported so that the decision is taken knowingly.
- An implementation MAY offer a strict mode that turns the report into a refusal, provided that it is not the default and not the same command name (same rule as section "12.2").

Plan mode is what makes a mutation language over irreplaceable linguistic data
reviewable before the fact, and it gives implementations a directly comparable
artifact to test against.

# Part 3. The LiftPatchShort concise syntax

This section describes an alternate concise syntax for practical purposes, LiftPatchShort.

This concise syntax allows only for a subset of the reference syntax described above.

Its underlying semantic is exactly the same.

The formal grammar of this concise syntax is given in "Appendix C.2", next to
the grammar of the reference syntax.

This surface syntax is line-oriented: a command is on a single line.

It is intended for lexicographers who frequently create entries, senses, examples, and their properties in a text document containing both commands and ordinary prose. The parser must then distinguish LiftPatchShort concise command line from ordinary prose paragraphs.

Every valid surface command MUST have an unambiguous expansion into one reference-language command, possibly a block command. All the semantic principles of the reference syntax MUST be followed. LiftPatchShort expresses a subset of what LiftPatchRef expresses, and it expresses it with a different surface syntax; it never has a semantics of its own.

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

### 1.1 Recognizing a command line in a mixed document

A one-letter prefix followed by a space is not, by itself, a reliable signal in a
document that also contains prose: ordinary sentences beginning with "c ", "d ",
"s " or "e " are not rare, in English or in French. The recognition rule below
keeps the concision of a one-letter command while making an accidental match
very unlikely, because it constrains the *second* token as well.

A line is a LiftPatchShort command line if and only if, after optional leading
whitespace, it matches one of the following:

1. `COMMAND-LETTER`, optionally followed by the multiplicity marker `*`, followed by one or more whitespace characters, followed by a second token that begins with:
   - `/` — a path (`c /mami`, `d /e[f="mami"] s[g="pig"]`), or
   - `$` — a label (`s $pig (d@en = "A pig")`), or
   - a component letter (or component name) immediately followed by `(` — a constructor with no parent path, which only `c` and `p` accept (`c e("mami")`, `p entry(f="mami", has-gloss="pig")`), or
   - `(` — the parenthesis of a property command with no path, which is admitted **only on an indented line**, where the parent is supplied by the enclosing indented block (`s (d@en = "A pig")`, section "3.3"), or
   - a component letter (or component name) immediately followed by `[`, `#` or `^` — a *relative path*, or the bare target step of a `d` or an `e`, also admitted **only on an indented line**, whose first step is a child of the enclosing indented block's component (`d x#1`, `d n^draft`, `d s[g="pig"] x#1`, section "3.3").
2. `language-default ` or `language-create ` followed by `object` or `meta`:

```
language-default object = "tww"
language-default meta = "en"
language-create object = "tpi"
language-create meta = "fr"
```

3. the version pragma `%liftpatch 1.0`, anywhere before the first command line of the document; a pragma declaring a `sigil` must be the first non-blank line (section "1.1").

Every command has a second token of one of the five shapes of rule 1: `c` and
`p` take either a path or a constructor; `s`, `u` and `l` take a path; `d`, `e`
and `m` take either a parent path or — when their direct target is an entry or a
label, and so has no parent path — a **rooted target step**; and in every case
that second token begins with `/` or with a label, so the recognition rule itself
is unchanged. On an indented line a property command may take its parenthesis
directly, `c` or `p` its constructor, and `d` or `e` a relative path or a bare
target step, because the enclosing indented block supplies the parent (section
"3.3").

**Leading whitespace is significant.** It does not change whether a line is a
command — the rule above skips it — but it decides which component the command
applies to, under the indentation rule of section "3.3". A command line at
column 1 is a top-level command, so a document that never indents a command is
a flat sequence of independent commands.

The command letters are:

```text
c  d  m  p  e  u  s  l
```

Two further rules make the recognition safe in both directions:

- **A line that matches the rule but does not parse is an error, not prose.** It is reported with its position and the script fails. Silently demoting a mistyped command to prose would drop an intended edit without telling anyone.
- **Prose that matches the rule can be escaped** by prefixing the line with a backslash. A line beginning with `\` is never a command; the backslash is not part of the text.

**Strict mode: an explicit line sigil.** In a document where a false positive
would be costly — a document quoting LiftPatchShort commands as examples, for
instance — the version pragma may declare a *sigil*. A sigil is RECOMMENDED in
**any** document that mixes commands with substantial prose: it reduces
recognition to a single character, it makes every namespace and look-ahead rule
of section "2" irrelevant to the question "is this line a command", and it lets a
lexicographer write about pigs in a file that also creates them:

```LiftPatchShort
%liftpatch 1.0 sigil=">"
```

- The sigil is one character, declared on the pragma line, which must then be the first non-blank line of the document.
- When a sigil is declared, a line is a command **if and only if** it begins, after optional leading whitespace, with that sigil; the rest of the line is then read exactly as above, and a line matching the rules 1–3 above without the sigil is prose. The sigil is not part of the command.
- When no sigil is declared, the rules 1–3 above apply; this is the default and the common case.
- In sigil mode, the indentation of a command (section "3.3") is measured from the **sigil**, not from the start of the line: whitespace between the sigil and the command letter is the indentation, and whitespace before the sigil is ignored. This keeps the sigils aligned in a left-hand column, where they are readable.

```LiftPatchShort
> c /mami
>   c s("pig")
>     s (d@en = "A pig")
```

**Comments.** A `#` preceded by whitespace, or beginning a line, starts a
comment that runs to the end of the physical line, exactly as in LiftPatchRef. A
`#` immediately following a path step, with no whitespace before it, is the
ordinal marker of section "4.2", and a `#` inside a quoted string is an ordinary
character:

```LiftPatchShort
d /e[f="mami"]/s[g="pig"] x#1   # deletes the first example, not a comment marker
```

Comments are not required in a mixed document, where prose lines already
surround the commands; they are available for annotating a command on its own
line.

A command continues until the end of its physical line: the concise syntax has
no multiline command, and a long or complex operation must be written in
LiftPatchRef. An indented block (section "3.3") does not break this rule: it is a
*sequence* of one-line commands sharing a parent, not one command spread over
several lines.

### 1.2 Constructs with no concise form

The concise syntax is a subset. The following constructs of the reference syntax
have no concise form, and a script that needs them must be written in
LiftPatchRef:

- `with` language scoping;
- an `at` clause on an embedded initializer (section "7");
- a block header that is a bare selector chain with no verb: a LiftPatchRef block may be headed by `sense[gloss="pig"] of entry[form@tww="mami"] { … }`, which asserts nothing and creates nothing, whereas the head of an indented block is always a command. Write `e` (ensure) as the head to get the same effect: `e /e[f="mami"] s[g="pig"]`. The restriction is on what a concise document may *write*: such a header may still appear in the LiftPatchRef expansion of a concise command, as it does for the embedded initializers of section "7".

Blocks themselves **do** have a concise form: an indented block (section "3.3")
is the concise spelling of a LiftPatchRef block, with the same parent anchoring,
the same scope for labels and language defaults, and the same transaction
boundary.

## 2. Short codes

In concise syntax, commands, components, and properties are designated by a single-letter code. The semantics, however, remain exactly the same.

Some commands, components, and properties are abbreviated by the same letter (for instance, `e` = the `ensure` command and the `entry` component). However, the same letter is never used for two commands, for two properties, or for two components. Position alone decides which namespace a letter belongs to, and the rule is normative:

1. The first token of a command line is a **command** letter, optionally followed by `*`.
2. A letter immediately followed by `[`, `#`, `^` or `(` and appearing either inside a path (after `/` or `!`), or in direct-target position — the target step of a `d`, an `e` or the source of an `m` (section "4.1") — or as the head of an initializer, is a **component** letter.
3. A letter appearing inside square brackets or inside a parenthesized list, and followed by (optional whitespace) `=`, `@`, `,` or `)`, is a **property** letter. All four terminators matter, the comma included: in the `clear` list `l /mami/pig/a[y="edit", v="todo"] (w, h)`, the property `w` (`when`) is followed by a comma and by nothing else, and in `l /mami/pig/c (v)` the property `v` (`value`) is followed only by the closing parenthesis.
4. A letter followed by `(` inside an initializer list is a **component** letter opening an embedded initializer (section "7"); the same letter followed by `=`, possibly after whitespace, is a property letter. One token of look-ahead therefore suffices.
5. A token that is exactly the code or the full name of a **singleton** component type (Part 1, section "3.1"), that stands where a path step is expected, and whose type the metamodel admits as a child of the component the preceding step resolved, is a **component** token naming that singleton. This is the one component step followed by nothing, and the rule is decidable because the set of singleton types and the parentage table are both fixed by the metamodel. Where the rule does not apply — no singleton of that name is admissible at that point — the token is an abbreviated step, that is a bare word (section "6.1"). A quoted token is **always** an abbreviated step: write `/mami/pig/"c"` for an example whose text is `"c"`, and `/mami/pig/c` for the category of that sense.

**Full names as aliases.** Anywhere a one-letter component code or property code
is expected, the full reference-syntax name may be written instead: `entry` for
`e`, `translation` for `o`, `transcription` for `r`, and so on. The two forms
have identical semantics and may be mixed in the same command:

```LiftPatchShort
c /entry[form="mami"] sense(gloss="pig")
c /e[f="mami"] s(g="pig")
```

This keeps the one-letter codes for the frequent components and properties
without forcing a lexicographer to remember that `o` is a translation and `l` a
reversal. A component name — one letter or full — is recognized as such when it
is immediately followed by `[`, `#`, `^` or `(`; a path step written `/entry`,
with nothing after it, is the bare entry form `"entry"`, exactly as `/e` is the
bare entry form `"e"` (section "6.1").

**The one exception is a singleton.** A singleton component (Part 1, section
"3.1") is written with its name alone, so it is the one component step that is
followed by nothing. Rule 5 below settles it, and section "6.1" states the
consequence for abbreviated steps.

### 2.1 Command codes

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

The short code must be followed by whitespace, or by the multiplicity marker
`*` and then whitespace.

The code for upsert is `p` because `u` is reserved for update.

**The multiplicity marker.** The keywords `each` and `all` of the reference
syntax (section "5.6" of Part 1) are written `*`, appended to the command
letter:

```LiftPatchShort
d* /e[f="mami"]/s[g="pig"] x[t~"^draft"]
s* /e[f="mami"]/s[g="pig"] (d@en = "A pig")
```

- `*` is allowed only on `d`, `s`, `u` and `l`, that is on `delete`, `set`, `update` and `clear`. On `c`, `p`, `m` and `e` it raises `MULTIPLICITY_NOT_ALLOWED`. The grammar of Appendix C.2 admits `*` after every command letter precisely so that this check can report that code: a marker on the wrong verb is a mistake about the language, and telling the writer "multiplicity is not allowed here" is more useful than telling them the line does not parse.
- It marks the target step of the command — the direct-target step for `d` (section "4.1"), the last step of the path for the property commands — exactly as `all` / `each` do in the reference syntax, with the same rules and the same errors.

### 2.2 Component codes

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
| Category | `c` |

### 2.3 Property codes

The properties on components are the same as in LIFT-DSL language, they are referred to with a single letter code:

| Property | Concise LIFT-DSL |
|---:|---|
| `form` | f |
| `morpheme` | m |
| `definition` | d |
| `gloss` | g |
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

### 3.1 Component commands

#### *create*

The created component is created under the last selected path component.

```text
c <parent_path> <component_type>(<initializer>) [at <position>] [as $<label>]
```

an entry has a special syntax since it is created without a parent path:

```text
c e(<initializer>) [as $<label>]
```

a third form takes a path and no constructor at all, and creates the component
that the last step of the path denotes:

```text
c <path>
```

This third form is available only when the last step of the path is an
**abbreviated step** (section "6.1"), that is an entry designated by its bare
form, a sense designated by its bare gloss, or an example designated by its bare
text. It is defined as a shorthand:

| Written | Means |
|---|---|
| `c /<form>` | `c e(f = "<form>")` |
| `c /<form>/<gloss>` | `c /e[f = "<form>"] s(g = "<gloss>")` |
| `c /<form>/<gloss>/<text>` | `c /e[f = "<form>"]/s[g = "<gloss>"] x(t = "<text>")` |

```LiftPatchShort
c /mami                      # create entry(form = "mami")
c /mami/pig                  # create sense(gloss = "pig") under entry[form = "mami"]
c /mami/pig/"a mami jefi"    # create example(text = "a mami jefi") under that sense
```

In the second and third of those lines, the leading steps of the path keep their
ordinary meaning: they *select* and never create. `c /mami/pig` therefore
requires the entry "mami" to exist and to be unambiguous — the link immediately
above the created component is the strict axis (section "4") — and it fails with
`NOT_FOUND` or `AMBIGUOUS_REFERENCE` otherwise. Only the last step is created.
To create the whole chain at once, use `p /mami/pig` (section "8") or an embedded
initializer (section "7").

If the last step of the path is **not** an abbreviated step, there is nothing for
the command to build the new component from, and the command is rejected with
`CONSTRUCTOR_REQUIRED`, a static error: `c /e[f = "mami"]/s[g = "pig"]` names an
existing sense by a selector and asks for it to be created, which is not a
meaning the language can give it. Write the constructor:
`c /e[f = "mami"] s(g = "pig")`.

A fourth form has no path at all. It is available only on an **indented** line,
where the enclosing indented block supplies the parent (section "3.3"):

```LiftPatchShort
c /mami
  c s("pig")
```

#### *upsert*

The upserted component is matched or created under the last selected path component:

```
p <parent_path> <component_type>(<initializers>) [at <position>] [as $<label>]
p e(<initializers>) [as $<label>]   # for entry
p <path>                            # abbreviated steps only, see section "8"
p <component_type>(<initializers>)  # indented lines only, see section "3.3"
```

As with `c`, the path-only form requires every step it creates to be an
abbreviated step, and raises `CONSTRUCTOR_REQUIRED` otherwise (section "8").

**Upserting an entry needs a disambiguation predicate.** `upsert` on an entry is
rejected with `UPSERT_ENTRY_WITHOUT_DISAMBIGUATION` when the initializer list
gives a `form` and nothing to tell the homophones apart (Part 2, section
"8.2.3"). Two consequences are specific to the concise syntax and are easy to
trip over:

- `p e("mami")` and `p e(f="mami")` always fail. Write the predicate: `p e(f="mami", has-gloss="pig")`, where `has-gloss` keeps its reference spelling (section "4.3");
- `p /mami`, a path with a single abbreviated step, always fails for the same reason. The useful form is the two-step one, `p /mami/pig` (section "8"), which carries its own disambiguation and does not raise the error.

```LiftPatchShort
p e(f="mami", has-gloss="pig")   # select that entry, or create a new "mami"
p /mami/pig                      # the idiomatic form (section "8")
```

#### *delete*

The component denoted by the **target step** is deleted. The target step is
written after the path of its parent, separated from it by whitespace, exactly
as the constructor of a `c` command is:

```
d [<parent-path>] <target-step>
```

An `entry` has no parent, so its target step carries the root marker and stands
alone:

```
d /e[<selector>]   # for entry
d /mami            # the same, abbreviated (section "6.1")
```

```LiftPatchShort
d /e[f="mami"]/s[g="pig"] x#1
d /e[f="mami"] s[g="pig"]
```

A target written as two or more steps — `d /mami/pig` — is rejected with
`TARGET_MUST_BE_A_SINGLE_STEP`, a static error (section "4.1").

#### *move*

The target step of the source is moved to a different position or, if a
destination path is expressed, under another parent (the last step of the
destination path) at the specified position. The destination path is introduced
by the keyword `to`:

```
m <source-path> <target-step> [to <destination-path>] at <position>
```

`to` names the **parent** the component moves under, and is an ordinary parent
path: its last step is the new parent, and the moved component is joined to it by
the strict axis, which is not written.

```LiftPatchShort
m /e[f="mami"]/s[g="pig"] x#1 to /e[f="mami"]/s[g="large animal"] at end
```

The `at <position>` clause is mandatory, as in the reference syntax. `m` cannot
target an entry: `m /mami` is a syntax error. Since `m` may not target an entry
and may not appear in an indented block (section "3.3"), its source parent path
is always present and always absolute, so the source path and the source target
step are never ambiguous.

#### *ensure*

The component denoted by the **target step** is asserted to exist. `ensure`
selects and never creates, so it takes a path and a target step and no
constructor. Like `c` and `p`, it may bind a label (Part 1, section "6.3"), which
makes it the concise way to name a component the document did not create:

```
e [<parent-path>] <target-step> [as $<label>]
```

```LiftPatchShort
e /e[f="mami"] s[g="pig"]
e /mami pig
e /mami pig as $pig
s $pig (d@en = "A pig")
```

`ensure` may target any component type, `entry` included; on an `entry` the
target step stands alone, as it does for `d`: `e /mami`.

### 3.2 Property commands

The properties are between parentheses, with only their name (for clear) or their name and their value separated by "=" (for set and update). One command may carry several of them, separated by commas.

#### *set*

```text
s <path> (<property> = <value> {, <property> = <value>})
```

#### *update*

```text
u <path> (<property> = <value> {, <property> = <value>})
```

#### *clear*

```text
l <path> (<property> {, <property>})
```

On an **indented** line the path of a property command may be omitted, and the
command then applies to the component of the enclosing indented block (section
"3.1"): `s (d@en = "A pig")`. Outside an indented block the path is mandatory, and a
property command written without one — like a component command other than an
`entry` written without a parent — is rejected with `MISSING_PARENT_CLAUSE`, a
static error.

A list of properties in one command is the concise spelling of the assignment
list of Part 2, section "9.1.1", and has exactly its semantics: the path is
resolved once, the assignments are applied in the order written, and the command
is atomic.

```LiftPatchShort
s /mami/pig (d@en = "A four-legged terrestrial animal", d@fr = "Un animal terrestre")
l /mami/pig (d@en, d@fr)
```

Three rules of the reference syntax apply unchanged to the parenthesized part:

- The language qualifier is written as in the reference syntax, and `clear` requires an explicit one on a multitext (Part 1, section "5.5.3"; concise examples in section "5.6").
- A multi-language literal is written as in the reference syntax: `s /mami (f = { tww: "mami", tpi: "pik" })`.
- A property of datatype `reference` takes a **path** as its value, never a bare string:

```LiftPatchShort
s /mami/pig/r#1 (a = /memi)
s /e[f="mami"]/r[y="synonym", a = /e[id="entry-42"]] (a = /memi)
```

  The path is resolved like any other path and must yield exactly one component. A label may be used instead: `(a = $newEntry)`. A bare string such as `(a = "entry-42")` is rejected with `REFERENCE_VALUE_MUST_BE_A_CHAIN`.

  Both lines above select the relation before rewriting its target, and neither
  writes `r[y="synonym"]` alone: the natural identity of a `relation`, a `variant`
  and a `reversal` is `type + target`, so a unique selector on one of them must
  give both properties — or reach it by its ordinal, as the first line does (Part
  2, section "10"). `r[y="synonym"]` on its own is `INCOMPLETE_SELECTOR`.

  The same holds **inside a selector**, where a `reference` property is compared
  to a path and not to a quoted id (Part 2, section "10"):

```LiftPatchShort
d /e[f="mami"] v[y="dialectal", a = /memi]
```

A path used as a **reference value** — the `/memi` above, or the `(a = /memi)`
of the two preceding commands — has no separated target: it is not a command's
parent path, so its **last step is the designated component** itself, and the
unique/filtering rule of section "4.3" applies to it unchanged.

**Labels.** A `c` or `p` command may bind the component it creates or resolves,
with `as $<label>` at the end of the line; the label is then usable in any later
path or reference value of the same document:

```LiftPatchShort
c e("mami") as $new
c /memi r(y="synonym", a = $new)
```

Section "5" takes each of the eight commands in turn, with an example and its
translation into the reference syntax.

### 3.3 Indented blocks

A lexicographer entering a new word writes about one entry at a time, and then
about one sense at a time. Written with a path on every line, that session
repeats the path on every line:

```LiftPatchShort
c /mami
c /mami/pig
s /mami/pig/c (v = "Noun")
c /mami/pig x("a mami jefi")
c /mami/pig/"a mami jefi" o("free", "I shot a pig")
```

**Indentation** removes the repetition. It is the concise spelling of the block
of Part 2, section "11", and it has exactly that semantics — a parent for the
commands it contains, a scope for labels and language defaults, and a
transaction boundary:

```LiftPatchShort
c /mami
  c s("pig")
    s (d@en = "A four-legged terrestrial animal")
    c x("a mami jefi")
      c o("free", "I shot a pig")
  c n("general", "recorded at Yakoro, 2025")
```

#### The rule

1. **Grouping.** A *command group* is a maximal run of consecutive lines each of which is a command line, a directive line, or a comment line. A blank line or a prose line ends the group. Indentation relates commands **inside one group only**: a command cannot be the parent of a command in another group, however the two are indented. This is what makes the construct safe in a document where commands and prose alternate — an indented line far below a paragraph of prose is a top-level command, not a continuation of something the reader can no longer see.
2. **Indentation.** The indentation of a command is the number of space characters before it — before the command letter, or, in sigil mode, between the sigil and the command letter (section "1.1"). A **tab** in the indentation of a command line is a `SYNTAX_ERROR`: the width of a tab is a property of the reader's editor, and the parent of an edit may not depend on it.
3. **Anchoring.** A command indented more than the command line that precedes it in its group opens a block: that preceding command is its *anchor*, and the component the anchor creates, upserts, asserts or targets is the parent of the indented command, exactly as a block header's component is the parent of the block body (Part 2, section "11"). Further lines at the same indentation belong to the same block. A line indented less returns to an already open level, and MUST match one exactly; an indentation that matches no open level is a `SYNTAX_ERROR`.
4. **How much** a line is indented does not matter, only that it is more, equal, or less. One level is one block.

An anchor must designate exactly one component that still exists after it has
run, so:

- `c`, `p` and `e` are the natural anchors, and are the common case;
- a property command (`s`, `u`, `l`) may be an anchor as well: it designates the component it writes on, which is still there afterwards;
- `d` may **not** be an anchor. Its component is gone by the time the indented commands would run, so a line indented under a `d` is rejected with `DELETE_CANNOT_BE_AN_ANCHOR`, a static error;
- `m` may neither be indented under anything nor be an anchor (`MOVE_NOT_ALLOWED_IN_BLOCK`, Part 2, section "11");
- a command carrying the multiplicity marker `*` designates several components and cannot be an anchor: `MULTIPLICITY_NOT_ALLOWED`.

#### What an indented command may omit

Inside a block, the parent is given, so the command need not name it. Four
forms become available, and they are the forms the recognition rule of section
"1.1" admits only on an indented line:

| Form | Written | Means |
|---|---|---|
| no path | `c s("pig")` | create under the block's component |
| no path | `s (d@en = "A pig")` | set on the block's component |
| target only | `d x#1` | a target step that is a **child** of the block's component |
| relative path + target | `d s[g="pig"] x#1` | a parent path whose first step is a **child** of the block's component, and the target below it |

A path that begins with `/` keeps its ordinary meaning inside a block: it is
resolved from the root, exactly as at top level. The two are therefore never
confusable — a leading slash always means "from the root", and its absence
always means "from here".

#### What an indented block inherits

An indented block **is** a block, so every rule of Part 2, section "11" applies
without restatement:

- its commands are executed in source order, each seeing the state left by the preceding ones;
- it is a transaction: if any command in it fails, the block is rolled back, and — since the block then fails — so is the whole document (Part 2, section "12.2");
- `as $<label>` bindings and `language-default` directives inside it are scoped to it; a label bound by the **anchor** is visible after the block, in the enclosing scope;
- `m` may not appear in it (`MOVE_NOT_ALLOWED_IN_BLOCK`);
- `p`, `e` and `u` may not appear in the scope of a `c`, directly or indirectly (`CANNOT_UPSERT_ENSURE_OR_UPDATE_IN_CREATION_BLOCK`): a component the same document has just created cannot already have children to upsert or assert;
- `c e(...)` may not appear in it: an entry has no parent (`ILLEGAL_PARENT`).

#### The example, expanded

The block above is exactly:

```LiftPatchRef
create entry(form@tww = "mami") {
  create sense(gloss@en = "pig") {
    set definition@en = "A four-legged terrestrial animal"
    create example(text@tww = "a mami jefi") {
      create translation(type = "free", text@en = "I shot a pig")
    }
  }
  create note(type = "general", text@en = "recorded at Yakoro, 2025")
}
```

Compared with the embedded initializers of section "7", which express the same
tree on one line, an indented block is not restricted to creation: its commands
may be `s`, `u`, `l`, `d` or `e` as well as `c`, and its anchor may be a `p` or
an `e`, which is what lets a session update an entry that already exists:

```LiftPatchShort
e /mami pig as $pig
  s (d@en = "A four-legged terrestrial animal")
  l (d@fr)
  d* x[t ~ "^draft"]
```

The last line carries the multiplicity marker because its predicate is a
filtering one: `t ~ "^draft"` may match several examples, and a target step that
may match several must say so (Part 1, section "5.6"). Matching none is not an
error, so the line is safe on a sense that has no draft example.

## 4. Paths

A path starts with a slash and is a sequence of slash-separated steps. Every
path written in a command is a **parent path**: it names the parent of what the
command acts on, and never that thing itself. The **direct target** is always
written after the path, outside it — as a constructor (`c`, `p`), as a
parenthesized property list (`s`, `u`, `l`), or as a single *target step*
separated from the path by whitespace (`d`, `e`, and the source of `m`). The
only paths that are not parent paths are the ones used as **reference values**
(section "3.2"), which designate their own last step.

Each step is made of a letter indicating the component type (or the full
component name, section "2") followed by one or several predicates between square
brackets, or by an ordinal.

Three properties of a parent path govern everything in this section, and the
first is the one most easily got wrong when passing between the two syntaxes:

- **A path reads from the ancestor down to the descendant**, left to right: `/e[f="mami"]/s[g="pig"]` is the entry first and its sense second. A LiftPatchRef chain reads the other way, from the component up to its ancestors: `sense[gloss@en="pig"] within entry[form@tww="mami"]` is the same chain written backwards (Part 1, section "5.1.1"). Wherever this section says "the step that follows", it means the step to the **right**, that is the **child**.
- **A path starts at the root.** Its first step is an `entry` — written `e[…]`, `e#n` is not available since entries have no ordinal, or an abbreviated bare form (section "6.1") — or a label bound earlier in the document, which stands for whatever component it was bound to. A path whose first step is anything else is rejected with `INCOMPLETE_ANCESTOR_CHAIN`, a static error: `s /s[g="pig"] (d@en = "A pig")` names no entry. A path that **skips a level** of the hierarchy of Part 1, section "3", is a different error: `d /e[f="mami"] x[t="…"]` states that an `example` is a child of an `entry`, and is rejected with `ILLEGAL_PARENT`.
- **Inside an indented block, a path may instead be relative** (section "3.3"): written without the leading `/`, its first step is a child of the block's component. The rule above then applies from that component rather than from the root. A `d` or an `e` inside a block may also carry no parent path at all, its target step then being a child of the block's component.

### 4.1 The axis of a link

A path separator is an *axis*, and the concise syntax has two, exactly as the
reference syntax (Part 1, section "5.1.1"). Each has one spelling, and that
spelling does not depend on where the link stands:

| Separator | Axis | Reference-syntax keyword |
|---|---|---|
| `/` | existential parent | `within` |
| `!` | strict parent | `under` / `of` / `on` |

Five rules govern every path of the concise syntax.

**R1 — Two axes, one spelling each.** `/` is the existential axis wherever it
appears between two steps, and `!` is the strict axis wherever it appears. A
**leading** `/`, the one that opens an absolute path, is not an axis at all: it
is the *root marker* that says the first step is an `entry`.

**R2 — The direct target is never in the path.** Every command is written

```text
VERB [<parent-path>] <target>
```

where the link joining the last step of the parent path to the target is the
**strict** axis and is never written: it is the whitespace between the two. The
target is:

| Command | Direct target |
|---|---|
| `c`, `p` | the constructor, `s(g="pig")` |
| `s`, `u`, `l` | the parenthesized property list, `(d@en = "A pig")` |
| `d`, `e`, source of `m` | a single **target step**, written after the path |

`m`'s destination is an ordinary parent path and is introduced by the keyword
`to` (section "3.1"): `m <path> <step> [to <path>] at <position>`.

**R3 — Unique versus filtering is decided by the axis, and by nothing else.**
No step has to be counted:

- the **direct-target step** is a unique selector — a *command selector* (Part 1, section "5.1.1") — unless the command carries the multiplicity marker `*`;
- the **last step of a parent path** is a unique selector: it is the strict parent of the target;
- a step whose **child** link is written `!` is a unique selector;
- **every other step** is a filtering step.

As in the reference syntax (Part 2, section "7.3.1"), a unique selector and the
`/` chain of ancestors standing above it — to its **left**, in a concise path —
form a single group, resolved as a whole: the uniqueness bears on the result of
the group, not on each step of it. In `d /e[f="mami"]/s[g="pig"] x#1` the group
is the whole parent path, the sense is the unique selector it must yield exactly
one of, and the entry is a filtering step inside it.

**R4 — Rooting and relativity are unchanged.** A leading `/` means "from the
root"; its absence means "from the component of the enclosing indented block"
(section "3.3"). When the target has no parent path — an `entry`, or a label —
the **target step itself** carries the root marker: `d /mami`, `e /e[f="mami"]`,
`d $pig`. Inside a block a `d` or an `e` may write its target step alone,
`d x#1`, or a relative parent path and a target, `d s[g="pig"] x#1`.

Parsing is deterministic: the **last top-level whitespace-separated path token**
before any `to`, `at` or `as` clause is the direct target, and whatever precedes
it is the parent path. *Top-level* means outside every bracket, parenthesis,
brace and quoted string, so the space inside `e[f="mami", hn=1]` and the ones
inside `v[y="dialectal", a = /memi]` do not split a token, and
`d /e[f="mami", hn=1] v[y="dialectal", a = /memi]` has a one-step parent path and
a one-step target. `m` is unambiguous for the same reason, and because it may not
target an `entry` and may not appear in a block, so its source parent path is
always present and always absolute.

**R5 — The target is exactly one step.** A target token of two or more steps is
rejected with `TARGET_MUST_BE_A_SINGLE_STEP`, a static error. `d /mami/pig` and
`e /e[f="mami"]/s[g="pig"]` raise it: a command that writes its parent path and
its target as one chain says nothing about where the one ends and the other
begins, and the language refuses it loudly rather than guessing. Write
`d /mami pig` and
`e /e[f="mami"] s[g="pig"]`.

So:

- in `c /e[f="mami"] s(g="pig")`, the target is the created sense; the link between it and the entry is strict, unwritten, and the path — one step — has no link at all;
- in `c /e[f="mami"]/s[g="pig"] x(t="…")`, the target is the created example; the link example–sense is strict and lies outside the path, and the path's only written link, sense–entry, is existential;
- in `d /e[f="mami"] s[g="pig"]`, the target is the sense; the link sense–entry is the unwritten strict one, so both selectors are unique;
- in `d /e[f="mami"]/s[g="pig"] x[t="…"]`, the target is the example; the link example–sense is the unwritten strict one, and the written link sense–entry is existential, so the entry is a filtering step;
- in `d /e[f="mami"]!s[g="pig"] x[t="…"]`, the same delete with the sense–entry link made strict: the entry is now a unique selector too, and must resolve to exactly one entry;
- in `s /e[f="mami"]/s[g="pig"] (d@en="A pig")`, the target is the `definition` property; the strict link joins it to the sense, and the written link sense–entry is existential.

The **path-only forms** of `c` and `p` — `c /mami/pig`, `p /mami/pig/"a mami
jefi"` — carry no separated target, because sections "3" and "8" define them by
their *expansion*, into a constructor command and into nested upserts
respectively, and the rules above apply to the expanded command. That is why
`c /mami/pig` requires the entry "mami" to be unambiguous: its expansion,
`c /e[f="mami"] s(g="pig")`, puts the entry immediately above the created sense,
on the strict axis.

This means that the following path, **used as the parent path of a command**:

```Path
/e[f="mami"]/s[g="pig"]
```

translates, in the reference language, into:

```Fragment
s[g="pig"]
within  e[f="mami"]
```

Because the entry and the sense are linked by the existential axis, the sense is
selected out of the set of entries with a form "mami", by the existential join
described in Part 1, section "7.4": the whole path must resolve to exactly one
component, and it raises `AMBIGUOUS_REFERENCE` when it does not.

Written with the strict axis instead:

```Path
/e[f="mami", hn=1]!s[g="pig"]
```

translates into:

```Fragment
s[g="pig"]
of  e[f="mami", hn=1]
```

Here the entry is a unique selector in its own right, which is what admits the
pseudo-property `hn` on it (Part 1, section "5.1.4"): the entry must resolve to
exactly one component, and then the sense must, under it.

And with a `create` command, where the path selects a sense and the initializers create an example under it:

```LiftPatchShort
c /e[f="mami"]/s[g="pig"] x(t="A mami jefi")
```

translates into:

```LiftPatchRef
create example(text="A mami jefi")
under sense[gloss="pig"]
within entry[form="mami"]
```

### 4.2 Ordinals

The ordinal of the reference syntax, written `#n` there (Part 1, section
"5.3.3"), is written `#n` in the concise syntax as well, and may also be written
as a bare integer between square brackets:

```LiftPatchShort
d /e[f="mami"]/s[g="pig"] x#1
d /e[f="mami"]/s[g="pig"] x[1]
```

Both lines are equivalent to:

```LiftPatchRef
delete example#1
  under sense[gloss@en = "pig"]
  within entry[form@tww = "mami"]
```

- The ordinal counts same-type siblings only and starts at 1.
- As in the reference syntax, an ordinal cannot occur together with any predicate in the same step: `x[1]` and `x#1` carry the ordinal and nothing else, and `x[g="pig"]#1` is a syntax error.
- The `#n` form is RECOMMENDED, since it is the form used by the reference syntax; the `[n]` form is kept for concision.
- The ordinal is available on **ordered** component types only (Part 1, section "3.1"); on a typed or a singleton one it raises `COMPONENT_NOT_ORDERED`. There is no `[n]` alias for the type key of section "4.2.1".

#### 4.2.1 Type keys and singleton steps

The other two selection devices of Part 1, section "3.1", are written in the
concise syntax exactly as in the reference syntax, and are the concise way to
reach a typed or a singleton component:

| Component kind | Step | Means |
|---|---|---|
| ordered | `x#1` | the first `example` child (section "4.2") |
| typed | `n^general` | the `note` child whose `type` is `"general"` (Part 1, section "5.3.4") |
| singleton | `c` | the `category` child, of which there is exactly one (Part 1, strategy S7) |

```LiftPatchShort
u /e[f="mami"]/s[g="pig"]/n^general (t@en = "recorded at Yakoro")
s /mami/pig/c (v = "Noun")
d /e[f="mami"] n^draft
```

- The **type key** `^t` takes a bare word (section "6.1.1") or a quoted string: `n^general` and `n^"field note"` are both steps. It cannot occur together with a predicate list in the same step — `n[t@en="…"]^general` is a syntax error — and it is available on **typed** component types only, raising `COMPONENT_NOT_TYPED` elsewhere. Like the ordinal, it selects and never creates, so it may not appear on a constructor: `c /mami n^general(t@en = "…")` is a syntax error, and the `type` is written as an ordinary initializer, `c /mami n(y="general", t@en = "…")`.
- The **singleton step** is the component code or name alone, with nothing after it. It is the one path step with no suffix and no brackets, and rule 5 of section "2" is what tells it apart from an abbreviated step: `/mami/pig/c` is the category of the sense "pig", while `/mami/pig/"c"` is the example whose text is `"c"`. A singleton step carrying a selector in a unique position — `/mami/pig/c[v="Noun"]` as the parent path of a command — raises `SINGLETON_TAKES_NO_SELECTOR`; inside a `has` predicate or on the parent side of a `/` link the same spelling is a filtering step and is legal: `d /e[f="mami", has s[has c[v="Verb"]]] s[g="pig"]`.
- A singleton is never created, never deleted and never moved, so `c /mami/pig c(v="Noun")`, `d /mami/pig c` and any `m` naming one raise `SINGLETON_CANNOT_BE_CREATED_OR_DELETED`. Write the property command instead: `s /mami/pig/c (v = "Noun")`.

### 4.3 Predicates in a step

The predicates of the reference syntax (Part 1, section "5.1.2") are available
unchanged in the concise syntax; only the property name is abbreviated. The
operators, the keywords `has`, `exists` and `absent`, and the pseudo-property
`has-gloss` keep their reference spelling — they are not abbreviated to a
letter, since letters are reserved for component and property names:

| Concise form | Reference form | Allowed in |
|---|---|---|
| `[f = "mami"]` | `[form = "mami"]` | any step |
| `[f != "mami"]` | `[form != "mami"]` | filtering steps only |
| `[t ~ "^draft"]` | `[text ~ "^draft"]` | filtering steps only |
| `[t ~i "^draft"]` | `[text ~i "^draft"]` | filtering steps only |
| `[exists(d@fr)]` | `[exists(definition@fr)]` | filtering steps only |
| `[absent(v)]` | `[absent(value)]` | filtering steps only |
| `[has s[g = "pig"]]` | `[has sense[gloss = "pig"]]` | any step, subject to Part 1, section "5.3.2" |
| `[has-gloss = "pig"]` | `[has-gloss = "pig"]` | see Part 1, section "5.3.2.2" |
| `[id = "entry-42"]` | `[id = "entry-42"]` | see Part 1, section "5.3.1" |
| `[hn = 1]` | `[hn = 1]` | see Part 1, section "5.3.2.1" |
| `^general` | `^general` | typed components, unique selectors only; see Part 1, section "5.3.4" |

Which steps are *unique selectors* and which are *filtering steps* is rule R3 of
section "4.1", restated here in full. It is decided by the axis of each link, and
never by counting steps:

- the **direct-target step** of a `d`, an `e` or the source of an `m` is a unique selector. The other four commands have no target step to classify: the target of `c` and `p` is a constructor and the target of `s`, `u` and `l` is a property list, and neither selects anything;
- the **last step of a parent path** is a unique selector: the link joining it to the direct target is the strict axis, written as whitespace;
- a step whose **child** link is written `!` is a unique selector;
- **every other step** — that is, every step on the parent side of a `/` link — is a filtering step;
- the direct-target step becomes a **filtering step** when the command carries the multiplicity marker `*`, which is exactly what the marker says. On a **singleton** target step the marker instead makes the step naming its host the filtering one, as in the reference syntax (Part 1, section "5.6"): `s* /e[f="mami"]/s[g!="pig"]/c (v = "Noun")` writes on the category of every matching sense;
- any step inside a `has` predicate is a filtering step, at any depth;
- a path used as a **reference value** (section "3.2") has no separated target, so the same rules apply to it with its own last step in the direct-target position.

Every step that is not a filtering step must select exactly one component, and a
predicate restricted to filtering steps that appears there — typically on the
immediate parent of the target — raises
`PREDICATE_NOT_ALLOWED_IN_UNIQUE_SELECTOR`, exactly as in the reference syntax.
Conversely a pseudo-property — `id`, `hn`, `has-gloss`, an ordinal, a type key — may appear
only on a unique selector, which is why `!` is what makes
`/e[f="mami", hn=1]!s[g="pig"]` legal where `/e[f="mami", hn=1]/s[g="pig"]` is
`PSEUDO_PROPERTY_NOT_ALLOWED_IN_FILTER`.

```LiftPatchShort
d* /e[f ~ "^mam"]/s[g="pig"] x[t ~i "^draft"]
```

```LiftPatchRef
delete all example[text ~i "^draft"]
  under sense[gloss@en = "pig"]
  within entry[form@tww ~ "^mam"]
```

## 5. The commands one by one

### 5.1 Create, upsert and ensure

Commands with initializer are identical to the initializer + `under` in the reference language.

```LiftPatchShort
c /e[f="mami"] s(g="pig")
```

This translates into:

```
create sense(gloss="pig") under
entry[form="mami"]
```

The path identifies the parent of the component created.

As in the reference language:

- ensure (e) takes no initializer at all: it is an assertion, and its component is designated by the target step written after its parent path (section "3.1")
- upsert (p) requires all identity properties
- the `at <position>` clause behaves as in Part 1, section "6.2": optional on `c` and `p`, defaulting to `at end`, forbidden on `e` and on any command creating an `entry` (`c e("mami") at beginning` is a syntax error).
- an `as $<label>` binding may close a `c`, `p` or `e` command line (section "3"), and binds the created, resolved or asserted component (Part 1, section "6.3")
- a multitext initializer may take a multi-language literal, written as in the reference syntax
- The initializer initializes the property or properties that are required for the creation of a component in the reference syntax. If two properties are required, they are separated by a comma:

```LiftPatchShort
c /e[f="mami"]/s[g="pig"] n(y="sociolinguistics", t="my note")
```

It translates into:

```
create note(type="sociolinguistics", text="my note")
under sense[gloss="pig"]
within entry[form="mami"]
```

A command using all the optional parts at once:

```LiftPatchShort
c /e[f="mami"] s(g = { en: "pig", fr: "cochon" }) at beginning as $pig
```

```LiftPatchRef
create sense(gloss = { en: "pig", fr: "cochon" })
  under entry[form@tww = "mami"]
  at beginning
  as $pig
```

### 5.2 Delete

The `delete` command deletes the component named by its target step, the token
that follows its parent path.

This command:

```LiftPatchShort
d /e[f="mami"] s[g="pig"]
```

This translates into:

```LiftPatchRef
delete sense[gloss="pig"] under
  entry[form="mami"]
```

This command:

```LiftPatchShort
d /e[f="mami"]/s[g="pig"] x[t="a mami jefi"]
```

This translates into:

```LiftPatchRef
delete example[text="a mami jefi"]
  under sense[gloss="pig"]
  within entry[form="mami"]
```

As stated in section "4.1", the link between the path and the target — here
example–sense — is the strict axis and is not written; every link of the path is
existential unless it is written `!`.

### 5.3 Move

```LiftPatchShort
m /e[f="mami"] s[g="pig"] at index 1
```

It translates into:

```LiftPatchRef
move sense[gloss="pig"]
under entry[form="mami"]
at index 1
```

When `move` has a `to` clause, it is equivalent to a move with a second under
clause in the reference syntax: it moves towards another parent. The `to` path is
an ordinary parent path, and its last step is the new parent.

Then, the following: 

```LiftPatchShort
m /e[f="mami"]/s[g="pig"] x#1 to /e[f="mami"]/s[g="large animal"] at end
```

is equivalent to:

```LiftPatchRef
move example#1
  under sense[gloss@en = "pig"]
  within entry[form@tww = "mami"]
  under sense[gloss@en = "large animal"]
  within entry[form@tww = "mami"]
  at end
```

The five positions of the reference syntax (Part 1, sections "6.2" and "6.2.1")
are all available, with exactly the same semantics. Since `m` moves an **ordered**
component and nothing else (Part 1, section "8.5"), `STEP` is an ordered step — a
component letter (or component name) with a predicate or an ordinal — and it must
select exactly one same-type sibling of the destination parent:

```LiftPatchShort
m /e[f="mami"]/s[g="pig"] x#3 at after x[t="a mami jefi"]
m /e[f="mami"]/s[g="pig"] x#3 at before #2
```

```LiftPatchRef
move example#3
  under sense[gloss@en = "pig"]
  within entry[form@tww = "mami"]
  at after example[text@tww = "a mami jefi"]
```

In an `at before` / `at after` step, the component letter (or name) may be omitted when it
is the one of the moved or created component, which is the usual case: `at
before #2` and `at before x#2` are the same position in the command above.

`at before` and `at after` are RECOMMENDED over `at index <n>`, which breaks as
soon as the sibling list changes. As in the reference syntax, the `at` clause is
mandatory on `m` and has no default.

### 5.4 Set

```LiftPatchShort
s /e[f="mami"]/s[g="pig"]/c (v = "Noun")
```

Is equivalent to:

```LiftPatchRef
set value = "Noun" 
  on category
  of sense[gloss="pig"]
  within entry[form="mami"]
```

A multitext property is qualified with `@` inside the parentheses, and several
languages can be set at once with a multi-language literal:

```LiftPatchShort
s /e[f="mami"]/s[g="pig"] (d@en = "A four-legged terrestrial animal")
s /e[id="entry-42"] (f = { tww: "mami", tpi: "pik" })
```

```LiftPatchRef
set definition@en = "A four-legged terrestrial animal"
  on sense[gloss="pig"]
  within entry[form@tww = "mami"]

set form = { tww: "mami", tpi: "pik" }
  on entry[id = "entry-42"]
```

The wildcard `@*` is forbidden on `s`, as it is on `set` (Part 2, section
"9.1"): `WILDCARD_NOT_ALLOWED`.

### 5.5 Update

```LiftPatchShort
u /e[f="mami"]/s[g="pig"]/c (v = "Verb")
```

is equivalent to

```LiftPatchRef
update value = "Verb" 
  on category
  of sense[gloss="pig"]
  within entry[form="mami"]
```

`u` requires the targeted value to be already set, and the requirement is on the
*qualified* value: `u /mami/pig (d@en = "…")` raises `UNSET_QUALIFIED_PROPERTY`
if the sense has a definition in French only. As `update` does, `u` rejects the
wildcard `@*` (`WILDCARD_NOT_ALLOWED`); a multi-language literal is the way to
rewrite several languages in one command.

### 5.6 Clear

```LiftPatchShort
l /e[f="mami"]/s[g="pig"]/c (v)
```

is equivalent to

```LiftPatchRef
clear value
  on category
  of sense[gloss="pig"]
  within entry[form="mami"]
```

On a **multitext** property the qualifier is mandatory, and it is either a
language code or the wildcard `@*` (Part 1, section "4.4.1"):

```LiftPatchShort
l /e[f="mami"]/s[g="pig"] (d@en)    # one language
l /e[f="mami"]/s[g="pig"] (d@*)     # every language
l /e[f="mami"]/s[g="pig"] (d)       # MISSING_LANGUAGE_QUALIFIER
```

```LiftPatchRef
clear definition@en
  on sense[gloss="pig"]
  within entry[form="mami"]

clear definition@*
  on sense[gloss="pig"]
  within entry[form="mami"]
```

The default language is never applied by `l`, exactly as it is never applied by
`clear`: on a multitext there is nothing to default, since the command removes
values rather than addressing one. The decision table of Part 2, section
"9.3.1", governs which of these commands succeeds and which error each refused
case raises.

## 6. Abbreviations

### 6.1 Abbreviated path steps

1/ If the first step of a path is a single string between single or double quotes, then it is the form of an entry:

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

If the form of the entry is a *bare word* (section "6.1.1"), it can even be
noted without quotes:

```
/mami
```

Then, the following command create a sense with gloss "pig" under form "mami" in the default object language:

```LiftPatchShort
c /mami s(g="pig")
```

This is equivalent with:

```LiftPatchRef
create sense(gloss="pig")
  under entry[form="mami"]
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
sense[gloss="pig"] under  entry[form="mami"]
```

Again, if the gloss of the sense is a *bare word* (section "6.1.1"), it can even be noted without quotes.

An actual command illustrating this path could be, for instance:

```LiftPatchShort
c /mami/pig x(t="A mami jefi")
```

which is equivalent to the following plain LiftPathShort notation:

```LiftPatchShort
c /e[f="mami"]/s[g="pig"] x(t="A mami jefi")
```

Which in turn is equivalent to the following LiftPatchRef notation:

```LiftPatchRef
create example(text="A mami jefi")
under sense[gloss="pig"]
within entry[form="mami"]
```

3/ if the third step of a path is a single string, quoted or a bare word, then it
is the `text` of an example:

```
/"mami"/"pig"/"a mami jefi"
```

is equivalent to

```
/e[f="mami"]/s[g="pig"]/x[t="a mami jefi"]
```

An example text rarely is a bare word, since it usually contains spaces, so this
third abbreviation is usually written with quotes. It exists because the
entry–sense–example chain is the chain a lexicographer writes most often, and
because it is what makes `c /mami/pig/"a mami jefi"` (section "3") and
`p /mami/pig/"a mami jefi"` (section "8") mean what they look like.

**The abbreviation is decided by the depth of the step below the root, and the
list of depths is exhaustive:**

| Depth from the root | Component | Property |
|---|---|---|
| 1 | `entry` | `form` |
| 2 | `sense` | `gloss` |
| 3 | `example` | `text` |

- The depth is counted from the root **across the parent path and the direct target together** (section "4.1"), and nothing else decides it: the target step of `d /mami/pig "a mami jefi"` is at depth 3 and is the abbreviated example, exactly as the last step of the parent path of `c /mami/pig/"a mami jefi" o("free", "…")` is. A target step that is not separated from the path — there is none, since a target is exactly one step — never changes the count, and neither does the shape of the earlier steps: `/e[f="mami"]/pig` is a legal path whose second step is the abbreviated sense, and `/mami/s[g="pig"]/"a mami jefi"` is legal too.
- A bare or quoted step at depth 4 or deeper raises `ABBREVIATED_STEP_NOT_ALLOWED_HERE`, a static error, so `d /mami/pig/"a mami jefi" foo` is rejected: its target step is at depth 4. There is no abbreviation below the example, because below it the component type is no longer determined by the depth: a sense may hold an example, a note, a field, an illustration and more.
- A bare token that is exactly the code or the name of a **singleton** component type admissible at that point is a singleton step and never an abbreviated step (section "2", rule 5). At depth 3 under a sense this is the only collision the language has, and quoting resolves it: `/mami/pig/c` is the category of that sense, `/mami/pig/"c"` the example whose text is `"c"`. A quoted token is always an abbreviated step.
- Abbreviated steps are available only where the depth from the root is written down, that is in a path opened by the root marker `/` whose first step is at depth 1, and in the target step that follows such a path. In a **relative** path inside an indented block (section "3.3"), in a bare target step inside one, and in a path that begins with a **label**, the depth from the root is not written down, so every step must name its component type; a bare or quoted step there raises the same error.

#### 6.1.1 The bare word

The abbreviated steps of the three preceding rules, and the value of a type key
`^t` (section "4.2.1"), are the only two places where a value may be written
without quotes. What may be written there is a *bare word*, defined positively:

```text
BARE-WORD ::= BARE-CHAR { BARE-CHAR }
BARE-CHAR ::= any character of Unicode general category L* (letter),
              M* (combining mark) or Nd (decimal digit),
            | '_' | '-' | '.'
```

Everything that is not a `BARE-CHAR` must be quoted. This includes, and is not
limited to, whitespace and the characters:

```text
/  \  "  '  $  (  )  [  ]  {  }  @  ,  :  =  #  ^  *  !  ~
```

The class is defined by what it admits, not by what it forbids, for two reasons:

- an implementation cannot accidentally omit a delimiter from a forbidden list and admit an unparsable step;
- the class is open to the scripts of the world's languages, which a `[A-Za-z0-9_-]` class would not be. A Tuwuli or German form is a bare word; a form containing a space, a slash or an apostrophe is not, and is written between quotes.

Excluding `@` from the class is what makes the language suffix of section "6.3"
unambiguous: in `/mami@tww`, the bare word ends at the `@`, and `@tww` is a
qualifier. A form that really contains an `@` must be quoted, and its qualifier
follows the closing quote: `/"a@b"@tww`.

Two further points, already stated elsewhere and recalled here because they bear
on the same lexical decision:

- a bare word is never a component name: `/e` is the entry whose form is `"e"`, and only `/e[…]`, `/e#n` and `/e(…)` designate the `entry` component type (section "2");
- a comment starts at an unquoted `#` preceded by whitespace, so a bare word never contains one (section "1.1").

### 6.2 Unnamed initializer arguments

The following initializers can drop the property name under the following conditions:

#### 6.2.1 `entry`

If an `entry` initializer has no property name and equal sign before the assigned string, then this string is `form` of the `entry`:

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

#### 6.2.2 `sense`

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

#### 6.2.3 `example`

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

#### 6.2.4 The other component types

The following table shows how many unnamed string arguments are allowed for the
initializers of the different component type, and to which properties they map.
It is exhaustive: a component type that does not appear in it, or an unnamed
argument beyond the number given for its type, raises
`UNNAMED_ARGUMENT_NOT_ALLOWED`.

| Component | Number of unnamed argument | Property mapping |
|--|--|--|
| Entry | 1 | form (section "6.2.1") |
| Sense | 1 | gloss (section "6.2.2") |
| Example | 1 | text (section "6.2.3") |
| Trait | 2 | type, value |
| Note | 2 | type, text |
| Translation | 2 | type, text |
| Field | 2 | type, text |
| Pronunciation | 1 | transcription |
| Illustration | 1 | url |
| Media | 1 | url |
| Etymology | 2 | type, form |
| Annotation | 2 | type, value |
| Relation | — | — |
| Variant | — | — |
| Reversal | — | — |
| Category | — | — — a singleton is never created (Part 1, section "3.1") |

The mapping is the required properties of the type, in a fixed order, which is
what makes it memorable. That order is **normative and is declared explicitly**,
per component type, in the `unnamedArguments` array of Appendix A; the table
above renders it. It is not derived from the order in which `properties` happens
to be written there: the metamodel is a JSON document, the order of the keys of a
JSON object is not significant, and no two parsers need agree on it. The two
orders do differ — Appendix A writes `etymology`'s properties as `form, gloss,
source, type`, while `c /… y("borrowing", "mami")` maps its two arguments to
`type, form`.

The three component types with no unnamed form are exactly the three whose
required properties include a `reference`: `relation`, `variant` and `reversal`
take a `type` and a `target`, and a target is a path, not a string (section "3"). Writing them out
avoids a line in which a quoted string would mean a component:

```LiftPatchShort
c /mami r(y="synonym", a = /memi)
```

This means that in the following example for example, the field initializer creates a field with type "editorial" and text "To be checked":

```
c /"mami"/"pig" f("editorial", "To be checked")
```

and that a pronunciation or an illustration is written with its single required
property alone:

```LiftPatchShort
c /mami p("mami")
c /mami/pig i("http://www.example.org/pig.png")
```

### 6.3 Language qualifiers on abbreviated values

In the two preceding sections, 6.1 and 6.2, new constructions were introduced where the property name is dropped.

For all this abbreviated property value notations, if an explicit language code must be given, it is suffixed to the value itself.

Consider the following notations:

- language code with quoted string for entry form and sense gloss:

```
/"mami"@tww
```

```
/"mami"@tww/"pig"@en
```

- language code with unquoted string for entry form and sense gloss:

```LiftPatchShort
d /mami@tww
```

```
/mami@tww/pig@en
```

- language code when creating an entry with abbreviated initalizer

```LiftPatchShort
c e("mami"@tww)
```

- creating an example, with qualified sense's `gloss` property (in the path) and qualified example `text` property:

```LiftPatchShort
c /"mami"/"Schwein"@de x("a mami jefi"@tww)
```

## 7. Embedded initializers

An embedded component creation is allowed *into* a component initializer for creating a child on the fly.

For instance, in the following example, the creation construct "o(t="I shot a pig", y="literal")" with a component letter (or component name), parentheses, and initalizer, is embedded in the example initializer. The translation created by the embedded initializer is created and added to its parent.

```
c /mami/pig x(t="a mami jefi", o(t="I shot a pig", y="literal"))
```

Embedded initializers necessarily translate into block syntax with embedded `create`:

```LiftPatchRef
sense[gloss="pig"] within entry[form="mami"] {
  create example(text="a mami jefi") {
    create translation(text="I shot a pig", type="literal")
  }
}
```

Recall that two examples cannot have the same text under the same sense (example `text` is its natural identity), so selecting by value is not ambiguous.

Embedded creation rules also include:

- Embedded child creation is atomic with the parent command;
- an embedded creation uses the semantics of `create`;
- an embedded initializer takes no `at` clause (section "1.2"). An embedded child is created at the last position, which is the documented default of Part 1, section "6.2". An `at` clause written at the end of the command line applies to the outermost created component, the one the command itself creates;
- a multitext property of an embedded initializer may take a multi-language literal, like any other initializer:

```LiftPatchShort
c /mami/pig x(t = { tww: "a mami jefi", tpi: "mi shutim pik" }) at beginning
```

## 8. The `p /path` idiom

A last and idiomatic construct is an `upsert` command *followed by a path made
only of abbreviated steps* (section "6.1") and *without the expected
constructor*:

```
p /"mami"/"pig"
```

Since, in this example, the `entry` `form` and the `sense` `gloss` have no whitespace or special character, it can be written:

```
p /mami/pig
```

It is equivalent to the construct mentionned in the Block section, where two upsert command are embedded:

```LiftPatchRef
upsert entry(form="mami", has-gloss="pig") {
  upsert sense(gloss="pig")
}
```

**The idiom applies to a path of any length**, not only to the two-step form.
Each step is upserted under the component the preceding step resolved or created,
and, since the abbreviated steps of section "6.1" go down to the example, the
useful depths are two and three:

```LiftPatchShort
p /mami/pig/"a mami jefi"
```

is

```LiftPatchRef
upsert entry(form="mami", has-gloss="pig") {
  upsert sense(gloss="pig") {
    upsert example(text="a mami jefi")
  }
}
```

— which is, in one line, the operation a lexicographer performs most often:
record a form, its meaning, and the sentence it was heard in, attaching each to
what already exists and creating only what does not. Two rules complete the
generalization:

- the `has-gloss` disambiguation of the entry comes from the **second** step, whenever there is one, exactly as in the two-step form; steps below the second add no disambiguation, since a sense and an example have a natural identity of their own;
- **every** step must be an abbreviated step. `p /e[f="mami"]/s[g="pig"]` is rejected with `CONSTRUCTOR_REQUIRED` (section "3"): a selector states a condition, and there is no rule for turning an arbitrary condition into the initializers of a component that has to be created. A path of one step, `p /mami`, remains rejected with `UPSERT_ENTRY_WITHOUT_DISAMBIGUATION` (section "3"), since it asks to upsert an entry with nothing to tell the homophones apart.

In other words:

- check if an entry with form "mami" -- and having a sense with gloss "pig" -- exist;
- if yes, do nothing;
- if no (i.e. if no "mami" entry exists, or if one or several entries exist but without the sense "pig"):
  - create a new entry "mami" and create a new sense "pig" on it.

The upsert in these construct will not raise 'UPSERT_ENTRY_WITHOUT_DISAMBIGUATION'.

It is very idiomatic and it is an exception to the general syntax; it is justified because it will provide a very short notation of a situation very frequent when noting lexicographic data.


It is different from the equivalent `create` command:

```
c /mami s("pig")
```

The two commands differ on every case, and the difference is the point of the
idiom:

| State of the dictionary | `c /mami s("pig")` | `p /mami/pig` |
|---|---|---|
| no entry "mami" | `NOT_FOUND` — the path *selects* and never creates | creates the entry and the sense |
| exactly one entry "mami", without the sense | creates the sense on it | creates a **new** entry "mami" carrying the sense |
| exactly one entry "mami", with the sense | `CANNOT_CREATE_DUPLICATE` | succeeds and changes nothing |
| several entries "mami", none with the sense | `AMBIGUOUS_REFERENCE` | creates a new entry "mami" carrying the sense |
| several entries "mami", exactly one with the sense | `AMBIGUOUS_REFERENCE` | succeeds and changes nothing |
| several entries "mami", several with the sense | `AMBIGUOUS_REFERENCE` | `AMBIGUOUS_REFERENCE` |

The row that matters is the second: `c` adds the sense to the entry that is
there, `p` treats "a mami that means pig" as the thing being upserted, and gives
the new meaning a new entry rather than attaching it to a homophone that means
something else. Which of the two is wanted is a lexicographic decision — is this
a new sense of an existing word, or a different word? — and the language makes
the writer state it rather than guessing.

## 9. A worked example

Using implicit field name and embedded initializers, consider the following
command, that uses many of the rules previously stated:

```LiftPatchShort
c e("mami", s("pig", x("A mami jefi", o("literal", "I shot a pig"))))
```

It should be translated into:

```LiftPatchRef
create entry(form="mami") {
  create sense(gloss="pig") {
    create example(text="A mami jefi") {
      create translation(type="literal", text="I shot a pig")
    }
  }
}
```

Reading the concise line from the inside out: `o("literal", "I shot a pig")` is
a `translation` initializer using the two unnamed arguments of section "6.2.4",
which map to `type` and `text` in that order; `x("A mami jefi", …)` is an
`example` initializer whose unnamed argument is its `text` (section "6.2.3");
`s("pig", …)` is a `sense` initializer whose unnamed argument is its `gloss`
(section "6.2.2"); and `e("mami", …)` is an `entry` initializer whose unnamed
argument is its `form` (section "6.2.1"). Each embedded initializer is created
under the component of the initializer that contains it, in the order in which
it is written, and the whole line is one transaction: if any of the four
creations fails, none of them is applied.

# Appendices

## Appendix A. The normative metamodel

This appendix is the normative source of truth for the LIFT metamodel used by
LiftPatch: component types, allowed parentage, properties, datatypes, language
kinds, required properties and natural identity property sets. The tables of
Part 1, sections "3", "4.1" and "5.2", render the same information for human
readers; where the two diverge, this appendix prevails.

It is given as a JSON document so that an implementation can load it directly
and so that conformance tests can be written against it.

**Version and identity.** The document carries its own version in
`liftpatchMetamodel`, independent of the language version of section "1.1": the
language may gain a construct without the metamodel changing, and the metamodel
may gain a component type without the language changing. An implementation MUST
report the metamodel version it validates against — plan mode carries it in the
plan document (section "12.4.1") — so that a script rejected as
`PROPERTY_DOES_NOT_EXIST_ON_COMPONENT_TYPE` can be told apart from a script
validated against an older metamodel.

**Extensibility.** Real LIFT projects declare component and property vocabulary
that this appendix does not list — ranges of `trait` types, project-specific
`field` types, additional component types in a future LIFT revision. An
implementation MAY therefore load an **extended metamodel**, a JSON document of
the same shape with additional entries, under three conditions:

1. It MUST NOT remove or redefine anything this appendix declares: an extension adds component types, adds properties to existing component types, and adds children to existing component types. Changing the datatype, the `required` flag or the `naturalIdentity` of anything declared here produces a different language, not an extension of this one.
2. The extended document MUST carry `"extends": "1.0"` and its own identifier in `"metamodelId"`, and a script MAY require it with a pragma attribute: `%liftpatch 1.0 metamodel="tww-project-2"`. If the required metamodel is not the one loaded, the script is rejected with `UNSUPPORTED_METAMODEL`.
3. Every rule of this specification is stated in terms of the metamodel, never in terms of the particular types listed below — with the single exception of `entry`, whose special status (no parent, empty natural identity, dictionary-ordered list, `hn` and `has-gloss`) is part of the language. An extension therefore needs no change to Parts 1 to 3. An added component type MUST declare a `componentKind`, and MUST satisfy the two invariants stated in the reading rules below; an extension may not change the `componentKind` of a type declared here.

```json
{
  "liftpatchMetamodel": "1.0",
  "languageKinds": ["object", "meta"],
  "datatypes": ["string", "integer", "reference", "url", "multitext"],
  "root": "dictionary",
  "components": {
    "entry": {
      "componentKind": "ordered",
      "children": ["sense", "etymology", "variant", "relation", "pronunciation",
                   "reversal", "trait", "annotation", "note", "field"],
      "naturalIdentity": [],
      "unnamedArguments": ["form"],
      "properties": {
        "form":     { "datatype": "multitext", "qualifier": "object", "required": true },
        "morpheme": { "datatype": "string",    "qualifier": null,     "required": false }
      }
    },
    "sense": {
      "componentKind": "ordered",
      "children": ["sense", "example", "relation", "illustration", "reversal",
                   "trait", "annotation", "note", "field", "category"],
      "naturalIdentity": ["gloss"],
      "unnamedArguments": ["gloss"],
      "properties": {
        "gloss":      { "datatype": "multitext", "qualifier": "meta", "required": true },
        "definition": { "datatype": "multitext", "qualifier": "meta", "required": false }
      }
    },
    "example": {
      "componentKind": "ordered",
      "children": ["translation", "trait", "annotation", "field"],
      "naturalIdentity": ["text"],
      "unnamedArguments": ["text"],
      "properties": {
        "text": { "datatype": "multitext", "qualifier": "object", "required": true }
      }
    },
    "etymology": {
      "componentKind": "ordered",
      "children": ["trait", "annotation", "field"],
      "naturalIdentity": ["type", "form"],
      "unnamedArguments": ["type", "form"],
      "properties": {
        "form":   { "datatype": "multitext", "qualifier": "object", "required": true },
        "gloss":  { "datatype": "multitext", "qualifier": "meta",   "required": false },
        "source": { "datatype": "multitext", "qualifier": "meta",   "required": false },
        "type":   { "datatype": "string",    "qualifier": null,     "required": true }
      }
    },
    "variant": {
      "componentKind": "ordered",
      "children": ["pronunciation", "relation", "trait", "annotation", "field"],
      "naturalIdentity": ["type", "target"],
      "unnamedArguments": [],
      "properties": {
        "type":   { "datatype": "string",    "qualifier": null, "required": true },
        "target": { "datatype": "reference", "qualifier": null, "required": true }
      }
    },
    "relation": {
      "componentKind": "ordered",
      "children": ["trait", "annotation", "field"],
      "naturalIdentity": ["type", "target"],
      "unnamedArguments": [],
      "properties": {
        "type":   { "datatype": "string",    "qualifier": null, "required": true },
        "target": { "datatype": "reference", "qualifier": null, "required": true }
      }
    },
    "reversal": {
      "componentKind": "ordered",
      "children": ["trait", "annotation", "field"],
      "naturalIdentity": ["type", "target"],
      "unnamedArguments": [],
      "properties": {
        "form":   { "datatype": "multitext", "qualifier": "object", "required": false },
        "type":   { "datatype": "string",    "qualifier": null,     "required": true },
        "target": { "datatype": "reference", "qualifier": null,     "required": true }
      }
    },
    "illustration": {
      "componentKind": "ordered",
      "children": ["annotation", "field"],
      "naturalIdentity": ["url"],
      "unnamedArguments": ["url"],
      "properties": {
        "url":   { "datatype": "url",       "qualifier": null,   "required": true },
        "label": { "datatype": "multitext", "qualifier": "meta", "required": false }
      }
    },
    "media": {
      "componentKind": "ordered",
      "children": ["annotation", "field"],
      "naturalIdentity": ["url"],
      "unnamedArguments": ["url"],
      "properties": {
        "url":   { "datatype": "url",       "qualifier": null,   "required": true },
        "label": { "datatype": "multitext", "qualifier": "meta", "required": false }
      }
    },
    "pronunciation": {
      "componentKind": "ordered",
      "children": ["media", "trait", "annotation", "field"],
      "naturalIdentity": ["transcription"],
      "unnamedArguments": ["transcription"],
      "properties": {
        "transcription": { "datatype": "multitext", "qualifier": "object", "required": true }
      }
    },
    "trait": {
      "componentKind": "typed",
      "children": ["annotation", "field"],
      "naturalIdentity": ["type"],
      "unnamedArguments": ["type", "value"],
      "properties": {
        "type":  { "datatype": "string", "qualifier": null, "required": true },
        "value": { "datatype": "string", "qualifier": null, "required": true }
      }
    },
    "annotation": {
      "componentKind": "ordered",
      "children": [],
      "naturalIdentity": ["type", "value"],
      "unnamedArguments": ["type", "value"],
      "properties": {
        "type":    { "datatype": "string",    "qualifier": null,   "required": true },
        "value":   { "datatype": "string",    "qualifier": null,   "required": true },
        "comment": { "datatype": "multitext", "qualifier": "meta", "required": false },
        "when":    { "datatype": "string",    "qualifier": null,   "required": false },
        "who":     { "datatype": "string",    "qualifier": null,   "required": false }
      }
    },
    "note": {
      "componentKind": "typed",
      "children": ["annotation"],
      "naturalIdentity": ["type"],
      "unnamedArguments": ["type", "text"],
      "properties": {
        "type": { "datatype": "string",    "qualifier": null,   "required": true },
        "text": { "datatype": "multitext", "qualifier": "meta", "required": true }
      }
    },
    "field": {
      "componentKind": "typed",
      "children": ["annotation"],
      "naturalIdentity": ["type"],
      "unnamedArguments": ["type", "text"],
      "properties": {
        "type": { "datatype": "string",    "qualifier": null,   "required": true },
        "text": { "datatype": "multitext", "qualifier": "meta", "required": true }
      }
    },
    "translation": {
      "componentKind": "typed",
      "children": [],
      "naturalIdentity": ["type"],
      "unnamedArguments": ["type", "text"],
      "properties": {
        "type": { "datatype": "string",    "qualifier": null,   "required": true },
        "text": { "datatype": "multitext", "qualifier": "meta", "required": true }
      }
    },
    "category": {
      "componentKind": "singleton",
      "children": ["trait"],
      "naturalIdentity": [],
      "unnamedArguments": [],
      "properties": {
        "value": { "datatype": "string", "qualifier": null, "required": false }
      }
    }
  },
  "pseudoProperties": {
    "id":        { "kind": "identifier",     "datatype": "string",  "scope": "entry and sense only" },
    "hn":        { "kind": "lookupKey",      "datatype": "integer", "scope": "entry only" },
    "ordinal":   { "kind": "position",       "datatype": "integer", "scope": "ordered component types except entry" },
    "typeKey":   { "kind": "key",            "datatype": "string",  "scope": "typed component types",
                   "expandsTo": "[type = V]" },
    "has":       { "kind": "childPredicate", "argument": "step",    "scope": "every component type" },
    "has-gloss": { "kind": "childPredicate", "argument": "string", "qualifier": "meta",
                   "scope": "entry only", "expandsTo": "has sense[gloss@L = V]" }
  }
}
```

Reading rules:

- `children` is the parentage relation of section "3": a component may be created or moved only under a component type that lists it in its `children`. Only `entry` may be a child of the root `dictionary`. The parent relation is the inverse of this one and is not stated separately, so that the two can never diverge.
- `componentKind` is the component kind of section "3.1", one of `"ordered"`, `"typed"` and `"singleton"`. It is the single declaration from which every rule about position, ordinals, type keys, the `at` clause and `move` is derived, and an implementation dispatches on it rather than on the component name. Two invariants tie it to the rest of the document and MUST hold of any metamodel, this one and any extension of it: a `"typed"` component type has `"naturalIdentity": ["type"]` and no other identity property, since the map key *is* the identity; and a `"singleton"` component type has an empty `naturalIdentity` and no `required` property, since it is never created and there is never a second one to distinguish it from.
- `qualifier` gives the language kind of a multitext (`"object"` or `"meta"`) and is `null` for every scalar property. A property with a non-null `qualifier` is exactly a property that accepts an `@L` key and the `@*` wildcard.
- `required` is *required at creation*: for a multitext, at least one qualified value.
- `naturalIdentity` is the natural identity property set `I(T)` of section "5.2". An empty set — `entry` — means that the uniqueness invariant is vacuous for that type and that `create` never rejects it as a duplicate. When a multitext belongs to the set, values are compared language by language, and unset qualified values never take part in the comparison.
- `unnamedArguments` is the ordered list of properties that the unnamed initializer arguments of LiftPatchShort map to, position by position (Part 3, section "6.2.4"). It is necessarily empty on a singleton component type, which has no initializer list at all because it is never created. It is declared explicitly, and is **not** derived from the order in which `properties` happens to be written: the order of the keys of a JSON object is not significant, so a derived rule would make the meaning of `c /mami f("editorial", "To be checked")` depend on a parser's hash table. An empty list means the component type admits no unnamed argument, and any unnamed argument given to it raises `UNNAMED_ARGUMENT_NOT_ALLOWED`; so does an argument beyond the length of the list. Every property listed here is a required property of its type.
- `pseudoProperties` do not belong to the data: they are selection devices, and their availability per command is given by the applicability tables of section "5.4". `kind` says what sort of device each one is — `identifier` (a persistent id), `lookupKey` (a dictionary-assigned disambiguator), `position` (an ordinal), `key` (a type key, section "5.3.4"), `childPredicate` (a condition on the children of the component) — and is what an implementation dispatches on, rather than on the name. The singleton step of strategy S7 is not listed among the pseudo-properties because it is not one: it is the *absence* of a selector, and it is declared by the `componentKind` of the component type instead. A `childPredicate` is not a datatype-bearing property: `argument` gives what it is written with, a step for `has` and a string for `has-gloss`, and `expandsTo` gives the equivalent general form of the shorthand.
- An extended metamodel (see "Extensibility" above) may add an `unnamedArguments` list to a component type it introduces, and may not change the one of a component type declared here.

## Appendix B. Error codes

Every error the language defines is listed here with its **kind**, as defined in
Part 2, section "12.3":

- **static** — the error depends only on the text of the script and on the metamodel of Appendix A. An implementation MUST report every static error of a script before applying any command, and a script containing one changes nothing;
- **dynamic** — the error depends on the state of the dictionary, and is raised while the command is being resolved or applied. A dynamic error aborts the script and rolls back every change already applied (section "12.2").

Plan mode (section "12.4") reports both kinds without changing anything. It also
reports **warnings**, which are not errors and do not stop a script; they are
listed in B.4.

### B.1 Static errors

| Code | Raised when | Defined in |
|---|---|---|
| `UNSUPPORTED_LANGUAGE_VERSION` | the version pragma declares a version the implementation does not support | 1.1 |
| `UNKNOWN_PRAGMA_ATTRIBUTE` | the version pragma carries an attribute other than `sigil` and `metamodel` | 1.1 |
| `UNSUPPORTED_METAMODEL` | the pragma requires a metamodel that the implementation has not loaded | 1.1, Appendix A |
| `PROPERTY_DOES_NOT_EXIST_ON_COMPONENT_TYPE` | a property is used on a component type on which Appendix A does not define it | 4.1, 7.1 |
| `ILLEGAL_PARENT` | two component types are stated to be parent and child where Appendix A does not relate them: a component created or moved under such a parent, a `create entry(...)` in a block body, or two adjacent steps of a chain or a path — which is what "skipping a level" amounts to, as in `on example[...] of entry[...]` or `d /e[f="mami"] x[t="…"]` | 3, 7.3, 8.5, 11 |
| `LANG_KEY_NOT_SUPPORTED_ON_SCALAR` | an `@L` or `@*` qualifier, or a multi-language literal, is written on a scalar property | 4.4, 5.5.1 |
| `MISSING_LANGUAGE_QUALIFIER` | `clear` is written on a multitext with neither a language code nor `@*` | 9.3.1 |
| `WILDCARD_NOT_ALLOWED` | `@*` is used where it is forbidden: in a `create`, `upsert`, `set` or `update` assignment | 4.4.1, 9.1, 9.2 |
| `DUPLICATE_PROPERTY` | the same qualified property is initialized or assigned twice in one command — in an initializer list, in an assignment list, or inside one multi-language literal | 6.1, 5.5.1, 9.1.1 |
| `QUALIFIER_ON_MULTITEXT_LITERAL` | a multi-language literal is assigned to a qualified property name, as in `form@tww = { … }` | 5.5.1 |
| `UNNAMED_ARGUMENT_NOT_ALLOWED` | a concise initializer gives an unnamed argument to a component type that has none, or more of them than its type allows | Part 3, 6.2.4 |
| `DUPLICATE_SELECTOR` | a selector states the same constraint twice, gives two qualified values of one identity multitext, or combines conflicting selection strategies | 5.1, 5.2 |
| `INCOMPLETE_SELECTOR` | a selector required to select exactly one component uses no valid selection strategy | 5.1.3 |
| `MISSING_REQUIRED_PROPERTY` | a `create` or `upsert` initializer list omits a property that Appendix A declares required | 6.1 |
| `MISSING_IDENTITY_PROPERTY` | an `upsert` initializer list does not cover the whole natural identity property set of the component type | 8.2 |
| `PREDICATE_NOT_ALLOWED_IN_UNIQUE_SELECTOR` | a unique selector carries a predicate that is not part of its selection strategy: either a filtering-only operator (`!=`, `~`, `~i`, `exists()`, `absent()`), or an otherwise legal predicate added to a complete strategy, as in `sense[gloss@en = "pig", definition@en = "…"]`. The `has` refinement of strategies S3 and S4 and the `hn` refinement of S4 are part of the strategy and are not additions | 5.1.2, 5.1.3, 5.4.1 |
| `PSEUDO_PROPERTY_NOT_ALLOWED_IN_FILTER` | `id`, `hn`, `has-gloss` or an ordinal is used in a filtering selector — the parent side of `within` / `/`, a step inside `has`, or a step marked `each` / `all` / `*` | 5.1.4, 5.3 |
| `COMMAND_NOT_ALLOWING_HN` | `hn` is used with a command that does not allow it, in particular in an `upsert` initializer list | 5.3.2.1 |
| `COMMAND_NOT_ALLOWING_HAS` | `has` or `has-gloss` is used with a command that does not allow it, `create` in particular | 5.3.2.2 |
| `HN_CANNOT_BE_USED_ALONE` | `hn` is used in a selector that does not also give `form` | 5.3.2.1 |
| `HAS_GLOSS_CANNOT_BE_USED_ALONE` | `has-gloss` is used in a selector that does not also give `form` | 5.3.2.2 |
| `ILLEGAL_HN` | the value given for `hn` is not an integer, or is lower than 1 | 5.3.2.1 |
| `ILLEGAL_ORDINAL` | an ordinal, or the `n` of an `at index n` clause, is lower than 1 | 5.3.3, 6.2 |
| `ID_NOT_ALLOWED_ON_UPSERT` | `id` is used in an `upsert` initializer list | 8.2 |
| `UPSERT_ENTRY_WITHOUT_DISAMBIGUATION` | `upsert entry(...)` gives a `form` with no `has` or `has-gloss` predicate | 8.2 |
| `INCOMPLETE_ANCESTOR_CHAIN` | a chain or a path stops before reaching the root: `on sense[...]` with no further ancestor clause, `/s[g="pig"]`. A chain that skips a level is `ILLEGAL_PARENT` instead | 7.3, Part 3, 4 |
| `MISSING_PARENT_CLAUSE` | a command gives no parent where one is required: a component command whose target is not an `entry` written with no `under` clause, or a property command written with no `on` clause, outside a block; in LiftPatchShort, the same commands written with no path outside an indented block, which includes a `d` or an `e` whose target step is not an `entry` or a label and which carries no parent path, as in an indented `d x#1` or `d s[g="pig"] x#1` that no enclosing block anchors | 7.2, 11, Part 3, 3, 3.1 |
| `CONSTRUCTOR_REQUIRED` | a `c` or `p` command is written with a path and no constructor, and a step it would have to create is not an abbreviated step: `c /e[f="mami"]/s[g="pig"]` | Part 3, 3, 8 |
| `SINGLETON_CANNOT_BE_CREATED_OR_DELETED` | a `create`, `upsert`, `delete` or `move` names a **singleton** component type, which always exists on its host and is never brought into being or removed by a command: `create category(...)`, `delete category under sense[...]` | 3.1, 6.1, 8.1, 8.4, 8.5 |
| `SINGLETON_TAKES_NO_SELECTOR` | a **singleton** step is given a selector, an ordinal or a type key where it is a unique selector: `category[value = "Noun"]` in an `on` or `under` clause. In a *filtering* selector a predicate list is legal there and this code is not raised (5.1.4) | 3.1, 5.1.3 |
| `COMPONENT_NOT_ORDERED` | an ordinal, an `at POSITION` clause or a `move` names a component type whose kind is not **ordered**: `note#1`, `create note(...) at beginning`, `move field[type = "x"]`. `entry` raises it for the ordinal too, its list being ordered by the dictionary | 2, 3.1, 5.3.3, 6.2, 8.5 |
| `COMPONENT_NOT_TYPED` | a type key `^t` names a component type whose kind is not **typed**: `example^free`, `category^noun` | 3.1, 5.3.4 |
| `TARGET_MUST_BE_A_SINGLE_STEP` | the direct target of a `d`, an `e` or the source of an `m` is written as two or more steps, as in `d /mami/pig`, instead of a parent path followed by one target step | Part 3, 3.1, 4.1 |
| `ABBREVIATED_STEP_NOT_ALLOWED_HERE` | a bare word or a quoted string is written as a step at a depth from the root for which no abbreviation is defined: at depth 4 or deeper, counted across the parent path and the direct target together, or at any depth in a relative path, in a bare target step inside a block, or in a path beginning with a label, where the depth from the root is not written down | Part 3, 6.1 |
| `OPERATOR_NOT_APPLICABLE_TO_DATATYPE` | an operator is used on a datatype that does not admit it, such as `~` or `~i` on a `reference` or an `integer` property | 5.1.2 |
| `INVALID_TARGET` | the component designated as the value of a `reference` property is neither an `entry` nor a `sense`. Static, because the component type is written in the chain or is known from the label's binding | 10 |
| `MULTIPLICITY_NOT_ALLOWED` | `each` / `all` (or `*` in LiftPatchShort) is used on a parent step, on `create`, `upsert`, `ensure` or `move`, or on a command used as the anchor of an indented block. A **singleton** target step is the one exception to "parent step": there the keyword is legal and distributes over the host (section "5.6") | 5.6, Part 3, 3.3 |
| `DUPLICATE_LABEL` | a label name visible in the current scope is bound again | 6.3 |
| `UNKNOWN_LABEL` | a `$name` is used that no visible command has bound | 6.3 |
| `REFERENCE_VALUE_MUST_BE_A_CHAIN` | a bare string is assigned to, or compared with, a property of datatype `reference`, in a command or in a selector | 10 |
| `MOVE_NOT_ALLOWED_IN_BLOCK` | a `move` command appears in a block body | 11 |
| `DELETE_CANNOT_BE_AN_ANCHOR` | a LiftPatchShort command line is indented under a `d`, whose component no longer exists when the indented commands would run | Part 3, 3.3 |
| `CANNOT_UPSERT_ENSURE_OR_UPDATE_IN_CREATION_BLOCK` | an `upsert`, `ensure` or `update` appears, directly or indirectly, in the scope of a `create` block | 11 |
| `CANNOT_CLEAR_IDENTITY_PROPERTY` | `clear` targets a scalar property belonging to the natural identity property set | 9.3.1 |
| `CANNOT_CLEAR_REQUIRED_PROPERTY` | `clear` targets a scalar property that Appendix A declares required | 9.3.1 |
| `SYNTAX_ERROR` | the script does not parse, or uses a construct the grammar forbids in that position (see the list below) | Appendix C |

Syntax errors are static too, and are reported with their position. They include
in particular:

- in both syntaxes: an `ensure` with an initializer list instead of a selector; an `at` clause on `ensure` or on a command creating an `entry`; an `at before` / `at after` step whose component type is not the type being placed (section "6.2.1"); a step combining an ordinal or a type key with a predicate list; an ordinal or a type key written on a constructor, as in `create note^general(...)`; an unterminated string, a raw line break inside a string, or a backslash followed by anything other than `"` or `\` in a double-quoted string (section "1.2");
- in LiftPatchRef: `move entry[...]`; a `move` without an `at` clause;
- in LiftPatchShort: a command spread over several physical lines; `m /mami`, which would move an entry; a tab character in the indentation of a command line, and an indentation that matches no open block level (Part 3, section "3.3").

### B.2 Dynamic errors

| Code | Raised when | Defined in |
|---|---|---|
| `NOT_FOUND` | a selector, a path, a value chain or an `ensure` assertion matches no component | 5.1, 8.3 |
| `AMBIGUOUS_REFERENCE` | a selector required to select exactly one component matches several | 5.1, 7.3 |
| `CANNOT_CREATE_DUPLICATE` | a `create`, `upsert`, `set`, `update` or `move` would give two same-type siblings the same values for the natural identity property set | 5.2, 8.1, 8.5 |
| `UNSET_PROPERTY` | `update` or `clear` targets a property that has no value at all | 9.2, 9.3.1 |
| `UNSET_QUALIFIED_PROPERTY` | `update` or `clear` targets a qualified value `p@L` that is not set, even though another language of `p` is | 9.2, 9.3.1 |
| `CANNOT_CLEAR_REQUIRED_MULTITEXT` | `clear` would leave a required or identity multitext with no value at all | 9.3.1 |
| `INDEX_OUT_OF_BOUNDS` | an ordinal or an `at index n` is greater than the upper bound of the same-type sibling list. A value lower than 1 is the static `ILLEGAL_ORDINAL` instead | 5.3.3, 6.2, 8.5 |
| `MOVING_TO_CURRENT_POSITION` | a `move` resolves to the position the component already occupies | 8.5 |
| `SELF_ANCESTOR` | a `move` would make a component its own ancestor | 8.5 |
| `NO_SUCH_OBJECT_LANGUAGE` | an object language code is not in the dictionary's object language list | 2.1, 4.2 |
| `NO_SUCH_META_LANGUAGE` | a meta language code is not in the dictionary's meta language list | 2.1, 4.2 |
| `LANGUAGE_ALREADY_EXISTS` | `language-create` names a language the dictionary already has | 2.1.3 |

### B.3 Codes that must not be raised

A conforming implementation MUST NOT raise any of the following codes. They are
listed because they are named by documents and implementations that predate this
version, and because each of them denotes a condition that a code of B.1 or B.2
already denotes:

| Code | Raise instead |
|---|---|
| `EMPTY_MULTITEXT` | `UNSET_PROPERTY` |
| `QUALIFIED_PROPERTY_NOT_FOUND` | `UNSET_QUALIFIED_PROPERTY` |
| `INCOMPATIBLE_DESTINATION` | `ILLEGAL_PARENT` |
| `DUPLICATE_COMPONENT` | `CANNOT_CREATE_DUPLICATE` |
| `ENTRY_CANNOT_MOVE` | `SYNTAX_ERROR` |
| `NEGATIVE_HN` | `ILLEGAL_HN` |
| `ILLEGAL_USE_OF_HAS_GLOSS` | `COMMAND_NOT_ALLOWING_HAS` |
| `COMMAND_NOT_ALLOWING_HAS_GLOSS` | `COMMAND_NOT_ALLOWING_HAS`, which governs the general `has` predicate as well |
| `ILLEGAL_LANGUAGE` | `NO_SUCH_OBJECT_LANGUAGE` / `NO_SUCH_META_LANGUAGE` |
| `OPERATOR_REQUIRE_A_MULTITEXT` | `OPERATOR_NOT_APPLICABLE_TO_DATATYPE` |
| `HN_NOT_EXISTING` | `NOT_FOUND` (section "5.3.2.1") |
| `NO_SUCH_TARGET` | `NOT_FOUND` (section "10") |

### B.4 Warnings

A warning reports a situation that is legal, changes nothing, and is more often a
mistake than an intention. A warning never stops a script, never changes the
`status` of a plan document, and never appears in `staticErrors`; plan mode
carries warnings in its `warnings` array (section "12.4.1").

| Code | Reported when | Defined in |
|---|---|---|
| `NO_COMMAND_RECOGNIZED` | not one line of the document was recognized as a command. Usually a LiftPatchRef script read as a LiftPatchShort document, which would otherwise apply nothing, silently | 1.1, 12.4 |
| `COMMAND_APPLIES_TO_NO_COMPONENT` | a command marked `each`, `all` or `*` whose target step matched no component. Legal by section "5.6", and worth saying out loud | 5.6, 12.4 |

## Appendix C. Grammars

The two grammars below are given in EBNF, with `{ x }` for zero or more, `[ x ]`
for optional, `|` for alternative, and quoted literals for terminals. They
define what a parser accepts. They deliberately do **not** encode the
constraints that depend on the metamodel or on the position of a selector in a
command — which predicates are allowed in a unique selector, which pseudo-
properties a command accepts, which properties a component type has. Those are
stated normatively in Parts 1 and 2 and in Appendices A and B, and a parse tree
accepted by these grammars may still be rejected as a static error.

Common lexical terminals, shared by both syntaxes:

```ebnf
string        ::= '"' { any-char-except-unescaped-double-quote } '"'
                | "'" { any-char-except-unescaped-single-quote } "'"
integer       ::= digit { digit }
lang          ::= letter { letter | digit | '-' }
name          ::= letter { letter | digit | '-' | '_' }
comment       ::= '#' { any-char } end-of-line
```

The escaping rules of section "1.2" apply and are exhaustive: `''` inside a
single-quoted string; `\"` and `\\`, and nothing else, inside a double-quoted
string; no raw line break inside either. In LiftPatchRef a comment starts at any
unquoted `#`; in LiftPatchShort, at a `#` that begins a line or is preceded by
whitespace (Part 3, section "1.1").

### C.1 LiftPatchRef

```ebnf
script            ::= { comment } [ pragma ] { item }
item              ::= command | block | directive | comment
pragma            ::= '%liftpatch' version { name '=' string }
version           ::= integer '.' integer

directive         ::= ( 'language-default' | 'language-create' ) lang-kind '=' string
lang-kind         ::= 'object' | 'meta'

block             ::= block-header '{' { block-item } '}'
block-item        ::= block-command | block | directive | comment
block-command     ::= create-cmd | upsert-cmd | ensure-cmd | delete-cmd
                    | set-cmd | update-cmd | clear-cmd
block-header      ::= chain [ label-binding ] | create-cmd | upsert-cmd
                    | ensure-cmd | with-header
with-header       ::= 'with' binding { ',' binding }
binding           ::= lang-kind '=' string

command           ::= create-cmd | upsert-cmd | ensure-cmd | delete-cmd | move-cmd
                    | set-cmd | update-cmd | clear-cmd

create-cmd        ::= 'create' constructor [ 'under' chain ] [ at-clause ] [ label-binding ]
upsert-cmd        ::= 'upsert' constructor [ 'under' chain ] [ at-clause ] [ label-binding ]
ensure-cmd        ::= 'ensure' step [ 'under' chain ] [ label-binding ]
delete-cmd        ::= 'delete' [ multiplicity ] step [ 'under' chain ]
move-cmd          ::= 'move' ordered-step 'under' chain [ 'under' chain ] at-clause

set-cmd           ::= 'set' assignment { ',' assignment } [ 'on' [ multiplicity ] chain ]
update-cmd        ::= 'update' assignment { ',' assignment } [ 'on' [ multiplicity ] chain ]
clear-cmd         ::= 'clear' property-ref { ',' property-ref } [ 'on' [ multiplicity ] chain ]
assignment        ::= property-ref '=' value

constructor       ::= component-type '(' [ initializer { ',' initializer } ] ')'
initializer       ::= property-ref '=' value
multiplicity      ::= 'each' | 'all'
at-clause         ::= 'at' position
position          ::= 'beginning' | 'end' | 'index' integer
                    | 'before' step | 'after' step
label-binding     ::= 'as' label-ref
label-ref         ::= '$' name

chain             ::= step { axis-keyword step }
axis-keyword      ::= 'of' | 'within'
step              ::= ordered-step | typed-step | singleton-step | label-ref
ordered-step      ::= component-type ( '[' selector ']' | ordinal )
typed-step        ::= component-type ( '[' selector ']' | type-key )
singleton-step    ::= component-type [ '[' selector ']' ]
ordinal           ::= '#' integer
type-key          ::= '^' ( name | string )

selector          ::= predicate { ',' predicate }
predicate         ::= property-ref comparison value
                    | 'exists' '(' property-ref ')'
                    | 'absent' '(' property-ref ')'
                    | 'has' step
                    | 'id' '=' string
                    | 'hn' '=' integer
                    | 'has-gloss' [ '@' lang ] '=' string
                    | 'index' '=' integer
comparison        ::= '=' | '!=' | '~' | '~i'

property-ref      ::= property-name [ '@' ( lang | '*' ) ]
value             ::= string | integer | multitext-literal | chain | label-ref
multitext-literal ::= '{' lang ':' string { ',' lang ':' string } '}'

component-type    ::= 'entry' | 'sense' | 'example' | 'etymology' | 'variant'
                    | 'relation' | 'illustration' | 'media' | 'pronunciation'
                    | 'reversal' | 'trait' | 'annotation' | 'note' | 'field'
                    | 'translation' | 'category'
property-name     ::= 'form' | 'morpheme' | 'gloss' | 'definition'
                    | 'text' | 'source' | 'target' | 'url' | 'label'
                    | 'transcription' | 'type' | 'value' | 'comment'
                    | 'when' | 'who'
```

Notes on the productions that carry a rule of their own:

- `index '=' integer` inside a selector is the deprecated alias of the ordinal `#n` (section "5.3.3"); it is kept for compatibility and SHOULD NOT be used in new scripts.
- The three step productions are **not** alternatives a parser chooses between: which one applies is fixed by the `componentKind` the metamodel declares for the `component-type` written (section "3.1"), and a parser that does not load the metamodel parses the union of the three and defers the decision to static validation. Writing an `ordinal` on a type that is not ordered is `COMPONENT_NOT_ORDERED`, a `type-key` on a type that is not typed is `COMPONENT_NOT_TYPED`, and a selector on a singleton *in a unique-selector position* is `SINGLETON_TAKES_NO_SELECTOR`.
- `singleton-step` is the one step that may be written as a bare component type: a singleton has exactly one instance per host, so the type names the component (strategy S7, section "5.1.3"). Its optional selector is admitted by the grammar because a singleton step in a **filtering** position may carry an ordinary predicate list (section "5.1.4"); in every other position that selector is a static error.
- Every other `step` carries either a selector between square brackets, an ordinal or a type key, or else is a label: a non-singleton component type written alone selects nothing and is not a step. A component type followed by parentheses is a `constructor`, not a step, and it creates instead of selecting (section "6").
- `ordinal` and `type-key` are selection devices and never initializers, so neither may appear on a `constructor`: `create note^general(...)` is a syntax error (sections "5.3.3" and "5.3.4").
- `move-cmd` takes an `ordered-step`, because only an ordered component has a position to change (section "8.5"). The grammar cannot tell the kinds apart for the reason given above, so an implementation parses `step` there and reports `COMPONENT_NOT_ORDERED` or `SINGLETON_CANNOT_BE_CREATED_OR_DELETED` as a static error.
- The `on` keyword closes a value chain (section "10"), which is why `on` cannot be a component name.
- `move` takes one `under` chain for the source parent and an optional second one for the destination parent; `at-clause` is mandatory. `move` is absent from `block-command`: it cannot appear in a block body (`MOVE_NOT_ALLOWED_IN_BLOCK`, section "11").
- The optional `'under'` / `'on'` clauses are omitted only inside a block, whose header supplies the parent (section "11"). The grammar makes them optional everywhere because it does not know whether it is inside a block; a command other than one targeting an `entry` that omits its parent clause at top level is rejected with `MISSING_PARENT_CLAUSE`, a static error.
- The grammar admits a `chain` that stops before reaching an `entry`, and one whose steps skip a level of the hierarchy, because parentage is a metamodel question; both are rejected with `INCOMPLETE_ANCESTOR_CHAIN` (section "7.3").
- The grammar admits a `label-ref` as any `step` of a `chain`, including after `within`. A label denotes exactly one component, so an existential join below one reduces to the strict one and the two spellings mean the same thing (section "6.3").
- `at-clause` is forbidden when the constructor creates an `entry` (section "6.2"). The grammar cannot express that restriction, since `component-type` is one production; it is a static error.
- On a property of datatype `reference`, the `value` of an `assignment`, of an `initializer` and of a `predicate` alike must be a `chain` or a `label-ref`, never a `string` (section "10"): `REFERENCE_VALUE_MUST_BE_A_CHAIN`. The grammar admits `string` there because it does not know datatypes.

### C.2 LiftPatchShort

```ebnf
document          ::= { line }
line              ::= command-line | prose-line | pragma-line
command-line      ::= [ spaces ] [ sigil ] indent ( command | directive )
                      [ comment ] end-of-line
prose-line        ::= [ '\' ] { any-char } end-of-line
pragma-line       ::= [ spaces ] '%liftpatch' version { name '=' string } end-of-line
sigil             ::= the one character declared by the pragma attribute `sigil`
spaces            ::= { ' ' }
indent            ::= { ' ' }

command           ::= create-cmd | upsert-cmd | ensure-cmd | delete-cmd | move-cmd
                    | set-cmd | update-cmd | clear-cmd

create-cmd        ::= 'c' [ '*' ] ( [ path ] constructor [ at-clause ] [ label-binding ] | path )
upsert-cmd        ::= 'p' [ '*' ] ( [ path ] constructor [ at-clause ] [ label-binding ] | path )
ensure-cmd        ::= 'e' [ '*' ] target-spec [ label-binding ]
delete-cmd        ::= 'd' [ '*' ] target-spec
move-cmd          ::= 'm' [ '*' ] ordered-target-spec [ 'to' path ] at-clause
target-spec       ::= [ path spaces ] [ '/' ] path-step
ordered-target-spec ::= [ path spaces ] ordered-step
set-cmd           ::= 's' [ '*' ] [ path ] '(' assignment { ',' assignment } ')'
update-cmd        ::= 'u' [ '*' ] [ path ] '(' assignment { ',' assignment } ')'
clear-cmd         ::= 'l' [ '*' ] [ path ] '(' property-ref { ',' property-ref } ')'

assignment        ::= property-ref '=' short-value
constructor       ::= component-code '(' [ short-init { ',' short-init } ] ')'
short-init        ::= property-ref '=' short-value
                    | short-value
                    | constructor
short-value       ::= string [ '@' lang ] | integer | multitext-literal
                    | path | label-ref

path              ::= absolute-path | relative-path
absolute-path     ::= '/' path-step { axis path-step }
                    | label-ref { axis path-step }
relative-path     ::= path-step { axis path-step }
axis              ::= '/' | '!'
path-step         ::= ordered-step | typed-step | singleton-step
                    | abbreviated-step
                    | label-ref
ordered-step      ::= component-code ( '[' selector ']' | ordinal | '[' integer ']' )
typed-step        ::= component-code ( '[' selector ']' | type-key )
singleton-step    ::= component-code [ '[' selector ']' ]
abbreviated-step  ::= ( bare-word | string ) [ '@' lang ]
bare-word         ::= bare-char { bare-char }
bare-char         ::= unicode-letter | unicode-mark | unicode-digit
                    | '_' | '-' | '.'
ordinal           ::= '#' integer
type-key          ::= '^' ( bare-word | string )

at-clause         ::= 'at' position
position          ::= 'beginning' | 'end' | 'index' integer
                    | ( 'before' | 'after' ) ( path-step | ordinal )
label-binding     ::= 'as' label-ref
label-ref         ::= '$' name

property-ref      ::= property-code [ '@' ( lang | '*' ) ]
component-code    ::= 'e' | 's' | 'x' | 'y' | 'v' | 'r' | 'i' | 'm' | 'p' | 'l'
                    | 't' | 'a' | 'n' | 'f' | 'o' | 'c' | component-type
property-code     ::= 'f' | 'm' | 'd' | 'g' | 't' | 's' | 'a' | 'u' | 'l'
                    | 'r' | 'y' | 'v' | 'o' | 'w' | 'h' | property-name
```

`selector`, `multitext-literal`, `component-type` and `property-name` are those
of C.1, `hn` included. `component-code` and `property-code` accept the one-letter
code and the full reference name alike (Part 3, section "2").

Four productions are ambiguous on their own and are disambiguated by the rules of
Part 3, sections "1.1", "2" and "4.1", which a parser MUST apply:

- `target-spec`: the `[ path spaces ]` and the final `path-step` are separated by whitespace, and the **last top-level whitespace-separated path token** of the command, before any `to`, `at` or `as` clause, is the `path-step`; everything before it is the `path` (Part 3, section "4.1"). Top-level means outside every bracket, parenthesis, brace and quoted string, so whitespace inside a selector never splits a token. The optional `'/'` before that `path-step` is the root marker, and may be written only when no `path` precedes it.
- `command-line` versus `prose-line`: a line is a command only if it satisfies the recognition rule of section "1.1" — command letter, optional `*`, whitespace, then a token beginning with `/`, with `$`, with a component code immediately followed by `(`, or — on an indented line only — with `(` or with a component code immediately followed by `[`, `#` or `^`. When a sigil is declared, a line is a command **if and only if** it begins, after optional whitespace, with that sigil; the `[ sigil ]` of the `command-line` production is optional only because one production covers both modes. A line that matches the rule but does not parse is an error, never prose.
- `component-code` versus `abbreviated-step` and `property-code` versus a value: one character of look-ahead decides. A letter followed by `[`, `#`, `^` or `(` is a component code; a letter followed by `=`, `@`, `,` or `)` is a property code; anything else in a path step is a bare word, that is an abbreviated step (Part 3, section "6.1").
- `singleton-step` versus `abbreviated-step`: a bare token followed by none of those characters is a `singleton-step` when it is exactly the code or the name of a **singleton** component type that the metamodel admits as a child at that point of the path, and an `abbreviated-step` otherwise; a **quoted** token is always an `abbreviated-step` (Part 3, section "2", rule 5). `/mami/pig/c` is therefore the category of that sense and `/mami/pig/"c"` the example whose text is `"c"`.

Eleven further constraints are not expressed by the grammar and are static errors:

- `indent` is the whitespace between the sigil — or the start of the line, when no sigil is declared — and the command letter. It is significant (Part 3, section "3.3"), and it MUST be made of spaces: a tab there is a `SYNTAX_ERROR`, as is an indentation matching no open block level.
- `[ path ]` is omitted, and `relative-path` is used, only on an **indented** line, whose enclosing block supplies the parent. At top level both raise `MISSING_PARENT_CLAUSE`.
- in a `target-spec`, `[ path spaces ]` is omitted only when the target is an `entry`, a `label-ref`, or a child of the enclosing indented block; otherwise `MISSING_PARENT_CLAUSE`.
- the direct target of a `delete-cmd`, an `ensure-cmd` or a `move-cmd` is exactly one step: a target written as a chain of steps raises `TARGET_MUST_BE_A_SINGLE_STEP`.
- `[ '*' ]` is admitted on all eight commands so that a marker on `c`, `p`, `e` or `m` can be reported as `MULTIPLICITY_NOT_ALLOWED` rather than as a parse failure, and so that `*` on the anchor of an indented block can be reported the same way.
- the path-only alternative of `create-cmd` and `upsert-cmd` requires every step it creates to be an `abbreviated-step`; otherwise `CONSTRUCTOR_REQUIRED`.
- an `abbreviated-step` is admitted by the grammar anywhere, and is legal only at depth 1, 2 or 3 below the root, counted across the `path` and the target `path-step` of a `target-spec` together, and only when the path is an `absolute-path` that does not begin with a label; elsewhere `ABBREVIATED_STEP_NOT_ALLOWED_HERE`.
- the three step productions are not alternatives a parser chooses between: which applies is fixed by the `componentKind` the metamodel declares for the `component-code` written (Part 1, section "3.1"). An `ordinal` on a type that is not ordered raises `COMPONENT_NOT_ORDERED`, a `type-key` on a type that is not typed raises `COMPONENT_NOT_TYPED`, and a `singleton-step` given a selector anywhere but in a filtering position raises `SINGLETON_TAKES_NO_SELECTOR`.
- the source of a `move-cmd` is an `ordered-step`; a `move` naming a typed component raises `COMPONENT_NOT_ORDERED` and one naming a singleton raises `SINGLETON_CANNOT_BE_CREATED_OR_DELETED`. A `c`, a `p` or a `d` naming a singleton raises the latter code too.
- `ordinal` and `type-key` never appear on a `constructor`: `c /mami n^general(t@en = "…")` is a `SYNTAX_ERROR`, and the `type` of a typed component is written as an ordinary initializer.
- at most one `pragma-line` may appear in a document, and it must precede the first `command-line`; a pragma declaring a `sigil` must be the first non-blank line (section "1.1").

## Appendix D. The conformance corpus

A specification of this size cannot be checked by reading. This appendix defines
a **conformance corpus**: a machine-readable set of cases, each giving a
dictionary, a script, and the expected outcome. An implementation claims
conformance to LiftPatch 1.0 by passing every case of the corpus.

The corpus has a second purpose, internal to this document: every example
written in Parts 1 to 3 is meant to be a legal script with a stated outcome, and
illegal ones keep surviving several reviews before being caught by hand — a
`create` written with square brackets, a selector giving half of a natural
identity, a trailing comma in a predicate list. A corpus makes that class of
defect mechanical to detect.

### D.1 Format

The corpus is a JSON array of cases. Each case is an object:

| Field | Meaning |
|---|---|
| `id` | stable case identifier, e.g. `"C-014"` |
| `description` | one sentence, for the test report |
| `syntax` | `"LiftPatchRef"` or `"LiftPatchShort"` |
| `dictionary` | the initial state: the name of a fixture (section "D.2"), or an inline dictionary document of the same shape |
| `script` | the script to run, as a single string, newlines included |
| `expect` | the expected outcome (below) |
| `section` | the section of this specification the case exercises, informative |

`expect` is one of two shapes:

```text
{ "status": "ok", "effects": [ … ], "danglingReferences": [ … ], "warnings": [ … ] }
{ "status": "error", "code": "AMBIGUOUS_REFERENCE", "kind": "dynamic", "commandIndex": 1 }
```

- For an `ok` case, `effects` is the concatenation, in order, of the `effects` (or `applications`) arrays that plan mode produces for the script (section "12.4.1"), restricted to the comparable fields. **This list is the single normative statement of which fields of an effect are compared**, and it is exhaustive: `kind`, `componentType`, `property`, `language`, `languageKind`, `oldValue`, `newValue`, `position`, `fromPosition`, `toPosition`. The remaining fields of an effect — `fromParent` and `toParent`, which contain a `path` — are informative and are never compared. An implementation passes the case when its plan document yields exactly that list, and when applying the script for real produces the same effects.
- `danglingReferences` and `warnings` are compared when the case states them, and are expected to be empty when it does not.
- For an `error` case, the implementation passes when it raises exactly `code`, of exactly that `kind`, on exactly the operation at `commandIndex`, **and** when the dictionary is unchanged afterwards (section "12.2"). `commandIndex` is the `index` of section "12.4.1": 1-based, flat across the whole script, counting a block header as an operation of its own, and `0` for an error carried by the script as a whole, such as a pragma error.
- `message` text is never compared. Neither is `path`, which is informative.
- An `error` case whose `kind` is `static` additionally requires that no command of the script has been applied even partially, and that the error be reported before any dictionary access.

Most commands of this language are not idempotent and were never meant to be:
running `C-001` twice raises `CANNOT_CREATE_DUPLICATE`, running `C-022` twice
deletes nothing the second time. Where re-running a script *is* meaningful —
`upsert`, `ensure`, `set` — a case may state what the second run does, with an
optional field:

```text
"rerun": "noEffect" | "error:CANNOT_CREATE_DUPLICATE"
```

An implementation checks `rerun` only when the case states it. Cases `C-028`,
`C-029` and `C-033` state it, since the idempotence of `upsert` and of `ensure`
is one of the properties this specification most needs held to.

### D.2 The standard fixture

Most cases run against one small dictionary, `"standard"`, which is deliberately
built around the hard cases: two homophonous entries, a sense with two glosses, a
sense with two examples, and an inbound reference.

```json
{
  "fixture": "standard",
  "objectLanguages": ["tww", "tpi"],
  "metaLanguages": ["en", "fr"],
  "entries": [
    {
      "id": "e-1", "hn": 1,
      "form": { "tww": "mami", "tpi": "pik" },
      "senses": [
        {
          "id": "s-1",
          "gloss": { "en": "pig", "fr": "cochon" },
          "definition": { "en": "A four-legged terrestrial animal" },
          "category": { "value": "Noun" },
          "examples": [
            { "text": { "tww": "a mami jefi" },
              "translations": [ { "type": "free", "text": { "en": "I shot a pig" } } ] },
            { "text": { "tww": "a mami jefo" } }
          ]
        },
        { "id": "s-2", "gloss": { "en": "pork" }, "category": { "value": "Noun" } }
      ]
    },
    {
      "id": "e-2", "hn": 2,
      "form": { "tww": "mami" },
      "senses": [
        { "id": "s-3", "gloss": { "en": "taro" } },
        { "id": "s-5", "gloss": { "en": "pig" } }
      ]
    },
    {
      "id": "e-3", "hn": 1,
      "form": { "tww": "memi" },
      "senses": [ { "id": "s-4", "gloss": { "en": "dog" } } ],
      "relations": [ { "type": "synonym", "target": "e-1" } ]
    }
  ]
}
```

Note that `hn` values are given so that cases are reproducible; they remain
dictionary-assigned (section "5.3.2.1"), and a case MUST NOT assume any `hn`
that the fixture does not state.

Two shapes of the fixture follow from the component kinds of section "3.1" and
are normative:

- a **singleton** child is written as an object rather than as a list, because there is exactly one: `"category": { "value": "Noun" }`. A sense for which the fixture states no `category` still **has** one — `s-3`, `s-4` and `s-5` each have a category whose `value` is unset — since a singleton always exists on its host. Writing `"category": {}` and omitting the key are therefore the same fixture;
- a **typed** child list is keyed by `type`, and no two entries of one such list may share a `type`; the order in which a fixture happens to write them is not significant and an implementation MUST NOT depend on it.

### D.3 The core cases

The cases below are normative and minimal: they cover the rules that this
specification had to state twice, or that a reviewer got wrong. An
implementation MAY add cases; it MUST pass these.

```json
[
  { "id": "C-001", "section": "8.1", "syntax": "LiftPatchRef",
    "description": "create a sense under an unambiguous entry",
    "dictionary": "standard",
    "script": "create sense(gloss@en = \"puppy\") under entry[form@tww = \"memi\"]",
    "expect": { "status": "ok", "effects": [
      { "kind": "componentCreated", "componentType": "sense", "position": 2 },
      { "kind": "propertySet", "property": "gloss", "language": "en", "oldValue": null, "newValue": "puppy" } ] } },

  { "id": "C-002", "section": "5.3.2", "syntax": "LiftPatchRef",
    "description": "a form shared by two entries is ambiguous, and is never silently resolved",
    "dictionary": "standard",
    "script": "create sense(gloss@en = \"piglet\") under entry[form@tww = \"mami\"]",
    "expect": { "status": "error", "code": "AMBIGUOUS_REFERENCE", "kind": "dynamic", "commandIndex": 1 } },

  { "id": "C-003", "section": "5.3.2.2", "syntax": "LiftPatchRef",
    "description": "has-gloss disambiguates the homophones",
    "dictionary": "standard",
    "script": "create sense(gloss@en = \"piglet\") under entry[form@tww = \"mami\", has-gloss@en = \"taro\"]",
    "expect": { "status": "ok", "effects": [
      { "kind": "componentCreated", "componentType": "sense", "position": 3 },
      { "kind": "propertySet", "property": "gloss", "language": "en", "oldValue": null, "newValue": "piglet" } ] } },

  { "id": "C-004", "section": "7.3", "syntax": "LiftPatchRef",
    "description": "a within chain resolves through an ambiguous ancestor",
    "dictionary": "standard",
    "script": "set value = \"Verb\"\n  on category\n  of sense[gloss@en = \"taro\"]\n  within entry[form@tww = \"mami\"]",
    "expect": { "status": "ok", "effects": [
      { "kind": "propertySet", "property": "value", "language": null, "oldValue": null, "newValue": "Verb" } ] } },

  { "id": "C-005", "section": "7.3", "syntax": "LiftPatchRef",
    "description": "a within chain that leaves two candidate paths fails",
    "dictionary": "standard",
    "script": "set value = \"Verb\"\n  on category\n  of sense[gloss@en = \"pig\"]\n  within entry[form@tww = \"mami\"]",
    "expect": { "status": "error", "code": "AMBIGUOUS_REFERENCE", "kind": "dynamic", "commandIndex": 1 } },

  { "id": "C-006", "section": "5.2", "syntax": "LiftPatchRef",
    "description": "two senses under one entry may not share a qualified gloss",
    "dictionary": "standard",
    "script": "create sense(gloss@en = \"pork\") under entry[form@tww = \"memi\", has-gloss@en = \"dog\"]\ncreate sense(gloss@en = \"pork\") under entry[form@tww = \"memi\", has-gloss@en = \"dog\"]",
    "expect": { "status": "error", "code": "CANNOT_CREATE_DUPLICATE", "kind": "dynamic", "commandIndex": 2 } },

  { "id": "C-007", "section": "8.1.1", "syntax": "LiftPatchRef",
    "description": "entry has an empty natural identity: a third homophone is legal",
    "dictionary": "standard",
    "script": "create entry(form@tww = \"mami\")",
    "expect": { "status": "ok", "effects": [
      { "kind": "componentCreated", "componentType": "entry", "position": null },
      { "kind": "propertySet", "property": "form", "language": "tww", "oldValue": null, "newValue": "mami" } ] } },

  { "id": "C-008", "section": "9.3.1", "syntax": "LiftPatchRef",
    "description": "clearing the only qualified value of a required multitext is refused",
    "dictionary": "standard",
    "script": "clear gloss@en on sense[gloss@en = \"taro\"] of entry[form@tww = \"mami\", hn = 2]",
    "expect": { "status": "error", "code": "CANNOT_CLEAR_REQUIRED_MULTITEXT", "kind": "dynamic", "commandIndex": 1 } },

  { "id": "C-009", "section": "9.3.1", "syntax": "LiftPatchRef",
    "description": "clearing one qualified value of a required multitext is legal when another remains",
    "dictionary": "standard",
    "script": "clear gloss@fr on sense[gloss@en = \"pig\"] of entry[form@tww = \"mami\", hn = 1]",
    "expect": { "status": "ok", "effects": [
      { "kind": "propertyRemoved", "property": "gloss", "language": "fr", "oldValue": "cochon", "newValue": null } ] } },

  { "id": "C-010", "section": "5.5.3", "syntax": "LiftPatchRef",
    "description": "clear on a multitext requires an explicit qualifier",
    "dictionary": "standard",
    "script": "clear definition on sense[gloss@en = \"pig\"] of entry[form@tww = \"mami\", hn = 1]",
    "expect": { "status": "error", "code": "MISSING_LANGUAGE_QUALIFIER", "kind": "static", "commandIndex": 1 } },

  { "id": "C-011", "section": "9.2", "syntax": "LiftPatchRef",
    "description": "update tests the qualified value, not the property",
    "dictionary": "standard",
    "script": "update definition@fr = \"Un animal\" on sense[gloss@en = \"pig\"] of entry[form@tww = \"mami\", hn = 1]",
    "expect": { "status": "error", "code": "UNSET_QUALIFIED_PROPERTY", "kind": "dynamic", "commandIndex": 1 } },

  { "id": "C-012", "section": "8.2.1", "syntax": "LiftPatchRef",
    "description": "upsert select branch: the sense exists, only the assignment part applies",
    "dictionary": "standard",
    "script": "upsert sense(gloss@en = \"taro\", definition@en = \"An edible root\") under entry[form@tww = \"mami\", hn = 2]",
    "expect": { "status": "ok", "effects": [
      { "kind": "propertySet", "property": "definition", "language": "en", "oldValue": null, "newValue": "An edible root" } ] } },

  { "id": "C-013", "section": "8.2.1", "syntax": "LiftPatchRef",
    "description": "upsert create branch: the sense does not exist",
    "dictionary": "standard",
    "script": "upsert sense(gloss@en = \"yam\", definition@en = \"An edible root\") under entry[form@tww = \"mami\", hn = 2]",
    "expect": { "status": "ok", "effects": [
      { "kind": "componentCreated", "componentType": "sense", "position": 3 },
      { "kind": "propertySet", "property": "gloss", "language": "en", "oldValue": null, "newValue": "yam" },
      { "kind": "propertySet", "property": "definition", "language": "en", "oldValue": null, "newValue": "An edible root" } ] } },

  { "id": "C-014", "section": "8.2.3", "syntax": "LiftPatchRef",
    "description": "upsert on an entry needs a disambiguation predicate",
    "dictionary": "standard",
    "script": "upsert entry(form@tww = \"mami\")",
    "expect": { "status": "error", "code": "UPSERT_ENTRY_WITHOUT_DISAMBIGUATION", "kind": "static", "commandIndex": 1 } },

  { "id": "C-015", "section": "6.2.1", "syntax": "LiftPatchRef",
    "description": "at after places the component relative to a named sibling",
    "dictionary": "standard",
    "script": "create example(text@tww = \"a mami jefu\")\n  under sense[gloss@en = \"pig\"]\n  of entry[form@tww = \"mami\", hn = 1]\n  at after example[text@tww = \"a mami jefi\"]",
    "expect": { "status": "ok", "effects": [
      { "kind": "componentCreated", "componentType": "example", "position": 2 },
      { "kind": "propertySet", "property": "text", "language": "tww", "oldValue": null, "newValue": "a mami jefu" } ] } },

  { "id": "C-016", "section": "6.2", "syntax": "LiftPatchRef",
    "description": "an entry takes no at clause",
    "dictionary": "standard",
    "script": "create entry(form@tww = \"mimi\") at beginning",
    "expect": { "status": "error", "code": "SYNTAX_ERROR", "kind": "static", "commandIndex": 1 } },

  { "id": "C-017", "section": "11", "syntax": "LiftPatchRef",
    "description": "move is not allowed in a block body",
    "dictionary": "standard",
    "script": "sense[gloss@en = \"pig\"] of entry[form@tww = \"mami\", hn = 1] {\n  move example#1 at end\n}",
    "expect": { "status": "error", "code": "MOVE_NOT_ALLOWED_IN_BLOCK", "kind": "static", "commandIndex": 2 } },

  { "id": "C-018", "section": "10", "syntax": "LiftPatchRef",
    "description": "a reference property is compared to a chain in a selector",
    "dictionary": "standard",
    "script": "set type = \"see-also\"\n  on relation[type = \"synonym\", target = entry[form@tww = \"mami\", hn = 1]]\n  of entry[form@tww = \"memi\"]",
    "expect": { "status": "ok", "effects": [
      { "kind": "propertyReplaced", "property": "type", "language": null, "oldValue": "synonym", "newValue": "see-also" } ] } },

  { "id": "C-019", "section": "10", "syntax": "LiftPatchRef",
    "description": "a bare string is not a reference value, in a selector either",
    "dictionary": "standard",
    "script": "set type = \"see-also\" on relation[type = \"synonym\", target = \"e-1\"] of entry[form@tww = \"memi\"]",
    "expect": { "status": "error", "code": "REFERENCE_VALUE_MUST_BE_A_CHAIN", "kind": "static", "commandIndex": 1 } },

  { "id": "C-020", "section": "3", "syntax": "LiftPatchRef",
    "description": "illegal parentage is refused: an example has no note child",
    "dictionary": "standard",
    "script": "create note(type = \"x\", text@en = \"y\")\n  under example[text@tww = \"a mami jefi\"]\n  of sense[gloss@en = \"pig\"]\n  of entry[form@tww = \"mami\", hn = 1]",
    "expect": { "status": "error", "code": "ILLEGAL_PARENT", "kind": "static", "commandIndex": 1 } },

  { "id": "C-021", "section": "9.1.1", "syntax": "LiftPatchRef",
    "description": "an assignment list is the sequence of its assignments, applied to one resolved parent",
    "dictionary": "standard",
    "script": "set definition@en = \"A pig\", definition@fr = \"Un animal\"\n  on sense[gloss@en = \"pig\"]\n  of entry[form@tww = \"mami\", hn = 1]",
    "expect": { "status": "ok", "effects": [
      { "kind": "propertyReplaced", "property": "definition", "language": "en", "oldValue": "A four-legged terrestrial animal", "newValue": "A pig" },
      { "kind": "propertySet", "property": "definition", "language": "fr", "oldValue": null, "newValue": "Un animal" } ] } },

  { "id": "C-022", "section": "5.6", "syntax": "LiftPatchRef",
    "description": "delete all applies a filtering predicate to the target step",
    "dictionary": "standard",
    "script": "delete all example[text@tww ~ \"jef\"]\n  under sense[gloss@en = \"pig\"]\n  of entry[form@tww = \"mami\", hn = 1]",
    "expect": { "status": "ok", "effects": [
      { "kind": "componentDeleted", "componentType": "example", "position": 1 },
      { "kind": "componentDeleted", "componentType": "example", "position": 2 } ] } },

  { "id": "C-023", "section": "12.2", "syntax": "LiftPatchRef",
    "description": "a script is atomic: a failing second command undoes the first",
    "dictionary": "standard",
    "script": "create sense(gloss@en = \"puppy\") under entry[form@tww = \"memi\"]\ncreate sense(gloss@en = \"piglet\") under entry[form@tww = \"mami\"]",
    "expect": { "status": "error", "code": "AMBIGUOUS_REFERENCE", "kind": "dynamic", "commandIndex": 2 } },

  { "id": "C-024", "section": "12.4.2", "syntax": "LiftPatchRef",
    "description": "deleting a referenced entry is legal and reported as a dangling reference",
    "dictionary": "standard",
    "script": "delete entry[form@tww = \"mami\", hn = 1]",
    "expect": { "status": "ok",
      "effects": [ { "kind": "componentDeleted", "componentType": "entry", "position": null } ],
      "danglingReferences": [
        { "component": { "componentType": "relation", "id": null }, "property": "target", "value": "e-1" } ] } },

  { "id": "C-025", "section": "Part 3, 1.1", "syntax": "LiftPatchShort",
    "description": "a prose line that starts with a command letter is prose, not a command",
    "dictionary": "standard",
    "script": "c est une erreur fréquente\ne e[f=\"pig\"] is prose too, since a bare step is not a path\nc /memi/puppy",
    "expect": { "status": "ok", "effects": [
      { "kind": "componentCreated", "componentType": "sense", "position": 2 },
      { "kind": "propertySet", "property": "gloss", "language": "en", "oldValue": null, "newValue": "puppy" } ] } },

  { "id": "C-026", "section": "Part 3, 4.1", "syntax": "LiftPatchShort",
    "description": "in a property command every path link is existential",
    "dictionary": "standard",
    "script": "s /e[f=\"mami\"]/s[g=\"taro\"]/c (v = \"Noun\")",
    "expect": { "status": "ok", "effects": [
      { "kind": "propertySet", "property": "value", "language": null, "oldValue": null, "newValue": "Noun" } ] } },

  { "id": "C-027", "section": "Part 3, 3", "syntax": "LiftPatchShort",
    "description": "the link between the path and the target is strict, so the homophones are ambiguous",
    "dictionary": "standard",
    "script": "d /e[f=\"mami\"] s[g=\"taro\"]",
    "expect": { "status": "error", "code": "AMBIGUOUS_REFERENCE", "kind": "dynamic", "commandIndex": 1 } },

  { "id": "C-028", "section": "Part 3, 8", "syntax": "LiftPatchShort",
    "description": "the idiomatic upsert creates the entry and the sense when no match exists",
    "dictionary": "standard",
    "script": "p /mimi/lizard",
    "expect": { "status": "ok", "rerun": "noEffect", "effects": [
      { "kind": "componentCreated", "componentType": "entry", "position": null },
      { "kind": "propertySet", "property": "form", "language": "tww", "oldValue": null, "newValue": "mimi" },
      { "kind": "componentCreated", "componentType": "sense", "position": 1 },
      { "kind": "propertySet", "property": "gloss", "language": "en", "oldValue": null, "newValue": "lizard" } ] } },

  { "id": "C-029", "section": "8.3", "syntax": "LiftPatchRef",
    "description": "ensure succeeds and changes nothing",
    "dictionary": "standard",
    "script": "ensure sense[gloss@en = \"taro\"] under entry[form@tww = \"mami\", hn = 2]",
    "expect": { "status": "ok", "rerun": "noEffect", "effects": [] } },

  { "id": "C-030", "section": "5.3.2.1", "syntax": "LiftPatchRef",
    "description": "an hn that matches nothing is NOT_FOUND, not a code of its own",
    "dictionary": "standard",
    "script": "ensure entry[form@tww = \"mami\", hn = 7]",
    "expect": { "status": "error", "code": "NOT_FOUND", "kind": "dynamic", "commandIndex": 1 } },

  { "id": "C-031", "section": "5.4.2", "syntax": "LiftPatchRef",
    "description": "an ordinal is allowed as the step of an on clause, and forbidden as a property",
    "dictionary": "standard",
    "script": "set text@tpi = \"mi shutim pik\"\n  on example#1\n  within sense[has category[value = \"Noun\"]]\n  within entry[form@tww = \"mami\"]",
    "expect": { "status": "ok", "effects": [
      { "kind": "propertySet", "property": "text", "language": "tpi", "oldValue": null, "newValue": "mi shutim pik" } ] } },

  { "id": "C-032", "section": "6.3", "syntax": "LiftPatchRef",
    "description": "ensure binds a label, which a later command uses as a step",
    "dictionary": "standard",
    "script": "ensure entry[form@tww = \"memi\"] as $memi\ncreate sense(gloss@en = \"puppy\") under $memi",
    "expect": { "status": "ok", "effects": [
      { "kind": "componentCreated", "componentType": "sense", "position": 2 },
      { "kind": "propertySet", "property": "gloss", "language": "en", "oldValue": null, "newValue": "puppy" } ] } },

  { "id": "C-033", "section": "Part 3, 8", "syntax": "LiftPatchShort",
    "description": "the idiomatic upsert takes its select branch and changes nothing when the pair exists",
    "dictionary": "standard",
    "script": "p /mami/taro",
    "expect": { "status": "ok", "rerun": "noEffect", "effects": [] } },

  { "id": "C-034", "section": "8.5", "syntax": "LiftPatchRef",
    "description": "a same-parent move reports one componentMoved with both positions",
    "dictionary": "standard",
    "script": "move example#1\n  under sense[gloss@en = \"pig\"]\n  of entry[form@tww = \"mami\", hn = 1]\n  at end",
    "expect": { "status": "ok", "effects": [
      { "kind": "componentMoved", "componentType": "example", "fromPosition": 1, "toPosition": 2 } ] } },

  { "id": "C-035", "section": "8.5", "syntax": "LiftPatchRef",
    "description": "moving to the position already occupied is refused",
    "dictionary": "standard",
    "script": "move example#1\n  under sense[gloss@en = \"pig\"]\n  of entry[form@tww = \"mami\", hn = 1]\n  at beginning",
    "expect": { "status": "error", "code": "MOVING_TO_CURRENT_POSITION", "kind": "dynamic", "commandIndex": 1 } },

  { "id": "C-036", "section": "8.5", "syntax": "LiftPatchRef",
    "description": "a move may not make a component its own ancestor",
    "dictionary": "standard",
    "script": "move sense[gloss@en = \"pig\"]\n  under entry[form@tww = \"mami\", hn = 1]\n  under sense[gloss@en = \"pig\"] of entry[form@tww = \"mami\", hn = 1]\n  at end",
    "expect": { "status": "error", "code": "SELF_ANCESTOR", "kind": "dynamic", "commandIndex": 1 } },

  { "id": "C-037", "section": "5.6", "syntax": "LiftPatchRef",
    "description": "each applies the assignment to every matched component, in document order",
    "dictionary": "standard",
    "script": "set value = \"Verb\"\n  on each category\n  of sense[exists(gloss@en)]\n  of entry[form@tww = \"mami\", hn = 1]",
    "expect": { "status": "ok", "effects": [
      { "kind": "propertyReplaced", "property": "value", "language": null, "oldValue": "Noun", "newValue": "Verb" },
      { "kind": "propertyReplaced", "property": "value", "language": null, "oldValue": "Noun", "newValue": "Verb" } ] } },

  { "id": "C-038", "section": "5.6", "syntax": "LiftPatchRef",
    "description": "a marked step matching nothing succeeds, changes nothing, and warns",
    "dictionary": "standard",
    "script": "delete all example[text@tww ~ \"^draft\"]\n  under sense[gloss@en = \"pork\"]\n  of entry[form@tww = \"mami\", hn = 1]",
    "expect": { "status": "ok", "effects": [],
      "warnings": [ { "code": "COMMAND_APPLIES_TO_NO_COMPONENT" } ] } },

  { "id": "C-039", "section": "5.5.1", "syntax": "LiftPatchRef",
    "description": "a multi-language literal is the sequence of its qualified assignments",
    "dictionary": "standard",
    "script": "set definition = { en: \"A pig\", fr: \"Un cochon\" }\n  on sense[gloss@en = \"pig\"]\n  of entry[form@tww = \"mami\", hn = 1]",
    "expect": { "status": "ok", "effects": [
      { "kind": "propertyReplaced", "property": "definition", "language": "en", "oldValue": "A four-legged terrestrial animal", "newValue": "A pig" },
      { "kind": "propertySet", "property": "definition", "language": "fr", "oldValue": null, "newValue": "Un cochon" } ] } },

  { "id": "C-040", "section": "9.3.1", "syntax": "LiftPatchRef",
    "description": "clear @* on an optional multitext removes one effect per language actually removed",
    "dictionary": "standard",
    "script": "clear definition@*\n  on sense[gloss@en = \"pig\"]\n  of entry[form@tww = \"mami\", hn = 1]",
    "expect": { "status": "ok", "effects": [
      { "kind": "propertyRemoved", "property": "definition", "language": "en", "oldValue": "A four-legged terrestrial animal", "newValue": null } ] } },

  { "id": "C-041", "section": "2.1.1", "syntax": "LiftPatchRef",
    "description": "language-default changes which language an unqualified assignment writes",
    "dictionary": "standard",
    "script": "language-default meta = \"fr\"\ncreate sense(gloss = \"chiot\") under entry[form@tww = \"memi\"]",
    "expect": { "status": "ok", "effects": [
      { "kind": "componentCreated", "componentType": "sense", "position": 2 },
      { "kind": "propertySet", "property": "gloss", "language": "fr", "oldValue": null, "newValue": "chiot" } ] } },

  { "id": "C-042", "section": "2.1.3", "syntax": "LiftPatchRef",
    "description": "language-create is an operation, and is rolled back with the rest of a failing script",
    "dictionary": "standard",
    "script": "language-create object = \"hui\"\ncreate entry(form@hui = \"mami\")\ncreate sense(gloss@en = \"piglet\") under entry[form@tww = \"mami\"]",
    "expect": { "status": "error", "code": "AMBIGUOUS_REFERENCE", "kind": "dynamic", "commandIndex": 3 } },

  { "id": "C-043", "section": "2.1.2", "syntax": "LiftPatchRef",
    "description": "a with header binds defaults for its body and is not an operation",
    "dictionary": "standard",
    "script": "with object = \"tpi\", meta = \"fr\" {\n  create entry(form = \"pikpik\") as $m\n  create sense(gloss = \"lezard\") under $m\n}",
    "expect": { "status": "ok", "effects": [
      { "kind": "componentCreated", "componentType": "entry", "position": null },
      { "kind": "propertySet", "property": "form", "language": "tpi", "oldValue": null, "newValue": "pikpik" },
      { "kind": "componentCreated", "componentType": "sense", "position": 1 },
      { "kind": "propertySet", "property": "gloss", "language": "fr", "oldValue": null, "newValue": "lezard" } ] } },

  { "id": "C-044", "section": "11", "syntax": "LiftPatchRef",
    "description": "a block header is an operation and may bind a label visible after the block",
    "dictionary": "standard",
    "script": "entry[form@tww = \"memi\"] as $memi {\n  create sense(gloss@en = \"puppy\")\n}\ncreate note(type = \"general\", text@en = \"checked\") under $memi",
    "expect": { "status": "ok", "effects": [
      { "kind": "componentCreated", "componentType": "sense", "position": 2 },
      { "kind": "propertySet", "property": "gloss", "language": "en", "oldValue": null, "newValue": "puppy" },
      { "kind": "componentCreated", "componentType": "note", "position": 1 },
      { "kind": "propertySet", "property": "text", "language": "en", "oldValue": null, "newValue": "checked" } ] } },

  { "id": "C-045", "section": "5.1.3", "syntax": "LiftPatchRef",
    "description": "a has predicate refines strategy S3 on a component type that is not an entry",
    "dictionary": "standard",
    "script": "set value = \"Verb\"\n  on category\n  of sense[gloss@en = \"pig\", has example[text@tww = \"a mami jefo\"]]\n  of entry[form@tww = \"mami\", hn = 1]",
    "expect": { "status": "ok", "effects": [
      { "kind": "propertyReplaced", "property": "value", "language": null, "oldValue": "Noun", "newValue": "Verb" } ] } },

  { "id": "C-046", "section": "7.3", "syntax": "LiftPatchRef",
    "description": "a chain that does not reach the root is refused before any lookup",
    "dictionary": "standard",
    "script": "set value = \"Verb\" on category of sense[gloss@en = \"pig\"]",
    "expect": { "status": "error", "code": "INCOMPLETE_ANCESTOR_CHAIN", "kind": "static", "commandIndex": 1 } },

  { "id": "C-047", "section": "7.2", "syntax": "LiftPatchRef",
    "description": "a component command other than one targeting an entry needs a parent clause",
    "dictionary": "standard",
    "script": "create sense(gloss@en = \"piglet\")",
    "expect": { "status": "error", "code": "MISSING_PARENT_CLAUSE", "kind": "static", "commandIndex": 1 } },

  { "id": "C-048", "section": "5.1.2", "syntax": "LiftPatchRef",
    "description": "a regular-expression operator does not apply to a reference property",
    "dictionary": "standard",
    "script": "delete all relation[target ~ entry[form@tww = \"mami\", hn = 1]] under entry[form@tww = \"memi\"]",
    "expect": { "status": "error", "code": "OPERATOR_NOT_APPLICABLE_TO_DATATYPE", "kind": "static", "commandIndex": 1 } },

  { "id": "C-049", "section": "10", "syntax": "LiftPatchRef",
    "description": "a reference target that is neither an entry nor a sense is a static error",
    "dictionary": "standard",
    "script": "set target = example[text@tww = \"a mami jefi\"] of sense[gloss@en = \"pig\"] of entry[form@tww = \"mami\", hn = 1]\n  on relation#1\n  of entry[form@tww = \"memi\"]",
    "expect": { "status": "error", "code": "INVALID_TARGET", "kind": "static", "commandIndex": 1 } },

  { "id": "C-050", "section": "5.3.2.2", "syntax": "LiftPatchRef",
    "description": "has-gloss is not admitted in a create initializer",
    "dictionary": "standard",
    "script": "create entry(form@tww = \"mimi\", has-gloss@en = \"pig\")",
    "expect": { "status": "error", "code": "COMMAND_NOT_ALLOWING_HAS", "kind": "static", "commandIndex": 1 } },

  { "id": "C-051", "section": "5.3.2.1", "syntax": "LiftPatchRef",
    "description": "hn may not be used without a form",
    "dictionary": "standard",
    "script": "delete entry[hn = 1]",
    "expect": { "status": "error", "code": "HN_CANNOT_BE_USED_ALONE", "kind": "static", "commandIndex": 1 } },

  { "id": "C-052", "section": "Part 3, 3.3", "syntax": "LiftPatchShort",
    "description": "an indented block anchors its commands to the component of the line above",
    "dictionary": "standard",
    "script": "c e(\"mimi\")\n  c s(\"lizard\")\n    s (d@en = \"A lizard\")",
    "expect": { "status": "ok", "effects": [
      { "kind": "componentCreated", "componentType": "entry", "position": null },
      { "kind": "propertySet", "property": "form", "language": "tww", "oldValue": null, "newValue": "mimi" },
      { "kind": "componentCreated", "componentType": "sense", "position": 1 },
      { "kind": "propertySet", "property": "gloss", "language": "en", "oldValue": null, "newValue": "lizard" },
      { "kind": "propertySet", "property": "definition", "language": "en", "oldValue": null, "newValue": "A lizard" } ] } },

  { "id": "C-053", "section": "Part 3, 8", "syntax": "LiftPatchShort",
    "description": "the idiomatic upsert extends to the third abbreviated step, the example text",
    "dictionary": "standard",
    "script": "p /mimi/lizard/\"a mimi jefi\"",
    "expect": { "status": "ok", "effects": [
      { "kind": "componentCreated", "componentType": "entry", "position": null },
      { "kind": "propertySet", "property": "form", "language": "tww", "oldValue": null, "newValue": "mimi" },
      { "kind": "componentCreated", "componentType": "sense", "position": 1 },
      { "kind": "propertySet", "property": "gloss", "language": "en", "oldValue": null, "newValue": "lizard" },
      { "kind": "componentCreated", "componentType": "example", "position": 1 },
      { "kind": "propertySet", "property": "text", "language": "tww", "oldValue": null, "newValue": "a mimi jefi" } ] } },

  { "id": "C-054", "section": "Part 3, 3", "syntax": "LiftPatchShort",
    "description": "a path-only create or upsert needs abbreviated steps",
    "dictionary": "standard",
    "script": "p /e[f=\"mami\"]/s[g=\"pig\"]",
    "expect": { "status": "error", "code": "CONSTRUCTOR_REQUIRED", "kind": "static", "commandIndex": 1 } },

  { "id": "C-055", "section": "Part 3, 6.1", "syntax": "LiftPatchShort",
    "description": "there is no abbreviated step below the third depth",
    "dictionary": "standard",
    "script": "d /mami/pig/\"a mami jefi\" foo",
    "expect": { "status": "error", "code": "ABBREVIATED_STEP_NOT_ALLOWED_HERE", "kind": "static", "commandIndex": 1 } },

  { "id": "C-056", "section": "1.1", "syntax": "LiftPatchShort",
    "description": "a reference-syntax script read as a concise document applies nothing, and says so",
    "dictionary": "standard",
    "script": "create sense(gloss@en = \"puppy\") under entry[form@tww = \"memi\"]",
    "expect": { "status": "ok", "effects": [],
      "warnings": [ { "code": "NO_COMMAND_RECOGNIZED" } ] } },

  { "id": "C-057", "section": "2.1.3", "syntax": "LiftPatchRef",
    "description": "language-create is an operation and carries a languageCreated effect",
    "dictionary": "standard",
    "script": "language-create object = \"hui\"\ncreate entry(form@hui = \"mimi\")",
    "expect": { "status": "ok", "effects": [
      { "kind": "languageCreated", "languageKind": "object", "language": "hui" },
      { "kind": "componentCreated", "componentType": "entry", "position": null },
      { "kind": "propertySet", "property": "form", "language": "hui", "oldValue": null, "newValue": "mimi" } ] } },

  { "id": "C-058", "section": "8.5", "syntax": "LiftPatchRef",
    "description": "a move to another parent reports one componentMoved with both positions",
    "dictionary": "standard",
    "script": "move example#1\n  under sense[gloss@en = \"pig\"] of entry[form@tww = \"mami\", hn = 1]\n  under sense[gloss@en = \"pork\"] of entry[form@tww = \"mami\", hn = 1]\n  at end",
    "expect": { "status": "ok", "effects": [
      { "kind": "componentMoved", "componentType": "example", "fromPosition": 1, "toPosition": 1 } ] } },

  { "id": "C-059", "section": "Part 3, 4.1", "syntax": "LiftPatchShort",
    "description": "every written link of a parent path is existential, so a path over two homophones that both carry the sense is ambiguous",
    "dictionary": "standard",
    "script": "d /mami/pig x#1",
    "expect": { "status": "error", "code": "AMBIGUOUS_REFERENCE", "kind": "dynamic", "commandIndex": 1 } },

  { "id": "C-060", "section": "Part 3, 4.1", "syntax": "LiftPatchShort",
    "description": "the strict axis is written ! on any link, and makes the step above it a unique selector, which is what admits hn",
    "dictionary": "standard",
    "script": "d /e[f=\"mami\", hn=1]!s[g=\"pig\"] x#1",
    "expect": { "status": "ok", "effects": [
      { "kind": "componentDeleted", "componentType": "example", "position": 1 } ] } },

  { "id": "C-061", "section": "Part 3, 4.1", "syntax": "LiftPatchShort",
    "description": "the direct target of a delete is exactly one step, written after the parent path",
    "dictionary": "standard",
    "script": "d /mami/pig",
    "expect": { "status": "error", "code": "TARGET_MUST_BE_A_SINGLE_STEP", "kind": "static", "commandIndex": 1 } },

  { "id": "C-062", "section": "Part 3, 3.1", "syntax": "LiftPatchShort",
    "description": "ensure with a separated target step succeeds and changes nothing",
    "dictionary": "standard",
    "script": "e /memi dog",
    "expect": { "status": "ok", "rerun": "noEffect", "effects": [] } },

  { "id": "C-063", "section": "Part 3, 5.3", "syntax": "LiftPatchShort",
    "description": "a concise move with a to clause reports one componentMoved with both positions, as C-058 does in the reference syntax",
    "dictionary": "standard",
    "script": "m /e[f=\"mami\", hn=1]!s[g=\"pig\"] x#1 to /e[f=\"mami\", hn=1]!s[g=\"pork\"] at end",
    "expect": { "status": "ok", "effects": [
      { "kind": "componentMoved", "componentType": "example", "fromPosition": 1, "toPosition": 1 } ] } },

  { "id": "C-064", "section": "3.1", "syntax": "LiftPatchRef",
    "description": "a singleton is written on with a property command and needs no selector",
    "dictionary": "standard",
    "script": "set value = \"Verb\"\n  on category\n  of sense[gloss@en = \"pig\"]\n  of entry[form@tww = \"mami\", hn = 1]",
    "expect": { "status": "ok", "effects": [
      { "kind": "propertyReplaced", "property": "value", "language": null, "oldValue": "Noun", "newValue": "Verb" } ] } },

  { "id": "C-065", "section": "3.1", "syntax": "LiftPatchRef",
    "description": "a singleton always exists, so its value may be set on a sense the fixture gives none for",
    "dictionary": "standard",
    "script": "set value = \"Noun\"\n  on category\n  of sense[gloss@en = \"taro\"]\n  of entry[form@tww = \"mami\", hn = 2]",
    "expect": { "status": "ok", "effects": [
      { "kind": "propertySet", "property": "value", "language": null, "oldValue": null, "newValue": "Noun" } ] } },

  { "id": "C-066", "section": "3.1", "syntax": "LiftPatchRef",
    "description": "a singleton cannot be created",
    "dictionary": "standard",
    "script": "create category(value = \"Noun\") under sense[gloss@en = \"taro\"] of entry[form@tww = \"mami\", hn = 2]",
    "expect": { "status": "error", "code": "SINGLETON_CANNOT_BE_CREATED_OR_DELETED", "kind": "static", "commandIndex": 1 } },

  { "id": "C-067", "section": "8.4", "syntax": "LiftPatchRef",
    "description": "a singleton cannot be deleted",
    "dictionary": "standard",
    "script": "delete category under sense[gloss@en = \"pig\"] of entry[form@tww = \"mami\", hn = 1]",
    "expect": { "status": "error", "code": "SINGLETON_CANNOT_BE_CREATED_OR_DELETED", "kind": "static", "commandIndex": 1 } },

  { "id": "C-068", "section": "5.1.3", "syntax": "LiftPatchRef",
    "description": "a singleton takes no selector in a unique position",
    "dictionary": "standard",
    "script": "set value = \"Verb\"\n  on category[value = \"Noun\"]\n  of sense[gloss@en = \"pig\"]\n  of entry[form@tww = \"mami\", hn = 1]",
    "expect": { "status": "error", "code": "SINGLETON_TAKES_NO_SELECTOR", "kind": "static", "commandIndex": 1 } },

  { "id": "C-069", "section": "5.1.4", "syntax": "LiftPatchRef",
    "description": "a singleton step inside a has predicate is a filtering step and may carry a predicate list",
    "dictionary": "standard",
    "script": "set definition@fr = \"Un animal\"\n  on sense[gloss@en = \"pig\", has category[value = \"Noun\"]]\n  of entry[form@tww = \"mami\", hn = 1]",
    "expect": { "status": "ok", "effects": [
      { "kind": "propertySet", "property": "definition", "language": "fr", "oldValue": null, "newValue": "Un animal" } ] } },

  { "id": "C-070", "section": "5.6", "syntax": "LiftPatchRef",
    "description": "a marked singleton step distributes over its hosts, which become the filtering step",
    "dictionary": "standard",
    "script": "set value = \"Verb\"\n  on each category\n  of sense[exists(gloss@en)]\n  of entry[form@tww = \"mami\", hn = 1]",
    "expect": { "status": "ok", "effects": [
      { "kind": "propertyReplaced", "property": "value", "language": null, "oldValue": "Noun", "newValue": "Verb" },
      { "kind": "propertyReplaced", "property": "value", "language": null, "oldValue": "Noun", "newValue": "Verb" } ] } },

  { "id": "C-071", "section": "5.3.4", "syntax": "LiftPatchRef",
    "description": "a typed component is selected by its type key",
    "dictionary": "standard",
    "script": "create note(type = \"general\", text@en = \"recorded at Yakoro\") under entry[form@tww = \"memi\"]\nset text@en = \"recorded at Yakoro, 2025\" on note^general of entry[form@tww = \"memi\"]",
    "expect": { "status": "ok", "effects": [
      { "kind": "componentCreated", "componentType": "note", "position": null },
      { "kind": "propertySet", "property": "type", "language": null, "oldValue": null, "newValue": "general" },
      { "kind": "propertySet", "property": "text", "language": "en", "oldValue": null, "newValue": "recorded at Yakoro" },
      { "kind": "propertyReplaced", "property": "text", "language": "en", "oldValue": "recorded at Yakoro", "newValue": "recorded at Yakoro, 2025" } ] } },

  { "id": "C-072", "section": "5.3.4", "syntax": "LiftPatchRef",
    "description": "a type key that matches no sibling is NOT_FOUND, like any other selector that matched nothing",
    "dictionary": "standard",
    "script": "delete note^general under entry[form@tww = \"memi\"]",
    "expect": { "status": "error", "code": "NOT_FOUND", "kind": "dynamic", "commandIndex": 1 } },

  { "id": "C-073", "section": "5.3.4", "syntax": "LiftPatchRef",
    "description": "a type key on a component type that is not typed is rejected statically",
    "dictionary": "standard",
    "script": "delete example^free under sense[gloss@en = \"pig\"] of entry[form@tww = \"mami\", hn = 1]",
    "expect": { "status": "error", "code": "COMPONENT_NOT_TYPED", "kind": "static", "commandIndex": 1 } },

  { "id": "C-074", "section": "5.3.3", "syntax": "LiftPatchRef",
    "description": "an ordinal on a typed component type is rejected statically: a map has no first element",
    "dictionary": "standard",
    "script": "delete translation#1 under example#1 of sense[gloss@en = \"pig\"] of entry[form@tww = \"mami\", hn = 1]",
    "expect": { "status": "error", "code": "COMPONENT_NOT_ORDERED", "kind": "static", "commandIndex": 1 } },

  { "id": "C-075", "section": "8.5", "syntax": "LiftPatchRef",
    "description": "move applies to ordered components only",
    "dictionary": "standard",
    "script": "move translation[type = \"free\"]\n  under example#1 of sense[gloss@en = \"pig\"] of entry[form@tww = \"mami\", hn = 1]\n  at beginning",
    "expect": { "status": "error", "code": "COMPONENT_NOT_ORDERED", "kind": "static", "commandIndex": 1 } },

  { "id": "C-076", "section": "6.2", "syntax": "LiftPatchRef",
    "description": "an at clause on a typed component is rejected statically",
    "dictionary": "standard",
    "script": "create note(type = \"general\", text@en = \"…\") under entry[form@tww = \"memi\"] at beginning",
    "expect": { "status": "error", "code": "COMPONENT_NOT_ORDERED", "kind": "static", "commandIndex": 1 } },

  { "id": "C-077", "section": "Part 3, 4.2.1", "syntax": "LiftPatchShort",
    "description": "the concise syntax writes the type key and the singleton step the same way",
    "dictionary": "standard",
    "script": "s /e[f=\"mami\", hn=1]!s[g=\"pig\"]/c (v = \"Verb\")",
    "expect": { "status": "ok", "effects": [
      { "kind": "propertyReplaced", "property": "value", "language": null, "oldValue": "Noun", "newValue": "Verb" } ] } },

  { "id": "C-078", "section": "Part 3, 4.2.1", "syntax": "LiftPatchShort",
    "description": "a concise type key selects a typed component",
    "dictionary": "standard",
    "script": "c /memi n(y=\"general\", t@en = \"recorded at Yakoro\")\nu /memi n^general (t@en = \"recorded at Yakoro, 2025\")",
    "expect": { "status": "ok", "effects": [
      { "kind": "componentCreated", "componentType": "note", "position": null },
      { "kind": "propertySet", "property": "type", "language": null, "oldValue": null, "newValue": "general" },
      { "kind": "propertySet", "property": "text", "language": "en", "oldValue": null, "newValue": "recorded at Yakoro" },
      { "kind": "propertyReplaced", "property": "text", "language": "en", "oldValue": "recorded at Yakoro", "newValue": "recorded at Yakoro, 2025" } ] } },

  { "id": "C-079", "section": "Part 3, 2", "syntax": "LiftPatchShort",
    "description": "a quoted step is always an abbreviated step, a bare singleton name never is",
    "dictionary": "standard",
    "script": "d /e[f=\"mami\", hn=1]!s[g=\"pig\"] \"c\"",
    "expect": { "status": "error", "code": "NOT_FOUND", "kind": "dynamic", "commandIndex": 1 } },

  { "id": "C-080", "section": "Part 3, 4.2.1", "syntax": "LiftPatchShort",
    "description": "a singleton cannot be deleted in the concise syntax either",
    "dictionary": "standard",
    "script": "d /e[f=\"mami\", hn=1]!s[g=\"pig\"] c",
    "expect": { "status": "error", "code": "SINGLETON_CANNOT_BE_CREATED_OR_DELETED", "kind": "static", "commandIndex": 1 } }
]
```

`SYNTAX_ERROR` in case C-016 is the generic code for the syntax errors listed at
the end of Appendix B.1; an implementation MAY report a more specific code of its
own in addition, and MUST report `SYNTAX_ERROR` for comparison purposes.

### D.4 Keeping the corpus and the document in step

Three rules keep this appendix honest:

1. **Every normative example of Parts 1 to 3 SHOULD appear in the corpus**, with its stated outcome. An example that no case covers is an example nobody has checked.
2. **A change to this specification that changes an outcome MUST change the corpus in the same edit.** The corpus is the part of this document that can be run, and it is therefore the part that decides, in practice, what the language is.
3. **Every code of Appendix B SHOULD be raised by at least one case**, and every warning of B.4 reported by at least one. A code that no case exercises is a code against which no implementation has ever been checked, and it is usually a code that turns out to name a condition another code already names. The corpus of version 1.0 does not yet reach every code; extending it is the first thing to do to it.

An implementation reports its corpus result as a table of `id` and pass/fail,
and a conformance claim cites the corpus version it passed.
