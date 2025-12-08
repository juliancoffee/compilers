grammar MS2;

// --- Parser Rules ---

program
    : topLevelStmt* EOF
    ;

topLevelStmt
    : funcDecl      # TopLevelFunc
    | letDecl       # TopLevelLet
    ;

// Function Declaration
// func add(a: Int, b: Int) -> Int { ... }
funcDecl
    : 'func' ID '(' paramList? ')' ('->' type)? block
    ;

paramList
    : param (',' param)* ','?
    ;

param
    : ID ':' type
    ;

block
    : '{' stmt* '}'
    ;

// Statements
stmt
    : varDecl                           # Var
    | letDecl                           # Let
    | assignStmt                        # Assign
    | printStmt                         # Print
    | returnStmt                        # Return
    | ifStmt                            # If
    | whileStmt                         # While
    | forStmt                           # For
    | switchStmt                        # Switch
    | exprStmt                          # CallStmt
    ;

// Variable Declarations
// var x: Int = 5;
varDecl
    : 'var' ID (':' type)? '=' expr ';'
    ;

// let x: Int = 5;
letDecl
    : 'let' ID (':' type)? '=' expr ';'
    ;

assignStmt
    : ID '=' expr ';'
    ;

// print("Hello", name);
printStmt
    : 'print' '(' expr (',' expr)* ','? ')' ';'
    ;

returnStmt
    : 'return' expr ';'
    ;

// Function call as a statement: foo();
exprStmt
    : callExpr ';'
    ;

// Control Flow
ifStmt
    : 'if' expr block ('else' block)?
    ;

whileStmt
    : 'while' expr block
    ;

// for i in range(0, 5, 1) { ... }
forStmt
    : 'for' ID 'in' iterable block
    ;

iterable
    : 'range' '(' INT ',' INT ',' INT ')'  # RangeIter
    | expr                                 # ExprIter
    ;

// Switch
switchStmt
    : 'switch' expr '{' caseStmt* '}'
    ;

caseStmt
    : 'case' comparator block   # CaseValue
    | 'default' block           # CaseDefault
    ;

comparator
    : 'range' '(' INT ',' INT ')'   # RangeCompRule
    | literal (',' literal)+        # SeqCompRule
    | literal                       # ConstCompRule
    ;

// --- Expressions ---
// Ordered by precedence (lowest at top, highest at bottom)

expr
    : expr '**' expr                       # Power
    | op=('+'|'-'|'!') expr                # Unary
    | expr op=('*'|'/') expr               # MulDiv
    | expr op=('+'|'-') expr               # AddSub
    | expr op=('<'|'<='|'>'|'>=') expr     # Relational
    | expr op=('=='|'!=') expr             # Equality
    | expr '&&' expr                       # And
    | expr '||' expr                       # Or
    // Atoms (highest precedence)
    | callExpr                             # Call
    | ID                                   # Id
    | literal                              # Lit
    | '(' expr ')'                         # Paren
    ;

callExpr
    : ID '(' (expr (',' expr)* ','?)? ')'
    ;

// --- Primitives ---

type
    : 'Int' | 'Float' | 'Double' | 'Bool' | 'String' | 'Void'
    ;

literal
    : INT           # LitInt
    | FLOAT         # LitFloat
    | STRING        # LitStr
    | BOOL          # LitBool
    ;

// --- Lexer Rules (Tokens) ---

FUNC    : 'func';
RETURN  : 'return';
LET     : 'let';
VAR     : 'var';
IF      : 'if';
ELSE    : 'else';
WHILE   : 'while';
FOR     : 'for';
IN      : 'in';
SWITCH  : 'switch';
CASE    : 'case';
DEFAULT : 'default';
PRINT   : 'print';
RANGE   : 'range';

BOOL    : 'true' | 'false';

// Operators
PLUS    : '+';
MINUS   : '-';
MULT    : '*';
DIV     : '/';
POW     : '**';
NOT     : '!';
AND     : '&&';
OR      : '||';

EQ      : '==';
NEQ     : '!=';
LT      : '<';
LTE     : '<=';
GT      : '>';
GTE     : '>=';

ASSIGN  : '=';
ARROW   : '->';
COLON   : ':';
SEMI    : ';';
COMMA   : ',';
LPAREN  : '(';
RPAREN  : ')';
LBRACE  : '{';
RBRACE  : '}';

// Literals
INT     : [0-9]+;
FLOAT   : [0-9]+ '.' [0-9]+;
ID      : [a-zA-Z_] [a-zA-Z0-9_]*;
STRING  : '"' .*? '"';

// Skip spaces and comments
WS      : [ \t\r\n]+ -> skip;
COMMENT : '//' ~[\r\n]* -> skip;
