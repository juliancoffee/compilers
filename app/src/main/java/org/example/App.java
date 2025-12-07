package org.example;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.gson.GsonBuilder;
import java.util.*;

import static utils.AnsiColors.*;

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
        String inputFileName = "fromCode";

        if (args.length > 0) {
            Path path = Paths.get(args[0]);
            code = Files.readString(path, StandardCharsets.UTF_8);

            if (code == null) {
                System.err.println("Cannot find file: " + args[0]);
                System.err.println(System.getProperty("user.dir"));
                return;
            }
            inputFileName = path.getFileName().toString();
            int dotIndex = inputFileName.lastIndexOf('.');
            if (dotIndex > 0) {
                inputFileName = inputFileName.substring(0, dotIndex);
            }
        } else {
            // Default code
            code = """
let debugNums = false;
let debugFormat = false;
func wrap(s: String) -> String { return "'" + s + "'"; }

func len(s: String) -> Int {
    var counter = 0;
    for char in s {
        counter = counter + 1;
    }
    return counter;
}

func testLen() {
    print("== Length ==");
    let nums = "12345";
    print("Len of", wrap(nums), "=", len(nums));
    print("Len of", wrap(""), "=", len(""));
}


func main() {
    testLen();
}
""";
        }

        var lexer = new Lexer(code);
        try {
            lexer.lex();
            System.out.println("\nЛексичний аналіз завершено успішно");
        } catch (RuntimeException e) {
            System.out.println("\nПомилка під час лексичного аналізу");
            System.err.println(e.getMessage());
            return; // зупинка якщо лексер впав
        }

        printTokenTable(lexer.tokenTable, lexer.lineIndex);
        System.out.println("\nColorized output:");
        colorizeAndPrint(code, lexer.tokenTable);

        // СИНТАКСИЧНИЙ АНАЛІЗ
        var parser = new Parser(lexer.tokenTable, lexer.lineIndex);
        try {
            parser.parse();
            System.out.println("\nСинтаксичний аналіз завершено успішно");
        } catch (RuntimeException e) {
            System.out.println("\nПомилка під час синтаксичного аналізу");
            if (parser.biggestError().isPresent()) {
                System.err.println(parser.biggestError().get());
            } else {
                System.err.println(e.getMessage());
            }
            return;
        }

        var gson = new GsonBuilder()
                .registerTypeAdapter(Optional.class, new OptionalAdapter())
                .setPrettyPrinting()
                .create();

        var printer = new PrinterST(lexer.lineIndex);
        var prettytree = printer.print(parser.parseTree);
        System.out.println(prettytree);

        // СЕМАНТИЧНИЙ АНАЛІЗ
        var typer = new Typer(parser.parseTree, lexer.lineIndex);
        var printerIR = new PrinterIR(lexer.lineIndex);
        try {
            typer.typecheck();
            System.out.println("\nСемантичний аналіз завершено успішно");
        } catch (RuntimeException e) {
            System.out.println("\nПомилка під час семантичного аналізу");
            var prettyrep = printerIR.print(typer.ir);
            System.err.println(prettyrep);
            System.err.println(e.getMessage());
            return;
        }

        var prettyrep = printerIR.print(typer.ir);
        System.out.println(prettyrep);
        System.out.println("\nПрограма пройшла всі етапи аналізу успішно!");

        // ГЕНЕРАЦІЯ POSTFIX
//        var translator = new Translator(typer.ir);
//        String outputDir = "sample/postfix";
//        String mainModule = "main";
//        try {
//            translator.generate(outputDir);
//            System.out.println("\nГенерація postfix коду завершена успішно!");
//        } catch (IOException e) {
//            System.err.println("\nПомилка під час генерації postfix коду: " + e.getMessage());
//            return;
//        }

        var translator = new TranslatorJVM(typer.ir, inputFileName);
        String outputDir = "sample/jvm/in";
        try {
            translator.generate(outputDir);
            System.out.println("\nГенерація JVM асемблерного файлу (.j) завершена успішно!");
        } catch (IOException e) {
            System.err.println("\nПомилка під час генерації .j файлу: " + e.getMessage());
            return;
        }

    }
}
//./gradlew run --args="sample/basic.ms2"
//./gradlew run
//./gradlew run --args="sample/test_errors/01_missing_terminal.ms2"
