/*
 * The LiftPatchShort concise syntax, as given in Appendix C.2 of the
 * specification.
 *
 * Three things that grammar states are *not* encoded here, because they are
 * decided by whitespace and by the shape of a whole document rather than by a
 * token stream, and Appendix C.2 says a parser must apply them as separate
 * rules:
 *
 *  - whether a line is a command line at all (the recognition rule of Part 3,
 *    section 1.1, and the sigil mode);
 *  - the indentation, which opens and closes blocks (Part 3, section 3.3);
 *  - the split between a command's parent path and its direct target, which is
 *    the *last top-level whitespace-separated path token* of the command
 *    (Part 3, section 4.1).
 *
 * All three are handled by {@code ShortLinePreprocessor}, which hands this
 * parser well-delimited fragments: a path, a single step, a constructor, a
 * parenthesized property list, an `at` clause. The start rules below are those
 * fragments, which is why there is no rule for a whole command line.
 *
 * As in the reference grammar, everything that depends on the metamodel -- which
 * component kind admits an ordinal, which properties a type has, whether an
 * abbreviated step is legal at that depth -- is deferred to the semantic
 * validator.
 */
grammar LiftPatchShort;

@lexer::members {
    /**
     * Whether the '#' at the current input position opens a comment.
     *
     * A '#' opens a comment when it begins a line or is preceded by whitespace,
     * and is not immediately followed by a digit, so that `at before #2` is an
     * ordinal and `# a remark` is a comment. Comments are in practice stripped
     * by the preprocessor before a fragment reaches this lexer; the rule is kept
     * so that the lexer is correct when used on its own.
     */
    private boolean commentStart() {
        int i = getCharIndex();
        int next = _input.LA(2);
        if (next >= '0' && next <= '9') {
            return false;
        }
        if (i == 0) {
            return true;
        }
        int prev = _input.getText(
            org.antlr.v4.runtime.misc.Interval.of(i - 1, i - 1)).charAt(0);
        return prev == ' ' || prev == '\t' || prev == '\r' || prev == '\n';
    }
}

// =====================================================================
// Parser: the fragment start rules
// =====================================================================

/** A whole path, absolute or relative. */
pathOnly        : path EOF ;

/** A single step: the direct target of a `d`, an `e` or the source of an `m`. */
stepOnly        : pathStep EOF ;

/** The constructor of a `c` or a `p`. */
constructorOnly : constructor EOF ;

/** The parenthesized assignment list of an `s` or a `u`. */
assignListOnly  : LPAREN assignment (COMMA assignment)* RPAREN EOF ;

/** The parenthesized property list of an `l`. */
propertyListOnly: LPAREN propertyRef (COMMA propertyRef)* RPAREN EOF ;

/** The trailing clauses of a command line: `to`, `at` and `as`. */
tailOnly        : (TO path)? atClause? labelBinding? EOF ;

/** A `language-default` or `language-create` directive line. */
directiveOnly   : (LANGUAGE_DEFAULT | LANGUAGE_CREATE) langKind EQUALS string EOF ;

/** A version pragma line. */
pragmaOnly      : PCT_LIFTPATCH version pragmaAttribute* EOF ;

// =====================================================================
// Parser: the productions
// =====================================================================

version         : INT DOT INT ;
pragmaAttribute : anyName EQUALS string ;

langKind        : OBJECT | META ;

path            : SLASH pathStep (axis pathStep)*        # absolutePath
                | labelRef (axis pathStep)*              # labelPath
                | pathStep (axis pathStep)*              # relativePath
                ;
axis            : SLASH | BANG ;

pathStep        : componentCode (LBRACK selector RBRACK | ORDINAL | LBRACK INT RBRACK | typeKey)
                | labelRef
                | abbreviatedStep
                ;

/*
 * A bare or quoted token standing for an entry form, a sense gloss or an example
 * text (Part 3, section 6.1). Which of the three it is depends on the depth of
 * the step below the root, which the visitor counts; a token that is exactly the
 * code or the name of a singleton component type admissible at that point is a
 * `componentCode` step instead, and rule 5 of Part 3, section 2 is applied by
 * the visitor, not here.
 */
abbreviatedStep : (bareWord | string) (ATSIGN lang)? ;

constructor     : componentCode LPAREN (shortInit (COMMA shortInit)*)? RPAREN ;
shortInit       : propertyRef EQUALS shortValue
                | hasPredicate
                | constructor
                | shortValue
                ;

assignment      : propertyRef EQUALS shortValue ;

shortValue      : string (ATSIGN lang)?
                | INT
                | multitextLiteral
                | path
                | labelRef
                ;

multitextLiteral: LBRACE langText (COMMA langText)* RBRACE ;
langText        : lang COLON string ;

atClause        : AT position ;
position        : BEGINNING
                | END
                | INDEX INT
                | (BEFORE | AFTER) (pathStep | ORDINAL)
                ;
labelBinding    : AS labelRef ;
labelRef        : DOLLAR anyName ;

selector        : predicate (COMMA predicate)* ;
predicate       : propertyRef comparison shortValue
                | EXISTS LPAREN propertyRef RPAREN
                | ABSENT LPAREN propertyRef RPAREN
                | hasPredicate
                ;
hasPredicate    : HAS pathStep ;
comparison      : EQUALS | NOT_EQUALS | TILDE_I | TILDE ;

propertyRef     : propertyCode (ATSIGN (lang | STAR))? ;

typeKey         : CARET (bareWord | string) ;

componentCode   : bareWord ;
propertyCode    : bareWord | INDEX ;
lang            : bareWord ;
string          : STRING_DQ | STRING_SQ ;

/*
 * A bare word is any run of letters, marks, digits, '_', '-' and '.'. Because
 * the keywords below are lexed as tokens of their own, they are listed here so
 * that a form, a gloss or a type key may be spelled with one: `/end` is the
 * entry whose form is "end".
 */
bareWord        : BARE_WORD
                | HAS | EXISTS | ABSENT
                | AT | AS | TO
                | BEGINNING | END | INDEX | BEFORE | AFTER
                | OBJECT | META
                | INT
                ;

anyName         : bareWord ;

// =====================================================================
// Lexer
// =====================================================================

PCT_LIFTPATCH   : '%liftpatch' ;

LANGUAGE_DEFAULT: 'language-default' ;
LANGUAGE_CREATE : 'language-create' ;

HAS             : 'has' ;
EXISTS          : 'exists' ;
ABSENT          : 'absent' ;

AT              : 'at' ;
AS              : 'as' ;
TO              : 'to' ;

BEGINNING       : 'beginning' ;
END             : 'end' ;
INDEX           : 'index' ;
BEFORE          : 'before' ;
AFTER           : 'after' ;

OBJECT          : 'object' ;
META            : 'meta' ;

LBRACE          : '{' ;
RBRACE          : '}' ;
LBRACK          : '[' ;
RBRACK          : ']' ;
LPAREN          : '(' ;
RPAREN          : ')' ;
COMMA           : ',' ;
COLON           : ':' ;
DOT             : '.' ;
DOLLAR          : '$' ;
CARET           : '^' ;
ATSIGN          : '@' ;
STAR            : '*' ;
SLASH           : '/' ;
BANG            : '!' ;

NOT_EQUALS      : '!=' ;
TILDE_I         : '~i' ;
TILDE           : '~' ;
EQUALS          : '=' ;

ORDINAL         : '#' [0-9]+ ;
COMMENT         : {commentStart()}? '#' ~[\r\n]* -> skip ;

INT             : [0-9]+ ;

STRING_DQ       : '"' ( '\\' ["\\] | ~["\\\r\n] )* '"' ;
STRING_SQ       : '\'' ( '\'\'' | ~['\r\n] )* '\'' ;

/*
 * The bare word of Part 3, section 6.1.1, defined positively: Unicode letters,
 * combining marks and decimal digits, plus '_', '-' and '.'. Everything else --
 * whitespace and the delimiters -- must be quoted. Defining the class by what it
 * admits rather than by what it forbids is what keeps it open to the scripts of
 * the world's languages, and is why '.' belongs to it while '@' does not.
 */
BARE_WORD       : [\p{L}\p{M}\p{Nd}_.-] [\p{L}\p{M}\p{Nd}_.-]* ;

WS              : [ \t\r\n]+ -> skip ;

UNKNOWN         : . ;
