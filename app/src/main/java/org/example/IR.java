package org.example;

import java.util.*;

public record IR(
    HashMap<String, Operator> opStore,
    Scope scope
) {
    public record Scope(
        Scope parentScope,
        // function name
        String funcName,
        // stores and mappings
        HashMap<String, IR.Var> varMapping,
        // for vars and scopes
        ArrayList<IR.Entry> entries
    ) {}

    public enum TY {
        INT,
        FLOAT,
        BOOL,
        STRING,
        VOID,
    }

    /*
     * Entries
     */
    public sealed interface Entry
        permits NewVar, Action, Scoped {}

    public record NewVar(String name) implements Entry {}
    public record Action(Value bind) implements Entry {}

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
        Optional<Value> dependencyValue,
        Scope scope
    ) implements Entry {}

    /*
     * Variables
     */
    public sealed interface Value
        permits Arg, Ref, Atom, Expr {}

    // Arg
    public record Arg(TY type) implements Value {}
    // ident
    public record Ref(String ident) implements Value {}
    // literal
    public record Atom(TY type, String val) implements Value {}
    // expr
    public record Expr(String op, ArrayList<Var> vars) implements Value {}

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

    // --- Static imports for cleaner map definition ---
    private static final TY INT = TY.INT;
    private static final TY FLOAT = TY.FLOAT;
    private static final TY BOOL = TY.BOOL;
    private static final TY STRING = TY.STRING;
    private static final TY VOID = TY.VOID;

    // --- Implementation ---
    public static String binOpCode(ST.BIN_OP op) {
        return switch (op) {
            case ADD -> "+";
            case SUB -> "-";
            case MUL -> "*";
            case DIV -> "/";
            case POW -> "**";
            case LT -> "<";
            case LE -> "<=";
            case GT -> ">";
            case GE -> ">=";
            case EQ -> "==";
            case NE -> "!=";
            case AND -> "&&";
            case OR -> "||";
        };
    }

    public static String unOpCode(ST.UNARY_OP op) {
        return switch (op) {
            case PLUS -> "u+";  // Using the "u+" prefix from the map
            case MINUS -> "u-"; // Using the "u-" prefix from the map
            case NOT -> "!";
        };
    }

    /**
     * Defines the default signatures for all built-in operators and functions.
     * Note: Unary operators are given a 'u' prefix (e.g., "u+", "u-")
     * to distinguish them from their binary counterparts.
     */
    public static HashMap<String, Operator> defaultOperatorMap() {
        return new HashMap<>(Map.ofEntries(
            // --- Unary Operators ---
            Map.entry("u+", new Operator(new ArrayList<>(List.of(
                new OpSpec(new ArrayList<>(List.of(INT)), INT),
                new OpSpec(new ArrayList<>(List.of(FLOAT)), FLOAT)
            )))),

            Map.entry("u-", new Operator(new ArrayList<>(List.of(
                new OpSpec(new ArrayList<>(List.of(INT)), INT),
                new OpSpec(new ArrayList<>(List.of(FLOAT)), FLOAT)
            )))),

            // Using "!" for logical NOT
            Map.entry("!", new Operator(new ArrayList<>(List.of(
                new OpSpec(new ArrayList<>(List.of(BOOL)), BOOL)
            )))),

            // --- Binary Arithmetic Operators ---
            Map.entry("+", new Operator(new ArrayList<>(List.of(
                new OpSpec(new ArrayList<>(List.of(INT, INT)), INT),
                new OpSpec(new ArrayList<>(List.of(INT, FLOAT)), INT),
                new OpSpec(new ArrayList<>(List.of(FLOAT, INT)), FLOAT),
                new OpSpec(new ArrayList<>(List.of(FLOAT, FLOAT)), FLOAT),
                new OpSpec(new ArrayList<>(List.of(STRING, STRING)), STRING)
            )))),

            Map.entry("-", new Operator(new ArrayList<>(List.of(
                new OpSpec(new ArrayList<>(List.of(INT, INT)), INT),
                new OpSpec(new ArrayList<>(List.of(FLOAT, FLOAT)), FLOAT)
            )))),

            Map.entry("*", new Operator(new ArrayList<>(List.of(
                new OpSpec(new ArrayList<>(List.of(INT, INT)), INT),
                new OpSpec(new ArrayList<>(List.of(FLOAT, FLOAT)), FLOAT)
            )))),

            Map.entry("/", new Operator(new ArrayList<>(List.of(
                new OpSpec(new ArrayList<>(List.of(FLOAT, FLOAT)), FLOAT)
            )))),

            // Using "**" for POW
            Map.entry("**", new Operator(new ArrayList<>(List.of(
                new OpSpec(new ArrayList<>(List.of(INT, INT)), FLOAT),
                new OpSpec(new ArrayList<>(List.of(INT, FLOAT)), FLOAT),
                new OpSpec(new ArrayList<>(List.of(FLOAT, INT)), FLOAT),
                new OpSpec(new ArrayList<>(List.of(FLOAT, FLOAT)), FLOAT)
            )))),

            // --- Binary Logical Operators ---
            Map.entry("&&", new Operator(new ArrayList<>(List.of(
                new OpSpec(new ArrayList<>(List.of(BOOL, BOOL)), BOOL)
            )))),

            Map.entry("||", new Operator(new ArrayList<>(List.of(
                new OpSpec(new ArrayList<>(List.of(BOOL, BOOL)), BOOL)
            )))),

            // --- Binary Relational Operators ---
            Map.entry("<", new Operator(new ArrayList<>(List.of(
                new OpSpec(new ArrayList<>(List.of(INT, INT)), BOOL),
                new OpSpec(new ArrayList<>(List.of(FLOAT, FLOAT)), BOOL)
            )))),

            Map.entry("<=", new Operator(new ArrayList<>(List.of(
                new OpSpec(new ArrayList<>(List.of(INT, INT)), BOOL),
                new OpSpec(new ArrayList<>(List.of(FLOAT, FLOAT)), BOOL)
            )))),

            Map.entry(">", new Operator(new ArrayList<>(List.of(
                new OpSpec(new ArrayList<>(List.of(INT, INT)), BOOL),
                new OpSpec(new ArrayList<>(List.of(FLOAT, FLOAT)), BOOL)
            )))),

            Map.entry(">=", new Operator(new ArrayList<>(List.of(
                new OpSpec(new ArrayList<>(List.of(INT, INT)), BOOL),
                new OpSpec(new ArrayList<>(List.of(STRING, STRING)), BOOL),
                new OpSpec(new ArrayList<>(List.of(FLOAT, FLOAT)), BOOL)
            )))),

            // --- Equality Operators ---
            Map.entry("==", new Operator(new ArrayList<>(List.of(
                new OpSpec(new ArrayList<>(List.of(INT, INT)), BOOL),
                new OpSpec(new ArrayList<>(List.of(FLOAT, FLOAT)), BOOL),
                new OpSpec(new ArrayList<>(List.of(STRING, STRING)), BOOL),
                new OpSpec(new ArrayList<>(List.of(BOOL, BOOL)), BOOL)
            )))),

            Map.entry("!=", new Operator(new ArrayList<>(List.of(
                new OpSpec(new ArrayList<>(List.of(INT, INT)), BOOL),
                new OpSpec(new ArrayList<>(List.of(FLOAT, FLOAT)), BOOL),
                new OpSpec(new ArrayList<>(List.of(STRING, STRING)), BOOL),
                new OpSpec(new ArrayList<>(List.of(BOOL, BOOL)), BOOL)
            )))),

            // --- Assignment Operator ---
            Map.entry(":=", new Operator(new ArrayList<>(List.of(
                new OpSpec(new ArrayList<>(List.of(INT, INT)), VOID),
                new OpSpec(new ArrayList<>(List.of(FLOAT, FLOAT)), VOID),
                new OpSpec(new ArrayList<>(List.of(STRING, STRING)), VOID),
                new OpSpec(new ArrayList<>(List.of(BOOL, BOOL)), VOID)
            )))),

            // --- Built-in Functions ---
            Map.entry("input", new Operator(new ArrayList<>(List.of(
                // No arguments
                new OpSpec(new ArrayList<>(), STRING)
            ))))
        ));
    }

    public static TY typeFromST(ST.TY ty) {
        return switch (ty) {
            case INT -> IR.TY.INT;
            case FLOAT -> IR.TY.FLOAT;
            case BOOL -> IR.TY.BOOL;
            case STRING -> IR.TY.STRING;
            case VOID -> IR.TY.VOID;
        };
    }

    public static Scope nullifyParentScopes(Scope originalScope) {
        // Base case: if the scope is null, there's nothing to do.
        if (originalScope == null) {
            return null;
        }

        // Create a new list to hold the processed entries.
        ArrayList<Entry> newEntries = new ArrayList<>();

        // Iterate through the entries of the original scope.
        for (Entry entry : originalScope.entries()) {
            // Check if the entry is a container for a nested scope.
            if (entry instanceof Scoped scopedEntry) {
                // If it is, recursively call this function on the nested scope.
                Scope nullifiedInnerScope =
                    nullifyParentScopes(scopedEntry.scope());

                // Create a new Scoped entry with the modified (nullified) inner
                // scope.
                Scoped newScopedEntry = new Scoped(
                    scopedEntry.kind(),
                    scopedEntry.bornVars(),
                    scopedEntry.dependencyValue(),
                    nullifiedInnerScope
                );
                newEntries.add(newScopedEntry);
            } else {
                // For other entry types (NewVar, Action), a shallow copy is
                // sufficient as they are immutable and don't contain nested
                // scopes.
                newEntries.add(entry);
            }
        }

        // Create and return the new scope with its own parent set to null.
        // The other fields are from the original scope, and the entries list
        // is the new one we just built.
        return new IR.Scope(
            null, // The core purpose of the function.
            originalScope.funcName(),
            originalScope.varMapping(),
            newEntries
        );
    }
}
