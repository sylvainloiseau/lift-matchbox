package fr.cnrs.lacito.liftdsl.parser;

import fr.cnrs.lacito.liftdsl.model.*;
import fr.cnrs.lacito.liftdsl.validation.ValidationError;
import java.util.*;
import java.util.regex.*;

/**
 * Public parser facade. ANTLR grammars are shipped and generated as part of the build; this facade additionally
 * performs the AST construction so callers do not depend on generated parser classes.
 */
public final class DslParser {
    private static final Pattern COMPONENT = Pattern.compile("([A-Za-z][A-Za-z0-9_-]*)\\s*(\\[[^\\]]*\\])?");
    private static final Map<Character, String> COMPONENTS = Map.ofEntries(Map.entry('e', "entry"),
            Map.entry('s', "sense"), Map.entry('x', "example"), Map.entry('y', "etymology"), Map.entry('v', "variant"),
            Map.entry('r', "relation"), Map.entry('i', "illustration"), Map.entry('m', "media"),
            Map.entry('p', "pronunciation"), Map.entry('l', "reversal"), Map.entry('t', "trait"),
            Map.entry('a', "annotation"), Map.entry('n', "note"), Map.entry('f', "field"),
            Map.entry('o', "translation"));
    private static final Map<Character, String> PROPERTIES = Map.ofEntries(Map.entry('f', "form"),
            Map.entry('m', "morpheme"), Map.entry('d', "definition"), Map.entry('g', "gloss"),
            Map.entry('c', "category"), Map.entry('t', "text"), Map.entry('s', "source"), Map.entry('a', "target"),
            Map.entry('u', "url"), Map.entry('l', "label"), Map.entry('r', "transcription"), Map.entry('y', "type"),
            Map.entry('v', "value"), Map.entry('o', "comment"), Map.entry('w', "when"), Map.entry('h', "who"));

    /**
     * Parses reference-syntax statements separated at the top level.
     *
     * @param source
     *            reference Lift-DSL source text
     *
     * @return immutable parsed program
     *
     * @throws ValidationError
     *             if the source is null or a statement is invalid
     */
    public Program parse(String source) {
        if (source == null)
            throw new ValidationError(ValidationError.Code.SYNTAX_ERROR, "DSL source is null");
        List<Command> commands = new ArrayList<>();
        for (String statement : splitStatements(source))
            if (!statement.trim().isEmpty())
                commands.add(parseReferenceStatement(statement.trim()));
        return new Program(commands);
    }

    /**
     * Parses one concise command per physical line.
     *
     * @param source
     *            concise Lift-DSL source text
     *
     * @return immutable parsed program
     *
     * @throws ValidationError
     *             if the source is null or a line is invalid
     */
    public Program parseConcise(String source) {
        if (source == null)
            throw new ValidationError(ValidationError.Code.SYNTAX_ERROR, "DSL source is null");
        List<Command> all = new ArrayList<>();
        for (String line : source.split("\\R")) {
            String s = line.trim();
            if (!s.isEmpty() && !s.startsWith("#"))
                all.add(parseConciseLine(s));
        }
        return new Program(all);
    }

    private List<String> splitStatements(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0, start = 0;
        boolean quote = false;
        char q = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote) {
                if (c == '\\')
                    i++;
                else if (c == q)
                    quote = false;
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = true;
                q = c;
            } else if (c == '(' || c == '{' || c == '[')
                depth++;
            else if (c == ')' || c == '}' || c == ']')
                depth--;
            else if ((c == ';' || c == '\n') && depth == 0) {
                out.add(s.substring(start, i));
                start = i + 1;
            }
        }
        out.add(s.substring(start));
        return out;
    }

    private Command parseReferenceStatement(String s) {
        Matcher lang = Pattern.compile("language-default\\s+(object|meta)\\s*=\\s*(.+)", Pattern.CASE_INSENSITIVE)
                .matcher(s);
        if (lang.matches())
            return Command.builder(Command.Kind.LANGUAGE_DEFAULT).languageSet(lang.group(1))
                    .language(unquote(lang.group(2).trim())).build();
        Matcher m = Pattern.compile("(create|upsert|ensure)\\s+(\\w+)\\s*(\\([^)]*\\))?\\s*(.*)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(s);
        if (m.matches()) {
            Command.Builder b = Command.builder(kind(m.group(1))).component(m.group(2));
            for (PropertyValue p : values(m.group(3)))
                b.add(p);
            if (!m.group(4).trim().isEmpty())
                b.target(parseParent(m.group(4).trim()));
            return b.build();
        }
        m = Pattern.compile("delete\\s+(\\w+)\\s*(\\[[^]]*\\])\\s*(.*)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                .matcher(s);
        if (m.matches())
            return Command
                    .builder(Command.Kind.DELETE).component(m.group(1)).target(new ComponentRef(m.group(1),
                            selector(m.group(2)), m.group(3).trim().isEmpty() ? null : parseParent(m.group(3).trim())))
                    .build();
        m = Pattern.compile("move\\s+(\\w+)\\s*(\\[[^]]*\\])\\s*(.*?)(?:\\s+at\\s+(beginning|end|index\\s+\\d+))?$",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(s);
        if (m.matches()) {
            Command.Builder b = Command.builder(Command.Kind.MOVE).component(m.group(1))
                    .target(new ComponentRef(m.group(1), selector(m.group(2)), null));
            if (m.group(4) != null)
                b.position(m.group(4));
            return b.build();
        }
        m = Pattern.compile(
                "(set|update)\\s+([\\w-]+)(?:@([\\w-]+))?(?:\\^([\\w-]+))?\\s*=\\s*(.+?)\\s+(?:on|under)\\s+(.+)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(s);
        if (m.matches())
            return Command.builder(kind(m.group(1)))
                    .property(new PropertyValue(m.group(2), m.group(3), m.group(4), unquote(m.group(5).trim())))
                    .target(parseParent("on " + m.group(6).trim())).build();
        m = Pattern.compile("clear\\s+([\\w-]+)(?:@([\\w-]+))?(?:\\^([\\w-]+))?\\s+(?:on|under)\\s+(.+)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(s);
        if (m.matches())
            return Command.builder(Command.Kind.CLEAR)
                    .property(new PropertyValue(m.group(1), m.group(2), m.group(3), null))
                    .target(parseParent("on " + m.group(4).trim())).build();
        throw new ValidationError(ValidationError.Code.SYNTAX_ERROR, "Cannot parse statement: " + s);
    }

    private Command parseConciseLine(String s) {
        char verb = s.charAt(0);
        if (s.length() < 3 || !Character.isWhitespace(s.charAt(1)))
            throw new ValidationError(ValidationError.Code.SYNTAX_ERROR,
                    "Concise command must start with a command letter and whitespace");
        String rest = s.substring(2).trim();
        int space = rest.indexOf(' ');
        String path = space < 0 ? rest : rest.substring(0, space);
        String tail = space < 0 ? "" : rest.substring(space + 1).trim();
        List<String> steps = splitPath(path);
        if (steps.isEmpty())
            throw new ValidationError(ValidationError.Code.SYNTAX_ERROR, "Missing concise path");
        ComponentRef chain = null;
        for (String step : steps) {
            String type = step.substring(0, 1);
            type = COMPONENTS.getOrDefault(type.charAt(0), type);
            Selector sel = new Selector();
            Matcher sm = Pattern.compile("[^\\[]+\\[([^]]*)\\]").matcher(step);
            if (sm.find())
                sel = parseConciseSelector(sm.group(1));
            else if (step.startsWith("\""))
                sel.predicate(type.equals("entry") ? "form" : type.equals("sense") ? "gloss" : "text", null,
                        unquote(step));
            ComponentRef n = new ComponentRef(type, sel, chain);
            chain = n;
        }
        if (verb == 'd')
            return Command.builder(Command.Kind.DELETE).component(chain.type()).target(chain).build();
        if (verb == 'm')
            return Command.builder(Command.Kind.MOVE).component(chain.type()).target(chain).build();
        Matcher im = Pattern.compile("([a-z])\\s*\\((.*)\\)").matcher(tail);
        if (im.find()) {
            String type = COMPONENTS.getOrDefault(im.group(1).charAt(0), im.group(1));
            Command.Builder b = Command.builder(kind(verb)).component(type);
            for (PropertyValue p : conciseValues(im.group(2), type))
                b.add(p);
            b.target(chain);
            return b.build();
        }
        Matcher pm = Pattern.compile("\\((\\w+)(?:@([\\w-]+))?(?:\\^([\\w-]+))?(?:\\s*=\\s*(.+))?\\)").matcher(tail);
        if (pm.find()) {
            String prop = PROPERTIES.getOrDefault(pm.group(1).charAt(0), pm.group(1));
            return Command.builder(kind(verb)).property(new PropertyValue(prop, pm.group(2), pm.group(3),
                    pm.group(4) == null ? null : unquote(pm.group(4).trim()))).target(chain).build();
        }
        throw new ValidationError(ValidationError.Code.SYNTAX_ERROR, "Cannot parse concise command: " + s);
    }

    private Selector parseConciseSelector(String x) {
        Selector s = new Selector();
        if (x.trim().matches("\\d+"))
            return s.index(Integer.valueOf(x.trim()));
        for (String p : splitComma(x)) {
            String[] kv = p.split("=", 2);
            if (kv.length == 2) {
                String n = kv[0].trim();
                String lang = null;
                int at = n.indexOf('@');
                if (at > 0) {
                    lang = n.substring(at + 1);
                    n = n.substring(0, at);
                }
                s.predicate(n, lang, unquote(kv[1].trim()));
            }
        }
        return s;
    }

    private List<String> splitPath(String p) {
        List<String> out = new ArrayList<>();
        for (String part : p.split("/"))
            if (!part.isEmpty())
                out.add(part);
        return out;
    }

    private Command.Kind kind(String x) {
        return kind(x.charAt(0));
    }

    private Command.Kind kind(char c) {
        switch (c) {
            case 'c':
            case 'C':
                return Command.Kind.CREATE;
            case 'p':
            case 'P':
                return Command.Kind.UPSERT;
            case 'e':
            case 'E':
                return Command.Kind.ENSURE;
            case 'd':
            case 'D':
                return Command.Kind.DELETE;
            case 'm':
            case 'M':
                return Command.Kind.MOVE;
            case 's':
            case 'S':
                return Command.Kind.SET;
            case 'u':
            case 'U':
                return Command.Kind.UPDATE;
            case 'l':
            case 'L':
                return Command.Kind.CLEAR;
            default:
                throw new ValidationError(ValidationError.Code.SYNTAX_ERROR, "Unknown command " + c);
        }
    }

    private List<PropertyValue> values(String x) {
        if (x == null)
            return List.of();
        String body = x.substring(1, x.length() - 1);
        List<PropertyValue> out = new ArrayList<>();
        for (String p : splitComma(body)) {
            String[] kv = p.split("=", 2);
            if (kv.length == 2) {
                String n = kv[0].trim();
                String lang = null;
                int at = n.indexOf('@');
                if (at > 0) {
                    lang = n.substring(at + 1);
                    n = n.substring(0, at);
                }
                out.add(new PropertyValue(n, lang, null, unquote(kv[1].trim())));
            }
        }
        return out;
    }

    private List<PropertyValue> conciseValues(String x, String type) {
        List<PropertyValue> out = new ArrayList<>();
        for (String p : splitComma(x)) {
            if (p.trim().startsWith("\""))
                out.add(new PropertyValue(type.equals("entry") ? "form" : type.equals("sense") ? "gloss" : "text", null,
                        null, unquote(p.trim())));
            else {
                String[] kv = p.split("=", 2);
                if (kv.length == 2)
                    out.add(new PropertyValue(kv[0].trim(), null, null, unquote(kv[1].trim())));
            }
        }
        return out;
    }

    private ComponentRef parseParent(String s) {
        Matcher m = Pattern.compile("(?:under|of|within|on)\\s+(\\w+)\\s*(\\[[^]]*\\])?(.*)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(s);
        if (!m.matches())
            throw new ValidationError(ValidationError.Code.SYNTAX_ERROR, "Invalid parent clause: " + s);
        return new ComponentRef(m.group(1), selector(m.group(2)),
                m.group(3).trim().isEmpty() ? null : parseParent(m.group(3).trim()));
    }

    private Selector selector(String x) {
        Selector s = new Selector();
        if (x == null)
            return s;
        for (String p : splitComma(x.substring(1, x.length() - 1))) {
            String[] kv = p.split("=", 2);
            if (kv.length == 2) {
                String n = kv[0].trim();
                String lang = null;
                int at = n.indexOf('@');
                if (at > 0) {
                    lang = n.substring(at + 1);
                    n = n.substring(0, at);
                }
                s.predicate(n, lang, unquote(kv[1].trim()));
            }
        }
        return s;
    }

    private List<String> splitComma(String s) {
        List<String> o = new ArrayList<>();
        int d = 0;
        boolean q = false;
        char z = 0, prev = 0;
        int st = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (q) {
                if (c == z && prev != '\\')
                    q = false;
            } else if (c == '\'' || c == '"') {
                q = true;
                z = c;
            } else if (c == '(' || c == '[')
                d++;
            else if (c == ')' || c == ']')
                d--;
            else if (c == ',' && d == 0) {
                o.add(s.substring(st, i));
                st = i + 1;
            }
            prev = c;
        }
        o.add(s.substring(st));
        return o;
    }

    private String unquote(String s) {
        if (s.length() >= 2 && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))))
            s = s.substring(1, s.length() - 1);
        return s.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\'", "'").replace("\\\\",
                "\\");
    }
}
