package fr.cnrs.lacito.liftpatchbox.ast;

import fr.cnrs.lacito.liftpatchbox.error.SourcePosition;
import fr.cnrs.lacito.liftpatchbox.metamodel.LanguageKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The fluent API for building LiftPatch commands.
 *
 * <p>This class is the single entry point through which command objects are
 * built. Both parsers use it — the LiftPatchRef visitor and the LiftPatchShort
 * visitor call exactly these methods — and so may application code that wants to
 * drive the engine without writing a script:</p>
 *
 * <pre>{@code
 * Script script = LiftPatch.script(Syntax.REFERENCE)
 *     .add(LiftPatch.create("sense")
 *             .init("gloss", "en", "pig")
 *             .under(LiftPatch.chain(LiftPatch.step("entry").eq("form", "tww", "mami")))
 *             .as("pig"))
 *     .add(LiftPatch.set()
 *             .assign("definition", "en", "A four-legged terrestrial animal")
 *             .on(LiftPatch.chain(LiftPatch.label("pig"))))
 *     .build();
 * }</pre>
 *
 * <p>Every builder carries an optional source position, which the visitors fill
 * in from the ANTLR tokens so that an error can be pointed back at the line the
 * user wrote. Positions default to {@link SourcePosition#UNKNOWN} for a command
 * built programmatically.</p>
 *
 * <p>The builders perform no validation beyond what a record's own constructor
 * requires: whether a command is legal is decided by the semantic validator
 * against the metamodel, so that a programmatically built command is rejected
 * with the same codes, and the same messages, as a parsed one.</p>
 */
public final class LiftPatch {

    private LiftPatch() {
    }

    // ===================================================================
    // Entry points
    // ===================================================================

    /**
     * Start building a script.
     *
     * @param syntax the surface syntax to record on the script
     * @return a new script builder
     */
    public static ScriptBuilder script(Syntax syntax) {
        return new ScriptBuilder(syntax);
    }

    /**
     * Start building a component step, the usual building block of a chain.
     *
     * @param componentType the component type name, spelled in full
     * @return a new step builder
     */
    public static StepBuilder step(String componentType) {
        return new StepBuilder(componentType);
    }

    /**
     * A step that is a label reference.
     *
     * @param name the label name, without the leading {@code $}
     * @return the label step
     */
    public static Step.Label label(String name) {
        return Step.Label.of(name);
    }

    /**
     * Start building a chain from its child-most step.
     *
     * @param head the step the chain designates
     * @return a new chain builder
     */
    public static ChainBuilder chain(Step head) {
        return new ChainBuilder(head);
    }

    /**
     * Start building a chain from its child-most step, given as a builder.
     *
     * @param head the step the chain designates
     * @return a new chain builder
     */
    public static ChainBuilder chain(StepBuilder head) {
        return new ChainBuilder(head.build());
    }

    /**
     * Start building a constructor: a component type with its initializer list.
     *
     * @param componentType the component type name, spelled in full
     * @return a new constructor builder
     */
    public static ConstructorBuilder constructor(String componentType) {
        return new ConstructorBuilder(componentType);
    }

    /**
     * Start building a {@code create} command.
     *
     * @param componentType the component type created
     * @return a new create builder, whose constructor is initialized with that type
     */
    public static CreateBuilder create(String componentType) {
        return new CreateBuilder(new ConstructorBuilder(componentType));
    }

    /**
     * Start building a {@code create} command from an already-built constructor.
     *
     * @param constructor the component being created
     * @return a new create builder
     */
    public static CreateBuilder create(ConstructorBuilder constructor) {
        return new CreateBuilder(constructor);
    }

    /**
     * Start building an {@code upsert} command.
     *
     * @param componentType the component type upserted
     * @return a new upsert builder, whose constructor is initialized with that type
     */
    public static UpsertBuilder upsert(String componentType) {
        return new UpsertBuilder(new ConstructorBuilder(componentType));
    }

    /**
     * Start building an {@code upsert} command from an already-built constructor.
     *
     * @param constructor the component being upserted
     * @return a new upsert builder
     */
    public static UpsertBuilder upsert(ConstructorBuilder constructor) {
        return new UpsertBuilder(constructor);
    }

    /**
     * Start building an {@code ensure} command.
     *
     * @param target the step asserted to resolve to exactly one component
     * @return a new ensure builder
     */
    public static EnsureBuilder ensure(Step target) {
        return new EnsureBuilder(target);
    }

    /**
     * Start building a {@code delete} command.
     *
     * @param target the step naming what is deleted
     * @return a new delete builder
     */
    public static DeleteBuilder delete(Step target) {
        return new DeleteBuilder(target);
    }

    /**
     * Start building a {@code move} command.
     *
     * @param target the ordered step naming the component moved
     * @return a new move builder
     */
    public static MoveBuilder move(Step target) {
        return new MoveBuilder(target);
    }

    /**
     * Start building a {@code set} command.
     *
     * @return a new set builder
     */
    public static PropertyCommandBuilder set() {
        return new PropertyCommandBuilder(Verb.SET);
    }

    /**
     * Start building an {@code update} command.
     *
     * @return a new update builder
     */
    public static PropertyCommandBuilder update() {
        return new PropertyCommandBuilder(Verb.UPDATE);
    }

    /**
     * Start building a {@code clear} command.
     *
     * @return a new clear builder
     */
    public static PropertyCommandBuilder clear() {
        return new PropertyCommandBuilder(Verb.CLEAR);
    }

    /**
     * Start building a block.
     *
     * @param header what stands before the opening brace
     * @return a new block builder
     */
    public static BlockBuilder block(BlockHeader header) {
        return new BlockBuilder(header);
    }

    /**
     * A {@code language-default} directive.
     *
     * @param kind     the language kind bound
     * @param language the language code
     * @return the directive, at an unknown position
     */
    public static Directive languageDefault(LanguageKind kind, String language) {
        return new Directive(false, kind, language, SourcePosition.UNKNOWN);
    }

    /**
     * A {@code language-create} directive.
     *
     * @param kind     the language kind created
     * @param language the language code
     * @return the directive, at an unknown position
     */
    public static Directive languageCreate(LanguageKind kind, String language) {
        return new Directive(true, kind, language, SourcePosition.UNKNOWN);
    }

    // ===================================================================
    // Value helpers
    // ===================================================================

    /**
     * A string value.
     *
     * @param text the string content
     * @return the value
     */
    public static Value.Str text(String text) {
        return Value.Str.of(text);
    }

    /**
     * An integer value.
     *
     * @param value the integer
     * @return the value
     */
    public static Value.Num number(long value) {
        return Value.Num.of(value);
    }

    /**
     * A multi-language literal built from a map of language code to text.
     *
     * @param values the qualified values, in the map's iteration order
     * @return the value
     */
    public static Value.Multitext multitext(Map<String, String> values) {
        List<LangText> entries = new ArrayList<>();
        values.forEach((lang, t) -> entries.add(LangText.of(lang, t)));
        return new Value.Multitext(entries, SourcePosition.UNKNOWN);
    }

    /**
     * A value that is a chain, which is how a reference property is written.
     *
     * @param chain the chain designating the target component
     * @return the value
     */
    public static Value.ChainRef ref(Chain chain) {
        return new Value.ChainRef(chain);
    }

    /**
     * A value that is a chain, given as a builder.
     *
     * @param chain the chain designating the target component
     * @return the value
     */
    public static Value.ChainRef ref(ChainBuilder chain) {
        return new Value.ChainRef(chain.build());
    }

    /**
     * A value that is a label reference.
     *
     * @param name the label name, without the leading {@code $}
     * @return the value
     */
    public static Value.LabelRef labelValue(String name) {
        return Value.LabelRef.of(name);
    }

    // ===================================================================
    // Builder interfaces
    // ===================================================================

    /** Anything that builds an {@link Item}. */
    public interface ItemBuilder {

        /**
         * Build the item.
         *
         * @return the item
         */
        Item build();
    }

    /** Anything that builds a {@link Command}. */
    public interface CommandBuilder extends ItemBuilder {

        @Override
        Command build();
    }

    // ===================================================================
    // ScriptBuilder
    // ===================================================================

    /** Builds a {@link Script}. */
    public static final class ScriptBuilder {

        private final Syntax syntax;
        private final List<Item> items = new ArrayList<>();
        private Pragma pragma;
        private String source = "<memory>";
        private int recognizedLines = -1;

        private ScriptBuilder(Syntax syntax) {
            this.syntax = syntax;
        }

        /**
         * Declare the version pragma of the script.
         *
         * @param pragma the pragma, or {@code null} for none
         * @return this builder
         */
        public ScriptBuilder pragma(Pragma pragma) {
            this.pragma = pragma;
            return this;
        }

        /**
         * Name the source, for diagnostics and plan documents.
         *
         * @param source the source name, typically a file name
         * @return this builder
         */
        public ScriptBuilder source(String source) {
            this.source = source;
            return this;
        }

        /**
         * Record how many lines of the source were recognized as commands, which is
         * what the {@code NO_COMMAND_RECOGNIZED} warning is based on.
         *
         * @param n the number of recognized command lines
         * @return this builder
         */
        public ScriptBuilder recognizedLines(int n) {
            this.recognizedLines = n;
            return this;
        }

        /**
         * Append an item.
         *
         * @param item the item
         * @return this builder
         */
        public ScriptBuilder add(Item item) {
            items.add(item);
            return this;
        }

        /**
         * Append an item, given as a builder.
         *
         * @param builder the item builder
         * @return this builder
         */
        public ScriptBuilder add(ItemBuilder builder) {
            items.add(builder.build());
            return this;
        }

        /**
         * Build the script.
         *
         * @return the script
         */
        public Script build() {
            int recognized = recognizedLines >= 0 ? recognizedLines : items.size();
            return new Script(pragma, items, syntax, source, recognized);
        }
    }

    // ===================================================================
    // StepBuilder
    // ===================================================================

    /** Builds a {@link Step.Component}. */
    public static final class StepBuilder {

        private final String componentType;
        private final List<Predicate> predicates = new ArrayList<>();
        private Integer ordinal;
        private String typeKey;
        private Multiplicity multiplicity;
        private SourcePosition position = SourcePosition.UNKNOWN;

        private StepBuilder(String componentType) {
            this.componentType = componentType;
        }

        /**
         * Record where the step was written.
         *
         * @param position the source position
         * @return this builder
         */
        public StepBuilder at(SourcePosition position) {
            this.position = position;
            return this;
        }

        /**
         * Add an arbitrary predicate.
         *
         * @param predicate the predicate
         * @return this builder
         */
        public StepBuilder predicate(Predicate predicate) {
            predicates.add(predicate);
            return this;
        }

        /**
         * Add an equality predicate on an unqualified property.
         *
         * @param property the property name
         * @param value    the string compared against
         * @return this builder
         */
        public StepBuilder eq(String property, String value) {
            return compare(PropertyRef.of(property), Operator.EQ, Value.Str.of(value));
        }

        /**
         * Add an equality predicate on a language-qualified property.
         *
         * @param property the property name
         * @param language the language code
         * @param value    the string compared against
         * @return this builder
         */
        public StepBuilder eq(String property, String language, String value) {
            return compare(PropertyRef.of(property, language), Operator.EQ, Value.Str.of(value));
        }

        /**
         * Add an inequality predicate, which a filtering selector alone admits.
         *
         * @param property the property reference
         * @param value    the value compared against
         * @return this builder
         */
        public StepBuilder ne(PropertyRef property, Value value) {
            return compare(property, Operator.NE, value);
        }

        /**
         * Add a regular-expression predicate, which a filtering selector alone admits.
         *
         * @param property    the property reference
         * @param regex       the PCRE pattern
         * @param ignoreCase  whether to fold case, that is whether to use {@code ~i}
         * @return this builder
         */
        public StepBuilder match(PropertyRef property, String regex, boolean ignoreCase) {
            return compare(property,
                ignoreCase ? Operator.MATCH_IGNORE_CASE : Operator.MATCH,
                Value.Str.of(regex));
        }

        /**
         * Add a comparison predicate.
         *
         * @param property the property reference
         * @param operator the comparison operator
         * @param value    the value compared against
         * @return this builder
         */
        public StepBuilder compare(PropertyRef property, Operator operator, Value value) {
            predicates.add(new Predicate.Comparison(property, operator, value, position));
            return this;
        }

        /**
         * Add an {@code exists(p)} predicate.
         *
         * @param property the property reference
         * @return this builder
         */
        public StepBuilder exists(PropertyRef property) {
            predicates.add(new Predicate.Existence(property, false, position));
            return this;
        }

        /**
         * Add an {@code absent(p)} predicate.
         *
         * @param property the property reference
         * @return this builder
         */
        public StepBuilder absent(PropertyRef property) {
            predicates.add(new Predicate.Existence(property, true, position));
            return this;
        }

        /**
         * Add a {@code has STEP} predicate.
         *
         * @param child the child step that must be matched
         * @return this builder
         */
        public StepBuilder has(Step child) {
            predicates.add(new Predicate.Has(child, position));
            return this;
        }

        /**
         * Add a {@code has STEP} predicate, the step given as a builder.
         *
         * @param child the child step that must be matched
         * @return this builder
         */
        public StepBuilder has(StepBuilder child) {
            return has(child.build());
        }

        /**
         * Add an {@code id} predicate.
         *
         * @param id the persistent identifier
         * @return this builder
         */
        public StepBuilder id(String id) {
            predicates.add(new Predicate.Id(id, position));
            return this;
        }

        /**
         * Add an {@code hn} predicate.
         *
         * @param hn the homophone number
         * @return this builder
         */
        public StepBuilder hn(long hn) {
            predicates.add(new Predicate.Hn(hn, position));
            return this;
        }

        /**
         * Add a {@code has-gloss} predicate.
         *
         * @param language the meta language code, or {@code null} for the default one
         * @param value    the gloss looked for
         * @return this builder
         */
        public StepBuilder hasGloss(String language, String value) {
            predicates.add(new Predicate.HasGloss(language, value, position));
            return this;
        }

        /**
         * Give the step an ordinal {@code #n}.
         *
         * @param n the 1-based position among same-type siblings
         * @return this builder
         */
        public StepBuilder ordinal(int n) {
            this.ordinal = n;
            return this;
        }

        /**
         * Give the step a type key {@code ^t}.
         *
         * @param key the {@code type} value
         * @return this builder
         */
        public StepBuilder typeKey(String key) {
            this.typeKey = key;
            return this;
        }

        /**
         * Mark the step with a multiplicity keyword.
         *
         * @param m the keyword, or {@code null} for none
         * @return this builder
         */
        public StepBuilder multiplicity(Multiplicity m) {
            this.multiplicity = m;
            return this;
        }

        /**
         * Mark the step {@code each}.
         *
         * @return this builder
         */
        public StepBuilder each() {
            return multiplicity(Multiplicity.EACH);
        }

        /**
         * Mark the step {@code all}.
         *
         * @return this builder
         */
        public StepBuilder all() {
            return multiplicity(Multiplicity.ALL);
        }

        /**
         * Build the step.
         *
         * @return the component step
         */
        public Step.Component build() {
            return new Step.Component(
                componentType, predicates, ordinal, typeKey, multiplicity, position);
        }
    }

    // ===================================================================
    // ChainBuilder
    // ===================================================================

    /** Builds a {@link Chain}, child first. */
    public static final class ChainBuilder {

        private final List<Step> steps = new ArrayList<>();
        private final List<Axis> axes = new ArrayList<>();

        private ChainBuilder(Step head) {
            steps.add(head);
        }

        /**
         * Extend the chain with a strict parent, written {@code of} in the reference
         * syntax and {@code !} in the concise one.
         *
         * @param parent the parent step
         * @return this builder
         */
        public ChainBuilder of(Step parent) {
            axes.add(Axis.STRICT);
            steps.add(parent);
            return this;
        }

        /**
         * Extend the chain with a strict parent, given as a builder.
         *
         * @param parent the parent step
         * @return this builder
         */
        public ChainBuilder of(StepBuilder parent) {
            return of(parent.build());
        }

        /**
         * Extend the chain with an existential parent, written {@code within} in the
         * reference syntax and {@code /} in the concise one.
         *
         * @param parent the parent step
         * @return this builder
         */
        public ChainBuilder within(Step parent) {
            axes.add(Axis.EXISTENTIAL);
            steps.add(parent);
            return this;
        }

        /**
         * Extend the chain with an existential parent, given as a builder.
         *
         * @param parent the parent step
         * @return this builder
         */
        public ChainBuilder within(StepBuilder parent) {
            return within(parent.build());
        }

        /**
         * Extend the chain with a parent on the given axis.
         *
         * @param axis   the axis of the link
         * @param parent the parent step
         * @return this builder
         */
        public ChainBuilder link(Axis axis, Step parent) {
            axes.add(axis);
            steps.add(parent);
            return this;
        }

        /**
         * Build the chain.
         *
         * @return the chain
         */
        public Chain build() {
            return new Chain(steps, axes);
        }
    }

    // ===================================================================
    // ConstructorBuilder
    // ===================================================================

    /** Builds a {@link Constructor}. */
    public static final class ConstructorBuilder {

        private final String componentType;
        private final List<Initializer> initializers = new ArrayList<>();
        private SourcePosition position = SourcePosition.UNKNOWN;

        private ConstructorBuilder(String componentType) {
            this.componentType = componentType;
        }

        /**
         * Record where the constructor was written.
         *
         * @param position the source position
         * @return this builder
         */
        public ConstructorBuilder at(SourcePosition position) {
            this.position = position;
            return this;
        }

        /**
         * Add an initializer.
         *
         * @param initializer the initializer
         * @return this builder
         */
        public ConstructorBuilder add(Initializer initializer) {
            initializers.add(initializer);
            return this;
        }

        /**
         * Initialize an unqualified property with a string.
         *
         * @param property the property name
         * @param value    the string value
         * @return this builder
         */
        public ConstructorBuilder init(String property, String value) {
            return init(PropertyRef.of(property), Value.Str.of(value));
        }

        /**
         * Initialize a language-qualified property with a string.
         *
         * @param property the property name
         * @param language the language code
         * @param value    the string value
         * @return this builder
         */
        public ConstructorBuilder init(String property, String language, String value) {
            return init(PropertyRef.of(property, language), Value.Str.of(value));
        }

        /**
         * Initialize a property with an arbitrary value.
         *
         * @param property the property reference
         * @param value    the value
         * @return this builder
         */
        public ConstructorBuilder init(PropertyRef property, Value value) {
            initializers.add(new Initializer.Assignment(property, value, position));
            return this;
        }

        /**
         * Add a {@code has} or {@code has-gloss} predicate, which only an
         * {@code upsert} parenthesis admits.
         *
         * @param predicate the predicate
         * @return this builder
         */
        public ConstructorBuilder condition(Predicate predicate) {
            initializers.add(new Initializer.Condition(predicate));
            return this;
        }

        /**
         * Add an embedded component creation.
         *
         * @param child the embedded constructor
         * @return this builder
         */
        public ConstructorBuilder embed(Constructor child) {
            initializers.add(new Initializer.Embedded(child));
            return this;
        }

        /**
         * Add an embedded component creation, given as a builder.
         *
         * @param child the embedded constructor
         * @return this builder
         */
        public ConstructorBuilder embed(ConstructorBuilder child) {
            return embed(child.build());
        }

        /**
         * Build the constructor.
         *
         * @return the constructor
         */
        public Constructor build() {
            return new Constructor(componentType, initializers, position);
        }
    }

    // ===================================================================
    // Command builders
    // ===================================================================

    /** Common state of every command builder: a position and the source text. */
    private abstract static class AbstractCommandBuilder implements CommandBuilder {

        SourcePosition position = SourcePosition.UNKNOWN;
        String sourceText;
    }

    /** Builds a {@link Command.Create}. */
    public static final class CreateBuilder extends AbstractCommandBuilder {

        private final ConstructorBuilder constructor;
        private Chain parent;
        private AtClause at;
        private String label;

        private CreateBuilder(ConstructorBuilder constructor) {
            this.constructor = constructor;
        }

        /**
         * Add an initializer to the component being created.
         *
         * @param property the property name
         * @param value    the string value
         * @return this builder
         */
        public CreateBuilder init(String property, String value) {
            constructor.init(property, value);
            return this;
        }

        /**
         * Add a language-qualified initializer to the component being created.
         *
         * @param property the property name
         * @param language the language code
         * @param value    the string value
         * @return this builder
         */
        public CreateBuilder init(String property, String language, String value) {
            constructor.init(property, language, value);
            return this;
        }

        /**
         * Add an initializer with an arbitrary value.
         *
         * @param property the property reference
         * @param value    the value
         * @return this builder
         */
        public CreateBuilder init(PropertyRef property, Value value) {
            constructor.init(property, value);
            return this;
        }

        /**
         * Embed a child creation in the initializer list.
         *
         * @param child the embedded constructor
         * @return this builder
         */
        public CreateBuilder embed(ConstructorBuilder child) {
            constructor.embed(child);
            return this;
        }

        /**
         * Name the parent the component is created under.
         *
         * @param parent the parent chain
         * @return this builder
         */
        public CreateBuilder under(Chain parent) {
            this.parent = parent;
            return this;
        }

        /**
         * Name the parent the component is created under, given as a chain builder.
         *
         * @param parent the parent chain
         * @return this builder
         */
        public CreateBuilder under(ChainBuilder parent) {
            return under(parent.build());
        }

        /**
         * Name the parent the component is created under, given as a single step.
         *
         * @param parent the parent step
         * @return this builder
         */
        public CreateBuilder under(Step parent) {
            return under(Chain.of(parent));
        }

        /**
         * State where the component goes among its same-type siblings.
         *
         * @param at the position clause
         * @return this builder
         */
        public CreateBuilder at(AtClause at) {
            this.at = at;
            return this;
        }

        /**
         * Bind a label to the created component.
         *
         * @param label the label name, without the leading {@code $}
         * @return this builder
         */
        public CreateBuilder as(String label) {
            this.label = label;
            return this;
        }

        /**
         * Record where the command was written and the text it was parsed from.
         *
         * @param position the source position
         * @param text     the source text, or {@code null}
         * @return this builder
         */
        public CreateBuilder source(SourcePosition position, String text) {
            this.position = position;
            this.sourceText = text;
            return this;
        }

        @Override
        public Command.Create build() {
            return new Command.Create(constructor.build(), parent, at, label, sourceText, position);
        }
    }

    /** Builds a {@link Command.Upsert}. */
    public static final class UpsertBuilder extends AbstractCommandBuilder {

        private final ConstructorBuilder constructor;
        private Chain parent;
        private AtClause at;
        private String label;

        private UpsertBuilder(ConstructorBuilder constructor) {
            this.constructor = constructor;
        }

        /**
         * Add an initializer to the component being upserted.
         *
         * @param property the property name
         * @param value    the string value
         * @return this builder
         */
        public UpsertBuilder init(String property, String value) {
            constructor.init(property, value);
            return this;
        }

        /**
         * Add a language-qualified initializer to the component being upserted.
         *
         * @param property the property name
         * @param language the language code
         * @param value    the string value
         * @return this builder
         */
        public UpsertBuilder init(String property, String language, String value) {
            constructor.init(property, language, value);
            return this;
        }

        /**
         * Add an initializer with an arbitrary value.
         *
         * @param property the property reference
         * @param value    the value
         * @return this builder
         */
        public UpsertBuilder init(PropertyRef property, Value value) {
            constructor.init(property, value);
            return this;
        }

        /**
         * Add a {@code has} or {@code has-gloss} disambiguation predicate.
         *
         * @param predicate the predicate
         * @return this builder
         */
        public UpsertBuilder condition(Predicate predicate) {
            constructor.condition(predicate);
            return this;
        }

        /**
         * Embed a child creation in the initializer list.
         *
         * @param child the embedded constructor
         * @return this builder
         */
        public UpsertBuilder embed(ConstructorBuilder child) {
            constructor.embed(child);
            return this;
        }

        /**
         * Name the parent the component is upserted under.
         *
         * @param parent the parent chain
         * @return this builder
         */
        public UpsertBuilder under(Chain parent) {
            this.parent = parent;
            return this;
        }

        /**
         * Name the parent the component is upserted under, given as a chain builder.
         *
         * @param parent the parent chain
         * @return this builder
         */
        public UpsertBuilder under(ChainBuilder parent) {
            return under(parent.build());
        }

        /**
         * Name the parent the component is upserted under, given as a single step.
         *
         * @param parent the parent step
         * @return this builder
         */
        public UpsertBuilder under(Step parent) {
            return under(Chain.of(parent));
        }

        /**
         * State where a created component goes among its same-type siblings; the
         * clause is ignored by the select branch.
         *
         * @param at the position clause
         * @return this builder
         */
        public UpsertBuilder at(AtClause at) {
            this.at = at;
            return this;
        }

        /**
         * Bind a label to the created or resolved component.
         *
         * @param label the label name, without the leading {@code $}
         * @return this builder
         */
        public UpsertBuilder as(String label) {
            this.label = label;
            return this;
        }

        /**
         * Record where the command was written and the text it was parsed from.
         *
         * @param position the source position
         * @param text     the source text, or {@code null}
         * @return this builder
         */
        public UpsertBuilder source(SourcePosition position, String text) {
            this.position = position;
            this.sourceText = text;
            return this;
        }

        @Override
        public Command.Upsert build() {
            return new Command.Upsert(constructor.build(), parent, at, label, sourceText, position);
        }
    }

    /** Builds a {@link Command.Ensure}. */
    public static final class EnsureBuilder extends AbstractCommandBuilder {

        private final Step target;
        private Chain parent;
        private String label;

        private EnsureBuilder(Step target) {
            this.target = target;
        }

        /**
         * Name the parent the component is asserted under.
         *
         * @param parent the parent chain
         * @return this builder
         */
        public EnsureBuilder under(Chain parent) {
            this.parent = parent;
            return this;
        }

        /**
         * Name the parent the component is asserted under, given as a chain builder.
         *
         * @param parent the parent chain
         * @return this builder
         */
        public EnsureBuilder under(ChainBuilder parent) {
            return under(parent.build());
        }

        /**
         * Bind a label to the asserted component.
         *
         * @param label the label name, without the leading {@code $}
         * @return this builder
         */
        public EnsureBuilder as(String label) {
            this.label = label;
            return this;
        }

        /**
         * Record where the command was written and the text it was parsed from.
         *
         * @param position the source position
         * @param text     the source text, or {@code null}
         * @return this builder
         */
        public EnsureBuilder source(SourcePosition position, String text) {
            this.position = position;
            this.sourceText = text;
            return this;
        }

        @Override
        public Command.Ensure build() {
            return new Command.Ensure(target, parent, label, sourceText, position);
        }
    }

    /** Builds a {@link Command.Delete}. */
    public static final class DeleteBuilder extends AbstractCommandBuilder {

        private final Step target;
        private Chain parent;

        private DeleteBuilder(Step target) {
            this.target = target;
        }

        /**
         * Name the parent the component is deleted under.
         *
         * @param parent the parent chain
         * @return this builder
         */
        public DeleteBuilder under(Chain parent) {
            this.parent = parent;
            return this;
        }

        /**
         * Name the parent the component is deleted under, given as a chain builder.
         *
         * @param parent the parent chain
         * @return this builder
         */
        public DeleteBuilder under(ChainBuilder parent) {
            return under(parent.build());
        }

        /**
         * Record where the command was written and the text it was parsed from.
         *
         * @param position the source position
         * @param text     the source text, or {@code null}
         * @return this builder
         */
        public DeleteBuilder source(SourcePosition position, String text) {
            this.position = position;
            this.sourceText = text;
            return this;
        }

        @Override
        public Command.Delete build() {
            return new Command.Delete(target, parent, sourceText, position);
        }
    }

    /** Builds a {@link Command.Move}. */
    public static final class MoveBuilder extends AbstractCommandBuilder {

        private final Step target;
        private Chain sourceParent;
        private Chain destinationParent;
        private AtClause at;

        private MoveBuilder(Step target) {
            this.target = target;
        }

        /**
         * Name the parent the component currently sits under.
         *
         * @param parent the source parent chain
         * @return this builder
         */
        public MoveBuilder from(Chain parent) {
            this.sourceParent = parent;
            return this;
        }

        /**
         * Name the parent the component currently sits under, given as a builder.
         *
         * @param parent the source parent chain
         * @return this builder
         */
        public MoveBuilder from(ChainBuilder parent) {
            return from(parent.build());
        }

        /**
         * Name the parent the component moves under; omit it for a same-parent move.
         *
         * @param parent the destination parent chain
         * @return this builder
         */
        public MoveBuilder to(Chain parent) {
            this.destinationParent = parent;
            return this;
        }

        /**
         * Name the destination parent, given as a builder.
         *
         * @param parent the destination parent chain
         * @return this builder
         */
        public MoveBuilder to(ChainBuilder parent) {
            return to(parent.build());
        }

        /**
         * State the destination position, which {@code move} requires.
         *
         * @param at the position clause
         * @return this builder
         */
        public MoveBuilder at(AtClause at) {
            this.at = at;
            return this;
        }

        /**
         * Record where the command was written and the text it was parsed from.
         *
         * @param position the source position
         * @param text     the source text, or {@code null}
         * @return this builder
         */
        public MoveBuilder source(SourcePosition position, String text) {
            this.position = position;
            this.sourceText = text;
            return this;
        }

        @Override
        public Command.Move build() {
            return new Command.Move(
                target, sourceParent, destinationParent, at, sourceText, position);
        }
    }

    /** Builds a {@code set}, an {@code update} or a {@code clear}. */
    public static final class PropertyCommandBuilder extends AbstractCommandBuilder {

        private final Verb verb;
        private final List<Assignment> assignments = new ArrayList<>();
        private final List<PropertyRef> properties = new ArrayList<>();
        private Chain target;

        private PropertyCommandBuilder(Verb verb) {
            this.verb = verb;
        }

        /**
         * Add an assignment, for {@code set} and {@code update}.
         *
         * @param property the property name
         * @param value    the string value
         * @return this builder
         */
        public PropertyCommandBuilder assign(String property, String value) {
            return assign(PropertyRef.of(property), Value.Str.of(value));
        }

        /**
         * Add a language-qualified assignment, for {@code set} and {@code update}.
         *
         * @param property the property name
         * @param language the language code
         * @param value    the string value
         * @return this builder
         */
        public PropertyCommandBuilder assign(String property, String language, String value) {
            return assign(PropertyRef.of(property, language), Value.Str.of(value));
        }

        /**
         * Add an assignment with an arbitrary value.
         *
         * @param property the property reference
         * @param value    the value
         * @return this builder
         */
        public PropertyCommandBuilder assign(PropertyRef property, Value value) {
            assignments.add(new Assignment(property, value, position));
            return this;
        }

        /**
         * Add a property to the list of a {@code clear}.
         *
         * @param property the property reference
         * @return this builder
         */
        public PropertyCommandBuilder property(PropertyRef property) {
            properties.add(property);
            return this;
        }

        /**
         * Name the component the command writes on.
         *
         * @param target the target chain
         * @return this builder
         */
        public PropertyCommandBuilder on(Chain target) {
            this.target = target;
            return this;
        }

        /**
         * Name the component the command writes on, given as a chain builder.
         *
         * @param target the target chain
         * @return this builder
         */
        public PropertyCommandBuilder on(ChainBuilder target) {
            return on(target.build());
        }

        /**
         * Name the component the command writes on, given as a single step.
         *
         * @param target the target step
         * @return this builder
         */
        public PropertyCommandBuilder on(Step target) {
            return on(Chain.of(target));
        }

        /**
         * Record where the command was written and the text it was parsed from.
         *
         * @param position the source position
         * @param text     the source text, or {@code null}
         * @return this builder
         */
        public PropertyCommandBuilder source(SourcePosition position, String text) {
            this.position = position;
            this.sourceText = text;
            return this;
        }

        @Override
        public Command build() {
            return switch (verb) {
                case SET -> new Command.Set(assignments, target, sourceText, position);
                case UPDATE -> new Command.Update(assignments, target, sourceText, position);
                case CLEAR -> new Command.Clear(properties, target, sourceText, position);
                default -> throw new IllegalStateException("Not a property command: " + verb);
            };
        }
    }

    // ===================================================================
    // BlockBuilder
    // ===================================================================

    /** Builds a {@link Block}. */
    public static final class BlockBuilder implements ItemBuilder {

        private final BlockHeader header;
        private final List<Item> body = new ArrayList<>();
        private SourcePosition position = SourcePosition.UNKNOWN;

        private BlockBuilder(BlockHeader header) {
            this.header = header;
            this.position = header.position();
        }

        /**
         * Append an item to the body.
         *
         * @param item the item
         * @return this builder
         */
        public BlockBuilder add(Item item) {
            body.add(item);
            return this;
        }

        /**
         * Append an item to the body, given as a builder.
         *
         * @param builder the item builder
         * @return this builder
         */
        public BlockBuilder add(ItemBuilder builder) {
            body.add(builder.build());
            return this;
        }

        /**
         * Record where the block begins.
         *
         * @param position the source position
         * @return this builder
         */
        public BlockBuilder at(SourcePosition position) {
            this.position = position;
            return this;
        }

        @Override
        public Block build() {
            return new Block(header, body, position);
        }
    }

    // ===================================================================
    // Header helpers
    // ===================================================================

    /**
     * A block header that is a bare selector chain.
     *
     * @param chain the chain designating the parent of the body
     * @param label the label bound to it, or {@code null}
     * @return the header, at the chain's position
     */
    public static BlockHeader selectorHeader(Chain chain, String label) {
        return new BlockHeader.Selector(chain, label, chain.position());
    }

    /**
     * A block header that is a command: a {@code create}, an {@code upsert}, an
     * {@code ensure}, or — in a concise indented block — a property command.
     *
     * @param command the heading command
     * @return the header
     */
    public static BlockHeader anchorHeader(Command command) {
        return new BlockHeader.Anchor(command);
    }

    /**
     * A {@code with} header binding default languages for the body.
     *
     * @param bindings the language kinds bound and their codes
     * @return the header, at an unknown position
     */
    public static BlockHeader withHeader(Map<LanguageKind, String> bindings) {
        return new BlockHeader.With(new LinkedHashMap<>(bindings), SourcePosition.UNKNOWN);
    }
}
