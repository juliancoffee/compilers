import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import au.com.origin.snapshots.Expect;
import au.com.origin.snapshots.annotations.SnapshotName;
import au.com.origin.snapshots.junit5.SnapshotExtension;

import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

import java.util.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.example.Lexer;

class SimpleTest {
    @Test void simple() {
        var input = "let x = 5;";
        var lexer = new Lexer(input);
        lexer.lex();

        Map<String, Object> expectedTokens = new LinkedHashMap<>();
        expectedTokens.put("((1, 1), (1, 3))", Map.of("keyword", "let"));
        expectedTokens.put("((1, 5), (1, 5))", Map.of("ident", "x"));
        expectedTokens.put("((1, 7), (1, 7))", Map.of("symbol", "="));
        expectedTokens.put("((1, 9), (1, 9))", Map.of("intLiteral", "5"));
        expectedTokens.put("((1, 10), (1, 10))", Map.of("symbol", ";"));

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

        Map<String, Object> expectedTokens = new LinkedHashMap<>();
        expectedTokens.put("((1, 1), (1, 3))", Map.of("keyword", "let"));
        expectedTokens.put("((1, 5), (1, 8))", Map.of("ident", "name"));
        expectedTokens.put("((1, 10), (1, 10))", Map.of("symbol", "="));
        expectedTokens.put("((1, 12), (1, 14))", Map.of("strLiteral", "x"));
        expectedTokens.put("((1, 15), (1, 15))", Map.of("symbol", ";"));

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

        Map<String, Object> expectedTokens = new LinkedHashMap<>();
        expectedTokens.put("((1, 1), (1, 4))", Map.of("ident", "varl"));
        expectedTokens.put("((1, 6), (1, 10))", Map.of("ident", "myVar"));
        expectedTokens.put("((1, 12), (1, 12))", Map.of("symbol", "="));
        expectedTokens.put("((1, 14), (1, 15))", Map.of("intLiteral", "10"));
        expectedTokens.put("((1, 16), (1, 16))", Map.of("symbol", ";"));

        var gson = new GsonBuilder().setPrettyPrinting().create();

        String actualJson = gson.toJson(lexer.tokenTable);
        String expectedJson = gson.toJson(expectedTokens);

        assertEquals(expectedJson, actualJson);
    }
}

@ExtendWith({SnapshotExtension.class})
class AppTest {
    private Expect expect;

    @Test void appOk() {
        var input = "let x = 5;";
        var lexer = new Lexer(input);
        lexer.lex();

        expect
            .serializer("json")
            .toMatchSnapshot(lexer.tokenTable);
    }
}

@ExtendWith({SnapshotExtension.class})
class FullTest {
    private Expect expect;

    @Test void appOk() {
        String input;
        try {
            input = Files.readString(
                Paths.get("src/main/resources/basic.ms2"),
                StandardCharsets.UTF_8
            );
        } catch (Exception e) {
            throw new RuntimeException("basic file couldn't be found");
        }

        var lexer = new Lexer(input);
        lexer.lex();

        expect
            .serializer("json")
            .toMatchSnapshot(lexer.tokenTable);
    }
}
