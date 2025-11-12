package org.example;

import java.util.*;
import java.io.File;
import java.io.IOException;

import static org.example.IR.SCOPE_KIND.CASE_BRANCH;

record OpInfo(String op, String token) {}

public class Translator {
    private final IR ir;
    private final Map<String, PostfixModule> modules = new HashMap<>();

    public Translator(IR ir) {
        this.ir = ir;
    }

    private PostfixModule getModule(String funcName) {
        String moduleKey = funcName.equals("main") ? "main" : "main$" + funcName;
        return modules.get(moduleKey);
    }

    /**
     * The main translation entry point.
     * Iterates over global scope entries, creating a Postfix module for each function.
     * Handles global variables, ensures functions are visible only after declaration.
     */
    public void generate(String baseDir) throws IOException {
        IR.Scope globalScope = this.ir.scope();

        List<String> currentlyVisibleGlobalVars = new ArrayList<>();
        Map<String, IR.Operator> availableGlobalFunctions = new LinkedHashMap<>();
        Map<String, PostfixModule> allModules = new HashMap<>();

        for (IR.Entry entry : globalScope.entries()) {
            if (entry instanceof IR.NewVar newVar) {
                if (!newVar.v().mutable()) {
                    currentlyVisibleGlobalVars.add(newVar.name());
                }
            } else if (
                entry instanceof IR.Scoped scoped
                    && scoped.kind() == IR.SCOPE_KIND.FUN
            ) {
                String funcName = scoped.scope().funcName();
                String moduleName =
                    funcName.equals("main")
                        ? "main"
                        : "main$" + funcName;
                PostfixModule module = new PostfixModule(moduleName);
                allModules.put(moduleName, module);

                collectAllVars(scoped.scope(), module);

                if (!funcName.equals("main")) {
                    module.globalVars.addAll(new ArrayList<>(currentlyVisibleGlobalVars));
                }

                // add curr func for recursion
                if (ir.opStore().containsKey(funcName) && !availableGlobalFunctions.containsKey(funcName)) {
                    availableGlobalFunctions.put(funcName, ir.opStore().get(funcName));
                }
                // add to .func (only those that have already been init-ed)
                for (Map.Entry<String, IR.Operator> opEntry : availableGlobalFunctions.entrySet()) {
                    String opKey = opEntry.getKey();
                    if (Set.of("+","-","*","/","**","&&","||","<","<=",">",">=","==","!=","!","main").contains(opKey)) continue;

                    IR.OpSpec spec = opEntry.getValue().alternatives().getFirst();
                    String retType = spec.returnType().toString().toLowerCase();
                    int arity = spec.argTypes().size();
                    module.funcDeclarations.add(new FuncDeclaration(opKey, retType, arity));
                }

                if (funcName.equals("main")) {
                    translateMainScope(scoped.scope(), module, globalScope, globalScope.entries());

                    // all globalScope.varMapping -> main .vars
                    for (Map.Entry<String, IR.Var> varEntry : globalScope.varMapping().entrySet()) {
                        if (!varEntry.getValue().mutable()) {
                            module.variables.putIfAbsent(varEntry.getKey(), varEntry.getValue().type().toString().toLowerCase());
                        }
                    }
                } else {
                    translateScope(scoped.scope(), module, globalScope);
                }
            }
        }

        // clear output directory beforehand
        var dir = new File(baseDir);
        var entries = dir.list();
        for (String s : entries) {
            File oldFile = new File(dir.getPath(), s);
            oldFile.delete();
        }

        // write all modules
        for (PostfixModule module : allModules.values()) {
            module.writeToFile(baseDir);
            printModuleInfo(module);
        }
    }

    /**
     * Translates the main scope of the program into Postfix code.
     * Handles initialization of global variables, local variable declarations,
     * and executes all statements in the main block (including nested structures).
     */
    private void translateMainScope(
        IR.Scope mainScope,
        PostfixModule module,
        IR.Scope rootScope,
        List<IR.Entry> globalEntries
    ) {

        // init global let     here?
        for (IR.Entry entry : globalEntries) {
            if (entry instanceof IR.NewVar newVar) {
                if (!newVar.v().mutable()) {
                    module.addCode(newVar.name(), "l-val");
                    translateValue(newVar.v().val(), module, rootScope);
                    module.addCode(":=", "assign_op");
                }
            }
        }

        // local .vars
        for (Map.Entry<String, IR.Var> entry : mainScope.varMapping().entrySet()) {
            if (entry.getValue().val() instanceof IR.Arg) {
                module.variables.put(
                    entry.getKey(),
                    entry.getValue().type().toString().toLowerCase()
                );
            } else {
                module.variables.put(
                    entry.getKey(),
                    entry.getValue().type().toString().toLowerCase()
                );
            }
        }

        for (int i = 0; i < mainScope.entries().size(); i++) {
            IR.Entry entry = mainScope.entries().get(i);

            if (entry instanceof IR.NewVar newVar) {
                // ignore global let (bc already init-ed)
                boolean isGlobalLet = rootScope
                    .varMapping()
                    .containsKey(newVar.name())
                    && !newVar.v().mutable();

                if (isGlobalLet) {
                    continue;
                }

                module.addCode(newVar.name(), "l-val");
                translateValue(newVar.v().val(), module, mainScope);
                module.addCode(":=", "assign_op");

            } else if (entry instanceof IR.Expr expr) {
                translateAction(expr, module, mainScope);

            } else if (entry instanceof IR.Scoped scoped) {
                if (scoped.kind() == CASE_BRANCH) {
                    List<IR.Scoped> switchCases = new ArrayList<>();
                    switchCases.add(scoped);

                    // look for all CASE_BRANCH
                    int j = i + 1;
                    while (
                        j < mainScope.entries().size()
                        &&
                        mainScope.entries().get(j) instanceof IR.Scoped nextScoped
                        &&
                        (nextScoped.kind() == CASE_BRANCH)
                    )
                    {
                        switchCases.add(nextScoped);
                        j++;
                    }
                    i = j - 1;
                    translateSwitch(switchCases, module, mainScope);

                } else {
                    translateScopedInstruction(scoped, module, mainScope, i);
                }
            }
        }
    }

    // other scopes (not main)
    private void translateScope(IR.Scope scope, PostfixModule module, IR.Scope rootScope) {
        // local .vars
        for (Map.Entry<String, IR.Var> entry : scope.varMapping().entrySet()) {
            if (entry.getValue().val() instanceof IR.Arg) {
                module.variables.put(
                    entry.getKey(),
                    entry.getValue().type().toString().toLowerCase()
                );
            } else if (entry.getValue().mutable()) {
                module.variables.put(
                    entry.getKey(),
                    entry.getValue().type().toString().toLowerCase()
                );
            }
        }

        for (int i = 0; i < scope.entries().size(); i++) {
            IR.Entry entry = scope.entries().get(i);

            // Switch on the type of 'entry'
            switch (entry) {
                case IR.NewVar newVar -> {
                    // this funcs local vars
                    module.addCode(newVar.name(), "l-val");
                    translateValue(newVar.v().val(), module, scope);
                    module.addCode(":=", "assign_op");
                }
                case IR.Expr expr -> {
                    translateAction(expr, module, scope);
                }
                case IR.Scoped scoped when scoped.kind() == CASE_BRANCH -> {
                    List<IR.Scoped> switchCases = new ArrayList<>();
                    switchCases.add(scoped);

                    // look for all CASE_BRANCH
                    int j = i + 1;
                    while (
                        j < scope.entries().size()
                        &&
                        scope.entries().get(j) instanceof IR.Scoped nextScoped
                        &&
                        (nextScoped.kind() == CASE_BRANCH))
                    {
                        switchCases.add(nextScoped);
                        j++;
                    }
                    i = j - 1;
                    translateSwitch(switchCases, module, scope);
                }
                case IR.Scoped scoped -> {
                    translateScopedInstruction(scoped, module, scope, i);
                }
            }
        }
    }

    /**
     * Recursively collects all variables (including those born in nested scopes like WHILE or IF blocks)
     */
    private void collectAllVars(IR.Scope scope, PostfixModule module) {
        for (Map.Entry<String, IR.Var> entry : scope.varMapping().entrySet()) {
            module.variables.putIfAbsent(
                entry.getKey(),
                entry.getValue().type().toString().toLowerCase()
            );
        }

        for (IR.Entry entry : scope.entries()) {
            if (entry instanceof IR.NewVar newVar) {
                module.variables.putIfAbsent(
                    newVar.name(),
                    newVar.v().type().toString().toLowerCase()
                );
            } else if (entry instanceof IR.Scoped scopedEntry) {
                collectAllVars(scopedEntry.scope(), module);
            }
        }
    }

    /**
     * Translates a single action or expression (e.g. assignment, print, return)
     * into Postfix instructions.
     */
    private void translateAction(IR.Expr expr, PostfixModule module, IR.Scope scope) {
        switch (expr.op()) {
            case "$assign" -> {
                IR.Var target = expr.vars().get(0);
                IR.Var value = expr.vars().get(1);
                module.addCode(
                    target.val() instanceof IR.Ref ref
                        ? ref.ident()
                        : "",
                    "l-val"
                );
                translateValue(value.val(), module, scope);
                module.addCode(":=", "assign_op");
            }
            case "$return" -> {
                translateValue(expr.vars().getFirst().val(), module, scope);
                module.addCode("RET", "RET");
            }
            case "print" -> { // TODO print on 1 line
                for (IR.Var var : expr.vars()) {
                    translateValue(var.val(), module, scope);
                    module.addCode("OUT", "out_op");
                }
            }
            default ->
                // FuncCallStmt/ Expression
                translateValue(expr, module, scope);
        }
    }

    /**
     * Translates a scoped instruction like IF, WHILE, or FOR into Postfix form.
     * Identifies matching ELSE branches
     * CASE branches was grouped in translateScope and translateMainScope.
     */
    private void translateScopedInstruction(
        IR.Scoped scoped, PostfixModule module, IR.Scope scope, int index
    ) {
        switch (scoped.kind()) {
            case IF_BRANCH -> {
                // look for ELSE_BRANCH
                IR.Scoped elseBranch = scope.entries().stream()
                        .skip(index + 1)
                        .filter(
                            e -> e instanceof IR.Scoped nextScoped
                                && nextScoped.kind() == IR.SCOPE_KIND.ELSE_BRANCH
                        )
                        .map(e -> (IR.Scoped) e)
                        .findFirst().orElse(null);

                translateIfStmt(scoped, module, scope, elseBranch);
            }
            case WHILE -> translateWhileStmt(scoped, module, scope);
            case FOR -> translateForStmt(scoped, module, scope);
            case CASE_BRANCH -> {}
            default -> {}
        }
    }

    /**
     * Translates a value (atom, variable reference, or expression)
     */
    private void translateValue(
        IR.Value value,
        PostfixModule module,
        IR.Scope scope
    ) {
        if (value instanceof IR.Atom atom) {
            module.addCode(atom.val(), atom.type().toString().toLowerCase());
            return;
        }

        if (value instanceof IR.Ref ref) {
            module.addCode(ref.ident(), "r-val");
            return;
        }

        if (value instanceof IR.Expr expr) {
            String op = expr.op();

            if (op.startsWith("$case")) {
                translateCaseExpr(expr, module, scope);
                return;
            }

            translateExprArgs(expr, module, scope);
            OpInfo opInfo = getOpToken(expr);
            op = opInfo.op();
            String token = opInfo.token();

            if (op.equals("u+")) return;
            if (op.equals("input")) op = "INP";

            module.addCode(op, token);
        }
    }

    /**
     * Automatically casts arguments to FLOAT if needed
     */
    private void translateExprArgs(
        IR.Expr expr, PostfixModule module, IR.Scope scope
    ) {
        String op = expr.op();
        boolean isMath = List.of("+", "-", "*", "/", "**").contains(op);

        boolean toFloat = "**".equals(op) ||
                (isMath
                    && expr.vars().stream().anyMatch(v ->
                        v.type() == IR.TY.FLOAT
                            ||
                        (v.val() instanceof IR.Expr e && "**".equals(e.op()))
                ));

        if (toFloat) {
            translateWithCast(expr.vars(), module, scope, IR.TY.FLOAT);
        } else {
            // for unary operators 1 argument
            for (IR.Var v : expr.vars()) {
                if (v != null && v.val() != null) {
                    translateValue(v.val(), module, scope);
                }
            }
        }
    }

    /**
     * Maps an operator to its postfix equivalent and token type for a given expression
     */
    private OpInfo getOpToken(IR.Expr expr) {
        String op = expr.op();

        String token = switch (op) {
            case "+", "-", "*", "/", "%" -> {
                boolean concat = op.equals("+") &&
                        expr.vars().size() == 2 &&
                        expr.vars().get(0).type() == IR.TY.STRING &&
                        expr.vars().get(1).type() == IR.TY.STRING;
                if (concat) {
                    op = "CAT";
                }
                yield concat ? "cat_op" : "math_op";
            }
            case "**" -> {
                op = "^";
                yield "pow_op";
            }
            case "u-", "u+" -> op;
            case "==", "!=", "<", "<=", ">", ">=" -> "rel_op";
            case "&&" -> {
                op = "AND";
                yield "bool_op";
            }
            case "||" -> {
                op = "OR";
                yield "bool_op";
            }
            case "!" -> {
                op = "NOT";
                yield "bool_op";
            }
            case "input" -> "inp_op";
            default -> "CALL";
        };

        return new OpInfo(op, token);
    }


    /**
     * Translates operands and inserts an implicit type conversion instruction (i2f conv)
     */
    private void translateWithCast(
        List<IR.Var> vars,
        PostfixModule module,
        IR.Scope scope,
        IR.TY targetType
    ) {
        for (IR.Var arg : vars) {
            translateValue(arg.val(), module, scope);

            if (arg.type() == IR.TY.INT && targetType == IR.TY.FLOAT) {
                module.addCode("i2f", "conv");
            }
        }
    }


    //  IfStmt
    private void translateIfStmt(
        IR.Scoped thenScoped,
        PostfixModule module,
        IR.Scope parentScope,
        IR.Scoped elseScoped
    ) {
        // cond
        if (thenScoped.dependencyValue().isEmpty()) {
            throw new IllegalStateException(
                "IF_BRANCH Scoped must contain condition (dependencyValue)"
            );
        }
        translateValue(thenScoped.dependencyValue().get(), module, parentScope);

        String labelElse = module.createLabel();
        String labelEnd = module.createLabel();

        // JF
        String targetLabelIfFalse = (elseScoped != null) ? labelElse : labelEnd;

        module.addCode(targetLabelIfFalse, "label");
        module.addCode("JF", "jf");

        // then block
        translateScope(thenScoped.scope(), module, parentScope);

        // JMP to else
        if (elseScoped != null) {
            module.addCode(labelEnd, "label");
            module.addCode("JMP", "jump");
        }

        // label for else
        if (elseScoped != null) {
            module.setLabelValue(labelElse);
            module.addCode(labelElse, "label");
            module.addCode(":", "colon");
            // elseblock
            translateScope(elseScoped.scope(), module, parentScope);
        }

        // end
        module.setLabelValue(labelEnd);
        module.addCode(labelEnd, "label");
        module.addCode(":", "colon");
    }

    // WhileStmt
    private void translateWhileStmt(
        IR.Scoped scopedEntry, PostfixModule module, IR.Scope parentScope
    ) {
        String labelLoop = module.createLabel();
        String labelEnd = module.createLabel();

        module.setLabelValue(labelLoop);
        module.addCode(labelLoop, "label");
        module.addCode(":", "colon");

        translateValue(scopedEntry.dependencyValue().get(), module, parentScope);

        module.addCode(labelEnd, "label");
        module.addCode("JF", "jf");

        translateScope(scopedEntry.scope(), module, parentScope);

        // JMP to start
        module.addCode(labelLoop, "label");
        module.addCode("JMP", "jump");

        module.setLabelValue(labelEnd);
        module.addCode(labelEnd, "label");
        module.addCode(":", "colon");
    }

    // ForStmt
    private void translateForStmt(
        IR.Scoped scopedEntry, PostfixModule module, IR.Scope parentScope
    ) {
        if (scopedEntry.dependencyValue().isEmpty()) {
            throw new IllegalStateException(
                "FOR Scoped must contain iterable definition."
            );
        }

        String iterVar = scopedEntry.bornVars().getFirst();
        IR.Value iterable = scopedEntry.dependencyValue().get();
        // range(A, B, C)
        if (iterable instanceof IR.Expr expr && expr.op().equals("$iterRange")) {
            IR.Var start = expr.vars().get(0);
            IR.Var end = expr.vars().get(1);
            IR.Var step = expr.vars().get(2);

            String labelLoop = module.createLabel();
            String labelEnd = module.createLabel();

            // iterVar = start
            module.addCode(iterVar, "l-val");
            translateValue(start.val(), module, parentScope);
            module.addCode(":=", "assign_op");

            module.setLabelValue(labelLoop);
            module.addCode(labelLoop, "label");
            module.addCode(":", "colon");

            // iterVar < end
            module.addCode(iterVar, "r-val");
            translateValue(end.val(), module, parentScope);
            module.addCode("<", "rel_op");

            // JF
            module.addCode(labelEnd, "label");
            module.addCode("JF", "jf");

            translateScope(scopedEntry.scope(), module, parentScope);
            // iterVar += step
            module.addCode(iterVar, "l-val");
            module.addCode(iterVar, "r-val");
            translateValue(step.val(), module, parentScope);
            module.addCode("+", "math_op");
            module.addCode(":=", "assign_op");

            // JMP back to start
            module.addCode(labelLoop, "label");
            module.addCode("JMP", "jump");

            module.setLabelValue(labelEnd);
            module.addCode(labelEnd, "label");
            module.addCode(":", "colon");

        } else if (
            iterable instanceof IR.Atom
            || iterable instanceof IR.Ref
            || iterable instanceof IR.Expr
        ) {
            // TODO for str

        }  else {
            throw new IllegalArgumentException(
                "Unsupported iterable for FOR loop."
            );
        }
    }

    // SwitchStmt
    private void translateSwitch(
        List<IR.Scoped> cases, PostfixModule module, IR.Scope parentScope
    ) {
        String labelEnd = module.createLabel();

        for (int i = 0; i < cases.size(); i++) {
            IR.Scoped caseScoped = cases.get(i);

            String labelNextCase = (i < cases.size() - 1)
                    ? module.createLabel()
                    : labelEnd;

            // $caseIs/Of/In condition
            if (caseScoped.dependencyValue().isPresent()) {
                translateValue(
                    caseScoped.dependencyValue().get(), module, parentScope
                );

                module.addCode(labelNextCase, "label");
                module.addCode("JF", "jf");
            }

            translateScope(caseScoped.scope(), module, parentScope); // body

            if (i < cases.size() - 1) {
                module.addCode(labelEnd, "label");
                module.addCode("JMP", "jump");
            }

        // label for next case or end
            module.setLabelValue(labelNextCase);
            module.addCode(labelNextCase, "label");
            module.addCode(":", "colon");
        }

        // in case its the last el or default case
        if (
            !module.labels.containsKey(labelEnd)
            || module.labels.get(labelEnd) == -1
        ) {
            module.setLabelValue(labelEnd);
            module.addCode(labelEnd, "label");
            module.addCode(":", "colon");
        }
    }

    /**
     * Translates special $case* expressions
     * - $caseIs → checks if target == constant
     * - $caseIn → checks if a target value is inside a range
     * - $caseOf → checks if target equals one of several constants
     * <p>
     * The generated postfix code pushes comparison results (true/false) to the stack.
     */
    private void translateCaseExpr(
        IR.Expr expr, PostfixModule module, IR.Scope scope
    ) {
        String op = expr.op();
        IR.Var target = expr.vars().get(0);

        switch (op) {
            case "$caseIs" -> {
                translateValue(target.val(), module, scope);
                translateValue(expr.vars().get(1).val(), module, scope);
                module.addCode("==", "rel_op");
            }
            case "$caseIn" -> {
                IR.Var min = expr.vars().get(1);
                IR.Var max = expr.vars().get(2);

                translateValue(min.val(), module, scope);
                translateValue(target.val(), module, scope);
                module.addCode("<=", "rel_op");

                translateValue(target.val(), module, scope);
                translateValue(max.val(), module, scope);
                module.addCode("<", "rel_op");

                module.addCode("AND", "bool_op");
            }
            case "$caseOf" -> {
                List<IR.Var> consts = expr.vars().subList(1, expr.vars().size());
                if (consts.isEmpty()) {
                    module.addCode("false", "bool");
                    return;
                }

                translateValue(target.val(), module, scope);
                translateValue(consts.getFirst().val(), module, scope);
                module.addCode("==", "rel_op");

                for (int i = 1; i < consts.size(); i++) {
                    translateValue(target.val(), module, scope);
                    translateValue(consts.get(i).val(), module, scope);
                    module.addCode("==", "rel_op");
                    module.addCode("OR", "bool_op");
                }
            }
        }
    }

    private void printModuleInfo(PostfixModule module) {
        final String RESET = "\u001B[0m";
        final String MAGENTA = "\u001B[35m";
        final String BLUE = "\u001B[34m";
        final String BOLD = "\u001B[1m";

        System.out.printf("\n"+BOLD + BLUE + "%15s%s\n" + RESET, "", "МОДУЛЬ: " + module.moduleName.toUpperCase());

        System.out.println(MAGENTA + "ТАБЛИЦЯ МІТОК" + RESET);
        System.out.printf("%-20s | %-10s\n", "Label", "Value");
        System.out.println("------------------------------");

        module.labels.entrySet().stream()
                .filter(e -> e.getValue() != -1)
                .forEach(e -> System.out.printf("%-20s | %-10d\n", e.getKey(), e.getValue()));

        System.out.println("\n" + MAGENTA + "ТАБЛИЦЯ ІДЕНТИФІКАТОРІВ" + RESET);
        System.out.printf("%-5s | %-15s | %-10s | %-10s\n", "Idx", "Ident", "Type", "Value");
        System.out.println("------------------------------------------------");

        int index = 0;
        for (Map.Entry<String, String> entry : module.variables.entrySet()) {
            System.out.printf("%-5d | %-15s | %-10s | %-10s\n",
                    index++, entry.getKey(), entry.getValue(), "undefined");
        }

        System.out.println("\n" + MAGENTA + "ПОСТФІКСНИЙ КОД (ПОЛІЗ)" + RESET);
        System.out.printf("%-5s | %-15s | %-15s\n", "№", "Lexeme", "Token");
        System.out.println("----------------------------------------------");

        for (int i = 0; i < module.code.size(); i++) {
            PostfixInstruction inst = module.code.get(i);
            String lexeme = inst.lexeme();
//            if ("string".equals(inst.token())) {
//                lexeme = "\"" + lexeme + "\"";
//            }
            System.out.printf("%-5d | %-15s | %-15s\n", i, lexeme, inst.token());
        }

        System.out.printf("Постфіксний код збережено у файлі: %s.postfix\n", module.moduleName);
    }

}
