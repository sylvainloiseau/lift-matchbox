package fr.cnrs.lacito.liftpatchbox.engine;

import fr.cnrs.lacito.liftpatchbox.ast.Axis;
import fr.cnrs.lacito.liftpatchbox.ast.Chain;
import fr.cnrs.lacito.liftpatchbox.ast.Operator;
import fr.cnrs.lacito.liftpatchbox.ast.Predicate;
import fr.cnrs.lacito.liftpatchbox.ast.PropertyRef;
import fr.cnrs.lacito.liftpatchbox.ast.Step;
import fr.cnrs.lacito.liftpatchbox.ast.Value;
import fr.cnrs.lacito.liftpatchbox.error.ErrorCode;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchError;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchException;
import fr.cnrs.lacito.liftpatchbox.error.SourcePosition;
import fr.cnrs.lacito.liftpatchbox.metamodel.ComponentTypeDef;
import fr.cnrs.lacito.liftpatchbox.metamodel.Datatype;
import fr.cnrs.lacito.liftpatchbox.metamodel.LanguageKind;
import fr.cnrs.lacito.liftpatchbox.metamodel.Metamodel;
import fr.cnrs.lacito.liftpatchbox.metamodel.PropertyDef;
import fr.cnrs.lacito.liftpatchbox.model.ComponentRef;
import fr.cnrs.lacito.liftpatchbox.model.LiftModel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Resolves selectors, steps and chains against the dictionary.
 *
 * <p>A chain is resolved from the root downwards, and the cardinality rules are
 * applied level by level. Which levels must yield exactly one component is
 * decided by the axis of each link, and by nothing else (Part 1, section 5.1.1,
 * and Part 2, section 7.3.1): a step whose <em>child</em> link is the strict axis
 * is a unique selector, and a unique selector together with the existential chain
 * of ancestors above it forms a single group, resolved as a whole, whose result —
 * not whose every step — must be unique.</p>
 *
 * <p>That is why {@code sense[gloss@en = "pig"] within entry[form@tww = "mami"]}
 * resolves through an ambiguous ancestor when exactly one candidate path survives,
 * and raises {@code AMBIGUOUS_REFERENCE} when two do.</p>
 */
public final class Resolver {

    private final LiftModel model;
    private final Metamodel metamodel;
    private final LanguageScope languages;
    private final Map<String, ComponentRef> labels = new LinkedHashMap<>();

    private int operationIndex;

    /**
     * A resolver over one dictionary.
     *
     * @param model     the dictionary adapter
     * @param metamodel the metamodel
     * @param languages the default-language scope
     */
    public Resolver(LiftModel model, Metamodel metamodel, LanguageScope languages) {
        this.model = model;
        this.metamodel = metamodel;
        this.languages = languages;
    }

    /**
     * Record which operation is being resolved, so that an error carries its index.
     *
     * @param index the 1-based operation index
     */
    public void setOperationIndex(int index) {
        this.operationIndex = index;
    }

    /**
     * Bind a label to the component a command created, resolved or asserted.
     *
     * @param name      the label name, without its {@code $}
     * @param component the component it denotes
     */
    public void bindLabel(String name, ComponentRef component) {
        labels.put(name, component);
    }

    /**
     * The component a label denotes.
     *
     * @param name the label name, without its {@code $}
     * @return the component
     * @throws LiftPatchException with {@link ErrorCode#UNKNOWN_LABEL} if the label
     *         is not bound
     */
    public ComponentRef label(String name, SourcePosition position) {
        ComponentRef ref = labels.get(name);
        if (ref == null) {
            throw error(ErrorCode.UNKNOWN_LABEL,
                "no visible command has bound `$" + name + "`", position);
        }
        return ref;
    }

    // ===================================================================
    // Chains
    // ===================================================================

    /**
     * Resolve a whole chain and return the components its head step matches.
     *
     * @param chain          the chain, child first
     * @param headUnique     whether the head step must match exactly one component
     * @param blockComponent the component an enclosing block supplies, for a
     *                       relative chain; may be {@code null}
     * @param headMayBeEmpty whether an empty result at the head is legal, which it
     *                       is exactly when the head step carries a multiplicity keyword
     * @return the components the head step matches
     */
    public List<ComponentRef> resolveChain(
        Chain chain, boolean headUnique, ComponentRef blockComponent, boolean headMayBeEmpty
    ) {
        List<Step> steps = chain.steps();
        int n = steps.size();
        // A marked singleton step is the one construct in which the marked step and
        // the filtering step are not the same step: a host has exactly one such
        // child, so "every category of these senses" can only mean "the category of
        // every one of these senses", and it is the host step that filters
        // (Part 1, section 5.6).
        boolean markedSingleton = !headUnique && n > 1 && isSingletonStep(steps.get(0));

        List<ComponentRef> current = resolveRootStep(steps.get(n - 1), blockComponent);
        checkCardinality(steps.get(n - 1), current,
            uniqueAt(chain, n - 1, headUnique, markedSingleton),
            n - 1 == 0 && headMayBeEmpty, 1);

        for (int i = n - 2; i >= 0; i--) {
            Step step = steps.get(i);
            Set<ComponentRef> next = new LinkedHashSet<>();
            for (ComponentRef parent : current) {
                next.addAll(resolveStepUnder(parent, step));
            }
            int parentCount = current.size();
            current = new ArrayList<>(next);
            checkCardinality(step, current, uniqueAt(chain, i, headUnique, markedSingleton),
                i == 0 && headMayBeEmpty, parentCount);
        }
        return current;
    }

    private boolean isSingletonStep(Step step) {
        return step instanceof Step.Component c
            && c.componentType() != null
            && metamodel.componentType(c.componentType())
                .map(d -> d.kind() == fr.cnrs.lacito.liftpatchbox.metamodel.ComponentKind.SINGLETON)
                .orElse(false);
    }

    /**
     * Whether the step at {@code index} must match exactly one component: the head
     * does when the command says so, and every other step does when the link to its
     * child is the strict axis. The one exception is the host of a marked singleton,
     * which the multiplicity keyword turns into the filtering step.
     */
    private static boolean uniqueAt(
        Chain chain, int index, boolean headUnique, boolean markedSingleton
    ) {
        if (index == 0) {
            return headUnique;
        }
        if (index == 1 && markedSingleton) {
            return false;
        }
        return chain.axisAbove(index - 1) == Axis.STRICT;
    }

    /**
     * Resolve a chain that must yield exactly one component.
     *
     * @param chain          the chain, child first
     * @param blockComponent the component an enclosing block supplies, or {@code null}
     * @return the one component the chain designates
     */
    public ComponentRef resolveOne(Chain chain, ComponentRef blockComponent) {
        return resolveChain(chain, true, blockComponent, false).get(0);
    }

    private List<ComponentRef> resolveRootStep(Step step, ComponentRef blockComponent) {
        if (step instanceof Step.Label labelStep) {
            return List.of(label(labelStep.name(), labelStep.position()));
        }
        Step.Component component = (Step.Component) step;
        ComponentRef parent = Metamodel.ENTRY.equals(component.componentType())
            ? model.root()
            : blockComponent;
        if (parent == null) {
            throw error(ErrorCode.INCOMPLETE_ANCESTOR_CHAIN,
                "the chain of ancestors must be continued up to the root", step.position());
        }
        return resolveStepUnder(parent, step);
    }

    private void checkCardinality(
        Step step, List<ComponentRef> found, boolean unique, boolean mayBeEmpty, int parentCount
    ) {
        if (found.isEmpty() && !mayBeEmpty) {
            if (parentCount == 1 && step instanceof Step.Component c && c.ordinal() != null) {
                throw error(ErrorCode.INDEX_OUT_OF_BOUNDS,
                    "the ordinal " + c.ordinal() + " is greater than the number of same-type "
                        + "siblings under this parent",
                    step.position());
            }
            throw error(ErrorCode.NOT_FOUND,
                "no component matches `" + step + "`", step.position());
        }
        if (unique && found.size() > 1) {
            throw error(ErrorCode.AMBIGUOUS_REFERENCE,
                found.size() + " components match `" + step
                    + "`, and this selector must designate exactly one",
                step.position());
        }
    }

    // ===================================================================
    // Steps
    // ===================================================================

    /**
     * Resolve one step among the children of a parent.
     *
     * @param parent the parent component
     * @param step   the step
     * @return the matching children, in document order
     */
    public List<ComponentRef> resolveStepUnder(ComponentRef parent, Step step) {
        if (step instanceof Step.Label labelStep) {
            ComponentRef ref = label(labelStep.name(), labelStep.position());
            return List.of(ref);
        }
        Step.Component component = (Step.Component) step;
        List<ComponentRef> siblings = model.children(parent, component.componentType());

        if (component.ordinal() != null) {
            int index = component.ordinal();
            if (index > siblings.size()) {
                // Out of range under *this* parent. Whether that is an error depends
                // on how many parents the step is being resolved under: over a join
                // it simply contributes no candidate, and only a step resolved under
                // one parent raises INDEX_OUT_OF_BOUNDS.
                return List.of();
            }
            return List.of(siblings.get(index - 1));
        }
        if (component.typeKey() != null) {
            for (ComponentRef sibling : siblings) {
                if (component.typeKey().equals(model.scalar(sibling, "type").orElse(null))) {
                    return List.of(sibling);
                }
            }
            return List.of();
        }

        List<ComponentRef> out = new ArrayList<>();
        for (ComponentRef sibling : siblings) {
            if (matches(sibling, component.predicates())) {
                out.add(sibling);
            }
        }
        return out;
    }

    /**
     * Whether a component satisfies every predicate of a list, which is how the
     * predicates of a selector are combined.
     *
     * @param ref        the component
     * @param predicates the predicate list
     * @return {@code true} when every predicate holds
     */
    public boolean matches(ComponentRef ref, List<Predicate> predicates) {
        for (Predicate predicate : predicates) {
            if (!matches(ref, predicate)) {
                return false;
            }
        }
        return true;
    }

    private boolean matches(ComponentRef ref, Predicate predicate) {
        return switch (predicate) {
            case Predicate.Comparison c -> matchesComparison(ref, c);
            case Predicate.Existence e -> matchesExistence(ref, e);
            case Predicate.Has has -> !resolveStepUnder(ref, has.step()).isEmpty();
            case Predicate.Id id -> id.id().equals(model.idOf(ref).orElse(null));
            case Predicate.Hn hn -> model.hnOf(ref) == hn.value();
            case Predicate.HasGloss hg -> matchesHasGloss(ref, hg);
            case Predicate.Index index -> matchesIndex(ref, index);
        };
    }

    private boolean matchesIndex(ComponentRef ref, Predicate.Index index) {
        return model.positionOf(ref).map(p -> p == index.value()).orElse(false);
    }

    private boolean matchesHasGloss(ComponentRef ref, Predicate.HasGloss predicate) {
        String language = predicate.language() != null
            ? predicate.language()
            : languages.defaultFor(LanguageKind.META);
        requireLanguage(LanguageKind.META, language, predicate.position());
        for (ComponentRef sense : model.children(ref, "sense")) {
            if (predicate.value().equals(model.qualified(sense, "gloss", language).orElse(null))) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesExistence(ComponentRef ref, Predicate.Existence predicate) {
        PropertyDef p = propertyOf(ref, predicate.property(), predicate.position());
        boolean present;
        if (p.datatype().isMultitext()) {
            present = predicate.property().language() == null
                ? !model.languagesOf(ref, p.name()).isEmpty()
                : model.qualified(ref, p.name(), predicate.property().language()).isPresent();
        } else {
            present = model.scalar(ref, p.name()).isPresent();
        }
        return predicate.absent() != present;
    }

    private boolean matchesComparison(ComponentRef ref, Predicate.Comparison predicate) {
        PropertyDef p = propertyOf(ref, predicate.property(), predicate.position());
        String expected = scalarValueOf(predicate.value(), p, predicate.position());

        if (!p.datatype().isMultitext()) {
            return compare(model.scalar(ref, p.name()).orElse(null), expected, predicate.operator());
        }
        if (predicate.property().wildcard()) {
            Set<String> langs = model.languagesOf(ref, p.name());
            if (predicate.operator() == Operator.NE) {
                // `p@* != V` requires p to be set and none of its values to be V.
                if (langs.isEmpty()) {
                    return false;
                }
                for (String lang : langs) {
                    if (expected.equals(model.qualified(ref, p.name(), lang).orElse(null))) {
                        return false;
                    }
                }
                return true;
            }
            for (String lang : langs) {
                if (compare(model.qualified(ref, p.name(), lang).orElse(null),
                    expected, predicate.operator())) {
                    return true;
                }
            }
            return false;
        }
        String language = languageOf(predicate.property(), p, predicate.position());
        return compare(
            model.qualified(ref, p.name(), language).orElse(null), expected, predicate.operator());
    }

    private static boolean compare(String actual, String expected, Operator operator) {
        return switch (operator) {
            case EQ -> expected.equals(actual);
            case NE -> actual != null && !expected.equals(actual);
            case MATCH -> actual != null && Pattern.compile(expected).matcher(actual).find();
            case MATCH_IGNORE_CASE -> actual != null && Pattern
                .compile(expected, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(actual).find();
        };
    }

    // ===================================================================
    // Values, properties and languages
    // ===================================================================

    /**
     * The property definition a reference names on a component.
     *
     * @param ref      the component
     * @param property the property reference
     * @param position where it was written
     * @return the property definition
     */
    public PropertyDef propertyOf(
        ComponentRef ref, PropertyRef property, SourcePosition position
    ) {
        ComponentTypeDef def = metamodel.requireComponentType(ref.componentType());
        Optional<PropertyDef> p = def.property(property.name());
        if (p.isEmpty()) {
            throw error(ErrorCode.PROPERTY_DOES_NOT_EXIST_ON_COMPONENT_TYPE,
                "the metamodel defines no property `" + property.name() + "` on a `"
                    + def.name() + "`",
                position);
        }
        return p.get();
    }

    /**
     * The language a property reference designates: the one it writes, or the
     * applicable default.
     *
     * @param ref      the property reference
     * @param property the property definition
     * @param position where the reference was written
     * @return the language code
     */
    public String languageOf(PropertyRef ref, PropertyDef property, SourcePosition position) {
        String language = ref.language() != null
            ? ref.language()
            : languages.defaultFor(property.qualifier());
        requireLanguage(property.qualifier(), language, position);
        return language;
    }

    /**
     * Check that the dictionary declares a language, raising the code of its kind.
     *
     * @param kind     the language kind
     * @param language the language code
     * @param position where it was written
     */
    public void requireLanguage(LanguageKind kind, String language, SourcePosition position) {
        if (language == null || !model.hasLanguage(kind, language)) {
            throw error(kind.noSuchLanguageError(),
                "`" + language + "` is not in the dictionary's " + kind.jsonName()
                    + " language list",
                position);
        }
    }

    /**
     * The string a value denotes for a scalar or a reference property.
     *
     * <p>On a reference property the value is a chain or a label, and the string is
     * the identifier of the component it designates: a reference is a link between
     * components, not a string field (Part 2, section 10).</p>
     *
     * @param value    the value
     * @param property the property being written or compared
     * @param position where the value was written
     * @return the string to store or compare
     */
    public String scalarValueOf(Value value, PropertyDef property, SourcePosition position) {
        if (property.datatype() == Datatype.REFERENCE) {
            ComponentRef target = switch (value) {
                case Value.ChainRef chain -> resolveOne(chain.chain(), null);
                case Value.LabelRef label -> label(label.name(), position);
                default -> throw error(ErrorCode.REFERENCE_VALUE_MUST_BE_A_CHAIN,
                    "a reference is written as a chain or as a label, never as a bare string",
                    position);
            };
            return model.idOf(target).orElseThrow(() -> error(ErrorCode.NOT_FOUND,
                "the component designated as a reference target carries no identifier", position));
        }
        return switch (value) {
            case Value.Str s -> s.text();
            case Value.Num n -> Long.toString(n.value());
            default -> throw error(ErrorCode.SYNTAX_ERROR,
                "a `" + property.datatype().jsonName() + "` property takes a literal value",
                position);
        };
    }

    /**
     * Build the exception for a dynamic error, tagged with the current operation.
     *
     * @param code     the error code
     * @param message  the explanation
     * @param position where the command was written
     * @return the exception, for the caller to throw
     */
    public LiftPatchException error(ErrorCode code, String message, SourcePosition position) {
        return new LiftPatchException(
            new LiftPatchError(code, message, position, operationIndex));
    }
}
