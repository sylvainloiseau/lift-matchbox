package fr.cnrs.lacito.liftpatchbox.ast;

import fr.cnrs.lacito.liftpatchbox.error.SourcePosition;
import java.util.List;

/**
 * A block: a header, which supplies a parent, and a body of items anchored to it
 * (Part 2, section 11).
 *
 * <p>A block is three things at once, and all three matter: a parent for the
 * commands it contains, a scope for labels and language defaults, and a nested
 * transaction boundary. The indented block of the concise syntax (Part 3,
 * section 3.3) produces exactly this object.</p>
 *
 * @param header   what stands before the opening brace
 * @param body     the items of the body, in source order
 * @param position where the block begins
 */
public record Block(BlockHeader header, List<Item> body, SourcePosition position) implements Item {

    /**
     * Canonical constructor, taking an unmodifiable copy of the body.
     *
     * @param header   the block header
     * @param body     the items of the body
     * @param position where the block begins
     */
    public Block {
        body = List.copyOf(body);
    }

    /**
     * Whether this block was headed by a {@code create}, which forbids
     * {@code upsert}, {@code ensure} and {@code update} anywhere in its scope.
     *
     * @return {@code true} when the header is a {@code create} command
     */
    public boolean isCreationBlock() {
        return header instanceof BlockHeader.Anchor a && a.command().verb() == Verb.CREATE;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(header.toString()).append(" {");
        for (Item i : body) {
            sb.append(System.lineSeparator()).append("  ").append(i);
        }
        return sb.append(System.lineSeparator()).append('}').toString();
    }
}
