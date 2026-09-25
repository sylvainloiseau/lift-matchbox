/**
 * The two parsers: ANTLR grammars, the concise-syntax preprocessor, and the
 * visitors that build the command model.
 *
 * <p>{@link fr.cnrs.lacito.liftpatchbox.parser.ScriptParser} is the entry point.
 * It dispatches on the surface syntax and applies the pragma rules, and both
 * paths end in the same {@link fr.cnrs.lacito.liftpatchbox.ast.Script}.</p>
 *
 * <p>The reference syntax is parsed directly by the generated
 * {@code LiftPatchRefParser}, and {@link fr.cnrs.lacito.liftpatchbox.parser.RefScriptVisitor}
 * walks the tree. The concise syntax needs one pass more, because three of its
 * rules are decided by whitespace and by the shape of a whole document rather
 * than by a token stream:
 * {@link fr.cnrs.lacito.liftpatchbox.parser.ShortLinePreprocessor} applies the
 * command-recognition rule, the sigil mode, the comment rule, the grouping and
 * the indentation, and
 * {@link fr.cnrs.lacito.liftpatchbox.parser.TopLevelSplitter} splits a command
 * line into the fragments the grammar can parse.
 * {@link fr.cnrs.lacito.liftpatchbox.parser.ShortScriptParser} then expands the
 * concise abbreviations — the reversed path, the abbreviated steps, the
 * {@code p /path} idiom, the indented block — into the reference-syntax model.</p>
 *
 * <p>Neither grammar encodes anything that depends on the metamodel. A parse tree
 * both accept may still be rejected as a static error, which is what Appendix C
 * prescribes and what keeps the grammars small and the error messages
 * specific.</p>
 */
package fr.cnrs.lacito.liftpatchbox.parser;
