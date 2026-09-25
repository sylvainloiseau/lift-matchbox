package fr.cnrs.lacito.liftpatchbox.model;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.LiftDictionaryLanguagesManager;
import fr.cnrs.lacito.liftapi.model.AbstractExtensibleWithField;
import fr.cnrs.lacito.liftapi.model.AbstractExtensibleWithoutField;
import fr.cnrs.lacito.liftapi.model.AbstractIdentifiable;
import fr.cnrs.lacito.liftapi.model.AbstractLiftRoot;
import fr.cnrs.lacito.liftapi.model.AbstractNotable;
import fr.cnrs.lacito.liftapi.model.Feature;
import fr.cnrs.lacito.liftapi.model.FeatureSet;
import fr.cnrs.lacito.liftapi.model.Form;
import fr.cnrs.lacito.liftapi.model.HasRefId;
import fr.cnrs.lacito.liftapi.model.GrammaticalInfo;
import fr.cnrs.lacito.liftapi.model.LiftAnnotation;
import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftapi.model.LiftEtymology;
import fr.cnrs.lacito.liftapi.model.LiftExample;
import fr.cnrs.lacito.liftapi.model.LiftField;
import fr.cnrs.lacito.liftapi.model.LiftFieldAndTraitDefinition;
import fr.cnrs.lacito.liftapi.model.LiftIllustration;
import fr.cnrs.lacito.liftapi.model.LiftMedia;
import fr.cnrs.lacito.liftapi.model.LiftNote;
import fr.cnrs.lacito.liftapi.model.LiftPronunciation;
import fr.cnrs.lacito.liftapi.model.LiftRelation;
import fr.cnrs.lacito.liftapi.model.LiftReversal;
import fr.cnrs.lacito.liftapi.model.LiftSense;
import fr.cnrs.lacito.liftapi.model.LiftTrait;
import fr.cnrs.lacito.liftapi.model.LiftVariant;
import fr.cnrs.lacito.liftapi.model.MultiText;
import fr.cnrs.lacito.liftpatchbox.metamodel.LanguageKind;
import fr.cnrs.lacito.liftpatchbox.metamodel.Metamodel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javafx.collections.ObservableList;

/**
 * The single place that knows how the LiftPatch metamodel maps onto the
 * {@code lift-api} dictionary model.
 *
 * <p>Everything above this class — the resolver, the executor, plan mode — speaks
 * only of component types, property names and language codes, and never of
 * {@code LiftSense} or {@code MultiText}. That is what lets the engine be written
 * once against the metamodel, and what confines to one file the handful of places
 * where the two models do not line up.</p>
 *
 * <h2>Where the two models differ</h2>
 *
 * <p>The metamodel of Appendix A is slightly wider than the object model. These
 * are the gaps, and each of them raises {@link UnsupportedByModelException}
 * rather than being silently ignored:</p>
 *
 * <ul>
 *   <li>{@code entry.morpheme} has no field in the dictionary model;</li>
 *   <li>{@code reversal.target} has no field: a reversal carries forms and a type,
 *       and no reference;</li>
 *   <li>the {@code url} of an {@code illustration} and of a {@code media} is fixed
 *       when the component is built and cannot be rewritten afterwards;</li>
 *   <li>a {@code trait}, an {@code illustration} and a {@code media} hold no
 *       {@code field}, and an {@code illustration}, a {@code media} and a
 *       {@code reversal} hold no {@code annotation};</li>
 *   <li>an {@code entry} holds no {@code reversal}: the dictionary model attaches
 *       reversals to senses only.</li>
 * </ul>
 *
 * <p>Two further properties are approximated rather than refused, because the
 * approximation is faithful for every script that writes one language:
 * {@code etymology.source} is a multitext in the metamodel and a plain string in
 * the dictionary model, so it is read and written for one language at a time and
 * reported under the default meta language.</p>
 *
 * <h2>Mutation and rollback</h2>
 *
 * <p>Every mutation goes through this class, and every mutation pushes its
 * inverse onto the {@link Journal}. That is what makes a script atomic without
 * copying the dictionary (Part 2, section 12.2).</p>
 */
public final class LiftModel {

    private final LiftDictionary dictionary;
    private final Metamodel metamodel;
    private final Journal journal;
    private final HomophoneNumbering homophones;

    /**
     * An adapter over a dictionary.
     *
     * @param dictionary the dictionary to read and write
     * @param metamodel  the metamodel the script is validated against
     * @param journal    the journal every mutation records its inverse in
     */
    public LiftModel(LiftDictionary dictionary, Metamodel metamodel, Journal journal) {
        this.dictionary = dictionary;
        this.metamodel = metamodel;
        this.journal = journal;
        this.homophones = new HomophoneNumbering(dictionary);
    }

    /**
     * The dictionary this adapter writes to.
     *
     * @return the dictionary
     */
    public LiftDictionary dictionary() {
        return dictionary;
    }

    /**
     * The root of the hierarchy, the one legal parent of an {@code entry}.
     *
     * @return the root handle
     */
    public ComponentRef root() {
        return new ComponentRef.Root(dictionary);
    }

    // ===================================================================
    // Languages
    // ===================================================================

    /**
     * The language manager of one language kind.
     *
     * @param kind the language kind
     * @return the dictionary's manager for that kind
     */
    public LiftDictionaryLanguagesManager languages(LanguageKind kind) {
        return kind == LanguageKind.OBJECT
            ? dictionary.getObjectLanguageManager()
            : dictionary.getMetaLanguageManager();
    }

    /**
     * Whether the dictionary declares a language of the given kind.
     *
     * @param kind the language kind
     * @param code the language code
     * @return {@code true} when the code is in that kind's list
     */
    public boolean hasLanguage(LanguageKind kind, String code) {
        return languages(kind).hasLanguage(code);
    }

    /**
     * The first language of a kind, which is the fallback default when no binding
     * is in scope (Part 1, section 2.1).
     *
     * @param kind the language kind
     * @return the first code of that kind's list, or {@code null} when the list is empty
     */
    public String firstLanguage(LanguageKind kind) {
        Set<String> all = languages(kind).getLanguages();
        return all.isEmpty() ? null : all.iterator().next();
    }

    /**
     * Add a language to the dictionary, recording its removal in the journal.
     *
     * @param kind the language kind
     * @param code the language code
     */
    public void createLanguage(LanguageKind kind, String code) {
        LiftDictionaryLanguagesManager manager = languages(kind);
        manager.addLanguage(code);
        journal.record(() -> manager.removeLanguage(code));
    }

    // ===================================================================
    // Identity and position
    // ===================================================================

    /**
     * The persistent identifier of a component, which only an {@code entry} and a
     * {@code sense} carry.
     *
     * @param ref the component
     * @return the identifier, or empty when the component has none
     */
    public Optional<String> idOf(ComponentRef ref) {
        if (ref instanceof ComponentRef.Node node
            && node.node() instanceof AbstractIdentifiable identifiable) {
            return identifiable.getId();
        }
        return Optional.empty();
    }

    /**
     * The homophone number of an entry (Part 1, section 5.3.2.1).
     *
     * @param ref the entry
     * @return its homophone number, an integer greater than or equal to 1
     */
    public int hnOf(ComponentRef ref) {
        return homophones.numberOf((LiftEntry) ((ComponentRef.Node) ref).node());
    }

    /**
     * Forget the cached homophone numbers, which a command changing a {@code form}
     * may have invalidated.
     */
    public void invalidateHomophones() {
        homophones.invalidate();
    }

    /**
     * The 1-based position of a component among its same-type siblings.
     *
     * @param ref the component
     * @return the position, or empty for an {@code entry}, a typed component and a
     *         singleton, none of which has a position this language reports
     */
    public Optional<Integer> positionOf(ComponentRef ref) {
        if (!(ref instanceof ComponentRef.Node node)) {
            return Optional.empty();
        }
        String type = node.componentType();
        if (Metamodel.ENTRY.equals(type)
            || !metamodel.requireComponentType(type).isOrdered()) {
            return Optional.empty();
        }
        ComponentRef parent = parentOf(ref);
        if (parent == null) {
            return Optional.empty();
        }
        List<ComponentRef> siblings = children(parent, type);
        int index = siblings.indexOf(ref);
        return index < 0 ? Optional.empty() : Optional.of(index + 1);
    }

    /**
     * The parent of a component.
     *
     * @param ref the component
     * @return its parent, the root for an {@code entry}, or {@code null} when the
     *         component is detached
     */
    public ComponentRef parentOf(ComponentRef ref) {
        return switch (ref) {
            case ComponentRef.Root _ -> null;
            case ComponentRef.Category c -> new ComponentRef.Node("sense", c.host());
            case ComponentRef.Translation t -> new ComponentRef.Node("example", t.example());
            case ComponentRef.Node node -> {
                if (node.node() instanceof LiftEntry) {
                    yield root();
                }
                AbstractLiftRoot parent = node.node().getParentNode();
                yield parent == null ? null : new ComponentRef.Node(typeOf(parent), parent);
            }
        };
    }

    /**
     * The LiftPatch component type of a dictionary-model component.
     *
     * @param node the component
     * @return the component type name
     * @throws UnsupportedByModelException if the component is of a class this
     *         metamodel does not name
     */
    public static String typeOf(AbstractLiftRoot node) {
        return switch (node) {
            case LiftEntry _ -> "entry";
            case LiftSense _ -> "sense";
            case LiftExample _ -> "example";
            case LiftEtymology _ -> "etymology";
            case LiftVariant _ -> "variant";
            case LiftRelation _ -> "relation";
            case LiftIllustration _ -> "illustration";
            case LiftMedia _ -> "media";
            case LiftPronunciation _ -> "pronunciation";
            case LiftReversal _ -> "reversal";
            case LiftTrait _ -> "trait";
            case LiftAnnotation _ -> "annotation";
            case LiftNote _ -> "note";
            case LiftField _ -> "field";
            case GrammaticalInfo _ -> "category";
            default -> throw new UnsupportedByModelException(
                "the dictionary model class " + node.getClass().getSimpleName()
                    + " has no LiftPatch component type");
        };
    }

    // ===================================================================
    // Children
    // ===================================================================

    /**
     * The children of one component type under a parent, in the order the language
     * designates them by: the sibling list for an ordered type, the map's keys
     * sorted for a typed one, and at most one for a singleton.
     *
     * @param parent    the parent component
     * @param childType the child component type
     * @return the children, never {@code null}
     * @throws UnsupportedByModelException if the dictionary model cannot hold that
     *         child under that parent
     */
    public List<ComponentRef> children(ComponentRef parent, String childType) {
        if (parent instanceof ComponentRef.Root) {
            if (!Metamodel.ENTRY.equals(childType)) {
                return List.of();
            }
            List<ComponentRef> out = new ArrayList<>();
            for (LiftEntry e : dictionary.getLiftDictionaryRegistry().getEntries()) {
                out.add(new ComponentRef.Node("entry", e));
            }
            return out;
        }
        if (parent instanceof ComponentRef.Category category) {
            if ("trait".equals(childType)) {
                return grammaticalInfo(category.host())
                    .map(g -> wrap("trait", g.getTraits()))
                    .orElse(List.of());
            }
            return List.of();
        }
        if (parent instanceof ComponentRef.Translation) {
            // A translation has no child in the metamodel.
            return List.of();
        }

        AbstractLiftRoot node = ((ComponentRef.Node) parent).node();
        return switch (childType) {
            case "sense" -> node instanceof LiftEntry e ? wrap("sense", e.getSenses())
                : node instanceof LiftSense s ? wrap("sense", s.getSenses())
                : unsupportedChild(parent, childType);
            case "example" -> node instanceof LiftSense s ? wrap("example", s.getExamples())
                : unsupportedChild(parent, childType);
            case "etymology" -> node instanceof LiftEntry e ? wrap("etymology", e.getEtymologies())
                : unsupportedChild(parent, childType);
            case "variant" -> node instanceof LiftEntry e ? wrap("variant", e.getVariants())
                : unsupportedChild(parent, childType);
            case "relation" -> node instanceof LiftEntry e ? wrap("relation", e.getRelations())
                : node instanceof LiftSense s ? wrap("relation", s.getRelations())
                : node instanceof LiftVariant v ? wrap("relation", v.getRelations())
                : unsupportedChild(parent, childType);
            case "pronunciation" -> node instanceof LiftEntry e
                ? wrap("pronunciation", e.getPronunciations())
                : node instanceof LiftVariant v ? wrap("pronunciation", v.getPronunciations())
                : unsupportedChild(parent, childType);
            case "illustration" -> node instanceof LiftSense s
                ? wrap("illustration", s.getIllustrations())
                : unsupportedChild(parent, childType);
            case "media" -> node instanceof LiftPronunciation p ? wrap("media", p.getMedias())
                : unsupportedChild(parent, childType);
            case "reversal" -> node instanceof LiftSense s ? wrap("reversal", s.getReversals())
                : node instanceof LiftReversal r ? wrap("reversal", r.getReversals())
                : unsupportedChild(parent, childType);
            case "trait" -> node instanceof AbstractExtensibleWithoutField e
                ? wrap("trait", e.getTraits())
                : unsupportedChild(parent, childType);
            case "annotation" -> node instanceof AbstractExtensibleWithoutField e
                ? wrap("annotation", e.getAnnotations())
                : node instanceof LiftTrait t ? wrap("annotation", t.getAnnotations())
                : unsupportedChild(parent, childType);
            case "note" -> node instanceof AbstractNotable n ? sortedByKey("note", n.getNotes())
                : unsupportedChild(parent, childType);
            case "field" -> node instanceof AbstractExtensibleWithField f
                ? sortedByKey("field", f.getFields())
                : unsupportedChild(parent, childType);
            case "translation" -> node instanceof LiftExample x ? translations(x)
                : unsupportedChild(parent, childType);
            case "category" -> node instanceof LiftSense s
                ? List.of(new ComponentRef.Category(s))
                : unsupportedChild(parent, childType);
            default -> List.of();
        };
    }

    private static List<ComponentRef> wrap(String type, List<? extends AbstractLiftRoot> nodes) {
        List<ComponentRef> out = new ArrayList<>(nodes.size());
        for (AbstractLiftRoot node : nodes) {
            out.add(new ComponentRef.Node(type, node));
        }
        return out;
    }

    private static List<ComponentRef> sortedByKey(
        String type, Map<String, ? extends AbstractLiftRoot> map
    ) {
        List<ComponentRef> out = new ArrayList<>(map.size());
        map.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> out.add(new ComponentRef.Node(type, e.getValue())));
        return out;
    }

    private static List<ComponentRef> translations(LiftExample example) {
        List<ComponentRef> out = new ArrayList<>();
        example.getTranslations().keySet().stream()
            .sorted(Comparator.comparing(Feature::getId))
            .forEach(f -> out.add(new ComponentRef.Translation(example, f.getId())));
        return out;
    }

    private static List<ComponentRef> unsupportedChild(ComponentRef parent, String childType) {
        throw new UnsupportedByModelException(
            "the dictionary model holds no `" + childType + "` under a `"
                + parent.componentType() + "`");
    }

    private static Optional<GrammaticalInfo> grammaticalInfo(LiftSense sense) {
        return sense.getGrammaticalInfo();
    }

    // ===================================================================
    // Reading properties
    // ===================================================================

    /**
     * The value of a scalar property.
     *
     * @param ref      the component
     * @param property the property name
     * @return the value, or empty when the property is unset
     * @throws UnsupportedByModelException if the dictionary model has no field for it
     */
    public Optional<String> scalar(ComponentRef ref, String property) {
        return switch (ref) {
            case ComponentRef.Category category -> "value".equals(property)
                ? grammaticalInfo(category.host())
                    .map(GrammaticalInfo::getGramInfoValue)
                    .map(Feature::getId)
                : Optional.empty();
            case ComponentRef.Translation translation -> "type".equals(property)
                ? Optional.of(translation.type())
                : Optional.empty();
            case ComponentRef.Node node -> scalarOfNode(node, property);
            case ComponentRef.Root _ -> Optional.empty();
        };
    }

    private Optional<String> scalarOfNode(ComponentRef.Node ref, String property) {
        AbstractLiftRoot node = ref.node();
        return switch (node) {
            case LiftEntry _ -> switch (property) {
                case "morpheme" -> throw unsupportedProperty("entry", "morpheme");
                default -> Optional.empty();
            };
            case LiftEtymology y -> switch (property) {
                case "type" -> featureId(y.getType());
                default -> Optional.empty();
            };
            case LiftVariant v -> switch (property) {
                case "type" -> featureId(v.getType());
                case "target" -> reference(v.getRefId(), v.getRefObject());
                default -> Optional.empty();
            };
            case LiftRelation r -> switch (property) {
                case "type" -> featureId(r.getType());
                case "target" -> reference(r.getRefId(), r.getRefObject());
                default -> Optional.empty();
            };
            case LiftReversal l -> switch (property) {
                case "type" -> featureId(l.getType());
                case "target" -> throw unsupportedProperty("reversal", "target");
                default -> Optional.empty();
            };
            case LiftIllustration i -> "url".equals(property)
                ? Optional.ofNullable(i.getHref()) : Optional.empty();
            case LiftMedia m -> "url".equals(property)
                ? Optional.ofNullable(m.getHref()) : Optional.empty();
            case LiftTrait t -> switch (property) {
                case "type" -> Optional.ofNullable(t.getSpecification()).map(LiftFieldAndTraitDefinition::getName);
                case "value" -> Optional.ofNullable(t.getValue()).filter(s -> !s.isEmpty());
                default -> Optional.empty();
            };
            case LiftAnnotation a -> switch (property) {
                case "type" -> featureId(a.getType());
                case "value" -> Optional.ofNullable(a.getValue());
                case "when" -> Optional.ofNullable(a.getWhen());
                case "who" -> Optional.ofNullable(a.getWho());
                default -> Optional.empty();
            };
            case LiftNote n -> "type".equals(property) ? featureId(n.getType()) : Optional.empty();
            case LiftField f -> "type".equals(property)
                ? Optional.ofNullable(f.getSpecification()).map(LiftFieldAndTraitDefinition::getName)
                : Optional.empty();
            case GrammaticalInfo g -> "value".equals(property)
                ? featureId(g.getGramInfoValue()) : Optional.empty();
            default -> Optional.empty();
        };
    }

    /**
     * The identifier a reference points at.
     *
     * <p>The dictionary model stores a reference in either of two places: the
     * builders set the referenced <em>object</em>, and the XML reader sets the
     * <em>identifier</em>. Both are read here, identifier first, which is the order
     * the model's own registration code uses.</p>
     */
    private static Optional<String> reference(
        Optional<String> refId, AbstractIdentifiable refObject
    ) {
        if (refId.isPresent()) {
            return refId;
        }
        return refObject == null ? Optional.empty() : refObject.getId();
    }

    private static Optional<String> featureId(Feature feature) {
        return feature == null ? Optional.empty() : Optional.of(feature.getId());
    }

    /**
     * The multitext of a property.
     *
     * @param ref      the component
     * @param property the property name
     * @return the multitext, or empty when the component has no such property
     * @throws UnsupportedByModelException if the dictionary model has no field for it
     */
    public Optional<MultiText> multitext(ComponentRef ref, String property) {
        if (ref instanceof ComponentRef.Translation translation) {
            if (!"text".equals(property)) {
                return Optional.empty();
            }
            Feature key = translationType(translation.example(), translation.type());
            return key == null
                ? Optional.empty()
                : Optional.ofNullable(translation.example().getTranslations().get(key));
        }
        if (ref instanceof ComponentRef.Category) {
            return Optional.empty();
        }
        if (!(ref instanceof ComponentRef.Node node)) {
            return Optional.empty();
        }
        AbstractLiftRoot n = node.node();
        return switch (n) {
            case LiftEntry e -> "form".equals(property)
                ? Optional.of(e.getForms()) : Optional.empty();
            case LiftSense s -> switch (property) {
                case "gloss" -> Optional.of(s.getGlosses());
                case "definition" -> Optional.of(s.getDefinition());
                default -> Optional.empty();
            };
            case LiftExample x -> "text".equals(property)
                ? Optional.of(x.getExample()) : Optional.empty();
            case LiftEtymology y -> switch (property) {
                case "form" -> Optional.of(y.getForms());
                case "gloss" -> Optional.of(y.getGlosses());
                default -> Optional.empty();
            };
            case LiftReversal l -> "form".equals(property)
                ? Optional.of(l.getForms()) : Optional.empty();
            case LiftIllustration i -> "label".equals(property)
                ? Optional.of(i.getLabel()) : Optional.empty();
            case LiftMedia m -> "label".equals(property)
                ? Optional.of(m.getLabel()) : Optional.empty();
            case LiftPronunciation p -> "transcription".equals(property)
                ? Optional.of(p.getPronunciation()) : Optional.empty();
            case LiftAnnotation a -> "comment".equals(property)
                ? Optional.of(a.getText()) : Optional.empty();
            case LiftNote note -> "text".equals(property)
                ? Optional.of(note.getText()) : Optional.empty();
            case LiftField f -> "text".equals(property)
                ? Optional.of(f.getText()) : Optional.empty();
            default -> Optional.empty();
        };
    }

    /**
     * One qualified value of a multitext property.
     *
     * @param ref      the component
     * @param property the property name
     * @param language the language code
     * @return the value, or empty when it is unset
     */
    public Optional<String> qualified(ComponentRef ref, String property, String language) {
        if (isEtymologySource(ref, property)) {
            LiftEtymology y = (LiftEtymology) ((ComponentRef.Node) ref).node();
            return Optional.ofNullable(y.getSource()).filter(s -> !s.isEmpty());
        }
        return multitext(ref, property)
            .flatMap(m -> m.getForm(language))
            .map(Form::toPlainText);
    }

    /**
     * The languages a multitext property has a value for.
     *
     * @param ref      the component
     * @param property the property name
     * @return the language codes, in the dictionary model's order
     */
    public Set<String> languagesOf(ComponentRef ref, String property) {
        if (isEtymologySource(ref, property)) {
            LiftEtymology y = (LiftEtymology) ((ComponentRef.Node) ref).node();
            String value = y.getSource();
            return value == null || value.isEmpty()
                ? Set.of()
                : Set.of(firstLanguage(LanguageKind.META));
        }
        return multitext(ref, property)
            .map(m -> (Set<String>) new LinkedHashSet<>(m.getLangs()))
            .orElse(Set.of());
    }

    private static boolean isEtymologySource(ComponentRef ref, String property) {
        return "source".equals(property)
            && ref instanceof ComponentRef.Node node
            && node.node() instanceof LiftEtymology;
    }

    // ===================================================================
    // Writing properties
    // ===================================================================

    /**
     * Write a scalar property, recording the inverse in the journal.
     *
     * @param ref      the component
     * @param property the property name
     * @param value    the new value, or {@code null} to clear it
     * @throws UnsupportedByModelException if the dictionary model cannot store it
     */
    public void setScalar(ComponentRef ref, String property, String value) {
        if (ref instanceof ComponentRef.Category category) {
            if (!"value".equals(property)) {
                throw unsupportedProperty("category", property);
            }
            setCategoryValue(category.host(), value);
            return;
        }
        if (ref instanceof ComponentRef.Translation translation) {
            if (!"type".equals(property)) {
                throw unsupportedProperty("translation", property);
            }
            retypeTranslation(translation, value);
            return;
        }
        AbstractLiftRoot node = ((ComponentRef.Node) ref).node();
        switch (node) {
            case LiftEntry _ -> throw unsupportedProperty("entry", property);
            case LiftEtymology y -> {
                switch (property) {
                    case "type" -> setFeature(
                        y.getType(), value, dictionary.getHeader().getEtymologyTypeManager(),
                        f -> y.setType(f));
                    case "source" -> {
                        String old = y.getSource();
                        y.setSource(value);
                        journal.record(() -> y.setSource(old));
                    }
                    default -> throw unsupportedProperty("etymology", property);
                }
            }
            case LiftVariant v -> {
                switch (property) {
                    case "type" -> setFeature(
                        v.getType(), value, dictionary.getHeader().getVariantTypeManager(),
                        f -> v.setType(f));
                    case "target" -> setReference(
                        value, v.getRefId().orElse(null), v.getRefObject(),
                        v::setRefId, v::setRefObject);
                    default -> throw unsupportedProperty("variant", property);
                }
            }
            case LiftRelation r -> {
                switch (property) {
                    case "type" -> setFeature(
                        r.getType(), value, dictionary.getHeader().getRelationTypeManager(),
                        f -> r.setType(f));
                    case "target" -> setReference(
                        value, r.getRefId().orElse(null), r.getRefObject(),
                        r::setRefId, r::setRefObject);
                    default -> throw unsupportedProperty("relation", property);
                }
            }
            case LiftReversal l -> {
                if (!"type".equals(property)) {
                    throw unsupportedProperty("reversal", property);
                }
                setFeature(l.getType(), value, dictionary.getHeader().getInverseTypeManager(),
                    f -> l.setType(f));
            }
            case LiftTrait t -> {
                switch (property) {
                    case "value" -> {
                        String old = t.getValue();
                        t.setValue(value == null ? "" : value);
                        journal.record(() -> t.setValue(old));
                    }
                    case "type" -> throw new UnsupportedByModelException(
                        "the dictionary model fixes the `type` of a trait when the trait is "
                            + "built; delete the trait and create it with the new type");
                    default -> throw unsupportedProperty("trait", property);
                }
            }
            case LiftAnnotation a -> {
                switch (property) {
                    case "type" -> setFeature(
                        a.getType(), value, dictionary.getHeader().getAnnotationTypeManager(),
                        f -> a.setType(f));
                    case "value" -> {
                        String old = a.getValue();
                        a.setValue(value);
                        journal.record(() -> a.setValue(old));
                    }
                    case "when" -> {
                        String old = a.getWhen();
                        a.setWhen(value);
                        journal.record(() -> a.setWhen(old));
                    }
                    case "who" -> {
                        String old = a.getWho();
                        a.setWho(value);
                        journal.record(() -> a.setWho(old));
                    }
                    default -> throw unsupportedProperty("annotation", property);
                }
            }
            case LiftNote n -> {
                if (!"type".equals(property)) {
                    throw unsupportedProperty("note", property);
                }
                retypeNote(n, value);
            }
            case LiftField f -> {
                if (!"type".equals(property)) {
                    throw unsupportedProperty("field", property);
                }
                retypeField(f, value);
            }
            case GrammaticalInfo g -> {
                if (!"value".equals(property)) {
                    throw unsupportedProperty("category", property);
                }
                setCategoryValue(g.getParent(), value);
            }
            case LiftIllustration _ -> throw unsupportedProperty("illustration", property);
            case LiftMedia _ -> throw unsupportedProperty("media", property);
            default -> throw unsupportedProperty(ref.componentType(), property);
        }
    }

    /**
     * Write one qualified value of a multitext property, recording the inverse.
     *
     * @param ref      the component
     * @param property the property name
     * @param language the language code
     * @param value    the new text
     */
    public void setQualified(ComponentRef ref, String property, String language, String value) {
        if (isEtymologySource(ref, property)) {
            setScalar(ref, "source", value);
            return;
        }
        MultiText multitext = multitext(ref, property)
            .orElseThrow(() -> unsupportedProperty(ref.componentType(), property));
        String old = multitext.getForm(language).map(Form::toPlainText).orElse(null);
        if (multitext.containsLang(language)) {
            multitext.removeForm(language);
        }
        multitext.add(new Form(language, value));
        journal.record(() -> {
            if (multitext.containsLang(language)) {
                multitext.removeForm(language);
            }
            if (old != null) {
                multitext.add(new Form(language, old));
            }
        });
    }

    /**
     * Remove one qualified value of a multitext property, recording the inverse.
     *
     * @param ref      the component
     * @param property the property name
     * @param language the language code
     */
    public void removeQualified(ComponentRef ref, String property, String language) {
        if (isEtymologySource(ref, property)) {
            setScalar(ref, "source", null);
            return;
        }
        MultiText multitext = multitext(ref, property)
            .orElseThrow(() -> unsupportedProperty(ref.componentType(), property));
        if (!multitext.containsLang(language)) {
            return;
        }
        String old = multitext.getForm(language).map(Form::toPlainText).orElse(null);
        multitext.removeForm(language);
        journal.record(() -> multitext.add(new Form(language, old)));
    }

    /**
     * Write a reference, setting both the identifier and the referenced object.
     *
     * <p>Both are set because the dictionary model reads each of them in a
     * different place: its registration code prefers the identifier, and its
     * removal code dereferences the object.</p>
     */
    private void setReference(
        String value,
        String oldId,
        AbstractIdentifiable oldObject,
        java.util.function.Consumer<String> idSetter,
        java.util.function.Consumer<AbstractIdentifiable> objectSetter
    ) {
        AbstractIdentifiable target = value == null
            ? null
            : dictionary.getLiftDictionaryRegistry().getEntryOrSenseByLiftId(value);
        idSetter.accept(value);
        if (target != null) {
            objectSetter.accept(target);
        }
        journal.record(() -> {
            idSetter.accept(oldId);
            objectSetter.accept(oldObject);
        });
    }

    private void setFeature(
        Feature old, String value, FeatureSet manager, java.util.function.Consumer<Feature> setter
    ) {
        Feature fresh = value == null ? null : manager.getOrCreateFeature(value);
        setter.accept(fresh);
        journal.record(() -> setter.accept(old));
    }

    private void setCategoryValue(LiftSense sense, String value) {
        Optional<GrammaticalInfo> old = sense.getGrammaticalInfo();
        String oldValue = old.map(GrammaticalInfo::getGramInfoValue).map(Feature::getId).orElse(null);
        if (value == null) {
            if (old.isPresent()) {
                sense.deleteGrammaticalInfo();
                journal.record(() -> restoreCategory(sense, oldValue));
            }
            return;
        }
        Feature feature = dictionary.getHeader().getGrammaticalInfoManager().getOrCreateFeature(value);
        if (old.isPresent()) {
            sense.deleteGrammaticalInfo();
        }
        sense.setGrammaticalInfo(feature);
        journal.record(() -> restoreCategory(sense, oldValue));
    }

    private void restoreCategory(LiftSense sense, String oldValue) {
        if (sense.getGrammaticalInfo().isPresent()) {
            sense.deleteGrammaticalInfo();
        }
        if (oldValue != null) {
            sense.setGrammaticalInfo(
                dictionary.getHeader().getGrammaticalInfoManager().getOrCreateFeature(oldValue));
        }
    }

    private void retypeNote(LiftNote note, String value) {
        AbstractNotable parent = note.getParent();
        Feature old = note.getType();
        if (parent == null) {
            note.setType(dictionary.getHeader().getNoteTypeManager().getOrCreateFeature(value));
            journal.record(() -> note.setType(old));
            return;
        }
        // The note lives in a map keyed by its type, so re-keying it is a removal
        // and an insertion rather than a field assignment.
        parent.deleteNote(note);
        note.setType(dictionary.getHeader().getNoteTypeManager().getOrCreateFeature(value));
        parent.addNote(note);
        journal.record(() -> {
            parent.deleteNote(note);
            note.setType(old);
            parent.addNote(note);
        });
    }

    private void retypeField(LiftField field, String value) {
        AbstractExtensibleWithField parent = field.getParent();
        LiftFieldAndTraitDefinition old = field.getSpecification();
        LiftFieldAndTraitDefinition fresh =
            dictionary.getHeader().getOrCreateFieldDefinitions(value);
        if (parent == null) {
            field.specificationProperty().set(fresh);
            journal.record(() -> field.specificationProperty().set(old));
            return;
        }
        parent.deleteField(field);
        field.specificationProperty().set(fresh);
        parent.addField(field);
        journal.record(() -> {
            parent.deleteField(field);
            field.specificationProperty().set(old);
            parent.addField(field);
        });
    }

    private void retypeTranslation(ComponentRef.Translation ref, String value) {
        LiftExample example = ref.example();
        Feature old = translationType(example, ref.type());
        if (old == null) {
            return;
        }
        MultiText text = example.getTranslations().get(old);
        Feature fresh =
            dictionary.getHeader().getTranslationTypeManager().getOrCreateFeature(value);
        example.getTranslations().remove(old);
        example.getTranslations().put(fresh, text);
        journal.record(() -> {
            example.getTranslations().remove(fresh);
            example.getTranslations().put(old, text);
        });
    }

    private static Feature translationType(LiftExample example, String type) {
        for (Feature f : example.getTranslations().keySet()) {
            if (f.getId().equals(type)) {
                return f;
            }
        }
        return null;
    }

    // ===================================================================
    // Creating, deleting and moving components
    // ===================================================================

    /**
     * What a {@code create} or the create branch of an {@code upsert} asks for: a
     * component type and its resolved initializers.
     *
     * @param componentType the component type to create
     * @param scalars       the scalar properties, by name
     * @param multitexts    the multitext properties, by name and then by language
     */
    public record NewComponent(
        String componentType,
        Map<String, String> scalars,
        Map<String, Map<String, String>> multitexts
    ) {

        /**
         * Canonical constructor, taking unmodifiable copies of the maps.
         *
         * @param componentType the component type to create
         * @param scalars       the scalar properties
         * @param multitexts    the multitext properties
         */
        public NewComponent {
            scalars = Map.copyOf(scalars);
            Map<String, Map<String, String>> copy = new LinkedHashMap<>();
            multitexts.forEach((k, v) -> copy.put(k, Map.copyOf(v)));
            multitexts = Map.copyOf(copy);
        }

        /**
         * The value of a scalar property, or {@code null}.
         *
         * @param name the property name
         * @return the value
         */
        public String scalar(String name) {
            return scalars.get(name);
        }
    }

    /**
     * Create a component under a parent and record its removal in the journal.
     *
     * @param parent   the parent component
     * @param spec     the component type and its initializers
     * @param position the 1-based position among same-type siblings, or {@code null}
     *                 for the last position; ignored for a typed component
     * @return a handle on the new component
     * @throws UnsupportedByModelException if the dictionary model cannot hold it
     */
    public ComponentRef createChild(ComponentRef parent, NewComponent spec, Integer position) {
        ComponentRef created = switch (spec.componentType()) {
            case "entry" -> createEntry(spec);
            case "sense" -> createSense(parent, spec, position);
            case "example" -> createExample(parent, spec, position);
            case "etymology" -> createEtymology(parent, spec);
            case "variant" -> createVariant(parent, spec, position);
            case "relation" -> createRelation(parent, spec, position);
            case "pronunciation" -> createPronunciation(parent, spec, position);
            case "illustration" -> createIllustration(parent, spec, position);
            case "media" -> createMedia(parent, spec, position);
            case "reversal" -> createReversal(parent, spec, position);
            case "trait" -> createTrait(parent, spec, position);
            case "annotation" -> createAnnotation(parent, spec, position);
            case "note" -> createNote(parent, spec);
            case "field" -> createField(parent, spec);
            case "translation" -> createTranslation(parent, spec);
            default -> throw new UnsupportedByModelException(
                "the dictionary model cannot create a `" + spec.componentType() + "`");
        };
        applyMultitexts(created, spec);
        return created;
    }

    private void applyMultitexts(ComponentRef ref, NewComponent spec) {
        spec.multitexts().forEach((property, values) ->
            values.forEach((lang, text) -> setQualified(ref, property, lang, text)));
    }

    private ComponentRef createEntry(NewComponent spec) {
        LiftEntry entry = LiftEntry.create();
        dictionary.addEntry(entry);
        journal.record(() -> dictionary.removeEntry(entry));
        homophones.invalidate();
        return new ComponentRef.Node("entry", entry);
    }

    private ComponentRef createSense(ComponentRef parent, NewComponent spec, Integer position) {
        LiftSense sense = LiftSense.create();
        AbstractLiftRoot node = requireNode(parent);
        if (node instanceof LiftEntry entry) {
            insert(sense, position, entry.sensesProperty(), () -> entry.addSense(sense),
                () -> entry.deleteSense(sense));
        } else if (node instanceof LiftSense host) {
            insert(sense, position, host.subSensesProperty(), () -> host.addSense(sense),
                () -> host.deleteSense(sense));
        } else {
            throw unsupportedParent(parent, "sense");
        }
        return new ComponentRef.Node("sense", sense);
    }

    private ComponentRef createExample(ComponentRef parent, NewComponent spec, Integer position) {
        LiftSense sense = requireType(parent, LiftSense.class, "example");
        LiftExample example = LiftExample.create();
        insert(example, position, sense.examplesProperty(), () -> sense.addExample(example),
            () -> sense.deleteExample(example));
        return new ComponentRef.Node("example", example);
    }

    private ComponentRef createEtymology(ComponentRef parent, NewComponent spec) {
        LiftEntry entry = requireType(parent, LiftEntry.class, "etymology");
        Feature type = dictionary.getHeader().getEtymologyTypeManager()
            .getOrCreateFeature(required(spec, "type", "etymology"));
        LiftEtymology etymology = LiftEtymology.create(type, spec.scalar("source"));
        entry.addEtymology(etymology);
        journal.record(() -> entry.deleteEtymology(etymology));
        return new ComponentRef.Node("etymology", etymology);
    }

    private ComponentRef createVariant(ComponentRef parent, NewComponent spec, Integer position) {
        LiftEntry entry = requireType(parent, LiftEntry.class, "variant");
        LiftVariant variant = new LiftVariant();
        variant.setType(dictionary.getHeader().getVariantTypeManager()
            .getOrCreateFeature(required(spec, "type", "variant")));
        setCreatedReference(spec.scalar("target"), variant::setRefId, variant::setRefObject);
        insert(variant, position, entry.variantsProperty(), () -> entry.addVariant(variant),
            () -> entry.deleteVariant(variant));
        return new ComponentRef.Node("variant", variant);
    }

    private ComponentRef createRelation(ComponentRef parent, NewComponent spec, Integer position) {
        LiftRelation relation = LiftRelation.create(
            dictionary.getHeader().getRelationTypeManager()
                .getOrCreateFeature(required(spec, "type", "relation")));
        setCreatedReference(spec.scalar("target"), relation::setRefId, relation::setRefObject);
        AbstractLiftRoot node = requireNode(parent);
        switch (node) {
            case LiftEntry entry -> insert(relation, position, entry.relationsProperty(),
                () -> entry.addRelation(relation), () -> entry.deleteRelation(relation));
            case LiftSense sense -> insert(relation, position, sense.relationsProperty(),
                () -> sense.addRelation(relation), () -> sense.deleteRelation(relation));
            case LiftVariant variant -> insert(relation, position, variant.relationsProperty(),
                () -> variant.addRelation(relation), () -> variant.deleteRelation(relation));
            default -> throw unsupportedParent(parent, "relation");
        }
        return new ComponentRef.Node("relation", relation);
    }

    private ComponentRef createPronunciation(
        ComponentRef parent, NewComponent spec, Integer position
    ) {
        LiftPronunciation pronunciation = LiftPronunciation.create();
        AbstractLiftRoot node = requireNode(parent);
        switch (node) {
            case LiftEntry entry -> insert(pronunciation, position, entry.pronunciationsProperty(),
                () -> entry.addPronunciation(pronunciation),
                () -> entry.deletePronunciation(pronunciation));
            case LiftVariant variant -> insert(pronunciation, position,
                variant.pronunciationsProperty(),
                () -> variant.addPronunciation(pronunciation),
                () -> variant.deletePronunciation(pronunciation));
            default -> throw unsupportedParent(parent, "pronunciation");
        }
        return new ComponentRef.Node("pronunciation", pronunciation);
    }

    private ComponentRef createIllustration(
        ComponentRef parent, NewComponent spec, Integer position
    ) {
        LiftSense sense = requireType(parent, LiftSense.class, "illustration");
        LiftIllustration illustration =
            new LiftIllustration(required(spec, "url", "illustration"));
        insert(illustration, position, sense.illustrationsProperty(),
            () -> sense.addIllustration(illustration), () -> sense.deleteIllustration(illustration));
        return new ComponentRef.Node("illustration", illustration);
    }

    private ComponentRef createMedia(ComponentRef parent, NewComponent spec, Integer position) {
        LiftPronunciation pronunciation = requireType(parent, LiftPronunciation.class, "media");
        LiftMedia media = new LiftMedia(required(spec, "url", "media"));
        insert(media, position, pronunciation.mediasProperty(),
            () -> pronunciation.addMedia(media), () -> pronunciation.deleteMedia(media));
        return new ComponentRef.Node("media", media);
    }

    private ComponentRef createReversal(ComponentRef parent, NewComponent spec, Integer position) {
        if (spec.scalars().containsKey("target")) {
            throw unsupportedProperty("reversal", "target");
        }
        LiftReversal reversal = new LiftReversal(
            dictionary.getHeader().getInverseTypeManager()
                .getOrCreateFeature(required(spec, "type", "reversal")));
        AbstractLiftRoot node = requireNode(parent);
        switch (node) {
            case LiftSense sense -> insert(reversal, position, sense.reversalsProperty(),
                () -> sense.addReversal(reversal), () -> sense.deleteReversal(reversal));
            default -> throw unsupportedParent(parent, "reversal");
        }
        return new ComponentRef.Node("reversal", reversal);
    }

    private ComponentRef createTrait(ComponentRef parent, NewComponent spec, Integer position) {
        LiftFieldAndTraitDefinition definition = dictionary.getHeader()
            .getOrCreateTraitsDefinitions(required(spec, "type", "trait"));
        LiftTrait trait = new LiftTrait(definition, spec.scalars().getOrDefault("value", ""));
        if (parent instanceof ComponentRef.Category category) {
            GrammaticalInfo info = grammaticalInfo(category.host()).orElseThrow(() ->
                new UnsupportedByModelException(
                    "the sense has no grammatical information to carry a trait; write its `value` "
                        + "first"));
            info.addTrait(trait);
            journal.record(() -> info.deleteTrait(trait));
            return new ComponentRef.Node("trait", trait);
        }
        AbstractLiftRoot node = requireNode(parent);
        if (node instanceof AbstractExtensibleWithoutField host) {
            insert(trait, position, host.traitsProperty(), () -> host.addTrait(trait),
                () -> host.deleteTrait(trait));
            return new ComponentRef.Node("trait", trait);
        }
        if (node instanceof GrammaticalInfo info) {
            info.addTrait(trait);
            journal.record(() -> info.deleteTrait(trait));
            return new ComponentRef.Node("trait", trait);
        }
        throw unsupportedParent(parent, "trait");
    }

    private ComponentRef createAnnotation(
        ComponentRef parent, NewComponent spec, Integer position
    ) {
        LiftAnnotation annotation = new LiftAnnotation(
            dictionary.getHeader().getAnnotationTypeManager()
                .getOrCreateFeature(required(spec, "type", "annotation")));
        annotation.setValue(spec.scalar("value"));
        annotation.setWhen(spec.scalar("when"));
        annotation.setWho(spec.scalar("who"));
        AbstractLiftRoot node = requireNode(parent);
        if (node instanceof AbstractExtensibleWithoutField host) {
            insert(annotation, position, host.annotationsProperty(),
                () -> host.addAnnotation(annotation), () -> host.deleteAnnotation(annotation));
            return new ComponentRef.Node("annotation", annotation);
        }
        if (node instanceof LiftTrait trait) {
            trait.addAnnotation(annotation);
            journal.record(() -> trait.deleteAnnotation(annotation));
            return new ComponentRef.Node("annotation", annotation);
        }
        throw unsupportedParent(parent, "annotation");
    }

    private ComponentRef createNote(ComponentRef parent, NewComponent spec) {
        AbstractNotable host = requireType(parent, AbstractNotable.class, "note");
        LiftNote note = LiftNote.create();
        note.setType(dictionary.getHeader().getNoteTypeManager()
            .getOrCreateFeature(required(spec, "type", "note")));
        host.addNote(note);
        journal.record(() -> host.deleteNote(note));
        return new ComponentRef.Node("note", note);
    }

    private ComponentRef createField(ComponentRef parent, NewComponent spec) {
        AbstractExtensibleWithField host =
            requireType(parent, AbstractExtensibleWithField.class, "field");
        LiftField field = LiftField.create(dictionary.getHeader()
            .getOrCreateFieldDefinitions(required(spec, "type", "field")));
        host.addField(field);
        journal.record(() -> host.deleteField(field));
        return new ComponentRef.Node("field", field);
    }

    private ComponentRef createTranslation(ComponentRef parent, NewComponent spec) {
        LiftExample example = requireType(parent, LiftExample.class, "translation");
        String type = required(spec, "type", "translation");
        Feature key = dictionary.getHeader().getTranslationTypeManager().getOrCreateFeature(type);
        example.getOrCreateTranslation(key);
        journal.record(() -> example.getTranslations().remove(key));
        return new ComponentRef.Translation(example, type);
    }

    private void setCreatedReference(
        String value,
        java.util.function.Consumer<String> idSetter,
        java.util.function.Consumer<AbstractIdentifiable> objectSetter
    ) {
        if (value == null) {
            return;
        }
        idSetter.accept(value);
        AbstractIdentifiable target =
            dictionary.getLiftDictionaryRegistry().getEntryOrSenseByLiftId(value);
        if (target != null) {
            objectSetter.accept(target);
        }
    }

    /**
     * Insert a component into an ordered sibling list at a 1-based position.
     *
     * <p>The component is appended through the model's own {@code addX} method,
     * which is what registers it in the dictionary, and only then moved into place
     * through the observable list, which reorders without unregistering.</p>
     */
    private <T> void insert(
        T component, Integer position, ObservableList<T> list, Runnable add, Runnable remove
    ) {
        add.run();
        if (position != null && position - 1 < list.size() - 1 && position >= 1) {
            list.remove(component);
            list.add(position - 1, component);
        }
        journal.record(remove);
    }

    /**
     * Delete a component with its descendants, recording its restoration.
     *
     * @param ref the component to delete
     * @throws UnsupportedByModelException if the dictionary model cannot remove it
     */
    public void delete(ComponentRef ref) {
        if (ref instanceof ComponentRef.Translation translation) {
            LiftExample example = translation.example();
            Feature key = translationType(example, translation.type());
            if (key == null) {
                return;
            }
            MultiText text = example.getTranslations().get(key);
            example.getTranslations().remove(key);
            journal.record(() -> example.getTranslations().put(key, text));
            return;
        }
        if (!(ref instanceof ComponentRef.Node node)) {
            throw new UnsupportedByModelException(
                "a `" + ref.componentType() + "` is never removed by a command");
        }
        AbstractLiftRoot child = node.node();
        // The dictionary model refuses to remove a component that something still
        // refers to. The language, by contrast, does not maintain referential
        // integrity (Part 2, section 10): deleting a referenced component is legal,
        // and the reference is reported as dangling rather than repaired. The
        // inbound references are therefore unlinked, the component removed, and the
        // inbound references put back, still pointing at the component that is gone.
        List<Runnable> restoreInbound = detachInboundReferences(ref);

        if (child instanceof LiftEntry entry) {
            int index = dictionary.getLiftDictionaryRegistry().getEntries().indexOf(entry);
            dictionary.removeEntry(entry);
            homophones.invalidate();
            journal.record(() -> {
                dictionary.addEntry(entry, Math.max(0, index));
                homophones.invalidate();
            });
        } else {
            AbstractLiftRoot parent = child.getParentNode();
            int position = indexOfSibling(parent, child);
            deleteFromParent(parent, child);
            journal.record(() -> reattach(parent, child, position));
        }
        restoreInbound.forEach(Runnable::run);
    }

    /**
     * Unlink every reference pointing into the subtree about to be removed, and
     * return the actions that put them back.
     */
    private List<Runnable> detachInboundReferences(ComponentRef ref) {
        Set<String> ids = new LinkedHashSet<>();
        collectIds(ref, ids);
        List<Runnable> restore = new ArrayList<>();
        for (String id : ids) {
            for (HasRefId referring
                : List.copyOf(dictionary.getLiftDictionaryRegistry().getReferencesTo(id))) {
                AbstractLiftRoot node = (AbstractLiftRoot) referring;
                AbstractLiftRoot parent = node.getParentNode();
                if (parent == null) {
                    continue;
                }
                int position = indexOfSibling(parent, node);
                deleteFromParent(parent, node);
                restore.add(() -> reattach(parent, node, position));
            }
        }
        return restore;
    }

    private void collectIds(ComponentRef ref, Set<String> ids) {
        idOf(ref).ifPresent(ids::add);
        for (String childType : metamodel.requireComponentType(ref.componentType()).children()) {
            List<ComponentRef> children;
            try {
                children = children(ref, childType);
            } catch (UnsupportedByModelException e) {
                continue;
            }
            for (ComponentRef child : children) {
                collectIds(child, ids);
            }
        }
    }

    private void deleteFromParent(AbstractLiftRoot parent, AbstractLiftRoot child) {
        switch (child) {
            case LiftSense s -> s.getParent().deleteSense(s);
            case LiftExample x -> x.getParent().deleteExample(x);
            case LiftEtymology y -> y.getParent().deleteEtymology(y);
            case LiftVariant v -> v.getParent().deleteVariant(v);
            case LiftRelation r -> r.getParent().deleteRelation(r);
            case LiftPronunciation p -> p.getParent().deletePronunciation(p);
            case LiftIllustration i -> i.getParent().deleteIllustration(i);
            case LiftMedia m -> m.getParent().deleteMedia(m);
            case LiftReversal l -> l.getParent().deleteReversal(l);
            case LiftNote n -> n.getParent().deleteNote(n);
            case LiftField f -> f.getParent().deleteField(f);
            case LiftTrait t -> t.getParent().deleteTrait(t);
            case LiftAnnotation a -> a.getParent().deleteAnnotation(a);
            default -> throw new UnsupportedByModelException(
                "the dictionary model cannot remove a " + child.getClass().getSimpleName());
        }
    }

    private void reattach(AbstractLiftRoot parent, AbstractLiftRoot child, int position) {
        switch (child) {
            case LiftSense s -> reinsert(s, position,
                parent instanceof LiftEntry e ? e.sensesProperty()
                    : ((LiftSense) parent).subSensesProperty(),
                () -> {
                    if (parent instanceof LiftEntry e) {
                        e.addSense(s);
                    } else {
                        ((LiftSense) parent).addSense(s);
                    }
                });
            case LiftExample x -> reinsert(x, position, ((LiftSense) parent).examplesProperty(),
                () -> ((LiftSense) parent).addExample(x));
            case LiftEtymology y -> ((LiftEntry) parent).addEtymology(y);
            case LiftVariant v -> reinsert(v, position, ((LiftEntry) parent).variantsProperty(),
                () -> ((LiftEntry) parent).addVariant(v));
            case LiftRelation r -> {
                if (parent instanceof LiftEntry e) {
                    reinsert(r, position, e.relationsProperty(), () -> e.addRelation(r));
                } else if (parent instanceof LiftSense s) {
                    reinsert(r, position, s.relationsProperty(), () -> s.addRelation(r));
                } else {
                    ((LiftVariant) parent).addRelation(r);
                }
            }
            case LiftPronunciation p -> {
                if (parent instanceof LiftEntry e) {
                    reinsert(p, position, e.pronunciationsProperty(), () -> e.addPronunciation(p));
                } else {
                    ((LiftVariant) parent).addPronunciation(p);
                }
            }
            case LiftIllustration i -> reinsert(i, position,
                ((LiftSense) parent).illustrationsProperty(),
                () -> ((LiftSense) parent).addIllustration(i));
            case LiftMedia m -> ((LiftPronunciation) parent).addMedia(m);
            case LiftReversal l -> ((LiftSense) parent).addReversal(l);
            case LiftNote n -> ((AbstractNotable) parent).addNote(n);
            case LiftField f -> ((AbstractExtensibleWithField) parent).addField(f);
            case LiftTrait t -> ((fr.cnrs.lacito.liftapi.model.HasTrait) parent).addTrait(t);
            case LiftAnnotation a ->
                ((fr.cnrs.lacito.liftapi.model.HasAnnotation) parent).addAnnotation(a);
            default -> throw new UnsupportedByModelException(
                "the dictionary model cannot restore a " + child.getClass().getSimpleName());
        }
    }

    private <T> void reinsert(T component, int position, ObservableList<T> list, Runnable add) {
        add.run();
        if (position >= 0 && position < list.size() - 1) {
            list.remove(component);
            list.add(position, component);
        }
    }

    private int indexOfSibling(AbstractLiftRoot parent, AbstractLiftRoot child) {
        if (parent == null) {
            return -1;
        }
        List<ComponentRef> siblings =
            children(new ComponentRef.Node(typeOf(parent), parent), typeOf(child));
        return siblings.indexOf(new ComponentRef.Node(typeOf(child), child));
    }

    /**
     * Move an ordered component, changing its position, its parent, or both.
     *
     * @param ref       the component to move
     * @param newParent the destination parent
     * @param position  the 1-based destination position among same-type siblings
     */
    public void move(ComponentRef ref, ComponentRef newParent, int position) {
        AbstractLiftRoot child = ((ComponentRef.Node) ref).node();
        AbstractLiftRoot oldParent = child.getParentNode();
        int oldPosition = indexOfSibling(oldParent, child);

        // `detach` unlinks without unregistering, which is the model's documented
        // way of moving a subtree inside one dictionary.
        child.detach();
        reattach(requireNode(newParent), child, position - 1);
        journal.record(() -> {
            child.detach();
            reattach(oldParent, child, oldPosition);
        });
    }

    // ===================================================================
    // Small helpers
    // ===================================================================

    private static AbstractLiftRoot requireNode(ComponentRef ref) {
        if (ref instanceof ComponentRef.Node node) {
            return node.node();
        }
        throw new UnsupportedByModelException(
            "a `" + ref.componentType() + "` cannot be the parent of a component here");
    }

    private static <T> T requireType(ComponentRef ref, Class<T> expected, String childType) {
        AbstractLiftRoot node = requireNode(ref);
        if (expected.isInstance(node)) {
            return expected.cast(node);
        }
        throw unsupportedParent(ref, childType);
    }

    private static String required(NewComponent spec, String property, String componentType) {
        String value = spec.scalar(property);
        if (value == null) {
            throw new UnsupportedByModelException(
                "a `" + componentType + "` needs its `" + property + "` at creation");
        }
        return value;
    }

    private static UnsupportedByModelException unsupportedParent(
        ComponentRef parent, String childType
    ) {
        return new UnsupportedByModelException(
            "the dictionary model holds no `" + childType + "` under a `"
                + parent.componentType() + "`");
    }

    private static UnsupportedByModelException unsupportedProperty(
        String componentType, String property
    ) {
        return new UnsupportedByModelException(
            "the dictionary model has no field for `" + componentType + "." + property + "`");
    }
}
