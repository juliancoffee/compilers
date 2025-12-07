package org.example;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class TranslatorJVM {
    private final IR ir;
    private final String className;
    private int labelCounter = 0;

    // Інформація про локальні змінні: index + type
    record VarInfo(int index, IR.TY type) {}

    // Стан транслятора (збільшується/скидається для кожного методу)
    private Map<String, VarInfo> localVarMap = new HashMap<>();
    private int localVarIndexCounter = 0;

    public TranslatorJVM(IR ir, String className) {
        this.ir = ir;
        this.className = className;
    }

    private void cleanDirectory(String dirPath) throws IOException {
        File dir = new File(dirPath);
        if (dir.exists()) {
            File[] entries = dir.listFiles();
            if (entries != null) {
                for (File entry : entries) {
                    if (entry.isDirectory()) {
                        cleanDirectory(entry.getAbsolutePath());
                    }
                    entry.delete();
                }
            }
        } else { dir.mkdirs();}
    }
    // --------------------------
    // Public entry
    // --------------------------
    public void generate(String baseDir) throws IOException {
        cleanDirectory(baseDir);
        String parentDir = new File(baseDir).getParent();
        cleanDirectory(parentDir + "/out");

        IR.Scope globalScope = this.ir.scope();

        new File(baseDir).mkdirs();
        File outputFile = new File(baseDir, className + ".j");

        try (FileWriter writer = new FileWriter(outputFile)) {
            writeClassHeader(writer);
            generateStaticFields(writer, globalScope);
            generateCtor(writer);
            generateGlobalMethods(writer, globalScope);
        }

        System.out.println("Generated JVM bytecode: " + outputFile.getAbsolutePath());
    }

    // --------------------------
    // High-level writing helpers
    // --------------------------
    private void writeClassHeader(FileWriter writer) throws IOException {
        writer.write(".class public " + className + "\n");
        writer.write(".super java/lang/Object\n\n");
    }

    private void generateCtor(FileWriter writer) throws IOException {
        writer.write(".method public <init> : ()V\n");
        writer.write("    .code stack 10 locals 10\n");
        writer.write("        aload_0\n");
        writer.write("        invokespecial Method java/lang/Object <init> ()V\n");
        writer.write("        return\n");
        writer.write("    .end code\n");
        writer.write(".end method\n\n");
    }

    private void generateGlobalMethods(FileWriter writer, IR.Scope globalScope) throws IOException {
        for (IR.Entry entry : globalScope.entries()) {
            if (entry instanceof IR.Scoped scoped && scoped.kind() == IR.SCOPE_KIND.FUN) {
                generateMethod(writer, scoped, globalScope);
            }
        }
    }

    // --------------------------
    // Static fields generation
    // --------------------------
    private void generateStaticFields(FileWriter writer, IR.Scope globalScope) throws IOException {
        for (
            Map.Entry<String, IR.Var> entry
            :
            globalScope.varMapping().entrySet()
        ) {
            IR.Var var = entry.getValue();
            writer
                .write(
                    ".field public static "
                    + entry.getKey() + " " + jvmType(var.type())
                    + "\n"
                );
        }
        writer.write("\n");
    }

    // --------------------------
    // Method generation
    // --------------------------
    private void generateMethod(
        FileWriter writer,
        IR.Scoped methodScoped,
        IR.Scope globalScope
    ) throws IOException {
        IR.Scope methodScope = methodScoped.scope();
        String funcName = methodScope.funcName();

        IR.Operator operator = ir.opStore().get(funcName);
        IR.OpSpec spec = operator.alternatives().getFirst();

        // Reset local var allocation for this method
        localVarMap.clear();
        localVarIndexCounter = 0;

        // Build descriptors
        String argsDescriptor = spec.argTypes().stream()
                .map(TranslatorJVM::jvmType)
                .collect(Collectors.joining());
        String returnDescriptor = jvmType(spec.returnType());
        if (spec.returnType() == IR.TY.VOID) returnDescriptor = "V";

        if (funcName.equals("main")) {
            argsDescriptor = "[Ljava/lang/String;";
            returnDescriptor = "V";
        }

        writer.write(".method public static " + funcName + " : (" + argsDescriptor + ")" + returnDescriptor + "\n");
        writer.write("    .code stack 100 locals 100\n");

        // Register parameters as local variables
        registerMethodArgs(methodScoped, spec, funcName);

        // If main -> init globals
        if (funcName.equals("main")) {
            generateGlobalInit(writer, globalScope);
        }

        // Translate method body
        translateScope(writer, methodScope);

        // Ensure method returns correctly based on spec
        writeMethodReturn(writer, spec, funcName);

        writer.write("    .end code\n");
        writer.write(".end method\n\n");
    }

    private void registerMethodArgs(IR.Scoped methodScoped, IR.OpSpec spec, String funcName) {
        if (funcName.equals("main")) {
            localVarMap.put("args", new VarInfo(localVarIndexCounter++, IR.TY.STRING));
        } else {
            for (int i = 0; i < methodScoped.bornVars().size(); i++) {
                String argName = methodScoped.bornVars().get(i);
                IR.TY argType = spec.argTypes().get(i);

                localVarMap.put(argName, new VarInfo(localVarIndexCounter++, argType));
                if (argType == IR.TY.FLOAT) localVarIndexCounter++; // wide slot
            }
        }
    }

    private void writeMethodReturn(FileWriter writer, IR.OpSpec spec, String funcName) throws IOException {
        IR.TY ret = spec.returnType();
        if (ret == IR.TY.VOID || funcName.equals("main")) {
            writer.write("        return\n");
        } else if (ret == IR.TY.INT || ret == IR.TY.BOOL) {
            writer.write("        ireturn\n");
        } else if (ret == IR.TY.FLOAT) {
            writer.write("        dreturn\n");
        } else if (ret == IR.TY.STRING) {
            writer.write("        areturn\n");
        }
    }

    private void generateGlobalInit(FileWriter writer, IR.Scope globalScope) throws IOException {
        writer.write("        ; --- Global Init ---\n");
        for (IR.Entry entry : globalScope.entries()) {
            if (entry instanceof IR.NewVar(String name, IR.Var v)) {
                translateValue(writer, v.val());
                writer.write("        putstatic Field " + className + " " + name + " " + jvmType(v.type()) + "\n");
            }
        }
        writer.write("        ; --- End Global Init ---\n");
    }

    // --------------------------
    // Scope translation
    // --------------------------
    private void translateScope(FileWriter writer, IR.Scope scope) throws IOException {
        // Register any variables declared in this scope as locals (lazy registration)
        for (Map.Entry<String, IR.Var> entry : scope.varMapping().entrySet()) {
            if (!localVarMap.containsKey(entry.getKey())) {
                IR.TY type = entry.getValue().type();
                localVarMap.put(entry.getKey(), new VarInfo(localVarIndexCounter++, type));
                if (type == IR.TY.FLOAT) localVarIndexCounter++;
            }
        }

        // Iterate entries
        for (int i = 0; i < scope.entries().size(); i++) {
            IR.Entry entry = scope.entries().get(i);

            if (entry instanceof IR.NewVar(String name, IR.Var v)) {
                translateValue(writer, v.val());
                storeVar(writer, name, v.type());
            } else if (entry instanceof IR.Expr expr) {
                translateExprStmt(writer, expr);
            } else if (entry instanceof IR.Scoped scoped) {
                // Handle if + optional else (else may be next entry)
                if (scoped.kind() == IR.SCOPE_KIND.IF_BRANCH) {
                    IR.Scoped elseScoped = null;
                    if (i + 1 < scope.entries().size()) {
                        IR.Entry nextEntry = scope.entries().get(i + 1);
                        if (nextEntry instanceof IR.Scoped nextScoped && nextScoped.kind() == IR.SCOPE_KIND.ELSE_BRANCH) {
                            elseScoped = nextScoped;
                            i++;
                        }
                    }
                    translateIf(writer, scoped, elseScoped);
                }
                // If it's an ELSE branch alone, translate its body
                else if (scoped.kind() == IR.SCOPE_KIND.ELSE_BRANCH) {
                    translateScope(writer, scoped.scope());
                }
                // Switch / case branches
                else if (scoped.kind() == IR.SCOPE_KIND.CASE_BRANCH) {
                    // If dependency missing or not an expression -> just inline scope
                    if (scoped.dependencyValue().isEmpty() || !(scoped.dependencyValue().get() instanceof IR.Expr conditionExpr)) {
                        translateScope(writer, scoped.scope());
                        continue;
                    }

                    // Build a fake target expr (switch will evaluate once)
                    IR.Var targetVarForSwitch = conditionExpr.vars().getFirst();
                    ArrayList<IR.Var> targetVars = new ArrayList<>();
                    targetVars.add(targetVarForSwitch);
                    IR.Expr targetExpr = new IR.Expr("N/A_Target", targetVars);

                    // Collect consecutive CASE branches
                    List<IR.Scoped> switchCases = new ArrayList<>();
                    switchCases.add(scoped);
                    int j = i + 1;
                    while (j < scope.entries().size() && scope.entries().get(j) instanceof IR.Scoped nextScoped
                            && nextScoped.kind() == IR.SCOPE_KIND.CASE_BRANCH) {
                        switchCases.add(nextScoped);
                        j++;
                    }
                    i = j - 1;

                    translateSwitch(writer, targetExpr, switchCases);
                }
                // Other scoped constructs (while/for/...)
                else {
                    translateScoped(writer, scoped);
                }
            }
        }
    }

    // --------------------------
    // Statement-level translation
    // --------------------------
    private void translateExprStmt(FileWriter writer, IR.Expr expr) throws IOException {
        switch (expr.op()) {
            case "print" -> translatePrint(writer, expr);
            case "$assign" -> translateAssign(writer, expr);
            case "$return" -> translateReturn(writer, expr);
            case "throw" -> generateThrow(writer, expr);
            default -> {
                translateExpression(writer, expr);
            }
        }
    }

    private void translatePrint(FileWriter writer, IR.Expr expr) throws IOException {
        for (int i = 0; i < expr.vars().size(); i++) {
            IR.Var arg = expr.vars().get(i);

            var last = (i == expr.vars().size() - 1);
            String method = last ? "println" : "print";

            // load PrintStream and duplicate it for padding
            writer.write("        getstatic Field java/lang/System out Ljava/io/PrintStream;\n");

            if (!last) {
                this.dup(writer);
            }

            // if VOID, print empty string
            // TODO: probably will need to evaluate it though
            if (arg.type() == IR.TY.VOID) {
                writer.write("        ldc \"\"\n");
                writer.write(
                    "        invokevirtual Method java/io/PrintStream "
                    + method
                    + " (Ljava/lang/String;)V\n");
                continue;
            }


            translateValue(writer, arg.val());

            if (arg.type() == IR.TY.STRING) {
                writer.write("        invokevirtual Method java/io/PrintStream " + method + " (Ljava/lang/String;)V\n");
            } else {
                String typeDesc = jvmType(arg.type());
                writer.write("        invokevirtual Method java/io/PrintStream " + method + " (" + typeDesc + ")V\n");
            }

            // add "padding"
            if (!last) {
                writer.write("        ldc \" \"\n");
                writer.write("        invokevirtual Method java/io/PrintStream print (Ljava/lang/String;)V\n");
            }
        }
    }

    private void translateAssign(FileWriter writer, IR.Expr expr) throws IOException {
        IR.Var target = expr.vars().get(0);
        IR.Var value = expr.vars().get(1);

        // If assigning to VOID - evaluate RHS but don't store
        if (target.type() == IR.TY.VOID) {
            translateValue(writer, value.val());
            return;
        }

        String varName = ((IR.Ref) target.val()).ident();
        translateValue(writer, value.val());

        if (localVarMap.containsKey(varName)) {
            storeVar(writer, varName, target.type());
        } else {
            writer.write("        putstatic Field " + className + " " + varName + " " + jvmType(target.type()) + "\n");
        }
    }

    private void translateReturn(FileWriter writer, IR.Expr expr) throws IOException {
        translateValue(writer, expr.vars().getFirst().val());
        IR.TY type = expr.vars().getFirst().type();
        if (type == IR.TY.VOID) {
            writer.write("        return\n");
        } else if (type == IR.TY.INT || type == IR.TY.BOOL) {
            writer.write("        ireturn\n");
        } else if (type == IR.TY.FLOAT) {
            writer.write("        dreturn\n");
        } else if (type == IR.TY.STRING) {
            writer.write("        areturn\n");
        }
    }

    // --------------------------
    // Scoped constructs
    // --------------------------
    private void translateScoped(FileWriter writer, IR.Scoped scoped) throws IOException {
        switch (scoped.kind()) {
            case WHILE -> translateWhile(writer, scoped);
            case FOR -> translateFor(writer, scoped);
            default -> translateScope(writer, scoped.scope());
        }
    }
    private void translateIf(FileWriter writer, IR.Scoped ifScoped, IR.Scoped elseScoped) throws IOException {
        String labelElse = createLabel("else");
        String labelEnd = createLabel("end_if");

        translateValue(writer, ifScoped.dependencyValue().get());
        writer.write("        ifeq " + labelElse + "\n");

        translateScope(writer, ifScoped.scope());
        writer.write("        goto " + labelEnd + "\n");

        writer.write(labelElse + ":\n");
        if (elseScoped != null) {
            translateScope(writer, elseScoped.scope());
        }
        writer.write(labelEnd + ":\n");
    }

    private void translateWhile(FileWriter writer, IR.Scoped scoped) throws IOException {
        String labelStart = createLabel("while_start");
        String labelEnd = createLabel("while_end");

        writer.write(labelStart + ":\n");
        translateValue(writer, scoped.dependencyValue().get());
        writer.write("        ifeq " + labelEnd + "\n");

        translateScope(writer, scoped.scope());
        writer.write("        goto " + labelStart + "\n");
        writer.write(labelEnd + ":\n");
    }

    private void translateFor(FileWriter writer, IR.Scoped scoped) throws IOException {

        // ------ for i in range(A,B,step)
        if (scoped.dependencyValue().isPresent()
                && scoped.dependencyValue().get() instanceof IR.Expr(String op, ArrayList<IR.Var> vars)
                && op.equals("$iterRange")) {

            String iterVar = scoped.bornVars().getFirst();
            IR.Value start = vars.get(0).val();
            IR.Value end = vars.get(1).val();
            IR.Value step = vars.get(2).val();

            String labelStart = createLabel("for_start");
            String labelEnd = createLabel("for_end");

            // iterVar = start
            translateValue(writer, start);
            storeVar(writer, iterVar, IR.TY.INT);

            writer.write(labelStart + ":\n");

            // if iterVar >= end -> break
            loadVar(writer, iterVar);
            translateValue(writer, end);
            writer.write("        if_icmpge " + labelEnd + "\n");

            // body
            translateScope(writer, scoped.scope());

            // iterVar += step
            loadVar(writer, iterVar);
            translateValue(writer, step);
            writer.write("        iadd\n");
            storeVar(writer, iterVar, IR.TY.INT);

            writer.write("        goto " + labelStart + "\n");
            writer.write(labelEnd + ":\n");
            return;
        }

        // ------ for ch in string
        // bornVars
        String iterVar = scoped.bornVars().get(0);
        String storeVar = scoped.bornVars().get(1);
        String countVar = scoped.bornVars().get(2);

        IR.Value iterable = scoped.dependencyValue().get();

        String labelStart = createLabel("for_str_start");
        String labelBody = createLabel("for_str_body");
        String labelEnd = createLabel("for_str_end");

        // storeVar = iterable
        translateValue(writer, iterable);
        storeVar(writer, storeVar, IR.TY.STRING);

        // countVar = 0
        writer.write("        iconst_0\n");
        storeVar(writer, countVar, IR.TY.INT);

        writer.write(labelStart + ":\n");

        // if (countVar < storeVar.length()) goto body else end
        loadVar(writer, countVar);
        loadVar(writer, storeVar);
        writer.write("        invokevirtual Method java/lang/String length ()I\n");
        writer.write("        if_icmplt " + labelBody + "\n");
        writer.write("        goto " + labelEnd + "\n");

        writer.write(labelBody + ":\n");

        // iterVar = String.valueOf(storeVar.charAt(countVar))
        loadVar(writer, storeVar);
        loadVar(writer, countVar);
        writer.write("        invokevirtual Method java/lang/String charAt (I)C\n");
        writer.write("        invokestatic Method java/lang/String valueOf (C)Ljava/lang/String;\n");
        storeVar(writer, iterVar, IR.TY.STRING);

        // body
        translateScope(writer, scoped.scope());

        // countVar += 1
        loadVar(writer, countVar);
        writer.write("        iconst_1\n");
        writer.write("        iadd\n");
        storeVar(writer, countVar, IR.TY.INT);

        writer.write("        goto " + labelStart + "\n");
        writer.write(labelEnd + ":\n");
    }

    private void translateSwitch(FileWriter writer, IR.Expr targetExpr, List<IR.Scoped> cases) throws IOException {
        // save target
        IR.Var targetVarOriginal = targetExpr.vars().getFirst();
        IR.TY targetType = targetVarOriginal.type();

        String targetVarName = createLabel("switch_target_var");
        if (!localVarMap.containsKey(targetVarName)) {
            localVarMap.put(targetVarName, new VarInfo(localVarIndexCounter++, targetType));
            if (targetType == IR.TY.FLOAT) localVarIndexCounter++;
        }

        translateValue(writer, targetExpr);
        storeVar(writer, targetVarName, targetType);

        // Prepare targetRef var to rewrite condition expressions
        IR.Value targetRef = new IR.Ref(targetVarName);
        IR.Var targetRefVar = new IR.Var(targetRef, targetType, null, false);

        String labelEnd = createLabel("switch_end");

        // Pre-create condition labels for all but last (last goes to end -> default)
        List<String> conditionLabels = new ArrayList<>();
        for (int i = 0; i < cases.size() - 1; i++) {
            conditionLabels.add(createLabel("case_cond_" + i));
        }
        conditionLabels.add(labelEnd);

        // Iterate cases
        for (int i = 0; i < cases.size(); i++) {
            IR.Scoped caseScoped = cases.get(i);

            String labelNextCond = conditionLabels.get(i);
            String labelCaseBody = createLabel("case_body_" + i);

            // Define entry point for this case (if previous condition jumped here)
            if (i > 0) {
                String jumpTarget = conditionLabels.get(i - 1);
                if (!jumpTarget.equals(labelEnd)) {
                    writer.write(jumpTarget + ":\n");
                }
            }

            // If the case has a condition -> evaluate it
            if (caseScoped.dependencyValue().isPresent()) {
                IR.Value dependencyVal = caseScoped.dependencyValue().get();
                if (dependencyVal instanceof IR.Expr(String op, ArrayList<IR.Var> vars)) {

                    // Replace first var of condition expression with targetRefVar
                    ArrayList<IR.Var> newVarsList = new ArrayList<>(vars);
                    newVarsList.set(0, targetRefVar);
                    IR.Expr fixedConditionExpr = new IR.Expr(op, newVarsList);

                    // Evaluate condition -> leaves int (0/1) on stack
                    translateExpression(writer, fixedConditionExpr);

                    // ifeq labelNextCond
                    writer.write("        ifeq " + labelNextCond + "\n");
                    // if true -> goto body
                    writer.write("        goto " + labelCaseBody + "\n");
                } else {
                    throw new IllegalStateException("Case dependency is not an Expression.");
                }
            } else {
                // default case
                writer.write(labelCaseBody + ":\n");
                translateScope(writer, caseScoped.scope());
                writer.write("        goto " + labelEnd + "\n");
                continue;
            }

            // body
            writer.write(labelCaseBody + ":\n");
            translateScope(writer, caseScoped.scope());
            writer.write("        goto " + labelEnd + "\n");
        }

        // end
        writer.write(labelEnd + ":\n");
    }

    // --------------------------
    // Value & Expression translation
    // --------------------------
    private void translateValue(FileWriter writer, IR.Value value) throws IOException {
        if (value instanceof IR.Atom(IR.TY type, String val)) {
            switch (type) {
                case INT -> writer.write("        ldc " + val + "\n");
                case FLOAT -> {
                    try {
                        double d = Double.parseDouble(val);
                        if (Math.abs(d) < 0.001 || Math.abs(d) > 1000000) {
                            val = String.format(Locale.US, "%.10f", d);
                        } else {
                            val = String.valueOf(d);
                        }
                        val = val.replace('E', 'e');
                    } catch (NumberFormatException ignored) {}
                    writer.write("        ldc2_w " + val + "\n");
                }
                case BOOL -> writer.write("        ldc " + (val.equals("true") ? 1 : 0) + "\n");
                case STRING -> writer.write("        ldc \"" + val + "\"\n");
                case VOID -> {}
            }
        } else if (value instanceof IR.Ref(String ident)) {
            boolean isVoidLocal = localVarMap.containsKey(ident) && localVarMap.get(ident).type() == IR.TY.VOID;
            boolean isVoidGlobal = !isVoidLocal && ir.scope().varMapping().containsKey(ident) && ir.scope().varMapping().get(ident).type() == IR.TY.VOID;

            if (isVoidLocal || isVoidGlobal) {
                writer.write("        iconst_0\n"); // placeholder for void argument
                return;
            }

            if (localVarMap.containsKey(ident)) {
                loadVar(writer, ident);
            } else if (ir.scope().varMapping().containsKey(ident) && ir.scope().varMapping().get(ident).type() != IR.TY.VOID) {
                IR.Var globalVar = ir.scope().varMapping().get(ident);
                if (globalVar != null) {
                    writer.write("        getstatic Field " + className + " " + ident + " " + jvmType(globalVar.type()) + "\n");
                }
            }
        } else if (value instanceof IR.Expr expr) {
            translateExpression(writer, expr);
        }
    }

    private void translateExpression(FileWriter writer, IR.Expr expr) throws IOException {
        String op = expr.op();

        if (op.equals("input")) {
            writer.write("        new java/util/Scanner\n");
            this.dup(writer);
            writer.write("        getstatic Field java/lang/System in Ljava/io/InputStream;\n");
            writer.write("        invokespecial Method java/util/Scanner <init> (Ljava/io/InputStream;)V\n");
            writer.write("        invokevirtual Method java/util/Scanner nextLine ()Ljava/lang/String;\n");
            return;
        }

        // UDF call
        if (ir.opStore().containsKey(op) && !isBuiltin(op)) {
            for (IR.Var v : expr.vars()) {
                translateValue(writer, v.val());
            }
            IR.OpSpec spec = ir.opStore().get(op).alternatives().getFirst();
            String desc = jvmMethodDescriptor(spec.returnType(), spec.argTypes());
            writer.write("        invokestatic Method " + className + " " + op + " " + desc + "\n");
            if (spec.returnType() == IR.TY.VOID) {
                // placeholder if used inline
                writer.write("        iconst_0\n");
            }
            return;
        }

        if (op.startsWith("$case")) {
            translateSwitchExpr(writer, expr, op);
            return;
        }


        // unary ops
        if (expr.vars().size() == 1) {
            translateValue(writer, expr.vars().getFirst().val());
            IR.TY type = expr.vars().getFirst().type();
            switch (op) {
                case "u-" -> {
                    if (type == IR.TY.FLOAT) writer.write("        dneg\n");
                    else if (type == IR.TY.INT) writer.write("        ineg\n");
                }
                case "u+" -> {}
                case "!" -> {
                    writer.write("        iconst_1\n");
                    writer.write("        ixor\n");
                }
                default -> {}
            }
            return;
        }

        // binary ops
        if (expr.vars().size() == 2) {
            IR.Var var1 = expr.vars().get(0);
            IR.Var var2 = expr.vars().get(1);
            IR.TY type1 = var1.type();
            IR.TY type2 = var2.type();

            boolean isStringOp = (type1 == IR.TY.STRING && type2 == IR.TY.STRING);

            boolean isMixedNumeric = (type1 == IR.TY.INT && type2 == IR.TY.FLOAT) || (type1 == IR.TY.FLOAT && type2 == IR.TY.INT);
            boolean isDivision = op.equals("/");
            boolean isPowOp = op.equals("**");
            boolean isFloatOp = isMixedNumeric || type1 == IR.TY.FLOAT || isDivision || isPowOp;
            IR.TY effectiveType = isFloatOp ? IR.TY.FLOAT : type1;

            // left operand
            translateValue(writer, var1.val());
            if (type1 == IR.TY.INT && effectiveType == IR.TY.FLOAT) {
                writer.write("        i2d\n");
            }

            // right operand
            translateValue(writer, var2.val());
            if (type2 == IR.TY.INT && effectiveType == IR.TY.FLOAT) {
                writer.write("        i2d\n");
            }

            switch (op) {
                case "+" -> {
                    if (type1 == IR.TY.STRING) writer.write("        invokevirtual Method java/lang/String concat (Ljava/lang/String;)Ljava/lang/String;\n");
                    else if (effectiveType == IR.TY.FLOAT) writer.write("        dadd\n");
                    else writer.write("        iadd\n");
                }
                case "-" -> writer.write(effectiveType == IR.TY.FLOAT ? "        dsub\n" : "        isub\n");
                case "*" -> writer.write(effectiveType == IR.TY.FLOAT ? "        dmul\n" : "        imul\n");
                case "/" -> writer.write("        ddiv\n");
                case "**" -> writer.write("        invokestatic Method java/lang/Math pow (DD)D\n");
                case ">", "<", ">=", "<=", "==", "!=" -> {
                    if (isStringOp) {
                        // [Ref_Str1, Ref_Str2] -> [Int (0, >0, <0)]
                        writer.write("        invokevirtual Method java/lang/String compareTo (Ljava/lang/String;)I\n");

                        // Comparison operators expect result (Int) vs zero (0)
                        String compareOp = switch(op) {
                            case "==" -> "if_icmpeq";  // Result == 0
                            case "!=" -> "if_icmpne";  // Result != 0
                            case ">" -> "if_icmpgt";   // Result > 0
                            case "<" -> "if_icmplt";   // Result < 0
                            case ">=" -> "if_icmpge";  // Result >= 0
                            case "<=" -> "if_icmple";  // Result <= 0
                            default -> throw new IllegalStateException("Unexpected operator: " + op);
                        };

                        // Push 0 onto stack for comparison
                        writer.write("        ldc 0\n");

                        // Generate comparison logic (consumes 0 and Int result)
                        generateCmp(writer, IR.TY.INT, compareOp, compareOp); // Compare Int result vs 0

                    } else {
                        String intOp = switch(op) {
                            case ">" -> "if_icmpgt"; case "<" -> "if_icmplt"; case "==" -> "if_icmpeq";
                            case "!=" -> "if_icmpne"; case ">=" -> "if_icmpge"; case "<=" -> "if_icmple";
                            default -> throw new IllegalStateException("Unexpected operator: " + op);
                        };
                        String floatOp = switch(op) {
                            case "==" -> "ifeq"; case "!=" -> "ifne"; case ">" -> "ifgt";
                            case "<" -> "iflt"; case ">=" -> "ifge"; case "<=" -> "ifle";
                            default -> throw new IllegalStateException("Unexpected float operator: " + op);
                        };
                        generateCmp(writer, effectiveType, intOp, floatOp);
                    }
                }
                case "&&" -> writer.write("        iand\n");
                case "||" -> writer.write("        ior\n");
                default -> {}
            }
        }
    }

    private void translateSwitchExpr(FileWriter writer, IR.Expr expr, String op) throws IOException {
        IR.Var target = expr.vars().getFirst();
        IR.TY targetType = target.type();

        if (!(target.val() instanceof IR.Ref(String ident))) {
            throw new IllegalArgumentException("Internal Translator Error: Switch target must be an IR.Ref after processing.");
        }

        switch (op) {
            case "$caseIs" -> {
                loadVar(writer, ident);
                translateValue(writer, expr.vars().get(1).val());

                if (targetType == IR.TY.STRING) {
                    writer.write("        invokevirtual Method java/lang/String equals (Ljava/lang/Object;)Z\n");
                } else {
                    generateCmp(writer, targetType, "if_icmpeq", "ifeq");
                }
                return;
            }
            case "$caseIn" -> {
                IR.Var min = expr.vars().get(1);
                IR.Var max = expr.vars().get(2);

                // target >= min
                loadVar(writer, ident);
                translateValue(writer, min.val());
                generateCmp(writer, targetType, "if_icmpge", "ifge");

                // target < max
                loadVar(writer, ident);
                translateValue(writer, max.val());
                generateCmp(writer, targetType, "if_icmplt", "iflt");

                writer.write("        iand\n");
                return;
            }
            case "$caseOf" -> {
                List<IR.Var> consts = expr.vars().subList(1, expr.vars().size());
                if (consts.isEmpty()) {
                    writer.write("        iconst_0\n");
                    return;
                }

                loadVar(writer, ident);
                translateValue(writer, consts.getFirst().val());

                if (targetType == IR.TY.STRING) {
                    writer.write("        invokevirtual Method java/lang/String equals (Ljava/lang/Object;)Z\n");
                } else {
                    generateCmp(writer, targetType, "if_icmpeq", "ifeq");
                }

                for (int i = 1; i < consts.size(); i++) {

                    loadVar(writer, ident);
                    translateValue(writer, consts.get(i).val());

                    if (targetType == IR.TY.STRING) {
                        writer.write("        invokevirtual Method java/lang/String equals (Ljava/lang/Object;)Z\n");
                    } else {
                        generateCmp(writer, targetType, "if_icmpeq", "ifeq");
                    }

                    writer.write("        ior\n");
                }
            }
        }

    }


    // --------------------------
    // Helpers
    // --------------------------
    private void generateCmp(FileWriter writer, IR.TY type, String intOp, String doubleOp) throws IOException {
        String trueLabel = createLabel("true");
        String endLabel = createLabel("cmp_end");
        if (type == IR.TY.FLOAT) {
            writer.write("        dcmpl\n");
            writer.write("        " + doubleOp + " " + trueLabel + "\n");
        } else {
            writer.write("        " + intOp + " " + trueLabel + "\n");
        }
        writer.write("        iconst_0\n");
        writer.write("        goto " + endLabel + "\n");
        writer.write(trueLabel + ":\n");
        writer.write("        iconst_1\n");
        writer.write(endLabel + ":\n");
    }

    private void storeVar(FileWriter writer, String name, IR.TY type) throws IOException {
        if (!localVarMap.containsKey(name)) {
            localVarMap.put(name, new VarInfo(localVarIndexCounter++, type));
            if (type == IR.TY.FLOAT) localVarIndexCounter++;
        }

        VarInfo info = localVarMap.get(name);
        int idx = info.index;
        switch (type) {
            case INT, BOOL -> writer.write("        istore " + idx + "\n");
            case FLOAT -> writer.write("        dstore " + idx + "\n");
            case STRING -> writer.write("        astore " + idx + "\n");
            default -> {}
        }
    }

    private void loadVar(FileWriter writer, String name) throws IOException {
        if (!localVarMap.containsKey(name)) {
            // fallback registration as INT
            localVarMap.put(name, new VarInfo(localVarIndexCounter++, IR.TY.INT));
        }

        VarInfo info = localVarMap.get(name);
        int idx = info.index;

        if (info.type == IR.TY.VOID) {
            writer.write("        iconst_0\n");
            return;
        }
        switch (info.type) {
            case INT, BOOL -> writer.write("        iload " + idx + "\n");
            case FLOAT -> writer.write("        dload " + idx + "\n");
            case STRING -> writer.write("        aload " + idx + "\n");
            default -> writer.write("        aload " + idx + "\n");
        }
    }

    private boolean isBuiltin(String op) {
        return Set.of("+", "-", "*", "/", "**", "==", "!=", ">", "<", ">=", "<=", "&&", "||", "!", "print", "input", "u-").contains(op);
    }

    private String createLabel(String prefix) {
        return "L_" + prefix + "_" + labelCounter++;
    }

    private static String jvmType(IR.TY type) {
        return switch (type) {
            case INT, VOID -> "I";
            case FLOAT -> "D";
            case BOOL -> "Z";
            case STRING -> "Ljava/lang/String;";
        };
    }

    private String jvmMethodDescriptor(IR.TY returnType, List<IR.TY> argTypes) {
        String args = argTypes.stream().map(TranslatorJVM::jvmType).collect(Collectors.joining());
        String returnDesc = (returnType == IR.TY.VOID) ? "V" : jvmType(returnType);
        return "(" + args + ")" + returnDesc;
    }

    private void generateThrow(FileWriter writer, IR.Expr expr) throws IOException {
        IR.Var message = expr.vars().getFirst();

        writer.write("        new java/lang/RuntimeException\n");
        this.dup(writer);
        translateValue(writer, message.val()); // помістити рядок на стек
        writer.write("        invokespecial Method java/lang/RuntimeException <init> (Ljava/lang/String;)V\n");
        writer.write("        athrow\n");
    }

    private void dup(FileWriter writer) throws IOException {
        writer.write("        dup\n");
    }
}
