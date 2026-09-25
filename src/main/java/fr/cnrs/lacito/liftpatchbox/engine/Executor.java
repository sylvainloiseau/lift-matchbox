package fr.cnrs.lacito.liftpatchbox.engine;

import fr.cnrs.lacito.liftpatchbox.ast.Assignment;
import fr.cnrs.lacito.liftpatchbox.ast.AtClause;
import fr.cnrs.lacito.liftpatchbox.ast.Block;
import fr.cnrs.lacito.liftpatchbox.ast.BlockHeader;
import fr.cnrs.lacito.liftpatchbox.ast.Chain;
import fr.cnrs.lacito.liftpatchbox.ast.Command;
import fr.cnrs.lacito.liftpatchbox.ast.Constructor;
import fr.cnrs.lacito.liftpatchbox.ast.Directive;
import fr.cnrs.lacito.liftpatchbox.ast.Initializer;
import fr.cnrs.lacito.liftpatchbox.ast.Item;
import fr.cnrs.lacito.liftpatchbox.ast.LangText;
import fr.cnrs.lacito.liftpatchbox.ast.Predicate;
import fr.cnrs.lacito.liftpatchbox.ast.PropertyRef;
import fr.cnrs.lacito.liftpatchbox.ast.Script;
import fr.cnrs.lacito.liftpatchbox.ast.Step;
import fr.cnrs.lacito.liftpatchbox.ast.Value;
import fr.cnrs.lacito.liftpatchbox.ast.Verb;
import fr.cnrs.lacito.liftpatchbox.error.ErrorCode;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchError;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchException;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchWarning;
import fr.cnrs.lacito.liftpatchbox.error.SourcePosition;
import fr.cnrs.lacito.liftpatchbox.error.WarningCode;
import fr.cnrs.lacito.liftpatchbox.metamodel.ComponentTypeDef;
import fr.cnrs.lacito.liftpatchbox.metamodel.LanguageKind;
import fr.cnrs.lacito.liftpatchbox.metamodel.Metamodel;
import fr.cnrs.lacito.liftpatchbox.metamodel.PropertyDef;
import fr.cnrs.lacito.liftpatchbox.model.ComponentRef;
import fr.cnrs.lacito.liftpatchbox.model.Journal;
import fr.cnrs.lacito.liftpatchbox.model.LiftModel;
import fr.cnrs.lacito.liftpatchbox.model.UnsupportedByModelException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs a validated script against a dictionary.
 *
 * <p>The commands of a script are executed in source order, and each observes the
 * state left by all the preceding ones (Part 2, section 12.1). The whole script
 * is the unit of atomicity: if any command fails, every change already applied is
 * rolled back through the {@link Journal} and the dictionary is left exactly as it
 * was (section 12.2).</p>
 *
 * <p>Plan mode is the same run with the same journal, rolled back at the end
 * instead of committed. That is what guarantees what Appendix D.1 requires: the
 * effects a plan reports and the effects applying the script produces are the
 * same list, because they are produced by the same code.</p>
 */
public final class Executor {

    private static final Logger LOGGER = Logger.getLogger(Executor.class.getName());

    private final LiftModel model;
    private final Metamodel metamodel;
    private final Journal journal;
    private final LanguageScope languages;
    private final Resolver resolver;

    private final List<Operation> operations = new ArrayList<>();
    private final List<LiftPatchWarning> warnings = new ArrayList<>();
    private final Summary summary = new Summary();
    private final Map<String, DeletedTarget> deleted = new LinkedHashMap<>();

    private int operationIndex;

    /**
     * An executor over one dictionary.
     *
     * @param model     the dictionary adapter
     * @param metamodel the metamodel
     * @param journal   the journal that makes the run atomic
     */
    public Executor(LiftModel model, Metamodel metamodel, Journal journal) {
        this.model = model;
        this.metamodel = metamodel;
        this.journal = journal;
        this.languages = new LanguageScope(model);
        this.resolver = new Resolver(model, metamodel, languages);
    }

    /**
     * Bind the default language of one kind for the whole script, overriding the
     * dictionary's first language of that kind.
     *
     * <p>Part 1, section 2.1 makes the first language of the dictionary's list the
     * fallback; a caller that knows better states it here, and a
     * {@code language-default} directive or a {@code with} header in the script
     * still overrides it, since those bind in an inner scope.</p>
     *
     * @param kind     the language kind
     * @param language the language code
     */
    public void bindDefaultLanguage(LanguageKind kind, String language) {
        languages.bind(kind, language);
    }

    /** A component a {@code delete} removed, remembered so that plan mode can report dangling references. */
    private record DeletedTarget(int operationIndex, String verb) {
    }

    /**
     * Run a script.
     *
     * @param script   the validated script
     * @param planOnly whether to roll the run back at the end instead of committing
     *                 it, which is what plan mode does
     * @return the plan document describing what the script did, or would do
     */
    public Plan run(Script script, boolean planOnly) {
        operationIndex = 0;
        LiftPatchError failure = null;
        if (script.recognizedLines() == 0) {
            warn(LiftPatchWarning.document(WarningCode.NO_COMMAND_RECOGNIZED,
                "not one line of " + script.source() + " was recognized as a command; if this is "
                    + "a reference-syntax script, declare it with %liftpatch 1.0 "
                    + "syntax=\"LiftPatchRef\" or pass the syntax explicitly"));
        }
        try {
            executeItems(script.items(), null);
        } catch (LiftPatchException e) {
            failure = e.firstError();
            operations.add(new Operation(
                failure.operationIndex(), failure.position(), null, null, null,
                null, null, List.of(), null, failure));
        } catch (UnsupportedByModelException e) {
            failure = new LiftPatchError(ErrorCode.UNSUPPORTED_BY_DICTIONARY_MODEL,
                e.getMessage(), SourcePosition.UNKNOWN, operationIndex);
            operations.add(new Operation(
                operationIndex, SourcePosition.UNKNOWN, null, null, null,
                null, null, List.of(), null, failure));
        }

        if (failure != null) {
            LOGGER.log(Level.SEVERE, failure::format);
        }
        List<DanglingReference> dangling = failure == null ? findDanglingReferences() : List.of();

        if (planOnly || failure != null) {
            journal.rollback();
        } else {
            journal.commit();
        }
        LOGGER.log(Level.FINE, () -> (planOnly ? "Planned " : "Applied ")
            + operations.size() + " operation(s) on " + script.source());

        return new Plan(
            script.source(),
            script.syntax(),
            script.allCommands().size(),
            metamodel.version(),
            metamodel.metamodelId(),
            failure == null ? "ok" : "error",
            failure == null ? List.of() : List.of(failure),
            warnings,
            operations,
            dangling,
            summary);
    }

    // ===================================================================
    // Items
    // ===================================================================

    private void executeItems(List<Item> items, ComponentRef blockComponent) {
        for (Item item : items) {
            switch (item) {
                case Command c -> executeCommand(c, blockComponent);
                case Directive d -> executeDirective(d);
                case Block b -> executeBlock(b, blockComponent);
            }
        }
    }

    private void executeBlock(Block block, ComponentRef blockComponent) {
        int mark = journal.mark();
        languages.push();
        try {
            ComponentRef component = switch (block.header()) {
                case BlockHeader.With with -> {
                    for (Map.Entry<LanguageKind, String> e : with.bindings().entrySet()) {
                        resolver.requireLanguage(e.getKey(), e.getValue(), with.position());
                        languages.bind(e.getKey(), e.getValue());
                    }
                    yield blockComponent;
                }
                case BlockHeader.Selector selector -> {
                    int index = ++operationIndex;
                    resolver.setOperationIndex(index);
                    ComponentRef resolved =
                        resolver.resolveOne(selector.chain(), blockComponent);
                    if (selector.label() != null) {
                        resolver.bindLabel(selector.label(), resolved);
                    }
                    operations.add(new Operation(index, selector.position(),
                        selector.chain().toString(), "select", null,
                        targetInfo(resolved, selector.label()),
                        targetInfo(model.parentOf(resolved), null),
                        List.of(), null, null));
                    yield resolved;
                }
                case BlockHeader.Anchor anchor -> executeCommand(anchor.command(), blockComponent);
            };
            executeItems(block.body(), component);
        } catch (RuntimeException e) {
            // A block is a nested transaction boundary: a failing child rolls the
            // block back, and the block then fails the script around it.
            journal.rollbackTo(mark);
            throw e;
        } finally {
            languages.pop();
        }
    }

    private void executeDirective(Directive directive) {
        if (!directive.create()) {
            resolver.requireLanguage(directive.kind(), directive.language(), directive.position());
            languages.bind(directive.kind(), directive.language());
            return;
        }
        int index = ++operationIndex;
        resolver.setOperationIndex(index);
        if (model.hasLanguage(directive.kind(), directive.language())) {
            throw resolver.error(ErrorCode.LANGUAGE_ALREADY_EXISTS,
                "the dictionary already has the " + directive.kind().jsonName() + " language `"
                    + directive.language() + "`",
                directive.position());
        }
        model.createLanguage(directive.kind(), directive.language());
        languages.bind(directive.kind(), directive.language());
        Effect effect = Effect.languageCreated(directive.kind().jsonName(), directive.language());
        summary.count(effect);
        operations.add(new Operation(index, directive.position(), directive.toString(),
            "language-create", null, null, null, List.of(effect), null, null));
    }

    // ===================================================================
    // Commands
    // ===================================================================

    private ComponentRef executeCommand(Command command, ComponentRef blockComponent) {
        int index = ++operationIndex;
        resolver.setOperationIndex(index);
        summary.countOperation(command.verb());
        return switch (command) {
            case Command.Create c -> executeCreate(c, blockComponent, index);
            case Command.Upsert u -> executeUpsert(u, blockComponent, index);
            case Command.Ensure e -> executeEnsure(e, blockComponent, index);
            case Command.Delete d -> executeDelete(d, blockComponent, index);
            case Command.Move m -> executeMove(m, blockComponent, index);
            case Command.Set s -> executeProperty(
                s.verb(), s.assignments(), List.of(), s.target(), blockComponent, index, s);
            case Command.Update u -> executeProperty(
                u.verb(), u.assignments(), List.of(), u.target(), blockComponent, index, u);
            case Command.Clear c -> executeProperty(
                c.verb(), List.of(), c.properties(), c.target(), blockComponent, index, c);
        };
    }

    // ------------------------------------------------------------ create

    private ComponentRef executeCreate(
        Command.Create command, ComponentRef blockComponent, int index
    ) {
        ComponentRef parent = resolveParent(
            command.parent(), command.constructor().componentType(), blockComponent);
        List<Effect> effects = new ArrayList<>();
        ComponentRef created = build(parent, command.constructor(), command.at(), effects);
        if (command.label() != null) {
            resolver.bindLabel(command.label(), created);
        }
        effects.forEach(summary::count);
        operations.add(new Operation(index, command.position(), command.sourceText(),
            "create", null, targetInfo(created, command.label()), targetInfo(parent, null),
            effects, null, null));
        return created;
    }

    /**
     * Create one component and, recursively, the children its embedded initializers
     * ask for.
     */
    private ComponentRef build(
        ComponentRef parent, Constructor constructor, AtClause at, List<Effect> effects
    ) {
        String type = constructor.componentType();
        ComponentTypeDef def = metamodel.requireComponentType(type);

        Map<String, String> scalars = new LinkedHashMap<>();
        Map<String, Map<String, String>> multitexts = new LinkedHashMap<>();
        List<Effect> written = new ArrayList<>();
        collectInitializers(constructor, def, scalars, multitexts, written);

        Integer position = creationPosition(at, parent, def);
        ComponentRef created =
            model.createChild(parent, new LiftModel.NewComponent(type, scalars, multitexts), position);
        checkUniquenessInvariant(parent, created, def, constructor.position());

        effects.add(Effect.created(type, model.positionOf(created).orElse(null)));
        effects.addAll(written);

        for (Constructor child : constructor.embedded()) {
            build(created, child, null, effects);
        }
        return created;
    }

    /**
     * Resolve the initializer list into the scalars and multitexts the model
     * adapter needs, and the effects the plan reports, in the order written.
     */
    private void collectInitializers(
        Constructor constructor,
        ComponentTypeDef def,
        Map<String, String> scalars,
        Map<String, Map<String, String>> multitexts,
        List<Effect> effects
    ) {
        for (Initializer.Assignment assignment : constructor.assignments()) {
            PropertyRef ref = assignment.property();
            PropertyDef property = def.property(ref.name()).orElseThrow(() ->
                resolver.error(ErrorCode.PROPERTY_DOES_NOT_EXIST_ON_COMPONENT_TYPE,
                    "the metamodel defines no property `" + ref.name() + "` on a `"
                        + def.name() + "`",
                    ref.position()));
            if (!property.datatype().isMultitext()) {
                String value =
                    resolver.scalarValueOf(assignment.value(), property, assignment.position());
                scalars.put(property.name(), value);
                effects.add(Effect.written(property.name(), null, null, value));
                continue;
            }
            for (Map.Entry<String, String> qualified
                : qualifiedValues(ref, property, assignment.value(), assignment.position()).entrySet()) {
                multitexts
                    .computeIfAbsent(property.name(), k -> new LinkedHashMap<>())
                    .put(qualified.getKey(), qualified.getValue());
                effects.add(Effect.written(
                    property.name(), qualified.getKey(), null, qualified.getValue()));
            }
        }
    }

    /**
     * The qualified values one assignment writes: one for an ordinary assignment,
     * several for a multi-language literal, which is pure surface sugar for the
     * sequence of the corresponding single-qualifier assignments.
     */
    private Map<String, String> qualifiedValues(
        PropertyRef ref, PropertyDef property, Value value, SourcePosition position
    ) {
        Map<String, String> out = new LinkedHashMap<>();
        if (value instanceof Value.Multitext literal) {
            for (LangText entry : literal.entries()) {
                resolver.requireLanguage(property.qualifier(), entry.language(), entry.position());
                out.put(entry.language(), entry.text());
            }
            return out;
        }
        String language = resolver.languageOf(ref, property, position);
        String text = value instanceof Value.Str s ? s.text()
            : value instanceof Value.Num n ? Long.toString(n.value())
            : null;
        if (text == null) {
            throw resolver.error(ErrorCode.SYNTAX_ERROR,
                "a multitext property takes a literal value", position);
        }
        out.put(language, text);
        return out;
    }

    // ------------------------------------------------------------ upsert

    private ComponentRef executeUpsert(
        Command.Upsert command, ComponentRef blockComponent, int index
    ) {
        Constructor constructor = command.constructor();
        ComponentTypeDef def = metamodel.requireComponentType(constructor.componentType());
        ComponentRef parent =
            resolveParent(command.parent(), constructor.componentType(), blockComponent);

        List<ComponentRef> matches = upsertMatches(parent, constructor, def);
        if (matches.size() > 1) {
            throw resolver.error(ErrorCode.AMBIGUOUS_REFERENCE,
                matches.size() + " components match the upsert, which must designate one",
                constructor.position());
        }

        List<Effect> effects = new ArrayList<>();
        ComponentRef component;
        if (matches.size() == 1) {
            // Select branch: no component is created, and only the assignment part
            // `A` is applied, with `set` semantics. The match part `K` is what found
            // the component and is never written again -- which is what makes a
            // second run of an `upsert` change nothing.
            component = matches.get(0);
            List<String> matchProperties = matchPropertiesOf(def);
            for (Initializer.Assignment assignment : constructor.assignments()) {
                if (matchProperties.contains(assignment.property().name())) {
                    continue;
                }
                effects.addAll(applySet(component, def, assignment));
            }
        } else {
            component = build(parent, constructor, command.at(), effects);
        }
        if (command.label() != null) {
            resolver.bindLabel(command.label(), component);
        }
        effects.forEach(summary::count);
        operations.add(new Operation(index, command.position(), command.sourceText(),
            "upsert", null, targetInfo(component, command.label()), targetInfo(parent, null),
            effects, null, null));
        return component;
    }

    /**
     * The set {@code M} of Part 2, section 8.2.1: the children of the resolved
     * parent that match the match part and the disambiguation part.
     *
     * <p>For an {@code entry}, whose natural identity property set is empty, the
     * match is performed on the qualified {@code form} values given in the
     * parenthesis, refined by the {@code has} and {@code has-gloss} predicates.</p>
     */
    private List<ComponentRef> upsertMatches(
        ComponentRef parent, Constructor constructor, ComponentTypeDef def
    ) {
        List<String> matchProperties = matchPropertiesOf(def);
        List<Predicate> conditions = constructor.conditions();

        List<ComponentRef> out = new ArrayList<>();
        for (ComponentRef candidate : model.children(parent, def.name())) {
            if (matchesUpsertKey(candidate, constructor, def, matchProperties)
                && resolver.matches(candidate, conditions)) {
                out.add(candidate);
            }
        }
        return out;
    }

    private boolean matchesUpsertKey(
        ComponentRef candidate, Constructor constructor, ComponentTypeDef def,
        List<String> matchProperties
    ) {
        if (matchProperties.isEmpty()) {
            return false;
        }
        for (String name : matchProperties) {
            PropertyDef property = def.property(name).orElse(null);
            if (property == null) {
                return false;
            }
            Initializer.Assignment assignment = constructor.assignments().stream()
                .filter(a -> a.property().name().equals(name))
                .findFirst().orElse(null);
            if (assignment == null) {
                return false;
            }
            if (!property.datatype().isMultitext()) {
                String expected = resolver.scalarValueOf(
                    assignment.value(), property, assignment.position());
                if (!expected.equals(model.scalar(candidate, name).orElse(null))) {
                    return false;
                }
                continue;
            }
            // For a multitext identity property given with several qualified values,
            // a child matches when it agrees on at least one of them.
            Map<String, String> wanted = new LinkedHashMap<>();
            for (Initializer.Assignment a : constructor.assignments()) {
                if (a.property().name().equals(name)) {
                    wanted.putAll(qualifiedValues(a.property(), property, a.value(), a.position()));
                }
            }
            boolean agrees = false;
            for (Map.Entry<String, String> e : wanted.entrySet()) {
                if (e.getValue().equals(
                    model.qualified(candidate, name, e.getKey()).orElse(null))) {
                    agrees = true;
                    break;
                }
            }
            if (!agrees) {
                return false;
            }
        }
        return true;
    }

    /**
     * The properties an {@code upsert} matches on: the natural identity property
     * set, or, for an {@code entry}, whose set is empty, the qualified {@code form}
     * values given in the parenthesis (Part 2, section 8.2.3).
     */
    private static List<String> matchPropertiesOf(ComponentTypeDef def) {
        if (Metamodel.ENTRY.equals(def.name())) {
            return List.of("form");
        }
        return def.naturalIdentity();
    }

    // ------------------------------------------------------------ ensure

    private ComponentRef executeEnsure(
        Command.Ensure command, ComponentRef blockComponent, int index
    ) {
        ComponentRef component = resolveTarget(
            command.target(), command.parent(), blockComponent, true).get(0);
        if (command.label() != null) {
            resolver.bindLabel(command.label(), component);
        }
        operations.add(new Operation(index, command.position(), command.sourceText(),
            "ensure", null, targetInfo(component, command.label()),
            targetInfo(model.parentOf(component), null), List.of(), null, null));
        return component;
    }

    // ------------------------------------------------------------ delete

    private ComponentRef executeDelete(
        Command.Delete command, ComponentRef blockComponent, int index
    ) {
        boolean marked = command.target().multiplicity() != null;
        List<ComponentRef> targets =
            resolveTarget(command.target(), command.parent(), blockComponent, !marked);
        if (marked && targets.isEmpty()) {
            warnCommandAppliesToNoComponent(command.position(), index);
        }

        // The positions reported are those of the original list, not of the list as
        // it shrinks, so they are read before anything is removed (section 12.4.1).
        List<Integer> positions = new ArrayList<>(targets.size());
        List<TargetInfo> infos = new ArrayList<>(targets.size());
        for (ComponentRef target : targets) {
            positions.add(model.positionOf(target).orElse(null));
            infos.add(targetInfo(target, null));
        }

        List<Effect> effects = new ArrayList<>();
        List<Operation.Application> applications = marked ? new ArrayList<>() : null;
        for (int i = 0; i < targets.size(); i++) {
            ComponentRef target = targets.get(i);
            Integer position = positions.get(i);
            TargetInfo info = infos.get(i);
            rememberDeletedIds(target, index, "delete");
            model.delete(target);
            Effect effect = Effect.deleted(target.componentType(), position);
            summary.count(effect);
            if (marked) {
                applications.add(new Operation.Application(info, List.of(effect)));
            } else {
                effects.add(effect);
            }
        }
        operations.add(new Operation(index, command.position(), command.sourceText(),
            "delete", marked ? command.target().multiplicity().spelling() : null,
            marked || targets.isEmpty() ? null : targetInfo(targets.get(0), null),
            null, marked ? null : effects, applications, null));
        return targets.isEmpty() ? null : targets.get(0);
    }

    /**
     * Remember the identifiers of a subtree about to disappear, so that plan mode
     * can report the references the script leaves dangling.
     */
    private void rememberDeletedIds(ComponentRef target, int index, String verb) {
        model.idOf(target).ifPresent(id -> deleted.put(id, new DeletedTarget(index, verb)));
        ComponentTypeDef def = metamodel.componentType(target.componentType()).orElse(null);
        if (def == null) {
            return;
        }
        for (String childType : def.children()) {
            List<ComponentRef> children;
            try {
                children = model.children(target, childType);
            } catch (UnsupportedByModelException e) {
                continue;
            }
            for (ComponentRef child : children) {
                rememberDeletedIds(child, index, verb);
            }
        }
    }

    // -------------------------------------------------------------- move

    private ComponentRef executeMove(
        Command.Move command, ComponentRef blockComponent, int index
    ) {
        ComponentRef sourceParent = resolveParent(
            command.sourceParent(), stepType(command.target()), blockComponent);
        List<ComponentRef> found = resolver.resolveStepUnder(sourceParent, command.target());
        if (found.isEmpty()) {
            throw outOfRangeOrNotFound(command.target(), sourceParent);
        }
        if (found.size() > 1) {
            throw resolver.error(ErrorCode.AMBIGUOUS_REFERENCE,
                found.size() + " components match `" + command.target() + "`",
                command.target().position());
        }
        ComponentRef target = found.get(0);
        ComponentRef destination = command.destinationParent() == null
            ? sourceParent
            : resolver.resolveOne(command.destinationParent(), blockComponent);

        String type = target.componentType();
        if (!metamodel.isLegalParent(destination.componentType(), type)) {
            throw resolver.error(ErrorCode.ILLEGAL_PARENT,
                "a `" + destination.componentType() + "` cannot hold a `" + type + "`",
                command.position());
        }
        if (isAncestorOrSelf(target, destination)) {
            throw resolver.error(ErrorCode.SELF_ANCESTOR,
                "a component cannot be moved under itself or under one of its own descendants",
                command.position());
        }

        boolean sameParent = destination.equals(sourceParent);
        int from = model.positionOf(target).orElse(1);
        int siblingCount = model.children(destination, type).size();
        int upperBound = sameParent ? siblingCount : siblingCount + 1;
        int to = movePosition(command.at(), destination, type, target, upperBound);

        if (sameParent && to == from) {
            throw resolver.error(ErrorCode.MOVING_TO_CURRENT_POSITION,
                "the component already occupies position " + from, command.at().position());
        }

        model.move(target, destination, to);
        checkUniquenessInvariant(destination, target,
            metamodel.requireComponentType(type), command.position());

        Effect effect = Effect.moved(type, from, model.positionOf(target).orElse(to),
            path(sourceParent), path(destination));
        summary.count(effect);
        operations.add(new Operation(index, command.position(), command.sourceText(),
            "move", null, targetInfo(target, null), targetInfo(destination, null),
            List.of(effect), null, null));
        return target;
    }

    private boolean isAncestorOrSelf(ComponentRef candidate, ComponentRef node) {
        ComponentRef current = node;
        while (current != null) {
            if (current.equals(candidate)) {
                return true;
            }
            current = model.parentOf(current);
        }
        return false;
    }

    // -------------------------------------------- set, update and clear

    private ComponentRef executeProperty(
        Verb verb,
        List<Assignment> assignments,
        List<PropertyRef> cleared,
        Chain target,
        ComponentRef blockComponent,
        int index,
        Command command
    ) {
        boolean marked = target != null && target.head().multiplicity() != null;
        List<ComponentRef> components;
        if (target == null) {
            components = List.of(blockComponent);
        } else {
            components = resolver.resolveChain(target, !marked, blockComponent, marked);
        }
        if (marked && components.isEmpty()) {
            warnCommandAppliesToNoComponent(command.position(), index);
        }

        List<Effect> effects = new ArrayList<>();
        List<Operation.Application> applications = marked ? new ArrayList<>() : null;
        for (ComponentRef component : components) {
            ComponentTypeDef def = metamodel.requireComponentType(component.componentType());
            List<Effect> local = new ArrayList<>();
            for (Assignment assignment : assignments) {
                local.addAll(verb == Verb.SET
                    ? applySet(component, def, toInitializer(assignment))
                    : applyUpdate(component, def, assignment));
            }
            for (PropertyRef property : cleared) {
                local.addAll(applyClear(component, def, property));
            }
            checkUniquenessInvariant(model.parentOf(component), component, def, command.position());
            local.forEach(summary::count);
            if (marked) {
                applications.add(new Operation.Application(targetInfo(component, null), local));
            } else {
                effects.addAll(local);
            }
        }
        operations.add(new Operation(index, command.position(), command.sourceText(),
            verb.keyword(), marked ? target.head().multiplicity().spelling() : null,
            marked || components.isEmpty() ? null : targetInfo(components.get(0), null),
            null, marked ? null : effects, applications, null));
        return components.isEmpty() ? null : components.get(0);
    }

    private static Initializer.Assignment toInitializer(Assignment assignment) {
        return new Initializer.Assignment(
            assignment.property(), assignment.value(), assignment.position());
    }

    private List<Effect> applySet(
        ComponentRef component, ComponentTypeDef def, Initializer.Assignment assignment
    ) {
        PropertyRef ref = assignment.property();
        PropertyDef property = requireProperty(def, ref, assignment.position());
        List<Effect> effects = new ArrayList<>();
        if (!property.datatype().isMultitext()) {
            String old = model.scalar(component, property.name()).orElse(null);
            String value = resolver.scalarValueOf(
                assignment.value(), property, assignment.position());
            model.setScalar(component, property.name(), value);
            effects.add(Effect.written(property.name(), null, old, value));
            return effects;
        }
        for (Map.Entry<String, String> qualified
            : qualifiedValues(ref, property, assignment.value(), assignment.position()).entrySet()) {
            String old = model.qualified(component, property.name(), qualified.getKey())
                .orElse(null);
            model.setQualified(
                component, property.name(), qualified.getKey(), qualified.getValue());
            effects.add(Effect.written(
                property.name(), qualified.getKey(), old, qualified.getValue()));
        }
        if (Metamodel.ENTRY.equals(def.name()) && "form".equals(property.name())) {
            model.invalidateHomophones();
        }
        return effects;
    }

    private List<Effect> applyUpdate(
        ComponentRef component, ComponentTypeDef def, Assignment assignment
    ) {
        PropertyRef ref = assignment.property();
        PropertyDef property = requireProperty(def, ref, assignment.position());
        if (!property.datatype().isMultitext()) {
            if (model.scalar(component, property.name()).isEmpty()) {
                throw resolver.error(ErrorCode.UNSET_PROPERTY,
                    "`update` replaces an existing value, and `" + property.qualifiedName()
                        + "` has none; `set` is the command to use",
                    assignment.position());
            }
            return applySet(component, def, toInitializer(assignment));
        }
        for (String language
            : qualifiedValues(ref, property, assignment.value(), assignment.position()).keySet()) {
            if (model.qualified(component, property.name(), language).isEmpty()) {
                throw resolver.error(ErrorCode.UNSET_QUALIFIED_PROPERTY,
                    "`update` tests the qualified value, not the property: `" + property.name()
                        + "@" + language + "` is not set, even if another language of `"
                        + property.name() + "` is",
                    assignment.position());
            }
        }
        return applySet(component, def, toInitializer(assignment));
    }

    /**
     * Apply a {@code clear}, following the decision table of Part 2, section 9.3.1,
     * which is the only place the outcome of a {@code clear} is decided.
     */
    private List<Effect> applyClear(
        ComponentRef component, ComponentTypeDef def, PropertyRef ref
    ) {
        PropertyDef property = requireProperty(def, ref, ref.position());
        List<Effect> effects = new ArrayList<>();

        if (!property.datatype().isMultitext()) {
            String old = model.scalar(component, property.name()).orElse(null);
            if (old == null) {
                throw resolver.error(ErrorCode.UNSET_PROPERTY,
                    "`" + property.qualifiedName() + "` has no value to remove", ref.position());
            }
            model.setScalar(component, property.name(), null);
            effects.add(Effect.removed(property.name(), null, old));
            return effects;
        }

        boolean protectedMultitext = property.required() || property.identity();
        Set<String> present = new LinkedHashSet<>(model.languagesOf(component, property.name()));

        if (ref.wildcard()) {
            if (protectedMultitext) {
                throw resolver.error(ErrorCode.CANNOT_CLEAR_REQUIRED_MULTITEXT,
                    "`clear " + property.name() + "@*` would empty a required or identity "
                        + "multitext, and such a property may never become unset",
                    ref.position());
            }
            if (present.isEmpty()) {
                throw resolver.error(ErrorCode.UNSET_PROPERTY,
                    "`" + property.qualifiedName() + "` has no value to remove", ref.position());
            }
            for (String language : present) {
                String old = model.qualified(component, property.name(), language).orElse(null);
                model.removeQualified(component, property.name(), language);
                effects.add(Effect.removed(property.name(), language, old));
            }
            return effects;
        }

        String language = resolver.languageOf(ref, property, ref.position());
        if (!present.contains(language)) {
            throw resolver.error(ErrorCode.UNSET_QUALIFIED_PROPERTY,
                "`" + property.name() + "@" + language + "` is not set", ref.position());
        }
        if (protectedMultitext && present.size() == 1) {
            throw resolver.error(ErrorCode.CANNOT_CLEAR_REQUIRED_MULTITEXT,
                "`" + property.qualifiedName() + "` is required and `" + language
                    + "` is its only value; `clear` is refused rather than allowed to produce a "
                    + "component that `create` would have rejected",
                ref.position());
        }
        String old = model.qualified(component, property.name(), language).orElse(null);
        model.removeQualified(component, property.name(), language);
        effects.add(Effect.removed(property.name(), language, old));
        if (Metamodel.ENTRY.equals(def.name()) && "form".equals(property.name())) {
            model.invalidateHomophones();
        }
        return effects;
    }

    private PropertyDef requireProperty(
        ComponentTypeDef def, PropertyRef ref, SourcePosition position
    ) {
        return def.property(ref.name()).orElseThrow(() -> resolver.error(
            ErrorCode.PROPERTY_DOES_NOT_EXIST_ON_COMPONENT_TYPE,
            "the metamodel defines no property `" + ref.name() + "` on a `" + def.name() + "`",
            position));
    }

    // ===================================================================
    // Parents, targets and positions
    // ===================================================================

    private ComponentRef resolveParent(
        Chain parent, String componentType, ComponentRef blockComponent
    ) {
        if (parent != null) {
            return resolver.resolveOne(parent, blockComponent);
        }
        if (Metamodel.ENTRY.equals(componentType)) {
            return model.root();
        }
        return blockComponent;
    }

    private List<ComponentRef> resolveTarget(
        Step target, Chain parent, ComponentRef blockComponent, boolean unique
    ) {
        ComponentRef resolvedParent =
            resolveParent(parent, stepType(target), blockComponent);
        if (target instanceof Step.Label label) {
            return List.of(resolver.label(label.name(), label.position()));
        }
        if (resolvedParent == null) {
            throw resolver.error(ErrorCode.MISSING_PARENT_CLAUSE,
                "the command names no parent", target.position());
        }
        List<ComponentRef> found = resolver.resolveStepUnder(resolvedParent, target);
        if (found.isEmpty() && unique) {
            throw outOfRangeOrNotFound(target, resolvedParent);
        }
        if (unique && found.size() > 1) {
            throw resolver.error(ErrorCode.AMBIGUOUS_REFERENCE,
                found.size() + " components match `" + target + "`", target.position());
        }
        return found;
    }

    private static String stepType(Step step) {
        return step instanceof Step.Component c ? c.componentType() : null;
    }

    /**
     * The error a target step that matched nothing under one resolved parent
     * raises: {@code INDEX_OUT_OF_BOUNDS} when it is an ordinal beyond the sibling
     * list, and the ordinary {@code NOT_FOUND} otherwise.
     */
    private LiftPatchException outOfRangeOrNotFound(Step target, ComponentRef parent) {
        if (target instanceof Step.Component c && c.ordinal() != null) {
            int count = model.children(parent, c.componentType()).size();
            if (c.ordinal() > count) {
                return resolver.error(ErrorCode.INDEX_OUT_OF_BOUNDS,
                    "the ordinal " + c.ordinal() + " is greater than the " + count
                        + " same-type sibling(s) under this parent",
                    target.position());
            }
        }
        return resolver.error(ErrorCode.NOT_FOUND,
            "no component matches `" + target + "`", target.position());
    }

    /**
     * Where a newly created component goes among its same-type siblings.
     *
     * @return the 1-based position, or {@code null} when the type has none
     */
    private Integer creationPosition(AtClause at, ComponentRef parent, ComponentTypeDef def) {
        if (!def.isOrdered() || Metamodel.ENTRY.equals(def.name())) {
            return null;
        }
        List<ComponentRef> siblings = model.children(parent, def.name());
        int count = siblings.size();
        if (at == null) {
            return count + 1;
        }
        return switch (at.kind()) {
            case BEGINNING -> 1;
            case END -> count + 1;
            case INDEX -> {
                int n = at.index();
                if (n > count + 1) {
                    throw resolver.error(ErrorCode.INDEX_OUT_OF_BOUNDS,
                        "the valid insertion range is 1.." + (count + 1), at.position());
                }
                yield n;
            }
            case BEFORE -> siblingIndex(siblings, at, def) + 1;
            case AFTER -> siblingIndex(siblings, at, def) + 2;
        };
    }

    private int movePosition(
        AtClause at, ComponentRef destination, String type, ComponentRef moving, int upperBound
    ) {
        List<ComponentRef> siblings = model.children(destination, type);
        return switch (at.kind()) {
            case BEGINNING -> 1;
            case END -> upperBound;
            case INDEX -> {
                int n = at.index();
                if (n > upperBound) {
                    throw resolver.error(ErrorCode.INDEX_OUT_OF_BOUNDS,
                        "the valid destination range is 1.." + upperBound, at.position());
                }
                yield n;
            }
            case BEFORE -> {
                int index = siblingIndexFor(siblings, at, type, moving);
                yield index + 1;
            }
            case AFTER -> {
                int index = siblingIndexFor(siblings, at, type, moving);
                yield index + 2;
            }
        };
    }

    /**
     * The 0-based index of the sibling an {@code at before} / {@code at after}
     * clause names. The step is resolved among the same-type siblings under the
     * destination parent, and nowhere else (Part 1, section 6.2.1).
     */
    private int siblingIndex(List<ComponentRef> siblings, AtClause at, ComponentTypeDef def) {
        return siblingIndexFor(siblings, at, def.name(), null);
    }

    private int siblingIndexFor(
        List<ComponentRef> siblings, AtClause at, String type, ComponentRef moving
    ) {
        Step step = at.step();
        List<ComponentRef> matches = new ArrayList<>();
        if (step instanceof Step.Component component) {
            Step.Component typed = component.componentType() == null
                ? new Step.Component(type, component.predicates(), component.ordinal(),
                    component.typeKey(), null, component.position())
                : component;
            if (typed.ordinal() != null) {
                if (typed.ordinal() > siblings.size()) {
                    throw resolver.error(ErrorCode.INDEX_OUT_OF_BOUNDS,
                        "the ordinal " + typed.ordinal() + " is greater than the "
                            + siblings.size() + " same-type sibling(s)",
                        at.position());
                }
                matches.add(siblings.get(typed.ordinal() - 1));
            } else {
                for (ComponentRef sibling : siblings) {
                    if (resolver.matches(sibling, typed.predicates())) {
                        matches.add(sibling);
                    }
                }
            }
        }
        if (matches.isEmpty()) {
            throw resolver.error(ErrorCode.NOT_FOUND,
                "the `at` clause names no sibling of the component being placed", at.position());
        }
        if (matches.size() > 1) {
            throw resolver.error(ErrorCode.AMBIGUOUS_REFERENCE,
                "the `at` clause names " + matches.size() + " siblings and must name one",
                at.position());
        }
        ComponentRef sibling = matches.get(0);
        if (moving != null && sibling.equals(moving)) {
            throw resolver.error(ErrorCode.MOVING_TO_CURRENT_POSITION,
                "the `at` clause names the component being moved, which asks for the position it "
                    + "already occupies",
                at.position());
        }
        return siblings.indexOf(sibling);
    }

    // ===================================================================
    // The uniqueness invariant
    // ===================================================================

    /**
     * Check the uniqueness invariant of Part 1, section 5.2: under a given parent,
     * for every language, no two same-type siblings may have the same defined
     * identity key.
     *
     * <p>The check runs after the write, and a failure propagates out of the
     * command, which the journal then undoes — so a refused command changes nothing,
     * exactly as if the check had run first.</p>
     */
    private void checkUniquenessInvariant(
        ComponentRef parent, ComponentRef component, ComponentTypeDef def, SourcePosition position
    ) {
        if (parent == null || def.hasEmptyIdentity()) {
            return;
        }
        List<String> identity = def.naturalIdentity();
        Set<String> languages = new LinkedHashSet<>();
        boolean hasMultitextKey = false;
        for (String name : identity) {
            PropertyDef property = def.property(name).orElse(null);
            if (property != null && property.datatype().isMultitext()) {
                hasMultitextKey = true;
                languages.addAll(model.languagesOf(component, name));
            }
        }
        if (!hasMultitextKey) {
            languages.add(null);
        }

        for (String language : languages) {
            List<String> key = identityKey(component, def, identity, language);
            if (key == null) {
                continue;
            }
            for (ComponentRef sibling : model.children(parent, def.name())) {
                if (sibling.equals(component)) {
                    continue;
                }
                if (key.equals(identityKey(sibling, def, identity, language))) {
                    throw resolver.error(ErrorCode.CANNOT_CREATE_DUPLICATE,
                        "two `" + def.name() + "` siblings would share the identity key "
                            + key + (language == null ? "" : " for the language `" + language + "`"),
                        position);
                }
            }
        }
    }

    /**
     * The identity key of a component for one language, or {@code null} when the key
     * is undefined, which happens when a multitext identity property has no value
     * for that language. An undefined key never takes part in any comparison.
     */
    private List<String> identityKey(
        ComponentRef component, ComponentTypeDef def, List<String> identity, String language
    ) {
        List<String> key = new ArrayList<>(identity.size());
        for (String name : identity) {
            PropertyDef property = def.property(name).orElse(null);
            if (property == null) {
                return null;
            }
            Optional<String> value = property.datatype().isMultitext()
                ? model.qualified(component, name, language)
                : model.scalar(component, name);
            if (value.isEmpty()) {
                return null;
            }
            key.add(value.get());
        }
        return key;
    }

    // ===================================================================
    // Plan-document helpers
    // ===================================================================

    private void warnCommandAppliesToNoComponent(SourcePosition position, int index) {
        warn(new LiftPatchWarning(WarningCode.COMMAND_APPLIES_TO_NO_COMPONENT,
            "the marked target step matched no component; the command changes nothing, which is "
                + "legal and worth saying out loud",
            position, index));
    }

    /**
     * Record a warning and log it, so that a run is diagnosable from the log alone.
     */
    private void warn(LiftPatchWarning warning) {
        warnings.add(warning);
        LOGGER.log(Level.WARNING, warning::format);
    }

    private TargetInfo targetInfo(ComponentRef ref, String label) {
        if (ref == null) {
            return null;
        }
        return new TargetInfo(
            ref.componentType(), model.idOf(ref).orElse(null), path(ref), label);
    }

    /**
     * An informative path from the root, written in the reference syntax. Two
     * implementations may differ in it and no comparison looks at it.
     */
    private String path(ComponentRef ref) {
        if (ref == null || ref instanceof ComponentRef.Root) {
            return "dictionary";
        }
        List<String> steps = new ArrayList<>();
        ComponentRef current = ref;
        while (current != null && !(current instanceof ComponentRef.Root)) {
            steps.add(0, stepLabel(current));
            current = model.parentOf(current);
        }
        return String.join("/", steps);
    }

    private String stepLabel(ComponentRef ref) {
        String type = ref.componentType();
        Optional<String> id = model.idOf(ref);
        if (id.isPresent()) {
            return type + "[id=\"" + id.get() + "\"]";
        }
        Optional<String> key = metamodel.componentType(type).filter(ComponentTypeDef::isTyped)
            .flatMap(d -> model.scalar(ref, "type"));
        if (key.isPresent()) {
            return type + "^" + key.get();
        }
        return model.positionOf(ref).map(p -> type + "#" + p).orElse(type);
    }

    /**
     * The references the script leaves dangling: a reference property whose stored
     * value points at a component the script deleted (Part 2, section 12.4.2).
     */
    private List<DanglingReference> findDanglingReferences() {
        if (deleted.isEmpty()) {
            return List.of();
        }
        List<DanglingReference> out = new ArrayList<>();
        for (ComponentRef entry : model.children(model.root(), Metamodel.ENTRY)) {
            collectDangling(entry, out);
        }
        return out;
    }

    private void collectDangling(ComponentRef component, List<DanglingReference> out) {
        ComponentTypeDef def = metamodel.componentType(component.componentType()).orElse(null);
        if (def == null) {
            return;
        }
        def.properties().values().stream()
            .filter(p -> p.datatype() == fr.cnrs.lacito.liftpatchbox.metamodel.Datatype.REFERENCE)
            .forEach(p -> {
                String value;
                try {
                    value = model.scalar(component, p.name()).orElse(null);
                } catch (UnsupportedByModelException e) {
                    return;
                }
                DeletedTarget cause = value == null ? null : deleted.get(value);
                if (cause != null) {
                    out.add(new DanglingReference(component.componentType(),
                        model.idOf(component).orElse(null), path(component), p.name(), value,
                        cause.operationIndex(), cause.verb()));
                }
            });
        for (String childType : def.children()) {
            List<ComponentRef> children;
            try {
                children = model.children(component, childType);
            } catch (UnsupportedByModelException e) {
                continue;
            }
            for (ComponentRef child : children) {
                collectDangling(child, out);
            }
        }
    }
}
