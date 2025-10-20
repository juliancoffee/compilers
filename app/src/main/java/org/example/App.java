package org.example;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.google.gson.GsonBuilder;
import java.util.*;

class App {
    private static void printTokenTable(
        TreeMap<Pair<Integer, Integer>, Token> tokenTable,
        ArrayList<Integer> lineIndex
    ) {
        System.out.println("Таблиця символів програми:");
        System.out.printf("%-7s %-15s %-20s %-12s %-10s %n",
                "n_rec", "lexeme", "token", "idxIdConst", "span");
        System.out.println("-----------------------------------------------------------------------");

        int nRec = 1;

        Map<String, Integer> idTable = new HashMap<>();
        Map<String, Integer> intConstTable = new HashMap<>();
        Map<String, Integer> doubleConstTable = new HashMap<>();
        Map<String, Integer> stringConstTable = new HashMap<>();

        for (var entry : tokenTable.entrySet()) {
            var pair = entry.getKey();
            var token = entry.getValue();

            String lexeme = "";
            String tokenName = "";
            String idxIdConst = "";
            int numChar = pair.first();
            int numCharEnd = pair.second();

            // tokens
            if (token instanceof Keyword k) {
                lexeme = k.keyword();
                tokenName = "keyword";
            } else if (token instanceof Ident i) {
                lexeme = i.ident();
                tokenName = "ident";
                idxIdConst = String.valueOf(idTable.computeIfAbsent(lexeme, key -> idTable.size() + 1));
            } else if (token instanceof IntLiteral i) {
                lexeme = i.intLiteral();
                tokenName = "int_const";
                idxIdConst = String.valueOf(intConstTable.computeIfAbsent(lexeme, key -> intConstTable.size() + 1));
            } else if (token instanceof FloatLiteral f) {
                lexeme = f.floatLiteral();
                tokenName = "double_const";
                idxIdConst = String.valueOf(doubleConstTable.computeIfAbsent(lexeme, key -> doubleConstTable.size() + 1));
            } else if (token instanceof StrLiteral s) {
                lexeme = "\"" + s.strLiteral() + "\""; // quotes
                tokenName = "string_const";
                idxIdConst = String.valueOf(stringConstTable.computeIfAbsent(s.strLiteral(), key -> stringConstTable.size() + 1));
            } else if (token instanceof Symbol s) {
                lexeme = s.symbol();
                switch (lexeme) {
                    case "+", "-" -> tokenName = "add_op";
                    case "*", "**", "/" -> tokenName = "mult_op";
                    case "=", "==", "!=", "<", "<=", ">", ">=" -> tokenName = "rel_op";
                    case "&&", "||", "!" -> tokenName = "logic_op";
                    case "(", ")", "{", "}" -> tokenName = "brackets_op";
                    case ",", ".", ";", ":" -> tokenName = "punct";
                    default -> tokenName = "symbol";
                }
            } else if (token instanceof Error e) {
                lexeme = e.error();
                tokenName = "error";
            }

            lexeme = lexeme.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");

            var first_pos = SpanUtils.locate(numChar, lineIndex);
            var second_pos = SpanUtils.locate(numCharEnd, lineIndex);
            var span = String.format("%d,%d..%d,%d",
                first_pos.first(), first_pos.second(),
                second_pos.first(), second_pos.second()
            );
            System.out.printf("%-7d %-15s %-20s %-12s %-10s %n",
                    nRec, lexeme, tokenName, idxIdConst, span);
            nRec++;
        }
        System.out.println("-----------------------------------------------------------------------");
    }
    /*
     * ANSI escape codes for colors
     */
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_WHITE = "\u001B[37m";
    /*
     * ANSI escape code for special effects
     */
    public static final String ANSI_UNDERLINE = "\u001b[4m";
    public static final String ANSI_RED_BACK = "\u001b[41m";

    private static String getColorForToken(Token token) {
        return switch (token) {
            case Keyword k -> ANSI_PURPLE;
            case Ident i -> ANSI_WHITE;
            case IntLiteral il -> ANSI_GREEN;
            case FloatLiteral fl -> ANSI_GREEN;
            case StrLiteral sl -> ANSI_YELLOW;
            case Symbol s -> ANSI_CYAN;
            case Error e -> ANSI_RED_BACK;
        };
    }

    public static void colorizeAndPrint(
        String sourceCode,
        TreeMap<Pair<Integer, Integer>, Token> tokenTable
    ) {
        if (tokenTable.isEmpty()) {
            // Lexer probably wasn't run, so just print the raw code
            System.out.println(sourceCode);
            return;
        }

        int lastPos = 0;
        for (var entry : tokenTable.entrySet()) {
            var span = entry.getKey();
            var token = entry.getValue();

            int startPos = span.first() - 1;

            String lexeme = switch (token) {
                case Keyword k -> k.keyword();
                case Ident i -> i.ident();
                case IntLiteral i -> i.intLiteral();
                case FloatLiteral f -> f.floatLiteral();
                case StrLiteral s -> "\"" + s.strLiteral() + "\"";
                case Symbol s -> s.symbol();
                case Error e -> e.error();
            };

            int endPos = startPos + lexeme.length();

            // Print text before the token
            if (startPos > lastPos) {
                System.out.print(sourceCode.substring(lastPos, startPos));
            }

            String color = getColorForToken(token);
            System.out.print(color + sourceCode.substring(startPos, endPos) + ANSI_RESET);

            lastPos = endPos;
        }

        // Print remaining text
        if (lastPos < sourceCode.length()) {
            System.out.print(sourceCode.substring(lastPos));
        }
    }

    public static void main(String[] args) throws IOException {
        String code;
        if (args.length > 0) {
            code = Files.readString(
                Paths.get(args[0]),
                StandardCharsets.UTF_8
            );

            if (code == null) {
                System.err.println("Cannot find file: " + args[0]);
                System.err.println(System.getProperty("user.dir"));
                return;
            }
        } else {
            // Default code
            code = """
let y = 5 + 6;
func noop() -> Void {
    let x = 5;
    x = 06;
    printPi();
    printSum(x, y);
    print(x, y);
}
func1(x+func2(5));
""";
            code = """
let x = 1 + 2 * 3 + 4;
""";
            code = """
func noop() -> Void {}
func simpleNoop() {}
func getName() -> String { return input(); }
func sumSquared(x: Double, y: Double) -> String { return x * x + y * y; }
""";
            code = """
let x = square(1 + square(2));
""";
            code = """
func main() {
    let x = 5;
    x = 6;

    // Цикл for з range
    for i in range(0, 5, 1) {
        print("For loop iteration: ", i);
    }

    // Цикл for з range
    for c in "hello" {
        print("For loop iteration: ", c);
    }
}
""";
            code = """
func main() {
    //while without relops
    var counter = 0;
    while counter {
        print("Counter = " + counter);
        counter = counter + 1;
    }
}
""";//TODO relops
            code = """
    func main() {
        // Умовне розгалуження if-else
        if flag {
            print("Flag is true and pi > 3");
        } else {
            print("Condition not met");
        }
    }
""";
            code = """
let pi: Double = 3.14;
let flag: Bool = true;
func add(a: Int, b: Int) -> Int {
    return a + b;
}
func main() {
    // Використання switch
    let choice: Int = 2;
    switch choice {
        case 1 {
            print("Choice is 1");
        }
        case range(2, 4) {
            print("Choice is 2 or 3");
        }
        default {
            print("Choice is something else");
        }
    }
    if flag == false {
        print("Flag is true and pi > 3");
    } else {
        print("Condition not met");
    }
    var result: Double = (pi + 2.0) * 3.0 / 2.0 - 1.0;
}
""";
        }

        var lexer = new Lexer(code);

        try {
            lexer.lex();
            System.out.println("\nFile was lexed successfully!");
        } catch (RuntimeException e) {
            System.out.println("\nLexer has failed!");
            System.err.println(e);
        }

        printTokenTable(lexer.tokenTable, lexer.lineIndex);
        System.out.println("\nColorized output:");
        colorizeAndPrint(code, lexer.tokenTable);

        var parser = new Parser(lexer.tokenTable, lexer.lineIndex);
        try {
            parser.parse();
        } catch (RuntimeException e) {
            System.err.println("\nParser has failed!");
            if (parser.biggestError().isPresent()) {
                System.err.println(parser.biggestError().get());
            } else {
                System.err.println(e);
            }
        }

        var gson = new GsonBuilder()
            .registerTypeAdapter(Optional.class, new OptionalAdapter())
            .setPrettyPrinting()
            .create();
        var printer = new PrinterST(lexer.lineIndex);
        var prettytree = printer.print(parser.parseTree);
        System.out.println(prettytree);
        // System.out.println(gson.toJson(parser.parseTree));

        var typer = new Typer(parser.parseTree, lexer.lineIndex);
        try {
            typer.typecheck();
        } catch (RuntimeException e) {
            // System.err.println(gson.toJson(
            //     IR.nullifyParentScopes(typer.ir.scope()))
            // );
            throw e;
        }
        // System.out.println(gson.toJson(
        //     IR.nullifyParentScopes(typer.ir.scope()))
        // );
    }
}
//./gradlew run --args="sample/basic.ms2"
//./gradlew run
//./gradlew run --args="sample/test_errors/01_missing_terminal.ms2"
