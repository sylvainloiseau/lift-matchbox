package fr.cnrs.lacito.liftpatchbox.metamodel;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The normative metamodel of Appendix A: component types, parentage, properties,
 * datatypes, language kinds, required properties and natural identity sets.
 *
 * <p>The metamodel is loaded from the JSON document the specification prints, so
 * that there is exactly one source of truth and no hand-maintained Java copy of
 * it to drift. {@link #standard()} loads the bundled 1.0 document;
 * {@link #load(String)} loads an <em>extended</em> metamodel, a document of the
 * same shape carrying additional component types or properties, under the rules
 * of Appendix A.</p>
 *
 * <p>Every rule of the language is stated in terms of this object rather than in
 * terms of the particular component types it happens to declare, with the single
 * exception of {@code entry}, whose special status is part of the language.</p>
 */
public final class Metamodel {

    /** The component type name of the one type that sits directly under the root. */
    public static final String ENTRY = "entry";

    private static final String RESOURCE = "/fr/cnrs/lacito/liftpatchbox/metamodel-1.0.json";

    private static volatile Metamodel standard;

    private final String version;
    private final String metamodelId;
    private final String root;
    private final Map<String, ComponentTypeDef> components;
    private final Map<String, PseudoPropertyDef> pseudoProperties;

    private Metamodel(
        String version,
        String metamodelId,
        String root,
        Map<String, ComponentTypeDef> components,
        Map<String, PseudoPropertyDef> pseudoProperties
    ) {
        this.version = version;
        this.metamodelId = metamodelId;
        this.root = root;
        this.components = Map.copyOf(components);
        this.pseudoProperties = Map.copyOf(pseudoProperties);
    }

    /**
     * The metamodel of Appendix A, version 1.0, loaded from the bundled document.
     *
     * <p>The document is parsed once and the result cached, since it is immutable.</p>
     *
     * @return the standard metamodel
     * @throws UncheckedIOException if the bundled resource cannot be read
     */
    public static Metamodel standard() {
        Metamodel m = standard;
        if (m == null) {
            synchronized (Metamodel.class) {
                m = standard;
                if (m == null) {
                    standard = m = load(readResource());
                }
            }
        }
        return m;
    }

    private static String readResource() {
        try (InputStream in = Metamodel.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing bundled metamodel resource " + RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the bundled metamodel", e);
        }
    }

    /**
     * Load a metamodel from a JSON document of the shape of Appendix A.
     *
     * @param json the document
     * @return the metamodel it declares
     * @throws IllegalArgumentException if the document is malformed, or declares a
     *         component type that breaks one of the two invariants of Appendix A —
     *         a typed type whose natural identity is not exactly {@code type}, or a
     *         singleton type with a non-empty natural identity or a required property
     */
    public static Metamodel load(String json) {
        Map<String, Object> doc = Json.parseObject(json);
        String version = Json.string(doc, "liftpatchMetamodel");
        String id = Json.string(doc, "metamodelId");
        String root = Optional.ofNullable(Json.string(doc, "root")).orElse("dictionary");

        Map<String, ComponentTypeDef> components = new LinkedHashMap<>();
        Map<String, Object> rawComponents = Json.object(doc, "components");
        for (Map.Entry<String, Object> e : rawComponents.entrySet()) {
            components.put(e.getKey(), readComponent(e.getKey(), asObject(e.getValue(), e.getKey())));
        }

        Map<String, PseudoPropertyDef> pseudo = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : Json.object(doc, "pseudoProperties").entrySet()) {
            Map<String, Object> raw = asObject(e.getValue(), e.getKey());
            pseudo.put(e.getKey(), new PseudoPropertyDef(
                e.getKey(),
                PseudoPropertyKind.parse(Json.string(raw, "kind")),
                Json.string(raw, "datatype") == null ? null : Datatype.parse(Json.string(raw, "datatype")),
                LanguageKind.parse(Json.string(raw, "qualifier")),
                Json.string(raw, "scope"),
                Json.string(raw, "expandsTo")
            ));
        }

        Metamodel m = new Metamodel(version, id, root, components, pseudo);
        m.checkInvariants();
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object v, String what) {
        if (!(v instanceof Map)) {
            throw new IllegalArgumentException("Expected a JSON object for " + what);
        }
        return (Map<String, Object>) v;
    }

    private static ComponentTypeDef readComponent(String name, Map<String, Object> raw) {
        Map<String, PropertyDef> properties = new LinkedHashMap<>();
        Set<String> identity = new LinkedHashSet<>(Json.strings(raw, "naturalIdentity"));
        for (Map.Entry<String, Object> e : Json.object(raw, "properties").entrySet()) {
            Map<String, Object> p = asObject(e.getValue(), name + "." + e.getKey());
            properties.put(e.getKey(), new PropertyDef(
                name,
                e.getKey(),
                Datatype.parse(Json.string(p, "datatype")),
                LanguageKind.parse(Json.string(p, "qualifier")),
                Json.bool(p, "required", false),
                identity.contains(e.getKey())
            ));
        }
        return new ComponentTypeDef(
            name,
            ComponentKind.parse(Json.string(raw, "componentKind")),
            Json.strings(raw, "children"),
            Json.strings(raw, "naturalIdentity"),
            Json.strings(raw, "unnamedArguments"),
            properties
        );
    }

    /**
     * Check the two invariants Appendix A requires of any metamodel.
     *
     * @throws IllegalArgumentException when one of them is broken
     */
    private void checkInvariants() {
        for (ComponentTypeDef c : components.values()) {
            switch (c.kind()) {
                case TYPED -> {
                    if (!c.naturalIdentity().equals(java.util.List.of("type"))) {
                        throw new IllegalArgumentException(
                            "A typed component type must have naturalIdentity [\"type\"]: " + c.name());
                    }
                }
                case SINGLETON -> {
                    if (!c.naturalIdentity().isEmpty()) {
                        throw new IllegalArgumentException(
                            "A singleton component type must have an empty naturalIdentity: " + c.name());
                    }
                    if (!c.requiredProperties().isEmpty()) {
                        throw new IllegalArgumentException(
                            "A singleton component type must have no required property: " + c.name());
                    }
                    if (!c.unnamedArguments().isEmpty()) {
                        throw new IllegalArgumentException(
                            "A singleton component type takes no unnamed argument: " + c.name());
                    }
                }
                case ORDERED -> {
                    // No kind invariant of its own.
                }
            }
            for (String child : c.children()) {
                if (!components.containsKey(child)) {
                    throw new IllegalArgumentException(
                        "Component type " + c.name() + " declares an unknown child type " + child);
                }
            }
        }
    }

    // ------------------------------------------------------------- accessors

    /**
     * The version this metamodel declares in {@code liftpatchMetamodel}.
     *
     * @return the version string, such as {@code "1.0"}
     */
    public String version() {
        return version;
    }

    /**
     * The identifier an extended metamodel declares in {@code metamodelId}.
     *
     * @return the identifier, or {@code null} for the standard metamodel
     */
    public String metamodelId() {
        return metamodelId;
    }

    /**
     * The name of the root of the hierarchy.
     *
     * @return {@code "dictionary"} in the standard metamodel
     */
    public String root() {
        return root;
    }

    /**
     * Every component type name this metamodel declares.
     *
     * @return an unmodifiable set, in declaration order
     */
    public Set<String> componentTypeNames() {
        return components.keySet();
    }

    /**
     * Look a component type up by name.
     *
     * @param name the component type name
     * @return the declaration, or empty when the metamodel declares no such type
     */
    public Optional<ComponentTypeDef> componentType(String name) {
        return Optional.ofNullable(components.get(name));
    }

    /**
     * Look a component type up by name, failing when it is unknown.
     *
     * @param name the component type name
     * @return the declaration
     * @throws IllegalArgumentException if the metamodel declares no such type
     */
    public ComponentTypeDef requireComponentType(String name) {
        ComponentTypeDef c = components.get(name);
        if (c == null) {
            throw new IllegalArgumentException("Unknown component type: " + name);
        }
        return c;
    }

    /**
     * Whether this metamodel declares a component type of that name.
     *
     * @param name the candidate name
     * @return {@code true} when the type is declared
     */
    public boolean isComponentType(String name) {
        return components.containsKey(name);
    }

    /**
     * The kind of a component type.
     *
     * @param name the component type name
     * @return the kind
     * @throws IllegalArgumentException if the metamodel declares no such type
     */
    public ComponentKind kindOf(String name) {
        return requireComponentType(name).kind();
    }

    /**
     * Whether the metamodel relates two component types as parent and child.
     *
     * <p>The root is a legal parent of exactly one type, {@code entry}.</p>
     *
     * @param parentType the parent component type name, or the root name
     * @param childType  the child component type name
     * @return {@code true} when a component of {@code childType} may be created or
     *         moved under a component of {@code parentType}
     */
    public boolean isLegalParent(String parentType, String childType) {
        if (root.equals(parentType)) {
            return ENTRY.equals(childType);
        }
        ComponentTypeDef parent = components.get(parentType);
        return parent != null && parent.admitsChild(childType);
    }

    /**
     * Look a property up by component type and property name.
     *
     * @param componentType the component type name
     * @param propertyName  the property name, spelled in full
     * @return the property, or empty when the type does not define it
     */
    public Optional<PropertyDef> property(String componentType, String propertyName) {
        ComponentTypeDef c = components.get(componentType);
        return c == null ? Optional.empty() : c.property(propertyName);
    }

    /**
     * Every pseudo-property name this metamodel declares.
     *
     * @return an unmodifiable set, in declaration order
     */
    public Set<String> pseudoPropertyNames() {
        return pseudoProperties.keySet();
    }

    /**
     * Look a pseudo-property up by name.
     *
     * @param name the pseudo-property name, such as {@code "hn"}
     * @return the declaration, or empty when there is none
     */
    public Optional<PseudoPropertyDef> pseudoProperty(String name) {
        return Optional.ofNullable(pseudoProperties.get(name));
    }

    /**
     * Whether a name is that of a pseudo-property rather than of a real property.
     *
     * @param name the candidate name
     * @return {@code true} for {@code id}, {@code hn}, {@code has}, {@code has-gloss}
     *         and the other pseudo-properties of Appendix A
     */
    public boolean isPseudoProperty(String name) {
        return pseudoProperties.containsKey(name);
    }
}
