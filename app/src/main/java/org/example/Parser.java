package org.example;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
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
    public ST parseTree = new ST(new ArrayList<>(), new ArrayList<>());

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

    ST.WhileStmt parseWhileStmt() {
        log.debug("parse while stmt");

        var cond = this.parseExpression();
        var block = this.parseBlock();

        return new ST.WhileStmt(cond, block);
    }

    ST.IfStmt parseIfStmt() {
        log.debug("parse if stmt");

        var cond = this.parseExpression();
        var thenBlock = this.parseBlock();

        Optional<ST.Block> elseBlock = Optional.empty();

        var next = this.nextPair();
        switch (next) {
            case Pair(var span, Keyword kw) when kw.isKeyword("else") -> {
                elseBlock = Optional.of(this.parseBlock());
            }
            default -> this.backPair();
        }

        return new ST.IfStmt(cond, thenBlock, elseBlock);
    }

    ST.SwitchStmt parseSwitchStmt() {
        log.debug("parse switch stmt");

        var expr = this.parseExpression();
        this.consumeSymbol("{");

        var cases = new ArrayList<ST.CaseStmt>();

        while (true) {
            var next = this.nextPair();
            switch (next) {
                case Pair(var span, Keyword kw) when kw.isKeyword("case") -> {
                    var comparator = this.parseComparator();
                    var block = this.parseBlock();
                    cases.add(new ST.ValueCase(comparator, block));
                }
                case Pair(var span, Keyword kw) when kw.isKeyword("default") -> {
                    var block = this.parseBlock();
                    cases.add(new ST.DefaultCase(block));
                }
                case Pair(var span, Symbol s) when s.isSym("}") -> {
                    return new ST.SwitchStmt(expr, cases);
                }
                case Pair(var span, Token token) -> throw fail(span, token, "expected case/default or '}'");
            }
        }
    }
    // parse 1const in switch case
    ST.LiteralExpr parseSingleConst(Pair<Pair<Integer,Integer>, Token> tok) {
        switch (tok) {
            case Pair(var span, IntLiteral t) -> {
                return new ST.IntLiteralExpr(Integer.parseInt(t.intLiteral()));
            }
            case Pair(var span, FloatLiteral t) -> {
                return new ST.FloatLiteralExpr(Double.parseDouble(t.floatLiteral()));
            }
            case Pair(var span, StrLiteral t) -> {
                return new ST.StrLiteralExpr(t.strLiteral());
            }
            case Pair(var span, Keyword kw) when kw.isKeyword("true") || kw.isKeyword("false") -> {
                return new ST.BoolLiteralExpr(kw.isKeyword("true"));
            }
            default -> throw fail(tok.first(), tok.second(), "expected Const");
        }
    }

    ST.Comparator parseComparator() {
        var next = this.nextPair();
        ArrayList<ST.Expression> seq = new ArrayList<>();

        switch (next) {
            case Pair(var span, Keyword kw) when kw.isKeyword("range") -> {
                this.consumeSymbol("(");
                int from = this.consumeIntLiteral();
                this.consumeSymbol(",");
                int to = this.consumeIntLiteral();
                this.consumeSymbol(")");
                return new ST.RangeComp(from, to);
            }
            case Pair(var span, var t) -> {
                // first el
                seq.add(parseSingleConst(next));

                // looking for ','
                while (true) {
                    var maybeComma = this.nextPair();
                    if (maybeComma == null) break;

                    switch (maybeComma) {
                        case Pair(var s, Symbol sym) when sym.isSym(",") -> {
                            var tok = this.nextPair();
                            seq.add(parseSingleConst(tok));
                            continue;
                        }
                        default -> {
                            // no comma
                            this.backPair();
                        }
                    }
                    break;
                }

                // its a sequel if we found ','
                if (seq.size() == 1) {
                    return new ST.ConstComp(seq.getFirst());
                } else {
                    return new ST.SeqComp(seq);
                }
            }
            default -> throw fail(next.first(), next.second(), "invalid comparator");
        }
    }

    ST.Block parseBlock() {
        log.debug("parse block");

        // expect `{` to open block
        this.consumeSymbol("{");

        var stmts = new ST.Block(new ArrayList<ST.Stmt>(), new ArrayList<>());
        Pair<Pair<Integer, Integer>, Token> nextToken;

        BiConsumer<ST.Stmt, Pair<Integer, Integer>> register = (
            var stmt,
            var startSpan
        ) -> {
            var endSpan = this.lastSpan();

            stmts.add(stmt, startSpan, endSpan);
        };

        while (true) {
            nextToken = this.nextPair();
            switch (nextToken) {
                case Pair(var span, Keyword token) -> {
                    switch (token.keyword()) {
                        case "let" -> register.accept(this.parseLetStmt(), span);
                        case "var" -> register.accept(this.parseVarStmt(), span);
                        case "print" -> register.accept(this.parsePrintStmt(), span);
                        case "return" -> register.accept(this.parseReturnStmt(), span);
                        case "for" -> register.accept(this.parseForStmt(), span);
                        case "while" -> register.accept(this.parseWhileStmt(), span);
                        case "if" -> register.accept(this.parseIfStmt(), span);
                        case "switch" -> register.accept(this.parseSwitchStmt(), span);
                        default -> {
                            var keywords = Set.of(
                                "let", "var", "print", "return", "while", "if", "switch"
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
                        register.accept(this.commit(stmt), span);

                        continue;
                    } catch (RuntimeException e) {
                        this.rollback(e);
                    }

                    // NOTE: last one without rollbacks
                    var stmt = this.parseFuncCallStmt(ident.ident());
                    register.accept(this.commit(stmt), span);
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
            log.debug("parsed statement " + stmts.stmts().size());
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

//    ST.Expression parseFactor() {
//        log.debug("parse factor");
//
//        var nextToken = this.nextPair();
//        switch (nextToken) {
//            case Pair(var span, Ident(String ident)) -> {
//                this.checkpoint();
//                try {
//                    var expr = this.commit(this.parseFuncCallExpr(ident));
//                    return expr;
//                } catch (RuntimeException e) {
//                    this.rollback(e);
//                }
//
//                return this.commit(this.parseIdentExpr(ident));
//            }
//            case Pair(var span, IntLiteral token) -> {
//                var val = Integer.parseInt(token.intLiteral());
//                return new ST.IntLiteralExpr(val);
//            }
//            case Pair(var span, FloatLiteral token) -> {
//                var val = Double.parseDouble(token.floatLiteral());
//                return new ST.FloatLiteralExpr(val);
//            }
//            case Pair(var span, StrLiteral token) -> {
//                return new ST.StrLiteralExpr(token.strLiteral());
//            }
//            /*
//             * NOTE: we don't parse BoolLiteral here, it should go with RelExpr
//             */
//            // not ident, not an int, fail
//            case Pair(var span, Token token) -> throw fail(
//                span, token, "expected Literal or Ident"
//            );
//        }
//    }

    ST.Expression parseFactor() { //with unary operators
        log.debug("parse factor");

        var nextToken = this.nextPair();
        switch (nextToken) {
            case Pair(var span, Symbol s) when s.isSym("+") -> {
                var factor = parseFactor();
                return new ST.UnaryOpExpr(ST.UNARY_OP.PLUS, factor);
            }
            case Pair(var span, Symbol s) when s.isSym("-") -> {
                var factor = parseFactor();
                return new ST.UnaryOpExpr(ST.UNARY_OP.MINUS, factor);
            }
            case Pair(var span, Symbol s) when s.isSym("!") -> {
                var factor = parseFactor();
                return new ST.UnaryOpExpr(ST.UNARY_OP.NOT, factor);
            }
            case Pair(var span, Symbol s) when s.isSym("(") -> {
                var expr = this.parseExpression();
                this.consumeSymbol(")");
                return expr;
            }
            case Pair(var span, Keyword kw) when kw.isKeyword("true") || kw.isKeyword("false") -> {
                return new ST.BoolLiteralExpr(kw.isKeyword("true"));
            } // for relops like flag==true
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
            case Pair(var span, IntLiteral t) -> {
                return new ST.IntLiteralExpr(Integer.parseInt(t.intLiteral()));
            }
            case Pair(var span, FloatLiteral t) -> {
                return new ST.FloatLiteralExpr(Double.parseDouble(t.floatLiteral()));
            }
            case Pair(var span, StrLiteral t) -> {
                return new ST.StrLiteralExpr(t.strLiteral());
            }
            default -> throw fail(nextToken.first(), nextToken.second(), "expected Factor");
        }
    }

    ST.Expression parsePower() {
        log.debug("parse power expr");

        ST.Expression left = this.parseFactor();

        var next = this.nextPair();
        if (next != null && next.second() instanceof Symbol s && s.isSym("**")) {
            // recursively parse the right part
            ST.Expression right = this.parsePower();
            return new ST.BinOpExpr(ST.BIN_OP.POW, left, right);
        }

        if (next != null) this.backPair();
        return left;
    }

    ST.Expression parseTerm() {
        log.debug("parse term expr (* and /)");
        ST.Expression left = parsePower();

        while (true) {
            var next = this.nextPair();
            if (next == null) break;

            switch (next.second()) {
                case Symbol s when s.isSym("*") -> {
                    ST.Expression right = parsePower();
                    left = new ST.BinOpExpr(ST.BIN_OP.MUL, left, right);
                }
                case Symbol s when s.isSym("/") -> {
                    ST.Expression right = parsePower();
                    left = new ST.BinOpExpr(ST.BIN_OP.DIV, left, right);
                }
                default -> {
                    this.backPair();
                    return left;
                }
            }
        }

        return left;
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
    ST.Expression parseRelExpr() {
        log.debug("parse rel expr");
        var next = this.nextPair();

        // BoolConst
        if (next.second() instanceof Keyword kw && (kw.isKeyword("true") || kw.isKeyword("false"))) {
            boolean val = kw.isKeyword("true");
            return new ST.BoolLiteralExpr(val);
        }

        this.backPair();
        ST.Expression left = parseArithExpr();

        next = this.nextPair();
        if (next != null && next.second() instanceof Symbol sym) {
            ST.BIN_OP op;
            switch (sym.symbol()) {
                case "==" -> op = ST.BIN_OP.EQ;
                case "!=" -> op = ST.BIN_OP.NE;
                case "<"  -> op = ST.BIN_OP.LT;
                case "<=" -> op = ST.BIN_OP.LE;
                case ">"  -> op = ST.BIN_OP.GT;
                case ">=" -> op = ST.BIN_OP.GE;
                default -> {
                    this.backPair();
                    return left;
                }
            }
            ST.Expression right = parseArithExpr();
            return new ST.BinOpExpr(op, left, right);
        }

        if (next != null) this.backPair();
        return left;
    }

    ST.Expression parseLogicExpr() {
        log.debug("parse logic expr");

        ST.Expression left = this.parseRelExpr();
        while (true) {
            var next = this.nextPair();
            if (next == null) break;

            ST.BIN_OP op;
            switch (next.second()) {
                case Symbol s when s.isSym("&&") -> op = ST.BIN_OP.AND;
                case Symbol s when s.isSym("||") -> op = ST.BIN_OP.OR;
                default -> {
                    this.backPair();
                    return left;
                }
            }

            ST.Expression right = this.parseRelExpr();
            left = new ST.BinOpExpr(op, left, right);
        }
        return left;
    }


    ST.Expression parseExpression() {
        log.debug("parse expr");

        return this.parseLogicExpr();
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
            var span = this.lastSpan();
            var stmt = this.parseTopStmt();
            var endSpan = this.lastSpan();

            this.parseTree.add(stmt, span, endSpan);
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

    Pair<Integer, Integer> lastSpan() {
        int index;
        if (this.numToken == 0) {
            index = 0;
        } else {
            index = this.numToken - 1;
        }
        var token = _tokenList.get(index);
        return token.first();
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
