/**
 * An implementation of the LiftPatch mutation language for the LIFT dictionary
 * data model.
 *
 * <p>{@link fr.cnrs.lacito.liftpatchbox.LiftPatchBox} is the front door: it
 * parses a script in either surface syntax, runs the static validation pass, and
 * either plans the script against a dictionary or applies it in one
 * transaction.</p>
 *
 * <p>The packages divide the work along the lines the specification itself draws:</p>
 *
 * <ul>
 *   <li>{@code metamodel} — the normative metamodel of Appendix A, loaded from the
 *       very JSON document the specification prints, and the short-code tables of
 *       the concise syntax;</li>
 *   <li>{@code ast} — one immutable command model shared by both syntaxes, and the
 *       fluent API that builds it, which the two visitors and application code use
 *       alike;</li>
 *   <li>{@code parser} — the two ANTLR grammars, the concise-syntax preprocessor
 *       that applies the rules no token grammar can express, and the visitors;</li>
 *   <li>{@code validation} — every error that depends only on the script text and
 *       the metamodel, collected rather than thrown, so that a script is reported
 *       in full before anything is applied;</li>
 *   <li>{@code engine} — selector resolution, execution, the plan document of
 *       Part 2, section 12.4.1, and the run summary;</li>
 *   <li>{@code model} — the one place that knows how the metamodel maps onto the
 *       {@code lift-api} dictionary model, and the journal that makes a script
 *       atomic;</li>
 *   <li>{@code error} — the codes of Appendix B, with their kinds;</li>
 *   <li>{@code cli} — the command-line tool.</li>
 * </ul>
 */
package fr.cnrs.lacito.liftpatchbox;
