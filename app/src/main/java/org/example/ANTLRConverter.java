package org.example;

import generated.*;
import generated.MS2Parser; 
import static generated.MS2Parser.*; 
import org.example.ST.*; 
import java.util.ArrayList;
import java.util.Optional;

public class ANTLRConverter extends MS2BaseVisitor<Object> {

    @Override
    public Object visitProgram(MS2Parser.ProgramContext ctx) {
        var stmts = new ArrayList<TopLevelStmt>();
        for (var child : ctx.topLevelStmt()) {
            stmts.add((TopLevelStmt) visit(child));
        }
        return new ST(stmts);
    }

    // --- Top Level Wrappers ---

    @Override
    public Object visitTopLevelFunc(MS2Parser.TopLevelFuncContext ctx) {
        return visit(ctx.funcDecl());
    }

    @Override
    public Object visitTopLevelLet(MS2Parser.TopLevelLetContext ctx) {
        return visit(ctx.letDecl());
    }

    // --- Declarations ---
    // These are fine because they are the "Inner" rules already.

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

    @Override
    public Object visitBlock(MS2Parser.BlockContext ctx) {
        var stmts = new ArrayList<Stmt>();
        for (var s : ctx.stmt()) {
            stmts.add((Stmt) visit(s));
        }
        return new Block(stmts);
    }

    // --- Statements (The Fix is Here) ---

    @Override
    public Object visitVar(MS2Parser.VarContext ctx) {
        var inner = ctx.varDecl(); 
        return visitVarOrLet(inner.ID().getText(), inner.type(), inner.expr(), true);
    }

    @Override
    public Object visitLet(MS2Parser.LetContext ctx) {
        var inner = ctx.letDecl();
        return visitVarOrLet(inner.ID().getText(), inner.type(), inner.expr(), false);
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
    public Object visitCallStmt(MS2Parser.CallStmtContext ctx) { 
        var inner = ctx.exprStmt().callExpr();
        String name = inner.ID().getText();
        var args = new ArrayList<Expression>();
        for(var e : inner.expr()) {
            args.add((Expression) visit(e));
        }
        return new FuncCallStmt(name, args);
    }

    // --- Expressions ---

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
    public Object visitAddSub(MS2Parser.AddSubContext ctx) {
        Expression left = (Expression) visit(ctx.expr(0));
        Expression right = (Expression) visit(ctx.expr(1));
        BIN_OP op = (ctx.op.getType() == PLUS) ? BIN_OP.ADD : BIN_OP.SUB;
        return new BinOpExpr(op, left, right);
    }

    @Override
    public Object visitMulDiv(MS2Parser.MulDivContext ctx) {
        Expression left = (Expression) visit(ctx.expr(0));
        Expression right = (Expression) visit(ctx.expr(1));
        BIN_OP op = (ctx.op.getType() == MULT) ? BIN_OP.MUL : BIN_OP.DIV;
        return new BinOpExpr(op, left, right);
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
