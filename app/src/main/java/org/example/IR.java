package org.example;

import java.util.*;

public record IR(
    LinkedHashMap<String, Operator> opStore,
    Scope scope
) {
    public record Scope(
        Scope parentScope,
        // scopeId used for Referencing variables
        Integer scopeId,
        // function name
        String funcName,
        // stores and mappings
        LinkedHashMap<String, IR.Var> varMapping,
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
        permits NewVar, Expr, Scoped {}

    public record NewVar(String name, Var v) implements Entry {}

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
    public record Expr(String op, ArrayList<Var> vars) implements Value, Entry {}

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
    public static LinkedHashMap<String, Operator> defaultOperatorMap() {
        // Create an empty LinkedHashMap to preserve insertion order
        LinkedHashMap<String, Operator> map = new LinkedHashMap<>();

        // --- Unary Operators ---
        // "u+" and "u-" are used to distinguish from binary "+" and "-"
        map.put("u+", new Operator(new ArrayList<>(List.of(
                new OpSpec(new ArrayList<>(List.of(INT)), INT),
                new OpSpec(new ArrayList<>(List.of(FLOAT)), FLOAT)
        ))));

        map.put("u-", new Operator(new ArrayList<>(List.of(
                new OpSpec(new ArrayList<>(List.of(INT)), INT),
                new OpSpec(new ArrayList<>(List.of(FLOAT)), FLOAT)
        ))));

        // Using "!" for logical NOT
        map.put("!", new Operator(new ArrayList<>(List.of(
                new OpSpec(new ArrayList<>(List.of(BOOL)), BOOL)
        ))));

        // --- Binary Arithmetic Operators ---
        map.put("+", new Operator(new ArrayList<>(List.of(
                new OpSpec(new ArrayList<>(List.of(INT, INT)), INT),
                new OpSpec(new ArrayList<>(List.of(INT, FLOAT)), FLOAT),
                new OpSpec(new ArrayList<>(List.of(FLOAT, INT)), FLOAT),
                new OpSpec(new ArrayList<>(List.of(FLOAT, FLOAT)), FLOAT),
                new OpSpec(new ArrayList<>(List.of(STRING, STRING)), STRING)
        ))));

        map.put("-", new Operator(new ArrayList<>(List.of(
                new OpSpec(new ArrayList<>(List.of(INT, INT)), INT),
                new OpSpec(new ArrayList<>(List.of(INT, FLOAT)), FLOAT),
                new OpSpec(new ArrayList<>(List.of(FLOAT, INT)), FLOAT),
                new OpSpec(new ArrayList<>(List.of(FLOAT, FLOAT)), FLOAT)
        ))));

        map.put("*", new Operator(new ArrayList<>(List.of(
                new OpSpec(new ArrayList<>(List.of(INT, INT)), INT),
                new OpSpec(new ArrayList<>(List.of(INT, FLOAT)), FLOAT),
                new OpSpec(new ArrayList<>(List.of(FLOAT, INT)), FLOAT),
                new OpSpec(new ArrayList<>(List.of(FLOAT, FLOAT)), FLOAT)
        ))));

        map.put("/", new Operator(new ArrayList<>(List.of(
                new OpSpec(new ArrayList<>(List.of(INT, INT)), FLOAT),
                new OpSpec(new ArrayList<>(List.of(INT, FLOAT)), FLOAT),
                new OpSpec(new ArrayList<>(List.of(FLOAT, INT)), FLOAT),
                new OpSpec(new ArrayList<>(List.of(FLOAT, FLOAT)), FLOAT)
        ))));

        // Using "**" for POW (exponentiation)
        map.put("**", new Operator(new ArrayList<>(List.of(
                new OpSpec(new ArrayList<>(List.of(INT, INT)), FLOAT),
                new OpSpec(new ArrayList<>(List.of(INT, FLOAT)), FLOAT),
                new OpSpec(new ArrayList<>(List.of(FLOAT, INT)), FLOAT),
                new OpSpec(new ArrayList<>(List.of(FLOAT, FLOAT)), FLOAT)
        ))));

        // --- Binary Logical Operators ---
        map.put("&&", new Operator(new ArrayList<>(List.of(
                new OpSpec(new ArrayList<>(List.of(BOOL, BOOL)), BOOL)
        ))));

        map.put("||", new Operator(new ArrayList<>(List.of(
                new OpSpec(new ArrayList<>(List.of(BOOL, BOOL)), BOOL)
        ))));

        // --- Binary Relational Operators ---
        map.put("<", new Operator(new ArrayList<>(List.of(
                new OpSpec(new ArrayList<>(List.of(INT, INT)), BOOL),
                new OpSpec(new ArrayList<>(List.of(FLOAT, FLOAT)), BOOL),
                new OpSpec(new ArrayList<>(List.of(INT, FLOAT)), BOOL),
                new OpSpec(new ArrayList<>(List.of(FLOAT, INT)), BOOL),
                new OpSpec(new ArrayList<>(List.of(STRING, STRING)), BOOL)
        ))));

        map.put("<=", new Operator(new ArrayList<>(List.of(
                new OpSpec(new ArrayList<>(List.of(INT, INT)), BOOL),
                new OpSpec(new ArrayList<>(List.of(FLOAT, FLOAT)), BOOL),
                new OpSpec(new ArrayList<>(List.of(INT, FLOAT)), BOOL),
                new OpSpec(new ArrayList<>(List.of(FLOAT, INT)), BOOL),
                new OpSpec(new ArrayList<>(List.of(STRING, STRING)), BOOL)
        ))));

        map.put(">", new Operator(new ArrayList<>(List.of(
                new OpSpec(new ArrayList<>(List.of(INT, INT)), BOOL),
                new OpSpec(new ArrayList<>(List.of(FLOAT, FLOAT)), BOOL),
                new OpSpec(new ArrayList<>(List.of(INT, FLOAT)), BOOL),
                new OpSpec(new ArrayList<>(List.of(FLOAT, INT)), BOOL),
                new OpSpec(new ArrayList<>(List.of(STRING, STRING)), BOOL)
        ))));

        map.put(">=", new Operator(new ArrayList<>(List.of(
                new OpSpec(new ArrayList<>(List.of(INT, INT)), BOOL),
                new OpSpec(new ArrayList<>(List.of(FLOAT, FLOAT)), BOOL),
                new OpSpec(new ArrayList<>(List.of(INT, FLOAT)), BOOL),
                new OpSpec(new ArrayList<>(List.of(FLOAT, INT)), BOOL),
                new OpSpec(new ArrayList<>(List.of(STRING, STRING)), BOOL)
        ))));

        // --- Equality Operators ---
        map.put("==", new Operator(new ArrayList<>(List.of(
                new OpSpec(new ArrayList<>(List.of(INT, INT)), BOOL),
                new OpSpec(new ArrayList<>(List.of(FLOAT, FLOAT)), BOOL),
                new OpSpec(new ArrayList<>(List.of(STRING, STRING)), BOOL),
                new OpSpec(new ArrayList<>(List.of(INT, FLOAT)), BOOL),
                new OpSpec(new ArrayList<>(List.of(FLOAT, INT)), BOOL),
                new OpSpec(new ArrayList<>(List.of(BOOL, BOOL)), BOOL)
        ))));

        map.put("!=", new Operator(new ArrayList<>(List.of(
                new OpSpec(new ArrayList<>(List.of(INT, INT)), BOOL),
                new OpSpec(new ArrayList<>(List.of(FLOAT, FLOAT)), BOOL),
                new OpSpec(new ArrayList<>(List.of(STRING, STRING)), BOOL),
                new OpSpec(new ArrayList<>(List.of(INT, FLOAT)), BOOL),
                new OpSpec(new ArrayList<>(List.of(FLOAT, INT)), BOOL),
                new OpSpec(new ArrayList<>(List.of(BOOL, BOOL)), BOOL)
        ))));

        // --- Built-in Functions ---
        map.put("input", new Operator(new ArrayList<>(List.of(
                // No arguments, returns a STRING
                new OpSpec(new ArrayList<>(), STRING)
        ))));

        // Return the populated, ordered map
        return map;
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

    private static String mangle(String name, Integer id) {
        return String.format("%s_%d", name, id);
    }

    // copy of Typer.lookupRef, but for mangling
    private static String mangleRef(String ident, Scope scope) {
        var rootScope = scope;

        var currentScope = scope;
        while (true) {
            var mangled = IR.mangle(ident, currentScope.scopeId());
            var find = currentScope.varMapping.get(mangled);

            if (find != null) {
                return mangled;
            }

            if (currentScope.parentScope() == null) {
                var id = rootScope.scopeId();
                throw new RuntimeException(
                    "ref <" + ident + ">@" + id + "can't be found"
                );
            }

            currentScope = currentScope.parentScope();
        }
    }

    private static Value mangleValue(Value val, Scope scope) {
        return switch (val) {
            case Arg arg -> arg;
            case Atom atom -> atom;
            case Ref(String ident) -> new Ref(IR.mangleRef(ident, scope));
            case Expr(var op, var vars) -> {
                var newVars = new ArrayList<Var>();
                for (var v: vars) {
                    newVars.add(IR.mangleVar(v, scope));
                }
                yield new Expr(op, newVars);
            }
        };
    }

    private static Var mangleVar(Var v, Scope scope) {
        return new Var(
            IR.mangleValue(v.val(), scope),
            v.type(),
            v.span(),
            v.mutable()
        );
    }

    // make it so every variable that should be unique is, in fact, unique
    //
    // mutates passed Scope *in-place* and returns mutated version
    public static Scope discriminateScopeVars(Scope scope) {
        var mangleMap = new LinkedHashMap<String, String>();

        for (var plainName: scope.varMapping().keySet()) {
            var mangled = IR.mangle(plainName, scope.scopeId());
            mangleMap.put(plainName, mangled);
        }

        for (var mangler: mangleMap.entrySet()) {
            var plainName = mangler.getKey();
            var mangled = mangler.getValue();

            // add new key with the value from previous key
            scope.varMapping.put(
                mangled, scope.varMapping().get(plainName)
            );
            // remove old key, mapping[plain]
            scope.varMapping.remove(plainName);
        }

        for (int entryIdx = 0; entryIdx < scope.entries.size(); entryIdx++) {
            switch (scope.entries().get(entryIdx)) {
                case NewVar(var name, var v) -> {
                    var mangledName = IR.mangle(name, scope.scopeId());
                    var mangledVar = IR.mangleVar(v, scope);
                    scope.entries().set(entryIdx, new NewVar(
                        mangledName, IR.mangleVar(v, scope)
                    ));
                }
                case Expr(var op, var vars) -> {
                    var newVars = new ArrayList<Var>();
                    for (var v: vars) {
                        newVars.add(IR.mangleVar(v, scope));
                    }
                    scope.entries().set(entryIdx, new Expr(op, newVars));
                }
                case Scoped scoped -> {
                    var newBornVars = new ArrayList<String>();
                    for (var varName: scoped.bornVars()) {
                        newBornVars.add(
                            IR.mangle(varName, scoped.scope().scopeId())
                        );
                    }

                    scope.entries().set(entryIdx, new Scoped(
                        scoped.kind(),
                        newBornVars,
                        scoped.dependencyValue().map(
                            v -> IR.mangleValue(v, scoped.scope())
                        ),
                        IR.discriminateScopeVars(scoped.scope())
                    ));
                }
            }
        }
        return scope;
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
            originalScope.scopeId(),
            originalScope.funcName(),
            originalScope.varMapping(),
            newEntries
        );
    }
}
