package org.example;

import generated.*;
import generated.MS2Parser; 
import static generated.MS2Parser.*; 
import org.example.ST.*; 
import java.util.ArrayList;
import java.util.Optional;

public class ANTLRConverter extends MS2BaseVisitor<Object> {
    private final Pair<Integer, Integer> dummySpan = new Pair<>(0, 0);

    @Override
    public Object visitProgram(MS2Parser.ProgramContext ctx) {
        var stmts = new ST(new ArrayList<TopLevelStmt>());
        for (var child : ctx.topLevelStmt()) {
            stmts.add((TopLevelStmt) visit(child), dummySpan, dummySpan);
        }
        return stmts;
    }

    // --- Top Level Wrappers ---

    @Override
    public Object visitTopLevelFunc(MS2Parser.TopLevelFuncContext ctx) {
        return visit(ctx.funcDecl());
    }

    @Override
    public Object visitTopLevelLet(MS2Parser.TopLevelLetContext ctx) {
        return visit(ctx.letDecl()); // Now safe: visitLetDecl is implemented below
    }

    // --- Declarations ---

    @Override
    public Object visitFuncDecl(MS2Parser.FuncDeclContext ctx) {
        String name = ctx.ID().getText();
        var params = new ArrayList<Pair<String, TY>>();

        if (ctx.paramList() != null) {
            for (var p : ctx.paramList().param()) {
                String pName = p.ID().getText();
                TY pType = parseType(p.type().getText());
                params.add(new Pair<>(pName, pType));
            }
        }

        Optional<TY> retType = Optional.empty();
        if (ctx.type() != null) {
            retType = Optional.of(parseType(ctx.type().getText()));
        }

        Block block = (Block) visit(ctx.block());
        return new FuncStmt(name, params, retType, block);
    }

    // FIX: Added specific visitors for LetDecl and VarDecl
    @Override
    public Object visitLetDecl(MS2Parser.LetDeclContext ctx) {
        return visitVarOrLet(ctx.ID().getText(), ctx.type(), ctx.expr(), false);
    }

    @Override
    public Object visitVarDecl(MS2Parser.VarDeclContext ctx) {
        return visitVarOrLet(ctx.ID().getText(), ctx.type(), ctx.expr(), true);
    }

    @Override
    public Object visitBlock(MS2Parser.BlockContext ctx) {
        var stmts = new Block(new ArrayList<Stmt>());
        for (var s : ctx.stmt()) {
            stmts.add((Stmt) visit(s), dummySpan, dummySpan);
        }
        return stmts;
    }

    // --- Statements ---

    @Override
    public Object visitVar(MS2Parser.VarContext ctx) {
        return visit(ctx.varDecl()); // Delegates to visitVarDecl
    }

    @Override
    public Object visitLet(MS2Parser.LetContext ctx) {
        return visit(ctx.letDecl()); // Delegates to visitLetDecl
    }

    private Stmt visitVarOrLet(String name, MS2Parser.TypeContext typeCtx, MS2Parser.ExprContext exprCtx, boolean isVar) {
        Optional<TY> type = Optional.empty();
        if (typeCtx != null) {
            type = Optional.of(parseType(typeCtx.getText()));
        }
        Expression expr = (Expression) visit(exprCtx);

        if (isVar) return new VarStmt(name, type, expr);
        else return new LetStmt(name, type, expr);
    }

    @Override
    public Object visitAssign(MS2Parser.AssignContext ctx) {
        var inner = ctx.assignStmt();
        return new AssignStmt(inner.ID().getText(), (Expression) visit(inner.expr()));
    }

    @Override
    public Object visitPrint(MS2Parser.PrintContext ctx) {
        var inner = ctx.printStmt();
        var exprs = new ArrayList<Expression>();
        for (var e : inner.expr()) {
            exprs.add((Expression) visit(e));
        }
        return new PrintStmt(exprs);
    }

    @Override
    public Object visitReturn(MS2Parser.ReturnContext ctx) {
        var inner = ctx.returnStmt();
        Expression expr = (Expression) visit(inner.expr());
        return new ReturnStmt(expr); 
    }

    @Override
    public Object visitIf(MS2Parser.IfContext ctx) {
        var inner = ctx.ifStmt();
        Expression cond = (Expression) visit(inner.expr());
        Block thenBlock = (Block) visit(inner.block(0));
        Optional<Block> elseBlock = Optional.empty();

        if (inner.block().size() > 1) {
            elseBlock = Optional.of((Block) visit(inner.block(1)));
        }
        return new IfStmt(cond, thenBlock, elseBlock);
    }

    @Override
    public Object visitWhile(MS2Parser.WhileContext ctx) {
        var inner = ctx.whileStmt();
        return new WhileStmt((Expression) visit(inner.expr()), (Block) visit(inner.block()));
    }

    @Override
    public Object visitFor(MS2Parser.ForContext ctx) {
        var inner = ctx.forStmt();
        String ident = inner.ID().getText();
        Iter iter = (Iter) visit(inner.iterable());
        Block block = (Block) visit(inner.block());
        return new ForStmt(ident, iter, block);
    }

    @Override
    public Object visitSwitch(MS2Parser.SwitchContext ctx) {
        var inner = ctx.switchStmt();
        Expression expr = (Expression) visit(inner.expr());
        var cases = new ArrayList<CaseStmt>();
        for (var c : inner.caseStmt()) {
            cases.add((CaseStmt) visit(c));
        }
        return new SwitchStmt(expr, cases);
    }

    @Override
    public Object visitCallStmt(MS2Parser.CallStmtContext ctx) { 
        var inner = ctx.exprStmt().callExpr();
        String name = inner.ID().getText();
        var args = new ArrayList<Expression>();
        for(var e : inner.expr()) {
            args.add((Expression) visit(e));
        }
        return new FuncCallStmt(name, args);
    }

    // --- Loop & Switch Helpers ---

    @Override
    public Object visitRangeIter(MS2Parser.RangeIterContext ctx) {
        int from = Integer.parseInt(ctx.INT(0).getText());
        int to = Integer.parseInt(ctx.INT(1).getText());
        int step = Integer.parseInt(ctx.INT(2).getText());
        return new RangeExpr(from, to, step);
    }

    @Override
    public Object visitExprIter(MS2Parser.ExprIterContext ctx) {
        return visit(ctx.expr());
    }

    @Override
    public Object visitCaseValue(MS2Parser.CaseValueContext ctx) {
        Comparator comp = (Comparator) visit(ctx.comparator());
        Block block = (Block) visit(ctx.block());
        return new ValueCase(comp, block);
    }

    @Override
    public Object visitCaseDefault(MS2Parser.CaseDefaultContext ctx) {
        Block block = (Block) visit(ctx.block());
        return new DefaultCase(block);
    }

    @Override
    public Object visitRangeCompRule(MS2Parser.RangeCompRuleContext ctx) {
        int from = Integer.parseInt(ctx.INT(0).getText());
        int to = Integer.parseInt(ctx.INT(1).getText());
        return new RangeComp(from, to);
    }

    @Override
    public Object visitConstCompRule(MS2Parser.ConstCompRuleContext ctx) {
        Expression literal = (Expression) visit(ctx.literal());
        return new ConstComp(literal);
    }

    @Override
    public Object visitSeqCompRule(MS2Parser.SeqCompRuleContext ctx) {
        var literals = new ArrayList<Expression>();
        for (var litCtx : ctx.literal()) {
            literals.add((Expression) visit(litCtx));
        }
        return new SeqComp(literals);
    }

    // --- Expressions ---

    // FIX: Added visitLit to unwrap the #Lit label in the grammar
    @Override
    public Object visitLit(MS2Parser.LitContext ctx) {
        return visit(ctx.literal());
    }

    @Override
    public Object visitLitInt(MS2Parser.LitIntContext ctx) {
        return new IntLiteralExpr(Integer.parseInt(ctx.getText()));
    }

    @Override
    public Object visitLitFloat(MS2Parser.LitFloatContext ctx) {
        return new FloatLiteralExpr(Double.parseDouble(ctx.getText()));
    }

    @Override
    public Object visitLitBool(MS2Parser.LitBoolContext ctx) {
        return new BoolLiteralExpr(Boolean.parseBoolean(ctx.getText()));
    }

    @Override
    public Object visitLitStr(MS2Parser.LitStrContext ctx) {
        String raw = ctx.getText();
        if (raw.length() >= 2) {
             return new StrLiteralExpr(raw.substring(1, raw.length() - 1));
        }
        return new StrLiteralExpr("");
    }

    @Override
    public Object visitId(MS2Parser.IdContext ctx) {
        return new IdentExpr(ctx.getText());
    }

    @Override
    public Object visitParen(MS2Parser.ParenContext ctx) {
        return visit(ctx.expr());
    }

    @Override
    public Object visitPower(MS2Parser.PowerContext ctx) {
        Expression left = (Expression) visit(ctx.expr(0));
        Expression right = (Expression) visit(ctx.expr(1));
        return new BinOpExpr(BIN_OP.POW, left, right);
    }

    @Override
    public Object visitUnary(MS2Parser.UnaryContext ctx) {
        Expression expr = (Expression) visit(ctx.expr());
        UNARY_OP op = switch (ctx.op.getType()) {
            case PLUS -> UNARY_OP.PLUS;
            case MINUS -> UNARY_OP.MINUS;
            case NOT -> UNARY_OP.NOT;
            default -> throw new RuntimeException("Unknown unary op");
        };
        return new UnaryOpExpr(op, expr);
    }

    @Override
    public Object visitMulDiv(MS2Parser.MulDivContext ctx) {
        Expression left = (Expression) visit(ctx.expr(0));
        Expression right = (Expression) visit(ctx.expr(1));
        BIN_OP op = (ctx.op.getType() == MULT) ? BIN_OP.MUL : BIN_OP.DIV;
        return new BinOpExpr(op, left, right);
    }

    @Override
    public Object visitAddSub(MS2Parser.AddSubContext ctx) {
        Expression left = (Expression) visit(ctx.expr(0));
        Expression right = (Expression) visit(ctx.expr(1));
        BIN_OP op = (ctx.op.getType() == PLUS) ? BIN_OP.ADD : BIN_OP.SUB;
        return new BinOpExpr(op, left, right);
    }

    @Override
    public Object visitRelational(MS2Parser.RelationalContext ctx) {
        Expression left = (Expression) visit(ctx.expr(0));
        Expression right = (Expression) visit(ctx.expr(1));
        
        BIN_OP op = switch (ctx.op.getType()) {
            case LT -> BIN_OP.LT;
            case LTE -> BIN_OP.LE;
            case GT -> BIN_OP.GT;
            case GTE -> BIN_OP.GE;
            default -> throw new RuntimeException("Unknown relational op");
        };
        return new BinOpExpr(op, left, right);
    }

    @Override
    public Object visitEquality(MS2Parser.EqualityContext ctx) {
        Expression left = (Expression) visit(ctx.expr(0));
        Expression right = (Expression) visit(ctx.expr(1));
        BIN_OP op = (ctx.op.getType() == EQ) ? BIN_OP.EQ : BIN_OP.NE;
        return new BinOpExpr(op, left, right);
    }

    @Override
    public Object visitAnd(MS2Parser.AndContext ctx) {
        Expression left = (Expression) visit(ctx.expr(0));
        Expression right = (Expression) visit(ctx.expr(1));
        return new BinOpExpr(BIN_OP.AND, left, right);
    }

    @Override
    public Object visitOr(MS2Parser.OrContext ctx) {
        Expression left = (Expression) visit(ctx.expr(0));
        Expression right = (Expression) visit(ctx.expr(1));
        return new BinOpExpr(BIN_OP.OR, left, right);
    }

    @Override
    public Object visitCall(MS2Parser.CallContext ctx) {
        return visit(ctx.callExpr());
    }

    @Override
    public Object visitCallExpr(MS2Parser.CallExprContext ctx) {
        String name = ctx.ID().getText(); 
        var args = new ArrayList<Expression>();
        for (var e : ctx.expr()) {
            args.add((Expression) visit(e));
        }
        return new FuncCallExpr(name, args);
    }

    // --- Helpers ---

    private TY parseType(String t) {
        return switch(t) {
            case "Int" -> TY.INT;
            case "Double" -> TY.FLOAT;
            case "Bool" -> TY.BOOL;
            case "String" -> TY.STRING;
            case "Void" -> TY.VOID;
            default -> throw new RuntimeException("Unknown type: " + t);
        };
    }

    @Override
    protected Object defaultResult() {
        throw new RuntimeException("Unimplemented visitor method reached!");
    }
}
