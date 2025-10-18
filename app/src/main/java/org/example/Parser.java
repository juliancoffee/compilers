package org.example;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.text.MessageFormat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

sealed interface TopLevelStmt extends Stmt
permits
    LetStmt, FuncStmt {}

sealed interface Expression
permits
    LiteralExpr, IdentExpr {}

sealed interface LiteralExpr extends Expression
permits
    IntLiteralExpr {}

sealed interface Stmt
permits
    TopLevelStmt, PrintStmt, AssignStmt, FuncCallStmt {}

// Program = { LetStmt | FuncStmt }
record ParseTree(ArrayList<TopLevelStmt> stmts) {}

// LetStmt = 'let' Ident '=' Expression ';'
record LetStmt(String letName, Expression expr) implements TopLevelStmt {}

// IntLiteralExpr = IntLiteral
record IntLiteralExpr(Integer intLiteral) implements LiteralExpr {};

// IdentExpr = IntLiteral
record IdentExpr(String identExpr) implements Expression {};

// FuncStmt = 'func' Ident '(' ')' Block
// Block = { Stmt }
// TODO: args
record FuncStmt(String funcName, ArrayList<Stmt> block) implements TopLevelStmt {}

// PrintStmt = 'print' ArgsFragment ';'
// ArgsFragment = '(' [ Expression { ',' Expression } [ ',' ] ] ')'
//
// Simply put, allows zero or more expressions, separated by comma.
// Comma optionally may be trailing.
record PrintStmt(ArrayList<Expression> printExprs) implements Stmt {}

// AssignStmt = Ident '=' Expression ';'
record AssignStmt(String assignIdent, Expression expr) implements Stmt {}

// FuncCallStmt = FuncCall ';'
// FuncCall = Ident ArgsFragment
record FuncCallStmt(String callIdent, ArrayList<Expression> args) implements Stmt {}

public class Parser {
    /*
     * Parser state
     */
    int numToken = 0;
    Optional<Integer> checkpointToken = Optional.empty();

    /*
     * Output
     */
    public ParseTree parseTree = new ParseTree(new ArrayList<>());

    /*
     * Parser data
     */
    ArrayList<Pair<Pair<Integer, Integer>, Token>> _tokenList;
    int tokenListLen;
    ArrayList<Integer> lineIndex;

    /*
     * Logger
     */
    private static final Logger log = LogManager.getLogger("parser");

    // Not a *real* parsing function, just a helper.
    // Parses arguments fragment for function call or print.
    //
    // Will return zero or more arguments
    ArrayList<Expression> parseArgsFragment() {
        // expect `(`
        this.consumeSymbol("(");

        var exprs = new ArrayList<Expression>();

        boolean close = false;
        Pair<Pair<Integer, Integer>, Token> nextToken;
        boolean allow_comma = false;
        while (!close) {
            nextToken = this.nextPair();
            switch (nextToken) {
                // if got `)`, finish with arguments
                case Pair(var span, Symbol token)
                    when token.equals(new Symbol(")")) -> {
                    close = true;
                }
                // if got ',', check if it's allowed (if it follows expression)
                case Pair(var span, Symbol token)
                    when token.equals(new Symbol(",")) -> {
                    if (allow_comma) {
                        allow_comma = false;
                    } else {
                        throw fail(span, token);
                    }
                }
                case Pair(var span, Token token) -> {
                    // Back and There again
                    this.backPair();
                    exprs.add(this.parseExpression());
                    allow_comma = true;
                }
            }
        }


        return exprs;
    }

    PrintStmt parsePrintStmt() {
        log.debug("parse print");

        // parse arguments
        var args = parseArgsFragment();

        // expect `;`
        this.consumeSymbol(";");

        return new PrintStmt(args);
    }

    AssignStmt parseAssignStmt(String ident) {
        log.debug("parse assign");

        // expect `=`
        this.consumeSymbol("=");

        // parse expression
        var expr = this.parseExpression();

        // expect `;`
        this.consumeSymbol(";");
        return new AssignStmt(ident, expr);
    }

    FuncCallStmt parseFuncCallStmt(String ident) {
        log.debug("parse func call");

        // parse args
        var exprs = this.parseArgsFragment();

        // expect `;`
        this.consumeSymbol(";");

        return new FuncCallStmt(ident, exprs);
    }

    ArrayList<Stmt> parseBlock() {
        log.debug("parse block");

        // expect `{` to open block
        this.consumeSymbol("{");

        var stmts = new ArrayList<Stmt>();
        Pair<Pair<Integer, Integer>, Token> nextToken;
        while (true) {
            nextToken = this.nextPair();
            switch (nextToken) {
                case Pair(var span, Keyword token) -> {
                    switch (token.keyword()) {
                        case "let" -> stmts.add(this.parseLetStmt());
                        case "print" -> stmts.add(this.parsePrintStmt());
                        default -> throw fail(span, token);
                    }
                }
                case Pair(var span, Ident ident) -> {
                    // NOTE: tada! backtracking
                    this.checkpoint();

                    try {
                        stmts.add(this.parseAssignStmt(ident.ident()));
                        // NOTE: don't forget to commit
                        this.commit();
                        continue;
                    } catch (RuntimeException e) {
                        this.rollback();
                    }

                    // NOTE: last one without rollbacks
                    stmts.add(this.parseFuncCallStmt(ident.ident()));
                    this.commit();
                }
                // if got `}`, collect and return
                case Pair(var span, Symbol token)
                    when token.equals(new Symbol("}")) -> {
                    return stmts;
                }
                case Pair(var span, Token token) -> throw fail(span, token);
            }
            log.debug("parsed statement " + stmts.size());
        }
    }

    FuncStmt parseFuncStmt() {
        log.debug("parse func stmt");

        // expect ident
        var name = this.consumeIdent();

        // expect `(`
        this.consumeSymbol("(");

        /*
         * TODO: more
         */

        // expect `)`
        this.consumeSymbol(")");

        /*
         * TODO: parse *optional* type arrow
         */
        this.consumeSymbol("->");
        this.nextPair();

        var stmts = this.parseBlock();

        return new FuncStmt(name, stmts);
    }

    Expression parseExpression() {
        log.debug("parse expr");

        Pair<Pair<Integer, Integer>, Token> nextToken;
        nextToken = this.nextPair();
        switch (nextToken) {
            case Pair(var span, Ident token) -> {
                return new IdentExpr(token.ident());
            }
            case Pair(var span, IntLiteral token) -> {
                var val = Integer.parseInt(token.intLiteral());
                return new IntLiteralExpr(val);
            }
            // not ident, not an int, fail
            case Pair(var span, Token token) -> throw fail(span, token);
        }
    }

    LetStmt parseLetStmt() {
        log.debug("parse let stmt");

        // expect ident
        var name = this.consumeIdent();

        /*
         * TODO: *optional* type
         */

        // expect `=`
        this.consumeSymbol("=");

        // get expression
        var expr = this.parseExpression();

        // let must end with `;`
        this.consumeSymbol(";");

        return new LetStmt(name, expr);
    }

    TopLevelStmt parseTopStmt() {
        log.debug("parse top stmt");

        var nextToken = this.nextPair();
        switch (nextToken) {
            // let keyword, parse let statement
            case Pair(var span, Keyword token) -> {
                switch (token.keyword()) {
                    case "let" -> { return this.parseLetStmt(); }
                    case "func" -> { return this.parseFuncStmt(); }
                    default -> throw fail(span, token);
                }
            }
            // not let, nor func, error
            case Pair(var span, Token token) -> throw fail(span, token);
        }
    }


    void parseTopStatementList() {
        log.debug("parse top stmt's list");

        while (this.numToken < tokenListLen) {
            this.parseTree.stmts().add(this.parseTopStmt());
        }
    }

    public void parse() {
        log.debug("parse prog");

        this.parseTopStatementList();
    }

    RuntimeException fail(Pair<Integer, Integer> span, Token token) {
        throw new RuntimeException(
            "at " + formatSpan(span) + " unexpected token: " + token
        );
    }

    void backPair() {
        this.numToken -= 1;
    }

    Pair<Pair<Integer, Integer>, Token> nextPair() {
        try {
            var token = _tokenList.get(this.numToken);
            this.numToken += 1;
            log.debug("" + "[" + this.numToken +"] " + formatPair(token));
            return token;
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    // NOTE: every checkpoint must end with commit
    void checkpoint() {
        this.checkpointToken = Optional.of(this.numToken);
        log.debug("checkpoint to " + this.checkpointToken);
    }

    void commit() {
        this.checkpointToken = Optional.empty();
    }

    void rollback() {
        log.debug("rollback to " + this.checkpointToken);
        this.numToken = this
            .checkpointToken
            .orElseThrow(() -> new RuntimeException("rollback with no checkpoint"));
    }

    void consumeSymbol(String symbol) {
        this.consumeToken(new Symbol(symbol));
    }

    String consumeIdent() {
        var next = this.nextPair();
        var span = next.first();
        var token = next.second();
        if (token instanceof Ident(String ident)) {
            return ident;
        } else {
            throw new RuntimeException(
                MessageFormat.format("""

> At {0} unexpected token {1}.
> Hint: expected Ident
""",
                    formatSpan(span), token)
            );
        }
    }

    void consumeToken(Token expected) {
        var next = this.nextPair();
        var span = next.first();
        var token = next.second();
        if (!token.equals(expected)) {
            throw new RuntimeException(
                MessageFormat.format("""

> At {0} unexpected token {1}.
> Hint: expected {2}
""",
                    formatSpan(span), token, expected)
            );
        }
    }

    String formatSpan(Pair<Integer, Integer> span) {
        return SpanUtils.formatSpan(span, this.lineIndex);
    }

    String formatPair(Pair<Pair<Integer, Integer>, Token> pair) {
        var span = pair.first();
        var token = pair.second();
        String formattedSpan = SpanUtils.formatSpan(span, this.lineIndex);

        return String.format("%s at (%s)", token, formattedSpan);
    }

    public Parser(
        TreeMap<Pair<Integer, Integer>, Token> tokenTable,
        ArrayList<Integer> lineIndex
    ) {
        this._tokenList = tokenTable
            .entrySet()
            .stream()
            .map(kv -> new Pair<>(kv.getKey(), kv.getValue()))
            .collect(Collectors.toCollection(ArrayList::new));
        this.lineIndex = lineIndex;
        this.tokenListLen = this._tokenList.size() - 1;
    }
}
