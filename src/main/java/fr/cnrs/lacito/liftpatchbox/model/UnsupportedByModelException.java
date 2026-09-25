package fr.cnrs.lacito.liftpatchbox.model;

/**
 * Raised when a script asks for something the LiftPatch specification defines but
 * the underlying {@code lift-api} dictionary model cannot express.
 *
 * <p>This is never a defect of the script. The LIFT metamodel of Appendix A is
 * slightly wider than the object model this library writes through: a
 * {@code reversal} carries a {@code target} in the specification and not in the
 * dictionary model, a {@code trait} may hold a {@code field} in the specification
 * and not in the dictionary model, and the {@code url} of an
 * {@code illustration} is fixed when the component is built. Those gaps are
 * listed in the documentation of {@link LiftModel}, and each of them is reported
 * with this exception rather than silently ignored or silently approximated.</p>
 */
public class UnsupportedByModelException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * An exception naming what cannot be expressed.
     *
     * @param message the explanation, which becomes the error message the user sees
     */
    public UnsupportedByModelException(String message) {
        super(message);
    }
}
