/**
 * The command model: one immutable representation of a LiftPatch script, shared
 * by the two surface syntaxes, and the fluent API that builds it.
 *
 * <p>Both parsers produce exactly these objects. A LiftPatchShort path, written
 * ancestor first, is reversed by its visitor into the child-first
 * {@link fr.cnrs.lacito.liftpatchbox.ast.Chain} the reference syntax writes, and
 * the concise abbreviations are expanded, so that validation, resolution,
 * execution and plan mode are written once and are the same for both
 * syntaxes.</p>
 *
 * <p>{@link fr.cnrs.lacito.liftpatchbox.ast.LiftPatch} is the single entry point
 * for building commands. It is what the two visitors call, and it is equally
 * usable from application code that wants to drive the engine without writing a
 * script. The builders perform no validation of their own: a programmatically
 * built command is checked by the same semantic validator, against the same
 * metamodel, and is rejected with the same codes as a parsed one.</p>
 *
 * <p>Every node carries a {@link fr.cnrs.lacito.liftpatchbox.error.SourcePosition},
 * filled in by the visitors from the ANTLR tokens, so that an error can be
 * reported against the line the user wrote.</p>
 */
package fr.cnrs.lacito.liftpatchbox.ast;
