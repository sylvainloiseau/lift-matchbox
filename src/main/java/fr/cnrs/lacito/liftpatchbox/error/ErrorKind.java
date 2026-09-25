package fr.cnrs.lacito.liftpatchbox.error;

/**
 * The two kinds of error the LiftPatch language defines, as fixed by Part 2,
 * section 12.3 of the specification.
 *
 * <p>The distinction is not cosmetic: a {@link #STATIC} error must be reported
 * before any command of the script is applied, and a script containing one
 * changes nothing at all, whereas a {@link #DYNAMIC} error is raised while a
 * command is being resolved or applied and rolls the whole script back.</p>
 */
public enum ErrorKind {

    /** The error depends only on the text of the script and on the metamodel. */
    STATIC,

    /** The error depends on the state of the dictionary. */
    DYNAMIC
}
