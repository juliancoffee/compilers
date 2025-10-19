package org.example;

import java.util.*;


enum BIN_OP {
    // yes, this too
    ASSIGN,
    // other simple ones
    ADD, SUB, MUL, DIV, POW,
    LT, LE, GT, GE, EQ, NE,
    AND, OR
}

enum UN_OP { PLUS, MINUS, NOT }

public record IR(
    // for vars and scopes
    ArrayList<IR.Entry> entries,
    // name mappings
    Map<String, Integer> varMapping,
    // stores
    ArrayList<IR.Var> varStore,
    Map<String, OpSpec> opStore
) {
    public enum TY {
        INT,
        FLOAT,
        BOOL,
        STRING,
        VOID,
        // if we dont know yet
        UNKNOWN,
        // for func<T>(x: T, y: T) to check that args are same
        //
        // for equality and assignments
        SAME,
        // for print
        ANY
    }

    /*
     * Entries
     */
    public sealed interface Entry
        permits NewVar, Action, Scoped {}

    public record NewVar(String name) implements Entry {}
    public record Action(Var bind) implements Entry {}

    /*
     * Scopes
     */
    public enum SCOPE_KIND {
        FUN,
        IF_BRANCH,
        ELSE_BRANCH,
        WHILE,
        FOR,
        CASE_BRANCH,
    }

    public record Scoped(
        SCOPE_KIND kind,
        // for variable, or function args
        ArrayList<String> bornVars,
        // if, switch, while expression
        Optional<Integer> dependencyId,
        // block entries
        ArrayList<IR.Entry> entries,
        // local name mappings
        Map<String, Integer> varMapping,
        // local stores
        ArrayList<IR.Var> varStore
    ) implements Entry {}

    /*
     * Variables
     */
    public sealed interface Value
        permits Ref, Atom, Expr, RangeExpr {}

    // ident
    public record Ref(int ident) implements Value {}
    // literal
    public record Atom(String val) implements Value {}
    // expr
    public record Expr(String op, ArrayList<Var> vars) implements Value {}
    // range
    public record RangeExpr(Integer from, Integer to, Integer step)
        implements Value {}

    public record Var(
        Value val,
        TY type,
        Pair<Integer, Integer> span,
        boolean mutable
    ) {}

    /*
     * Operators
     */
    public record OpSpec(
        ArrayList<TY> argTypes,
        TY returnType
    ) {}

    public record Operator(ArrayList<OpSpec> alternatives) {}
}
