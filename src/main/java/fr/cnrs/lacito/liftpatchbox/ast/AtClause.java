package fr.cnrs.lacito.liftpatchbox.ast;

import fr.cnrs.lacito.liftpatchbox.error.SourcePosition;

/**
 * The {@code at POSITION} clause of Part 1, section 6.2, which states where a
 * newly created or moved component goes in the list of its same-type siblings.
 *
 * <p>The clause is optional on {@code create} and {@code upsert}, where omitting
 * it means {@code at end}, and mandatory on {@code move}, which has no default.
 * It is available on ordered component types only.</p>
 *
 * @param kind     which of the five positions is written
 * @param index    the 1-based index of {@code at index n}, or {@code null}
 * @param step     the sibling step of {@code at before} / {@code at after}, or {@code null}
 * @param position where the clause was written
 */
public record AtClause(Kind kind, Integer index, Step step, SourcePosition position) {

    /** The five positions the clause may name. */
    public enum Kind {
        /** {@code at beginning}: the first position. */
        BEGINNING,
        /** {@code at end}: the last position, which is also the default. */
        END,
        /** {@code at index n}: the 1-based position {@code n}. */
        INDEX,
        /** {@code at before STEP}: immediately before an existing same-type sibling. */
        BEFORE,
        /** {@code at after STEP}: immediately after an existing same-type sibling. */
        AFTER
    }

    /**
     * {@code at beginning}, at an unknown position.
     *
     * @return the clause
     */
    public static AtClause beginning() {
        return new AtClause(Kind.BEGINNING, null, null, SourcePosition.UNKNOWN);
    }

    /**
     * {@code at end}, at an unknown position.
     *
     * @return the clause
     */
    public static AtClause end() {
        return new AtClause(Kind.END, null, null, SourcePosition.UNKNOWN);
    }

    /**
     * {@code at index n}, at an unknown position.
     *
     * @param n the 1-based index
     * @return the clause
     */
    public static AtClause index(int n) {
        return new AtClause(Kind.INDEX, n, null, SourcePosition.UNKNOWN);
    }

    /**
     * {@code at before STEP}, at an unknown position.
     *
     * @param sibling the sibling step
     * @return the clause
     */
    public static AtClause before(Step sibling) {
        return new AtClause(Kind.BEFORE, null, sibling, SourcePosition.UNKNOWN);
    }

    /**
     * {@code at after STEP}, at an unknown position.
     *
     * @param sibling the sibling step
     * @return the clause
     */
    public static AtClause after(Step sibling) {
        return new AtClause(Kind.AFTER, null, sibling, SourcePosition.UNKNOWN);
    }

    @Override
    public String toString() {
        return switch (kind) {
            case BEGINNING -> "at beginning";
            case END -> "at end";
            case INDEX -> "at index " + index;
            case BEFORE -> "at before " + step;
            case AFTER -> "at after " + step;
        };
    }
}
