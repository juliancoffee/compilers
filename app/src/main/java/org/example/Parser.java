package org.example;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.text.MessageFormat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Parser {
    /*
     * Parser state
     */
    int numToken = 0;
    Optional<Integer> checkpointToken = Optional.empty();

    /*
     * Used with rollbacks, check updateError
     */
    public Optional<? extends RuntimeException> biggestError = Optional.empty();
    Optional<Integer> biggestChain = Optional.empty();

    /*
     * Output
     */
    public ST parseTree = new ST(new ArrayList<>());

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
    ArrayList<ST.Expression> parseArgsFragment() {
        // expect `(`
        this.consumeSymbol("(");

        var exprs = new ArrayList<ST.Expression>();

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
                        throw fail(span, token, "',' can only follow argument");
                    }
                }
                case Pair(var span, Token token) -> {
                    // There and Back Again
                    this.backPair();
                    exprs.add(this.parseExpression());
                    allow_comma = true;
                }
            }
        }


        return exprs;
    }

    ST.PrintStmt parsePrintStmt() {
        log.debug("parse print");

        // parse arguments
        var args = parseArgsFragment();

        // expect `;`
        this.consumeSymbol(";");

        return new ST.PrintStmt(args);
    }

    ST.AssignStmt parseAssignStmt(String ident) {
        log.debug("parse assign");

        // expect `=`
        this.consumeSymbol("=");

        // parse expression
        var expr = this.parseExpression();

        // expect `;`
        this.consumeSymbol(";");
        return new ST.AssignStmt(ident, expr);
    }

    ST.FuncCallStmt parseFuncCallStmt(String ident) {
        log.debug("parse func call");

        // parse args
        var exprs = this.parseArgsFragment();

        // expect `;`
        this.consumeSymbol(";");

        return new ST.FuncCallStmt(ident, exprs);
    }

    ArrayList<ST.Stmt> parseBlock() {
        log.debug("parse block");

        // expect `{` to open block
        this.consumeSymbol("{");

        var stmts = new ArrayList<ST.Stmt>();
        Pair<Pair<Integer, Integer>, Token> nextToken;
        while (true) {
            nextToken = this.nextPair();
            switch (nextToken) {
                case Pair(var span, Keyword token) -> {
                    switch (token.keyword()) {
                        case "let" -> stmts.add(this.parseLetStmt());
                        case "print" -> stmts.add(this.parsePrintStmt());
                        default -> throw fail(
                            span,
                            token,
                            "only `let` and `print` keywords " +
                            "are allowed at the start"
                        );
                    }
                }
                case Pair(var span, Ident ident) -> {
                    // NOTE: tada! backtracking
                    this.checkpoint();

                    try {
                        var stmt = this.parseAssignStmt(ident.ident());
                        stmts.add(this.commit(stmt));

                        continue;
                    } catch (RuntimeException e) {
                        this.rollback(e);
                    }

                    // NOTE: last one without rollbacks
                    var stmt = this.parseFuncCallStmt(ident.ident());
                    stmts.add(this.commit(stmt));
                }
                // if got `}`, collect and return
                case Pair(var span, Symbol token)
                    when token.equals(new Symbol("}")) -> {
                    return stmts;
                }
                case Pair(var span, Token token) -> throw fail(
                    span, token, "expected '}'"
                );
            }
            log.debug("parsed statement " + stmts.size());
        }
    }

    ST.FuncStmt parseFuncStmt() {
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

        return new ST.FuncStmt(name, stmts);
    }

    ST.Expression parseFuncCallExpr(String ident) {
        log.debug("parse fun call");
        var exprs = parseArgsFragment();
        return new ST.FuncCallExpr(ident, exprs);
    }

    ST.Expression parseIdentExpr(String ident) {
        log.debug("parse ident");
        return new ST.IdentExpr(ident);
    }

    ST.Expression parseFactor() {
        log.debug("parse factor");

        var nextToken = this.nextPair();
        switch (nextToken) {
            case Pair(var span, Ident(String ident)) -> {
                this.checkpoint();
                try {
                    var expr = this.commit(this.parseFuncCallExpr(ident));
                    return expr;
                } catch (RuntimeException e) {
                    this.rollback(e);
                }

                return this.commit(this.parseIdentExpr(ident));
            }
            case Pair(var span, IntLiteral token) -> {
                var val = Integer.parseInt(token.intLiteral());
                return new ST.IntLiteralExpr(val);
            }
            // not ident, not an int, fail
            case Pair(var span, Token token) -> throw fail(
                span, token, "expected IntLiteral or Ident"
            );
        }
    }

    ST.Expression parseArithExpr() {
        log.debug("parse arith expr");
        var a = this.parseFactor();

        ST.Expression binOp = a;
        Pair<Pair<Integer, Integer>, Token> nextToken;

        boolean close = false;
        while (!close) {
            nextToken = this.nextPair();
            switch (nextToken) {
                case Pair(var span, Symbol token) when token.isSym("+") -> {
                    var b = this.parseFactor();
                    binOp = new ST.BinOpExpr(ST.BIN_OP.ADD, binOp, b);
                }
                case Pair(var span, Symbol token) when token.isSym("-") -> {
                    var b = this.parseFactor();
                    binOp = new ST.BinOpExpr(ST.BIN_OP.SUB, binOp, b);
                }
                case Pair(var span, Token token) -> {
                    this.backPair();
                    return binOp;
                }
            }
        }

        return binOp;
    }

    ST.Expression parseExpression() {
        log.debug("parse expr");

        return this.parseArithExpr();
    }

    ST.LetStmt parseLetStmt() {
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

        return new ST.LetStmt(name, expr);
    }

    ST.TopLevelStmt parseTopStmt() {
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


    void backPair() {
        log.debug("[back again]");
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

    <T> T commit(T object) {
        this.checkpointToken = Optional.empty();
        log.debug("[success] " + object);

        return object;
    }

    <E extends RuntimeException> void updateError(E e, int checkpointToken) {
        var currentLen = this.numToken - checkpointToken;

        if (this.biggestChain.map(chain -> currentLen > chain).orElse(true)) {
            this.biggestError = Optional.of(e);
            this.biggestChain = Optional.of(currentLen);
        }
    }

    <E extends RuntimeException> void rollback(E e) {
        log.debug(e);

        var checkpointToken = this
            .checkpointToken
            .orElseThrow(() -> new RuntimeException("rollback with no checkpoint"));
        this.updateError(e, checkpointToken);

        log.debug("rollback to " + this.checkpointToken);
        this.numToken = checkpointToken;
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
            throw fail(span, token, "expected Ident");
        }
    }

    void consumeToken(Token expected) {
        var next = this.nextPair();
        var span = next.first();
        var token = next.second();
        if (!token.equals(expected)) {
            throw fail(span, token, "expected " + expected);
        }
    }

    RuntimeException fail(Pair<Integer, Integer> span, Token token) {
        return fail(span, token, "re-read the source code at that location");
    }

    RuntimeException fail(Pair<Integer, Integer> span, Token token, String hint) {
        throw new RuntimeException(
            MessageFormat.format("""
> At {0} unexpected token: {1}.
> Hint: {2}
""",
            formatSpan(span), token, hint)
        );
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
