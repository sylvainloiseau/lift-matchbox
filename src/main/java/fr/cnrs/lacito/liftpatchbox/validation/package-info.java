/**
 * The static validation pass.
 *
 * <p>{@link fr.cnrs.lacito.liftpatchbox.validation.SemanticValidator} reports
 * every error of a script that depends only on its text and on the metamodel. It
 * collects rather than throws, because Part 2, section 12.3 requires an
 * implementation to report <em>every</em> static error before applying any
 * command, and because a lexicographer fixing a long script would rather see the
 * whole list than one error per run.</p>
 *
 * <p>The order in which a step is checked is the one Part 1, section 3.1 fixes:
 * the component kind first, because it decides what the step may contain at all,
 * and only then the ordinary selector rules.</p>
 */
package fr.cnrs.lacito.liftpatchbox.validation;
