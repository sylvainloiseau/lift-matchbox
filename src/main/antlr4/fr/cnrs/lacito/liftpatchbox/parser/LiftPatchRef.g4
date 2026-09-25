/*
 * The LiftPatchRef reference syntax, as given in Appendix C.1 of the
 * specification.
 *
 * The grammar deliberately does NOT encode the constraints that depend on the
 * metamodel or on the position of a selector in a command -- which predicates a
 * unique selector admits, which pseudo-properties a command accepts, which
 * properties a component type has, which component kind admits an ordinal. Those
 * are stated normatively in Parts 1 and 2 and in Appendices A and B, and are
 * checked by the semantic validator. A parse tree accepted here may therefore
 * still be rejected as a static error, which is exactly what Appendix C says.
 *
 * Two consequences are worth stating, because they look like laxity and are not:
 *
 *  - the three step productions of the specification (ordered, typed, singleton)
 *    are folded into one `step` rule, because which one applies is fixed by the
 *    componentKind the metamodel declares, and this parser does not load the
 *    metamodel;
 *  - the `under` / `on` clauses are optional everywhere, because the grammar does
 *    not know whether it is inside a block. A command that omits its parent
 *    clause at top level is rejected with MISSING_PARENT_CLAUSE.
 */
grammar LiftPatchRef;

@lexer::members {
    /**
     * Whether the '#' at the current input position opens a comment.
     *
     * A '#' opens a comment when it begins a line or is preceded by whitespace,
     * and is not immediately followed by a digit. The second condition is what
     * keeps `at before #2` an ordinal rather than a comment, while `# two senses`
     * remains a comment.
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
// Parser
// =====================================================================

script          : pragma? item* EOF ;

item            : block
                | command
                | directive
                ;

pragma          : PCT_LIFTPATCH version pragmaAttribute* ;
version         : INT DOT INT ;
pragmaAttribute : anyName EQUALS string ;

directive       : (LANGUAGE_DEFAULT | LANGUAGE_CREATE) langKind EQUALS string ;
langKind        : OBJECT | META ;

block           : blockHeader LBRACE item* RBRACE ;
blockHeader     : createCmd
                | upsertCmd
                | ensureCmd
                | withHeader
                | chain labelBinding?
                ;
withHeader      : WITH binding (COMMA binding)* ;
binding         : langKind EQUALS string ;

command         : createCmd
                | upsertCmd
                | ensureCmd
                | deleteCmd
                | moveCmd
                | setCmd
                | updateCmd
                | clearCmd
                ;

createCmd       : CREATE constructor (UNDER chain)? atClause? labelBinding? ;
upsertCmd       : UPSERT constructor (UNDER chain)? atClause? labelBinding? ;
ensureCmd       : ENSURE ensureTarget (UNDER chain)? labelBinding? ;
deleteCmd       : DELETE multiplicity? step (UNDER chain)? ;
moveCmd         : MOVE step (UNDER chain)? (UNDER chain)? atClause? ;

// `ensure` takes a selector between square brackets and never a constructor.
// A constructor written there is admitted by this rule so that the visitor can
// report it as the SYNTAX_ERROR that Part 1, section 6.1 rule 5 prescribes,
// instead of a bare parse failure with no explanation.
ensureTarget    : step | constructor ;

setCmd          : SET assignment (COMMA assignment)* (ON multiplicity? chain)? ;
updateCmd       : UPDATE assignment (COMMA assignment)* (ON multiplicity? chain)? ;
clearCmd        : CLEAR propertyRef (COMMA propertyRef)* (ON multiplicity? chain)? ;
assignment      : propertyRef EQUALS value ;

constructor     : componentType LPAREN (initializer (COMMA initializer)*)? RPAREN ;
initializer     : propertyRef EQUALS value
                | hasPredicate
                ;

multiplicity    : EACH | ALL ;
atClause        : AT position ;
position        : BEGINNING
                | END
                | INDEX INT
                | BEFORE step
                | AFTER step
                ;
labelBinding    : AS labelRef ;
labelRef        : DOLLAR anyName ;

chain           : step (axis step)* ;
axis            : OF | WITHIN ;

step            : componentType (LBRACK selector RBRACK | ORDINAL | typeKey)?
                | labelRef
                ;
typeKey         : CARET (anyName | string) ;

selector        : predicate (COMMA predicate)* ;
predicate       : propertyRef comparison value
                | EXISTS LPAREN propertyRef RPAREN
                | ABSENT LPAREN propertyRef RPAREN
                | hasPredicate
                ;
hasPredicate    : HAS step ;
comparison      : EQUALS | NOT_EQUALS | TILDE_I | TILDE ;

propertyRef     : propertyName (ATSIGN (lang | STAR))? ;

value           : string
                | INT
                | multitextLiteral
                | chain
                ;
multitextLiteral: LBRACE langText (COMMA langText)* RBRACE ;
langText        : lang COLON string ;

componentType   : NAME ;
propertyName    : NAME | INDEX ;
lang            : anyName ;
string          : STRING_DQ | STRING_SQ ;

// Every keyword may also be written where an arbitrary name is expected: a type
// key, a label name, a language code or a pragma attribute name.
anyName         : NAME
                | CREATE | UPSERT | ENSURE | DELETE | MOVE | SET | UPDATE | CLEAR
                | UNDER | ON | OF | WITHIN | AT | AS | EACH | ALL | WITH
                | HAS | EXISTS | ABSENT
                | BEGINNING | END | INDEX | BEFORE | AFTER
                | OBJECT | META
                ;

// =====================================================================
// Lexer
// =====================================================================

PCT_LIFTPATCH   : '%liftpatch' ;

LANGUAGE_DEFAULT: 'language-default' ;
LANGUAGE_CREATE : 'language-create' ;

CREATE          : 'create' ;
UPSERT          : 'upsert' ;
ENSURE          : 'ensure' ;
DELETE          : 'delete' ;
MOVE            : 'move' ;
SET             : 'set' ;
UPDATE          : 'update' ;
CLEAR           : 'clear' ;

UNDER           : 'under' ;
ON              : 'on' ;
OF              : 'of' ;
WITHIN          : 'within' ;
WITH            : 'with' ;
AT              : 'at' ;
AS              : 'as' ;
EACH            : 'each' ;
ALL             : 'all' ;

HAS             : 'has' ;
EXISTS          : 'exists' ;
ABSENT          : 'absent' ;

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

NOT_EQUALS      : '!=' ;
TILDE_I         : '~i' ;
TILDE           : '~' ;
EQUALS          : '=' ;

// The ordinal is defined before the comment so that `example#1` lexes as one
// token; the comment rule's predicate then refuses a '#' followed by a digit.
ORDINAL         : '#' [0-9]+ ;
COMMENT         : {commentStart()}? '#' ~[\r\n]* -> skip ;

INT             : [0-9]+ ;

STRING_DQ       : '"' ( '\\' ["\\] | ~["\\\r\n] )* '"' ;
STRING_SQ       : '\'' ( '\'\'' | ~['\r\n] )* '\'' ;

NAME            : [\p{L}] [\p{L}\p{M}\p{Nd}_-]* ;

WS              : [ \t\r\n]+ -> skip ;

// Anything the rules above do not match is emitted as a token of its own, so
// that the error listener can report it with its position instead of the lexer
// silently dropping it.
UNKNOWN         : . ;
