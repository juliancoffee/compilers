package org.example;

import java.util.*;

// Program = { LetStmt | FuncStmt }
//
// ST short for Syntax Tree
//
// Idk if it's Abstract or Concrete, something inbetween
//
// P. S. I think it's actually AST
public record ST(
    ArrayList<ST.TopLevelStmt> stmts, ArrayList<Pair<Integer, Integer>> spans
) {
    public ST(ArrayList<ST.TopLevelStmt> stmts) {
        this(stmts, new ArrayList<>());
    }

    // adding new statement to program tree, get a spans
    void add(
        ST.TopLevelStmt stmt,
        Pair<Integer, Integer> from,
        Pair<Integer, Integer> to
    ) {
        this.stmts.add(stmt);
        this.spans.add(new Pair<>(from.first(), to.second()));
    }

    // adding new statement to a block, get a spans
    record Block(
        ArrayList<ST.Stmt> stmts,
        ArrayList<Pair<Integer, Integer>> spans
    ) {
        public Block(ArrayList<ST.Stmt> stmts) {
            this(stmts, new ArrayList<>());
        }

        void add(
            ST.Stmt stmt,
            Pair<Integer, Integer> from,
            Pair<Integer, Integer> to
        ) {
            this.stmts.add(stmt);
            this.spans.add(new Pair<>(from.first(), to.second()));
        }
    }

    public enum BIN_OP {
        ADD, SUB, MUL, DIV, POW,
        LT, LE, GT, GE, EQ, NE,
        AND, OR
    }
    public enum UNARY_OP { PLUS, MINUS, NOT }
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
        Block block
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
        String forIdent, Iter iterable, Block block
    ) implements Stmt {}

    // WhileStmt = 'while' Expression Block
    public record WhileStmt(
        Expression whileCond, Block block
    ) implements BranchStmt {}

    // IfStmt = 'if' Expression Block [ 'else' Block ]
    public record IfStmt(
        Expression ifCond, Block thenBlock, Optional<Block> elseBlock
    ) implements BranchStmt {}

    // SwitchStmt = 'switch' Expression '{' { Case } '}'
    public record SwitchStmt(
        Expression switchExpr, ArrayList<CaseStmt> cases
    ) implements BranchStmt {}

    // Case = ('case' Comparator | 'default') Block
    public sealed interface CaseStmt permits ValueCase, DefaultCase {}
    public record ValueCase(Comparator comparator, Block block)
        implements CaseStmt {}
    public record DefaultCase(Block block)
        implements CaseStmt {}

    // Comparator = Const | Seq | Range
    public sealed interface Comparator permits ConstComp, SeqComp, RangeComp {}

    public record ConstComp(Expression literal) implements Comparator {}
    public record SeqComp(ArrayList<Expression> literals) implements Comparator {}
    public record RangeComp(int from, int to) implements Comparator {}

    // =================================================================
    // NEW FUNCTION TO CLEAR ALL SPANS
    // =================================================================

    /**
     * Public entry point to clear all span lists in the entire AST.
     */
    public void clearAllSpans() {
        // 1. Clear the root-level spans
        this.spans.clear();

        // 2. Start traversal from the top-level statements
        for (TopLevelStmt stmt : this.stmts) {
            clearSpans(stmt);
        }
    }

    /**
     * Traverses and clears spans from a Block.
     * This is a key method, as 'Block' is the other record
     * that contains a 'spans' list.
     */
    private void clearSpans(Block block) {
        if (block == null) return;

        // 1. Clear the block's own span list
        block.spans.clear();

        // 2. Recurse into the block's statements
        for (Stmt stmt : block.stmts) {
            clearSpans(stmt);
        }
    }

    /**
     * Traverses a generic Statement.
     */
    private void clearSpans(Stmt stmt) {
        switch (stmt) {
            // Statements with expressions
            case AssignStmt s: clearSpans(s.expr); break;
            case VarStmt s: clearSpans(s.expr); break;
            case ReturnStmt s: clearSpans(s.returnExpr); break;

            // Statements with expression lists
            case FuncCallStmt s: s.args.forEach(this::clearSpans); break;
            case PrintStmt s: s.printExprs.forEach(this::clearSpans); break;

            // Statements with blocks and/or expressions
            case ForStmt s:
                clearSpans(s.iterable);
                clearSpans(s.block);
                break;
            case IfStmt s:
                clearSpans(s.ifCond);
                clearSpans(s.thenBlock);
                s.elseBlock.ifPresent(this::clearSpans); // Handle Optional
                break;
            case WhileStmt s:
                clearSpans(s.whileCond);
                clearSpans(s.block);
                break;
            case SwitchStmt s:
                clearSpans(s.switchExpr);
                s.cases.forEach(this::clearSpans);
                break;

            // TopLevel statements (which are also Stmt)
            case LetStmt s: clearSpans(s.expr); break;
            case FuncStmt s: clearSpans(s.block); break;
        }
    }

    /**
     * Traverses an Expression.
     */
    private void clearSpans(Expression expr) {
        switch (expr) {
            // Recursive cases
            case BinOpExpr e:
                clearSpans(e.a);
                clearSpans(e.b);
                break;
            case UnaryOpExpr e:
                clearSpans(e.expr);
                break;
            case FuncCallExpr e:
                e.args.forEach(this::clearSpans);
                break;

            // Base cases (no children)
            case IdentExpr e: break;
            case IntLiteralExpr e: break;
            case FloatLiteralExpr e: break;
            case StrLiteralExpr e: break;
            case BoolLiteralExpr e: break;
        }
    }

    /**
     * Traverses an Iterable.
     */
    private void clearSpans(Iter iter) {
        switch (iter) {
            case Expression e: clearSpans(e); break;
            case RangeExpr e: break; // No children
        }
    }

    /**
     * Traverses a Case statement.
     */
    private void clearSpans(CaseStmt caseStmt) {
        switch (caseStmt) {
            case ValueCase c:
                clearSpans(c.comparator);
                clearSpans(c.block);
                break;
            case DefaultCase c:
                clearSpans(c.block);
                break;
        }
    }

    /**
     * Traverses a Comparator.
     */
    private void clearSpans(Comparator comparator) {
        switch (comparator) {
            case ConstComp c: clearSpans(c.literal); break;
            case SeqComp c: c.literals.forEach(this::clearSpans); break;
            case RangeComp c: break; // No children
        }
    }
}
