package fr.cnrs.lacito.liftdsl.api;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.*;
import fr.cnrs.lacito.liftdsl.model.*;
import fr.cnrs.lacito.liftdsl.validation.ValidationError;
import java.util.*;

/**
 * Resolves selectors against a lift-api dictionary before mutation.
 * <p>
 * Resolution is strict: no match and multiple matches are both errors, preventing an operation from silently targeting
 * the wrong component.
 * </p>
 */
public final class LiftDictionaryValidator {
    private final LiftDictionary dictionary;

    /**
     * Creates a validator for a dictionary.
     *
     * @param dictionary
     *            dictionary to inspect
     *
     * @throws NullPointerException
     *             if {@code dictionary} is null
     */
    public LiftDictionaryValidator(LiftDictionary dictionary) {
        this.dictionary = Objects.requireNonNull(dictionary);
    }

    /**
     * Resolves a component reference to exactly one dictionary node.
     *
     * @param ref
     *            component reference and ancestor constraints
     *
     * @return a singleton list containing the resolved component
     *
     * @throws NullPointerException
     *             if {@code ref} is null
     * @throws DictionaryValidationException
     *             if no or multiple components match
     */
    public List<AbstractLiftRoot> resolve(ComponentRef ref) {
        List<AbstractLiftRoot> candidates = resolveCandidates(ref);
        if (candidates.isEmpty())
            throw new DictionaryValidationException(ValidationError.Code.NOT_FOUND,
                    "No " + ref.type() + " matches selector");
        if (candidates.size() > 1)
            throw new DictionaryValidationException(ValidationError.Code.AMBIGUOUS_REFERENCE,
                    "Selector matches multiple " + ref.type() + " components");
        return candidates;
    }

    private List<AbstractLiftRoot> resolveCandidates(ComponentRef ref) {
        List<AbstractLiftRoot> roots = ref.parent().isPresent() ? resolveCandidates(ref.parent().get())
                : rootsFor(ref.type());
        List<AbstractLiftRoot> candidates = new ArrayList<>();
        if (ref.parent().isEmpty() && "entry".equals(ref.type())) {
            for (LiftEntry e : dictionary.getLiftDictionaryRegistry().getEntries())
                if (matches(e, ref.selector()))
                    candidates.add(e);
            return candidates;
        }
        for (AbstractLiftRoot root : roots) {
            for (AbstractLiftRoot child : children(root, ref.type()))
                if (matches(child, ref.selector()))
                    candidates.add(child);
        }
        return candidates;
    }

    private List<AbstractLiftRoot> rootsFor(String type) {
        if ("entry".equals(type)) {
            List<AbstractLiftRoot> r = new ArrayList<>();
            r.addAll(dictionary.getLiftDictionaryRegistry().getEntries());
            return r;
        }
        return List.of();
    }

    private boolean matches(AbstractLiftRoot node, Selector s) {
        if (s.index().isPresent())
            return true;
        for (Map.Entry<String, String> e : s.predicates().entrySet()) {
            String k = e.getKey(), v = e.getValue();
            String lang = null;
            int at = k.indexOf('@');
            if (at > 0) {
                lang = k.substring(at + 1);
                k = k.substring(0, at);
            }
            String actual = null;
            if (node instanceof LiftEntry && k.equals("form"))
                actual = text(((LiftEntry) node).getForms(), lang, "tww");
            else if (node instanceof LiftSense && k.equals("gloss"))
                actual = text(((LiftSense) node).getGlosses(), lang, "en");
            else if (node instanceof LiftSense && k.equals("definition"))
                actual = text(((LiftSense) node).getDefinition(), lang, "en");
            else if (node instanceof LiftExample && k.equals("text"))
                actual = text(((LiftExample) node).getExample(), lang, "tww");
            else if (node instanceof AbstractIdentifiable && k.equalsIgnoreCase("id"))
                actual = ((AbstractIdentifiable) node).getId().orElse(null);
            if (!Objects.equals(actual, v))
                return false;
        }
        return true;
    }

    private String text(MultiText t, String lang, String defaultLanguage) {
        String effectiveLanguage = lang == null ? defaultLanguage : lang;
        if (!t.containsLang(effectiveLanguage))
            return null;
        return t.getForm(effectiveLanguage).map(Form::toPlainText).orElse(null);
    }

    private List<AbstractLiftRoot> children(AbstractLiftRoot p, String type) {
        List<AbstractLiftRoot> out = new ArrayList<>();
        Object value = null;
        String method = "get" + Character.toUpperCase(type.charAt(0)) + type.substring(1) + "s";
        try {
            value = p.getClass().getMethod(method).invoke(p);
        } catch (ReflectiveOperationException ignored) {
        }
        if (value instanceof Iterable<?>)
            for (Object x : (Iterable<?>) value)
                if (x instanceof AbstractLiftRoot)
                    out.add((AbstractLiftRoot) x);
        if ("sense".equals(type) && p instanceof LiftSense) {
            out.addAll(((LiftSense) p).getSenses());
        }
        return out;
    }
}
