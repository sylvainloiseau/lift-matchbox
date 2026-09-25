package fr.cnrs.lacito.liftpatchbox.engine;

/**
 * A reference property whose stored value, after the whole script has been
 * applied, would point at no existing component (Part 2, section 12.4.2).
 *
 * <p>Reporting is mandatory; refusing the script is not. A lexicographer may
 * legitimately delete a sense and repair its inbound references afterwards,
 * possibly in another script, so a dangling reference is reported in order that
 * the decision be taken knowingly.</p>
 *
 * @param componentType  the component type carrying the reference
 * @param componentId    its persistent identifier, or {@code null}
 * @param path           an informative path to it
 * @param property       the reference property
 * @param value          the stored identifier that no longer resolves
 * @param operationIndex the index of the operation that broke it
 * @param verb           the verb of that operation
 */
public record DanglingReference(
    String componentType,
    String componentId,
    String path,
    String property,
    String value,
    int operationIndex,
    String verb
) {
}
