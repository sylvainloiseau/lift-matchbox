
# LiftPatch: a Mutation Language (DSL) for the Lift datamodel

This document defines a command DSL, called *LiftPatch*, for creating, updating, deleting, upserting, and moving components and properties in a dictionary based on the LIFT data model. The *LiftPatch* language is not a serialization format: it is a mutation command language for updating the dictionary content. The language preserves the LIFT component hierarchy while making component identity, parentage, property availability, creation, selection, and mutation semantics explicit.

The LIFT dictionary format is intended for the linguistic description of the
lexicon of a language. It a tree-like structure; it contains *components*, such
as `entry`, `sense`, or `example`, which are nodes in the tree, and *properties*
that are attached to the components, such as the `form` (on an `entry` component),  `gloss` or `definition` (on a `sense` component), etc.

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
12. `ensure` **asserts** that a component exists. It never creates anything and never modifies anything; it fails if the component is absent.
13. A command, a block, and a whole script each either succeed completely or have no effect.
14. A command operates on exactly one component unless an explicit multiplicity keyword (`each`, `all`) is written. Multiplicity is never implicit.
15. A component created by a command may be given a label (`as $name`) so that later commands in the same script can refer to it without selecting it again.

### 1.1 Language version

A script MAY declare the version of the language it targets with a version pragma. When present, the pragma MUST be the first non-blank, non-comment line of the file:

```text
%liftpatch 1.0
```

The pragma has the same form in both surface syntaxes. A script without a pragma is processed as `1.0`. An implementation that does not support the declared version MUST reject the script with `UNSUPPORTED_LANGUAGE_VERSION`.

The pragma may carry named attributes after the version number, written
`name="value"` and separated by whitespace. Two are defined in version 1.0:

- `sigil`, meaningful in LiftPatchShort documents only, which declares the line prefix that marks a command (Part 3, section "1.1");
- `metamodel`, which names the metamodel the script requires (Appendix A). When it is present and does not name the metamodel the implementation has loaded, the script is rejected with `UNSUPPORTED_METAMODEL`. When it is absent, the script is validated against the metamodel of Appendix A.

```text
%liftpatch 1.0 sigil=">"
%liftpatch 1.0 metamodel="tww-project-2"
```

An unknown attribute is rejected with `UNKNOWN_PRAGMA_ATTRIBUTE`.

### The two liftPatch language surface syntaxes

The liftPatch language comes with two syntaxes. The two 
syntaxes have exactly the same semantics. They differ only in surface syntax:

1/ The first syntax, the *LiftPatch reference syntax* (short:
*LiftPatchRef*).

It is a verbose and explicit language.

LiftPatch reference commands are contained in a script file, "Lift patch reference script". This file contains only:

- commands that start on their own line with one of the eight verbs (set, upsert, update, clear, delete, ensure, create, move).
- *block headers*, together with their opening `{` and their closing `}` line (see section "11. Block construct").
- scope directives: `language-default`, `language-create`, and the `with` block (see section "2.1 Default languages").
- the optional version pragma `%liftpatch 1.0` (see section "1.1 Language version").
- comments. A comment starts with an unquoted `#` and runs to the end of the physical line. A comment may occupy a whole line, or follow a command on the same line (*inline comment*). A `#` occurring inside a quoted string is an ordinary character, not a comment marker.

2/ The second syntax, the *LiftPatch short language* (short: *LiftPatchShort*) is more concise.

It is intended for lexicographers expressing lexical information to be ingested in a dictionary.

The LiftPatchShort commands are expressed on a single line. It can be mixed with other content and non-LiftPatchShort commands in a file.

LiftPatchShort commands are:

- lines starting with optional whitespace followed by one of the single-letter command abbreviations, followed by whitespace and by a second token of a constrained shape. The recognition rule is normative and is given in Part 3, section "1.1"; a one-letter prefix alone is deliberately *not* sufficient, so that ordinary prose beginning with "c " or "s " is not mistaken for a command.
- special instructions `language-default` and `language-create` 
- the optional version pragma `%liftpatch 1.0`, on the first non-blank line of the document.

In both syntaxes, string are quoted by single or double quotes; a single quote is escaped as '' in a single-quoted string; double quote is escaped as \" in a double-quoted string.

### Structure of this document

- Part 1 describes the LIFT dictionary data model and the semantics of the LiftPatch language.
- Part 2 describes the *LiftPatchRef* reference syntax.
- Part 3 describes the *LiftPatchShort* concise syntax.
- Appendix A gives the normative metamodel — component types, parentage, properties, required properties and natural identity — in a machine-readable form. Where a table of Part 1 and Appendix A diverge, Appendix A prevails.
- Appendix B lists every error code with its kind, static or dynamic, and the codes withdrawn from earlier drafts.
- Appendix C gives the EBNF grammar of each of the two surface syntaxes.
- Appendix D gives the conformance corpus: a machine-readable set of cases, each with a dictionary, a script and the expected outcome, which an implementation must pass to claim conformance.

# Part 1. Lift Dictionary and LiftPatch DSL Language semantics

## 2 LIFT Dictionary

A lift dictionary is a list of `entry` components.

The order of that list is **maintained by the dictionary, not by LiftPatch**: it
is typically an insertion or a collation order, and this language neither reads
it nor changes it. Three rules of the language follow from this, and are stated
again where they apply:

- an `entry` takes no `at POSITION` clause when it is created or upserted (section "6.2");
- `move entry[...]` is a syntax error (section "8.5");
- an `entry` cannot be selected by an ordinal (section "5.3.3").

Every other component type does live in an ordered list — the list of its
same-type siblings under one parent — and is positionable by all three means.

Since a lift dictionary is not a monolingual dictionary, it also has:

- an ordered list of object languages, i.e. one or more languages that are described in the dictionary. These are, for instance, the languages represented in the `entry` `form` or in the `example` `text`. The list cannot be empty.
- an ordered list of meta languages, i.e. one or more languages that are used to describe the object language. These are, for instance, the languages used in the `gloss` and the `definition` properties of a `sense`, or the `translation` of the `example`. The list cannot be empty.

## 2.1. Default languages

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
"5.5.3 Clearing").

### 2.1.1 The `language-default` directive

The two following directives set the default language from a LiftPatch script:

```text
language-default object = "tww"
language-default meta = "en"
```

New languages cannot be declared that way. A meta (resp. object) language name
referred to by this directive must exist in the dictionary's meta (respectively, object) language list. `NO_SUCH_META_LANGUAGE` (resp. `NO_SUCH_OBJECT_LANGUAGE`) MUST be raised if the language name mentioned in the directive is not found.

The scope of a `language-default` directive is:

- the remainder of the enclosing block, if the directive occurs inside a block (see section "11. Block construct"), including any nested block, and *not* beyond the closing brace of that block;
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
- A `with` block is a scope, not a transaction of its own: it takes part in the atomicity of the enclosing block or script (see section "11").
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

## 3. LIFT components

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
 
This table is exhaustive.

- An `entry` is a top-level component.
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
| Trait | `annotation`, `field` |
| Illustration | `annotation`, `field` |
| Media | `annotation`, `field` |
| Annotation | |
| Note | `annotation` |
| Field | `annotation` |
| Translation |  |

This table is exhaustive: every component type of the component table appears as
a row, and a row with an empty right-hand cell denotes a component type that
cannot have any child. A component may only be created or moved below a
parent listed in the table.

Creating a component under an illegal parent (or moving a component towards an
illegal parent) must fail with an 'ILLEGAL_PARENT' error.

All parents can have multiple child components of the same type. For
instance, a `sense` component can have multiple `example` children. Under a
given parent, the children of each type form an ordered list; a child can be
selected by its position **in the list of its same-type siblings** — not by its
position among all the children of the parent — as described below in section
"5.3.3 Ordinal selectors".

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
    - Reference (a string containing the ID of an `entry` or `sense`),
    - URL (a string containing an URL).
  - Non scalar: multitext, which is a Map containing several strings associated to keys that are a lang code (more on this below, section "4.2. The multitext datatype")
- `Qualifier`: a multitext property value is qualified if a language name is also specified. A multitext property may contain several language-qualified values, but each language may occur at most once.
  - `O` — object language qualifier required or defaultable, for example `form@tww`.
  - `M` — meta language qualifier required or defaultable, for example `gloss@en`.
  - `—` — no language qualifier.
- `Required at creation`: indicate whether this property must be initialized with a value at creation. For a multitext, this means that at least one qualified value must be set.
- `Natural identity`: indicate if this property belongs to the *natural identity property set* of the component type, as defined in section "5.2 Natural identity". A component type has a natural identity property set of zero, one, or two properties. When a multitext belongs to that set, only *same-language* values are compared: a qualified value `p@L` of one component is compared with the qualified value `p@L` of a sibling for the same language `L`, never with a value in another language, and unset qualified values never take part in the comparison.

The table is exhaustive and normative for semantic validation. It is the
human-readable rendering of the normative metamodel given in "Appendix A. The
normative metamodel"; in case of divergence, Appendix A prevails.

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
for the `language-default` directive and the `with` block (section "2.1"). The
code `ILLEGAL_LANGUAGE` used by earlier drafts is withdrawn.

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

### 4.4 Syntax for referring to qualified values of multitext property

Whether a property accepts a language key is decided by its `Datatype` column in
the table of section "4.1": every multitext property accepts a language key, and
no scalar property does. The two lists given below are a convenience rendering
of that table (and of Appendix A); they are not an independent source of truth.

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

The following properties are multitexts and support a language key:

```text
form
definition
gloss
text
source
label
transcription
comment
```

The following properties are scalar and do not support a language key:

```text
type
morpheme
category
target
url
value
when
who
```

The pseudo-properties `hn`, `index` and `id` (section "5.3") are not properties
and never accept a language key. The pseudo-predicate `has-gloss` does accept
one, because it is a shorthand for a predicate over the multitext `gloss`
(section "5.3.2.2").

The validator MUST reject a combination of a scalar property with a language key with error `LANG_KEY_NOT_SUPPORTED_ON_SCALAR`. For example, the following are invalid:

```text
category@en
value@en
```

#### 4.4.1 The `@*` wildcard qualifier

The qualifier `@*` denotes *every* language for which the multitext has a value.
It is allowed only where a set of qualified values is meaningful:

- in `clear`, where `clear definition@*` removes all the language values of `definition` (section "5.5.3");
- in a filtering selector predicate, where:
  - `p@* = V` matches a component having the value `V` for *at least one* language (qualified value) of `p`.
  - `p@* != V` matches a component having the value `V` for *none* of the languages of `p`.
  -  `p@* ~ V` matches a component having the value `V` matching *at least one* languages (qualified value) of `p`.

`@*` is forbidden in `create`, `upsert`, `set` and `update` initializers and
targets, which must designate exactly one qualified value; the error is
`WILDCARD_NOT_ALLOWED`.

## 5. Selecting component using component identity properties in selectors and commands

### 5.1 Selector syntax

Square brackets always select existing objects. They never create an object. Parents are not implicitly created by a selector. Parent creation is explicit.

#### 5.1.1 Steps, predicates and axes

A *step* denotes one component type together with the conditions it must
satisfy:

```text
STEP        ::= COMPONENT-NAME [ '[' PREDICATE-LIST ']' ] [ ORDINAL ]
ORDINAL     ::= '#' INTEGER
PREDICATE-LIST ::= PREDICATE { ',' PREDICATE }
```

The predicates of a list are combined by conjunction: a component matches the
step when it matches *every* predicate of the list. A step may also be replaced
by a label reference (`$name`, see section "6.3 Labels").

A *chain* links a step to its ancestors through an *axis*:

```text
CHAIN ::= STEP { AXIS STEP }
AXIS  ::= '/'    (strict parent, also written `of` / `under` / `on`)
        | '//'   (existential parent, also written `within`)
```

`/` and `of`/`under`/`on` are the same axis, and `//` and `within` are the same
axis; the keyword forms are the readable aliases used throughout Part 2, the
operator forms are used in Part 3. The two forms may not be mixed inside one
chain. The semantics of the two axes are given in sections "7.2" and "7.3".

We distinguish:

- *unique selector* (or *unique selection*): selector that must match one value, which are of two sub-kinds:
  - a *parent selector*: a selector appearing in an `of`, `under`, `on` or `within` clause (equivalently, on the right of a `/` or `//` axis);
  - a *command selector*: the selector of the component directly targeted by a `delete`, `ensure`, `move` or `set`/`update`/`clear` command, or by the select branch of an `upsert` command;
- a *filtering selector*: a selector appearing on the right of a `within` axis, or inside a `has` predicate. A filtering selector is not required to select a single component by itself.

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
- `exists(p)` and `absent(p)` are exact complements: for the same `p`, exactly one of them holds on any given component. They accept a scalar property as well as a multitext one, since "the sense that has no `category`" is as ordinary a query as "the sense that has no French definition":
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

The predicates restricted to filtering selectors are excluded from unique
selection because they are, by construction, not uniquely identifying; using one
of them in a command selector or in a `/` (`of`/`under`/`on`) parent selector
raises `PREDICATE_NOT_ALLOWED_IN_UNIQUE_SELECTOR`.

#### 5.1.3 Selection strategies for unique selection


A command selector, and a parent selector on the `/` axis, are *unique selector* : it must select exactly
one component. To make this checkable statically, such a selector MUST use
exactly one of the following *selection strategies*. This list is exhaustive.

| # | Strategy | Written as | Applicable to |
|---|---|---|---|
| S1 | Label | `$name` | any component created and labelled earlier in the script |
| S2 | Persistent identifier | `[id = "…"]` | `entry` and `sense` only |
| S3 | Natural identity | all the properties of the natural identity property set of the component type, each given exactly once; a multitext identity property with exactly one language qualifier | every component type whose natural identity property set is non-empty |
| S4 | Entry lookup | a qualified `form` predicate, OPTIONALLY refined by **either** exactly one `hn` predicate **or** one or more `has` / `has-gloss` predicates | `entry` only |
| S5 | Ordinal | `#n` (deprecated alias: `[index = n]`) | every component type except `entry` |

Notes:

- S3 is not applicable to `entry`, whose natural identity property set is empty (section "5.2"); S4 replaces it. S4 is not applicable to any other component type.
- The refinements of S4 are part of S4, not separate strategies: `entry[form@tww = "mami", hn = 1]` uses one strategy, not two.
- S4 without refinement may still resolve to several entries (homophones); this raises `AMBIGUOUS_REFERENCE` like any other ambiguous selection.
- The ordinal `#n` may not be combined with any predicate list.

If a selector combines two strategies — for instance an `id` and a natural
identity property, or an ordinal and a predicate list — the validator MUST
reject it with `DUPLICATE_SELECTOR`. If it uses none — for instance an empty
predicate list, or a predicate list that does not cover the whole natural
identity property set — the validator MUST reject it with
`INCOMPLETE_SELECTOR`.

A filtering selector is under none of these constraints: see section "5.1.4".

Example:

```text
sense[id = "pig-44"]
sense[gloss@en = "pig"]
variant[type="dialectal", target = sense[id = "pig-44"]]
media[url="http://www.example.org/Image.png"]
entry[form@tww = "mami", has-gloss@en="pig"]
entry[form@tww = "mami", has sense[gloss@en = "pig"]]
example#1
```

#### 5.1.4 Filtering selectors

A filtering selector (the right-hand side of `within`, and the step inside a
`has` predicate) may contain:

- any property allowed on the component type, be it an identity property or not, in any number, with any of the predicate forms of section "5.1.2";
- no pseudo-property: `id`, `hn`, `has-gloss` and the ordinal `#n` are forbidden there, and raise `PSEUDO_PROPERTY_NOT_ALLOWED_IN_FILTER`.

A filtering selector may match zero, one, or several components; what must be
unambiguous is the result of the whole chain, not the result of each step (see
section "7.3").

#### 5.1.5 Resolution rules

A selector resolves to a set. The following rules are mandatory:

1. An empty result raises `NOT_FOUND`.
2. When a unique result is required, more than one result raises `AMBIGUOUS_REFERENCE`.
3. A command requiring one component must receive exactly one component, unless it carries an explicit multiplicity keyword (section "5.7 Multiplicity").
4. Component type and parent type compatibility are checked before dictionary lookup.
5. Selector comparisons use the declared language and datatype.
6. Selectors are re-evaluated for each command, against the state of the dictionary produced by the preceding commands of the script.

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
one. `entry` is the only component type with an empty natural identity property
set in the current metamodel; the strategies available for selecting an entry
are listed in section "5.1.3" (strategy S4) and detailed in section "5.3.2".

**Natural, not persistent.** Identity properties are not a persistent,
invariant identity: the gloss of a sense can be changed, which changes its
identity key. The persistent identity is the `id` pseudo-property (section
"5.3.1").

In the context of selection (not the context of creation of a component), i.e.
between square brackets, when a multitext identity property is mentioned, only
*one* qualified value must be given for that property.
 
Therefore, the following example will be rejected with 'DUPLICATE_SELECTOR' because the sense is selected by two qualified values:

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

#### Identity and multitext property

When a multitext property is a natural identity property, the uniqueness means that, when for each language in the relevant language set (meta or object), in the group of components that have a value set for this language and this property, there is no duplicate. Unset qualified value does not count in the identity checking.

### 5.3 Pseudo-properties used for selecting component

These pseudo properties are defined. They do not participate in component identity.

| Name | Kind | Allowed use |
|---|---|---|
| `id` | system-managed persistent identifier | selector and reference values only |
| `hn` | dictionary-assigned entry-disambiguation key | entry selector only, with qualified `form` |
| `has-gloss@M` | selector predicate, shorthand for `has sense[gloss@M = …]` | entry selector only, with qualified `form` |
| `#n` (ordinal) | positional selector, deprecated alias `index` | non-entry selector only |

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
- The `id` can be used as a lookup predicate in a command selector and in a parent selector on the `/` axis (in `of`, `under`, `on`); it is forbidden in a filtering selector (`within`, `has`).
- The `id` can be used as a lookup predicate with the `move` and `delete` commands.
- The `id` is the only way to designate a component by a value that the script did not itself choose; a component created by the running script is designated by a label instead (section "6.3 Labels").


#### 5.3.2 The case of `entry`: disambiguation of homophone entries with 'hn' and 'has-gloss'

Homophones are pervasive in language and therefore in dictionary entries. Since
entries are not grouped in small sets under parents, but are all directly under
the root, they are not easy to select.

The `form` property CAN be used alone in a selector for an `entry`, but if there are several matches (i.e. homophones), an error 'AMBIGUOUS_REFERENCE' will be raised. If there is only one match, the selector succeeds.

For `entry`, only the `id` can uniquely identify an instance by itself. However, ids are arbitrary and not very human-readable. Ids CAN be used in a selector, but are not a satisfying solution from a practical point of view.

For practical purposes, two refinements of strategy S4 are offered, which are
expressed together with the `form` property in order to disambiguate homophone
entries and select an `entry` uniquely: the `hn` pseudo-property and the `has`
child-existence predicate (of which `has-gloss` is a shorthand). Since they are a
selecting mechanism only, and not natural identity properties, both:

- can be used where unique selection takes place: a command selector (`delete`, `move`, `ensure`), a parent selector on the `/` axis (`under`, `of`, `on`), and — for `has`/`has-gloss` only — the select branch of `upsert`;
- as far as `hn` and the `has-gloss` shorthand are concerned, are forbidden in a filtering selector (`within`, or inside a `has`), where they raise `PSEUDO_PROPERTY_NOT_ALLOWED_IN_FILTER`. The general `has` predicate is not restricted in this way: it may appear in a filtering selector and may be nested inside another `has`;
- are never initializers: they cannot be written in a `create` initializer list, and they are never applied with `set` semantics by `upsert`;
- are forbidden with `hn` for `upsert` altogether (see section "5.3.2.1").

They are not natural identity properties, since they refer to a context outside
of the `entry` itself: `hn` depends on the other entries of the dictionary, and
`has` depends on the children of the entry.

##### 5.3.2.1 The homophone number (`hn`)

All homophonous entries share the same qualified form, but have a different
homophone number ('hn').

The homophone number ('hn') is not a natural identity property: on a given
`entry`, it depends on the number of other entries with the same form, which is not
a natural property of the `entry` itself. However, the form + the homophone number
('hn') allows to uniquely select an `entry` in the dictionary at any given state of the dictionary: two entries can have
the same qualified form, but no two entries can have the same value for both
the qualified form and the homophone number ('hn'). It is a contextual lookup key rather than an identity property.

The value of an `hn` pseudo-property is an Integer, without quotes.

**Model.** For the purposes of this language, `hn` is an opaque lookup key
assigned by the dictionary. The normative model is minimal and deliberately
independent of how the dictionary computes it:

1. At any moment, every `entry` of the dictionary has exactly one `hn`, an integer greater than or equal to 1. An entry that has no homophone also has an `hn`.
2. At any moment, for a given qualified form value, no two entries have the same `hn`.
3. The value of `hn` is chosen by the dictionary. The language neither assigns it nor predicts it.

**Matching rule.** A predicate `hn = N` in an entry selector matches the entry
whose qualified `form` matches the accompanying `form` predicate and whose
dictionary-assigned `hn` is exactly `N`. Whether that entry has homophones is
irrelevant: it plays no part in the matching rule. (Earlier drafts of this
specification required an error when the selected entry had no homophone; that
rule is withdrawn, because it made the validity of a selector depend on entries
that the selector does not mention, and made a script stop working when an
unrelated homophone was deleted.)

**Usage rules.**

- `hn` cannot be set by any initializer: it is managed internally by the dictionary.
- `hn` cannot be the target of a `set`, `clear` or `update` command, and cannot appear in a `create` initializer list.
- `hn` can be used in the command selector of `delete`, `move` and `ensure`, and in a parent selector on the `/` axis.
- `hn` is **forbidden in the initializer list of an `upsert`**, that is, as a predicate of the component being upserted (error `COMMAND_NOT_ALLOWING_HN`). `upsert` has a create branch, and `hn` cannot be given to a component that does not exist yet: asking to create "the entry that is the second homophone" is meaningless. Either the entry with this `hn` exists, in which case `ensure` or a plain selector applies, or it does not, in which case `create` applies. Use `has`/`has-gloss` when an `upsert` on an entry must be disambiguated (section "8.2"). `hn` remains allowed in the *parent* selector of an `upsert` command (`upsert sense(...) under entry[form@tww = "mami", hn = 1]`), which is a pure selection.
- Use of `hn` with a command that does not allow it raises `COMMAND_NOT_ALLOWING_HN`.
- `hn` is forbidden in a filtering selector (`within`, `has`): `PSEUDO_PROPERTY_NOT_ALLOWED_IN_FILTER`.

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
- if no entry matches the combination of the `form` predicate and the `hn` predicate, a `HN_NOT_EXISTING` error is raised. This is a dynamic error.

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
entry[form@tww = "mami", has sense[category = "Verb", has example[text@tww ~ "jefi"]]]
```

`has` is an existential predicate: it filters the component it is attached to,
and it never designates the matched child. To operate *on* the child, use the
`within` axis (section "7.3"), of which `has` is the predicate counterpart:
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

Another option could be to use `within`, as described in section "7.3" below:

```LiftPatchRef
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
- it is combined with the `form` predicate, and possibly with other `has` / `has-gloss` predicates, inside the single selection strategy S4 (section "5.1.3");
- if several entries satisfy it, the selector remains ambiguous and an `AMBIGUOUS_REFERENCE` error is raised.

Neither `has` nor `has-gloss` can be set by any initializer. They can be used
only in selection operations (as lookup predicates): together with the `form`
property in the command selector of `delete`, `move` and `ensure`, in a parent
selector on the `/` axis, and in the select branch of `upsert`. If the create
branch of `upsert` is executed, `has`/`has-gloss` is not taken into account and
an `entry` is created with the given form, even if it results in creating
homophones.

The use of `has-gloss` — or of a `has` predicate — with a command that does not
allow it, `create` in particular, raises `COMMAND_NOT_ALLOWING_HAS_GLOSS`. This
is the only error code for this condition.

#### 5.3.3 Ordinal selectors

A component may also be selected by its position. The ordinal is written as a
suffix on the step, introduced by `#`, and its value is an integer without
quotes:

```text
sense#2
example#1
```

Because the ordinal is a step suffix and not a predicate, it is structurally
impossible to combine it with a predicate list: `sense[gloss@en = "pig"]#2` is a
syntax error. This replaces the rule of earlier drafts, where `index` was a
predicate that no other predicate could accompany.

- The ordinal counts **same-type siblings only**: `example#2` is the second `example` child of its parent, whatever other children the parent may have.
- The ordinal starts at 1.
- The ordinal can be used with any component type except `entry`.
- The ordinal is not a property: it cannot be set by any initializer, cannot be the target of `set`, `update` or `clear`, cannot appear in a `create` initializer list, and is forbidden with `upsert` (a component that does not exist yet has no position). It can be used in the command selector of `delete`, `move` and `ensure`, and in a parent selector on the `/` axis.
- The ordinal is forbidden in a filtering selector (`within`, `has`): `PSEUDO_PROPERTY_NOT_ALLOWED_IN_FILTER`.
- If the ordinal is lower than 1, `ILLEGAL_ORDINAL` is raised (static error). If it is greater than the number of same-type siblings, `INDEX_OUT_OF_BOUNDS` is raised (dynamic error).
- Ordinals are re-evaluated for each command, against the state of the dictionary left by the preceding commands. Two consecutive `delete example#1` under the same parent therefore delete two different components.

For compatibility with earlier drafts, the predicate form `[index = n]` is
accepted as a **deprecated** alias of `#n`, with exactly the same semantics and
the same restrictions. It will be removed in a future version and SHOULD NOT be
used in new scripts.

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

| Role | `create` initializer | `upsert` initializer | selector of `ensure`, `delete`, `move`, and parent selector on the `/` axis | filtering selector (`within`, `has`) | `set` | `update` | `clear` |
|---|---|---|---|---|---|---|---|
| **R1** identity, scalar | required | required | required (part of strategy S3) | allowed | allowed, subject to the uniqueness invariant | allowed, subject to the uniqueness invariant | forbidden — `CANNOT_CLEAR_IDENTITY_PROPERTY` |
| **R2** identity, multitext | required, at least one qualified value | required, at least one qualified value | required (part of strategy S3), exactly one qualified value | allowed, any number of qualified values, `@*` allowed | exactly one qualified value, subject to the uniqueness invariant | exactly one qualified value, subject to the uniqueness invariant | `@L` only, and only if another qualified value remains; `@*` forbidden |
| **R3** required, non-identity, scalar | required | required | forbidden | allowed | allowed | allowed if set | forbidden — `CANNOT_CLEAR_REQUIRED_PROPERTY` |
| **R4** required, non-identity, multitext | required, at least one qualified value | required, at least one qualified value | forbidden, except `entry.form`, which is strategy S4 | allowed, any number of qualified values, `@*` allowed | exactly one qualified value | exactly one qualified value | `@L` only, and only if another qualified value remains; `@*` forbidden |
| **R5** optional, non-identity, scalar | allowed | allowed, `set` semantics | forbidden | allowed | allowed | allowed if set | allowed |
| **R6** optional, non-identity, multitext | allowed, one or more qualified values | allowed, `set` semantics | forbidden | allowed, any number of qualified values, `@*` allowed | exactly one qualified value | exactly one qualified value | `@L` or `@*` |

Reading rules:

- "required" means: the command is rejected with `MISSING_REQUIRED_PROPERTY` (for `create` and `upsert`) or `INCOMPLETE_SELECTOR` (for a selector) if the property is absent.
- "forbidden" in a selector column means that the predicate is rejected with `PREDICATE_NOT_ALLOWED_IN_UNIQUE_SELECTOR`: a unique selector uses exactly one selection strategy (section "5.1.3") and carries no additional predicate.
- R3 and R4 are "required" for `upsert` even though the select branch may make them unnecessary: which branch will run is not known statically, and the create branch must be able to satisfy section "6.1" rule 1.
- The `clear` column implements one invariant: **a property that the metamodel declares required may never become unset**, and an identity property may never become unset. See the decision table in section "9.3.1".

#### 5.4.2 Applicability of pseudo-properties and labels

| | `create` initializer | `upsert` | selector of `ensure`, `delete`, `move`, and parent selector on the `/` axis | filtering selector (`within`, `has`) | `set` / `update` / `clear` target |
|---|---|---|---|---|---|
| `id` | forbidden | forbidden — `ID_NOT_ALLOWED_ON_UPSERT` | allowed, alone, on `entry` and `sense` only | forbidden — `PSEUDO_PROPERTY_NOT_ALLOWED_IN_FILTER` | forbidden |
| `hn` | forbidden | forbidden in the initializer list — `COMMAND_NOT_ALLOWING_HN`; allowed in the parent selector | allowed on `entry` only, together with `form` | forbidden — `PSEUDO_PROPERTY_NOT_ALLOWED_IN_FILTER` | forbidden |
| `has-gloss` | forbidden — `COMMAND_NOT_ALLOWING_HAS_GLOSS` | select branch only, on `entry`, together with `form`; never applied in the create branch | allowed on `entry` only, together with `form` | forbidden — `PSEUDO_PROPERTY_NOT_ALLOWED_IN_FILTER` | forbidden |
| `has STEP` | forbidden — `COMMAND_NOT_ALLOWING_HAS_GLOSS` | select branch only; never applied in the create branch | allowed | allowed, including nested | forbidden |
| ordinal `#n` | forbidden | forbidden | allowed, alone, on every component type except `entry` | forbidden — `PSEUDO_PROPERTY_NOT_ALLOWED_IN_FILTER` | forbidden |
| label `$name` | not a predicate; introduced by `as $name` | not a predicate; introduced by `as $name` | allowed, as a whole step | forbidden | allowed, as the step of the `on` clause |

#### 5.4.3 (Deprecated) Command applicability table

> **Deprecated — superseded by the tables of sections 5.5.1 and 5.5.2, to be deleted later.**
>
> The table below is kept only for comparison with earlier drafts. Its rows do
> not form a partition (an optional non-identity multitext matches three of
> them), its parent-selector column merges the `/` and `//` axes, which have
> different rules, and several of its cells are contradicted by the prose. Where
> it disagrees with sections 5.5.1 and 5.5.2, sections 5.5.1 and 5.5.2 prevail.

| Property kind | `delete` | `move` | `create` | `upsert` | `ensure` | on parent selector (in `under`, `on`, `within` clause) | `set` | `update` | `clear` |
|---|---|---|---|---|---|---|---|---|---|
| Required identity property | required | required | required | required | required | allowed | allowed, subject to uniqueness | allowed, subject to uniqueness | only if the component remains valid |
| Optional natural property | allowed | allowed | allowed | allowed; uses set semantics | forbidden | allowed only if part of identity | allowed | allowed if present | allowed |
| Required non-identity property | required | required | required | allowed | forbidden | not allowed unless explicitly defined | allowed | allowed | only if another value remains |
| Optional scalar property | forbidden | forbidden | allowed | allowed | forbidden | allowed only if declared identity | allowed | allowed if present | allowed |
| Optional multitext property | forbidden | forbidden | one or more qualifiers | one or more qualifiers | forbidden | one qualifier only if identity | one qualifier | one qualifier | one qualifier or all qualifiers |
| System-managed ID | allowed (for component where IDs are supported) alone | allowed (for component where IDs are supported) alone | forbidden | forbidden | forbidden | allowed (for component where IDs are supported); forbidden in `within` | forbidden | forbidden | forbidden |
| `hn `| allowed only with `form` | allowed only with `form` | forbidden | allowed, but used only in the selector branch, and not used as initializer in the creation branch | forbidden | entry selector only, with `form`; forbidden with `within` | not as a property target | not as a property target | forbidden |
| `has-gloss` | allowed with `form` | allowed with `form` | forbidden | allowed, but used only in the selector branch, not used as an initializer in the creation branch | forbidden | entry selector only, with `form`; forbidden in `within` | forbidden | forbidden | forbidden |
| `index` | as sole selector only | as sole selector only | forbidden | forbidden | forbidden | as sole selector only; forbidden in `within` | forbidden | forbidden | forbidden |

Here `allowed` does not mean that every property is valid on every component. Availability is first determined by the property availability table.

### 5.5 Qualifier rules

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
set category@en = "noun"
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
- The property name must be written **without a qualifier**: `form = { … }`, never `form@tww = { … }`. A qualified name raises `QUALIFIER_ON_MULTITEXT_LITERAL` — the literal carries its own language keys, and a qualifier in front of it can only contradict them. (Earlier drafts raised `DUPLICATE_PROPERTY` here, which named the wrong condition: nothing is duplicated.) The wildcard form `form@* = { … }` raises `WILDCARD_NOT_ALLOWED`.
- Repeating a language inside one literal raises `DUPLICATE_PROPERTY`.
- A multi-language literal is allowed wherever an assignment is allowed (`create`, `upsert`, `set`, `update` initializers and targets). It is **not** a selector predicate.

#### 5.5.2 Qualified selection

For selectors, an identity multitext property must use exactly one qualified value:

```text
sense[gloss@en = "pig"]
example[text@tww = "The dog ran"]
```

An unqualified multitext identity property use the default object or meta language:

```text
sense[gloss = "pig"]    # equivalent to gloss@<default-meta-language>
```

#### 5.5.3 Clearing

On a multitext property, `clear` requires an **explicit** qualifier: either a
language code, or the wildcard `@*`.

```text
clear definition@en   # remove one language value
clear definition@*    # remove all language values
clear definition      # ILLEGAL: MISSING_LANGUAGE_QUALIFIER
```

Earlier drafts gave an unqualified `clear` the meaning now written `@*`, and
had to state, as the single exception in the whole language, that the default
language does not apply to it. That exception is withdrawn: the qualifier is
mandatory, so there is nothing to default. On a scalar property, no qualifier is
allowed and none is needed:

```text
clear category
```

For a required or identity multitext property:

```text
clear gloss@en
```

is legal only if another qualified `gloss` value remains, and:

```text
clear gloss@*
```

is never legal, since it would empty the property. Both violations raise
`CANNOT_CLEAR_REQUIRED_MULTITEXT`. Then:

> A required multitext property may lose individual language values, but may never become empty.

and, symmetrically:

> A required scalar property, and any identity property, may never become unset.

The complete decision table is given in section "9.3.1".

### 5.6 Multiplicity: `each` and `all`

By default every command operates on exactly one component, and any ambiguity is
an error. This makes a whole class of ordinary editorial operations
inexpressible — "give every sense of this entry the category Noun", "delete
every example of this sense". Rather than weakening the default, the language
lets a script *state* that it intends to operate on several components.

A multiplicity keyword may be written immediately before the **target step** of a
command:

```LiftPatchRef
delete all example[...] under sense[gloss@en = "pig"] of entry[form@tww = "mami"]

set category = "Noun"
  on each sense[...] 
  of entry[form@tww = "mami"]
```

- `all` and `each` are synonyms and have identical semantics. `all` reads better with `delete`, `each` with `set`, `update` and `clear`.
- A multiplicity keyword is allowed **only** on the target step of `delete`, `set`, `update` and `clear`. It is forbidden on `create`, `upsert`, `ensure` and `move`, and on every parent step, where it raises `MULTIPLICITY_NOT_ALLOWED`.
- A marked target step is a *filtering selector* (section "5.1.4"): it may carry any predicate, and it is not required to use a selection strategy. The steps above it in the chain are unaffected and keep their own rules.
- The marked step may match one or several components. Matching none raises `NOT_FOUND`, as everywhere else in the language.
- The matched components are processed in document order: for a given parent, in the order of the same-type sibling list; parents themselves in document order.
- The command remains atomic: either every application succeeds, or the command has no effect.
- Every per-component rule still applies to every application. `delete all` therefore deletes each matched component with its descendants, and `set ... on each` must preserve the uniqueness invariant for every component it touches.

Without a multiplicity keyword, a target step matching more than one component
raises `AMBIGUOUS_REFERENCE`, unchanged.

## 6. Initializers and creation syntax

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

### 6.1 Creation and initializer rules

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

- The index is 1-based and counts same-type siblings only. The valid insertion range is `1..count+1`; outside it, `INDEX_OUT_OF_BOUNDS` is raised (see 8.5).
- `at before STEP` and `at after STEP` place the component relative to an existing same-type sibling. These forms are RECOMMENDED over `at index n`, which breaks as soon as the sibling list changes.
- **When the `at` clause is omitted, the component is created at the last position**, exactly as with `at end`.
- The `at` clause is available on `create`, on `upsert` (where it applies only if the create branch runs), on an embedded initializer, and on a command written inside a block. It is mandatory on `move` (section "8.5"), which has no default.
- The `at` clause is forbidden on `ensure`, which creates nothing.
- The `at` clause is forbidden on a command targeting an `entry` — `create entry(...) at ...` and `upsert entry(...) at ...` are syntax errors. The dictionary, not the script, orders the entry list (section "2"), so there is no position for the clause to designate.

#### 6.2.1 The step of `at before` / `at after`

The `at` clause is not a selector: it is a positioning clause of `create`,
`upsert` and `move`, and it does not designate the component the command
operates on. But it contains a `STEP`, and that step is resolved like any other
step, under rules that are stated here rather than in section "5.1" because they
apply nowhere else:

- The step is resolved **among the same-type siblings under the destination parent**, and nowhere else: it is not a chain, it takes no axis, and it never leaves that sibling list.
- Its component type, when written, must be the type of the component being placed; it may also be omitted in the concise syntax (Part 3, section "5.3"). A different type raises `ILLEGAL_PARENT`.
- It must resolve to **exactly one** sibling: `NOT_FOUND` if none matches, `AMBIGUOUS_REFERENCE` if several do.
- Because it must resolve uniquely, it obeys the rules of a *command selector* (section "5.1.3"): one selection strategy, no additional predicate, and none of the filtering-only predicates (`!=`, `~`, `~i`, `exists`, `absent`), which raise `PREDICATE_NOT_ALLOWED_IN_UNIQUE_SELECTOR`. The ordinal strategy is the usual one here: `at after #2`.
- On `move`, the step is resolved **before** the moved component is removed from its list. A step that denotes the moved component itself asks for the position it already occupies, and raises `MOVING_TO_CURRENT_POSITION`.

### 6.3 Labels

A command that creates or resolves a component may bind it to a *label*, so that
later commands refer to it directly instead of selecting it again:

```text
create COMPONENT(initializers) [under PARENT] [at POSITION] as $NAME
upsert  COMPONENT(initializers) [under PARENT] [at POSITION] as $NAME
```

```LiftPatchRef
create entry(form@tww = "mami") as $newEntry

create relation(type = "synonym", target = $newEntry)
  under entry[form@tww = "memi"]
```

- A label name is written `$` followed by a letter and then letters, digits, `-` or `_`.
- A label is bound by `create` (to the created component) or by `upsert` (to the created or resolved component). No other command binds a label.
- A label may be used wherever a step is expected on the `/` axis — as a command target, as a parent in `under`, `of` or `on`, or as the value of a `reference` property. It is forbidden on the `//` axis and in filtering selectors, where it would be pointless: a label already denotes exactly one component.
- A label denotes a component, not a selector: it is not re-resolved, and it keeps denoting the same component even if the properties used to create it are afterwards modified.
- Scope: a label is visible from its binding command to the end of the enclosing block, or to the end of the script if it is bound at top level. Re-binding a visible name raises `DUPLICATE_LABEL`; using an unbound name raises `UNKNOWN_LABEL`. Both are static errors.
- If the command that binds a label is rolled back, the label is unbound.

Labels close a real gap: after `create entry(form@tww = "mami")` in a dictionary
that already contains entries with this form, the new entry has a
system-generated `id` that the script does not know, an `hn` that the script
does not control, and a form that is ambiguous. Without a label it cannot be
referred to at all.

# Part 2. The `LiftPatchRef` LiftPatch reference syntax

## 7. Core command syntax

There are five *component commands* (commands directly targeting a component):

- create
- move
- delete
- ensure
- upsert

`entry` does not have an "under PARENT" clause since an entry is at the dictionary root. `entry` cannot be moved: `move entry[...]` is a **syntax error**, rejected by the parser, not a runtime condition.

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

ensure entry[SELECTOR]
ensure COMPONENT[SELECTOR] under PARENT
```

`entry` has **no `at POSITION` clause**: the order of the entry list belongs to
the dictionary, not to the script (section "2"), which is the same reason why
`move entry[...]` is a syntax error and why an entry cannot be selected by an
ordinal. Writing `create entry(form@tww = "mami") at beginning` is a syntax
error.

`move` cannot target an `entry` component. `ensure` takes a **selector between
square brackets**, not an initializer list: it asserts, it never creates
(section "8.3"). It may target any component type, including `entry`.

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

#### 7.0.1 The eight verbs as a precondition/postcondition matrix

The five component commands differ only in what they require of the dictionary
before they run and in what they leave behind. Reading them as a matrix removes
any doubt about which one to use:

| Verb | Component absent | Component present | Creates | Modifies | Binds a label |
|---|---|---|---|---|---|
| `create` | creates it | `CANNOT_CREATE_DUPLICATE` | yes | no | yes |
| `upsert` | creates it, then applies the non-identity initializers | resolves it, then applies the non-identity initializers with `set` semantics | yes | yes | yes |
| `ensure` | `NOT_FOUND` | succeeds, changes nothing | no | no | no |
| `delete` | `NOT_FOUND` | removes it with its descendants | no | yes | no |
| `move` | `NOT_FOUND` | changes its position or its parent | no | yes | no |

And for the three property commands:

| Verb | Property unset | Property set |
|---|---|---|
| `set` | creates the value | replaces the value |
| `update` | `UNSET_QUALIFIED_PROPERTY` / `UNSET_PROPERTY` | replaces the value |
| `clear` | see the decision table of section "9.3.1" | removes the value, subject to the invariants of section "5.5.3" |

In prose:

- `create COMPONENT(...)` requires that the component does not already exist
  (according to the natural identity of the component type); if it does, the error
  `CANNOT_CREATE_DUPLICATE` is raised;
- `upsert COMPONENT(...)` creates or resolves the component;
- `ensure COMPONENT[...]` asserts that the component exists and fails with
  `NOT_FOUND` otherwise; it creates nothing and modifies nothing;
- `set PROPERTY = VALUE` creates the property if absent and replaces
  its value if present;
- `update PROPERTY = VALUE` requires an existing property and replaces
  its value; if the targeted (qualified) value is not already set, an
  `UNSET_QUALIFIED_PROPERTY` or `UNSET_PROPERTY` error is raised;
- `clear PROPERTY` removes the selected property value(s), subject to the
  decision table of section "9.3.1".

For all *property commands*, if the command refers to a property that does not exist on its parent type according to the table in section "4.1", an error 'PROPERTY_DOES_NOT_EXIST_ON_COMPONENT_TYPE' is raised.

### 7.1 Selecting direct parent with `under` or `on`

For all eight commands, PARENT refers to the parent component of the targeted property or component. The parent is identified by either the `under` or the `on` keyword, then a mandatory component name, and then a mandatory `[SELECTOR]`.

- `under` identifies the immediate parent of a component directly targeted by a `component command` (create, move, upsert, ensure, delete)
  - there is no `under` clause when the target of the command is an entry, since an entry is at the root of the hierarchy and has no parent
- `on` identifies the immediate parent of a property directly targeted by a property command (set, update, clear)

### 7.2 Selecting other ancestor with `of`

If the component selected by `under` or `on` is not an entry, further ancestor
clauses must be used, **`of` clauses or `within` clauses, in any combination**,
until an entry is reached. What is mandatory is that the chain of ancestors be
continued up to the root; which of the two keywords continues it is a separate
choice, made link by link, between strict selection (`of`, section "7.2") and
existential filtering (`within`, section "7.3").

- An additional `of` or `within` clause can qualify that parent with its own parent
- Multiple such clauses can be chained together, and the two keywords may alternate freely. Every `of` or `within` selector must be a parent of the previous component.
- either the component after `on` or `under`, or the component after the last clause of the chain must be an entry
- skipped ancestors are not allowed
- `of`, `under` and `on` are the three keyword spellings of the same axis, the strict-parent axis, written `/` in Part 3. Which keyword is used depends only on the position in the command, never on the semantics: `under` before the immediate parent of a component command, `on` before the immediate parent of a property command, `of` before every further ancestor.

#### 7.2.1 An `of` clause followed by a `within` chain

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
to the clause itself, and the requirement falls back to what section "7.2" says:
the selector alone must select exactly one component, using one selection
strategy.

The single component produced by a group is then handed to whatever stands at
the left of that group — another `of` clause, an `on`/`under` clause, or the
command itself — which resolves it under the ordinary strict-parent rule.

**What the group rule does not relax.** It relaxes the *cardinality* of the
steps, and nothing else. The selector `X` of the `of`/`under`/`on` clause is
still a selector on the `/` axis, and it keeps every rule of section "5.1.3": one
selection strategy, no additional predicate, and none of the filtering-only
predicates of section "5.1.2", which raise
`PREDICATE_NOT_ALLOWED_IN_UNIQUE_SELECTOR` there as anywhere else. Only the
selectors written *to the right of a `within`* are filtering selectors. So:

```LiftPatchRef
set category = "Verb"
  on sense[gloss@en = "pig"]        # legal: strategy S3, may match once per candidate entry
  within entry[form@tww = "mami"]

set category = "Verb"
  on sense[category = "Noun"]       # PREDICATE_NOT_ALLOWED_IN_UNIQUE_SELECTOR
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
set category = ""
  on sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

To remove a property, use `clear`:

```LiftPatchRef
clear category
  on sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

The following strict update requires the property to exist:

```LiftPatchRef
update definition@en =
  "A revised definition"
on sense[gloss@en = "pig"]
of entry[form@tww = "mami"]
```

### 7.3 Replacing `of` with `within` for existential filtering semantics

While each `of` clause explicitly selects a component among its siblings, `within` allows for a existential filtering strategy. The logic is : *Keep parent P if ∃ child C in P such that C matches selector S.*

`within` allow for intermediate multiplicity, but the final evaluation of a within chain must result in a single child component, otherwise an `AMBIGUOUS_REFERENCE` is raised. If no child component is selected, an `NOT_FOUND` error is raised.

- with `of`, a component is selected if it satisfies its own selectors. As mentioned, the selector must contain all the identity properties so that the component is selected unambiguously.
- with `within`, a component is selected if:
  - first, it belongs to one or more components selected (the selector at the right of `within` can contain any number of properties, identity property or not, that allows for several matches)
  - secondly, it matches the condition expressed in the selector at the left of the `within` selector).
  - For a chain `C0 within C1 ... within Cn`, construct the set of candidate paths `(c0, c1, ..., cn)` such that each `ci` matches its selector and each `ci` is a direct child of `c(i+1)`. This is an existential join over the chain, not independent selection of each component. The command succeeds only when exactly one complete candidate path remains; it raises `NOT_FOUND` when none remain and `AMBIGUOUS_REFERENCE` when two or more remain.
- it is then allowed to use any property in a selector targeted by within, for instance the category property on sense, that is not an identity property
- if the `within` chain result in one component, it succeed.
- If several components match, then an `AMBIGUOUS_REFERENCE` error is raised.
- If no component is kept at the end, then a `NOT_FOUND` is raised.


For instance, the following example contains a chain of two within clause 
- First, entry matching the given form are select. Several entries can be selected, say Entry-A, Entry-B and Entry-C.
- Then, only the entries having one (*or several*) sense with the given category are kept. If several senses match on a same entry, it creates several candidate-tree:
  - Lets say that Entry-C has no sense with category="Verb" and is ruled out.
  - Lets say that Entry-B has one sense with category="Verb" and is then kept. A candidate path Entry-B.Sense-A is kept.
  - Lets say that Entry-A has two senses with category="Verb". Two candidate paths are kept: Entry-A.Sense-B and Entry-A.Sense-C
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
within sense[category = "Verb"]
within entry[form@tww = "mami"]
```

#### 7.3.1 Syntax

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

#### 7.3.2 Properties allowed in a `within` selectors

The selector of a `within` clause may contain any property of the component, not only the identity properties, since a `within` clause is not responsible for uniquely and unambiguously selecting one component alone. This excludes the pseudo-predicates that are not directly manageable: 'hn', 'has-gloss', '#n', 'id'.

#### 7.3.3 Selection algorithm

Here is an example. Let's focus on the following example.

```LiftPatchRef
update value = "Animals"
on annotation[type="semantic domain", value="Animal"]
of field[type="free"]
of example[text="a mami jefi"]
within sense[category="Noun"]
within entry[form="mami"]
```

(The natural identity property set of `annotation` is `type + value`, so the
selector must give both — strategy S3, section "5.1.3" — even though the command
then rewrites `value`.)

In the previous example:

- The resolver starts with the last group (section "7.2.1"), which contains:

```text
of example[text="a mami jefi"]
within sense[category="Noun"]
within entry[form="mami"]
```

- the resolver starts with the last `within` clause: it selects all entries matching `entry[form="mami"]`. If no `entry` matches, it raises `NOT_FOUND`. Suppose that four entries have this form.
- it then moves to the left of that `within` clause, which has a selector containing `sense[category="Noun"]`. It applies this filter to the previously selected entries. Suppose that two of the four entries have a `sense` child with the category "Noun". Two candidate paths remain. Had one entry carried two such senses, that entry would have contributed two candidate paths, not one.
- the step above is repeated with the left of this last `within`, i.e. the selector `example[text="a mami jefi"]`. For each candidate path, we look for an example with the given text under its sense. If exactly one candidate path yields such an example, it is kept. If several candidate paths yield one, an `AMBIGUOUS_REFERENCE` error is raised. If none does, a `NOT_FOUND` error is raised.
- the group is now resolved to exactly one `example`. **This single component is then handed to the clause at the left of the group, `of field[type="free"]`, which resolves it under the ordinary `of` rule** (section "7.2"): the field is looked up among the children of that one example, it must be selected unambiguously by its own selection strategy, and it raises `NOT_FOUND` or `AMBIGUOUS_REFERENCE` on its own.
- the same applies in turn to `on annotation[type="semantic domain", value="Animal"]`, resolved among the children of that one `field`. The `update` command is then applied to the `value` property of that one `annotation`.

The general rule is: a `within` chain is resolved as a group and yields exactly
one component; that component is then an ordinary, uniquely resolved parent for
everything standing at the left of the group.

#### 7.3.4 Difference between `of` and `within`

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
within sense[category = "Noun"]
within entry[form@tww = "mami"]
```

#### 7.3.5 Existential filtering with the selected child

When a `within` clause is used, the parent selector is evaluated together with the already selected descendant path.

For example:

```LiftPatchRef
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

#### 7.3.6 Cardinality

The complete constrained path must resolve to exactly one valid target for commands that require one target.

The following errors apply:

- `NOT_FOUND` — no ancestor satisfies the constraint in the `within` chain 
- `AMBIGUOUS_REFERENCE` — more than one complete ancestor/descendant path satisfies it;
- `ILLEGAL_PARENT` — the selected components cannot be related according to the component hierarchy;
- `DUPLICATE_SELECTOR` — the same ancestor constraint is specified more than once or conflicts with another selector strategy.

The ancestor is valid only if exactly one complete matching descendant path exists.

#### 7.3.7 No implicit creation

A `within` clause is always a selector constraint. It never creates an ancestor or any intermediate component.

For example:

```LiftPatchRef
create note(type = "special", text@en = "Very important")
under sense[gloss@en = "foo"]
within entry[form@tww = "mami"]
```

This command does not create the entry or the sense. If either component is absent, the command fails.

#### 7.3.8 Blocks

`within` is allowed in the header of a block (see section "11" for the definition of block construct) for selecting the parent targeted by the command(s) in the block body.

#### 7.3.9 Assessment

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

### 8.1 The `create` command

The `create` command always creates a new component and does not reinterpret the
properties as a component selector.

Properties required for the creation of a component are defined in the "Required at creation" column in the property table in section "4. Lift properties".

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

#### 8.1.1 The case of entry

This is not an exception to the rule above but an instance of it. The natural
identity property set of `entry` is empty, so the uniqueness invariant of
section "5.2" is vacuous for entries and `create` has no duplicate to detect.
A component with the same value for the `form` property may therefore exist; the following
works, even if an entry already exists with the same qualified form:

```LiftPatchRef
create entry(form@tww = "mami")
```

#### 8.1.2 The `at POSITION` clause

The `create` command allows an optional `at POSITION` clause, which specifies
where to insert the component in the parent's list of same-type components. The
clause is defined once, for every command that creates a component, in section
"6.2 The `at` position clause". Allowed positions are:

```text
at beginning
at end
at index <n>
at before <STEP>
at after <STEP>
```

Examples:

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

- The index is 1-based and counts same-type siblings only.
- The valid insertion range is 1..count+1. If the index given is < 1 or greater than the number of already existing same-type components + 1, an error `INDEX_OUT_OF_BOUNDS` is raised.
- `at before` / `at after` take a step that must select exactly one same-type sibling under the destination parent; otherwise `NOT_FOUND` or `AMBIGUOUS_REFERENCE` is raised. The rules of that step are given in section "6.2.1".
- **When the clause is omitted, the component is created at the last position** (`at end`).
- The clause is **not** available when the created component is an `entry` (sections "2" and "6.2"): `create entry(...) at ...` is a syntax error, since the order of the entry list is the dictionary's and not the script's.

### 8.2 The `upsert` command

`upsert` select a component or create it if it does not exist:

```LiftPatchRef
upsert sense(gloss@en = "pig")
  under entry[form@tww = "mami", hn=1]
```

#### 8.2.1 The two branches of `upsert`

`upsert` is defined by two branches. Exactly one of them runs.

Let `T` be the component type, `P` the resolved parent, and let the parenthesized
list be split into three disjoint parts:

- `K` — the *match part*: the predicates bearing on the properties of the natural identity property set `I(T)`;
- `D` — the *disambiguation part*: the `has` / `has-gloss` predicates, which may be present only for `entry`;
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
`create` parenthesis.

Further rules:

- An `upsert` command fails with `MISSING_IDENTITY_PROPERTY` if `K` does not cover the whole natural identity property set of `T` (qualified text for an example, type + target for a variant, etc.).
- Every property that the metamodel declares required must be present in `K ∪ A`, so that the create branch can satisfy rule 1 of section "6.1"; otherwise `MISSING_REQUIRED_PROPERTY` is raised. This is a static check: which branch will run is not known before execution.
- The `at` clause, if present, applies only to the create branch; it is ignored by the select branch. It is not available at all when `T` is `entry` (section "6.2").
- `as $LABEL`, if present, binds the created component in the create branch and the resolved component in the select branch.

In the following example, the `category` property
  will be either created (if it does not exist) or updated to "Noun" (if it does
  exist).

```
upsert sense(
  gloss@en = "pig",
  category = "Noun"
)
under entry[form@tww = "mami"]
```

#### 8.2.2 `id` and `upsert`

`upsert` cannot use an `id`, since an `id` cannot be set and a component that
does not exist yet has none. Using an `id` in an `upsert` parenthesis fails with
the error `ID_NOT_ALLOWED_ON_UPSERT`.

#### 8.2.3 `upsert` on an `entry`

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
ensure entry[SELECTOR]
ensure COMPONENT[SELECTOR] under PARENT
```

Because it selects and does not create, `ensure` uses **square brackets**, like
every other selecting construct of the language, and it obeys exactly the rules
of a command selector (section "5.1.3"): one selection strategy, no extra
predicate.

```LiftPatchRef
ensure sense[gloss@en = "pig"]
  under entry[form@tww = "mami"]
```

```LiftPatchRef
ensure entry[form@tww = "mami", has-gloss@en = "pig"]
```

- If the selector matches no component, `NOT_FOUND` is raised.
- If it matches more than one, `AMBIGUOUS_REFERENCE` is raised.
- If it matches exactly one, the command succeeds and the dictionary is unchanged.
- `ensure` accepts every selection strategy, including `id`, `hn`, `has` / `has-gloss` and the ordinal `#n`. It may target any component type, `entry` included: since `ensure` does not create, the fact that `entry` has an empty natural identity property set is irrelevant to it.
- `ensure` takes no `at` clause and binds no label.

Writing `ensure sense(gloss@en = "pig")` — with parentheses — is a **syntax
error**. Earlier drafts described `ensure` as "an idempotent upsert that does not
change existing properties", which made it a creating command; that description
is withdrawn. A command that creates a component if it is missing is spelled
`upsert`.

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
descendants. Matching none still raises `NOT_FOUND`.

### 8.5 The `move` command

The `at POSITION` clause is **mandatory** on `move`: a move with no stated
destination position would have no defined meaning, and `move` is the one
command with no sensible default (appending at the end is a real change of
order, not a neutral choice). A `move` without an `at` clause is a syntax error.

Allowed positions are those of section "6.2":

```text
at beginning
at end
at index <n>
at before <STEP>
at after <STEP>
```

- The index is 1-based and counts same-type siblings only.
- `at before` / `at after` are RECOMMENDED over `at index <n>`, since they do not depend on the current length of the sibling list:

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

- moving to the current position raises `MOVING_TO_CURRENT_POSITION`
- the destination cannot contain the source component type: `ILLEGAL_PARENT`, the same code as for any other illegal parentage (section "3"); the code `INCOMPATIBLE_DESTINATION` used by earlier drafts is withdrawn
- the move would make a component its own ancestor (`SELF_ANCESTOR`);
- the requested position is outside the destination's valid range (an error `INDEX_OUT_OF_BOUNDS` is raised):
  - lower than 1 
  - greater than the number of components in the destination + 1 for a different-parent move
  - greater than the number of components in the destination for a same-parent move
Moving an `entry` is not one of these runtime conditions: `move entry[...]` is
rejected by the parser as a syntax error, since `entry` has no parent and no
sibling order to change. The code `ENTRY_CANNOT_MOVE` used by earlier drafts is
withdrawn.

A move also fails if:

- the move will create a duplicate amongst the sibling set according to natural identity properties. The validation must iterate on pre-existing siblings and check that none has the same values as the new candidate sibling for the identity properties of this type of component. If a duplicate would be created, an error 'CANNOT_CREATE_DUPLICATE' must be raised. For multitext natural identity property, it means iterating on the relevant language set languages and check that, for the multitext having a value set for this language, there is no duplicate.

The selectors will raise an exception if:

- the source does not exist;
- the destination does not exist.

The source and destination are resolved before the move is applied.

Moving changes position and parentage, never identity.

## 9. Commands operating on properties

### 9.1 The `set` command

`set` is the normal property assignment operation. It creates the target if
absent and replaces its value if present:

```LiftPatchRef
set category = "Noun"
  on sense[gloss@en = "pig"]
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

If the `set` command targets one of the natural identity properties of the component, the validation must iterate on siblings and check that none has the same values as the new candidate sibling for the identity properties of this type of component. If a duplicate would be created, an error `CANNOT_CREATE_DUPLICATE` must be raised. When the targeted natural identity property is a multitext, it means to check the values qualified with the same languages on the other sibling components.

`set` accepts the multiplicity keyword `each` on its `on` clause (section
"5.6"), and a multi-language literal as its value (section "5.5.1"):

```LiftPatchRef
set category = "Noun"
  on each sense[category != "Verb"]
  of entry[form@tww = "mami"]
```

The uniqueness invariant is then checked for every component the command
touches, and the command has no effect at all if it fails for any of them.

`set` never accepts the `@*` wildcard (`WILDCARD_NOT_ALLOWED`): assigning one
string to "all languages" is not a meaningful operation. `@*` is a reading and a
removing qualifier, not an assigning one (section "4.4.1"); to assign several
languages in one command, use a multi-language literal.

#### 9.1.1 Assigning several properties in one command

A property command may carry an **assignment list**, so that the properties of
one component are written in one command instead of one command per property:

```text
set PROPERTY = VALUE { ',' PROPERTY = VALUE } on [each] PARENT
update PROPERTY = VALUE { ',' PROPERTY = VALUE } on [each] PARENT
clear PROPERTY { ',' PROPERTY } on [each] PARENT
```

```LiftPatchRef
set category = "Noun",
    definition@en = "A four-legged terrestrial animal",
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
clear category, definition@*
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

If the `update` command target one of the natural identity properties of the component, the validation must iterate on siblings and check that none has the same values than the new candidate sibling for the identity properties of this type of component. If a duplicate would be created, an error 'CANNOT_CREATE_DUPLICATE' must be raised. When the targeted natural identity property is a multitext, it means to check the values qualified with the same languages on the other sibling components.

### 9.3 The `clear` command

#### Clear for non-identity properties

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

`clear` is the only command that may intentionally target multiple
qualified property values. Component selectors and `set`/`update` targets must
resolve to exactly one qualified value. A `@*` `clear` may then remove
several matching values, however it never removes the parent component.

On a multitext property the qualifier is mandatory: `clear definition` — with
neither a language nor `@*` — raises `MISSING_LANGUAGE_QUALIFIER`. The default
language is never applied by `clear`, because there is nothing to default.

`clear` on scalar properties removes the property value, and takes no
qualifier:

```LiftPatchRef
clear category
  on sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

`clear` accepts the multiplicity keyword `each` on its `on` clause:

```LiftPatchRef
clear definition@fr
  on each sense[exists(definition@fr)]
  of entry[form@tww = "mami"]
```

#### 9.3.1 Decision table for `clear`

The following table is normative and exhaustive. It gives, for every
combination of property role (section "5.4.1"), qualifier, and current state of
the property, either the effect of the command or the error it raises. It
supersedes the scattered rules of earlier drafts, which named three different
errors (`UNSET_PROPERTY`, `QUALIFIED_PROPERTY_NOT_FOUND`, `EMPTY_MULTITEXT`) for
overlapping situations.

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

The codes `QUALIFIED_PROPERTY_NOT_FOUND` and `EMPTY_MULTITEXT` used by earlier
drafts are withdrawn; `UNSET_QUALIFIED_PROPERTY` and `UNSET_PROPERTY`
respectively replace them.

#### Clear for identity and required scalar properties

A scalar property that belongs to the natural identity property set of the
component cannot be cleared (`CANNOT_CLEAR_IDENTITY_PROPERTY`), and neither can
a scalar property that the metamodel declares required
(`CANNOT_CLEAR_REQUIRED_PROPERTY`). Optional scalar properties can be cleared.

For instance `Illustration.url` is both required and the identity property of
`illustration`: `clear url on illustration[...]` is always refused. `Trait.value`
is required and not an identity property: `clear value on trait[...]` is refused
too. `Sense.category` is optional: it can be cleared.

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
command cannot be used with the `@*` wildcard, as it would remove the value for
all languages and leave the multitext property empty. The following two commands
raise `CANNOT_CLEAR_REQUIRED_MULTITEXT`:

```text
clear form@*
  on entry[form@tww = "mami"]
```

and:

```text
clear gloss@*
  on sense[gloss@en = "pig"]
  of entry[form@tww = "mami"]
```

3/ written with no qualifier at all, both commands raise
`MISSING_LANGUAGE_QUALIFIER` instead:

```text
clear form
  on entry[form@tww = "mami"]
```

It means that the `clear` command never uses the implicit language: on a
multitext, the qualifier must always be written.

## 10. References to other components

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
on relation[type = "synonym"]
of entry[form@tww = "mami"]
```

A relation to a sense may be written:

```LiftPatchRef
set target =
  sense[gloss@en = "pig"]
  of entry[form@tww = "memi"]
on relation[type = "associated-sense"]
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

The validator further verifies that:

- the target of a `reference` property is an entry or a sense; otherwise `INVALID_TARGET` is raised;
- the stored value of a reference resolves to an existing component when it is *assigned*; `NO_SUCH_TARGET` is raised if it does not;
- no two identical `Relation`, `Variant` or `Reversal` components exist on the same parent component (i.e. with the same type and the same target).

These rules regarding reference apply to every target property on:

- variants;
- relations;
- reversals.

When an identical component with the same type and the same target already exists,
the creation of a Variant, Relation, or Reversal component should fail with 'CANNOT_CREATE_DUPLICATE'.

## 11. Block construct

Blocks provide convenient construct for related operations while preserving explicit component construction.

A curly brace block is a syntactic construct that groups related commands together, and anchors them to a common parent.


A block consists of
- the *block header* which is given before the curly brace.
  - the block header is either:
    - a component with a selector, possibly extended by `of` / `within` clauses
    - a `create` or `upsert` command with a component and its initializer list, possibly with an `at` clause and an `as $LABEL` binding
    - an `ensure` command with a component and its selector (the assertion is evaluated first; the asserted component is then the parent of the body)
    - a `with` directive (section "2.1.2"), which binds default languages for the body without designating a parent; the parent is then the one of the enclosing block, if any
- the *block body* which is given inside the curly brace. The block body contains either:
  - another block, with its header and body: block can be nested
  - one or several commands. Those commands have neither an `under` nor an `on` clause, since the parent is given by the block header. They may carry an `at` clause, an `as $LABEL` binding, and a multiplicity keyword, exactly as at top level.
  - `language-default` directives, whose scope is then the block (section "2.1.1").
  - Note that an `upsert`, `ensure` or `update` cannot be in the scope of a `create` command, be it at the direct upper level or indirectly related.
  - **`move` cannot appear in a block body** (`MOVE_NOT_ALLOWED_IN_BLOCK`, a static error). A `move` names two parents — the source parent and the destination parent — while a block header supplies exactly one, and a block body command takes no `under` clause with which to supply the other. Rather than give `move` a special dispensation to carry one `under` clause inside a block, the language keeps the rule simple: a `move` is written at top level, where both of its parents are visible in the command itself.

The semantic of the relation between the block header and the command it contains is that of `under` and `on`: the block header is the parent of the component or property targeted by the inner commands.

The next example has a block header that contains a component with selector. It will fail with 'AMBIGUOUS_REFERENCE' or 'NOT_FOUND' if the selector failed.

```LiftPatchRef
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
    set category = "Noun"
    create example(text@tww = "a mami jefi") 
  }
}
```

The preceding command is very useful if the user want to create a new entry only if none of the existing entry with "form=mami" has the gloss given in "has-gloss". In other word, if an entry exist with that sense, we do nothing (appart updating definition and category), but if the sense does not exist, we do not want to create it on any of the existing entry having form="mami": we want to create it on a new entry.

This semantic of two embedded upsert cannot be expressed without the block syntax.

- As stated above, an `upsert`, `ensure` or `update` cannot be in the scope of a `create` command, be it at the direct upper level or indirectly related.
- For instance, in the following command, an upsert command is illegally in the scope of a `create` command at the direct upper level -- expecting that a sense exist on a newly created entry make no sense --:

```
create entry(form@tww = "mami") {
  upsert sense(gloss@en = "pig") {
    set definition@en = "A four-legged terrestrial animal"
    set category = "Noun"
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
block component as its parent. Outside a block, `on` is mandatory for property commands.

A block is also a *scope*: `language-default` directives and `as $LABEL`
bindings made inside it are visible until its closing brace and not beyond
(sections "2.1.1" and "6.3").

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

- **Static errors** depend only on the text of the script and on the metamodel: unknown component or property name, property not available on the component type, illegal parentage, wrong selection strategy, forbidden pseudo-property, missing required initializer, `ILLEGAL_HN`, `ILLEGAL_ORDINAL`, `DUPLICATE_PROPERTY`, `DUPLICATE_LABEL`, `UNKNOWN_LABEL`, and every syntax error.
- **Dynamic errors** depend on the state of the dictionary: `NOT_FOUND`, `AMBIGUOUS_REFERENCE`, `CANNOT_CREATE_DUPLICATE`, `UNSET_PROPERTY`, `UNSET_QUALIFIED_PROPERTY`, `HN_NOT_EXISTING`, `INDEX_OUT_OF_BOUNDS`, and the `clear` invariants.

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
    "propertiesSet": 1, "propertiesRemoved": 1
  }
}
```

Normative points about the plan document:

- `status` is `"ok"` when the script would apply cleanly, `"error"` otherwise. A plan with `status: "error"` still lists every operation it could resolve, so that a reader sees the whole intent, and the first failing operation carries a non-null `error`.
- `staticErrors` lists every static error of the script (section "12.3"), each with its code and position. It is non-empty only when `status` is `"error"`, and when it is non-empty no operation is resolved.
- `error`, where present, is an object `{ "code": "...", "message": "...", "position": {...} }`. `code` is one of Appendix B and is the only part an automated comparison relies on; `message` is free prose and MUST NOT be compared.
- `effects` uses exactly these `kind` values: `componentCreated`, `componentDeleted`, `componentMoved`, `propertySet`, `propertyReplaced`, `propertyRemoved`. `propertySet` is used when the value did not exist, `propertyReplaced` when it did.
- `language` is `null` for a scalar property, and is the language code for a qualified value. A `clear p@*` yields one `propertyRemoved` effect per language actually removed, in the dictionary's language-list order.
- `position` is 1-based and counts same-type siblings. On `componentCreated` it is the position the component would occupy once created; on `componentDeleted` and on the source side of `componentMoved`, it is the position the component occupies **before** the command runs, so that a `delete all` reports the positions of the original list rather than of the list as it shrinks. It is `null` for an `entry`, whose list the language does not order (section "2").
- `path` is a human-readable path from the root using the reference syntax; it is informative, and two implementations may differ in it. `id`, `componentType`, `effects`, `error.code` and `summary` are the comparable parts.
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

# Part 3. The *LiftPatchShort* LiftPatch Concise surface syntax

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
   - `/` — a path (`c /mami`, `d /e[f="mami"]/s[g="pig"]`), or
   - `$` — a label (`s $pig (c = "Noun")`), or
   - a component letter (or component name) immediately followed by `(` — a constructor with no parent path, which only `c` and `p` accept (`c e("mami")`, `p entry(f="mami", has-gloss="pig")`).

Every command of the concise syntax has a second token of one of these three
shapes: `c` and `p` take either a path or a constructor, and `d`, `e`, `m`, `s`,
`u` and `l` always take a path, which begins with `/` or with a label.
2. `language-default ` or `language-create ` followed by `object` or `meta`:

```
language-default object = "tww"
language-default meta = "en"
language-create object = "tpi"
language-create meta = "fr"
```

3. the version pragma `%liftpatch 1.0`, on the first non-blank line of the document.

The command letters are:

```text
c  d  m  p  e  u  s  l
```

Two further rules make the recognition safe in both directions:

- **A line that matches the rule but does not parse is an error, not prose.** It is reported with its position and the script fails. Silently demoting a mistyped command to prose would drop an intended edit without telling anyone.
- **Prose that matches the rule can be escaped** by prefixing the line with a backslash. A line beginning with `\` is never a command; the backslash is not part of the text.

**Strict mode: an explicit line sigil.** In a document where a false positive
would be costly — a document quoting LiftPatchShort commands as examples, for
instance — the version pragma may declare a *sigil*:

```LiftPatchShort
%liftpatch 1.0 sigil=">"
```

- The sigil is one character, declared on the pragma line, which must then be the first non-blank line of the document.
- When a sigil is declared, a line is a command **if and only if** it begins, after optional leading whitespace, with that sigil; the rest of the line is then read exactly as above, and a line matching the rules 1–3 above without the sigil is prose. The sigil is not part of the command.
- When no sigil is declared, the rules 1–3 above apply; this is the default and the common case.

```LiftPatchShort
> c /mami/pig
> s /mami/pig (c = "Noun")
```

**Comments.** A `#` preceded by whitespace, or beginning a line, starts a
comment that runs to the end of the physical line, exactly as in LiftPatchRef. A
`#` immediately following a path step, with no whitespace before it, is the
ordinal marker of section "4.2", and a `#` inside a quoted string is an ordinary
character:

```LiftPatchShort
d /e[f="mami"]/s[g="pig"]/x#1   # deletes the first example, not a comment marker
```

Comments are not required in a mixed document, where prose lines already
surround the commands; they are available for annotating a command on its own
line.

For reliable extraction from ordinary prose, commands MUST begin at the
beginning of the line or be separated from the beginning of the line only by whitespace. A command
continues until the end of that physical line.

The concise syntax does not support multiline commands. A long or complex operation must use the reference language instead.

### 1.2 What the concise syntax does not express

The concise syntax is a subset. The following constructs of the reference syntax
have no concise form, and a script that needs them must be written in
LiftPatchRef:

- the strict-parent axis on a link that is not immediately above the command target (section "4. Path specification");
- `hn` selection;
- explicit blocks and nested transactions (embedded initializers cover the common case, section "7");
- `with` language scoping;
- an `at` clause on an embedded initializer (section "7").

## 2. Single-letter designations of commands, components and properties

In concise syntax, commands, components, and properties are designated by a single-letter code. The semantics, however, remain exactly the same.

Some commands, components, and properties are abbreviated by the same letter (for instance, `e` = the `ensure` command and the `entry` component). However, the same letter is never used for two commands, for two properties, or for two components. Position alone decides which namespace a letter belongs to, and the rule is normative:

1. The first token of a command line is a **command** letter, optionally followed by `*`.
2. A letter immediately followed by `[`, `#`, or `(` and appearing either inside a path (after `/` or `//`) or as the head of an initializer is a **component** letter.
3. A letter appearing inside square brackets or inside an initializer list, and followed by (optional whitespace) `=`, by `@`, or by a closing parenthesis, is a **property** letter.
4. A letter followed by `(` inside an initializer list is a **component** letter opening an embedded initializer (section "7"); the same letter followed by `=`, possibly after whitespace, is a property letter. One token of look-ahead therefore suffices.

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
reversal. A component name — one letter or full — is recognized as such only
when it is immediately followed by `[`, `#` or `(`; a path step written
`/entry`, with nothing after it, is the bare entry form `"entry"`, exactly as
`/e` is the bare entry form `"e"` (section "6.1").

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

The short code must be followed by whitespace, or by the multiplicity marker
`*` and then whitespace.

The code for upsert is `p` because `u` is reserved for update.

**The multiplicity marker.** The keywords `each` and `all` of the reference
syntax (section "5.6" of Part 1) are written `*`, appended to the command
letter:

```LiftPatchShort
d* /e[f="mami"]/s[g="pig"]/x[t~"^draft"]
s* /e[f="mami"]/s[g="pig"] (c = "Noun")
```

- `*` is allowed only on `d`, `s`, `u` and `l`, that is on `delete`, `set`, `update` and `clear`. On `c`, `p`, `m` and `e` it raises `MULTIPLICITY_NOT_ALLOWED`.
- It marks the target step of the command — the last step of the path for `d`, the last step of the path for the property commands — exactly as `all` / `each` do in the reference syntax, with the same rules and the same errors.

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

The properties on components are the same as in LIFT-DSL language, they are referred to with a single letter code:

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
abbreviated step (section "6.1"), that is an entry designated by its bare form
or a sense designated by its bare gloss. It is defined as a shorthand:

| Written | Means |
|---|---|
| `c /<form>` | `c e(f = "<form>")` |
| `c /<form>/<gloss>` | `c /e[f = "<form>"] s(g = "<gloss>")` |

```LiftPatchShort
c /mami          # create entry(form = "mami")
c /mami/pig      # create sense(gloss = "pig") under entry[form = "mami"]
```

In the second line, the leading steps of the path keep their ordinary meaning:
they *select* and never create. `c /mami/pig` therefore requires the entry
"mami" to exist and to be unambiguous — the link immediately above the created
component is the strict axis (section "4") — and it fails with `NOT_FOUND` or
`AMBIGUOUS_REFERENCE` otherwise. Only the last step is created. To create both
at once, use `p /mami/pig` (section "8") or an embedded initializer (section
"7").

*upsert*: the upserted component is matched or created under the last selected path component:

```
p <parent_path> <component_type>(<initializers>) [at <position>] [as $<label>]
p e(<initializers>) [as $<label>]   # for entry
p <path>                                            # see section 8
```

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

The `at <position>` clause is mandatory, as in the reference syntax. `m` cannot
target an entry: `m /mami` is a syntax error.

*ensure*: the component denoted by the last step of the path is asserted to
exist. `ensure` selects and never creates, so it takes a path and no
constructor:

```
e <path>
```

```LiftPatchShort
e /e[f="mami"]/s[g="pig"]
e /mami/pig
```

Earlier drafts wrote this command `e <parent_path> <component_type>[selector]`,
with the asserted component outside the path; the two forms denote the same
thing, and the path form is the normative one. `ensure` may target any component
type, `entry` included.

2/ For properties: the properties are between parentheses, with only their name (for clear) or their name and their value separated by "=" (for set and update). One command may carry several of them, separated by commas.

*set*:

```text
s <path> (<property> = <value> {, <property> = <value>})
```

*update*

```text
u <path> (<property> = <value> {, <property> = <value>})
```

*clear*:

```text
l <path> (<property> {, <property>})
```

A list of properties in one command is the concise spelling of the assignment
list of Part 2, section "9.1.1", and has exactly its semantics: the path is
resolved once, the assignments are applied in the order written, and the command
is atomic.

```LiftPatchShort
s /mami/pig (c = "Noun", d@en = "A four-legged terrestrial animal")
l /mami/pig (c, d@*)
```

Four rules of the reference syntax apply unchanged to the parenthesized part:

- On a multitext property, `clear` requires an explicit qualifier, a language code or `@*`: `l /mami/pig (d@en)` clears one language, `l /mami/pig (d@*)` clears them all, and `l /mami/pig (d)` raises `MISSING_LANGUAGE_QUALIFIER`. On a scalar property no qualifier is written: `l /mami/pig (c)`.
- A multi-language literal is written as in the reference syntax: `s /mami (f = { tww: "mami", tpi: "pik" })`.
- Naming the same qualified property twice in one list raises `DUPLICATE_PROPERTY`.
- A property of datatype `reference` takes a **path** as its value, never a bare string:

```LiftPatchShort
s /mami/pig/r[y="synonym"] (a = /memi)
s /e[f="mami"]/r[y="synonym"] (a = /e[id="entry-42"])
```

  The path is resolved like any other path and must yield exactly one component. A label may be used instead: `(a = $newEntry)`. A bare string such as `(a = "entry-42")` is rejected with `REFERENCE_VALUE_MUST_BE_A_CHAIN`.

  The same holds **inside a selector**, where a `reference` property is compared
  to a path and not to a quoted id (Part 2, section "10"):

```LiftPatchShort
d /e[f="mami"]/v[y="dialectal", a = /memi]
```

**Labels.** A `c` or `p` command may bind the component it creates or resolves,
with `as $<label>` at the end of the line; the label is then usable in any later
path or reference value of the same document:

```LiftPatchShort
c e("mami") as $new
c /memi r(y="synonym", a = $new)
```

Example:

1/ Create a sense with gloss "pig" (in default meta language) under the existing entry with form "mami" (in default object language):

```LiftPatchShort
c /e[f="mami"] s(g="pig") 
```

2/ Delete an existing sense with gloss "pig" (in default meta language) under the existing entry  with form "mami" (in default object language):

```LiftPatchShort
d /e[f="mami"]/s[g="pig"]
```

3/ Update the property "category" of a sense:

```LiftPatchShort
u /e[f="mami"]/s[g="pig"] (c = "Verb")
```

4/ Clear the property "category" of a sense:

```LiftPatchShort
l /e[f="mami"]/s[g="pig"] (c)
```

5/ Set the property "category" of a sense:

```LiftPatchShort
s /e[f="mami"]/s[g="pig"] (c = "Noun")
```

## 4 Path specification

A path starts with a slash and is a sequence of slash-separated steps.

Each step is made of a letter indicating the component type (or the full
component name, section "2") followed by one or several predicates between square
brackets, or by an ordinal.

### 4.1 The axis of a link

A path separator is an *axis*, and the concise syntax has two, exactly as the
reference syntax (Part 1, section "5.1.1"):

| Separator | Axis | Reference-syntax keyword |
|---|---|---|
| `/` | see the positional rule below | `under` / `of` / `on`, or `within` |
| `//` | existential | `within` |

The positional rule for the default separator `/` is:

> In a path,:
> - for a component command, the link **immediately above the direct target of the command** is the strict axis (The direct target of the command is the component created or upserted by the constructor); every other link of the path is the existential axis (`within`).
> - for a property command the strict link is the `on` link between the property and its parent, so **every** path link is existential.
 
 So:

- in `c /e[f="mami"] s(g="pig")`, the target is the created sense; the link between it and the entry is strict;
- in `c /e[f="mami"]/s[g="pig"] x(t="…")`, the target is the created example; the link example–sense is strict, and the link sense–entry is existential;
- in `d /e[f="mami"]/s[g="pig"]`, the target is the sense; the link sense–entry is strict;
- in `d /e[f="mami"]/s[g="pig"]/x[t="…"]`, the target is the example; the link example–sense is strict, and the link sense–entry is existential.

Writing `//` forces the existential axis on a link that the positional rule
would make strict. It is the only way, in the concise syntax, to say "delete
that sense, wherever among the homophones of this form it may be":

```LiftPatchShort
d /e[f="mami"]//s[g="pig"]
```

The converse — forcing the strict axis on a link that the rule makes existential
— has no concise form; a script that needs it must be written in LiftPatchRef
(section "1.2").

This means that the following path, **used as the parent path of a command**:

```Path
/e[f="mami"]/s[g="pig"]
```

translates, in the reference language, into:

```Fragment
s[g="pig"]
within  e[f="mami"]
```

Because the steps above the target are linked by the existential axis, the sense
is selected out of the set of entries with a form "mami", by the existential
join described in Part 1, section "7.3": the whole path must resolve to exactly
one component, and it raises `AMBIGUOUS_REFERENCE` when it does not.

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
d /e[f="mami"]/s[g="pig"]/x#1
d /e[f="mami"]/s[g="pig"]/x[1]
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

### 4.3 Predicates inside a step

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
| `[absent(c)]` | `[absent(category)]` | filtering steps only |
| `[has s[g = "pig"]]` | `[has sense[gloss = "pig"]]` | any step, subject to Part 1, section "5.3.2" |
| `[has-gloss = "pig"]` | `[has-gloss = "pig"]` | see Part 1, section "5.3.2.2" |
| `[id = "entry-42"]` | `[id = "entry-42"]` | see Part 1, section "5.3.1" |

A *filtering step* in the concise syntax is:

- a step whose link to the step that follows it is existential — written `//`, or made existential by the positional rule of section "4.1", which is the case of every step of the path except the immediate parent of the command target;
- the target step itself, when the command carries the multiplicity marker `*`;
- any step inside a `has` predicate.

Every other step must select exactly one component, and a predicate restricted
to filtering steps that appears there — typically on the immediate parent of the
target — raises `PREDICATE_NOT_ALLOWED_IN_UNIQUE_SELECTOR`, exactly as in the
reference syntax.

```LiftPatchShort
d* /e[f ~ "^mam"]/s[g="pig"]/x[t ~i "^draft"]
```

```LiftPatchRef
delete all example[text ~i "^draft"]
  under sense[gloss@en = "pig"]
  within entry[form@tww ~ "^mam"]
```

## 5. Command syntax details

### 5.1 Create, upsert, ensure: commands with initializers

Commands with initializer are identical to the initializer + `under` in the reference language.

```LiftPatchShort
c /e[f="mami"] s(g="pig")
```

This translates into

```
create sense(gloss="pig") under
entry[form="mami"]
```

The path identifies the parent of the component created.

As in the reference language:

- ensure (e) takes no initializer at all: it is an assertion and its component is designated by the last step of a path (section "3")
- upsert (p) requires all identity properties
- the `at <position>` clause is optional on `c` and `p`, and when it is omitted the component is created at the last position, exactly as with `at end` (Part 1, section "6.2"); it is forbidden on `e`, which has no position, and on any command creating an `entry`, whose list is ordered by the dictionary (Part 1, section "2") — `c e("mami") at beginning` is a syntax error.
- an `as $<label>` binding may close a `c` or `p` command line (section "3")
- a multitext initializer may take a multi-language literal, written as in the reference syntax
- The initializer initializes the property or properties that are required for the creation of a component in the reference syntax. If two properties are required, they are separated by a comma:

```LiftPatchShort
c /e[f="mami"]/s[g="pig"] n(y="sociolinguistics", t="my note")
```

Translate into:

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

The `delete` command deletes the last step of the path.

This command:

```LiftPatchShort
d /e[f="mami"]/s[g="pig"]
```

This translate into:

```LiftPatchRef
delete sense[gloss="pig"] under
  entry[form="mami"]
```

This command:

```LiftPatchShort
d /e[f="mami"]/s[g="pig"]/x[t="a mami jefi"]
```

This translate into:

```LiftPatchRef
delete example[text="a mami jefi"]
  under sense[gloss="pig"]
  within entry[form="mami"]
```

As stated in section "4.1", the link immediately above the target — here
example–sense — is the strict axis, and every link above it is existential.

### 5.3 Move

```LiftPatchShort
m /e[f="mami"]/s[g="pig"] at index 1
```

Translate in:

```LiftPatchRef
move sense[gloss="pig"]
under entry[form="mami"]
at index 1
```

When `move` has a second path, it is equivalent to a move with an under clause in the reference syntax: it moves towards another parent.

Then, the following: 

```LiftPatchShort
m /e[f="mami"]/s[g="pig"]/x#1 /e[f="mami"]/s[g="large animal"] at end
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

The five positions of the reference syntax (Part 1, section "6.2") are all
available, with exactly the same semantics:

```text
at beginning
at end
at index <n>
at before <STEP>
at after <STEP>
```

`STEP` is an ordinary concise step — a component letter (or component name) with a predicate or an
ordinal — and it must select exactly one same-type sibling of the destination
parent:

```LiftPatchShort
m /e[f="mami"]/s[g="pig"]/x#3 at after x[t="a mami jefi"]
m /e[f="mami"]/s[g="pig"]/x#3 at before #2
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
s /e[f="mami"]/s[g="pig"] (c = "Noun")
```

Is equivalent to:

```LiftPatchRef
set category = "Noun" 
  on sense[gloss="pig"]
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
u /e[f="mami"]/s[g="pig"] (c = "Verb")
```

is equivalent to

```LiftPatchRef
update category = "Verb" 
  on sense[gloss="pig"]
  within entry[form="mami"]
```

`u` requires the targeted value to be already set, and the requirement is on the
*qualified* value: `u /mami/pig (d@en = "…")` raises `UNSET_QUALIFIED_PROPERTY`
if the sense has a definition in French only. As `update` does, `u` rejects the
wildcard `@*` (`WILDCARD_NOT_ALLOWED`); a multi-language literal is the way to
rewrite several languages in one command.

### 5.6 Clear

```LiftPatchShort
l /e[f="mami"]/s[g="pig"] (c)
```

is equivalent to

```LiftPatchRef
clear category
  on sense[gloss="pig"]
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

## 6. Simplified path and initializers

### 6.1 Dropping component name and property name for entry's `form` and sense's `gloss` in path

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
create sense[gloss="pig"]
under  entry[form="mami"]
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

Wich in turn is equivalent to the following LiftPathRef notation:

```LiftPatchShort
create example(text="A mami jefi")
under sense[gloss="pig"]
within entry[form="mami"]
```

#### 6.1.1 The bare word

The abbreviated steps of the two preceding rules are the only place where a
value may be written without quotes. What may be written there is a *bare word*,
defined positively:

```text
BARE-WORD ::= BARE-CHAR { BARE-CHAR }
BARE-CHAR ::= any character of Unicode general category L* (letter),
              M* (combining mark) or Nd (decimal digit),
            | '_' | '-' | '.'
```

Everything that is not a `BARE-CHAR` must be quoted. This includes, and is not
limited to, whitespace and the characters:

```text
/  \  "  '  $  (  )  [  ]  {  }  @  ,  :  =  #  *  !  ~
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

### 6.2 Dropping property name in initializer

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

#### 6.2.2 `Sense`

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

#### 6.2.3 `Example`

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

#### 6.2.4. Default argument for other initializer

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

The mapping is always "the required properties of the type, in the order of the
metamodel (Appendix A)", which is what makes it memorable. The three component
types with no unnamed form are exactly the three whose required properties
include a `reference`: `relation`, `variant` and `reversal` take a `type` and a
`target`, and a target is a path, not a string (section "3"). Writing them out
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

### 6.3 Specifying language code when the property name is dropped

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

## 7. Embedding initializer

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

## 8. An idiomatic construct

An last and idiomatic construct is an `upsert` command, *followed by a path that contain only an entry (with a form) and a sense (with a gloss)*, and *without the expected constructor*:

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

This last command will regularly create the `sense` on a new, non-homophonous "mami"
`entry`; it will fail if no such `entry` (or if several homophonous entries)
exist, and it will fail if the `sense` already exist. On the contrary, the
`upsert` construct will not fail if the entry+sense exist, and it will create the `sense` on a different (new) `entry` if it does not, not adding the `sense`
on an existing `entry`.

## 9. A final example

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
3. Every rule of this specification is stated in terms of the metamodel, never in terms of the particular types listed below — with the single exception of `entry`, whose special status (no parent, empty natural identity, dictionary-ordered list, `hn` and `has-gloss`) is part of the language. An extension therefore needs no change to Parts 1 to 3.

```json
{
  "liftpatchMetamodel": "1.0",
  "languageKinds": ["object", "meta"],
  "datatypes": ["string", "integer", "reference", "url", "multitext"],
  "root": "dictionary",
  "components": {
    "entry": {
      "children": ["sense", "etymology", "variant", "relation", "pronunciation",
                   "reversal", "trait", "annotation", "note", "field"],
      "naturalIdentity": [],
      "properties": {
        "form":     { "datatype": "multitext", "qualifier": "object", "required": true },
        "morpheme": { "datatype": "string",    "qualifier": null,     "required": false }
      }
    },
    "sense": {
      "children": ["sense", "example", "relation", "illustration", "reversal",
                   "trait", "annotation", "note", "field"],
      "naturalIdentity": ["gloss"],
      "properties": {
        "gloss":      { "datatype": "multitext", "qualifier": "meta", "required": true },
        "definition": { "datatype": "multitext", "qualifier": "meta", "required": false },
        "category":   { "datatype": "string",    "qualifier": null,   "required": false }
      }
    },
    "example": {
      "children": ["translation", "trait", "annotation", "field"],
      "naturalIdentity": ["text"],
      "properties": {
        "text": { "datatype": "multitext", "qualifier": "object", "required": true }
      }
    },
    "etymology": {
      "children": ["trait", "annotation", "field"],
      "naturalIdentity": ["type", "form"],
      "properties": {
        "form":   { "datatype": "multitext", "qualifier": "object", "required": true },
        "gloss":  { "datatype": "multitext", "qualifier": "meta",   "required": false },
        "source": { "datatype": "multitext", "qualifier": "meta",   "required": false },
        "type":   { "datatype": "string",    "qualifier": null,     "required": true }
      }
    },
    "variant": {
      "children": ["pronunciation", "relation", "trait", "annotation", "field"],
      "naturalIdentity": ["type", "target"],
      "properties": {
        "type":   { "datatype": "string",    "qualifier": null, "required": true },
        "target": { "datatype": "reference", "qualifier": null, "required": true }
      }
    },
    "relation": {
      "children": ["trait", "annotation", "field"],
      "naturalIdentity": ["type", "target"],
      "properties": {
        "type":   { "datatype": "string",    "qualifier": null, "required": true },
        "target": { "datatype": "reference", "qualifier": null, "required": true }
      }
    },
    "reversal": {
      "children": ["trait", "annotation", "field"],
      "naturalIdentity": ["type", "target"],
      "properties": {
        "form":   { "datatype": "multitext", "qualifier": "object", "required": false },
        "type":   { "datatype": "string",    "qualifier": null,     "required": true },
        "target": { "datatype": "reference", "qualifier": null,     "required": true }
      }
    },
    "illustration": {
      "children": ["annotation", "field"],
      "naturalIdentity": ["url"],
      "properties": {
        "url":   { "datatype": "url",       "qualifier": null,   "required": true },
        "label": { "datatype": "multitext", "qualifier": "meta", "required": false }
      }
    },
    "media": {
      "children": ["annotation", "field"],
      "naturalIdentity": ["url"],
      "properties": {
        "url":   { "datatype": "url",       "qualifier": null,   "required": true },
        "label": { "datatype": "multitext", "qualifier": "meta", "required": false }
      }
    },
    "pronunciation": {
      "children": ["media", "trait", "annotation", "field"],
      "naturalIdentity": ["transcription"],
      "properties": {
        "transcription": { "datatype": "multitext", "qualifier": "object", "required": true }
      }
    },
    "trait": {
      "children": ["annotation", "field"],
      "naturalIdentity": ["type"],
      "properties": {
        "type":  { "datatype": "string", "qualifier": null, "required": true },
        "value": { "datatype": "string", "qualifier": null, "required": true }
      }
    },
    "annotation": {
      "children": [],
      "naturalIdentity": ["type", "value"],
      "properties": {
        "type":    { "datatype": "string",    "qualifier": null,   "required": true },
        "value":   { "datatype": "string",    "qualifier": null,   "required": true },
        "comment": { "datatype": "multitext", "qualifier": "meta", "required": false },
        "when":    { "datatype": "string",    "qualifier": null,   "required": false },
        "who":     { "datatype": "string",    "qualifier": null,   "required": false }
      }
    },
    "note": {
      "children": ["annotation"],
      "naturalIdentity": ["type"],
      "properties": {
        "type": { "datatype": "string",    "qualifier": null,   "required": true },
        "text": { "datatype": "multitext", "qualifier": "meta", "required": true }
      }
    },
    "field": {
      "children": ["annotation"],
      "naturalIdentity": ["type"],
      "properties": {
        "type": { "datatype": "string",    "qualifier": null,   "required": true },
        "text": { "datatype": "multitext", "qualifier": "meta", "required": true }
      }
    },
    "translation": {
      "children": [],
      "naturalIdentity": ["type"],
      "properties": {
        "type": { "datatype": "string",    "qualifier": null,   "required": true },
        "text": { "datatype": "multitext", "qualifier": "meta", "required": true }
      }
    }
  },
  "pseudoProperties": {
    "id":         { "datatype": "string",  "scope": "entry and sense only" },
    "hn":         { "datatype": "integer", "scope": "entry only" },
    "has-gloss":  { "datatype": "multitext", "qualifier": "meta", "scope": "entry only" },
    "ordinal":    { "datatype": "integer", "scope": "every component type except entry" }
  }
}
```

Reading rules:

- `children` is the parentage relation of section "3": a component may be created or moved only under a component type that lists it in its `children`. Only `entry` may be a child of the root `dictionary`. The parent relation is the inverse of this one and is not stated separately, so that the two can never diverge.
- `qualifier` gives the language kind of a multitext (`"object"` or `"meta"`) and is `null` for every scalar property. A property with a non-null `qualifier` is exactly a property that accepts an `@L` key and the `@*` wildcard.
- `required` is *required at creation*: for a multitext, at least one qualified value.
- `naturalIdentity` is the natural identity property set `I(T)` of section "5.2". An empty set — `entry` — means that the uniqueness invariant is vacuous for that type and that `create` never rejects it as a duplicate. When a multitext belongs to the set, values are compared language by language, and unset qualified values never take part in the comparison.
- `pseudoProperties` do not belong to the data: they are selection devices, and their availability per command is given by the applicability tables of section "5.4".

## Appendix B. Error codes

Every error the language defines is listed here with its **kind**, as defined in
Part 2, section "12.3":

- **static** — the error depends only on the text of the script and on the metamodel of Appendix A. An implementation MUST report every static error of a script before applying any command, and a script containing one changes nothing;
- **dynamic** — the error depends on the state of the dictionary, and is raised while the command is being resolved or applied. A dynamic error aborts the script and rolls back every change already applied (section "12.2").

Plan mode (section "12.4") reports both kinds without changing anything.

### B.1 Static errors

| Code | Raised when | Defined in |
|---|---|---|
| `UNSUPPORTED_LANGUAGE_VERSION` | the version pragma declares a version the implementation does not support | 1.1 |
| `UNKNOWN_PRAGMA_ATTRIBUTE` | the version pragma carries an attribute other than `sigil` and `metamodel` | 1.1 |
| `UNSUPPORTED_METAMODEL` | the pragma requires a metamodel that the implementation has not loaded | 1.1, Appendix A |
| `PROPERTY_DOES_NOT_EXIST_ON_COMPONENT_TYPE` | a property is used on a component type on which Appendix A does not define it | 4.1, 7.0.1 |
| `ILLEGAL_PARENT` | a component is created or moved under a component type that Appendix A does not list as its parent | 3, 8.5 |
| `LANG_KEY_NOT_SUPPORTED_ON_SCALAR` | an `@L` or `@*` qualifier, or a multi-language literal, is written on a scalar property | 4.4, 5.6.1 |
| `MISSING_LANGUAGE_QUALIFIER` | `clear` is written on a multitext with neither a language code nor `@*` | 9.3.1 |
| `WILDCARD_NOT_ALLOWED` | `@*` is used where it is forbidden: in a `create`, `upsert`, `set` or `update` assignment | 4.4.1, 9.1, 9.2 |
| `DUPLICATE_PROPERTY` | the same qualified property is initialized or assigned twice in one command — in an initializer list, in an assignment list, or inside one multi-language literal | 6.1, 5.5.1, 9.1.1 |
| `QUALIFIER_ON_MULTITEXT_LITERAL` | a multi-language literal is assigned to a qualified property name, as in `form@tww = { … }` | 5.5.1 |
| `UNNAMED_ARGUMENT_NOT_ALLOWED` | a concise initializer gives an unnamed argument to a component type that has none, or more of them than its type allows | Part 3, 6.2.4 |
| `DUPLICATE_SELECTOR` | a selector states the same constraint twice, gives two qualified values of one identity multitext, or combines conflicting selection strategies | 5.1, 5.2 |
| `INCOMPLETE_SELECTOR` | a selector required to select exactly one component uses no valid selection strategy | 5.1.3 |
| `MISSING_REQUIRED_PROPERTY` | a `create` or `upsert` initializer list omits a property that Appendix A declares required | 6.1 |
| `MISSING_IDENTITY_PROPERTY` | an `upsert` initializer list does not cover the whole natural identity property set of the component type | 8.2 |
| `PREDICATE_NOT_ALLOWED_IN_UNIQUE_SELECTOR` | `!=`, `~`, `~i`, `exists()` or `absent()` is used in a command selector or in a `/` (`of` / `under` / `on`) parent selector | 5.1.2 |
| `PSEUDO_PROPERTY_NOT_ALLOWED_IN_FILTER` | `id`, `hn`, `has-gloss` or an ordinal is used in a filtering selector (`within`, or inside `has`) | 5.1.1, 5.3 |
| `COMMAND_NOT_ALLOWING_HN` | `hn` is used with a command that does not allow it, in particular in an `upsert` initializer list | 5.3.2.1 |
| `COMMAND_NOT_ALLOWING_HAS_GLOSS` | `has-gloss` or `has` is used with a command that does not allow it, `create` in particular | 5.3.2.2 |
| `HN_CANNOT_BE_USED_ALONE` | `hn` is used in a selector that does not also give `form` | 5.3.2.1 |
| `HAS_GLOSS_CANNOT_BE_USED_ALONE` | `has-gloss` is used in a selector that does not also give `form` | 5.3.2.2 |
| `ILLEGAL_HN` | the value given for `hn` is not an integer, or is lower than 1 | 5.3.2.1 |
| `ILLEGAL_ORDINAL` | an ordinal is lower than 1 | 5.3.3 |
| `ID_NOT_ALLOWED_ON_UPSERT` | `id` is used in an `upsert` initializer list | 8.2 |
| `UPSERT_ENTRY_WITHOUT_DISAMBIGUATION` | `upsert entry(...)` gives a `form` with no `has` or `has-gloss` predicate | 8.2 |
| `MULTIPLICITY_NOT_ALLOWED` | `each` / `all` (or `*` in LiftPatchShort) is used on a parent step, or on `create`, `upsert`, `ensure` or `move` | 5.7 |
| `DUPLICATE_LABEL` | a label name visible in the current scope is bound again | 6.3 |
| `UNKNOWN_LABEL` | a `$name` is used that no visible command has bound | 6.3 |
| `REFERENCE_VALUE_MUST_BE_A_CHAIN` | a bare string is assigned to, or compared with, a property of datatype `reference`, in a command or in a selector | 10 |
| `MOVE_NOT_ALLOWED_IN_BLOCK` | a `move` command appears in a block body | 11 |
| `CANNOT_UPSERT_ENSURE_OR_UPDATE_IN_CREATION_BLOCK` | an `upsert`, `ensure` or `update` appears, directly or indirectly, in the scope of a `create` block | 11 |
| `CANNOT_CLEAR_IDENTITY_PROPERTY` | `clear` targets a scalar property belonging to the natural identity property set | 9.3.1 |
| `CANNOT_CLEAR_REQUIRED_PROPERTY` | `clear` targets a scalar property that Appendix A declares required | 9.3.1 |
| `SYNTAX_ERROR` | the script does not parse, or uses a construct the grammar forbids in that position (see the list below) | Appendix C |

Syntax errors are static too, and are reported with their position. They include
in particular: `move entry[...]`; a `move` without an `at` clause; an `ensure`
with an initializer list instead of a selector; an `at` clause on `ensure` or on
a command creating an `entry`; and, in LiftPatchShort, a command spread over
several physical lines and a step combining an ordinal with a predicate.

### B.2 Dynamic errors

| Code | Raised when | Defined in |
|---|---|---|
| `NOT_FOUND` | a selector, a path, a value chain or an `ensure` assertion matches no component | 5.1, 8.3 |
| `AMBIGUOUS_REFERENCE` | a selector required to select exactly one component matches several | 5.1, 7.3 |
| `CANNOT_CREATE_DUPLICATE` | a `create`, `upsert`, `set`, `update` or `move` would give two same-type siblings the same values for the natural identity property set | 5.2, 8.1, 8.5 |
| `UNSET_PROPERTY` | `update` or `clear` targets a property that has no value at all | 9.2, 9.3.1 |
| `UNSET_QUALIFIED_PROPERTY` | `update` or `clear` targets a qualified value `p@L` that is not set, even though another language of `p` is | 9.2, 9.3.1 |
| `CANNOT_CLEAR_REQUIRED_MULTITEXT` | `clear` would leave a required or identity multitext with no value at all | 9.3.1 |
| `HN_NOT_EXISTING` | no entry has the requested homophone number for this form | 5.3.2.1 |
| `INDEX_OUT_OF_BOUNDS` | an ordinal or an `at index n` falls outside the valid range of the same-type sibling list | 5.3.3, 6.2, 8.5 |
| `MOVING_TO_CURRENT_POSITION` | a `move` resolves to the position the component already occupies | 8.5 |
| `SELF_ANCESTOR` | a `move` would make a component its own ancestor | 8.5 |
| `NO_SUCH_TARGET` | the id given to a `reference` property resolves to no component | 10 |
| `INVALID_TARGET` | the target of a `reference` property is neither an entry nor a sense | 10 |
| `NO_SUCH_OBJECT_LANGUAGE` | an object language code is not in the dictionary's object language list | 2.1, 4.2 |
| `NO_SUCH_META_LANGUAGE` | a meta language code is not in the dictionary's meta language list | 2.1, 4.2 |
| `LANGUAGE_ALREADY_EXISTS` | `language-create` names a language the dictionary already has | 2.1.3 |

### B.3 Withdrawn codes

These codes appeared in earlier drafts and MUST NOT be raised by a conforming
implementation. Each is replaced by a code of the tables above:

| Withdrawn code | Replaced by |
|---|---|
| `EMPTY_MULTITEXT` | `UNSET_PROPERTY` |
| `QUALIFIED_PROPERTY_NOT_FOUND` | `UNSET_QUALIFIED_PROPERTY` |
| `INCOMPATIBLE_DESTINATION` | `ILLEGAL_PARENT` |
| `DUPLICATE_COMPONENT` | `CANNOT_CREATE_DUPLICATE` |
| `ENTRY_CANNOT_MOVE` | a syntax error |
| `NEGATIVE_HN` | `ILLEGAL_HN` |
| `ILLEGAL_USE_OF_HAS_GLOSS` | `COMMAND_NOT_ALLOWING_HAS_GLOSS` |
| `ILLEGAL_LANGUAGE` | `NO_SUCH_OBJECT_LANGUAGE` / `NO_SUCH_META_LANGUAGE` |
| `OPERATOR_REQUIRE_A_MULTITEXT` | — withdrawn without replacement: `exists()` and `absent()` accept a scalar property as well as a multitext one (section "5.1.2") |

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

A single quote is escaped as `''` inside a single-quoted string, a double quote
as `\"` inside a double-quoted string. In LiftPatchRef a comment starts at any
unquoted `#`; in LiftPatchShort, at a `#` that begins a line or is preceded by
whitespace (Part 3, section "1.1").

### C.1 LiftPatchRef

```ebnf
script            ::= [ pragma ] { item }
item              ::= command | block | directive | comment
pragma            ::= '%liftpatch' version { name '=' string }
version           ::= integer '.' integer

directive         ::= ( 'language-default' | 'language-create' ) lang-kind '=' string
lang-kind         ::= 'object' | 'meta'

block             ::= block-header '{' { block-item } '}'
block-item        ::= block-command | block | directive | comment
block-command     ::= create-cmd | upsert-cmd | ensure-cmd | delete-cmd
                    | set-cmd | update-cmd | clear-cmd
block-header      ::= chain | create-cmd | upsert-cmd | ensure-cmd | with-header
with-header       ::= 'with' binding { ',' binding }
binding           ::= lang-kind '=' string

command           ::= create-cmd | upsert-cmd | ensure-cmd | delete-cmd | move-cmd
                    | set-cmd | update-cmd | clear-cmd

create-cmd        ::= 'create' constructor [ 'under' chain ] [ at-clause ] [ label-binding ]
upsert-cmd        ::= 'upsert' constructor [ 'under' chain ] [ at-clause ] [ label-binding ]
ensure-cmd        ::= 'ensure' step [ 'under' chain ]
delete-cmd        ::= 'delete' [ multiplicity ] step [ 'under' chain ]
move-cmd          ::= 'move' step 'under' chain [ 'under' chain ] at-clause

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
step              ::= component-type ( '[' selector ']' | ordinal )
                    | label-ref
ordinal           ::= '#' integer

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
                    | 'translation'
property-name     ::= 'form' | 'morpheme' | 'gloss' | 'definition' | 'category'
                    | 'text' | 'source' | 'target' | 'url' | 'label'
                    | 'transcription' | 'type' | 'value' | 'comment'
                    | 'when' | 'who'
```

Notes on the productions that carry a rule of their own:

- `index '=' integer` inside a selector is the deprecated alias of the ordinal `#n` (section "5.3.3"); it is kept for compatibility and SHOULD NOT be used in new scripts.
- Every `step` carries either a selector between square brackets or an ordinal, or else is a label: a component type written alone selects nothing and is not a step. A component type followed by parentheses is a `constructor`, not a step, and it creates instead of selecting (section "6").
- The `on` keyword closes a value chain (section "10"), which is why `on` cannot be a component name.
- `move` takes one `under` chain for the source parent and an optional second one for the destination parent; `at-clause` is mandatory. `move` is absent from `block-command`: it cannot appear in a block body (`MOVE_NOT_ALLOWED_IN_BLOCK`, section "11").
- The optional `'under'` / `'on'` clauses are omitted only inside a block, whose header supplies the parent (section "11").
- `at-clause` is forbidden when the constructor creates an `entry` (section "6.2"). The grammar cannot express that restriction, since `component-type` is one production; it is a static error.
- On a property of datatype `reference`, the `value` of an `assignment`, of an `initializer` and of a `predicate` alike must be a `chain` or a `label-ref`, never a `string` (section "10"): `REFERENCE_VALUE_MUST_BE_A_CHAIN`. The grammar admits `string` there because it does not know datatypes.

### C.2 LiftPatchShort

```ebnf
document          ::= [ pragma-line ] { line }
line              ::= command-line | prose-line
command-line      ::= [ sigil ] ( command | directive ) [ comment ] end-of-line
prose-line        ::= [ '\' ] { any-char } end-of-line
pragma-line       ::= '%liftpatch' version { name '=' string } end-of-line
sigil             ::= the one character declared by the pragma attribute `sigil`

command           ::= create-cmd | upsert-cmd | ensure-cmd | delete-cmd | move-cmd
                    | set-cmd | update-cmd | clear-cmd

create-cmd        ::= 'c' ( [ path ] constructor [ at-clause ] [ label-binding ] | path )
upsert-cmd        ::= 'p' ( [ path ] constructor [ at-clause ] [ label-binding ] | path )
ensure-cmd        ::= 'e' path
delete-cmd        ::= 'd' [ '*' ] path
move-cmd          ::= 'm' path [ path ] at-clause
set-cmd           ::= 's' [ '*' ] path '(' assignment { ',' assignment } ')'
update-cmd        ::= 'u' [ '*' ] path '(' assignment { ',' assignment } ')'
clear-cmd         ::= 'l' [ '*' ] path '(' property-ref { ',' property-ref } ')'

assignment        ::= property-ref '=' short-value
constructor       ::= component-code '(' [ short-init { ',' short-init } ] ')'
short-init        ::= property-ref '=' short-value
                    | short-value
                    | constructor
short-value       ::= string [ '@' lang ] | integer | multitext-literal
                    | path | label-ref

path              ::= label-ref { axis path-step } | axis path-step { axis path-step }
axis              ::= '/' | '//'
path-step         ::= component-code ( '[' selector ']' | ordinal | '[' integer ']' )
                    | abbreviated-step
                    | label-ref
abbreviated-step  ::= ( bare-word | string ) [ '@' lang ]
bare-word         ::= bare-char { bare-char }
bare-char         ::= unicode-letter | unicode-mark | unicode-digit
                    | '_' | '-' | '.'
ordinal           ::= '#' integer

at-clause         ::= 'at' position
position          ::= 'beginning' | 'end' | 'index' integer
                    | ( 'before' | 'after' ) ( path-step | ordinal )
label-binding     ::= 'as' label-ref
label-ref         ::= '$' name

property-ref      ::= property-code [ '@' ( lang | '*' ) ]
component-code    ::= 'e' | 's' | 'x' | 'y' | 'v' | 'r' | 'i' | 'm' | 'p' | 'l'
                    | 't' | 'a' | 'n' | 'f' | 'o' | component-type
property-code     ::= 'f' | 'm' | 'd' | 'g' | 'c' | 't' | 's' | 'a' | 'u' | 'l'
                    | 'r' | 'y' | 'v' | 'o' | 'w' | 'h' | property-name
```

`selector`, `multitext-literal`, `component-type` and `property-name` are those
of C.1, with one exception: the `hn` predicate is **not** part of the concise
syntax (Part 3, section "1.2"), and a step carrying it is a syntax error.
`component-code` and `property-code` accept the one-letter code and the full
reference name alike (Part 3, section "2").

Two productions are ambiguous on their own and are disambiguated by the rules of
Part 3, sections "1.1" and "2", which a parser MUST apply:

- `command-line` versus `prose-line`: a line is a command only if it satisfies the recognition rule of section "1.1" — command letter, optional `*`, whitespace, then a token beginning with `/`, with `$`, or with a component code immediately followed by `(` — or, when a sigil is declared, only if it begins with that sigil. A line that matches but does not parse is an error, never prose.
- `component-code` versus `abbreviated-step` and `property-code` versus a value: one character of look-ahead decides. A letter followed by `[`, `#` or `(` is a component code; a letter followed by `=`, by `@`, or by the closing parenthesis of a `clear` is a property code; anything else in a path step is a bare word, that is an entry form or a sense gloss.

## Appendix D. The conformance corpus

A specification of this size cannot be checked by reading. This appendix defines
a **conformance corpus**: a machine-readable set of cases, each giving a
dictionary, a script, and the expected outcome. An implementation claims
conformance to LiftPatch 1.0 by passing every case of the corpus.

The corpus has a second purpose, internal to this document: every example
written in Parts 1 to 3 is meant to be a legal script with a stated outcome, and
three illegal ones survived several reviews before being caught by hand. A
corpus makes that class of defect mechanical to detect.

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
{ "status": "ok", "effects": [ … ], "danglingReferences": [ … ] }
{ "status": "error", "code": "AMBIGUOUS_REFERENCE", "kind": "dynamic", "commandIndex": 1 }
```

- For an `ok` case, `effects` is the concatenation, in order, of the `effects` (or `applications`) arrays that plan mode produces for the script (section "12.4.1"), restricted to the comparable fields: `kind`, `componentType`, `property`, `language`, `oldValue`, `newValue`, `position`. An implementation passes the case when its plan document yields exactly that list, **and** when applying the script for real leaves a dictionary from which re-running plan mode yields no effect at all (idempotence of the observed state, not of the command).
- For an `error` case, the implementation passes when it raises exactly `code`, of exactly that `kind`, on exactly the command at `commandIndex` (1-based; `0` for an error carried by the script as a whole, such as a pragma error), **and** when the dictionary is unchanged afterwards (section "12.2").
- `message` text is never compared. Neither is `path`, which is informative.
- An `error` case whose `kind` is `static` additionally requires that no command of the script has been applied even partially, and that the error be reported before any dictionary access.

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
          "category": "Noun",
          "examples": [
            { "text": { "tww": "a mami jefi" },
              "translations": [ { "type": "free", "text": { "en": "I shot a pig" } } ] },
            { "text": { "tww": "a mami jefo" } }
          ]
        },
        { "id": "s-2", "gloss": { "en": "pork" }, "category": "Noun" }
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
    "script": "set category = \"Verb\"\n  on sense[gloss@en = \"taro\"]\n  within entry[form@tww = \"mami\"]",
    "expect": { "status": "ok", "effects": [
      { "kind": "propertySet", "property": "category", "language": null, "oldValue": null, "newValue": "Verb" } ] } },

  { "id": "C-005", "section": "7.3", "syntax": "LiftPatchRef",
    "description": "a within chain that leaves two candidate paths fails",
    "dictionary": "standard",
    "script": "set category = \"Verb\"\n  on sense[gloss@en = \"pig\"]\n  within entry[form@tww = \"mami\"]",
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
    "script": "upsert sense(gloss@en = \"taro\", category = \"Noun\") under entry[form@tww = \"mami\", hn = 2]",
    "expect": { "status": "ok", "effects": [
      { "kind": "propertySet", "property": "category", "language": null, "oldValue": null, "newValue": "Noun" } ] } },

  { "id": "C-013", "section": "8.2.1", "syntax": "LiftPatchRef",
    "description": "upsert create branch: the sense does not exist",
    "dictionary": "standard",
    "script": "upsert sense(gloss@en = \"yam\", category = \"Noun\") under entry[form@tww = \"mami\", hn = 2]",
    "expect": { "status": "ok", "effects": [
      { "kind": "componentCreated", "componentType": "sense", "position": 3 },
      { "kind": "propertySet", "property": "gloss", "language": "en", "oldValue": null, "newValue": "yam" },
      { "kind": "propertySet", "property": "category", "language": null, "oldValue": null, "newValue": "Noun" } ] } },

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
    "expect": { "status": "error", "code": "MOVE_NOT_ALLOWED_IN_BLOCK", "kind": "static", "commandIndex": 1 } },

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
    "script": "set category = \"Verb\", definition@fr = \"Un animal\"\n  on sense[gloss@en = \"pig\"]\n  of entry[form@tww = \"mami\", hn = 1]",
    "expect": { "status": "ok", "effects": [
      { "kind": "propertyReplaced", "property": "category", "language": null, "oldValue": "Noun", "newValue": "Verb" },
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
    "script": "s /e[f=\"mami\"]/s[g=\"taro\"] (c = \"Noun\")",
    "expect": { "status": "ok", "effects": [
      { "kind": "propertySet", "property": "category", "language": null, "oldValue": null, "newValue": "Noun" } ] } },

  { "id": "C-027", "section": "Part 3, 3", "syntax": "LiftPatchShort",
    "description": "in a component command the link above the target is strict, so the homophones are ambiguous",
    "dictionary": "standard",
    "script": "d /e[f=\"mami\"]/s[g=\"taro\"]",
    "expect": { "status": "error", "code": "AMBIGUOUS_REFERENCE", "kind": "dynamic", "commandIndex": 1 } },

  { "id": "C-028", "section": "Part 3, 8", "syntax": "LiftPatchShort",
    "description": "the idiomatic upsert creates the entry and the sense when no match exists",
    "dictionary": "standard",
    "script": "p /mimi/lizard",
    "expect": { "status": "ok", "effects": [
      { "kind": "componentCreated", "componentType": "entry", "position": null },
      { "kind": "propertySet", "property": "form", "language": "tww", "oldValue": null, "newValue": "mimi" },
      { "kind": "componentCreated", "componentType": "sense", "position": 1 },
      { "kind": "propertySet", "property": "gloss", "language": "en", "oldValue": null, "newValue": "lizard" } ] } }
]
```

`SYNTAX_ERROR` in case C-016 is the generic code for the syntax errors listed at
the end of Appendix B.1; an implementation MAY report a more specific code of its
own in addition, and MUST report `SYNTAX_ERROR` for comparison purposes.

### D.4 Keeping the corpus and the document in step

Two rules keep this appendix honest:

1. **Every normative example of Parts 1 to 3 SHOULD appear in the corpus**, with its stated outcome. An example that no case covers is an example nobody has checked.
2. **A change to this specification that changes an outcome MUST change the corpus in the same edit.** The corpus is the part of this document that can be run, and it is therefore the part that decides, in practice, what the language is.

An implementation reports its corpus result as a table of `id` and pass/fail,
and a conformance claim cites the corpus version it passed.
