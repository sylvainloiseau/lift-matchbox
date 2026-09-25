package fr.cnrs.lacito.liftpatchbox.ast;

import fr.cnrs.lacito.liftpatchbox.error.SourcePosition;

/**
 * Anything a script or a block body may contain: a command, a block, or a
 * directive.
 *
 * <p>Comments carry no meaning and are dropped by the parsers, so they are not
 * items.</p>
 */
public sealed interface Item permits Command, Block, Directive {

    /**
     * Where the item begins in the source.
     *
     * @return the source position
     */
    SourcePosition position();
}
