package org.example;

import generated.*;
import generated.MS2Parser;
import static generated.MS2Parser.*;

import org.example.ST.*;

import java.util.ArrayList;
import java.util.Optional;
import java.util.List;

public class ANTLRConverter extends MS2BaseVisitor<Object> {
    private final Pair<Integer, Integer> dummySpan = new Pair<>(0, 0);

    // --- Program & Block ---

    @Override
    public Object visitProgram(MS2Parser.ProgramContext ctx) {
        var stmts = new ST(new ArrayList<TopLevelStmt>());
        for (var child : ctx.topLevelStmt()) {
            stmts.add((TopLevelStmt) visit(child), dummySpan, dummySpan);
        }
        return stmts;
    }

    @Override
    public Object visitBlock(MS2Parser.BlockContext ctx) {
        var stmts = new Block(new ArrayList<Stmt>());
        for (var s : ctx.stmt()) {
            stmts.add((Stmt) visit(s), dummySpan, dummySpan);
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
        return visit(ctx.letDecl());
    }

    // --- Declarations & Statements (Retained Logic) ---

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
    public Object visitLetDecl(MS2Parser.LetDeclContext ctx) {
        return visitVarOrLet(ctx.ID().getText(), ctx.type(), ctx.expr(), false);
    }

    @Override
    public Object visitVarDecl(MS2Parser.VarDeclContext ctx) {
        return visitVarOrLet(ctx.ID().getText(), ctx.type(), ctx.expr(), true);
    }
    
    // ... (visitVar, visitLet, visitVarOrLet, visitAssign, visitPrint, visitReturn, 
    // visitIf, visitWhile, visitFor, visitSwitch, visitCallStmt are retained and assumed correct) ...

    @Override
    public Object visitVar(MS2Parser.VarContext ctx) {
        return visit(ctx.varDecl());
    }

    @Override
    public Object visitLet(MS2Parser.LetContext ctx) {
        return visit(ctx.letDecl());
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
    
    // ... (Loop & Switch Helpers and Literal visitors are retained) ...
    
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

    // --- Literal Visitors (Retained) ---

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


    // --- Expression Hierarchy (Nested, Corrected) ---

    // ENTRY POINT: Delegates the top-level 'expr' rule (if used) or 'expression' rule
    @Override
    public Object visitExpr(MS2Parser.ExprContext ctx) {
        return visit(ctx.getChild(0)); 
    }
    
    @Override
    public Object visitExpression(MS2Parser.ExpressionContext ctx) {
        return visit(ctx.logicExpr()); 
    }
    
    // Logic (||, &&) - Handles Looping over RelExpr
    @Override
    public Object visitLogicExpr(MS2Parser.LogicExprContext ctx) {
        if (ctx.relExpr().size() == 1) return visit(ctx.relExpr(0));

        Expression left = (Expression) visit(ctx.relExpr(0));
        for (int i = 1; i < ctx.relExpr().size(); i++) {
            String opText = ctx.getChild(2 * i - 1).getText(); 
            Expression right = (Expression) visit(ctx.relExpr(i));
            
            BIN_OP op = switch (opText) {
                case "&&" -> BIN_OP.AND;
                case "||" -> BIN_OP.OR;
                default -> throw new RuntimeException("Unknown logical op: " + opText);
            };
            left = new BinOpExpr(op, left, right);
        }
        return left;
    }

    // Relational/Equality - Handles optional RelOp
    @Override
    public Object visitRelExpr(MS2Parser.RelExprContext ctx) {
        if (ctx.relOp() == null) return visit(ctx.arithExpr(0));

        Expression left = (Expression) visit(ctx.arithExpr(0));
        Expression right = (Expression) visit(ctx.arithExpr(1));
        
        String opText = ctx.relOp().getText();
        
        BIN_OP op = switch (opText) {
            case "<" -> BIN_OP.LT;
            case "<=" -> BIN_OP.LE;
            case ">" -> BIN_OP.GT;
            case ">=" -> BIN_OP.GE;
            case "==" -> BIN_OP.EQ;
            case "!=" -> BIN_OP.NE;
            default -> throw new RuntimeException("Unknown relational/equality op: " + opText);
        };
        return new BinOpExpr(op, left, right);
    }
    
    // Additive
    @Override
    public Object visitArithExpr(MS2Parser.ArithExprContext ctx) {
        if (ctx.term().size() == 1) return visit(ctx.term(0));
        
        Expression left = (Expression) visit(ctx.term(0));
        for (int i = 1; i < ctx.term().size(); i++) {
            String opText = ctx.getChild(2 * i - 1).getText(); 
            Expression right = (Expression) visit(ctx.term(i));
            
            BIN_OP op = (opText.equals("+")) ? BIN_OP.ADD : BIN_OP.SUB;
            left = new BinOpExpr(op, left, right);
        }
        return left;
    }

    // Multiplicative
    @Override
    public Object visitTerm(MS2Parser.TermContext ctx) {
        if (ctx.power().size() == 1) return visit(ctx.power(0));

        Expression left = (Expression) visit(ctx.power(0));
        for (int i = 1; i < ctx.power().size(); i++) {
            String opText = ctx.getChild(2 * i - 1).getText();
            Expression right = (Expression) visit(ctx.power(i));

            BIN_OP op = (opText.equals("*")) ? BIN_OP.MUL : BIN_OP.DIV;
            left = new BinOpExpr(op, left, right);
        }
        return left;
    }

    // Power (Right Recursion)
    @Override
    public Object visitPower(MS2Parser.PowerContext ctx) {
        if (ctx.power() == null) {
            // Base case: only a factor
            return visit(ctx.factor());
        }
        // Right-recursive call: left is factor, right is power
        Expression left = (Expression) visit(ctx.factor());
        Expression right = (Expression) visit(ctx.power());

        return new BinOpExpr(BIN_OP.POW, left, right);
    }

    @Override
    public Object visitIdentFactor(MS2Parser.IdentFactorContext ctx) {
        return new IdentExpr(ctx.ID().getText());
    }

    @Override
    public Object visitParenFactor(MS2Parser.ParenFactorContext ctx) {
        return visit(ctx.expression());
    }

    @Override
    public Object visitLitFactor(MS2Parser.LitFactorContext ctx) {
        return visit(ctx.literal());
    }
    
    @Override
    public Object visitCallFactor(MS2Parser.CallFactorContext ctx) {
        return visit(ctx.callExpr());
    }

    @Override
    public Object visitUnaryFactor(MS2Parser.UnaryFactorContext ctx) {
        Expression expr = (Expression) visit(ctx.factor());
        String opText = ctx.getChild(0).getText();
        
        UNARY_OP op = switch (opText) {
            case "+" -> UNARY_OP.PLUS;
            case "-" -> UNARY_OP.MINUS;
            case "!" -> UNARY_OP.NOT;
            default -> throw new RuntimeException("Unknown unary op: " + opText);
        };
        return new UnaryOpExpr(op, expr);
    }
    
    // --- Call Expressions ---

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
