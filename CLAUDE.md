# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repository currently is

This repository is presently a **specification-writing project**, not a buildable codebase. It defines LiftPatch,
a mutation/query DSL for editing dictionaries in the LIFT (Lexicon Interchange Format) model used by SIL Fieldworks
and Elan. LiftPatch has two surface syntaxes sharing one semantics:

- **LiftPatchRef** — a verbose, unambiguous reference syntax (`create entry[form="mami"] { create sense[gloss="pig"] }`).
- **LiftPatchShort** — a concise, line-oriented syntax for fast note-taking (`c e("mami", s("pig"))`, `u /mami/pig`).

## The authoritative document

**`specification.md`** is the single source of truth for the language. It is organized as:

- Part 1 — Data model and semantics (§2–6): the LIFT dictionary model, components/parentage, properties, selection
  and natural identity, creation/initializers.
- Part 2 — The LiftPatchRef reference syntax (§7–12): command structure, component commands (`create`, `upsert`,
  `ensure`, `delete`, `move`), property commands (`set`, `update`, `clear`), reference properties, blocks, execution
  model (atomicity, static/dynamic errors, plan mode).
- Part 3 — The LiftPatchShort concise syntax (§1–9): short codes, command syntax, paths, abbreviations, the
  `p /form/gloss` idiom.
- Appendices — the normative metamodel (A), error codes (B), ANTLR-style grammars for both syntaxes (C), and a
  conformance corpus of worked examples (D).

## Working with `specification.md`

Grammars for both surface syntaxes live in Appendix C (§C.1 LiftPatchRef, §C.2 LiftPatchShort); the conformance
corpus in Appendix D contains runnable example commands fenced as ` ```LiftPatchShort ` / ` ```LiftPatchRef ` blocks
that double as test cases if/when an implementation is rebuilt.
