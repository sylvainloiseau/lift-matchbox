/**
 * Selector resolution, execution, and the plan document.
 *
 * <p>{@link fr.cnrs.lacito.liftpatchbox.engine.Resolver} turns a chain into the
 * components it designates, applying the cardinality rules level by level: which
 * levels must yield exactly one component is decided by the axis of each link,
 * and a unique selector together with the existential chain above it forms a
 * single group whose <em>result</em> must be unique.</p>
 *
 * <p>{@link fr.cnrs.lacito.liftpatchbox.engine.Executor} runs the commands in
 * source order, each seeing the state the preceding ones left, and produces a
 * {@link fr.cnrs.lacito.liftpatchbox.engine.Plan}. Plan mode is the same run with
 * the same journal, rolled back at the end instead of committed, which is what
 * guarantees that the effects a plan reports and the effects applying the script
 * produces are the same list.</p>
 */
package fr.cnrs.lacito.liftpatchbox.engine;
