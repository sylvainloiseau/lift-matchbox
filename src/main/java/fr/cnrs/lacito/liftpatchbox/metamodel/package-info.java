/**
 * The normative metamodel of Appendix A, and the short-code tables of the
 * concise syntax.
 *
 * <p>{@link fr.cnrs.lacito.liftpatchbox.metamodel.Metamodel} is loaded from the
 * very JSON document the specification prints, bundled as a resource, so that
 * there is exactly one source of truth for component types, parentage,
 * properties, datatypes, required properties and natural identity sets. An
 * extended metamodel — a document of the same shape declaring additional types or
 * properties — is loaded by {@link fr.cnrs.lacito.liftpatchbox.metamodel.Metamodel#load(String)}
 * and is subject to the same invariants.</p>
 *
 * <p>Every rule of validation and execution is stated in terms of this object
 * rather than in terms of the particular component types it declares. The three
 * enumerations that carry the dispatch are
 * {@link fr.cnrs.lacito.liftpatchbox.metamodel.ComponentKind} — ordered, typed or
 * singleton, which decides position, ordinals, type keys and {@code move} —
 * {@link fr.cnrs.lacito.liftpatchbox.metamodel.Datatype}, and
 * {@link fr.cnrs.lacito.liftpatchbox.metamodel.PropertyRole}, the six-role
 * partition of Part 1, section 5.4.1.</p>
 */
package fr.cnrs.lacito.liftpatchbox.metamodel;
