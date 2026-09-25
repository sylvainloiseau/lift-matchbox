package fr.cnrs.lacito.liftpatchbox.engine;

import fr.cnrs.lacito.liftpatchbox.ast.Verb;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * What a script did, counted.
 *
 * <p>The six totals are the ones a plan document carries (Part 2, section
 * 12.4.1). The two breakdowns — components created per component type, and
 * operations per verb — are this implementation's own, and are what the
 * command-line tool prints after a run so that a lexicographer sees at a glance
 * whether a large script did what they expected.</p>
 */
public final class Summary {

    private int componentsCreated;
    private int componentsDeleted;
    private int componentsMoved;
    private int propertiesSet;
    private int propertiesRemoved;
    private int languagesCreated;

    private final Map<String, Integer> createdByType = new TreeMap<>();
    private final Map<String, Integer> deletedByType = new TreeMap<>();
    private final Map<Verb, Integer> operationsByVerb = new LinkedHashMap<>();

    /**
     * Count one effect.
     *
     * @param effect the effect to count
     */
    public void count(Effect effect) {
        switch (effect.kind()) {
            case componentCreated -> {
                componentsCreated++;
                createdByType.merge(effect.componentType(), 1, Integer::sum);
            }
            case componentDeleted -> {
                componentsDeleted++;
                deletedByType.merge(effect.componentType(), 1, Integer::sum);
            }
            case componentMoved -> componentsMoved++;
            case propertySet, propertyReplaced -> propertiesSet++;
            case propertyRemoved -> propertiesRemoved++;
            case languageCreated -> languagesCreated++;
        }
    }

    /**
     * Count one operation against its verb.
     *
     * @param verb the verb, or {@code null} for a directive
     */
    public void countOperation(Verb verb) {
        if (verb != null) {
            operationsByVerb.merge(verb, 1, Integer::sum);
        }
    }

    /**
     * The total of the {@code componentCreated} effects.
     *
     * @return how many components the script created
     */
    public int componentsCreated() {
        return componentsCreated;
    }

    /**
     * The total of the {@code componentDeleted} effects.
     *
     * @return how many components the script deleted
     */
    public int componentsDeleted() {
        return componentsDeleted;
    }

    /**
     * The total of the {@code componentMoved} effects.
     *
     * @return how many components the script moved
     */
    public int componentsMoved() {
        return componentsMoved;
    }

    /**
     * The total of the {@code propertySet} and {@code propertyReplaced} effects.
     *
     * @return how many property values the script wrote
     */
    public int propertiesSet() {
        return propertiesSet;
    }

    /**
     * The total of the {@code propertyRemoved} effects.
     *
     * @return how many property values the script removed
     */
    public int propertiesRemoved() {
        return propertiesRemoved;
    }

    /**
     * The total of the {@code languageCreated} effects.
     *
     * @return how many languages the script added to the dictionary
     */
    public int languagesCreated() {
        return languagesCreated;
    }

    /**
     * The breakdown of the created components, which is what a run report prints.
     *
     * @return how many components were created, per component type
     */
    public Map<String, Integer> createdByType() {
        return createdByType;
    }

    /**
     * The breakdown of the deleted components.
     *
     * @return how many components were deleted, per component type
     */
    public Map<String, Integer> deletedByType() {
        return deletedByType;
    }

    /**
     * The breakdown of the operations by verb.
     *
     * @return how many operations ran, per verb
     */
    public Map<Verb, Integer> operationsByVerb() {
        return operationsByVerb;
    }

    /**
     * A multi-line rendering of the summary, for a log or a terminal.
     *
     * @return the rendering, without a trailing newline
     */
    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append("components created: ").append(componentsCreated);
        if (!createdByType.isEmpty()) {
            sb.append(' ').append(createdByType);
        }
        sb.append(System.lineSeparator());
        sb.append("components deleted: ").append(componentsDeleted);
        if (!deletedByType.isEmpty()) {
            sb.append(' ').append(deletedByType);
        }
        sb.append(System.lineSeparator());
        sb.append("components moved:   ").append(componentsMoved).append(System.lineSeparator());
        sb.append("properties written: ").append(propertiesSet).append(System.lineSeparator());
        sb.append("properties removed: ").append(propertiesRemoved).append(System.lineSeparator());
        sb.append("languages created:  ").append(languagesCreated).append(System.lineSeparator());
        sb.append("operations:         ").append(operationsByVerb.isEmpty() ? "none" : operationsByVerb);
        return sb.toString();
    }
}
