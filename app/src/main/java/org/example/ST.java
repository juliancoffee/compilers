package org.example;

import java.util.*;

// Program = { LetStmt | FuncStmt }
//
// ST short for Syntax Tree
//
// Idk if it's Abstract or Concrete, something inbetween
public record ST(ArrayList<ST.TopLevelStmt> stmts) {
    public enum BIN_OP {
        ADD, SUB, MUL, DIV, POW,
        LT, LE, GT, GE, EQ, NE,
        AND, OR
    }
    public enum UNARY_OP { PLUS, MINUS, NOT }
//    public enum REL_OP {
//        EQ,  // ==
//        NE,  // !=
//        LT,  // <
//        LE,  // <=
//        GT,  // >
//        GE   // >=
//    }
//    public enum LOGIC_OP { AND, OR }
    public enum TY {
        INT,
        FLOAT,
        BOOL,
        STRING,
        VOID,
    }

    public sealed interface TopLevelStmt extends Stmt
            permits LetStmt, FuncStmt {}

    public sealed interface Stmt
            permits AssignStmt, BranchStmt, ForStmt, FuncCallStmt, PrintStmt, ReturnStmt, TopLevelStmt, VarStmt {}
    public sealed interface BranchStmt extends Stmt
            permits IfStmt, WhileStmt, SwitchStmt {}

    public sealed interface Expression extends Iter
            permits BinOpExpr, FuncCallExpr, IdentExpr, LiteralExpr, UnaryOpExpr {}

    public sealed interface LiteralExpr extends Expression
            permits
                IntLiteralExpr,
                FloatLiteralExpr,
                StrLiteralExpr,
                BoolLiteralExpr {}

    // IdentExpr = Ident
    public record IdentExpr(String identExpr)
        implements Expression {}

    // FuncCallExpr = Ident ArgsFragment
    public record FuncCallExpr(String callIdent, ArrayList<Expression> args)
        implements Expression {}

    // IntLiteralExpr = Int
    public record IntLiteralExpr(Integer intLiteral)
        implements LiteralExpr {}

    // FloatLiteralExpr = Float
    public record FloatLiteralExpr(Double floatLiteral)
        implements LiteralExpr {}

    // StrLiteralExpr = String
    public record StrLiteralExpr(String strLiteral)
        implements LiteralExpr {}

    public record BoolLiteralExpr(Boolean boolLiteral)
            implements LiteralExpr {}

    // Abstract thingy for every binary operation, including logical ?
    public record BinOpExpr(BIN_OP op, Expression a, Expression b)
        implements Expression {}
//    public record RelOpExpr(REL_OP op, Expression a, Expression b)
//        implements Expression {}
//    public record LogicOpExpr(LOGIC_OP op, Expression a, Expression b)
//        implements Expression {}
    public record UnaryOpExpr(UNARY_OP op, Expression expr)
        implements Expression {}



    // VarStmt = 'var' Ident [ ':' Type ] '=' Expression ';'
    public record VarStmt(String letName, Optional<TY> letType, Expression expr)
        implements Stmt {}

    // LetStmt = 'let' Ident [ ':' Type ] '=' Expression ';'
    public record LetStmt(String letName, Optional<TY> letType, Expression expr)
        implements TopLevelStmt {}

    // FuncStmt = 'func' Ident ParamList [ '->' Type ] Block
    // Block = '{' { Stmt } '}'
    //
    // ParamList = '(' [ ParamSpec { ',' ParamSpec } [ ',' ] ] ')'
    // ParamSpec = Ident ':' Type
    public record FuncStmt(
        String funcName,
        ArrayList<Pair<String, TY>> paramList,
        Optional<TY> returnType,
        ArrayList<Stmt> block
    )
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

    // ReturnStmt = 'return' Expression ';'
    public record ReturnStmt(Expression returnExpr) implements Stmt {}

    // Iterable = Expression
    //          | 'range' '(' Expression ',' Expression ',' Expression ')'
    sealed interface Iter permits Expression, RangeExpr {}
    record RangeExpr(Integer from, Integer to, Integer step)
        implements Iter {}

    public record ForStmt(
        String forIdent, Iter iterable, ArrayList<Stmt> block
    ) implements Stmt {}

    // WhileStmt = 'while' Expression Block
    public record WhileStmt(
        Expression whileCond, ArrayList<Stmt> block
    ) implements BranchStmt {}

    // IfStmt = 'if' Expression Block [ 'else' Block ]
    public record IfStmt(
        Expression ifCond, ArrayList<Stmt> thenBlock, Optional<ArrayList<Stmt>> elseBlock
    ) implements BranchStmt {}

    // SwitchStmt = 'switch' Expression '{' { Case } '}'
    public record SwitchStmt(
        Expression switchExpr, ArrayList<CaseStmt> cases
    ) implements BranchStmt {}

    // Case = ('case' Comparator | 'default') Block
    public sealed interface CaseStmt permits ValueCase, DefaultCase {}
    public record ValueCase(Comparator comparator, ArrayList<Stmt> block) implements CaseStmt {}
    public record DefaultCase(ArrayList<Stmt> block) implements CaseStmt {}

    // Comparator = Const | Seq | Range
    public sealed interface Comparator permits ConstComp, SeqComp, RangeComp {}

    public record ConstComp(Expression literal) implements Comparator {}
    public record SeqComp(ArrayList<Expression> literals) implements Comparator {}
    public record RangeComp(int from, int to) implements Comparator {}
}
