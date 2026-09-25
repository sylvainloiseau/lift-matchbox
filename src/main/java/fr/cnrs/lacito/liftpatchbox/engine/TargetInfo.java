package fr.cnrs.lacito.liftpatchbox.engine;

/**
 * How a plan document identifies the component an operation resolved to
 * (Part 2, section 12.4.1).
 *
 * <p>A component is identified by its {@code id} when it has one, and by its path
 * from the root otherwise. The path is human-readable and <strong>informative</strong>:
 * two implementations may differ in it, and no conformance comparison looks at
 * it.</p>
 *
 * @param componentType the component type
 * @param id            the persistent identifier, or {@code null} when it has none
 * @param path          an informative path from the root, written in the reference syntax
 * @param label         the label the command bound to it, or {@code null}
 */
public record TargetInfo(String componentType, String id, String path, String label) {

    /**
     * A target with no label.
     *
     * @param componentType the component type
     * @param id            the persistent identifier, or {@code null}
     * @param path          an informative path from the root
     * @return the target
     */
    public static TargetInfo of(String componentType, String id, String path) {
        return new TargetInfo(componentType, id, path, null);
    }
}
