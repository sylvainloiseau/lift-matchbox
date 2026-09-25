/**
 * The error vocabulary of the LiftPatch language.
 *
 * <p>This package is the Java rendering of Appendix B of the specification. It
 * defines:</p>
 *
 * <ul>
 *   <li>{@link fr.cnrs.lacito.liftpatchbox.error.ErrorCode}, one constant per
 *       normative code, each carrying its {@link fr.cnrs.lacito.liftpatchbox.error.ErrorKind}
 *       — static or dynamic. The codes of Appendix B.3, which a conforming
 *       implementation must not raise, are deliberately absent;</li>
 *   <li>{@link fr.cnrs.lacito.liftpatchbox.error.LiftPatchError}, one error with
 *       its code, its message, its position in the source and the index of the
 *       operation that raised it;</li>
 *   <li>{@link fr.cnrs.lacito.liftpatchbox.error.ErrorCollector}, which
 *       accumulates the errors of a validation pass so that every static error of
 *       a script is reported before anything is applied;</li>
 *   <li>{@link fr.cnrs.lacito.liftpatchbox.error.LiftPatchException}, the one
 *       exception every failure is reported with;</li>
 *   <li>{@link fr.cnrs.lacito.liftpatchbox.error.WarningCode} and
 *       {@link fr.cnrs.lacito.liftpatchbox.error.LiftPatchWarning}, the warnings
 *       of Appendix B.4, which never stop a script.</li>
 * </ul>
 *
 * <p>Errors and warnings are logged through {@code java.util.logging} as they are
 * recorded, at {@code SEVERE} and {@code WARNING} respectively.</p>
 */
package fr.cnrs.lacito.liftpatchbox.error;
