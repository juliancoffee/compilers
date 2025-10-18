package org.example;

import java.util.ArrayList;

// Program = { LetStmt | FuncStmt }
//
// ST short for Syntax Tree
//
// Idk if it's Abstract or Concrete, something inbetween
public record ST(ArrayList<ST.TopLevelStmt> stmts) {
    public enum BIN_OP {
        ADD,
        SUB,
        MUL,
        DIV,
    }

    public sealed interface TopLevelStmt extends Stmt
            permits LetStmt, FuncStmt {}

    public sealed interface Stmt
            permits TopLevelStmt, PrintStmt, AssignStmt, FuncCallStmt {}

    public sealed interface Expression
            permits LiteralExpr, IdentExpr, FuncCallExpr, BinOpExpr {}

    public sealed interface LiteralExpr extends Expression
            permits IntLiteralExpr {}

    // IdentExpr = Ident
    public record IdentExpr(String identExpr)
        implements Expression {}

    // FuncCallExpr = Ident ArgsFragment
    public record FuncCallExpr(String callIdent, ArrayList<Expression> args)
        implements Expression {}

    // IntLiteralExpr = IntLiteral
    public record IntLiteralExpr(Integer intLiteral)
        implements LiteralExpr {}

    public record BinOpExpr(BIN_OP op, Expression a, Expression b)
        implements Expression {}

    // LetStmt = 'let' Ident '=' Expression ';'
    public record LetStmt(String letName, Expression expr)
        implements TopLevelStmt {}

    // FuncStmt = 'func' Ident '(' ')' Block
    // Block = { Stmt }
    // TODO: args
    public record FuncStmt(String funcName, ArrayList<Stmt> block)
        implements TopLevelStmt {}

    // PrintStmt = 'print' ArgsFragment ';'
    // ArgsFragment = '(' [ Expression { ',' Expression } [ ',' ] ] ')'
    //
    // Simply put, allows zero or more expressions, separated by comma.
    // Comma optionally may be trailing.
    public record PrintStmt(ArrayList<Expression> printExprs)
        implements Stmt {}

    // AssignStmt = Ident '=' Expression ';'
    public record AssignStmt(String assignIdent, Expression expr)
        implements Stmt {}

    // FuncCallStmt = FuncCall ';'
    // FuncCall = Ident ArgsFragment
    public record FuncCallStmt(String callIdent, ArrayList<Expression> args)
        implements Stmt {}
}
