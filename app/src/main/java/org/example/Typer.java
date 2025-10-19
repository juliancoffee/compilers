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

        for (var alt : operator.alternatives()) {
            // FIXME: can't do typecasts
            if (alt.argTypes().equals(types)) {
                return alt.returnType();
            }
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
            case ST.IdentExpr(String identExpr) -> {
                yield new IR.Ref(identExpr);
            }
            case ST.Expression s -> {
                throw new RuntimeException("unexpected expr to eval: " + s);
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
                    letName, letType, expr, false, span, this.ir.scope()
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
                case ST.Stmt s -> {
                    throw new RuntimeException("stmt is not implemented" + s);
                }
            }
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
