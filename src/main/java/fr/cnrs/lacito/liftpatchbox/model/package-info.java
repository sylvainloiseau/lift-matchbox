/**
 * The bridge to the {@code lift-api} dictionary model.
 *
 * <p>{@link fr.cnrs.lacito.liftpatchbox.model.LiftModel} is the only class that
 * knows how a LiftPatch component type, property and language map onto
 * {@code LiftSense}, {@code MultiText} and the rest. Everything above it speaks
 * only of the metamodel, which is what lets the engine be written once and
 * confines to one file the handful of places where the two models do not line
 * up; those are listed in that class's documentation.</p>
 *
 * <p>{@link fr.cnrs.lacito.liftpatchbox.model.Journal} records the inverse of
 * every mutation, which is how a script is made atomic over a dictionary that
 * offers no transaction of its own — and without copying a file that is routinely
 * tens of megabytes.</p>
 */
package fr.cnrs.lacito.liftpatchbox.model;
