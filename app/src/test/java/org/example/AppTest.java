import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import au.com.origin.snapshots.Expect;
import au.com.origin.snapshots.junit5.SnapshotExtension;

import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

import java.util.*;
import java.util.stream.Collectors;

import com.google.gson.GsonBuilder;

import org.example.Lexer;
import org.example.Pair;
import org.example.SpanUtils;
import org.example.ST;
import org.example.Parser;

class SimpleLexTest {
    private static Pair<Integer, Integer> span(int from, int to) {
        return new Pair<>(from, to);
    }

    @Test void simple() {
        var input = "let x = 5;";
        var lexer = new Lexer(input);
        lexer.lex();

        Map<Pair, Object> expectedTokens = new LinkedHashMap<>();
        expectedTokens.put(span(1, 3), Map.of("keyword", "let"));
        expectedTokens.put(span(5, 5), Map.of("ident", "x"));
        expectedTokens.put(span(7, 7), Map.of("symbol", "="));
        expectedTokens.put(span(9, 9), Map.of("intLiteral", "5"));
        expectedTokens.put(span(10, 10), Map.of("symbol", ";"));

        var gson = new GsonBuilder().setPrettyPrinting().create();

        String actualJson = gson.toJson(lexer.tokenTable);
        String expectedJson = gson.toJson(expectedTokens);

        assertEquals(expectedJson, actualJson);
    }

    @Test
    void stringLiteralsTest() {
        var input = "let name = \"x\";";
        var lexer = new Lexer(input);
        lexer.lex();

        Map<Pair, Object> expectedTokens = new LinkedHashMap<>();
        expectedTokens.put(span(1, 3), Map.of("keyword", "let"));
        expectedTokens.put(span(5, 8), Map.of("ident", "name"));
        expectedTokens.put(span(10, 10), Map.of("symbol", "="));
        expectedTokens.put(span(12, 14), Map.of("strLiteral", "x"));
        expectedTokens.put(span(15, 15), Map.of("symbol", ";"));

        var gson = new GsonBuilder().setPrettyPrinting().create();

        String actualJson = gson.toJson(lexer.tokenTable);
        String expectedJson = gson.toJson(expectedTokens);

        assertEquals(expectedJson, actualJson);
    }

    @Test
    void invalidSymbolErrorTest() {
        var input = "let x = 5#;";
        var lexer = new Lexer(input);

        Exception exception = assertThrows(Exception.class, () -> {
            lexer.lex();
        });

        String expectedMessage = "E101";
        assertTrue(exception.getMessage().contains(expectedMessage));
    }

    @Test
    void malformedNumberErrorTest() {
        var input = "let pi = 5.;";
        var lexer = new Lexer(input);

        Exception exception = assertThrows(Exception.class, () -> {
            lexer.lex();
        });

        // This checks for a specific error message related to malformed numbers
        String expectedMessage = "E102";
        assertTrue(exception.getMessage().contains(expectedMessage));
    }

    @Test
    void unclosedStringErrorTest() {
        var input = "let str = \"Hello, world!;";
        var lexer = new Lexer(input);

        Exception exception = assertThrows(Exception.class, () -> {
            lexer.lex();
        });

        String expectedMessage = "E103";
        assertTrue(exception.getMessage().contains(expectedMessage));
    }

    @Test
    void malformedLogicalOperatorTest() {
        var input = "if a & b { }";
        var lexer = new Lexer(input);

        Exception exception = assertThrows(Exception.class, () -> {
            lexer.lex();
        });

        String expectedMessage = "E104";
        assertTrue(exception.getMessage().contains(expectedMessage));
    }

    @Test
    void misspelledKeywordTest() {
        var input = "varl myVar = 10;";
        var lexer = new Lexer(input);
        lexer.lex();

        Map<Pair, Object> expectedTokens = new LinkedHashMap<>();
        expectedTokens.put(span(1, 4), Map.of("ident", "varl"));
        expectedTokens.put(span(6, 10), Map.of("ident", "myVar"));
        expectedTokens.put(span(12, 12), Map.of("symbol", "="));
        expectedTokens.put(span(14, 15), Map.of("intLiteral", "10"));
        expectedTokens.put(span(16, 16), Map.of("symbol", ";"));

        var gson = new GsonBuilder().setPrettyPrinting().create();

        String actualJson = gson.toJson(lexer.tokenTable);
        String expectedJson = gson.toJson(expectedTokens);

        assertEquals(expectedJson, actualJson);
    }
}


class SimpleParseTest {
    private ST parseCode(String code) {
        var lexer = new Lexer(code);
        lexer.lex();

        var parser = new Parser(lexer.tokenTable, lexer.lineIndex);
        parser.parse();

        return parser.parseTree;
    }

    // --- Tests ---
    @Test
    void testSimpleVariableDeclaration() {
        var input = "let x = 5;";
        var actualTree = parseCode(input);

        var expectedTree = new ST(new ArrayList<>(List.of(
            new ST.LetStmt("x", new ST.IntLiteralExpr(5))
        )));

        assertEquals(expectedTree, actualTree);
    }

    @Test
    void testSimpleAddition() {
        var input = "let x = 5 + 2;";
        var actualTree = parseCode(input);

        var expectedTree = new ST(new ArrayList<>(List.of(
            new ST.LetStmt("x",
                new ST.BinOpExpr(
                    ST.BIN_OP.ADD,
                    new ST.IntLiteralExpr(5),
                    new ST.IntLiteralExpr(2)
                )
            )
        )));

        assertEquals(expectedTree, actualTree);
    }

    @Test
    void testSimpleSubtraction() {
        var input = "let x = 5 - 2;";
        var actualTree = parseCode(input);

        var expectedTree = new ST(new ArrayList<>(List.of(
            new ST.LetStmt("x",
                new ST.BinOpExpr(
                    ST.BIN_OP.SUB,
                    new ST.IntLiteralExpr(5),
                    new ST.IntLiteralExpr(2)
                )
            )
        )));

        assertEquals(expectedTree, actualTree);
    }

    @Test
    void testLeftAssociativity() {
        var input = "let x = 5 - 2 + 3;";
        var actualTree = parseCode(input);

        // Expected: ((5 - 2) + 3)
        var expectedTree = new ST(new ArrayList<>(List.of(
            new ST.LetStmt("x",
                new ST.BinOpExpr(
                    ST.BIN_OP.ADD,
                    new ST.BinOpExpr(
                        ST.BIN_OP.SUB,
                        new ST.IntLiteralExpr(5),
                        new ST.IntLiteralExpr(2)
                    ),
                    new ST.IntLiteralExpr(3)
                )
            )
        )));

        assertEquals(expectedTree, actualTree);
    }

    @Test
    void testComplexLeftAssociativity() {
        var input = "let x = 5 - 7 + 2 - 3;";
        var actualTree = parseCode(input);

        // Expected: (((5 - 7) + 2) - 3)
        var expectedTree = new ST(new ArrayList<>(List.of(
            new ST.LetStmt("x",
                new ST.BinOpExpr(ST.BIN_OP.SUB,
                    new ST.BinOpExpr(ST.BIN_OP.ADD,
                        new ST.BinOpExpr(ST.BIN_OP.SUB,
                            new ST.IntLiteralExpr(5),
                            new ST.IntLiteralExpr(7)
                        ),
                        new ST.IntLiteralExpr(2)
                    ),
                    new ST.IntLiteralExpr(3)
                )
            )
        )));

        assertEquals(expectedTree, actualTree);
    }
}

@ExtendWith({SnapshotExtension.class})
class FullLexTest {
    private Expect expect;

    @Test void appOk() {
        String input;
        try {
            input = Files.readString(
                Paths.get("sample/basic.ms2"),
                StandardCharsets.UTF_8
            );
        } catch (Exception e) {
            throw new RuntimeException("basic file couldn't be found");
        }

        var lexer = new Lexer(input);
        lexer.lex();

        var formattedTable = lexer.tokenTable.entrySet()
            .stream()
            .collect(Collectors.toMap(
                entry -> new Pair<>(
                    SpanUtils.locate(entry.getKey().first(), lexer.lineIndex),
                    SpanUtils.locate(entry.getKey().second(), lexer.lineIndex)
                ),
                Map.Entry::getValue,
                (v1, v2) -> v2,
                LinkedHashMap::new
            ));

        expect
            .serializer("json")
            .toMatchSnapshot(formattedTable);
    }
}
