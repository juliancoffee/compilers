package org.example;

import java.util.*;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;

// Assuming necessary imports for ST.* and Pair are present.

/**
 * Pretty-prints the *structure* of the AST in a human-readable tree format.
 * This version also uses a lineIndex to print (line:column) span
 * information for each statement.
 */
class PrinterST {

    private final StringBuilder sb = new StringBuilder();
    private int indentLevel = 0;
    private static final String INDENT_CHAR = "  "; // 2 spaces per indent level

    private final ArrayList<Integer> lineIndex;

    public PrinterST(ArrayList<Integer> lineIndex) {
        this.lineIndex = (lineIndex != null && !lineIndex.isEmpty()) ? lineIndex : new ArrayList<>();
        if (this.lineIndex.isEmpty() || this.lineIndex.get(0) != 0) {
            this.lineIndex.add(0, 0); // Ensure line 1 starts at offset 0
        }
    }

    private String getLocation(int offset) {
        int searchResult = Collections.binarySearch(lineIndex, offset);
        int lineIdx = (searchResult >= 0) ? searchResult : -(searchResult + 1) - 1;
        if (lineIdx < 0) lineIdx = 0;

        int line = lineIdx + 1;
        int col = offset - lineIndex.get(lineIdx);

        return line + ":" + col;
    }

    private String getSpanLocation(Pair<Integer, Integer> span) {
        if (span == null) {
            return "";
        }
        // Use getLocation for both start and end offsets (end is inclusive)
        return " @ " + getLocation(span.first()) + " - " + getLocation(span.second());
    }

    public String print(ST ast) {
        sb.setLength(0);
        indentLevel = 0;

        // Root ST node doesn't have a single span
        printNode("ST", new Pair<>(1, 1));
        increaseIndent();

        for (int i = 0; i < ast.stmts().size(); i++) {
            ST.TopLevelStmt stmt = ast.stmts().get(i);
            Pair<Integer, Integer> span = ast.spans().size() > i ? ast.spans().get(i) : null;
            print(stmt, span); // Pass the span down
        }
        decreaseIndent();
        return sb.toString();
    }

    // --- Indentation & Node Helpers ---

    private void increaseIndent() { indentLevel++; }
    private void decreaseIndent() { indentLevel--; }

    private void printLine(String line) {
        sb.append(INDENT_CHAR.repeat(indentLevel)).append(line).append("\n");
    }

    private void printNode(String nodeName, Pair<Integer, Integer> span, String... fields) {
        StringBuilder fieldsStr = new StringBuilder();
        if (fields.length > 0) {
            fieldsStr.append(" (");
            fieldsStr.append(String.join(", ", fields));
            fieldsStr.append(")");
        }
        String loc = getSpanLocation(span);
        printLine(nodeName + fieldsStr + loc);
    }

    private void printNode(String nodeName, String... fields) {
        printNode(nodeName, null, fields);
    }

    // --- Dispatcher Methods ---

    private void print(ST.Stmt stmt, Pair<Integer, Integer> span) {
        switch (stmt) {
            case ST.LetStmt s -> print(s, span);
            case ST.FuncStmt s -> print(s, span);
            case ST.AssignStmt s -> print(s, span);
            case ST.VarStmt s -> print(s, span);
            case ST.PrintStmt s -> print(s, span);
            case ST.FuncCallStmt s -> print(s, span);
            case ST.ReturnStmt s -> print(s, span);
            case ST.ForStmt s -> print(s, span);
            case ST.IfStmt s -> print(s, span);
            case ST.WhileStmt s -> print(s, span);
            case ST.SwitchStmt s -> print(s, span);
        }
    }

    private void print(ST.Expression expr) {
        switch (expr) {
            case ST.BinOpExpr e -> print(e);
            case ST.FuncCallExpr e -> print(e);
            case ST.IdentExpr e -> print(e);
            case ST.UnaryOpExpr e -> print(e);
            case ST.IntLiteralExpr e -> print(e);
            case ST.FloatLiteralExpr e -> print(e);
            case ST.StrLiteralExpr e -> print(e);
            case ST.BoolLiteralExpr e -> print(e);
        }
    }

    private void print(ST.Iter iter) {
        switch (iter) {
            case ST.RangeExpr r -> print(r);
            case ST.Expression e -> print(e);
        }
    }

    private void print(ST.CaseStmt caseStmt) {
        switch (caseStmt) {
            case ST.ValueCase c -> print(c);
            case ST.DefaultCase c -> print(c);
        }
    }

    private void print(ST.Comparator comp) {
        switch (comp) {
            case ST.ConstComp c -> print(c);
            case ST.SeqComp c -> print(c);
            case ST.RangeComp c -> print(c);
        }
    }

    // --- Node Printers (with Spans) ---

    private void print(ST.Block block) {
        printNode("Block");
        increaseIndent();
        for (int i = 0; i < block.stmts().size(); i++) {
            ST.Stmt s = block.stmts().get(i);
            Pair<Integer, Integer> span = block.spans().size() > i ? block.spans().get(i) : null;
            print(s, span);
        }
        decreaseIndent();
    }

    private void print(ST.FuncStmt stmt, Pair<Integer, Integer> span) {
        String type = stmt.returnType().map(Object::toString).orElse("void");
        printNode("FuncStmt", span, "funcName=" + stmt.funcName(), "returnType=" + type);

        increaseIndent();
        if (!stmt.paramList().isEmpty()) {
            printNode("Parameters");
            increaseIndent();
            for (var param : stmt.paramList()) {
                printNode("Param", "name=" + param.first(), "type=" + param.second());
            }
            decreaseIndent();
        }

        print(stmt.block());
        decreaseIndent();
    }

    private void print(ST.LetStmt stmt, Pair<Integer, Integer> span) {
        String type = stmt.letType().map(Object::toString).orElse("inferred");
        printNode("LetStmt", span, "letName=" + stmt.letName(), "letType=" + type);
        increaseIndent();
        print(stmt.expr());
        decreaseIndent();
    }

    private void print(ST.VarStmt stmt, Pair<Integer, Integer> span) {
        String type = stmt.letType().map(Object::toString).orElse("inferred");
        printNode("VarStmt", span, "letName=" + stmt.letName(), "letType=" + type);
        increaseIndent();
        print(stmt.expr());
        decreaseIndent();
    }

    private void print(ST.AssignStmt stmt, Pair<Integer, Integer> span) {
        printNode("AssignStmt", span, "assignIdent=" + stmt.assignIdent());
        increaseIndent();
        print(stmt.expr());
        decreaseIndent();
    }

    private void print(ST.PrintStmt stmt, Pair<Integer, Integer> span) {
        printNode("PrintStmt", span);
        increaseIndent();
        for (ST.Expression e : stmt.printExprs()) {
            print(e);
        }
        decreaseIndent();
    }

    private void print(ST.FuncCallStmt stmt, Pair<Integer, Integer> span) {
        printNode("FuncCallStmt", span, "callIdent=" + stmt.callIdent());
        increaseIndent();
        if (!stmt.args().isEmpty()) {
            printNode("Arguments");
            increaseIndent();
            for (ST.Expression e : stmt.args()) {
                print(e);
            }
            decreaseIndent();
        }
        decreaseIndent();
    }

    private void print(ST.ReturnStmt stmt, Pair<Integer, Integer> span) {
        printNode("ReturnStmt", span);
        increaseIndent();
        print(stmt.returnExpr());
        decreaseIndent();
    }

    private void print(ST.IfStmt stmt, Pair<Integer, Integer> span) {
        printNode("IfStmt", span);
        increaseIndent();

        printNode("Condition");
        increaseIndent();
        print(stmt.ifCond());
        decreaseIndent();

        printNode("Then");
        increaseIndent();
        print(stmt.thenBlock());
        decreaseIndent();

        stmt.elseBlock().ifPresent(elseBlock -> {
            printNode("Else");
            increaseIndent();
            print(elseBlock);
            decreaseIndent();
        });

        decreaseIndent();
    }

    private void print(ST.WhileStmt stmt, Pair<Integer, Integer> span) {
        printNode("WhileStmt", span);
        increaseIndent();

        printNode("Condition");
        increaseIndent();
        print(stmt.whileCond());
        decreaseIndent();

        printNode("Body");
        increaseIndent();
        print(stmt.block());
        decreaseIndent();

        decreaseIndent();
    }

    private void print(ST.ForStmt stmt, Pair<Integer, Integer> span) {
        printNode("ForStmt", span, "forIdent=" + stmt.forIdent());
        increaseIndent();

        printNode("Iterable");
        increaseIndent();
        print(stmt.iterable());
        decreaseIndent();

        printNode("Body");
        increaseIndent();
        print(stmt.block());
        decreaseIndent();

        decreaseIndent();
    }

    private void print(ST.SwitchStmt stmt, Pair<Integer, Integer> span) {
        printNode("SwitchStmt", span);
        increaseIndent();

        printNode("Expression");
        increaseIndent();
        print(stmt.switchExpr());
        decreaseIndent();

        printNode("Cases");
        increaseIndent();
        for (ST.CaseStmt c : stmt.cases()) {
            print(c);
        }
        decreaseIndent();

        decreaseIndent();
    }

    private void print(ST.ValueCase stmt) {
        printNode("ValueCase");
        increaseIndent();

        printNode("Comparator");
        increaseIndent();
        print(stmt.comparator());
        decreaseIndent();

        printNode("Body");
        increaseIndent();
        print(stmt.block());
        decreaseIndent();

        decreaseIndent();
    }

    private void print(ST.DefaultCase stmt) {
        printNode("DefaultCase");
        increaseIndent();
        print(stmt.block());
        decreaseIndent();
    }

    // --- Comparators (No Spans) ---

    private void print(ST.ConstComp comp) {
        printNode("ConstComp");
        increaseIndent();
        print(comp.literal());
        decreaseIndent();
    }

    private void print(ST.SeqComp comp) {
        printNode("SeqComp");
        increaseIndent();
        for (ST.Expression e : comp.literals()) {
            print(e);
        }
        decreaseIndent();
    }

    private void print(ST.RangeComp comp) {
        printNode("RangeComp", "from=" + comp.from(), "to=" + comp.to());
    }

    // --- Expressions & Iterables (No Spans) ---

    private void print(ST.BinOpExpr expr) {
        printNode("BinOpExpr", "op=" + expr.op());
        increaseIndent();
        print(expr.a());
        print(expr.b());
        decreaseIndent();
    }

    private void print(ST.UnaryOpExpr expr) {
        printNode("UnaryOpExpr", "op=" + expr.op());
        increaseIndent();
        print(expr.expr());
        decreaseIndent();
    }

    private void print(ST.FuncCallExpr expr) {
        printNode("FuncCallExpr", "callIdent=" + expr.callIdent());
        increaseIndent();
        if (!expr.args().isEmpty()) {
            printNode("Arguments");
            increaseIndent();
            for (ST.Expression e : expr.args()) {
                print(e);
            }
            decreaseIndent();
        }
        decreaseIndent();
    }

    private void print(ST.IdentExpr expr) {
        printNode("IdentExpr", "identExpr=" + expr.identExpr());
    }

    private void print(ST.IntLiteralExpr expr) {
        printNode("IntLiteralExpr", "value=" + expr.intLiteral());
    }

    private void print(ST.FloatLiteralExpr expr) {
        printNode("FloatLiteralExpr", "value=" + expr.floatLiteral());
    }

    private void print(ST.StrLiteralExpr expr) {
        printNode("StrLiteralExpr", "value=\"" + expr.strLiteral() + "\"");
    }

    private void print(ST.BoolLiteralExpr expr) {
        printNode("BoolLiteralExpr", "value=" + expr.boolLiteral());
    }

    private void print(ST.RangeExpr expr) {
        printNode("RangeExpr", "from=" + expr.from(), "to=" + expr.to(), "step=" + expr.step());
    }
}
