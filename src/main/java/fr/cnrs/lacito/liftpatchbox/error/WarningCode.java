package fr.cnrs.lacito.liftpatchbox.error;

/**
 * The warnings of Appendix B.4.
 *
 * <p>A warning reports a situation that is legal, changes nothing, and is more
 * often a mistake than an intention. It never stops a script and never changes
 * the status of a plan document.</p>
 */
public enum WarningCode {

    /**
     * Not one line of the document was recognized as a command. Usually a
     * LiftPatchRef script read as a LiftPatchShort document.
     */
    NO_COMMAND_RECOGNIZED,

    /**
     * A command marked {@code each}, {@code all} or {@code *} whose target step
     * matched no component.
     */
    COMMAND_APPLIES_TO_NO_COMPONENT
}
