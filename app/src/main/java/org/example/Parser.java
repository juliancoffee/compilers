package org.example;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.text.MessageFormat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

record Checkpoint(
    Integer checkpoint,
    Optional<RuntimeException> biggestError,
    Integer maxRollbackLen,
    Integer maxRollbackToken
) {
    Checkpoint updateError(
        RuntimeException newError, int checkpointToken, int numToken
    ) {
        var newRollbackLen = numToken - checkpointToken;
        if (this.maxRollbackLen > newRollbackLen) {
            return this;
        }
        if (this.maxRollbackToken > numToken) {
            return this;
        }

        var newRollbackToken = numToken;
        return new Checkpoint(
            checkpoint,
            Optional.of(newError),
            newRollbackLen,
            newRollbackToken
        );
    }
}

public class Parser {
    /*
     * Parser state
     */
    int numToken = 0;
    ArrayList<Checkpoint> checkpoints = new ArrayList<>();

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

    ST.ReturnStmt parseReturnStmt() {
        var expr = this.parseExpression();

        this.consumeSymbol(";");

        return new ST.ReturnStmt(expr);
    }

    ST.ForStmt parseForStmt() {
        var iterName = this.consumeIdent();

        this.consumeKeyword("in");

        ST.Iter iterable;

        var nextToken = this.nextPair();
        switch (nextToken) {
            case Pair(var span, Keyword keyword)
                when keyword.isKeyword("range") -> {
                this.consumeSymbol("(");
                var from = this.consumeIntLiteral();
                this.consumeSymbol(",");
                var to = this.consumeIntLiteral();
                this.consumeSymbol(",");
                var step = this.consumeIntLiteral();
                // we could have trailing commas here, but we don't
                this.consumeSymbol(")");
                iterable = new ST.RangeExpr(from, to, step);
            }
            default -> {
                this.backPair();
                iterable = this.parseExpression();
            }
        }

        var stmts = this.parseBlock();

        return new ST.ForStmt(iterName, iterable, stmts);
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
                        case "var" -> stmts.add(this.parseVarStmt());
                        case "print" -> stmts.add(this.parsePrintStmt());
                        case "return" -> stmts.add(this.parseReturnStmt());
                        case "for" -> stmts.add(this.parseForStmt());
                        default -> {
                            var keywords = Set.of(
                                "let", "var", "print", "return"
                            );
                            throw fail(
                                span,
                                token,
                                "keywords expected: " + keywords
                            );
                        }
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

    ST.TY parseType() {
        var nextToken = this.nextPair();

        return switch (nextToken) {
            case Pair(var span, Keyword keyword) -> switch (keyword.keyword()) {
                case "Int" -> ST.TY.INT;
                case "Double" -> ST.TY.FLOAT;
                case "Bool" -> ST.TY.BOOL;
                case "String" -> ST.TY.STRING;
                case "Void" -> ST.TY.VOID;
                default -> throw fail(
                    span,
                    keyword,
                    "expected a type"
                );
            };
            case Pair(var span, Token token) -> throw fail(
                span,
                token,
                "expected a type"
            );
        };
    }

    Pair<String, ST.TY> parseParamSpec() {
        // expect ident
        var name = this.consumeIdent();

        // expect ':'
        this.consumeSymbol(":");

        var type = this.parseType();

        return new Pair<>(name, type);
    }

    ArrayList<Pair<String, ST.TY>> parseParamList() {
        // expect `(`
        this.consumeSymbol("(");

        var paramList = new ArrayList<Pair<String, ST.TY>>();

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
                // if got ',', check if it's allowed (if it follows paramspec)
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
                    paramList.add(this.parseParamSpec());
                    allow_comma = true;
                }
            }
        }

        return paramList;
    }

    ST.FuncStmt parseFuncStmt() {
        log.debug("parse func stmt");

        // expect ident
        var name = this.consumeIdent();

        // parse parameter list
        var paramList = this.parseParamList();

        // try an optional type arrow
        Optional<ST.TY> returnType = Optional.empty();
        var nextToken = this.nextPair();
        switch (nextToken) {
            case Pair(var span, Symbol token)
                when token.isSym("->") -> {

                returnType = Optional.of(this.parseType());
            }
            default -> this.backPair();
        }

        var stmts = this.parseBlock();
        return new ST.FuncStmt(name, paramList, returnType, stmts);
    }

    ST.Expression parseFuncCallExpr(String ident) {
        log.debug("parse fun call");
        var exprs = this.parseArgsFragment();
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
            case Pair(var span, FloatLiteral token) -> {
                var val = Double.parseDouble(token.floatLiteral());
                return new ST.FloatLiteralExpr(val);
            }
            case Pair(var span, StrLiteral token) -> {
                return new ST.StrLiteralExpr(token.strLiteral());
            }
            /*
             * NOTE: we don't parse BoolLiteral here, it should go with RelExpr
             */
            // not ident, not an int, fail
            case Pair(var span, Token token) -> throw fail(
                span, token, "expected Literal or Ident"
            );
        }
    }

    ST.Expression parseTerm() {
        log.debug("parse term expr (* and /)");
        var a = this.parseFactor();

        ST.Expression binOp = a;
        Pair<Pair<Integer, Integer>, Token> nextToken;

        boolean close = false;
        while (!close) {
            nextToken = this.nextPair();
            switch (nextToken) {
                case Pair(var span, Symbol token) when token.isSym("*") -> {
                    var b = this.parseFactor();
                    binOp = new ST.BinOpExpr(ST.BIN_OP.MUL, binOp, b);
                }
                case Pair(var span, Symbol token) when token.isSym("/") -> {
                    var b = this.parseFactor();
                    binOp = new ST.BinOpExpr(ST.BIN_OP.DIV, binOp, b);
                }
                case Pair(var span, Token token) -> {
                    this.backPair();
                    return binOp;
                }
            }
        }

        return binOp;
    }

    ST.Expression parseArithExpr() {
        log.debug("parse arith expr");
        var a = this.parseTerm();

        ST.Expression binOp = a;
        Pair<Pair<Integer, Integer>, Token> nextToken;

        boolean close = false;
        while (!close) {
            nextToken = this.nextPair();
            switch (nextToken) {
                case Pair(var span, Symbol token) when token.isSym("+") -> {
                    var b = this.parseTerm();
                    binOp = new ST.BinOpExpr(ST.BIN_OP.ADD, binOp, b);
                }
                case Pair(var span, Symbol token) when token.isSym("-") -> {
                    var b = this.parseTerm();
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

    @FunctionalInterface
    interface DeclFactory {
        ST.Stmt build(String name, Optional<ST.TY> type, ST.Expression expr);
    }

    ST.Stmt parseDeclStmt(DeclFactory builder) {
        // expect ident
        var name = this.consumeIdent();

        // try an optional type hint
        Optional<ST.TY> varType = Optional.empty();
        var nextToken = this.nextPair();
        switch (nextToken) {
            case Pair(var span, Symbol token) when token.isSym(":") -> {
                varType = Optional.of(this.parseType());
            }
            default -> this.backPair();
        }

        // expect `=`
        this.consumeSymbol("=");

        // get expression
        var expr = this.parseExpression();

        // let must end with `;`
        this.consumeSymbol(";");

        return builder.build(name, varType, expr);
    }

    ST.VarStmt parseVarStmt() {
        log.debug("parse var stmt");

        return (ST.VarStmt) this.parseDeclStmt(
            (name, varType, expr) -> new ST.VarStmt(name, varType, expr)
        );
    }

    ST.LetStmt parseLetStmt() {
        log.debug("parse let stmt");

        return (ST.LetStmt) this.parseDeclStmt(
            (name, varType, expr) -> new ST.LetStmt(name, varType, expr)
        );
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

    int checkpointDepth() {
        var checkpointStackDepth = this.checkpoints.size() - 1;
        assert checkpointStackDepth >= 0 : "checkpoint depth is negative";

        return checkpointStackDepth;
    }

    Optional biggestError() {
        if (this.checkpoints.isEmpty()) {
            return Optional.empty();
        }

        var biggestError = this
            .checkpoints
            .get(this.checkpointDepth())
            .biggestError();

        return biggestError;
    }

    // NOTE: every checkpoint must end with commit
    void checkpoint() {
        var checkpoint = new Checkpoint(this.numToken, Optional.empty(), 0, 0);
        this.checkpoints.add(checkpoint);

        log.debug("checkpoints to " + checkpoint);
    }

    <T> T commit(T object) {
        this.checkpoints.remove(this.checkpointDepth());

        log.debug("[success] " + object);

        return object;
    }

    void updateError(RuntimeException e, int checkpointToken) {
        this.checkpoints.set(
            this.checkpointDepth(),
            this
                .checkpoints
                .get(this.checkpointDepth())
                .updateError(e, checkpointToken, this.numToken));
    }

    void rollback(RuntimeException e) {
        log.debug(e);

        var checkpoint = this
            .checkpoints
            .get(this.checkpointDepth())
            .checkpoint();
        this.updateError(e, checkpoint);

        log.debug(String.format("rollback to [%s]", checkpoint));
        this.numToken = checkpoint;
    }

    void consumeSymbol(String symbol) {
        this.consumeToken(new Symbol(symbol));
    }

    void consumeKeyword(String keyword) {
        this.consumeToken(new Keyword(keyword));
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

    Integer consumeIntLiteral() {
        var next = this.nextPair();
        var span = next.first();
        var token = next.second();
        if (token instanceof IntLiteral(String intLiteral)) {
            return Integer.parseInt(intLiteral);
        } else {
            throw fail(span, token, "expected IntLiteral");
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
