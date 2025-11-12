package org.example;

import java.util.*;
import java.io.FileWriter;
import java.io.IOException;
import java.util.stream.Collectors;

// represent the generated instruction
record PostfixInstruction(String lexeme, String token) {}

record FuncDeclaration(String name, String type, int arity) {}

// encapsulates the contents of single .postfix module
class PostfixModule {
    public final String moduleName;
    final List<PostfixInstruction> code = new ArrayList<>();
    final Map<String, Integer> labels = new LinkedHashMap<>();
    public final Map<String, String> variables = new LinkedHashMap<>();
    public final List<String> globalVars = new ArrayList<>();
    public final List<FuncDeclaration> funcDeclarations = new ArrayList<>();
    private final Map<String, String> constants = new LinkedHashMap<>();

    private int labelCounter = 1;

    public PostfixModule(String moduleName) {
        this.moduleName = moduleName;
    }

    public void addCode(String lexeme, String token) {
        if (lexeme.equals("u-")) {
            lexeme = "NEG";
            token = "math_op"; // math_op in PSM
        } else if (lexeme.equals("u+")) {
            // ignore u+
            return;
        }

        // strings in ""
        if (token.equals("string") && !lexeme.startsWith("\"")) {
            lexeme = "\"" + lexeme + "\"";
        }

        code.add(new PostfixInstruction(lexeme, token));
    }

    public String createLabel() {
        String label = "m" + labelCounter++;
        labels.put(label, -1);
        return label;
    }

    public void setLabelValue(String label) {
        if (labels.containsKey(label)) {
            labels.put(label, code.size());
        } else {
            throw new RuntimeException("Label " + label + " not created.");
        }
    }

    public void addConstant(String value, String type) {
        String key = value + " " + type;
        if (!constants.containsKey(key)) {
            constants.put(key, value);
        }
    }

    public void writeToFile(String baseDir) throws IOException {
        String filename = baseDir + "/" + moduleName + ".postfix";
        try (FileWriter writer = new FileWriter(filename)) {
            writer.write(".target: Postfix Machine\n");
            writer.write(".version: 1.0\n");

            // .vars
            if (!variables.isEmpty()) {
                writer.write(".vars(\n");
                for (Map.Entry<String, String> entry : variables.entrySet()) {
                    writer.write(String.format("    %s %s\n", entry.getKey(), entry.getValue()));
                }
                writer.write(")\n");
            }

            // .globVarList
            if (!globalVars.isEmpty()) {
                writer.write(".globVarList(\n");
                for (String var : globalVars) {
                    writer.write(String.format("    %s\n", var));
                }
                writer.write(")\n");
            }

            // .funcs
            if (!funcDeclarations.isEmpty()) {
                writer.write(".funcs(\n");
                for (FuncDeclaration decl : funcDeclarations) {
                    writer.write(String.format("    %s %s %d\n", decl.name(), decl.type(), decl.arity()));
                }
                writer.write(")\n");
            }

            // .labels
            Map<String, Integer> declaredLabels = labels.entrySet().stream()
                    .filter(e -> e.getValue() != -1)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            if (!declaredLabels.isEmpty()) {
                writer.write(".labels(\n");
                for (Map.Entry<String, Integer> entry : declaredLabels.entrySet()) {
                    writer.write(String.format("    %s %d\n", entry.getKey(), entry.getValue()));
                }
                writer.write(")\n");
            }

            writer.write(".code(\n");
            for (PostfixInstruction inst : code) {
                writer.write(String.format("    %s %s\n", inst.lexeme(), inst.token()));
            }
            writer.write(")\n");
        }
//        System.out.println("Generated file: " + filename);
    }
}