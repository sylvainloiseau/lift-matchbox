package fr.cnrs.lacito.liftpatchbox.model;

/**
 * An internal guard: the model adapter was asked for a component type, a
 * property or a parentage that the dictionary model has no home for.
 *
 * <p>No script can reach it. The metamodel of Appendix A and the dictionary model
 * agree, and the static validator refuses every command that names a parentage or
 * a property the metamodel does not declare, long before the adapter is called.
 * This exception therefore reports a defect of this library — a metamodel and an
 * adapter that have drifted apart — and not a defect of the script, which is why
 * it carries no error code of Appendix B.</p>
 *
 * <p>It is a {@link RuntimeException} rather than an assertion so that it is
 * reported with its message whatever the JVM's assertion settings, and it is kept
 * rather than removed so that the drift is loud if it ever happens.</p>
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
