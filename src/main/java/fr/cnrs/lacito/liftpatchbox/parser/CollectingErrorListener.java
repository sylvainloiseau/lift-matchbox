package fr.cnrs.lacito.liftpatchbox.parser;

import fr.cnrs.lacito.liftpatchbox.error.ErrorCode;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchError;
import fr.cnrs.lacito.liftpatchbox.error.SourcePosition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

/**
 * An ANTLR error listener that turns every lexer and parser diagnostic into a
 * {@link ErrorCode#SYNTAX_ERROR}, with its position.
 *
 * <p>The listener replaces ANTLR's default console listener, which writes to
 * {@code System.err} and is invisible to the caller. A line that matches the
 * command-recognition rule of Part 3, section 1.1 but does not parse is an error
 * and never prose, which is why the concise parser installs this listener
 * too.</p>
 *
 * <p>An offset can be applied to every position, so that a fragment parsed out
 * of a larger document — a single concise command line, or the target step of
 * one — reports positions in the coordinates of that document rather than of the
 * fragment.</p>
 */
public final class CollectingErrorListener extends BaseErrorListener {

    private final List<LiftPatchError> errors = new ArrayList<>();
    private final int lineOffset;
    private final int columnOffset;

    /**
     * A listener reporting positions as the recognizer sees them.
     */
    public CollectingErrorListener() {
        this(0, 0);
    }

    /**
     * A listener shifting every reported position.
     *
     * @param lineOffset   added to the reported line minus one, so that a fragment
     *                     starting on line {@code n} reports line {@code n}
     * @param columnOffset added to the reported column
     */
    public CollectingErrorListener(int lineOffset, int columnOffset) {
        this.lineOffset = lineOffset;
        this.columnOffset = columnOffset;
    }

    @Override
    public void syntaxError(
        Recognizer<?, ?> recognizer,
        Object offendingSymbol,
        int line,
        int charPositionInLine,
        String msg,
        RecognitionException e
    ) {
        SourcePosition position =
            new SourcePosition(line + lineOffset, charPositionInLine + 1 + columnOffset);
        errors.add(LiftPatchError.of(ErrorCode.SYNTAX_ERROR, msg, position));
    }

    /**
     * Whether at least one syntax error was reported.
     *
     * @return {@code true} when {@link #errors()} is not empty
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * The syntax errors reported, in the order in which the recognizer found them.
     *
     * @return an unmodifiable view
     */
    public List<LiftPatchError> errors() {
        return Collections.unmodifiableList(errors);
    }
}
