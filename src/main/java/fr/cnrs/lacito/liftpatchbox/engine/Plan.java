package fr.cnrs.lacito.liftpatchbox.engine;

import fr.cnrs.lacito.liftpatchbox.ast.Syntax;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchError;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchWarning;
import java.util.List;

/**
 * The plan document of Part 2, section 12.4.1: what a script did, or would do.
 *
 * <p>The document is the normative artifact of plan mode. Two conforming
 * implementations, given the same script and the same dictionary, must emit plan
 * documents that are equal after normalization, and Appendix D.1 states exactly
 * which fields a comparison looks at.</p>
 *
 * <p>The same object is produced whether the script was planned or applied, since
 * both go through the same executor; only the last step differs, a rollback or a
 * commit.</p>
 *
 * @param source             the name of the script's source
 * @param syntax             the surface syntax it was parsed with
 * @param commandCount       how many commands the script contains
 * @param metamodelVersion   the version of the metamodel it was validated against
 * @param metamodelId        the identifier of an extended metamodel, or {@code null}
 * @param status             {@code "ok"} or {@code "error"}
 * @param staticErrors       every static error of the script; non-empty only on error
 * @param warnings           the warnings of Appendix B.4
 * @param operations         the operations, in source order
 * @param danglingReferences the references the script would leave dangling
 * @param summary            what the script did, counted
 */
public record Plan(
    String source,
    Syntax syntax,
    int commandCount,
    String metamodelVersion,
    String metamodelId,
    String status,
    List<LiftPatchError> staticErrors,
    List<LiftPatchWarning> warnings,
    List<Operation> operations,
    List<DanglingReference> danglingReferences,
    Summary summary
) {

    /**
     * Canonical constructor, taking unmodifiable copies of the lists.
     *
     * @param source             the name of the script's source
     * @param syntax             the surface syntax
     * @param commandCount       how many commands the script contains
     * @param metamodelVersion   the metamodel version
     * @param metamodelId        the extended metamodel identifier, or {@code null}
     * @param status             {@code "ok"} or {@code "error"}
     * @param staticErrors       the static errors
     * @param warnings           the warnings
     * @param operations         the operations
     * @param danglingReferences the dangling references
     * @param summary            the counts
     */
    public Plan {
        staticErrors = List.copyOf(staticErrors);
        warnings = List.copyOf(warnings);
        operations = List.copyOf(operations);
        danglingReferences = List.copyOf(danglingReferences);
    }

    /**
     * A plan that reports static errors alone, produced when validation fails and
     * no operation is resolved.
     *
     * @param source       the name of the script's source
     * @param syntax       the surface syntax
     * @param metamodel    the metamodel version
     * @param errors       the static errors
     * @param warnings     the warnings
     * @return the plan
     */
    public static Plan ofStaticErrors(
        String source, Syntax syntax, String metamodel,
        List<LiftPatchError> errors, List<LiftPatchWarning> warnings
    ) {
        return new Plan(source, syntax, 0, metamodel, null, "error",
            errors, warnings, List.of(), List.of(), new Summary());
    }

    /**
     * Whether the script would apply cleanly.
     *
     * @return {@code true} when the status is {@code "ok"}
     */
    public boolean isOk() {
        return "ok".equals(status);
    }

    /**
     * The error the plan reports, which is the first failing operation's.
     *
     * @return the error, or {@code null} when the plan is ok
     */
    public LiftPatchError error() {
        if (!staticErrors.isEmpty()) {
            return staticErrors.get(0);
        }
        return operations.stream()
            .map(Operation::error)
            .filter(java.util.Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    /**
     * Every effect of the script, flattened in operation order, which is the list
     * Appendix D.1 compares.
     *
     * @return the effects
     */
    public List<Effect> allEffects() {
        return operations.stream().flatMap(o -> o.allEffects().stream()).toList();
    }

    /**
     * The plan document as JSON, the artifact Part 2, section 12.4.1 defines.
     *
     * @return the document, pretty-printed
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"liftpatchPlan\": \"1.0\",\n");
        sb.append("  \"script\": { \"source\": ").append(quote(source))
            .append(", \"syntax\": ").append(quote(syntax.pragmaName()))
            .append(", \"commands\": ").append(commandCount).append(" },\n");
        sb.append("  \"metamodel\": { \"version\": ").append(quote(metamodelVersion))
            .append(", \"metamodelId\": ").append(quote(metamodelId)).append(" },\n");
        sb.append("  \"status\": ").append(quote(status)).append(",\n");

        sb.append("  \"staticErrors\": [");
        for (int i = 0; i < staticErrors.size(); i++) {
            LiftPatchError e = staticErrors.get(i);
            sb.append(i > 0 ? ",\n    " : "\n    ").append("{ \"code\": ").append(quote(e.code().name()))
                .append(", \"message\": ").append(quote(e.message()))
                .append(", \"position\": ").append(position(e.position())).append(" }");
        }
        sb.append(staticErrors.isEmpty() ? "],\n" : "\n  ],\n");

        sb.append("  \"warnings\": [");
        for (int i = 0; i < warnings.size(); i++) {
            LiftPatchWarning w = warnings.get(i);
            sb.append(i > 0 ? ",\n    " : "\n    ").append("{ \"code\": ").append(quote(w.code().name()))
                .append(", \"position\": ").append(position(w.position()))
                .append(", \"operationIndex\": ")
                .append(w.operationIndex() == 0 ? "null" : w.operationIndex()).append(" }");
        }
        sb.append(warnings.isEmpty() ? "],\n" : "\n  ],\n");

        sb.append("  \"operations\": [");
        for (int i = 0; i < operations.size(); i++) {
            sb.append(i > 0 ? ",\n" : "\n").append(operation(operations.get(i)));
        }
        sb.append(operations.isEmpty() ? "],\n" : "\n  ],\n");

        sb.append("  \"danglingReferences\": [");
        for (int i = 0; i < danglingReferences.size(); i++) {
            DanglingReference d = danglingReferences.get(i);
            sb.append(i > 0 ? ",\n    " : "\n    ")
                .append("{ \"component\": { \"componentType\": ").append(quote(d.componentType()))
                .append(", \"id\": ").append(quote(d.componentId()))
                .append(", \"path\": ").append(quote(d.path())).append(" }")
                .append(", \"property\": ").append(quote(d.property()))
                .append(", \"value\": ").append(quote(d.value()))
                .append(", \"cause\": { \"operationIndex\": ").append(d.operationIndex())
                .append(", \"verb\": ").append(quote(d.verb())).append(" } }");
        }
        sb.append(danglingReferences.isEmpty() ? "],\n" : "\n  ],\n");

        sb.append("  \"summary\": {")
            .append(" \"componentsCreated\": ").append(summary.componentsCreated())
            .append(", \"componentsDeleted\": ").append(summary.componentsDeleted())
            .append(", \"componentsMoved\": ").append(summary.componentsMoved())
            .append(", \"propertiesSet\": ").append(summary.propertiesSet())
            .append(", \"propertiesRemoved\": ").append(summary.propertiesRemoved())
            .append(", \"languagesCreated\": ").append(summary.languagesCreated())
            .append(" }\n}");
        return sb.toString();
    }

    private static String operation(Operation o) {
        StringBuilder sb = new StringBuilder("    {");
        sb.append(" \"index\": ").append(o.index());
        sb.append(", \"position\": ").append(position(o.position()));
        sb.append(", \"command\": ").append(quote(o.command()));
        sb.append(", \"verb\": ").append(quote(o.verb()));
        if (o.multiplicity() != null) {
            sb.append(", \"multiplicity\": ").append(quote(o.multiplicity()));
        }
        sb.append(", \"target\": ").append(target(o.target()));
        sb.append(", \"parent\": ").append(target(o.parent()));
        if (o.applications() != null) {
            sb.append(", \"applications\": [");
            for (int i = 0; i < o.applications().size(); i++) {
                Operation.Application a = o.applications().get(i);
                sb.append(i > 0 ? ", " : "").append("{ \"target\": ").append(target(a.target()))
                    .append(", \"effects\": ").append(effects(a.effects())).append(" }");
            }
            sb.append("]");
        } else {
            sb.append(", \"effects\": ").append(effects(o.effects()));
        }
        sb.append(", \"error\": ");
        if (o.error() == null) {
            sb.append("null");
        } else {
            sb.append("{ \"code\": ").append(quote(o.error().code().name()))
                .append(", \"message\": ").append(quote(o.error().message()))
                .append(", \"position\": ").append(position(o.error().position())).append(" }");
        }
        return sb.append(" }").toString();
    }

    private static String effects(List<Effect> effects) {
        if (effects == null || effects.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < effects.size(); i++) {
            Effect e = effects.get(i);
            sb.append(i > 0 ? ", " : "").append("{ \"kind\": ").append(quote(e.kind().name()));
            if (e.componentType() != null) {
                sb.append(", \"componentType\": ").append(quote(e.componentType()));
            }
            if (e.property() != null) {
                sb.append(", \"property\": ").append(quote(e.property()));
            }
            if (e.languageKind() != null) {
                sb.append(", \"languageKind\": ").append(quote(e.languageKind()));
            }
            if (e.kind() != Effect.Kind.componentCreated
                && e.kind() != Effect.Kind.componentDeleted
                && e.kind() != Effect.Kind.componentMoved) {
                sb.append(", \"language\": ").append(quote(e.language()));
                sb.append(", \"oldValue\": ").append(quote(e.oldValue()));
                sb.append(", \"newValue\": ").append(quote(e.newValue()));
            }
            if (e.kind() == Effect.Kind.componentCreated
                || e.kind() == Effect.Kind.componentDeleted) {
                sb.append(", \"position\": ")
                    .append(e.position() == null ? "null" : e.position());
            }
            if (e.kind() == Effect.Kind.componentMoved) {
                sb.append(", \"fromPosition\": ")
                    .append(e.fromPosition() == null ? "null" : e.fromPosition());
                sb.append(", \"toPosition\": ")
                    .append(e.toPosition() == null ? "null" : e.toPosition());
                sb.append(", \"fromParent\": ").append(quote(e.fromParent()));
                sb.append(", \"toParent\": ").append(quote(e.toParent()));
            }
            sb.append(" }");
        }
        return sb.append("]").toString();
    }

    private static String target(TargetInfo info) {
        if (info == null) {
            return "null";
        }
        return "{ \"componentType\": " + quote(info.componentType())
            + ", \"id\": " + quote(info.id())
            + ", \"path\": " + quote(info.path())
            + (info.label() == null ? "" : ", \"label\": " + quote("$" + info.label()))
            + " }";
    }

    private static String position(fr.cnrs.lacito.liftpatchbox.error.SourcePosition p) {
        if (p == null || !p.isKnown()) {
            return "null";
        }
        return "{ \"line\": " + p.line() + ", \"column\": " + p.column() + " }";
    }

    private static String quote(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
