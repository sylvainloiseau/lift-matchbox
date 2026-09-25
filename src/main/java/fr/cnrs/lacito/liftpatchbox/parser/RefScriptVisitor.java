package fr.cnrs.lacito.liftpatchbox.parser;

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
import fr.cnrs.lacito.liftpatchbox.ast.LiftPatch;
import fr.cnrs.lacito.liftpatchbox.ast.Multiplicity;
import fr.cnrs.lacito.liftpatchbox.ast.Operator;
import fr.cnrs.lacito.liftpatchbox.ast.Pragma;
import fr.cnrs.lacito.liftpatchbox.ast.Predicate;
import fr.cnrs.lacito.liftpatchbox.ast.PropertyRef;
import fr.cnrs.lacito.liftpatchbox.ast.Script;
import fr.cnrs.lacito.liftpatchbox.ast.Step;
import fr.cnrs.lacito.liftpatchbox.ast.Syntax;
import fr.cnrs.lacito.liftpatchbox.ast.Value;
import fr.cnrs.lacito.liftpatchbox.error.ErrorCode;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchError;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchException;
import fr.cnrs.lacito.liftpatchbox.error.SourcePosition;
import fr.cnrs.lacito.liftpatchbox.metamodel.LanguageKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Builds the command model from a LiftPatchRef parse tree.
 *
 * <p>The visitor does no validation of its own beyond the few conditions the
 * grammar cannot express and the specification classes as syntax errors — an
 * {@code ensure} written with parentheses, a {@code move} with no {@code at}
 * clause. Everything else is left to the semantic validator, so that a command
 * built through the fluent API is checked by exactly the same rules as a parsed
 * one.</p>
 *
 * <p>Every node is given the position of its first token, which is what an error
 * message and a plan document point at.</p>
 */
public final class RefScriptVisitor extends LiftPatchRefBaseVisitor<Object> {

    private static final Logger LOGGER = Logger.getLogger(RefScriptVisitor.class.getName());

    private final String source;

    /**
     * A visitor for a script read from the named source.
     *
     * @param source the source name, used in diagnostics and plan documents
     */
    public RefScriptVisitor(String source) {
        this.source = source;
    }

    // ===================================================================
    // Script
    // ===================================================================

    /**
     * Build the whole script.
     *
     * @param ctx the {@code script} parse tree
     * @return the script
     */
    public Script buildScript(LiftPatchRefParser.ScriptContext ctx) {
        Pragma pragma = ctx.pragma() == null ? null : visitPragma(ctx.pragma());
        List<Item> items = new ArrayList<>();
        for (LiftPatchRefParser.ItemContext ic : ctx.item()) {
            items.add(visitItem(ic));
        }
        LOGGER.log(Level.FINE, () ->
            "Parsed " + items.size() + " top-level item(s) from " + source);
        LiftPatch.ScriptBuilder builder = LiftPatch.script(Syntax.REFERENCE)
            .pragma(pragma)
            .source(source)
            .recognizedLines(items.size());
        items.forEach(builder::add);
        return builder.build();
    }

    @Override
    public Pragma visitPragma(LiftPatchRefParser.PragmaContext ctx) {
        Map<String, String> attributes = new LinkedHashMap<>();
        for (LiftPatchRefParser.PragmaAttributeContext a : ctx.pragmaAttribute()) {
            attributes.put(text(a.anyName()), string(a.string()));
        }
        String version = ctx.version().INT(0).getText() + "." + ctx.version().INT(1).getText();
        return new Pragma(version, attributes, position(ctx));
    }

    /**
     * Build one item of a script or of a block body.
     *
     * @param ctx the {@code item} parse tree
     * @return the command, block or directive
     */
    public Item visitItem(LiftPatchRefParser.ItemContext ctx) {
        if (ctx.block() != null) {
            return visitBlock(ctx.block());
        }
        if (ctx.command() != null) {
            return buildCommand(ctx.command());
        }
        return visitDirective(ctx.directive());
    }

    @Override
    public Directive visitDirective(LiftPatchRefParser.DirectiveContext ctx) {
        boolean create = ctx.LANGUAGE_CREATE() != null;
        LanguageKind kind = ctx.langKind().OBJECT() != null ? LanguageKind.OBJECT : LanguageKind.META;
        return new Directive(create, kind, string(ctx.string()), position(ctx));
    }

    // ===================================================================
    // Blocks
    // ===================================================================

    @Override
    public Block visitBlock(LiftPatchRefParser.BlockContext ctx) {
        BlockHeader header = buildHeader(ctx.blockHeader());
        List<Item> body = new ArrayList<>();
        for (LiftPatchRefParser.ItemContext ic : ctx.item()) {
            body.add(visitItem(ic));
        }
        return new Block(header, body, position(ctx));
    }

    private BlockHeader buildHeader(LiftPatchRefParser.BlockHeaderContext ctx) {
        if (ctx.createCmd() != null) {
            return LiftPatch.anchorHeader(visitCreateCmd(ctx.createCmd()));
        }
        if (ctx.upsertCmd() != null) {
            return LiftPatch.anchorHeader(visitUpsertCmd(ctx.upsertCmd()));
        }
        if (ctx.ensureCmd() != null) {
            return LiftPatch.anchorHeader(visitEnsureCmd(ctx.ensureCmd()));
        }
        if (ctx.withHeader() != null) {
            return visitWithHeader(ctx.withHeader());
        }
        String label = ctx.labelBinding() == null ? null : labelName(ctx.labelBinding());
        return new BlockHeader.Selector(visitChain(ctx.chain()), label, position(ctx));
    }

    @Override
    public BlockHeader.With visitWithHeader(LiftPatchRefParser.WithHeaderContext ctx) {
        Map<LanguageKind, String> bindings = new LinkedHashMap<>();
        for (LiftPatchRefParser.BindingContext b : ctx.binding()) {
            LanguageKind kind =
                b.langKind().OBJECT() != null ? LanguageKind.OBJECT : LanguageKind.META;
            bindings.put(kind, string(b.string()));
        }
        return new BlockHeader.With(bindings, position(ctx));
    }

    // ===================================================================
    // Commands
    // ===================================================================

    /**
     * Build one command.
     *
     * @param ctx the {@code command} parse tree
     * @return the command
     */
    public Command buildCommand(LiftPatchRefParser.CommandContext ctx) {
        if (ctx.createCmd() != null) {
            return visitCreateCmd(ctx.createCmd());
        }
        if (ctx.upsertCmd() != null) {
            return visitUpsertCmd(ctx.upsertCmd());
        }
        if (ctx.ensureCmd() != null) {
            return visitEnsureCmd(ctx.ensureCmd());
        }
        if (ctx.deleteCmd() != null) {
            return visitDeleteCmd(ctx.deleteCmd());
        }
        if (ctx.moveCmd() != null) {
            return visitMoveCmd(ctx.moveCmd());
        }
        if (ctx.setCmd() != null) {
            return visitSetCmd(ctx.setCmd());
        }
        if (ctx.updateCmd() != null) {
            return visitUpdateCmd(ctx.updateCmd());
        }
        return visitClearCmd(ctx.clearCmd());
    }

    @Override
    public Command.Create visitCreateCmd(LiftPatchRefParser.CreateCmdContext ctx) {
        return new Command.Create(
            visitConstructor(ctx.constructor()),
            ctx.chain() == null ? null : visitChain(ctx.chain()),
            ctx.atClause() == null ? null : visitAtClause(ctx.atClause()),
            ctx.labelBinding() == null ? null : labelName(ctx.labelBinding()),
            sourceText(ctx),
            position(ctx));
    }

    @Override
    public Command.Upsert visitUpsertCmd(LiftPatchRefParser.UpsertCmdContext ctx) {
        return new Command.Upsert(
            visitConstructor(ctx.constructor()),
            ctx.chain() == null ? null : visitChain(ctx.chain()),
            ctx.atClause() == null ? null : visitAtClause(ctx.atClause()),
            ctx.labelBinding() == null ? null : labelName(ctx.labelBinding()),
            sourceText(ctx),
            position(ctx));
    }

    @Override
    public Command.Ensure visitEnsureCmd(LiftPatchRefParser.EnsureCmdContext ctx) {
        LiftPatchRefParser.EnsureTargetContext target = ctx.ensureTarget();
        if (target.constructor() != null) {
            // Parentheses create, and `ensure` does not (Part 1, section 6.1, rule 5).
            throw syntaxError(
                "`ensure` takes a selector between square brackets, not an initializer list; "
                    + "a command that creates a component when it is missing is spelled `upsert`",
                position(target));
        }
        return new Command.Ensure(
            visitStep(target.step()),
            ctx.chain() == null ? null : visitChain(ctx.chain()),
            ctx.labelBinding() == null ? null : labelName(ctx.labelBinding()),
            sourceText(ctx),
            position(ctx));
    }

    @Override
    public Command.Delete visitDeleteCmd(LiftPatchRefParser.DeleteCmdContext ctx) {
        Step target = withMultiplicity(visitStep(ctx.step()), multiplicity(ctx.multiplicity()));
        return new Command.Delete(
            target,
            ctx.chain() == null ? null : visitChain(ctx.chain()),
            sourceText(ctx),
            position(ctx));
    }

    @Override
    public Command.Move visitMoveCmd(LiftPatchRefParser.MoveCmdContext ctx) {
        List<LiftPatchRefParser.ChainContext> chains = ctx.chain();
        if (ctx.atClause() == null) {
            // `move` is the one command with no sensible default position, so a
            // move written without one has no defined meaning (Part 2, section 8.5).
            throw syntaxError("`move` requires an `at` clause", position(ctx));
        }
        return new Command.Move(
            visitStep(ctx.step()),
            chains.isEmpty() ? null : visitChain(chains.get(0)),
            chains.size() > 1 ? visitChain(chains.get(1)) : null,
            visitAtClause(ctx.atClause()),
            sourceText(ctx),
            position(ctx));
    }

    @Override
    public Command.Set visitSetCmd(LiftPatchRefParser.SetCmdContext ctx) {
        return new Command.Set(
            assignments(ctx.assignment()),
            propertyTarget(ctx.chain(), ctx.multiplicity()),
            sourceText(ctx),
            position(ctx));
    }

    @Override
    public Command.Update visitUpdateCmd(LiftPatchRefParser.UpdateCmdContext ctx) {
        return new Command.Update(
            assignments(ctx.assignment()),
            propertyTarget(ctx.chain(), ctx.multiplicity()),
            sourceText(ctx),
            position(ctx));
    }

    @Override
    public Command.Clear visitClearCmd(LiftPatchRefParser.ClearCmdContext ctx) {
        List<PropertyRef> properties = new ArrayList<>();
        for (LiftPatchRefParser.PropertyRefContext p : ctx.propertyRef()) {
            properties.add(visitPropertyRef(p));
        }
        return new Command.Clear(
            properties,
            propertyTarget(ctx.chain(), ctx.multiplicity()),
            sourceText(ctx),
            position(ctx));
    }

    private List<Assignment> assignments(List<LiftPatchRefParser.AssignmentContext> ctxs) {
        List<Assignment> out = new ArrayList<>();
        for (LiftPatchRefParser.AssignmentContext a : ctxs) {
            out.add(new Assignment(
                visitPropertyRef(a.propertyRef()), buildValue(a.value()), position(a)));
        }
        return out;
    }

    /**
     * Build the {@code on} clause of a property command, moving the multiplicity
     * keyword onto the head step of the chain, which is the step it marks.
     */
    private Chain propertyTarget(
        LiftPatchRefParser.ChainContext chainCtx,
        LiftPatchRefParser.MultiplicityContext multCtx
    ) {
        if (chainCtx == null) {
            return null;
        }
        Chain chain = visitChain(chainCtx);
        Multiplicity m = multiplicity(multCtx);
        if (m == null) {
            return chain;
        }
        Chain parent = chain.parentChain();
        Step head = withMultiplicity(chain.head(), m);
        return parent == null
            ? Chain.of(head)
            : parent.prepend(head, chain.axisAbove(0));
    }

    // ===================================================================
    // Constructors and initializers
    // ===================================================================

    @Override
    public Constructor visitConstructor(LiftPatchRefParser.ConstructorContext ctx) {
        List<Initializer> initializers = new ArrayList<>();
        for (LiftPatchRefParser.InitializerContext i : ctx.initializer()) {
            if (i.hasPredicate() != null) {
                initializers.add(new Initializer.Condition(visitHasPredicate(i.hasPredicate())));
                continue;
            }
            PropertyRef property = visitPropertyRef(i.propertyRef());
            Value value = buildValue(i.value());
            Predicate predicate =
                pseudoPredicate(property, Operator.EQ, value, position(i));
            if (predicate instanceof Predicate.HasGloss) {
                // `has-gloss` is written like an assignment and is a matching
                // condition: it is meaningful when looking for an existing entry,
                // and meaningless as an initializer, since the entry being created
                // has no sense yet (Part 2, section 8.2.1).
                initializers.add(new Initializer.Condition(predicate));
                continue;
            }
            initializers.add(new Initializer.Assignment(property, value, position(i)));
        }
        return new Constructor(text(ctx.componentType()), initializers, position(ctx));
    }

    // ===================================================================
    // Chains, steps, selectors
    // ===================================================================

    @Override
    public Chain visitChain(LiftPatchRefParser.ChainContext ctx) {
        List<Step> steps = new ArrayList<>();
        List<Axis> axes = new ArrayList<>();
        for (LiftPatchRefParser.StepContext s : ctx.step()) {
            steps.add(visitStep(s));
        }
        for (LiftPatchRefParser.AxisContext a : ctx.axis()) {
            axes.add(a.WITHIN() != null ? Axis.EXISTENTIAL : Axis.STRICT);
        }
        return new Chain(steps, axes);
    }

    @Override
    public Step visitStep(LiftPatchRefParser.StepContext ctx) {
        SourcePosition pos = position(ctx);
        if (ctx.labelRef() != null) {
            return new Step.Label(labelName(ctx.labelRef()), null, pos);
        }
        List<Predicate> predicates = new ArrayList<>();
        if (ctx.selector() != null) {
            for (LiftPatchRefParser.PredicateContext p : ctx.selector().predicate()) {
                predicates.add(buildPredicate(p));
            }
        }
        Integer ordinal = ctx.ORDINAL() == null
            ? null
            : Integer.valueOf(ctx.ORDINAL().getText().substring(1));
        String typeKey = ctx.typeKey() == null ? null : typeKeyValue(ctx.typeKey());
        return new Step.Component(
            text(ctx.componentType()), predicates, ordinal, typeKey, null, pos);
    }

    private String typeKeyValue(LiftPatchRefParser.TypeKeyContext ctx) {
        return ctx.string() != null ? string(ctx.string()) : text(ctx.anyName());
    }

    /**
     * Build one predicate.
     *
     * <p>The pseudo-property predicates {@code id}, {@code hn} and
     * {@code has-gloss} are written as ordinary comparisons in the grammar, since
     * their names lex as names; they are recognized here and turned into the
     * dedicated nodes, so that everything downstream sees one shape per
     * predicate rather than a comparison it must re-inspect.</p>
     *
     * @param ctx the {@code predicate} parse tree
     * @return the predicate
     */
    public Predicate buildPredicate(LiftPatchRefParser.PredicateContext ctx) {
        SourcePosition pos = position(ctx);
        if (ctx.hasPredicate() != null) {
            return visitHasPredicate(ctx.hasPredicate());
        }
        if (ctx.EXISTS() != null) {
            return new Predicate.Existence(visitPropertyRef(ctx.propertyRef()), false, pos);
        }
        if (ctx.ABSENT() != null) {
            return new Predicate.Existence(visitPropertyRef(ctx.propertyRef()), true, pos);
        }
        PropertyRef property = visitPropertyRef(ctx.propertyRef());
        Operator operator = Operator.parse(ctx.comparison().getText());
        Value value = buildValue(ctx.value());
        return pseudoPredicate(property, operator, value, pos);
    }

    private Predicate pseudoPredicate(
        PropertyRef property, Operator operator, Value value, SourcePosition pos
    ) {
        if (operator != Operator.EQ) {
            return new Predicate.Comparison(property, operator, value, pos);
        }
        switch (property.name()) {
            case "id" -> {
                if (!property.hasQualifier() && value instanceof Value.Str s) {
                    return new Predicate.Id(s.text(), pos);
                }
            }
            case "hn" -> {
                if (!property.hasQualifier() && value instanceof Value.Num n) {
                    return new Predicate.Hn(n.value(), pos);
                }
                // A non-integer `hn`, or one carrying a language key, is ILLEGAL_HN
                // or LANG_KEY_NOT_SUPPORTED_ON_SCALAR; leave it to the validator.
            }
            case "has-gloss" -> {
                if (value instanceof Value.Str s) {
                    return new Predicate.HasGloss(property.language(), s.text(), pos);
                }
            }
            case "index" -> {
                if (!property.hasQualifier() && value instanceof Value.Num n) {
                    return new Predicate.Index(n.value(), pos);
                }
            }
            default -> {
                // An ordinary property predicate.
            }
        }
        return new Predicate.Comparison(property, operator, value, pos);
    }

    @Override
    public Predicate.Has visitHasPredicate(LiftPatchRefParser.HasPredicateContext ctx) {
        return new Predicate.Has(visitStep(ctx.step()), position(ctx));
    }

    @Override
    public PropertyRef visitPropertyRef(LiftPatchRefParser.PropertyRefContext ctx) {
        String name = text(ctx.propertyName());
        if (ctx.ATSIGN() == null) {
            return new PropertyRef(name, null, false, position(ctx));
        }
        if (ctx.STAR() != null) {
            return new PropertyRef(name, null, true, position(ctx));
        }
        return new PropertyRef(name, text(ctx.lang()), false, position(ctx));
    }

    // ===================================================================
    // Values
    // ===================================================================

    /**
     * Build one value.
     *
     * @param ctx the {@code value} parse tree
     * @return the value
     */
    public Value buildValue(LiftPatchRefParser.ValueContext ctx) {
        SourcePosition pos = position(ctx);
        if (ctx.string() != null) {
            return new Value.Str(string(ctx.string()), null, pos);
        }
        if (ctx.INT() != null) {
            return new Value.Num(Long.parseLong(ctx.INT().getText()), pos);
        }
        if (ctx.multitextLiteral() != null) {
            return visitMultitextLiteral(ctx.multitextLiteral());
        }
        Chain chain = visitChain(ctx.chain());
        // A chain of a single label step is a label value, not a chain value: the
        // two are the same component, and the label form is what section 10 calls
        // for on a reference property.
        if (chain.length() == 1 && chain.head() instanceof Step.Label l) {
            return new Value.LabelRef(l.name(), pos);
        }
        return new Value.ChainRef(chain);
    }

    @Override
    public Value.Multitext visitMultitextLiteral(LiftPatchRefParser.MultitextLiteralContext ctx) {
        List<LangText> entries = new ArrayList<>();
        for (LiftPatchRefParser.LangTextContext lt : ctx.langText()) {
            entries.add(new LangText(text(lt.lang()), string(lt.string()), position(lt)));
        }
        return new Value.Multitext(entries, position(ctx));
    }

    // ===================================================================
    // Positions and labels
    // ===================================================================

    @Override
    public AtClause visitAtClause(LiftPatchRefParser.AtClauseContext ctx) {
        LiftPatchRefParser.PositionContext p = ctx.position();
        SourcePosition pos = position(ctx);
        if (p.BEGINNING() != null) {
            return new AtClause(AtClause.Kind.BEGINNING, null, null, pos);
        }
        if (p.END() != null) {
            return new AtClause(AtClause.Kind.END, null, null, pos);
        }
        if (p.INDEX() != null) {
            return new AtClause(
                AtClause.Kind.INDEX, Integer.valueOf(p.INT().getText()), null, pos);
        }
        AtClause.Kind kind = p.BEFORE() != null ? AtClause.Kind.BEFORE : AtClause.Kind.AFTER;
        return new AtClause(kind, null, visitStep(p.step()), pos);
    }

    private String labelName(LiftPatchRefParser.LabelBindingContext ctx) {
        return labelName(ctx.labelRef());
    }

    private String labelName(LiftPatchRefParser.LabelRefContext ctx) {
        return text(ctx.anyName());
    }

    private Multiplicity multiplicity(LiftPatchRefParser.MultiplicityContext ctx) {
        if (ctx == null) {
            return null;
        }
        return ctx.EACH() != null ? Multiplicity.EACH : Multiplicity.ALL;
    }

    private static Step withMultiplicity(Step step, Multiplicity m) {
        if (m == null) {
            return step;
        }
        return switch (step) {
            case Step.Component c -> c.withMultiplicity(m);
            case Step.Label l -> new Step.Label(l.name(), m, l.position());
        };
    }

    // ===================================================================
    // Token helpers
    // ===================================================================

    private static SourcePosition position(ParserRuleContext ctx) {
        return position(ctx.getStart());
    }

    private static SourcePosition position(Token token) {
        return new SourcePosition(token.getLine(), token.getCharPositionInLine() + 1);
    }

    private static String text(ParserRuleContext ctx) {
        return ctx.getText();
    }

    private String string(LiftPatchRefParser.StringContext ctx) {
        TerminalNode node = ctx.STRING_DQ() != null ? ctx.STRING_DQ() : ctx.STRING_SQ();
        return Literals.unquote(node.getText(), position(node.getSymbol()));
    }

    /**
     * The original text of a command, whitespace and all, for plan mode.
     */
    private static String sourceText(ParserRuleContext ctx) {
        Interval interval = new Interval(
            ctx.getStart().getStartIndex(), ctx.getStop().getStopIndex());
        return ctx.getStart().getInputStream().getText(interval)
            .replaceAll("\\s*\\R\\s*", " ")
            .trim();
    }

    private static LiftPatchException syntaxError(String message, SourcePosition position) {
        return new LiftPatchException(
            LiftPatchError.of(ErrorCode.SYNTAX_ERROR, message, position));
    }
}
