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
import fr.cnrs.lacito.liftpatchbox.ast.Verb;
import fr.cnrs.lacito.liftpatchbox.error.ErrorCode;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchError;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchException;
import fr.cnrs.lacito.liftpatchbox.error.SourcePosition;
import fr.cnrs.lacito.liftpatchbox.metamodel.ComponentKind;
import fr.cnrs.lacito.liftpatchbox.metamodel.ComponentTypeDef;
import fr.cnrs.lacito.liftpatchbox.metamodel.LanguageKind;
import fr.cnrs.lacito.liftpatchbox.metamodel.Metamodel;
import fr.cnrs.lacito.liftpatchbox.metamodel.ShortCodes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Parses a LiftPatchShort document into the command model.
 *
 * <p>The work is split in two. {@link ShortLinePreprocessor} decides which lines
 * are commands, strips comments, measures indentation and groups the lines; this
 * class splits each command line into its top-level tokens, hands the
 * well-delimited fragments to the ANTLR parser, expands the concise
 * abbreviations, and assembles the result into the same {@link Command} and
 * {@link Block} objects the reference syntax produces.</p>
 *
 * <p>Three expansions happen here, and they are what makes the concise syntax a
 * surface syntax rather than a language of its own:</p>
 *
 * <ul>
 *   <li>a path, written ancestor first, is reversed into the child-first chain of
 *       the reference syntax, and the unwritten strict link between a parent path
 *       and its direct target is made explicit;</li>
 *   <li>an abbreviated step — a bare or quoted token standing for an entry form, a
 *       sense gloss or an example text — is expanded into the component step it
 *       abbreviates, using the depth of the step below the root;</li>
 *   <li>the {@code p /path} idiom is expanded into the nested upserts of Part 3,
 *       section 8, and an indented block into the block of Part 2, section 11.</li>
 * </ul>
 */
public final class ShortScriptParser {

    private static final Pattern PRAGMA =
        Pattern.compile("%liftpatch\\s+(\\d+\\.\\d+)(.*)");
    private static final Pattern PRAGMA_ATTRIBUTE =
        Pattern.compile("([\\p{L}][\\p{L}\\p{N}_-]*)\\s*=\\s*\"([^\"]*)\"|"
            + "([\\p{L}][\\p{L}\\p{N}_-]*)\\s*=\\s*'([^']*)'");
    private static final Pattern CONSTRUCTOR_TOKEN =
        Pattern.compile("^[\\p{L}\\p{M}\\p{Nd}_.-]+\\(");

    /** The properties the three abbreviated depths stand for (Part 3, section 6.1). */
    private static final String[] ABBREVIATED_TYPES = {"entry", "sense", "example"};
    private static final String[] ABBREVIATED_PROPERTIES = {"form", "gloss", "text"};

    private final String source;
    private final Metamodel metamodel;

    /**
     * Counts the operations emitted so far, so that a static error raised while a
     * command line is being built carries the index that command will have in a
     * plan document (Part 2, section 12.4.1).
     */
    private int operationCounter;

    /**
     * A parser for a document read from the named source.
     *
     * @param source    the source name, used in diagnostics and plan documents
     * @param metamodel the metamodel, which decides whether a bare token is a
     *                  singleton step or an abbreviated one
     */
    public ShortScriptParser(String source, Metamodel metamodel) {
        this.source = source;
        this.metamodel = metamodel;
    }

    // ===================================================================
    // Entry point
    // ===================================================================

    /**
     * Parse a whole document.
     *
     * @param document the document text, commands and prose alike
     * @return the script
     * @throws LiftPatchException with a static error code when a line that the
     *         recognition rule accepts does not parse, or breaks one of the rules
     *         the grammar cannot express
     */
    public Script parse(String document) {
        ShortLinePreprocessor.Scan scan = new ShortLinePreprocessor(source).scan(document);
        operationCounter = 0;

        Pragma pragma = scan.pragmaLine() == null
            ? null
            : parsePragma(scan.pragmaLine());

        Frame root = new Frame(-1, null);
        List<Frame> stack = new ArrayList<>();
        Object lastEmitted = null;
        Frame lastContainer = root;
        int lastIndent = -1;
        int lastGroup = -1;

        for (ShortLinePreprocessor.Recognized line : scan.lines()) {
            if (line.groupId() != lastGroup) {
                // Indentation relates commands inside one group only: a command can
                // never be the parent of a command in another group, however the two
                // are indented.
                stack.clear();
                lastEmitted = null;
                lastContainer = root;
                lastIndent = -1;
                lastGroup = line.groupId();
            }

            Frame container;
            if (canAnchor(lastEmitted) && line.indent() > lastIndent) {
                container = openBlock(stack, lastContainer, lastEmitted, line.indent());
            } else {
                while (!stack.isEmpty() && line.indent() < stack.get(stack.size() - 1).bodyIndent) {
                    stack.remove(stack.size() - 1);
                }
                if (!stack.isEmpty() && line.indent() != stack.get(stack.size() - 1).bodyIndent) {
                    throw error(ErrorCode.SYNTAX_ERROR,
                        "this indentation matches no open block level", line.position(),
                        operationCounter + 1);
                }
                container = stack.isEmpty() ? root : stack.get(stack.size() - 1);
            }

            Object emitted = buildLine(line);
            container.body.add(emitted);
            lastEmitted = emitted;
            lastContainer = container;
            lastIndent = line.indent();
        }

        List<Item> items = new ArrayList<>();
        for (Object o : root.body) {
            items.add(toItem(o));
        }
        return new Script(pragma, items, Syntax.CONCISE, source, scan.commandCount());
    }

    /**
     * Whether the last item emitted can be the anchor of an indented block. A
     * directive designates no component and so anchors nothing; a {@code d} and a
     * {@code m} are refused by the validator, not here, so that the error carries
     * its own code rather than a parse failure.
     */
    private static boolean canAnchor(Object lastEmitted) {
        return lastEmitted instanceof Command || lastEmitted instanceof Frame;
    }

    /**
     * Open the block that an indented line starts, turning the anchor into its
     * header.
     */
    private Frame openBlock(List<Frame> stack, Frame container, Object anchor, int bodyIndent) {
        Frame frame;
        if (anchor instanceof Frame existing) {
            // The anchor is itself a block: the `p /path` idiom, whose children
            // belong to the innermost component it upserts.
            frame = innermost(existing);
            frame.bodyIndent = bodyIndent;
        } else {
            // A plain command becomes the header of a new block, so it leaves the
            // list it was appended to and the block takes its place.
            container.body.remove(container.body.size() - 1);
            frame = new Frame(bodyIndent, LiftPatch.anchorHeader((Command) anchor));
            container.body.add(frame);
        }
        stack.add(frame);
        return frame;
    }

    private static Frame innermost(Frame f) {
        Frame inner = f;
        while (!inner.body.isEmpty() && inner.body.get(inner.body.size() - 1) instanceof Frame g) {
            inner = g;
        }
        return inner;
    }

    private Item toItem(Object o) {
        if (o instanceof Frame f) {
            List<Item> body = new ArrayList<>();
            for (Object child : f.body) {
                body.add(toItem(child));
            }
            return new Block(f.header, body, f.header.position());
        }
        return (Item) o;
    }

    /** A block being built: its header, its body, and the indentation of its body. */
    private static final class Frame {

        private int bodyIndent;
        private final BlockHeader header;
        private final List<Object> body = new ArrayList<>();

        Frame(int bodyIndent, BlockHeader header) {
            this.bodyIndent = bodyIndent;
            this.header = header;
        }
    }

    // ===================================================================
    // One line
    // ===================================================================

    private Object buildLine(ShortLinePreprocessor.Recognized line) {
        if (line.kind() == ShortLinePreprocessor.Recognized.Kind.DIRECTIVE) {
            Directive d = parseDirective(line);
            if (d.isOperation()) {
                operationCounter++;
            }
            return d;
        }
        Object built = buildCommand(line);
        if (built instanceof Frame f) {
            operationCounter += countOperations(f);
        } else {
            operationCounter++;
        }
        return built;
    }

    private static int countOperations(Frame f) {
        int n = 1;
        for (Object o : f.body) {
            n += o instanceof Frame g ? countOperations(g) : 1;
        }
        return n;
    }

    private Pragma parsePragma(ShortLinePreprocessor.Recognized line) {
        Matcher m = PRAGMA.matcher(line.body().strip());
        if (!m.matches()) {
            throw error(ErrorCode.SYNTAX_ERROR, "malformed version pragma", line.position(), 0);
        }
        Map<String, String> attributes = new LinkedHashMap<>();
        Matcher a = PRAGMA_ATTRIBUTE.matcher(m.group(2));
        while (a.find()) {
            if (a.group(1) != null) {
                attributes.put(a.group(1), a.group(2));
            } else {
                attributes.put(a.group(3), a.group(4));
            }
        }
        return new Pragma(m.group(1), attributes, line.position());
    }

    private Directive parseDirective(ShortLinePreprocessor.Recognized line) {
        LiftPatchShortParser p = parserFor(line.body(), line.lineNumber(), line.bodyColumn());
        LiftPatchShortParser.DirectiveOnlyContext ctx = p.directiveOnly();
        checkErrors(p);
        boolean create = ctx.LANGUAGE_CREATE() != null;
        LanguageKind kind =
            ctx.langKind().OBJECT() != null ? LanguageKind.OBJECT : LanguageKind.META;
        return new Directive(create, kind, string(ctx.string()), line.position());
    }

    private Object buildCommand(ShortLinePreprocessor.Recognized line) {
        List<TopLevelSplitter.Token> tokens =
            TopLevelSplitter.split(line.body(), line.bodyColumn());
        if (tokens.isEmpty()) {
            throw error(ErrorCode.SYNTAX_ERROR, "a command needs an argument", line.position(),
                operationCounter + 1);
        }
        int tailStart = tokens.size();
        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i).text();
            if (t.equals("to") || t.equals("at") || t.equals("as")) {
                tailStart = i;
                break;
            }
        }
        List<TopLevelSplitter.Token> main = tokens.subList(0, tailStart);
        Tail tail = parseTail(tokens.subList(tailStart, tokens.size()), line);
        Multiplicity multiplicity = line.star() ? Multiplicity.STAR : null;

        return switch (line.verb()) {
            case CREATE, UPSERT -> buildCreateOrUpsert(line, main, tail, multiplicity);
            case DELETE, ENSURE -> buildDeleteOrEnsure(line, main, tail, multiplicity);
            case MOVE -> buildMove(line, main, tail, multiplicity);
            case SET, UPDATE, CLEAR -> buildPropertyCommand(line, main, tail, multiplicity);
        };
    }

    // ===================================================================
    // c and p
    // ===================================================================

    private Object buildCreateOrUpsert(
        ShortLinePreprocessor.Recognized line,
        List<TopLevelSplitter.Token> main,
        Tail tail,
        Multiplicity multiplicity
    ) {
        if (multiplicity != null) {
            // The grammar admits the marker after every command letter precisely so
            // that this check can report the code: a marker on the wrong verb is a
            // mistake about the language, and saying so is more useful than saying
            // the line does not parse (Part 3, section 2.1).
            throw error(ErrorCode.MULTIPLICITY_NOT_ALLOWED,
                "`" + line.verb().keyword() + "` operates on exactly one component",
                line.position(), operationCounter + 1);
        }
        if (main.isEmpty()) {
            throw error(ErrorCode.SYNTAX_ERROR, "a `" + line.verb().code()
                + "` command needs a path or a constructor", line.position(),
                operationCounter + 1);
        }
        TopLevelSplitter.Token last = main.get(main.size() - 1);
        boolean hasConstructor = CONSTRUCTOR_TOKEN.matcher(last.text()).find();

        if (!hasConstructor) {
            if (main.size() > 1) {
                throw error(ErrorCode.SYNTAX_ERROR,
                    "a path-only `" + line.verb().code() + "` command takes one path",
                    position(line, last), operationCounter + 1);
            }
            return buildPathOnly(line, last, tail, multiplicity);
        }

        ParsedPath path = null;
        if (main.size() > 1) {
            if (main.size() > 2) {
                throw error(ErrorCode.SYNTAX_ERROR,
                    "a `" + line.verb().code() + "` command takes one parent path",
                    position(line, main.get(0)), operationCounter + 1);
            }
            path = parsePath(main.get(0), line, startDepthOf(main.get(0)));
        }
        Chain parent = path == null ? null : path.toChain();
        int depth = path == null ? -1 : path.steps.size() + 1;
        Constructor constructor = parseConstructor(last, line, depth);

        if (line.verb() == Verb.CREATE) {
            return new Command.Create(constructor, parent, tail.at, tail.label,
                line.rawText(), line.position());
        }
        return new Command.Upsert(constructor, parent, tail.at, tail.label,
            line.rawText(), line.position());
    }

    /**
     * The path-only forms: {@code c /mami/pig}, which creates the component the
     * last step denotes, and {@code p /mami/pig}, the idiom of Part 3, section 8,
     * which upserts every step of the path in turn.
     */
    private Object buildPathOnly(
        ShortLinePreprocessor.Recognized line,
        TopLevelSplitter.Token token,
        Tail tail,
        Multiplicity multiplicity
    ) {
        ParsedPath path = parsePath(token, line, startDepthOf(token));
        if (line.verb() == Verb.CREATE) {
            if (!path.abbreviated.get(path.steps.size() - 1)) {
                throw error(ErrorCode.CONSTRUCTOR_REQUIRED,
                    "a path-only `c` creates the component its last step denotes, and can do so "
                        + "only when that step is an abbreviated one; write the constructor",
                    position(line, token), operationCounter + 1);
            }
            Constructor constructor = path.constructorFor(path.steps.size() - 1);
            Chain parent = path.steps.size() == 1 ? null : path.toChainUpTo(path.steps.size() - 1);
            return new Command.Create(constructor, parent, tail.at, tail.label,
                line.rawText(), line.position());
        }

        // `p /path`: every step must be an abbreviated one, because a selector
        // states a condition and there is no rule for turning an arbitrary
        // condition into the initializers of a component that has to be created.
        for (int i = 0; i < path.steps.size(); i++) {
            if (!path.abbreviated.get(i)) {
                throw error(ErrorCode.CONSTRUCTOR_REQUIRED,
                    "every step of a path-only `p` is upserted, so every step must be an "
                        + "abbreviated one; write the constructor",
                    position(line, token), operationCounter + 1);
            }
        }
        return buildUpsertIdiom(line, path, tail);
    }

    /**
     * Expand {@code p /mami/pig} into the nested upserts of Part 3, section 8.
     *
     * <p>The disambiguation of the entry comes from the second step, whenever
     * there is one, which is what keeps the idiom from raising
     * {@code UPSERT_ENTRY_WITHOUT_DISAMBIGUATION}; steps below the second add no
     * disambiguation, a sense and an example having a natural identity of their
     * own.</p>
     */
    private Object buildUpsertIdiom(
        ShortLinePreprocessor.Recognized line, ParsedPath path, Tail tail
    ) {
        int n = path.steps.size();
        if (n == 1) {
            // A single abbreviated step: an entry with nothing to tell the
            // homophones apart. Legal to write, refused by the validator.
            return new Command.Upsert(path.constructorFor(0), null, tail.at, tail.label,
                line.rawText(), line.position());
        }

        List<Frame> frames = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Constructor c = path.constructorFor(i);
            if (i == 0) {
                Predicate.HasGloss hasGloss = new Predicate.HasGloss(
                    path.languages.get(1), path.values.get(1), path.positions.get(1));
                List<Initializer> initializers = new ArrayList<>(c.initializers());
                initializers.add(new Initializer.Condition(hasGloss));
                c = new Constructor(c.componentType(), initializers, c.position());
            }
            Command.Upsert upsert = new Command.Upsert(
                c, null, i == n - 1 ? tail.at : null, i == n - 1 ? tail.label : null,
                line.rawText(), line.position());
            frames.add(new Frame(-1, LiftPatch.anchorHeader(upsert)));
        }
        for (int i = 0; i < n - 1; i++) {
            frames.get(i).body.add(frames.get(i + 1));
        }
        return frames.get(0);
    }

    // ===================================================================
    // d and e
    // ===================================================================

    private Object buildDeleteOrEnsure(
        ShortLinePreprocessor.Recognized line,
        List<TopLevelSplitter.Token> main,
        Tail tail,
        Multiplicity multiplicity
    ) {
        if (main.isEmpty()) {
            throw error(ErrorCode.SYNTAX_ERROR,
                "a `" + line.verb().code() + "` command needs a target step",
                line.position(), operationCounter + 1);
        }
        if (main.size() > 2) {
            throw error(ErrorCode.SYNTAX_ERROR,
                "a `" + line.verb().code() + "` command takes a parent path and one target step",
                position(line, main.get(0)), operationCounter + 1);
        }
        ParsedPath parentPath = main.size() == 2
            ? parsePath(main.get(0), line, startDepthOf(main.get(0)))
            : null;
        TopLevelSplitter.Token targetToken = main.get(main.size() - 1);
        ParsedPath targetPath = parsePath(targetToken, line,
            targetDepth(parentPath, targetToken), parentTypeOf(parentPath));
        if (targetPath.steps.size() != 1) {
            throw error(ErrorCode.TARGET_MUST_BE_A_SINGLE_STEP,
                "the direct target of a `" + line.verb().code()
                    + "` is exactly one step, written after the parent path",
                position(line, targetToken), operationCounter + 1);
        }
        if (parentPath != null && targetPath.absolute) {
            throw error(ErrorCode.SYNTAX_ERROR,
                "the root marker `/` may be written on a target step only when no parent path "
                    + "precedes it",
                position(line, targetToken), operationCounter + 1);
        }
        Step target = targetPath.steps.get(0);
        if (multiplicity != null) {
            target = withMultiplicity(target, multiplicity);
        }
        Chain parent = parentPath == null ? null : parentPath.toChain();

        if (line.verb() == Verb.DELETE) {
            return new Command.Delete(target, parent, line.rawText(), line.position());
        }
        return new Command.Ensure(target, parent, tail.label, line.rawText(), line.position());
    }

    // ===================================================================
    // m
    // ===================================================================

    private Object buildMove(
        ShortLinePreprocessor.Recognized line,
        List<TopLevelSplitter.Token> main,
        Tail tail,
        Multiplicity multiplicity
    ) {
        if (main.isEmpty()) {
            throw error(ErrorCode.SYNTAX_ERROR, "a `m` command needs a source",
                line.position(), operationCounter + 1);
        }
        ParsedPath sourcePath = main.size() >= 2
            ? parsePath(main.get(0), line, startDepthOf(main.get(0)))
            : null;
        TopLevelSplitter.Token targetToken = main.get(main.size() - 1);
        ParsedPath targetPath = parsePath(targetToken, line,
            targetDepth(sourcePath, targetToken), parentTypeOf(sourcePath));
        if (targetPath.steps.size() != 1) {
            throw error(ErrorCode.TARGET_MUST_BE_A_SINGLE_STEP,
                "the source of a `m` is exactly one step, written after the parent path",
                position(line, targetToken), operationCounter + 1);
        }
        Step target = targetPath.steps.get(0);
        if (multiplicity != null) {
            target = withMultiplicity(target, multiplicity);
        }
        if (sourcePath == null && target instanceof Step.Component c
            && Metamodel.ENTRY.equals(c.componentType())) {
            throw error(ErrorCode.SYNTAX_ERROR,
                "`m` cannot target an entry: the dictionary, not the script, orders the entry list",
                position(line, targetToken), operationCounter + 1);
        }
        return new Command.Move(
            target,
            sourcePath == null ? null : sourcePath.toChain(),
            tail.destination,
            tail.at,
            line.rawText(),
            line.position());
    }

    // ===================================================================
    // s, u and l
    // ===================================================================

    private Object buildPropertyCommand(
        ShortLinePreprocessor.Recognized line,
        List<TopLevelSplitter.Token> main,
        Tail tail,
        Multiplicity multiplicity
    ) {
        if (main.isEmpty()) {
            throw error(ErrorCode.SYNTAX_ERROR,
                "a `" + line.verb().code() + "` command needs a parenthesized property list",
                line.position(), operationCounter + 1);
        }
        TopLevelSplitter.Token last = main.get(main.size() - 1);
        if (!last.text().startsWith("(")) {
            throw error(ErrorCode.SYNTAX_ERROR,
                "a `" + line.verb().code() + "` command ends with a parenthesized property list",
                position(line, last), operationCounter + 1);
        }
        if (main.size() > 3) {
            throw error(ErrorCode.SYNTAX_ERROR,
                "a `" + line.verb().code() + "` command takes a path and at most one further step",
                position(line, main.get(0)), operationCounter + 1);
        }
        Chain target = null;
        if (main.size() >= 2) {
            ParsedPath path = parsePath(main.get(0), line, startDepthOf(main.get(0)));
            target = path.toChain();
            if (main.size() == 3) {
                // `u /memi n^general (…)`: the immediate parent is written as a
                // separate step, joined to the path by the strict axis, exactly as
                // the target step of a `d` or an `e` is (Part 3, section 4.1).
                TopLevelSplitter.Token stepToken = main.get(1);
                ParsedPath stepPath = parsePath(stepToken, line,
                    targetDepth(path, stepToken), parentTypeOf(path));
                if (stepPath.steps.size() != 1) {
                    throw error(ErrorCode.TARGET_MUST_BE_A_SINGLE_STEP,
                        "the component a property command writes on is one step",
                        position(line, stepToken), operationCounter + 1);
                }
                target = target.prepend(stepPath.steps.get(0), Axis.STRICT);
            }
            if (multiplicity != null) {
                Chain parent = target.parentChain();
                Step head = withMultiplicity(target.head(), multiplicity);
                target = parent == null ? Chain.of(head) : parent.prepend(head, target.axisAbove(0));
            }
        }

        LiftPatchShortParser p = parserFor(last.text(), line.lineNumber(), last.column());
        if (line.verb() == Verb.CLEAR) {
            LiftPatchShortParser.PropertyListOnlyContext ctx = p.propertyListOnly();
            checkErrors(p);
            List<PropertyRef> properties = new ArrayList<>();
            for (LiftPatchShortParser.PropertyRefContext pr : ctx.propertyRef()) {
                properties.add(propertyRef(pr));
            }
            return new Command.Clear(properties, target, line.rawText(), line.position());
        }
        LiftPatchShortParser.AssignListOnlyContext ctx = p.assignListOnly();
        checkErrors(p);
        List<Assignment> assignments = new ArrayList<>();
        for (LiftPatchShortParser.AssignmentContext a : ctx.assignment()) {
            assignments.add(new Assignment(
                propertyRef(a.propertyRef()), shortValue(a.shortValue()), position(a)));
        }
        if (line.verb() == Verb.SET) {
            return new Command.Set(assignments, target, line.rawText(), line.position());
        }
        return new Command.Update(assignments, target, line.rawText(), line.position());
    }

    // ===================================================================
    // The tail: to, at and as
    // ===================================================================

    /**
     * The trailing clauses of a command line.
     *
     * @param destination the {@code to} path of a {@code m}, or {@code null}
     * @param at          the {@code at} clause, or {@code null}
     * @param label       the label bound by {@code as $name}, or {@code null}
     */
    private record Tail(Chain destination, AtClause at, String label) {

        static final Tail EMPTY = new Tail(null, null, null);
    }

    private Tail parseTail(List<TopLevelSplitter.Token> tokens, ShortLinePreprocessor.Recognized line) {
        if (tokens.isEmpty()) {
            return Tail.EMPTY;
        }
        StringBuilder sb = new StringBuilder();
        for (TopLevelSplitter.Token t : tokens) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(t.text());
        }
        int column = tokens.get(0).column();
        LiftPatchShortParser p = parserFor(sb.toString(), line.lineNumber(), column);
        LiftPatchShortParser.TailOnlyContext ctx = p.tailOnly();
        checkErrors(p);

        Chain destination = null;
        if (ctx.path() != null) {
            destination = pathOf(ctx.path(), line, 1, null).toChain();
        }
        AtClause at = ctx.atClause() == null ? null : atClause(ctx.atClause(), line);
        String label = ctx.labelBinding() == null
            ? null
            : anyName(ctx.labelBinding().labelRef().anyName());
        return new Tail(destination, at, label);
    }

    private AtClause atClause(LiftPatchShortParser.AtClauseContext ctx, ShortLinePreprocessor.Recognized line) {
        LiftPatchShortParser.PositionContext pos = ctx.position();
        SourcePosition where = position(ctx);
        if (pos.BEGINNING() != null) {
            return new AtClause(AtClause.Kind.BEGINNING, null, null, where);
        }
        if (pos.END() != null) {
            return new AtClause(AtClause.Kind.END, null, null, where);
        }
        if (pos.INDEX() != null) {
            return new AtClause(AtClause.Kind.INDEX, Integer.valueOf(pos.INT().getText()),
                null, where);
        }
        AtClause.Kind kind =
            pos.BEFORE() != null ? AtClause.Kind.BEFORE : AtClause.Kind.AFTER;
        Step step;
        if (pos.ORDINAL() != null) {
            // `at before #2`: the component letter may be omitted when it is the one
            // of the component being placed, which the engine supplies.
            step = new Step.Component(null, List.of(),
                Integer.valueOf(pos.ORDINAL().getText().substring(1)), null, null, where);
        } else {
            step = buildStep(pos.pathStep(), -1, null, line).step;
        }
        return new AtClause(kind, null, step, where);
    }

    // ===================================================================
    // Paths
    // ===================================================================

    /**
     * A concise path, kept in the order it was written — ancestor first — together
     * with what each step abbreviates, so that the path-only forms of {@code c}
     * and {@code p} can build a constructor from it.
     */
    private final class ParsedPath {

        private final boolean absolute;

        /**
         * Whether the depth of the first step from the root is written down, which
         * is what an abbreviated step needs. It is not, in a relative path inside an
         * indented block or in a path rooted at a label.
         */
        private final boolean depthKnown;
        private final List<Step> steps = new ArrayList<>();
        private final List<Axis> axes = new ArrayList<>();
        private final List<Boolean> abbreviated = new ArrayList<>();
        private final List<String> types = new ArrayList<>();
        private final List<String> properties = new ArrayList<>();
        private final List<String> values = new ArrayList<>();
        private final List<String> languages = new ArrayList<>();
        private final List<SourcePosition> positions = new ArrayList<>();

        ParsedPath(boolean absolute, boolean depthKnown) {
            this.absolute = absolute;
            this.depthKnown = depthKnown;
        }

        /**
         * The chain of the whole path, reversed into the child-first order of the
         * reference syntax.
         */
        Chain toChain() {
            return toChainUpTo(steps.size());
        }

        /**
         * The chain of the first {@code count} steps of the path, reversed.
         */
        Chain toChainUpTo(int count) {
            List<Step> reversed = new ArrayList<>(count);
            for (int i = count - 1; i >= 0; i--) {
                reversed.add(steps.get(i));
            }
            List<Axis> reversedAxes = new ArrayList<>(Math.max(0, count - 1));
            for (int i = count - 2; i >= 0; i--) {
                reversedAxes.add(axes.get(i));
            }
            return new Chain(reversed, reversedAxes);
        }

        /**
         * The constructor the abbreviated step at {@code index} stands for.
         */
        Constructor constructorFor(int index) {
            PropertyRef property =
                new PropertyRef(properties.get(index), languages.get(index), false,
                    positions.get(index));
            Initializer init = new Initializer.Assignment(
                property,
                new Value.Str(values.get(index), null, positions.get(index)),
                positions.get(index));
            return new Constructor(types.get(index), List.of(init), positions.get(index));
        }
    }

    private ParsedPath parsePath(
        TopLevelSplitter.Token token, ShortLinePreprocessor.Recognized line, int startDepth
    ) {
        return parsePath(token, line, startDepth, null);
    }

    private ParsedPath parsePath(
        TopLevelSplitter.Token token, ShortLinePreprocessor.Recognized line,
        int startDepth, String parentTypeHint
    ) {
        LiftPatchShortParser p = parserFor(token.text(), line.lineNumber(), token.column());
        LiftPatchShortParser.PathOnlyContext ctx = p.pathOnly();
        checkErrors(p);
        return pathOf(ctx.path(), line, startDepth, parentTypeHint);
    }

    /**
     * The depth of the first step of a path token: 1 when the path is rooted, and
     * unknown otherwise, since a relative path inside an indented block does not
     * write its depth from the root down (Part 3, section 6.1).
     */
    private static int startDepthOf(TopLevelSplitter.Token token) {
        return token.text().startsWith("/") ? 1 : -1;
    }

    /**
     * The depth of a target step: its own when it carries the root marker, one
     * below the parent path when that path's depth is known, and unknown otherwise.
     */
    private static int targetDepth(ParsedPath parentPath, TopLevelSplitter.Token targetToken) {
        if (targetToken.text().startsWith("/")) {
            return 1;
        }
        if (parentPath == null) {
            return -1;
        }
        return parentPath.depthKnown ? parentPath.steps.size() + 1 : -1;
    }

    /**
     * The component type of the last step of a parent path, which is what tells a
     * bare singleton step apart from an abbreviated one (Part 3, section 2, rule 5).
     */
    private static String parentTypeOf(ParsedPath parentPath) {
        if (parentPath == null || parentPath.types.isEmpty()) {
            return null;
        }
        return parentPath.types.get(parentPath.types.size() - 1);
    }

    private ParsedPath pathOf(
        LiftPatchShortParser.PathContext ctx,
        ShortLinePreprocessor.Recognized line,
        int startDepth,
        String parentTypeHint
    ) {
        List<LiftPatchShortParser.PathStepContext> stepCtxs = new ArrayList<>();
        List<Axis> axes = new ArrayList<>();
        boolean absolute;
        String rootLabel = null;

        switch (ctx) {
            case LiftPatchShortParser.AbsolutePathContext a -> {
                absolute = true;
                stepCtxs.addAll(a.pathStep());
                for (LiftPatchShortParser.AxisContext ax : a.axis()) {
                    axes.add(ax.BANG() != null ? Axis.STRICT : Axis.EXISTENTIAL);
                }
            }
            case LiftPatchShortParser.LabelPathContext l -> {
                absolute = true;
                rootLabel = anyName(l.labelRef().anyName());
                stepCtxs.addAll(l.pathStep());
                for (LiftPatchShortParser.AxisContext ax : l.axis()) {
                    axes.add(ax.BANG() != null ? Axis.STRICT : Axis.EXISTENTIAL);
                }
            }
            case LiftPatchShortParser.RelativePathContext r -> {
                absolute = false;
                stepCtxs.addAll(r.pathStep());
                for (LiftPatchShortParser.AxisContext ax : r.axis()) {
                    axes.add(ax.BANG() != null ? Axis.STRICT : Axis.EXISTENTIAL);
                }
            }
            default -> throw new IllegalStateException("Unknown path shape");
        }

        ParsedPath path = new ParsedPath(absolute, rootLabel == null && startDepth >= 1);
        String parentType = parentTypeHint;
        int depth = startDepth;

        if (rootLabel != null) {
            path.steps.add(new Step.Label(rootLabel, null, position(ctx)));
            path.abbreviated.add(Boolean.FALSE);
            path.types.add(null);
            path.properties.add(null);
            path.values.add(null);
            path.languages.add(null);
            path.positions.add(position(ctx));
            // Below a label the depth from the root is not written down, so no step
            // may be abbreviated (Part 3, section 6.1).
            depth = -1;
        }

        for (int i = 0; i < stepCtxs.size(); i++) {
            StepResult r = buildStep(stepCtxs.get(i), depth, parentType, line);
            path.steps.add(r.step);
            path.abbreviated.add(r.abbreviated);
            path.types.add(r.componentType);
            path.properties.add(r.property);
            path.values.add(r.value);
            path.languages.add(r.language);
            path.positions.add(r.step.position());
            parentType = r.componentType;
            if (depth > 0) {
                depth++;
            }
        }
        // In all three shapes the grammar yields exactly one axis fewer than the
        // number of steps: an absolute or relative path writes `n - 1` axes for its
        // `n` steps, and a label path writes `n` axes for the label plus its `n`
        // steps. The two lists therefore line up without adjustment.
        path.axes.addAll(axes);
        return path;
    }

    // ===================================================================
    // Steps
    // ===================================================================

    /** A built step, with what it abbreviates when it is an abbreviated step. */
    private record StepResult(
        Step step,
        String componentType,
        boolean abbreviated,
        String property,
        String value,
        String language
    ) {
    }

    private StepResult buildStep(
        LiftPatchShortParser.PathStepContext ctx,
        int depth,
        String parentType,
        ShortLinePreprocessor.Recognized line
    ) {
        SourcePosition where = position(ctx);

        if (ctx.labelRef() != null) {
            return new StepResult(
                new Step.Label(anyName(ctx.labelRef().anyName()), null, where),
                null, false, null, null, null);
        }

        if (ctx.componentCode() != null) {
            String code = ctx.componentCode().getText();
            String type = ShortCodes.componentName(code).orElse(code);
            List<Predicate> predicates = new ArrayList<>();
            if (ctx.selector() != null) {
                for (LiftPatchShortParser.PredicateContext pc : ctx.selector().predicate()) {
                    predicates.add(predicate(pc, line));
                }
            }
            Integer ordinal = null;
            if (ctx.ORDINAL() != null) {
                ordinal = Integer.valueOf(ctx.ORDINAL().getText().substring(1));
            } else if (ctx.INT() != null) {
                // The `[n]` spelling of the ordinal, kept for concision.
                ordinal = Integer.valueOf(ctx.INT().getText());
            }
            String typeKey = ctx.typeKey() == null ? null : typeKeyValue(ctx.typeKey());
            return new StepResult(
                new Step.Component(type, predicates, ordinal, typeKey, null, where),
                type, false, null, null, null);
        }

        // An abbreviated step -- unless the token is exactly the code or the name of
        // a singleton component type admissible at this point (Part 3, section 2,
        // rule 5). A quoted token is always an abbreviated step.
        LiftPatchShortParser.AbbreviatedStepContext abbrev = ctx.abbreviatedStep();
        boolean quoted = abbrev.string() != null;
        String token = quoted
            ? string(abbrev.string())
            : abbrev.bareWord().getText();
        String language = abbrev.lang() == null ? null : abbrev.lang().getText();

        if (!quoted && language == null) {
            Optional<String> singleton = singletonNamed(token, parentType);
            if (singleton.isPresent()) {
                return new StepResult(
                    new Step.Component(singleton.get(), List.of(), null, null, null, where),
                    singleton.get(), false, null, null, null);
            }
        }

        if (depth < 1 || depth > ABBREVIATED_TYPES.length) {
            throw error(ErrorCode.ABBREVIATED_STEP_NOT_ALLOWED_HERE,
                depth < 1
                    ? "an abbreviated step is available only where the depth from the root is "
                        + "written down, that is in an absolute path that does not begin with a "
                        + "label"
                    : "there is no abbreviation below the third depth: at depth " + depth
                        + " the component type is no longer determined by the depth",
                where, operationCounter + 1);
        }
        String type = ABBREVIATED_TYPES[depth - 1];
        String property = ABBREVIATED_PROPERTIES[depth - 1];
        Predicate eq = new Predicate.Comparison(
            new PropertyRef(property, language, false, where),
            Operator.EQ,
            new Value.Str(token, null, where),
            where);
        return new StepResult(
            new Step.Component(type, List.of(eq), null, null, null, where),
            type, true, property, token, language);
    }

    /**
     * Whether a bare token names a singleton component type the metamodel admits as
     * a child at this point of the path.
     */
    private Optional<String> singletonNamed(String token, String parentType) {
        Optional<String> name = ShortCodes.componentName(token);
        if (name.isEmpty()) {
            return Optional.empty();
        }
        Optional<ComponentTypeDef> def = metamodel.componentType(name.get());
        if (def.isEmpty() || def.get().kind() != ComponentKind.SINGLETON) {
            return Optional.empty();
        }
        if (parentType == null) {
            return Optional.empty();
        }
        return metamodel.isLegalParent(parentType, name.get()) ? name : Optional.empty();
    }

    private String typeKeyValue(LiftPatchShortParser.TypeKeyContext ctx) {
        return ctx.string() != null ? string(ctx.string()) : ctx.bareWord().getText();
    }

    // ===================================================================
    // Constructors, predicates, values
    // ===================================================================

    private Constructor parseConstructor(
        TopLevelSplitter.Token token, ShortLinePreprocessor.Recognized line, int depth
    ) {
        LiftPatchShortParser p = parserFor(token.text(), line.lineNumber(), token.column());
        LiftPatchShortParser.ConstructorOnlyContext ctx = p.constructorOnly();
        checkErrors(p);
        return constructor(ctx.constructor(), line);
    }

    private Constructor constructor(
        LiftPatchShortParser.ConstructorContext ctx, ShortLinePreprocessor.Recognized line
    ) {
        String code = ctx.componentCode().getText();
        String type = ShortCodes.componentName(code).orElse(code);
        List<Initializer> initializers = new ArrayList<>();
        int unnamed = 0;
        List<String> unnamedMapping = metamodel.componentType(type)
            .map(ComponentTypeDef::unnamedArguments)
            .orElse(List.of());

        for (LiftPatchShortParser.ShortInitContext init : ctx.shortInit()) {
            if (init.hasPredicate() != null) {
                initializers.add(new Initializer.Condition(hasPredicate(init.hasPredicate(), line)));
                continue;
            }
            if (init.constructor() != null) {
                initializers.add(new Initializer.Embedded(constructor(init.constructor(), line)));
                continue;
            }
            if (init.propertyRef() != null) {
                PropertyRef property = propertyRef(init.propertyRef());
                Value value = shortValue(init.shortValue());
                if ("has-gloss".equals(property.name()) && value instanceof Value.Str str) {
                    // `has-gloss` is written like an assignment and is a matching
                    // condition (Part 2, section 8.2.1).
                    initializers.add(new Initializer.Condition(new Predicate.HasGloss(
                        property.language(), str.text(), position(init))));
                    continue;
                }
                initializers.add(
                    new Initializer.Assignment(property, value, position(init)));
                continue;
            }
            // An unnamed argument: the properties of the type, in the order the
            // metamodel declares in `unnamedArguments`.
            if (unnamed >= unnamedMapping.size()) {
                throw error(ErrorCode.UNNAMED_ARGUMENT_NOT_ALLOWED,
                    unnamedMapping.isEmpty()
                        ? "the component type `" + type + "` admits no unnamed initializer argument"
                        : "the component type `" + type + "` admits "
                            + unnamedMapping.size() + " unnamed initializer argument(s)",
                    position(init), operationCounter + 1);
            }
            Value value = shortValue(init.shortValue());
            String language = value instanceof Value.Str s ? s.language() : null;
            PropertyRef property = new PropertyRef(
                unnamedMapping.get(unnamed), language, false, position(init));
            Value stripped = value instanceof Value.Str s
                ? new Value.Str(s.text(), null, s.position())
                : value;
            initializers.add(new Initializer.Assignment(property, stripped, position(init)));
            unnamed++;
        }
        return new Constructor(type, initializers, position(ctx));
    }

    private Predicate predicate(
        LiftPatchShortParser.PredicateContext ctx, ShortLinePreprocessor.Recognized line
    ) {
        SourcePosition where = position(ctx);
        if (ctx.hasPredicate() != null) {
            return hasPredicate(ctx.hasPredicate(), line);
        }
        if (ctx.EXISTS() != null) {
            return new Predicate.Existence(propertyRef(ctx.propertyRef()), false, where);
        }
        if (ctx.ABSENT() != null) {
            return new Predicate.Existence(propertyRef(ctx.propertyRef()), true, where);
        }
        PropertyRef property = propertyRef(ctx.propertyRef());
        Operator operator = Operator.parse(ctx.comparison().getText());
        Value value = shortValue(ctx.shortValue());
        if (operator == Operator.EQ) {
            switch (property.name()) {
                case "id" -> {
                    if (value instanceof Value.Str s && !property.hasQualifier()) {
                        return new Predicate.Id(s.text(), where);
                    }
                }
                case "hn" -> {
                    if (value instanceof Value.Num n && !property.hasQualifier()) {
                        return new Predicate.Hn(n.value(), where);
                    }
                }
                case "has-gloss" -> {
                    if (value instanceof Value.Str s) {
                        return new Predicate.HasGloss(property.language(), s.text(), where);
                    }
                }
                case "index" -> {
                    if (value instanceof Value.Num n && !property.hasQualifier()) {
                        return new Predicate.Index(n.value(), where);
                    }
                }
                default -> {
                    // An ordinary property predicate.
                }
            }
        }
        return new Predicate.Comparison(property, operator, value, where);
    }

    private Predicate.Has hasPredicate(
        LiftPatchShortParser.HasPredicateContext ctx, ShortLinePreprocessor.Recognized line
    ) {
        // A step inside a `has` predicate is a filtering step at any depth, and the
        // depth from the root is not written down there, so it may not be abbreviated.
        return new Predicate.Has(buildStep(ctx.pathStep(), -1, null, line).step, position(ctx));
    }

    private PropertyRef propertyRef(LiftPatchShortParser.PropertyRefContext ctx) {
        String code = ctx.propertyCode().getText();
        String name = ShortCodes.propertyName(code).orElse(code);
        if (ctx.ATSIGN() == null) {
            return new PropertyRef(name, null, false, position(ctx));
        }
        if (ctx.STAR() != null) {
            return new PropertyRef(name, null, true, position(ctx));
        }
        return new PropertyRef(name, ctx.lang().getText(), false, position(ctx));
    }

    private Value shortValue(LiftPatchShortParser.ShortValueContext ctx) {
        SourcePosition where = position(ctx);
        if (ctx.string() != null) {
            String language = ctx.lang() == null ? null : ctx.lang().getText();
            return new Value.Str(string(ctx.string()), language, where);
        }
        if (ctx.INT() != null) {
            return new Value.Num(Long.parseLong(ctx.INT().getText()), where);
        }
        if (ctx.multitextLiteral() != null) {
            List<LangText> entries = new ArrayList<>();
            for (LiftPatchShortParser.LangTextContext lt : ctx.multitextLiteral().langText()) {
                entries.add(new LangText(lt.lang().getText(), string(lt.string()), position(lt)));
            }
            return new Value.Multitext(entries, where);
        }
        if (ctx.labelRef() != null) {
            return new Value.LabelRef(anyName(ctx.labelRef().anyName()), where);
        }
        // A path used as a reference value designates its own last step.
        ParsedPath path = pathOf(ctx.path(), null, 1, null);
        Chain chain = path.toChain();
        if (chain.length() == 1 && chain.head() instanceof Step.Label l) {
            return new Value.LabelRef(l.name(), where);
        }
        return new Value.ChainRef(chain);
    }

    // ===================================================================
    // ANTLR plumbing
    // ===================================================================

    private LiftPatchShortParser parserFor(String text, int line, int column) {
        LiftPatchShortLexer lexer = new LiftPatchShortLexer(CharStreams.fromString(text));
        CollectingErrorListener listener = new CollectingErrorListener(line - 1, column - 1);
        lexer.removeErrorListeners();
        lexer.addErrorListener(listener);
        LiftPatchShortParser parser = new LiftPatchShortParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(listener);
        parser.setErrorHandler(new org.antlr.v4.runtime.DefaultErrorStrategy());
        listeners.put(parser, listener);
        return parser;
    }

    private final Map<LiftPatchShortParser, CollectingErrorListener> listeners =
        new java.util.IdentityHashMap<>();

    private void checkErrors(LiftPatchShortParser parser) {
        CollectingErrorListener listener = listeners.remove(parser);
        if (listener != null && listener.hasErrors()) {
            List<LiftPatchError> errors = new ArrayList<>();
            for (LiftPatchError e : listener.errors()) {
                errors.add(new LiftPatchError(
                    e.code(), e.message(), e.position(), operationCounter + 1));
            }
            throw new LiftPatchException(errors);
        }
    }

    // ===================================================================
    // Small helpers
    // ===================================================================

    private static SourcePosition position(ParserRuleContext ctx) {
        Token t = ctx.getStart();
        return new SourcePosition(t.getLine(), t.getCharPositionInLine() + 1);
    }

    private static SourcePosition position(
        ShortLinePreprocessor.Recognized line, TopLevelSplitter.Token token
    ) {
        return new SourcePosition(line.lineNumber(), token.column());
    }

    private static String anyName(LiftPatchShortParser.AnyNameContext ctx) {
        return ctx.getText();
    }

    private static String string(LiftPatchShortParser.StringContext ctx) {
        TerminalNode node = ctx.STRING_DQ() != null ? ctx.STRING_DQ() : ctx.STRING_SQ();
        Token t = node.getSymbol();
        return Literals.unquote(node.getText(),
            new SourcePosition(t.getLine(), t.getCharPositionInLine() + 1));
    }

    private static Step withMultiplicity(Step step, Multiplicity m) {
        return switch (step) {
            case Step.Component c -> c.withMultiplicity(m);
            case Step.Label l -> new Step.Label(l.name(), m, l.position());
        };
    }

    private LiftPatchException error(
        ErrorCode code, String message, SourcePosition position, int operationIndex
    ) {
        return new LiftPatchException(
            new LiftPatchError(code, message, position, operationIndex));
    }
}
