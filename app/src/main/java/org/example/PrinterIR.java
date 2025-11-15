package org.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A pretty printer for the IR structure.
 * Can optionally resolve character spans to (line:column) positions
 * if a lineIndex is provided.
 */
public class PrinterIR {

    private final StringBuilder sb = new StringBuilder();
    private final ArrayList<Integer> lineIndex;

    /**
     * Creates a pretty printer without line/column resolution.
     * Spans will be printed as raw offsets.
     */
    public PrinterIR() {
        this(null);
    }

    /**
     * Creates a pretty printer that resolves spans to (line:column) positions.
     * @param lineIndex An ArrayList where each element is the character offset
     * of the beginning of a new line. Must start with 0.
     */
    public PrinterIR(ArrayList<Integer> lineIndex) {
        this.lineIndex = lineIndex;
    }

    /**
     * Main entry point. Pass the top-level IR object here.
     * @param ir The IR to print.
     * @return A formatted, indented string representation of the IR.
     */
    public String print(IR ir) {
        sb.setLength(0); // Clear buffer for a new print job
        printIR(ir, 0);
        return sb.toString();
    }

    private void printIR(IR ir, int level) {
        sb.append(indent(level)).append("IR {\n");

        // Print Operator Store
        sb.append(indent(level + 1)).append("opStore: {\n");
        for (var entry : ir.opStore().entrySet()) {
            printOperator(entry.getKey(), entry.getValue(), level + 2);
        }
        sb.append(indent(level + 1)).append("}\n");

        // Print main scope
        printScope(ir.scope(), level + 1);

        sb.append(indent(level)).append("}\n");
    }

    private void printOperator(String name, IR.Operator op, int level) {
        sb.append(indent(level)).append(name).append(": [\n");
        for (IR.OpSpec spec : op.alternatives()) {
            printOpSpec(spec, level + 1);
        }
        sb.append(indent(level)).append("]\n");
    }

    private void printOpSpec(IR.OpSpec spec, int level) {
        String args = spec.argTypes().stream()
                .map(Enum::toString)
                .collect(Collectors.joining(", "));
        sb.append(indent(level))
          .append(String.format("(%s) -> %s\n", args, spec.returnType()));
    }

    private void printScope(IR.Scope scope, int level) {
        sb.append(indent(level)).append("Scope (&").append(scope.funcName()).append(") {\n");

        // --- Handle parentScope without cyclic reference ---
        String parentName = (scope.parentScope() != null)
                            ? scope.parentScope().funcName()
                            : "null";
        sb.append(indent(level + 1)).append("parent: ").append(parentName).append("\n");
        sb
            .append(indent(level + 1))
            .append("scopeId: ")
            .append(scope.scopeId())
            .append("\n");
        // ---

        // Print Var Mapping
        sb.append(indent(level + 1)).append("varMapping: {\n");
        for (var entry : scope.varMapping().entrySet()) {
            sb.append(indent(level + 2)).append(entry.getKey()).append(" -> ");
            // Use helper to print Var on one line
            sb.append(varToString(entry.getValue())).append("\n");
        }
        sb.append(indent(level + 1)).append("}\n");

        // Print Entries
        sb.append(indent(level + 1)).append("entries: [\n");
        for (IR.Entry entry : scope.entries()) {
            printEntry(entry, level + 2);
        }
        sb.append(indent(level + 1)).append("]\n");

        sb.append(indent(level)).append("}\n");
    }

    private void printEntry(IR.Entry entry, int level) {
        switch (entry) {
            case IR.NewVar newVar -> printNewVar(newVar, level);
            case IR.Expr expr -> printAction(expr, level);
            case IR.Scoped scoped -> printScoped(scoped, level);
            default -> sb.append(indent(level)).append("Unknown Entry\n");
        }
    }

    private void printNewVar(IR.NewVar newVar, int level) {
        sb
            .append(indent(level))
            .append("NewVar(name: ")
            .append(newVar.name())
            .append(", ")
            .append(varToString(newVar.v()))
            .append(")\n");
    }

    private void printAction(IR.Expr expr, int level) {
        sb.append(indent(level)).append("Action");
        sb.append(valueToString(expr));
        sb.append(")\n");
    }

    private void printScoped(IR.Scoped scoped, int level) {
        sb.append(indent(level)).append("Scoped (").append(scoped.kind()).append(") {\n");

        sb.append(indent(level + 1)).append("bornVars: ").append(scoped.bornVars()).append("\n");

        sb.append(indent(level + 1)).append("dependencyValue: ");
        sb.append(scoped.dependencyValue().map(this::valueToString).orElse("Optional.empty"));
        sb.append("\n");

        printScope(scoped.scope(), level + 1);

        sb.append(indent(level)).append("}\n");
    }

    // --- Helper Methods ---

    /**
     * Converts a character offset to a "line:column" string.
     * @param offset The character offset from the start of the file.
     * @return A "line:col" string (1-indexed) or the raw offset if lineIndex is unusable.
     */
    private String getPosition(int offset) {
        if (this.lineIndex == null || this.lineIndex.isEmpty()) {
            return String.valueOf(offset); // Fallback to raw offset
        }

        // Find the line this offset belongs to using binary search
        // `searchResult` = Collections.binarySearch(lineIndex, offset)
        // if `searchResult >= 0` -> `lineStartIdx = searchResult`. (Exact match, offset is start of a line)
        // if `searchResult < 0` -> `insertionPoint = -(searchResult + 1)`.
        //   `lineStartIdx = insertionPoint - 1`. (This is the index of the largest element <= offset)

        int searchResult = Collections.binarySearch(lineIndex, offset);
        int lineStartIdx;

        if (searchResult >= 0) {
            lineStartIdx = searchResult; // Exact match on a newline
        } else {
            int insertionPoint = -(searchResult + 1);
            lineStartIdx = insertionPoint - 1; // The line *before* the insertion point
        }

        // Handle offsets on the first line (before the first indexed newline)
        if (lineStartIdx < 0) {
            // Assumes a misconfigured lineIndex (doesn't start with 0).
            // We'll just call it line 1 and use the offset as the column.
            return String.format("1:%d", offset + 1);
        }

        int line = lineStartIdx + 1; // 1-based line number
        int lineStartOffset = lineIndex.get(lineStartIdx);
        int col = offset - lineStartOffset + 1; // 1-based column number

        return String.format("%d:%d", line, col);
    }

    /**
     * Creates a compact, single-line string representation of a Value.
     */
    private String valueToString(IR.Value value) {
        if (value == null) return "null";

        return switch (value) {
            case IR.Arg arg -> String.format("Arg(%s)", arg.type());
            case IR.Ref ref -> String.format("Ref(%s)", ref.ident());
            case IR.Atom atom -> String.format("Atom(%s, \"%s\")", atom.type(), atom.val());
            case IR.Expr expr -> {
                String args = expr.vars().stream()
                    .map(var -> valueToString(var.val()))
                    .collect(Collectors.joining(", "));
                yield String.format("Expr(op: '%s', args: [%s])", expr.op(), args);
            }
        };
    }

    /**
     * Creates a compact, single-line string representation of a Var.
     * Uses lineIndex to resolve spans if available.
     */
    private String varToString(IR.Var var) {
        if (var == null) return "null";

        String spanStr;
        // Use lineIndex if available and var has a span
        if (this.lineIndex != null && !this.lineIndex.isEmpty() && var.span() != null) {
            spanStr = String.format("%s-%s",
                getPosition(var.span().first()),   // start offset
                getPosition(var.span().second())  // end offset
            );
        } else if (var.span() != null) {
            // Fallback to offsets if no lineIndex
            spanStr = var.span().toString();
        } else {
            // No span info
            spanStr = "N/A";
        }

        return String.format("Var(type: %s, mut: %b, span: %s, val: %s)",
            var.type(),
            var.mutable(),
            spanStr,
            valueToString(var.val())
        );
    }

    /**
     * Helper for indentation.
     */
    private String indent(int level) {
        return "  ".repeat(level);
    }
}
