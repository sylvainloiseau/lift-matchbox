package fr.cnrs.lacito.liftpatchbox.engine;

import fr.cnrs.lacito.liftpatchbox.error.LiftPatchError;
import fr.cnrs.lacito.liftpatchbox.error.SourcePosition;
import java.util.List;

/**
 * One operation of a plan document.
 *
 * <p>An operation is anything that resolves a component or changes the
 * dictionary, which by Part 2, section 12.4.1 means every command, every block
 * header, and every {@code language-create} directive. A {@code with} header and
 * a {@code language-default} directive are not operations and take no index: they
 * bind names for the rest of a scope and change nothing.</p>
 *
 * <p>A command marked {@code each} or {@code all} carries {@link #applications()}
 * instead of {@link #effects()}, one element per affected component, in execution
 * order.</p>
 *
 * @param index        the 1-based index, flat across the whole script in source order
 * @param position     where the command was written
 * @param command      the source text of the command
 * @param verb         the verb, or {@code "language-create"} for the directive
 * @param multiplicity the multiplicity keyword written, or {@code null}
 * @param target       the component the operation resolved to, or {@code null}
 * @param parent       the parent it resolved under, or {@code null}
 * @param effects      the effects of an operation applying to one component
 * @param applications one element per affected component, for a marked command
 * @param error        the error the operation raised, or {@code null}
 */
public record Operation(
    int index,
    SourcePosition position,
    String command,
    String verb,
    String multiplicity,
    TargetInfo target,
    TargetInfo parent,
    List<Effect> effects,
    List<Application> applications,
    LiftPatchError error
) {

    /**
     * Canonical constructor, taking unmodifiable copies of the two lists.
     *
     * @param index        the 1-based operation index
     * @param position     where the command was written
     * @param command      the source text
     * @param verb         the verb
     * @param multiplicity the multiplicity keyword, or {@code null}
     * @param target       the resolved component, or {@code null}
     * @param parent       the resolved parent, or {@code null}
     * @param effects      the effects
     * @param applications the per-component applications of a marked command
     * @param error        the error raised, or {@code null}
     */
    public Operation {
        effects = effects == null ? null : List.copyOf(effects);
        applications = applications == null ? null : List.copyOf(applications);
    }

    /**
     * One affected component of a command marked {@code each}, {@code all} or {@code *}.
     *
     * @param target  the component
     * @param effects what the command did to it
     */
    public record Application(TargetInfo target, List<Effect> effects) {

        /**
         * Canonical constructor, taking an unmodifiable copy of the effects.
         *
         * @param target  the component
         * @param effects what the command did to it
         */
        public Application {
            effects = List.copyOf(effects);
        }
    }

    /**
     * Every effect of this operation, flattened across its applications, which is
     * what Appendix D.1 compares.
     *
     * @return the effects, in order
     */
    public List<Effect> allEffects() {
        if (applications == null) {
            return effects == null ? List.of() : effects;
        }
        return applications.stream().flatMap(a -> a.effects().stream()).toList();
    }
}
