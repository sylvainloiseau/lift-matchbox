package fr.cnrs.lacito.liftpatchbox.model;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.AbstractLiftRoot;
import fr.cnrs.lacito.liftapi.model.LiftSense;

/**
 * A handle on one component of the dictionary, carrying the LiftPatch component
 * type together with whatever the {@code lift-api} model uses to represent it.
 *
 * <p>Almost every component is an ordinary {@code lift-api} object and is wrapped
 * by {@link Node}. Two shapes exist for the two that are not:</p>
 *
 * <ul>
 *   <li>a {@link Category} wraps the <em>host sense</em> rather than the
 *       component, because the specification says a singleton always exists while
 *       the dictionary model leaves its grammatical information absent until a
 *       value is written. Resolving it lazily is what makes
 *       {@code set value = "Noun" on category of sense[...]} work on a sense that
 *       has no grammatical information yet;</li>
 *   <li>a {@link Root} stands for the dictionary itself, the one legal parent of
 *       an {@code entry}.</li>
 * </ul>
 *
 * <p>All three are records, so two handles on the same component compare equal,
 * which is what the resolver relies on when it deduplicates candidate paths.</p>
 */
public sealed interface ComponentRef
    permits ComponentRef.Root, ComponentRef.Node, ComponentRef.Category {

    /**
     * The LiftPatch component type this handle denotes.
     *
     * @return the component type name, or the metamodel's root name for {@link Root}
     */
    String componentType();

    /**
     * The dictionary itself: the parent of every {@code entry} and of nothing else.
     *
     * @param dictionary the dictionary
     */
    record Root(LiftDictionary dictionary) implements ComponentRef {

        @Override
        public String componentType() {
            return "dictionary";
        }

        @Override
        public String toString() {
            return "dictionary";
        }
    }

    /**
     * An ordinary component of the dictionary model.
     *
     * @param componentType the LiftPatch component type
     * @param node          the {@code lift-api} component
     */
    record Node(String componentType, AbstractLiftRoot node) implements ComponentRef {

        @Override
        public String toString() {
            return componentType;
        }
    }

    /**
     * The grammatical category of a sense, identified by its host because the
     * component may not yet exist in the dictionary model.
     *
     * @param host the sense whose category this is
     */
    record Category(LiftSense host) implements ComponentRef {

        @Override
        public String componentType() {
            return "category";
        }

        @Override
        public String toString() {
            return "category";
        }
    }
}
