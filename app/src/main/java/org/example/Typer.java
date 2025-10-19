package org.example;

import java.util.*;
import java.text.MessageFormat;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class Typer {
    /*
     * Type state
     */
    Integer nextId = 0;

    /*
     * Typer data
     */
    ST parseTree;
    ArrayList<Integer> lineIndex;

    /* Typer output
     */
    public IR ir = new IR(
        IR.defaultOperatorMap(),
        new IR.Scope(
            // parent scope
            null,
            // global namespace
            "",
            // name mappings
            new HashMap<String, IR.Var>(),
            // for vars and scopes
            new ArrayList<IR.Entry>()
        )
    );

    /*
     * Logger
     */
    private static final Logger log = LogManager.getLogger("typer");

    IR.Var lookupRef(
        String ident, Pair<Integer, Integer> span, IR.Scope scope
    ) {
        var currentScope = scope;
        while (true) {
            var find = currentScope.varMapping().get(ident);
            if (find != null) {
                return find;
            }
            if (currentScope.parentScope() == null) {
                throw fail(
                    span,
                    "var <" + ident + "> can't be found",
                    "you might forgot to set it"
                );
            }
            currentScope = currentScope.parentScope();
        }
    }

    IR.TY typeCheckExpression(
        IR.Value expr, Pair<Integer, Integer> span, IR.Scope scope
    ) {
        return switch (expr) {
            case IR.Atom(var type, var value) -> type;
            case IR.Ref(var ident) -> {
                yield this.lookupRef(ident, span, scope).type();
            }
            case IR.Expr e -> {
                yield this.resolveExpr(e, span, scope);
            }
            case IR.Arg(var type) -> type;
        };
    }

    IR.TY resolveExpr(
        IR.Expr expr, Pair<Integer, Integer> span, IR.Scope scope
    ) {
        log.debug(expr);
        var types = expr
            .vars()
            .stream()
            .map(p -> p.type())
            .collect(Collectors.toCollection(ArrayList::new));
        var operator = this.ir.opStore().get(expr.op());
        log.debug(types);
        log.debug(operator);
        if (operator == null) {
            throw fail(
                span,
                "undefined function: " + expr.op(),
                "maybe you forgot to define it?"
            );
        }

        for (var alt : operator.alternatives()) {
            if (alt.argTypes().equals(types)) {
                return alt.returnType();
            }

            // if (alt.argTypes().size() == 2) {
            //     IR.TY req1 = alt.argTypes().get(0);
            //     IR.TY req2 = alt.argTypes().get(1);
            //     IR.TY got1 = types.get(0);
            //     IR.TY got2 = types.get(1);
            //
            //     // Перевірка 1: Обидва INT, потрібні FLOAT
            //     if (req1 == IR.TY.FLOAT && req2 == IR.TY.FLOAT && got1 == IR.TY.INT && got2 == IR.TY.INT) {
            //         return alt.returnType();
            //     }
            //
            //     // Перевірка 2: Змішані типи (наприклад, [INT, FLOAT] -> [FLOAT, FLOAT])
            //     if ((req1 == IR.TY.FLOAT && got1 == IR.TY.INT) || (req2 == IR.TY.FLOAT && got2 == IR.TY.INT)) {
            //         return alt.returnType();
            //     }
            // }
        }
        throw fail(
            span,
            "can't apply operator to parameters: " + expr.op(),
            "your parameters: " + types
        );
    }

    IR.TY toType(
        ST.Expression expr, Pair<Integer, Integer> span, IR.Scope scope
    ) {
        var value = this.toValue(expr, span, scope);
        return typeCheckExpression(value, span, scope);
    }

    IR.Var toVar(
        ST.Expression expr, Pair<Integer, Integer> span, IR.Scope scope
    ) {
        var value = this.toValue(expr, span, scope);
        var type = this.toType(expr, span, scope);

        return new IR.Var(value, type, span, false);
    }

    IR.Value toValue(
        ST.Expression expr, Pair<Integer, Integer> span, IR.Scope scope
    ) {
        return switch (expr) {
            case ST.IntLiteralExpr(var intLiteral) -> {
                yield new IR.Atom(IR.TY.INT, intLiteral.toString());
            }
            case ST.FloatLiteralExpr(var floatLiteral) -> {
                yield new IR.Atom(IR.TY.FLOAT, floatLiteral.toString());
            }
            case ST.StrLiteralExpr(var strLiteral) -> {
                yield new IR.Atom(IR.TY.STRING, strLiteral);
            }
            case ST.BoolLiteralExpr(var boolLiteral) -> {
                yield new IR.Atom(IR.TY.BOOL, boolLiteral.toString());
            }
            case ST.BinOpExpr(var op, var a, var b) -> {
                var aVar = this.toVar(a, span, scope);
                var bVar = this.toVar(b, span, scope);
                yield new IR.Expr(IR.binOpCode(op), new ArrayList<IR.Var>(
                    List.of(aVar, bVar)
                ));
            }
            case ST.UnaryOpExpr(var op, var a) -> {
                var aVar = this.toVar(a, span, scope);
                yield new IR.Expr(IR.unOpCode(op), new ArrayList<IR.Var>(
                    List.of(aVar)
                ));
            }
            case ST.IdentExpr(String identExpr) -> {
                yield new IR.Ref(identExpr);
            }
            case ST.FuncCallExpr(var callIdent, var args) -> {
                var funArgs = args
                    .stream()
                    .map((e) -> this.toVar(e, span, scope))
                    .collect(Collectors.toCollection(ArrayList::new));
                yield new IR.Expr(callIdent, funArgs);
            }
        };
    }

    void typeCheckNewVar(
        String name,
        Optional<ST.TY> type,
        ST.Expression expr,
        boolean mutable,
        Pair<Integer, Integer> span,
        IR.Scope scope
    ) {
        log.debug(
            String.format("typecheck new var: %s %s\n%s", name, type, expr)
        );
        log.debug(span);

        var newVar = new IR.NewVar(name);
        var value = this.toValue(expr, span, scope);

        var valueType = typeCheckExpression(value, span, scope);

        IR.TY resType;
        if (type.isPresent()) {
            var claimedType = IR.typeFromST(type.get());
            if (claimedType == valueType) {
                resType = valueType;
            } else {
                throw fail(
                    span,
                    "wrong type",
                    "expected: " + valueType + " specified: " + claimedType
                );
            }
        } else {
            resType = valueType;
        }

        var variable = new IR.Var(value, resType, span, mutable);
        scope.entries().add(newVar);
        scope.varMapping().put(name, variable);
    }

    void typeCheckBlock(
        ST.Block block,
        IR.Scope scope
    ) {
        var stmts = block.stmts().size();
        for (var idx = 0; idx < stmts; idx++) {
            var stmt = block.stmts().get(idx);
            var span = block.spans().get(idx);
            switch (stmt) {
                case ST.LetStmt(
                    var letName,
                    var letType,
                    var expr
                ) -> typeCheckNewVar(
                    letName, letType, expr, false, span, scope
                );
                case ST.VarStmt(
                    var letName,
                    var letType,
                    var expr
                ) -> typeCheckNewVar(
                    letName, letType, expr, true, span, scope
                );
                case ST.ReturnStmt(var expr) -> {
                    var value = this.toValue(expr, span, scope);
                    var type = this.typeCheckExpression(value, span, scope);
                    var fun = this.ir.opStore().get(scope.funcName());
                    for (var alt : fun.alternatives()) {
                        // FIXME: can't do typecasts
                        if (alt.returnType().equals(type)) {
                            return;
                        }
                    }
                    var types = fun
                        .alternatives()
                        .stream()
                        .map(p -> p.returnType())
                        .collect(Collectors.toCollection(ArrayList::new));
                    throw fail(
                        span,
                        "unexpected return type",
                        "expected: " + types + " got: " + type
                    );
                }
                case ST.AssignStmt(var ident, var expr) -> {
                    var value = this.toValue(expr, span, scope);
                    var type = this.typeCheckExpression(value, span, scope);
                    var tgt = this.lookupRef(ident, span, scope);
                    if (!tgt.mutable()) {
                        throw fail(
                            span,
                            "cant mutate " + ident,
                            "variable was defined at " + formatSpan(tgt.span())
                        );
                    }
                    if (tgt.type() != type) {
                        throw fail(
                            span,
                            "wrong assignment "
                                + ident
                                + " "
                                + typeMismatch(
                                    tgt.type(),
                                    type
                                ),
                            "variable was defined at " + formatSpan(tgt.span())
                        );
                    }
                }
                case ST.SwitchStmt s -> this.typeCheckSwitchStmt(
                    s, span, scope
                );
                case ST.PrintStmt(var exprs) -> {
                    for (var expr : exprs) {
                        var value = this.toValue(expr, span, scope);
                        var type = this.typeCheckExpression(value, span, scope);
                    }
                    var args = exprs
                        .stream()
                        .map((e) -> this.toVar(e, span, scope))
                        .collect(Collectors.toCollection(ArrayList::new));
                    var action = new IR.Expr("print", args);
                    scope.entries().add(new IR.Action(action));
                }
                case ST.IfStmt ifStmt -> {
                    typeCheckIfStmt(ifStmt, span, scope);
                }
                case ST.WhileStmt whileStmt -> {
                    typeCheckWhileStmt(whileStmt, span, scope);
                }
                case ST.ForStmt forStmt -> {
                    typeCheckForStmt(forStmt, span, scope);
                }
                case ST.FuncCallStmt(var callIdent, var args) -> {
                    for (var expr : args) {
                        var value = this.toValue(expr, span, scope);
                        var type = this.typeCheckExpression(value, span, scope);
                    }
                    var funArgs = args
                        .stream()
                        .map((e) -> this.toVar(e, span, scope))
                        .collect(Collectors.toCollection(ArrayList::new));

                    var action = new IR.Expr(callIdent, funArgs);
                    this.resolveExpr(action, span, scope);

                    scope.entries().add(new IR.Action(action));
                }
                case ST.FuncStmt s -> {
                    throw fail(span, "func can't be nested", "yes");
                }
            }
        }
    }

    String typeMismatch(IR.TY t1, IR.TY t2) {
        return String.format("%s != %s", t1, t2);
    }

    void typeCheckIfStmt(
            ST.IfStmt stmt,
            Pair<Integer, Integer> span,
            IR.Scope scope
    ) {
        IR.TY condType = this.toType(stmt.ifCond(), span, scope);
        if (condType != IR.TY.BOOL) {
            throw fail(span, "if condition must be BOOL", "got: " + condType);
        }
        // if
        IR.Scope thenScope = new IR.Scope(
            scope,
            scope.funcName(),
            new HashMap<>(),
            new ArrayList<>()
        );
        IR.Scoped scopedThen = new IR.Scoped(
            IR.SCOPE_KIND.IF_BRANCH,
            new ArrayList<>(),
            Optional.empty(),
            thenScope
        );
        scope.entries().add(scopedThen);

        //else
        typeCheckBlock(stmt.thenBlock(), thenScope);
        if (stmt.elseBlock().isPresent()) {
            IR.Scope elseScope = new IR.Scope(
                scope,
                scope.funcName(),
                new HashMap<>(),
                new ArrayList<>()
            );
            IR.Scoped scopedElse = new IR.Scoped(
                IR.SCOPE_KIND.ELSE_BRANCH,
                new ArrayList<>(),
                Optional.empty(),
                elseScope
            );
            scope.entries().add(scopedElse);

            typeCheckBlock(stmt.elseBlock().get(), elseScope);
        }
    }

    void typeCheckForStmt(
        ST.ForStmt stmt,
        Pair<Integer, Integer> span,
        IR.Scope scope
    ) {
        IR.TY iterType;
        switch (stmt.iterable()) {
            case ST.RangeExpr(var from, var to, var step) -> {
                iterType = IR.TY.INT;
            }
            case ST.Expression e -> {
                var type = this.toType(e, span, scope);
                if (type != IR.TY.STRING) {
                    throw fail(
                        span,
                        "for iterable must be String or range()",
                        "got: " + type
                    );
                }
                iterType = IR.TY.STRING;
            }
        }

        IR.Scope forScope = new IR.Scope(
            scope,
            scope.funcName(),
            new HashMap<>(),
            new ArrayList<>()
        );

        var forName = stmt.forIdent();
        IR.Scoped scopedFor = new IR.Scoped(
            IR.SCOPE_KIND.WHILE,
            new ArrayList<>(List.of(forName)),
            Optional.empty(),
            forScope
        );

        var arg = new IR.Var(
            new IR.Arg(iterType),
            iterType,
            span,
            false
        );
        forScope.varMapping().put(forName, arg);

        scope.entries().add(scopedFor);
        typeCheckBlock(stmt.block(), forScope);
    }

    void typeCheckWhileStmt(
        ST.WhileStmt stmt,
        Pair<Integer, Integer> span,
        IR.Scope scope
    ) {
        IR.TY condType = this.toType(stmt.whileCond(), span, scope);
        if (condType != IR.TY.BOOL) {
            throw fail(span, "while condition must be BOOL", "got: " + condType);
        }

        IR.Scope whileScope = new IR.Scope(
            scope,
            scope.funcName(),
            new HashMap<>(),
            new ArrayList<>()
        );

        IR.Scoped scopedWhile = new IR.Scoped(
            IR.SCOPE_KIND.WHILE,
            new ArrayList<>(),
            Optional.empty(),
            whileScope
        );

        scope.entries().add(scopedWhile);
        typeCheckBlock(stmt.block(), whileScope);
    }

    void typeCheckSwitchStmt(
        ST.SwitchStmt stmt,
        Pair<Integer, Integer> span,
        IR.Scope scope
    ) {
        log.debug("SwitchStmt");
        log.debug(span);

        var matched = stmt.switchExpr();
        var matchedType = this.toType(matched, span, scope);

        boolean last = false;
        for (var caseStmt : stmt.cases()) {
            ST.Block blockToCreate;
            Optional<IR.Value> pattern = Optional.empty();

            if (last) {
                throw fail(span, "unexpected case", "default was reached");
            }

            switch (caseStmt) {
                case ST.ValueCase(var comparator, var block) -> {
                    blockToCreate = block;

                    switch (comparator) {
                        case ST.ConstComp(var literal) -> {
                            // should be covered by parser
                            assert literal instanceof ST.LiteralExpr;

                            var type = this.toType(literal, span, scope);
                            if (matchedType != type) {
                                throw fail(
                                    span,
                                    "wrong type of "
                                        + literal
                                        + ": "
                                        + typeMismatch(matchedType, type),
                                    "case types should be equal to switch expr"
                                );
                            }
                            pattern = Optional.of(
                                this.toValue(literal, span, scope)
                            );
                        }
                        case ST.SeqComp(var literals) -> {
                            for (var literal : literals) {
                                // should be covered by parser
                                assert literal instanceof ST.LiteralExpr;

                                var type = this.toType(literal, span, scope);
                                if (matchedType != type) {
                                    throw fail(
                                        span,
                                        "wrong type of "
                                            + literal
                                            + ": "
                                            + typeMismatch(matchedType, type),
                                        "case types should be equal to switch expr"
                                    );
                                }
                            }
                            var args = literals
                                .stream()
                                .map((e) -> this.toVar(e, span, scope))
                                .collect(Collectors.toCollection(ArrayList::new));
                            pattern = Optional.of(new IR.Expr("any", args));

                        }
                        case ST.RangeComp(int from, int to) -> {
                            if (matchedType != IR.TY.INT) {
                                throw fail(
                                    span,
                                    "wrong case type: "
                                        + typeMismatch(matchedType, IR.TY.INT),
                                    "if matched with range(), variable must be Int"
                                );
                            }
                            // TODO: add args
                            pattern = Optional.of(
                                new IR.Expr("caseRange", new ArrayList<>())
                            );
                        }
                    }
                }
                case ST.DefaultCase(var block) -> {
                    log.debug(block);
                    blockToCreate = block;
                    last = true;
                }
            }

            var caseScope = new IR.Scope(
                scope,
                scope.funcName(),
                new HashMap<String, IR.Var>(),
                new ArrayList<IR.Entry>()
            );

            var scoped = new IR.Scoped(
                IR.SCOPE_KIND.CASE_BRANCH,
                new ArrayList<>(),
                pattern,
                caseScope
            );

            scope.entries().add(scoped);

            typeCheckBlock(blockToCreate, caseScope);
        }
    }

    void typeCheckFuncStmt(
        ST.FuncStmt stmt,
        Pair<Integer, Integer> span,
        IR.Scope scope
    ) {
        log.debug("FuncStmt");
        log.debug(span);

        var args = stmt
            .paramList()
            .stream()
            .map((p) -> p.first())
            .collect(Collectors.toCollection(ArrayList::new));

        var name = stmt.funcName();
        var newScope = new IR.Scope(
            // parent scope
            scope,
            // func name
            name,
            // name mappings
            new HashMap<String, IR.Var>(),
            // for vars and scopes
            new ArrayList<IR.Entry>()
        );

        var scoped = new IR.Scoped(
            IR.SCOPE_KIND.FUN,
            args,
            Optional.empty(),
            newScope
        );

        scope.entries().add(scoped);

        IR.TY returnType;
        if (stmt.returnType().isPresent()) {
            returnType = IR.typeFromST(stmt.returnType().get());
        } else {
            returnType = IR.TY.VOID;
        }

        var newOp = new IR.Operator(
            new ArrayList<>(List.of(
                new IR.OpSpec(
                    stmt
                        .paramList()
                        .stream()
                        .map((p) -> IR.typeFromST(p.second()))
                        .collect(Collectors.toCollection(ArrayList::new)),
                    returnType
                )
            )));

        log.debug("[new func] " + name + " -> " + newOp);
        this.ir.opStore().put(name, newOp);

        for (var p : stmt.paramList()) {
            var arg = new IR.Var(
                new IR.Arg(IR.typeFromST(p.second())),
                IR.typeFromST(p.second()),
                span,
                false
            );
            newScope.varMapping().put(p.first(), arg);
        }
        this.typeCheckBlock(stmt.block(), newScope);
    }


    void typecheck() {
        var stmts = parseTree.stmts().size();
        for (var idx = 0; idx < stmts; idx++) {
            var stmt = parseTree.stmts().get(idx);
            var span = parseTree.spans().get(idx);
            switch (stmt) {
                case ST.LetStmt(
                    var letName,
                    var letType,
                    var expr
                ) -> typeCheckNewVar(
                    letName, letType, expr, false, span, this.ir.scope()
                );
                case ST.FuncStmt f -> typeCheckFuncStmt(f, span, this.ir.scope());
            }
        }
    }

    RuntimeException fail(Pair<Integer, Integer> span, String err, String hint) {
        throw new RuntimeException(
            MessageFormat.format("""

> At {0} failure: {1}.
> Hint: {2}
""",
            formatSpan(span), err, hint)
        );
    }

    String formatSpan(Pair<Integer, Integer> span) {
        return SpanUtils.formatSpan(span, this.lineIndex);
    }

    public Typer(ST parseTree, ArrayList<Integer> lineIndex) {
        this.parseTree = parseTree;
        this.lineIndex = lineIndex;
    }
}
