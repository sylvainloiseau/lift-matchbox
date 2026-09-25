package fr.cnrs.lacito.liftpatchbox.error;

/**
 * Every error code the LiftPatch language defines, with its kind.
 *
 * <p>This enumeration is the Java rendering of Appendix B of the specification.
 * The codes of B.3 — the codes a conforming implementation MUST NOT raise — are
 * deliberately absent, so that they cannot be raised by accident.</p>
 *
 * <p>The {@code enum} constant name is the code as the specification spells it,
 * so {@code ErrorCode.valueOf(String)} maps a code written in a conformance case
 * onto the constant without any translation table.</p>
 */
public enum ErrorCode {

    // ---------------------------------------------------------------- static

    /** The version pragma declares a version the implementation does not support. */
    UNSUPPORTED_LANGUAGE_VERSION(ErrorKind.STATIC),
    /** The version pragma carries an attribute other than the three defined ones. */
    UNKNOWN_PRAGMA_ATTRIBUTE(ErrorKind.STATIC),
    /** The pragma requires a metamodel the implementation has not loaded. */
    UNSUPPORTED_METAMODEL(ErrorKind.STATIC),
    /** A property is used on a component type the metamodel does not define it on. */
    PROPERTY_DOES_NOT_EXIST_ON_COMPONENT_TYPE(ErrorKind.STATIC),
    /** Two component types are stated to be parent and child where the metamodel does not relate them. */
    ILLEGAL_PARENT(ErrorKind.STATIC),
    /** A language qualifier is written on a scalar property. */
    LANG_KEY_NOT_SUPPORTED_ON_SCALAR(ErrorKind.STATIC),
    /** {@code clear} is written on a multitext with neither a language code nor {@code @*}. */
    MISSING_LANGUAGE_QUALIFIER(ErrorKind.STATIC),
    /** The wildcard {@code @*} is used where it is forbidden. */
    WILDCARD_NOT_ALLOWED(ErrorKind.STATIC),
    /** The same qualified property is initialized or assigned twice in one command. */
    DUPLICATE_PROPERTY(ErrorKind.STATIC),
    /** A multi-language literal is assigned to a qualified property name. */
    QUALIFIER_ON_MULTITEXT_LITERAL(ErrorKind.STATIC),
    /** A concise initializer gives more unnamed arguments than its component type allows. */
    UNNAMED_ARGUMENT_NOT_ALLOWED(ErrorKind.STATIC),
    /** A selector states the same constraint twice or combines conflicting strategies. */
    DUPLICATE_SELECTOR(ErrorKind.STATIC),
    /** A selector required to select exactly one component uses no valid selection strategy. */
    INCOMPLETE_SELECTOR(ErrorKind.STATIC),
    /** A `create` or `upsert` initializer list omits a property the metamodel declares required. */
    MISSING_REQUIRED_PROPERTY(ErrorKind.STATIC),
    /** An `upsert` initializer list does not cover the whole natural identity property set. */
    MISSING_IDENTITY_PROPERTY(ErrorKind.STATIC),
    /** A unique selector carries a predicate that is not part of its selection strategy. */
    PREDICATE_NOT_ALLOWED_IN_UNIQUE_SELECTOR(ErrorKind.STATIC),
    /** A pseudo-property is used in a filtering selector. */
    PSEUDO_PROPERTY_NOT_ALLOWED_IN_FILTER(ErrorKind.STATIC),
    /** {@code hn} is used with a command that does not allow it. */
    COMMAND_NOT_ALLOWING_HN(ErrorKind.STATIC),
    /** {@code has} or {@code has-gloss} is used with a command that does not allow it. */
    COMMAND_NOT_ALLOWING_HAS(ErrorKind.STATIC),
    /** {@code hn} is used in a selector that does not also give {@code form}. */
    HN_CANNOT_BE_USED_ALONE(ErrorKind.STATIC),
    /** {@code has-gloss} is used in a selector that does not also give {@code form}. */
    HAS_GLOSS_CANNOT_BE_USED_ALONE(ErrorKind.STATIC),
    /** The value given for {@code hn} is not an integer, or is lower than 1. */
    ILLEGAL_HN(ErrorKind.STATIC),
    /** An ordinal, or the {@code n} of an {@code at index n} clause, is lower than 1. */
    ILLEGAL_ORDINAL(ErrorKind.STATIC),
    /** {@code id} is used in an {@code upsert} initializer list. */
    ID_NOT_ALLOWED_ON_UPSERT(ErrorKind.STATIC),
    /** {@code upsert entry(...)} gives a form with no {@code has} or {@code has-gloss} predicate. */
    UPSERT_ENTRY_WITHOUT_DISAMBIGUATION(ErrorKind.STATIC),
    /** A chain or a path stops before reaching the root. */
    INCOMPLETE_ANCESTOR_CHAIN(ErrorKind.STATIC),
    /** A command gives no parent where one is required. */
    MISSING_PARENT_CLAUSE(ErrorKind.STATIC),
    /** A {@code c} or {@code p} command is written with a path whose created steps are not abbreviated. */
    CONSTRUCTOR_REQUIRED(ErrorKind.STATIC),
    /** A command tries to create, upsert, delete or move a singleton component type. */
    SINGLETON_CANNOT_BE_CREATED_OR_DELETED(ErrorKind.STATIC),
    /** A singleton step is given a selector where it is a unique selector. */
    SINGLETON_TAKES_NO_SELECTOR(ErrorKind.STATIC),
    /** An ordinal, an {@code at} clause or a {@code move} names a type whose kind is not ordered. */
    COMPONENT_NOT_ORDERED(ErrorKind.STATIC),
    /** A type key names a component type whose kind is not typed. */
    COMPONENT_NOT_TYPED(ErrorKind.STATIC),
    /** The direct target of a concise {@code d}, {@code e} or {@code m} is written as several steps. */
    TARGET_MUST_BE_A_SINGLE_STEP(ErrorKind.STATIC),
    /** A bare word or quoted string is written as a step at a depth for which no abbreviation is defined. */
    ABBREVIATED_STEP_NOT_ALLOWED_HERE(ErrorKind.STATIC),
    /** An operator is used on a datatype that does not admit it. */
    OPERATOR_NOT_APPLICABLE_TO_DATATYPE(ErrorKind.STATIC),
    /** The component designated as the value of a reference property is neither an entry nor a sense. */
    INVALID_TARGET(ErrorKind.STATIC),
    /** A multiplicity keyword is used where it is not allowed. */
    MULTIPLICITY_NOT_ALLOWED(ErrorKind.STATIC),
    /** A label name visible in the current scope is bound again. */
    DUPLICATE_LABEL(ErrorKind.STATIC),
    /** A {@code $name} is used that no visible command has bound. */
    UNKNOWN_LABEL(ErrorKind.STATIC),
    /** A bare string is assigned to, or compared with, a property of datatype reference. */
    REFERENCE_VALUE_MUST_BE_A_CHAIN(ErrorKind.STATIC),
    /** A {@code move} command appears in a block body. */
    MOVE_NOT_ALLOWED_IN_BLOCK(ErrorKind.STATIC),
    /** A concise command line is indented under a {@code d}. */
    DELETE_CANNOT_BE_AN_ANCHOR(ErrorKind.STATIC),
    /** An {@code upsert}, {@code ensure} or {@code update} appears in the scope of a {@code create} block. */
    CANNOT_UPSERT_ENSURE_OR_UPDATE_IN_CREATION_BLOCK(ErrorKind.STATIC),
    /** {@code clear} targets a scalar property belonging to the natural identity property set. */
    CANNOT_CLEAR_IDENTITY_PROPERTY(ErrorKind.STATIC),
    /** {@code clear} targets a scalar property the metamodel declares required. */
    CANNOT_CLEAR_REQUIRED_PROPERTY(ErrorKind.STATIC),
    /** The script does not parse, or uses a construct the grammar forbids in that position. */
    SYNTAX_ERROR(ErrorKind.STATIC),

    // --------------------------------------------------------------- dynamic

    /** A selector, a path, a value chain or an {@code ensure} assertion matches no component. */
    NOT_FOUND(ErrorKind.DYNAMIC),
    /** A selector required to select exactly one component matches several. */
    AMBIGUOUS_REFERENCE(ErrorKind.DYNAMIC),
    /** A command would give two same-type siblings the same natural identity key. */
    CANNOT_CREATE_DUPLICATE(ErrorKind.DYNAMIC),
    /** {@code update} or {@code clear} targets a property that has no value at all. */
    UNSET_PROPERTY(ErrorKind.DYNAMIC),
    /** {@code update} or {@code clear} targets a qualified value that is not set. */
    UNSET_QUALIFIED_PROPERTY(ErrorKind.DYNAMIC),
    /** {@code clear} would leave a required or identity multitext with no value at all. */
    CANNOT_CLEAR_REQUIRED_MULTITEXT(ErrorKind.DYNAMIC),
    /** An ordinal or an {@code at index n} is greater than the upper bound of the sibling list. */
    INDEX_OUT_OF_BOUNDS(ErrorKind.DYNAMIC),
    /** A {@code move} resolves to the position the component already occupies. */
    MOVING_TO_CURRENT_POSITION(ErrorKind.DYNAMIC),
    /** A {@code move} would make a component its own ancestor. */
    SELF_ANCESTOR(ErrorKind.DYNAMIC),
    /** An object language code is not in the dictionary's object language list. */
    NO_SUCH_OBJECT_LANGUAGE(ErrorKind.DYNAMIC),
    /** A meta language code is not in the dictionary's meta language list. */
    NO_SUCH_META_LANGUAGE(ErrorKind.DYNAMIC),
    /** {@code language-create} names a language the dictionary already has. */
    LANGUAGE_ALREADY_EXISTS(ErrorKind.DYNAMIC),

    /**
     * An operation the specification defines but the underlying {@code lift-api}
     * dictionary model cannot express.
     *
     * <p>This code is outside Appendix B: it never reports a defect of the script,
     * only a limit of this implementation's storage layer. It is raised, for
     * instance, when a script assigns the {@code target} of a {@code reversal},
     * which the {@code lift-api} model does not carry.</p>
     */
    UNSUPPORTED_BY_DICTIONARY_MODEL(ErrorKind.DYNAMIC);

    private final ErrorKind kind;

    ErrorCode(ErrorKind kind) {
        this.kind = kind;
    }

    /**
     * The kind of this error code.
     *
     * @return {@link ErrorKind#STATIC} when the error depends only on the script
     *         text and the metamodel, {@link ErrorKind#DYNAMIC} when it depends on
     *         the state of the dictionary
     */
    public ErrorKind kind() {
        return kind;
    }

    /**
     * Whether this code denotes a static error.
     *
     * @return {@code true} if and only if {@link #kind()} is {@link ErrorKind#STATIC}
     */
    public boolean isStatic() {
        return kind == ErrorKind.STATIC;
    }
}
