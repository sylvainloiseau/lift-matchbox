package fr.cnrs.lacito.liftpatchbox.validation;

import fr.cnrs.lacito.liftpatchbox.ast.Assignment;
import fr.cnrs.lacito.liftpatchbox.ast.AtClause;
import fr.cnrs.lacito.liftpatchbox.ast.Axis;
import fr.cnrs.lacito.liftpatchbox.ast.Block;
import fr.cnrs.lacito.liftpatchbox.ast.BlockHeader;
import fr.cnrs.lacito.liftpatchbox.ast.Chain;
import fr.cnrs.lacito.liftpatchbox.ast.Command;
import fr.cnrs.lacito.liftpatchbox.ast.Constructor;
import fr.cnrs.lacito.liftpatchbox.ast.Directive;
import fr.cnrs.lacito.liftpatchbox.ast.Initializer;
import fr.cnrs.lacito.liftpatchbox.ast.Item;
import fr.cnrs.lacito.liftpatchbox.ast.LangText;
import fr.cnrs.lacito.liftpatchbox.ast.Multiplicity;
import fr.cnrs.lacito.liftpatchbox.ast.Operator;
import fr.cnrs.lacito.liftpatchbox.ast.Predicate;
import fr.cnrs.lacito.liftpatchbox.ast.PropertyRef;
import fr.cnrs.lacito.liftpatchbox.ast.Script;
import fr.cnrs.lacito.liftpatchbox.ast.Step;
import fr.cnrs.lacito.liftpatchbox.ast.Value;
import fr.cnrs.lacito.liftpatchbox.ast.Verb;
import fr.cnrs.lacito.liftpatchbox.error.ErrorCode;
import fr.cnrs.lacito.liftpatchbox.error.ErrorCollector;
import fr.cnrs.lacito.liftpatchbox.error.SourcePosition;
import fr.cnrs.lacito.liftpatchbox.metamodel.ComponentTypeDef;
import fr.cnrs.lacito.liftpatchbox.metamodel.Datatype;
import fr.cnrs.lacito.liftpatchbox.metamodel.Metamodel;
import fr.cnrs.lacito.liftpatchbox.metamodel.PropertyDef;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The static validation pass: every error of a script that depends only on its
 * text and on the metamodel.
 *
 * <p>Part 2, section 12.3 requires an implementation to report <em>every</em>
 * static error of a script before applying any command, and a script containing
 * one changes nothing. This class therefore collects errors rather than throwing
 * at the first one, and the caller decides when to stop.</p>
 *
 * <p>The order in which a step is checked is fixed by Part 1, section 3.1, and is
 * observed here: the <strong>kind</strong> of the component type is checked
 * first, because it decides what the step may contain at all, and only then are
 * the ordinary selector rules applied. That is why {@code category[value =
 * "Noun"]} in an {@code on} clause is {@code SINGLETON_TAKES_NO_SELECTOR} and not
 * {@code PREDICATE_NOT_ALLOWED_IN_UNIQUE_SELECTOR}, even though the predicate
 * would be refused on its own.</p>
 */
public final class SemanticValidator {

    private static final Logger LOGGER = Logger.getLogger(SemanticValidator.class.getName());

    private final Metamodel metamodel;
    private final ErrorCollector errors = new ErrorCollector();
    private final LabelScope labels = new LabelScope();
    private int operationIndex;

    /**
     * A validator checking against the given metamodel.
     *
     * @param metamodel the metamodel, standard or extended
     */
    public SemanticValidator(Metamodel metamodel) {
        this.metamodel = metamodel;
    }

    /**
     * Validate a whole script.
     *
     * @param script the script
     * @return the collected errors and warnings; empty when the script is free of
     *         static errors
     */
    public ErrorCollector validate(Script script) {
        operationIndex = 0;
        validateItems(script.items(), Context.topLevel());
        LOGGER.log(Level.FINE, () -> "Static validation found "
            + errors.errors().size() + " error(s) in " + script.source());
        return errors;
    }

    /**
     * What the enclosing block supplies to the commands of its body.
     *
     * @param parentType       the component type of the block's component, or
     *                         {@code null} at top level
     * @param inBlock          whether the items are a block body
     * @param inCreationBlock  whether a {@code create} block encloses them, directly
     *                         or indirectly
     */
    private record Context(String parentType, boolean inBlock, boolean inCreationBlock) {

        static Context topLevel() {
            return new Context(null, false, false);
        }

        Context inside(String type, boolean creation) {
            return new Context(type, true, inCreationBlock || creation);
        }
    }

    // ===================================================================
    // Items
    // ===================================================================

    private void validateItems(List<Item> items, Context ctx) {
        for (Item item : items) {
            switch (item) {
                case Command c -> {
                    operationIndex++;
                    validateCommand(c, ctx, false);
                }
                case Directive d -> {
                    if (d.isOperation()) {
                        operationIndex++;
                    }
                }
                case Block b -> validateBlock(b, ctx);
            }
        }
    }

    private void validateBlock(Block block, Context ctx) {
        String headerType;
        boolean creation = false;
        switch (block.header()) {
            case BlockHeader.With unusedWith -> {
                // A `with` header binds names and designates nothing, so it is not an
                // operation and the parent stays the one of the enclosing block.
                labels.push();
                validateItems(block.body(), ctx);
                labels.pop();
                return;
            }
            case BlockHeader.Selector selector -> {
                operationIndex++;
                headerType = validateChain(selector.chain(), ctx, SelectorKind.UNIQUE, true);
                bindLabel(selector.label(), headerType, selector.position());
            }
            case BlockHeader.Anchor anchor -> {
                operationIndex++;
                Command command = anchor.command();
                creation = command.verb() == Verb.CREATE;
                if (command.verb() == Verb.DELETE) {
                    error(ErrorCode.DELETE_CANNOT_BE_AN_ANCHOR,
                        "the component a `delete` names is gone by the time the commands "
                            + "indented under it would run",
                        command.position());
                }
                if (command.verb() == Verb.MOVE) {
                    error(ErrorCode.MOVE_NOT_ALLOWED_IN_BLOCK,
                        "a `move` names two parents while a block header supplies one; write it "
                            + "at top level",
                        command.position());
                }
                validateCommand(command, ctx, true);
                headerType = commandComponentType(command);
            }
            default -> headerType = null;
        }
        labels.push();
        validateItems(block.body(), ctx.inside(headerType, creation));
        labels.pop();
    }

    private String commandComponentType(Command command) {
        return switch (command) {
            case Command.Create c -> c.constructor().componentType();
            case Command.Upsert u -> u.constructor().componentType();
            case Command.Ensure e -> stepType(e.target());
            case Command.Set s -> s.target() == null ? null : stepType(s.target().head());
            case Command.Update u -> u.target() == null ? null : stepType(u.target().head());
            case Command.Clear c -> c.target() == null ? null : stepType(c.target().head());
            default -> null;
        };
    }

    // ===================================================================
    // Commands
    // ===================================================================

    private void validateCommand(Command command, Context ctx, boolean isBlockHeader) {
        switch (command) {
            case Command.Create c -> validateCreate(c, ctx);
            case Command.Upsert u -> validateUpsert(u, ctx);
            case Command.Ensure e -> validateEnsure(e, ctx);
            case Command.Delete d -> validateDelete(d, ctx);
            case Command.Move m -> validateMove(m, ctx);
            case Command.Set s -> validateProperty(s.verb(), s.assignments(), List.of(), s.target(), ctx, s.position());
            case Command.Update u -> {
                if (ctx.inCreationBlock()) {
                    error(ErrorCode.CANNOT_UPSERT_ENSURE_OR_UPDATE_IN_CREATION_BLOCK,
                        "expecting a property to exist on a component the same script has just "
                            + "created makes no sense",
                        u.position());
                }
                validateProperty(u.verb(), u.assignments(), List.of(), u.target(), ctx, u.position());
            }
            case Command.Clear c ->
                validateProperty(c.verb(), List.of(), c.properties(), c.target(), ctx, c.position());
        }
    }

    // ------------------------------------------------------------ create

    private void validateCreate(Command.Create command, Context ctx) {
        Constructor constructor = command.constructor();
        Optional<ComponentTypeDef> type = componentType(constructor.componentType(), constructor.position());
        if (type.isEmpty()) {
            return;
        }
        ComponentTypeDef def = type.get();

        if (def.isSingleton()) {
            error(ErrorCode.SINGLETON_CANNOT_BE_CREATED_OR_DELETED,
                "a `" + def.name() + "` comes into existence with its host and is never created; "
                    + "write its properties with `set` once the host exists",
                constructor.position());
            return;
        }
        if (Metamodel.ENTRY.equals(def.name()) && ctx.inBlock()) {
            error(ErrorCode.ILLEGAL_PARENT,
                "a block header supplies a parent and an entry has none; create the entry at top "
                    + "level and write the block that fills it with that `create entry` as its header",
                constructor.position());
        }

        String parentType = validateParentClause(
            def, command.parent(), ctx, constructor.position(), "under");
        validateParentage(parentType, def.name(), constructor.position());
        validateAtClause(command.at(), def, constructor.position());
        validateInitializers(constructor, def, Verb.CREATE, ctx);
        bindLabel(command.label(), def.name(), constructor.position());
    }

    // ------------------------------------------------------------ upsert

    private void validateUpsert(Command.Upsert command, Context ctx) {
        Constructor constructor = command.constructor();
        Optional<ComponentTypeDef> type = componentType(constructor.componentType(), constructor.position());
        if (type.isEmpty()) {
            return;
        }
        ComponentTypeDef def = type.get();

        if (ctx.inCreationBlock()) {
            error(ErrorCode.CANNOT_UPSERT_ENSURE_OR_UPDATE_IN_CREATION_BLOCK,
                "a component the same script has just created cannot already have children to "
                    + "upsert",
                constructor.position());
        }
        if (def.isSingleton()) {
            error(ErrorCode.SINGLETON_CANNOT_BE_CREATED_OR_DELETED,
                "a `" + def.name() + "` is never created, so it is never upserted either",
                constructor.position());
            return;
        }

        String parentType = validateParentClause(
            def, command.parent(), ctx, constructor.position(), "under");
        validateParentage(parentType, def.name(), constructor.position());
        validateAtClause(command.at(), def, constructor.position());

        // The match part must cover the whole natural identity before anything else
        // is said about the initializer list (Part 2, section 8.2).
        Set<String> given = new HashSet<>();
        for (Initializer.Assignment a : constructor.assignments()) {
            given.add(a.property().name());
        }
        for (String identity : def.naturalIdentity()) {
            if (!given.contains(identity)) {
                error(ErrorCode.MISSING_IDENTITY_PROPERTY,
                    "an `upsert` on a `" + def.name() + "` must give its whole natural identity, "
                        + def.naturalIdentity() + "; `" + identity + "` is missing",
                    constructor.position());
            }
        }
        validateInitializers(constructor, def, Verb.UPSERT, ctx);
        if (Metamodel.ENTRY.equals(def.name()) && constructor.conditions().isEmpty()) {
            error(ErrorCode.UPSERT_ENTRY_WITHOUT_DISAMBIGUATION,
                "without a `has` or `has-gloss` predicate this command could only ever create a "
                    + "new, possibly homophonous entry, which is what `create` already does",
                constructor.position());
        }
        bindLabel(command.label(), def.name(), constructor.position());
    }

    // ------------------------------------------------------------ ensure

    private void validateEnsure(Command.Ensure command, Context ctx) {
        checkMultiplicity(Verb.ENSURE, command.target(), command.position());
        if (ctx.inCreationBlock()) {
            error(ErrorCode.CANNOT_UPSERT_ENSURE_OR_UPDATE_IN_CREATION_BLOCK,
                "asserting the existence of a child of a component the same script has just "
                    + "created makes no sense",
                command.position());
        }
        String targetType = validateStep(command.target(), SelectorKind.UNIQUE, null);
        String parentType = validateTargetParent(
            command.target(), targetType, command.parent(), ctx, "under");
        validateParentage(parentType, targetType, command.position());
        bindLabel(command.label(), targetType, command.position());
    }

    // ------------------------------------------------------------ delete

    private void validateDelete(Command.Delete command, Context ctx) {
        Step target = command.target();
        String targetType = stepType(target);
        if (targetType != null) {
            Optional<ComponentTypeDef> def = componentType(targetType, target.position());
            if (def.isPresent() && def.get().isSingleton()) {
                error(ErrorCode.SINGLETON_CANNOT_BE_CREATED_OR_DELETED,
                    "a `" + targetType + "` disappears only with its host; to empty it rather "
                        + "than remove it, clear its properties",
                    target.position());
                return;
            }
        }
        SelectorKind kind = multiplicityOf(target) == null
            ? SelectorKind.UNIQUE
            : SelectorKind.FILTERING;
        validateStep(target, kind, null);
        String parentType = validateTargetParent(target, targetType, command.parent(), ctx, "under");
        validateParentage(parentType, targetType, command.position());
    }

    // -------------------------------------------------------------- move

    private void validateMove(Command.Move command, Context ctx) {
        if (ctx.inBlock()) {
            error(ErrorCode.MOVE_NOT_ALLOWED_IN_BLOCK,
                "a `move` names two parents while a block header supplies one; write it at top "
                    + "level, where both of its parents are visible in the command itself",
                command.position());
        }
        if (multiplicityOf(command.target()) != null) {
            error(ErrorCode.MULTIPLICITY_NOT_ALLOWED,
                "`move` operates on exactly one component", command.position());
        }
        String targetType = stepType(command.target());
        if (targetType != null) {
            Optional<ComponentTypeDef> def = componentType(targetType, command.target().position());
            if (def.isPresent()) {
                if (def.get().isSingleton()) {
                    error(ErrorCode.SINGLETON_CANNOT_BE_CREATED_OR_DELETED,
                        "a `" + targetType + "` is never moved: it comes into existence with its "
                            + "host and disappears with it",
                        command.target().position());
                    return;
                }
                if (!def.get().isOrdered()) {
                    error(ErrorCode.COMPONENT_NOT_ORDERED,
                        "`move` changes a position, and a `" + targetType + "` has none; it is "
                            + "re-keyed by writing its `type` with `set`, and re-parented by "
                            + "deleting it and creating it under the new parent",
                        command.target().position());
                    return;
                }
                if (Metamodel.ENTRY.equals(targetType)) {
                    error(ErrorCode.SYNTAX_ERROR,
                        "an entry has no parent and no sibling order to change: the dictionary, "
                            + "not the script, orders the entry list",
                        command.target().position());
                    return;
                }
            }
        }
        validateStep(command.target(), SelectorKind.UNIQUE, null);
        String sourceParent = validateTargetParent(
            command.target(), targetType, command.sourceParent(), ctx, "under");
        validateParentage(sourceParent, targetType, command.position());

        if (command.destinationParent() != null) {
            String destination = validateChain(command.destinationParent(), ctx, SelectorKind.UNIQUE, true);
            validateParentage(destination, targetType, command.position());
        }
        Optional<ComponentTypeDef> def = componentType(targetType, command.target().position());
        if (command.at() == null) {
            error(ErrorCode.SYNTAX_ERROR, "`move` requires an `at` clause", command.position());
        } else if (def.isPresent()) {
            validateAtClause(command.at(), def.get(), command.position());
        }
    }

    // -------------------------------------------- set, update and clear

    private void validateProperty(
        Verb verb,
        List<Assignment> assignments,
        List<PropertyRef> cleared,
        Chain target,
        Context ctx,
        SourcePosition position
    ) {
        String componentType;
        if (target == null) {
            if (!ctx.inBlock()) {
                error(ErrorCode.MISSING_PARENT_CLAUSE,
                    "a property command written outside a block names the component it writes on "
                        + "with an `on` clause",
                    position);
                return;
            }
            componentType = ctx.parentType();
        } else {
            SelectorKind kind = multiplicityOf(target.head()) == null
                ? SelectorKind.UNIQUE
                : SelectorKind.FILTERING;
            componentType = validateChain(target, ctx, kind, true);
        }
        if (componentType == null) {
            return;
        }
        Optional<ComponentTypeDef> def = componentType(componentType, position);
        if (def.isEmpty()) {
            return;
        }

        Set<String> seen = new HashSet<>();
        for (Assignment a : assignments) {
            checkAssignment(a.property(), a.value(), def.get(), verb, seen, a.position());
        }
        for (PropertyRef p : cleared) {
            checkCleared(p, def.get(), seen);
        }
    }

    private void checkAssignment(
        PropertyRef ref, Value value, ComponentTypeDef def, Verb verb,
        Set<String> seen, SourcePosition position
    ) {
        Optional<PropertyDef> property = property(def, ref, position);
        if (property.isEmpty()) {
            return;
        }
        PropertyDef p = property.get();
        if (!checkQualifier(ref, p, false)) {
            return;
        }
        if (ref.wildcard()) {
            error(ErrorCode.WILDCARD_NOT_ALLOWED,
                "`" + verb.keyword() + "` writes exactly one qualified value; to write several "
                    + "languages at once, use a multi-language literal",
                ref.position());
            return;
        }
        checkValue(value, p, ref, seen, position);
    }

    private void checkCleared(PropertyRef ref, ComponentTypeDef def, Set<String> seen) {
        Optional<PropertyDef> property = property(def, ref, ref.position());
        if (property.isEmpty()) {
            return;
        }
        PropertyDef p = property.get();
        if (p.datatype().isMultitext()) {
            if (!ref.hasQualifier()) {
                error(ErrorCode.MISSING_LANGUAGE_QUALIFIER,
                    "`clear` on a multitext requires an explicit qualifier: a language code, or "
                        + "the wildcard `@*`",
                    ref.position());
                return;
            }
        } else {
            if (ref.hasQualifier()) {
                error(ErrorCode.LANG_KEY_NOT_SUPPORTED_ON_SCALAR,
                    "`" + p.qualifiedName() + "` is a scalar property and takes no language key",
                    ref.position());
                return;
            }
            if (p.identity()) {
                error(ErrorCode.CANNOT_CLEAR_IDENTITY_PROPERTY,
                    "`" + p.qualifiedName() + "` belongs to the natural identity of a `"
                        + def.name() + "` and may never become unset",
                    ref.position());
                return;
            }
            if (p.required()) {
                error(ErrorCode.CANNOT_CLEAR_REQUIRED_PROPERTY,
                    "`" + p.qualifiedName() + "` is required at creation and may never become "
                        + "unset; `clear` is refused rather than allowed to produce a component "
                        + "that `create` would have rejected",
                    ref.position());
                return;
            }
        }
        if (!seen.add(ref.toString())) {
            error(ErrorCode.DUPLICATE_PROPERTY,
                "`" + ref + "` is named twice in one command", ref.position());
        }
    }

    // ===================================================================
    // Initializers
    // ===================================================================

    private void validateInitializers(
        Constructor constructor, ComponentTypeDef def, Verb verb, Context ctx
    ) {
        Set<String> seen = new HashSet<>();
        Set<String> present = new HashSet<>();

        for (Initializer initializer : constructor.initializers()) {
            switch (initializer) {
                case Initializer.Assignment a -> {
                    PropertyRef ref = a.property();
                    if (metamodel.isPseudoProperty(ref.name())) {
                        reportPseudoPropertyInInitializer(ref, verb);
                        continue;
                    }
                    Optional<PropertyDef> property = property(def, ref, ref.position());
                    if (property.isEmpty()) {
                        continue;
                    }
                    PropertyDef p = property.get();
                    if (!checkQualifier(ref, p, false)) {
                        continue;
                    }
                    if (ref.wildcard()) {
                        error(ErrorCode.WILDCARD_NOT_ALLOWED,
                            "an initializer designates exactly one qualified value",
                            ref.position());
                        continue;
                    }
                    present.add(p.name());
                    checkValue(a.value(), p, ref, seen, a.position());
                }
                case Initializer.Condition c -> {
                    if (verb == Verb.CREATE) {
                        error(ErrorCode.COMMAND_NOT_ALLOWING_HAS,
                            "`has` and `has-gloss` are matching conditions, and `create` matches "
                                + "nothing: the component being created has no children yet",
                            c.position());
                        continue;
                    }
                    if (c.predicate() instanceof Predicate.HasGloss
                        && !Metamodel.ENTRY.equals(def.name())) {
                        error(ErrorCode.COMMAND_NOT_ALLOWING_HAS,
                            "`has-gloss` is an entry-only shorthand; on another component type, "
                                + "write the `has` predicate out",
                            c.position());
                        continue;
                    }
                    if (c.predicate() instanceof Predicate.Has has) {
                        validateStep(has.step(), SelectorKind.FILTERING, def.name());
                    }
                }
                case Initializer.Embedded e -> {
                    Optional<ComponentTypeDef> child =
                        componentType(e.constructor().componentType(), e.position());
                    if (child.isEmpty()) {
                        continue;
                    }
                    if (child.get().isSingleton()) {
                        error(ErrorCode.SINGLETON_CANNOT_BE_CREATED_OR_DELETED,
                            "a `" + child.get().name() + "` is never created",
                            e.position());
                        continue;
                    }
                    validateParentage(def.name(), child.get().name(), e.position());
                    validateInitializers(e.constructor(), child.get(), Verb.CREATE, ctx);
                }
            }
        }

        for (PropertyDef p : def.properties().values()) {
            if (p.required() && !present.contains(p.name())) {
                error(ErrorCode.MISSING_REQUIRED_PROPERTY,
                    "`" + p.qualifiedName() + "` is required at creation"
                        + (p.datatype().isMultitext() ? ", with at least one qualified value" : ""),
                    constructor.position());
            }
        }
    }

    private void reportPseudoPropertyInInitializer(PropertyRef ref, Verb verb) {
        switch (ref.name()) {
            case "hn" -> error(ErrorCode.COMMAND_NOT_ALLOWING_HN,
                "`hn` cannot be given to a component that does not exist yet: asking to create "
                    + "\"the entry that is the second homophone\" is meaningless",
                ref.position());
            case "has-gloss" -> error(ErrorCode.COMMAND_NOT_ALLOWING_HAS,
                "`has-gloss` is a matching condition and never an assignment", ref.position());
            case "id" -> {
                if (verb == Verb.UPSERT) {
                    error(ErrorCode.ID_NOT_ALLOWED_ON_UPSERT,
                        "an `id` cannot be set, and a component that does not exist yet has none",
                        ref.position());
                } else {
                    error(ErrorCode.PROPERTY_DOES_NOT_EXIST_ON_COMPONENT_TYPE,
                        "`id` is system-managed: it can be referred to, and never created, set, "
                            + "updated or cleared",
                        ref.position());
                }
            }
            default -> error(ErrorCode.PROPERTY_DOES_NOT_EXIST_ON_COMPONENT_TYPE,
                "`" + ref.name() + "` is a selection device and never an initializer",
                ref.position());
        }
    }

    // ===================================================================
    // Chains and steps
    // ===================================================================

    /**
     * Validate the parent clause of a component command, which is mandatory except
     * for an {@code entry} and inside a block.
     *
     * @return the component type of the parent, or {@code null} when it is unknown
     */
    private String validateParentClause(
        ComponentTypeDef def, Chain parent, Context ctx, SourcePosition position, String keyword
    ) {
        if (parent != null) {
            return validateChain(parent, ctx, SelectorKind.UNIQUE, false);
        }
        if (Metamodel.ENTRY.equals(def.name())) {
            return metamodel.root();
        }
        if (ctx.inBlock()) {
            return ctx.parentType();
        }
        error(ErrorCode.MISSING_PARENT_CLAUSE,
            "a `" + def.name() + "` is not an entry, so the command names its parent with an `"
                + keyword + "` clause",
            position);
        return null;
    }

    /**
     * The same, for a command written with a target step rather than a constructor.
     */
    private String validateTargetParent(
        Step target, String targetType, Chain parent, Context ctx, String keyword
    ) {
        if (parent != null) {
            return validateChain(parent, ctx, SelectorKind.UNIQUE, false);
        }
        if (target instanceof Step.Label) {
            // A label denotes a component whose parent the script need not name.
            return null;
        }
        if (Metamodel.ENTRY.equals(targetType)) {
            return metamodel.root();
        }
        if (ctx.inBlock()) {
            return ctx.parentType();
        }
        error(ErrorCode.MISSING_PARENT_CLAUSE,
            "a target that is not an entry or a label needs a parent path",
            target.position());
        return null;
    }

    /**
     * Validate a whole chain, child first, and return the component type of its
     * head step.
     *
     * @param chain    the chain
     * @param ctx      the enclosing context, which says whether a relative chain is
     *                 admissible
     * @param headKind whether the head step is a unique or a filtering selector
     * @param headIsTarget whether the head step is the component the command acts
     *                 on, rather than the parent of it
     * @return the component type of the head step, or {@code null} when unknown
     */
    private String validateChain(
        Chain chain, Context ctx, SelectorKind headKind, boolean headIsTarget
    ) {
        List<Step> steps = chain.steps();
        List<String> types = new ArrayList<>(steps.size());
        // A marked singleton step distributes over its host, which becomes the
        // filtering step: a host has exactly one such child, so "every category of
        // these senses" can only mean "the category of every one of these senses"
        // (Part 1, section 5.6). This is the one construct in which the marked step
        // and the filtering step are not the same step.
        boolean markedSingleton = headKind == SelectorKind.FILTERING
            && steps.size() > 1
            && steps.get(0) instanceof Step.Component head
            && head.componentType() != null
            && metamodel.componentType(head.componentType())
                .map(ComponentTypeDef::isSingleton).orElse(false);

        for (int i = 0; i < steps.size(); i++) {
            // A step is a unique selector when the link to its child is the strict
            // axis; the head's child link is the unwritten strict one that joins a
            // parent path to its direct target.
            SelectorKind kind = i == 0
                ? headKind
                : (i == 1 && markedSingleton) ? SelectorKind.FILTERING
                : (chain.axisAbove(i - 1) == Axis.STRICT
                    ? SelectorKind.UNIQUE : SelectorKind.FILTERING);
            if (steps.get(i).multiplicity() != null && !(i == 0 && headIsTarget)) {
                error(ErrorCode.MULTIPLICITY_NOT_ALLOWED,
                    "a multiplicity keyword marks the target step of a `delete`, a `set`, an "
                        + "`update` or a `clear`, and no parent step",
                    steps.get(i).position());
            }
            String parentType = i + 1 < steps.size() ? stepType(steps.get(i + 1)) : null;
            types.add(validateStep(steps.get(i), kind, parentType));
        }

        for (int i = 0; i + 1 < steps.size(); i++) {
            validateParentage(types.get(i + 1), types.get(i), steps.get(i).position());
        }

        Step root = steps.get(steps.size() - 1);
        String rootType = types.get(types.size() - 1);
        boolean rootIsLabel = root instanceof Step.Label;
        if (!rootIsLabel && !Metamodel.ENTRY.equals(rootType) && rootType != null) {
            if (ctx.inBlock()) {
                // Inside a block a path may be relative: its root-most step is a
                // child of the block's component.
                validateParentage(ctx.parentType(), rootType, root.position());
            } else {
                error(ErrorCode.INCOMPLETE_ANCESTOR_CHAIN,
                    "the chain of ancestors must be continued up to the root; this one stops at a "
                        + "`" + rootType + "`",
                    root.position());
            }
        }
        return types.isEmpty() ? null : types.get(0);
    }

    /**
     * Validate one step and return the component type it names.
     */
    private String validateStep(Step step, SelectorKind kind, String parentType) {
        if (step instanceof Step.Label label) {
            if (kind == SelectorKind.FILTERING) {
                error(ErrorCode.PSEUDO_PROPERTY_NOT_ALLOWED_IN_FILTER,
                    "a label already denotes exactly one component, so there is nothing to filter",
                    label.position());
            }
            if (!labels.isBound(label.name())) {
                error(ErrorCode.UNKNOWN_LABEL,
                    "no visible command has bound `$" + label.name() + "`", label.position());
            }
            return labels.typeOf(label.name()).orElse(null);
        }

        Step.Component component = (Step.Component) step;
        if (component.componentType() == null) {
            // The `at before #2` spelling, whose component type the engine supplies.
            return null;
        }
        Optional<ComponentTypeDef> type =
            componentType(component.componentType(), component.position());
        if (type.isEmpty()) {
            return null;
        }
        ComponentTypeDef def = type.get();

        // The kind of the component type is checked first: it decides what the step
        // may contain at all (Part 1, section 3.1).
        if (component.ordinal() != null) {
            if (!def.isOrdered() || Metamodel.ENTRY.equals(def.name())) {
                error(ErrorCode.COMPONENT_NOT_ORDERED,
                    Metamodel.ENTRY.equals(def.name())
                        ? "the entry list is ordered by the dictionary and not by this language"
                        : "a `" + def.name() + "` lives in no ordered list, so it has no first "
                            + "element",
                    component.position());
                return def.name();
            }
            if (component.ordinal() < 1) {
                error(ErrorCode.ILLEGAL_ORDINAL, "an ordinal starts at 1", component.position());
            }
            if (kind == SelectorKind.FILTERING) {
                error(ErrorCode.PSEUDO_PROPERTY_NOT_ALLOWED_IN_FILTER,
                    "an ordinal is a selection device and is forbidden in a filtering selector",
                    component.position());
            }
            return def.name();
        }
        if (component.typeKey() != null) {
            if (!def.isTyped()) {
                error(ErrorCode.COMPONENT_NOT_TYPED,
                    "a `" + def.name() + "` is not keyed by its `type`, so `^` names a key where "
                        + "there is none",
                    component.position());
                return def.name();
            }
            if (kind == SelectorKind.FILTERING) {
                error(ErrorCode.PSEUDO_PROPERTY_NOT_ALLOWED_IN_FILTER,
                    "a type key is forbidden in a filtering selector; write the `type` predicate "
                        + "out, which is legal and says the same thing",
                    component.position());
            }
            return def.name();
        }
        if (def.isSingleton() && kind == SelectorKind.UNIQUE && component.hasSelector()) {
            error(ErrorCode.SINGLETON_TAKES_NO_SELECTOR,
                "a host has exactly one `" + def.name() + "`, so there is nothing to choose among",
                component.position());
            return def.name();
        }

        validatePredicates(component, def, kind);
        if (kind == SelectorKind.UNIQUE) {
            validateStrategy(component, def);
        }
        return def.name();
    }

    /**
     * Check every predicate of a step against the metamodel and against the kind of
     * selector it stands in.
     */
    private void validatePredicates(Step.Component step, ComponentTypeDef def, SelectorKind kind) {
        Set<String> seen = new HashSet<>();
        for (Predicate predicate : step.predicates()) {
            switch (predicate) {
                case Predicate.Comparison c -> {
                    Optional<PropertyDef> property = property(def, c.property(), c.position());
                    if (property.isEmpty()) {
                        continue;
                    }
                    PropertyDef p = property.get();
                    if (!checkQualifier(c.property(), p, kind == SelectorKind.FILTERING)) {
                        continue;
                    }
                    if (kind == SelectorKind.UNIQUE && c.operator().isFilteringOnly()) {
                        error(ErrorCode.PREDICATE_NOT_ALLOWED_IN_UNIQUE_SELECTOR,
                            "`" + c.operator().spelling() + "` is not uniquely identifying and is "
                                + "admitted in a filtering selector only",
                            c.position());
                        continue;
                    }
                    if (c.operator().isRegex() && !p.datatype().admitsRegex()) {
                        error(ErrorCode.OPERATOR_NOT_APPLICABLE_TO_DATATYPE,
                            "`" + c.operator().spelling() + "` does not apply to a `"
                                + p.datatype().jsonName() + "` property; matching a string "
                                + "rendering of the value would be an implementation detail",
                            c.position());
                        continue;
                    }
                    if (kind == SelectorKind.UNIQUE && c.property().wildcard()) {
                        error(ErrorCode.WILDCARD_NOT_ALLOWED,
                            "a unique selector designates one qualified value",
                            c.property().position());
                        continue;
                    }
                    checkPredicateValue(c.value(), p, c.position());
                    if (!seen.add(c.property().toString())) {
                        error(ErrorCode.DUPLICATE_SELECTOR,
                            "the selector states `" + c.property() + "` twice", c.position());
                    }
                }
                case Predicate.Existence e -> {
                    Optional<PropertyDef> property = property(def, e.property(), e.position());
                    if (property.isEmpty()) {
                        continue;
                    }
                    if (e.property().wildcard()) {
                        error(ErrorCode.WILDCARD_NOT_ALLOWED,
                            "`exists(p@*)` would mean `exists(p)` and `absent(p@*)` would be "
                                + "ambiguous; write `exists(p)` or `absent(p)`",
                            e.position());
                        continue;
                    }
                    if (!e.property().hasQualifier() || property.get().datatype().isMultitext()) {
                        // An unqualified exists/absent on a multitext asks about the
                        // property as a whole, which is legal and needs no default.
                        if (e.property().hasQualifier() && !property.get().datatype().isMultitext()) {
                            checkQualifier(e.property(), property.get(), true);
                        }
                    } else {
                        checkQualifier(e.property(), property.get(), true);
                    }
                    if (kind == SelectorKind.UNIQUE) {
                        error(ErrorCode.PREDICATE_NOT_ALLOWED_IN_UNIQUE_SELECTOR,
                            "`" + (e.absent() ? "absent" : "exists") + "` is not uniquely "
                                + "identifying and is admitted in a filtering selector only",
                            e.position());
                    }
                }
                case Predicate.Has has -> validateStep(has.step(), SelectorKind.FILTERING, def.name());
                case Predicate.Id id -> {
                    if (kind == SelectorKind.FILTERING) {
                        error(ErrorCode.PSEUDO_PROPERTY_NOT_ALLOWED_IN_FILTER,
                            "`id` is forbidden in a filtering selector", id.position());
                    } else if (!isIdBearing(def)) {
                        error(ErrorCode.PROPERTY_DOES_NOT_EXIST_ON_COMPONENT_TYPE,
                            "only an `entry` and a `sense` carry an `id`", id.position());
                    }
                }
                case Predicate.Hn hn -> {
                    if (kind == SelectorKind.FILTERING) {
                        error(ErrorCode.PSEUDO_PROPERTY_NOT_ALLOWED_IN_FILTER,
                            "`hn` is forbidden in a filtering selector", hn.position());
                    } else if (!Metamodel.ENTRY.equals(def.name())) {
                        error(ErrorCode.COMMAND_NOT_ALLOWING_HN,
                            "`hn` disambiguates homophonous entries and applies to an `entry` only",
                            hn.position());
                    } else if (hn.value() < 1) {
                        error(ErrorCode.ILLEGAL_HN,
                            "a homophone number is an integer greater than or equal to 1",
                            hn.position());
                    }
                }
                case Predicate.HasGloss hg -> {
                    if (kind == SelectorKind.FILTERING) {
                        error(ErrorCode.PSEUDO_PROPERTY_NOT_ALLOWED_IN_FILTER,
                            "`has-gloss` is forbidden in a filtering selector", hg.position());
                    } else if (!Metamodel.ENTRY.equals(def.name())) {
                        error(ErrorCode.COMMAND_NOT_ALLOWING_HAS,
                            "`has-gloss` is an entry-only shorthand; on another component type, "
                                + "write `has sense[gloss@L = V]` out",
                            hg.position());
                    }
                }
                case Predicate.Index index -> {
                    if (kind == SelectorKind.FILTERING) {
                        error(ErrorCode.PSEUDO_PROPERTY_NOT_ALLOWED_IN_FILTER,
                            "an ordinal is forbidden in a filtering selector", index.position());
                    } else if (index.value() < 1) {
                        error(ErrorCode.ILLEGAL_ORDINAL, "an ordinal starts at 1", index.position());
                    }
                }
            }
        }
    }

    /**
     * Check that a unique selector uses exactly one of the seven selection
     * strategies of Part 1, section 5.1.3.
     */
    private void validateStrategy(Step.Component step, ComponentTypeDef def) {
        if (def.isSingleton()) {
            // Strategy S7 is the absence of a selector, and it is complete on its own.
            return;
        }
        List<Predicate> predicates = step.predicates();
        boolean hasId = predicates.stream().anyMatch(p -> p instanceof Predicate.Id);
        boolean hasHn = predicates.stream().anyMatch(p -> p instanceof Predicate.Hn);
        boolean hasGloss = predicates.stream().anyMatch(p -> p instanceof Predicate.HasGloss);
        boolean hasIndex = predicates.stream().anyMatch(p -> p instanceof Predicate.Index);
        List<Predicate.Comparison> comparisons = predicates.stream()
            .filter(Predicate.Comparison.class::isInstance)
            .map(Predicate.Comparison.class::cast)
            .toList();

        if (hasId) {
            if (predicates.size() > 1) {
                error(ErrorCode.DUPLICATE_SELECTOR,
                    "an `id` selects on its own and states a whole strategy; it may not be "
                        + "combined with another predicate",
                    step.position());
            }
            return;
        }
        if (hasIndex) {
            if (predicates.size() > 1) {
                error(ErrorCode.DUPLICATE_SELECTOR,
                    "the deprecated `index` predicate states a whole strategy and may not be "
                        + "combined with another predicate",
                    step.position());
            }
            return;
        }

        if (Metamodel.ENTRY.equals(def.name())) {
            boolean hasForm = comparisons.stream().anyMatch(c -> c.property().name().equals("form"));
            if (!hasForm) {
                if (hasHn) {
                    error(ErrorCode.HN_CANNOT_BE_USED_ALONE,
                        "`hn` selects the entry whose qualified form matches the accompanying "
                            + "`form` predicate, so it is never written alone",
                        step.position());
                } else if (hasGloss) {
                    error(ErrorCode.HAS_GLOSS_CANNOT_BE_USED_ALONE,
                        "`has-gloss` narrows a form lookup and is never written alone",
                        step.position());
                } else {
                    error(ErrorCode.INCOMPLETE_SELECTOR,
                        "an `entry` is selected by a qualified `form`, by an `id`, or by a label",
                        step.position());
                }
                return;
            }
            boolean hasHasPredicate =
                predicates.stream().anyMatch(p -> p instanceof Predicate.Has);
            if (hasHn && (hasGloss || hasHasPredicate)) {
                error(ErrorCode.DUPLICATE_SELECTOR,
                    "strategy S4 is refined by either one `hn` predicate or one or more `has` / "
                        + "`has-gloss` predicates, and not by both",
                    step.position());
            }
            for (Predicate.Comparison c : comparisons) {
                if (!c.property().name().equals("form")) {
                    error(ErrorCode.PREDICATE_NOT_ALLOWED_IN_UNIQUE_SELECTOR,
                        "a unique selector on an `entry` uses a qualified `form`, refined only by "
                            + "`hn`, `has` or `has-gloss`; `" + c.property().name()
                            + "` is an addition to the strategy",
                        c.position());
                }
            }
            return;
        }

        if (def.hasEmptyIdentity()) {
            error(ErrorCode.INCOMPLETE_SELECTOR,
                "a `" + def.name() + "` has an empty natural identity property set, so no "
                    + "identity-based selection is available for it",
                step.position());
            return;
        }

        Set<String> identity = new HashSet<>(def.naturalIdentity());
        Set<String> given = new HashSet<>();
        for (Predicate.Comparison c : comparisons) {
            String name = c.property().name();
            if (identity.contains(name)) {
                if (!given.add(name)) {
                    error(ErrorCode.DUPLICATE_SELECTOR,
                        "a multitext identity property must be given exactly one qualified value "
                            + "in a unique selector: strategy S3 is an identity key for one "
                            + "language, and two languages would be two keys",
                        c.position());
                }
            } else {
                error(ErrorCode.PREDICATE_NOT_ALLOWED_IN_UNIQUE_SELECTOR,
                    "a unique selector uses exactly one selection strategy and carries no "
                        + "additional predicate; `" + name + "` is not part of the natural "
                        + "identity of a `" + def.name() + "`",
                    c.position());
            }
        }
        if (!given.containsAll(identity)) {
            List<String> missing = new ArrayList<>(identity);
            missing.removeAll(given);
            error(ErrorCode.INCOMPLETE_SELECTOR,
                "strategy S3 gives every property of the natural identity of a `" + def.name()
                    + "`, " + def.naturalIdentity() + "; " + missing + " missing",
                step.position());
        }
        if (hasHn) {
            error(ErrorCode.COMMAND_NOT_ALLOWING_HN,
                "`hn` applies to an `entry` only", step.position());
        }
    }

    // ===================================================================
    // The at clause
    // ===================================================================

    private void validateAtClause(AtClause at, ComponentTypeDef def, SourcePosition position) {
        if (at == null) {
            return;
        }
        if (Metamodel.ENTRY.equals(def.name())) {
            error(ErrorCode.SYNTAX_ERROR,
                "the dictionary, not the script, orders the entry list, so there is no position "
                    + "for an `at` clause to designate",
                at.position());
            return;
        }
        if (!def.isOrdered()) {
            error(ErrorCode.COMPONENT_NOT_ORDERED,
                "a `" + def.name() + "` lives in no ordered list, so an `at` clause names a "
                    + "position that does not exist",
                at.position());
            return;
        }
        if (at.kind() == AtClause.Kind.INDEX && at.index() != null && at.index() < 1) {
            error(ErrorCode.ILLEGAL_ORDINAL, "an index starts at 1", at.position());
        }
        if (at.step() instanceof Step.Component c && c.componentType() != null
            && !c.componentType().equals(def.name())) {
            error(ErrorCode.SYNTAX_ERROR,
                "the step of an `at before` / `at after` clause names a sibling of the component "
                    + "being placed, and a `" + c.componentType() + "` is not one of a `"
                    + def.name() + "`",
                c.position());
            return;
        }
        if (at.step() != null) {
            validateStep(at.step(), SelectorKind.UNIQUE, null);
        }
    }

    // ===================================================================
    // Values and qualifiers
    // ===================================================================

    private void checkValue(
        Value value, PropertyDef p, PropertyRef ref, Set<String> seen, SourcePosition position
    ) {
        if (value instanceof Value.Multitext literal) {
            if (!p.datatype().isMultitext()) {
                error(ErrorCode.LANG_KEY_NOT_SUPPORTED_ON_SCALAR,
                    "a multi-language literal assigns qualified values, and `" + p.qualifiedName()
                        + "` is a scalar property",
                    position);
                return;
            }
            if (ref.wildcard()) {
                error(ErrorCode.WILDCARD_NOT_ALLOWED,
                    "a multi-language literal carries its own language keys", ref.position());
                return;
            }
            if (ref.language() != null) {
                error(ErrorCode.QUALIFIER_ON_MULTITEXT_LITERAL,
                    "a multi-language literal carries its own language keys, and a qualifier in "
                        + "front of it can only contradict them",
                    ref.position());
                return;
            }
            for (LangText entry : literal.entries()) {
                if (!seen.add(p.name() + "@" + entry.language())) {
                    error(ErrorCode.DUPLICATE_PROPERTY,
                        "`" + p.name() + "@" + entry.language() + "` is assigned twice",
                        entry.position());
                }
            }
            return;
        }

        if (p.datatype() == Datatype.REFERENCE) {
            checkReferenceValue(value, position);
        } else if (value instanceof Value.ChainRef || value instanceof Value.LabelRef) {
            error(ErrorCode.REFERENCE_VALUE_MUST_BE_A_CHAIN,
                "`" + p.qualifiedName() + "` is not a reference property, so its value is a "
                    + "literal and not a component",
                position);
        }
        if (!seen.add(ref.toString())) {
            error(ErrorCode.DUPLICATE_PROPERTY,
                "`" + ref + "` is assigned twice in one command", position);
        }
    }

    private void checkPredicateValue(Value value, PropertyDef p, SourcePosition position) {
        if (p.datatype() == Datatype.REFERENCE) {
            checkReferenceValue(value, position);
        }
    }

    /**
     * A reference is a link between components, not a string field: its value is a
     * chain or a label, and the component it designates is an entry or a sense
     * (Part 2, section 10).
     */
    private void checkReferenceValue(Value value, SourcePosition position) {
        switch (value) {
            case Value.ChainRef chain -> {
                String type = stepType(chain.chain().head());
                if (type != null && !isIdBearingName(type)) {
                    error(ErrorCode.INVALID_TARGET,
                        "the value of a reference property is an `entry` or a `sense`, and a `"
                            + type + "` is neither",
                        position);
                }
            }
            case Value.LabelRef label -> {
                if (!labels.isBound(label.name())) {
                    error(ErrorCode.UNKNOWN_LABEL,
                        "no visible command has bound `$" + label.name() + "`", position);
                    return;
                }
                labels.typeOf(label.name()).ifPresent(type -> {
                    if (!isIdBearingName(type)) {
                        error(ErrorCode.INVALID_TARGET,
                            "the label `$" + label.name() + "` denotes a `" + type
                                + "`, and the value of a reference property is an `entry` or a "
                                + "`sense`",
                            position);
                    }
                });
            }
            default -> error(ErrorCode.REFERENCE_VALUE_MUST_BE_A_CHAIN,
                "a bare string is not a reference value: to assign a known id, select by id, as "
                    + "in `target = sense[id = \"pig-44\"]`",
                position);
        }
    }

    /**
     * Check the language qualifier of a property reference against its datatype.
     *
     * @return {@code false} when the qualifier is illegal, so that the caller stops
     *         checking that reference
     */
    private boolean checkQualifier(PropertyRef ref, PropertyDef p, boolean allowWildcard) {
        if (!ref.hasQualifier()) {
            return true;
        }
        if (!p.datatype().isMultitext()) {
            error(ErrorCode.LANG_KEY_NOT_SUPPORTED_ON_SCALAR,
                "`" + p.qualifiedName() + "` is a `" + p.datatype().jsonName()
                    + "` property and takes no language key",
                ref.position());
            return false;
        }
        if (ref.wildcard() && !allowWildcard) {
            error(ErrorCode.WILDCARD_NOT_ALLOWED,
                "`@*` is a reading and a removing qualifier, not an assigning one",
                ref.position());
            return false;
        }
        return true;
    }

    // ===================================================================
    // Small helpers
    // ===================================================================

    private void validateParentage(String parentType, String childType, SourcePosition position) {
        if (parentType == null || childType == null) {
            return;
        }
        if (!metamodel.isLegalParent(parentType, childType)) {
            error(ErrorCode.ILLEGAL_PARENT,
                "the metamodel does not relate a `" + parentType + "` and a `" + childType
                    + "` as parent and child",
                position);
        }
    }

    private Optional<ComponentTypeDef> componentType(String name, SourcePosition position) {
        if (name == null) {
            return Optional.empty();
        }
        Optional<ComponentTypeDef> def = metamodel.componentType(name);
        if (def.isEmpty()) {
            error(ErrorCode.SYNTAX_ERROR,
                "the metamodel declares no component type called `" + name + "`", position);
        }
        return def;
    }

    private Optional<PropertyDef> property(
        ComponentTypeDef def, PropertyRef ref, SourcePosition position
    ) {
        Optional<PropertyDef> p = def.property(ref.name());
        if (p.isEmpty()) {
            error(ErrorCode.PROPERTY_DOES_NOT_EXIST_ON_COMPONENT_TYPE,
                "the metamodel defines no property `" + ref.name() + "` on a `" + def.name() + "`",
                position);
        }
        return p;
    }

    private static String stepType(Step step) {
        return step instanceof Step.Component c ? c.componentType() : null;
    }

    private static Multiplicity multiplicityOf(Step step) {
        return step.multiplicity();
    }

    private boolean isIdBearing(ComponentTypeDef def) {
        return isIdBearingName(def.name());
    }

    private static boolean isIdBearingName(String type) {
        return Metamodel.ENTRY.equals(type) || "sense".equals(type);
    }

    private void bindLabel(String name, String componentType, SourcePosition position) {
        if (name == null) {
            return;
        }
        if (labels.isBound(name)) {
            error(ErrorCode.DUPLICATE_LABEL,
                "`$" + name + "` is already bound in this scope", position);
            return;
        }
        labels.bind(name, componentType);
    }

    private void error(ErrorCode code, String message, SourcePosition position) {
        errors.add(code, message, position, operationIndex);
    }

    /**
     * Whether a component kind forbids the multiplicity keyword on a given verb.
     *
     * @param verb the verb
     * @param step the target step
     * @param position where to report
     */
    private void checkMultiplicity(Verb verb, Step step, SourcePosition position) {
        if (step.multiplicity() != null && !verb.allowsMultiplicity()) {
            error(ErrorCode.MULTIPLICITY_NOT_ALLOWED,
                "`" + verb.keyword() + "` operates on exactly one component", position);
        }
    }
}
