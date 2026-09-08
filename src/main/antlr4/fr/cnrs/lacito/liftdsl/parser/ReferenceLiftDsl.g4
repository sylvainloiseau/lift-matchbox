grammar ReferenceLiftDsl;
program: statement* EOF;
statement: languageDefault | command block?;
languageDefault: 'language-default' ('object'|'meta') '=' STRING;
block: '{' (statement)* '}';
command: componentCommand | propertyCommand;
componentCommand: ('create'|'upsert'|'ensure') component initializerList? parentClause? position?
                | 'delete' component selector parentClause?
                | 'move' component selector parentClause? ('under' component selector parentClause?)? position;
propertyCommand: ('set'|'update') property ('=' value) ('on'|'under') component selector parentClause?
               | 'clear' property ('on'|'under') component selector parentClause?;
component: COMPONENT;
selector: '[' predicate (',' predicate)* ']';
predicate: NAME ('@' NAME)? '=' value | 'index' '=' INT | 'ID' '=' value | 'hn' '=' INT | 'has-gloss' '@' NAME '=' value;
initializerList: '(' initializer (',' initializer)* ')';
initializer: property '=' value;
parentClause: ('under'|'of'|'within'|'on') component selector parentClause?;
position: 'at' ('beginning'|'end'|'index' INT);
property: NAME ('@' NAME)? ('^' NAME)?;
value: STRING | reference;
reference: component selector parentClause?;
COMPONENT: 'entry'|'sense'|'example'|'etymology'|'variant'|'relation'|'illustration'|'media'|'pronunciation'|'reversal'|'trait'|'annotation'|'note'|'field'|'translation';
NAME: [a-zA-Z_][a-zA-Z0-9_-]*;
INT: [0-9]+;
STRING: '"' ('\\' . | ~["\\])* '"' | '\'' ('\\' . | ~['\\])* '\'';
WS: [ \t\r\n]+ -> skip;
COMMENT: '#' ~[\r\n]* -> skip;
