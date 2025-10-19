package org.example;

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
    private static Optional<ST.TY> none() {
        return Optional.empty();
    }

    private ST parseCode(String code) {
        var lexer = new Lexer(code);
        lexer.lex();

        var parser = new Parser(lexer.tokenTable, lexer.lineIndex);
        parser.parse();

        var tree = parser.parseTree;
        tree.clearAllSpans();

        return tree;
    }

    // --- Tests ---
    @Test
    void testSimpleVariableDeclaration() {
        var input = "let x = 5;";
        var actualTree = parseCode(input);

        var expectedTree = new ST(new ArrayList<>(List.of(
            new ST.LetStmt("x", none(), new ST.IntLiteralExpr(5))
        )));

        assertEquals(expectedTree, actualTree);
    }

    @Test
    void testSimpleAddition() {
        var input = "let x = 5 + 2;";
        var actualTree = parseCode(input);

        var expectedTree = new ST(new ArrayList<>(List.of(
            new ST.LetStmt("x", none(),
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
            new ST.LetStmt("x", none(),
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
            new ST.LetStmt("x", none(),
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
            new ST.LetStmt("x", none(),
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

    @Test
    void testFunctionCallNoArgs() {
        var input = "let x = myFunc();";
        var actualTree = parseCode(input);

        var expectedTree = new ST(new ArrayList<>(List.of(
            new ST.LetStmt("x", none(),
                new ST.FuncCallExpr("myFunc", new ArrayList<>())
            )
        )));

        assertEquals(expectedTree, actualTree);
    }

    @Test
    void testFunctionCallWithIntLiteralArg() {
        var input = "let x = myFunc(5);";
        var actualTree = parseCode(input);

        var expectedTree = new ST(new ArrayList<>(List.of(
            new ST.LetStmt("x", none(),
                new ST.FuncCallExpr("myFunc", new ArrayList<>(List.of(
                    new ST.IntLiteralExpr(5)
                )))
            )
        )));

        assertEquals(expectedTree, actualTree);
    }

    @Test
    void testFunctionCallWithIdentArg() {
        var input = "let x = myFunc(y);";
        var actualTree = parseCode(input);

        var expectedTree = new ST(new ArrayList<>(List.of(
            new ST.LetStmt("x", none(),
                new ST.FuncCallExpr("myFunc", new ArrayList<>(List.of(
                    new ST.IdentExpr("y")
                )))
            )
        )));

        assertEquals(expectedTree, actualTree);
    }

    @Test
    void testFunctionCallPrecedence() {
        var input = "let x = 5 + myFunc();";
        var actualTree = parseCode(input);

        // Expected: (5 + myFunc())
        var expectedTree = new ST(new ArrayList<>(List.of(
            new ST.LetStmt("x", none(),
                new ST.BinOpExpr(
                    ST.BIN_OP.ADD,
                    new ST.IntLiteralExpr(5),
                    new ST.FuncCallExpr("myFunc", new ArrayList<>())
                )
            )
        )));

        assertEquals(expectedTree, actualTree);
    }

    @Test
    void testNestedFunctionCall() {
        var input = "let x = funcOne(funcTwo());";
        var actualTree = parseCode(input);

        var expectedTree = new ST(new ArrayList<>(List.of(
            new ST.LetStmt("x", none(),
                new ST.FuncCallExpr("funcOne", new ArrayList<>(List.of(
                    new ST.FuncCallExpr("funcTwo", new ArrayList<>())
                )))
            )
        )));

        assertEquals(expectedTree, actualTree);
    }

    @Test
    void testComplexFunctionCallAndOps() {
        var input = "let x = 5 - funcOne() + funcTwo(y);";
        var actualTree = parseCode(input);

        // Expected: (5 - funcOne()) + funcTwo(y)
        var expectedTree = new ST(new ArrayList<>(List.of(
            new ST.LetStmt("x", none(),
                new ST.BinOpExpr(
                    ST.BIN_OP.ADD,
                    new ST.BinOpExpr(
                        ST.BIN_OP.SUB,
                        new ST.IntLiteralExpr(5),
                        new ST.FuncCallExpr("funcOne", new ArrayList<>())
                    ),
                    new ST.FuncCallExpr("funcTwo", new ArrayList<>(List.of(
                        new ST.IdentExpr("y")
                    )))
                )
            )
        )));

        assertEquals(expectedTree, actualTree);
    }

    @Test
        void testMulPrecedenceOverAdd() {
            var input = "let x = a + b * c;";
            var actualTree = parseCode(input);

            // Expected: a + (b * c)
            var expectedTree = new ST(new ArrayList<>(List.of(
                new ST.LetStmt("x", none(),
                    new ST.BinOpExpr(
                        ST.BIN_OP.ADD,
                        new ST.IdentExpr("a"),
                        new ST.BinOpExpr(
                            ST.BIN_OP.MUL,
                            new ST.IdentExpr("b"),
                            new ST.IdentExpr("c")
                        )
                    )
                )
            )));

            assertEquals(expectedTree, actualTree);
        }

        @Test
        void testDivPrecedenceOverSub() {
            var input = "let x = a - b / c;";
            var actualTree = parseCode(input);

            // Expected: a - (b / c)
            var expectedTree = new ST(new ArrayList<>(List.of(
                new ST.LetStmt("x", none(),
                    new ST.BinOpExpr(
                        ST.BIN_OP.SUB,
                        new ST.IdentExpr("a"),
                        new ST.BinOpExpr(
                            ST.BIN_OP.DIV,
                            new ST.IdentExpr("b"),
                            new ST.IdentExpr("c")
                        )
                    )
                )
            )));

            assertEquals(expectedTree, actualTree);
        }

        @Test
        void testMulLeftAssociativity() {
            var input = "let x = a * b * c;";
            var actualTree = parseCode(input);

            // Expected: (a * b) * c
            var expectedTree = new ST(new ArrayList<>(List.of(
                new ST.LetStmt("x", none(),
                    new ST.BinOpExpr(
                        ST.BIN_OP.MUL,
                        new ST.BinOpExpr(
                            ST.BIN_OP.MUL,
                            new ST.IdentExpr("a"),
                            new ST.IdentExpr("b")
                        ),
                        new ST.IdentExpr("c")
                    )
                )
            )));

            assertEquals(expectedTree, actualTree);
        }

        @Test
        void testDivLeftAssociativity() {
            var input = "let x = a / b / c;";
            var actualTree = parseCode(input);

            // Expected: (a / b) / c
            var expectedTree = new ST(new ArrayList<>(List.of(
                new ST.LetStmt("x", none(),
                    new ST.BinOpExpr(
                        ST.BIN_OP.DIV,
                        new ST.BinOpExpr(
                            ST.BIN_OP.DIV,
                            new ST.IdentExpr("a"),
                            new ST.IdentExpr("b")
                        ),
                        new ST.IdentExpr("c")
                    )
                )
            )));

            assertEquals(expectedTree, actualTree);
        }

        @Test
        void testMixedMulDivLeftAssociativity() {
            var input = "let x = a * b / c;";
            var actualTree = parseCode(input);

            // Expected: (a * b) / c
            var expectedTree = new ST(new ArrayList<>(List.of(
                new ST.LetStmt("x", none(),
                    new ST.BinOpExpr(
                        ST.BIN_OP.DIV,
                        new ST.BinOpExpr(
                            ST.BIN_OP.MUL,
                            new ST.IdentExpr("a"),
                            new ST.IdentExpr("b")
                        ),
                        new ST.IdentExpr("c")
                    )
                )
            )));

            assertEquals(expectedTree, actualTree);
        }

        @Test
        void testComplexPrecedenceWithIdentifiers() {
            var input = "let x = a + b * c - d / e;";
            var actualTree = parseCode(input);

            // Expected: (a + (b * c)) - (d / e)
            var expectedTree = new ST(new ArrayList<>(List.of(
                new ST.LetStmt("x", none(),
                    new ST.BinOpExpr(
                        ST.BIN_OP.SUB,
                        new ST.BinOpExpr(
                            ST.BIN_OP.ADD,
                            new ST.IdentExpr("a"),
                            new ST.BinOpExpr(
                                ST.BIN_OP.MUL,
                                new ST.IdentExpr("b"),
                                new ST.IdentExpr("c")
                            )
                        ),
                        new ST.BinOpExpr(
                            ST.BIN_OP.DIV,
                            new ST.IdentExpr("d"),
                            new ST.IdentExpr("e")
                        )
                    )
                )
            )));

            assertEquals(expectedTree, actualTree);
        }

        @Test
        void testFunctionCallAsOperandForMul() {
            var input = "let x = myFunc() * b;";
            var actualTree = parseCode(input);

            // Expected: (myFunc() * b)
            var expectedTree = new ST(new ArrayList<>(List.of(
                new ST.LetStmt("x", none(),
                    new ST.BinOpExpr(
                        ST.BIN_OP.MUL,
                        new ST.FuncCallExpr("myFunc", new ArrayList<>()),
                        new ST.IdentExpr("b")
                    )
                )
            )));

            assertEquals(expectedTree, actualTree);
        }

    @Test
        void testFunctionCallWithMultipleArgs() {
            var input = "let x = myFunc(a, 5, b);";
            var actualTree = parseCode(input);

            // Expected: myFunc(a, 5, b)
            var expectedTree = new ST(new ArrayList<>(List.of(
                new ST.LetStmt("x", none(),
                    new ST.FuncCallExpr("myFunc", new ArrayList<>(List.of(
                        new ST.IdentExpr("a"),
                        new ST.IntLiteralExpr(5),
                        new ST.IdentExpr("b")
                    )))
                )
            )));

            assertEquals(expectedTree, actualTree);
        }

        @Test
        void testFunctionCallWithExpressionAsArg() {
            var input = "let x = myFunc(a * 5);";
            var actualTree = parseCode(input);

            // Expected: myFunc(a * 5)
            var expectedTree = new ST(new ArrayList<>(List.of(
                new ST.LetStmt("x", none(),
                    new ST.FuncCallExpr("myFunc", new ArrayList<>(List.of(
                        new ST.BinOpExpr(
                            ST.BIN_OP.MUL,
                            new ST.IdentExpr("a"),
                            new ST.IntLiteralExpr(5)
                        )
                    )))
                )
            )));

            assertEquals(expectedTree, actualTree);
        }

        @Test
        void testFunctionCallWithComplexArgsAndOps() {
            var input = "let x = a * myFunc(b + c, 10) / d;";
            var actualTree = parseCode(input);

            // Expected: (a * myFunc(b + c, 10)) / d
            var expectedTree = new ST(new ArrayList<>(List.of(
                new ST.LetStmt("x", none(),
                    new ST.BinOpExpr(
                        ST.BIN_OP.DIV,
                        new ST.BinOpExpr(
                            ST.BIN_OP.MUL,
                            new ST.IdentExpr("a"),
                            new ST.FuncCallExpr("myFunc", new ArrayList<>(List.of(
                                new ST.BinOpExpr(
                                    ST.BIN_OP.ADD,
                                    new ST.IdentExpr("b"),
                                    new ST.IdentExpr("c")
                                ),
                                new ST.IntLiteralExpr(10)
                            )))
                        ),
                        new ST.IdentExpr("d")
                    )
                )
            )));

            assertEquals(expectedTree, actualTree);
        }

        @Test
        void testFunctionCallInsideAnotherFunctionCall() {
            var input = "let x = funcOne(a, funcTwo(b / c));";
            var actualTree = parseCode(input);

            // Expected: funcOne(a, funcTwo(b / c))
            var expectedTree = new ST(new ArrayList<>(List.of(
                new ST.LetStmt("x", none(),
                    new ST.FuncCallExpr("funcOne", new ArrayList<>(List.of(
                        new ST.IdentExpr("a"),
                        new ST.FuncCallExpr("funcTwo", new ArrayList<>(List.of(
                            new ST.BinOpExpr(
                                ST.BIN_OP.DIV,
                                new ST.IdentExpr("b"),
                                new ST.IdentExpr("c")
                            )
                        )))
                    )))
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


class ExtendedParseTest {

    private static Optional<ST.TY> none() {
        return Optional.empty();
    }

    private ST parseCode(String code) {
        var lexer = new Lexer(code);
        lexer.lex();
        var parser = new Parser(lexer.tokenTable, lexer.lineIndex);
        parser.parse();
        var tree = parser.parseTree;
        tree.clearAllSpans();

        return tree;
    }

    @Test
    void testBoolLiteralTrue() {
        var input = "let flag = true;";
        var actual = parseCode(input);

        var expected = new ST(new ArrayList<>(List.of(
                new ST.LetStmt("flag", none(), new ST.BoolLiteralExpr(true))
        )));

        assertEquals(expected, actual);
    }

    @Test
    void testLogicAndOr() {
        var input = "let result = a && b || c;";
        var actual = parseCode(input);

        var expected = new ST(new ArrayList<>(List.of(
            new ST.LetStmt("result", none(),
                new ST.BinOpExpr(
                    ST.BIN_OP.OR,
                    new ST.BinOpExpr(
                        ST.BIN_OP.AND,
                        new ST.IdentExpr("a"),
                        new ST.IdentExpr("b")
                    ),
                    new ST.IdentExpr("c")
                )
            )
        )));

        assertEquals(expected, actual);
    }

    @Test
    void testNotOperator() {
        var input = "let x = !flag;";
        var actual = parseCode(input);

        var expected = new ST(new ArrayList<>(List.of(
            new ST.LetStmt("x", none(),
                new ST.UnaryOpExpr(ST.UNARY_OP.NOT, new ST.IdentExpr("flag"))
            )
        )));

        assertEquals(expected, actual);
    }


    @Test
    void testRelationalExpr() {
        var input = "let cmp = a < b;";
        var actual = parseCode(input);

        var expected = new ST(new ArrayList<>(List.of(
            new ST.LetStmt("cmp", none(),
                new ST.BinOpExpr(ST.BIN_OP.LT,
                    new ST.IdentExpr("a"),
                    new ST.IdentExpr("b")
                )
            )
        )));

        assertEquals(expected, actual);
    }


    @Test
    void testPowerRightAssociativity() {
        var input = "let area = pi * radius ** 2 ** 3;";
        var actual = parseCode(input);

        // Expected: pi * (radius ** (2 ** 3))
        var expected = new ST(new ArrayList<>(List.of(
            new ST.LetStmt("area", none(),
                new ST.BinOpExpr(
                    ST.BIN_OP.MUL,
                    new ST.IdentExpr("pi"),
                    new ST.BinOpExpr(
                        ST.BIN_OP.POW,
                        new ST.IdentExpr("radius"),
                        new ST.BinOpExpr(
                            ST.BIN_OP.POW,
                            new ST.IntLiteralExpr(2),
                            new ST.IntLiteralExpr(3)
                        )
                    )
                )
            )
        )));

        assertEquals(expected, actual);
    }


    @Test
    void testWhileStmt() {
        var input = "func main() { while i < 10 { let i = i + 1; } }";
        var actual = parseCode(input);

        var expected = new ST(new ArrayList<>(List.of(
            new ST.FuncStmt(
                "main",
                new ArrayList<>(),
                Optional.empty(),
                new ST.Block(new ArrayList<>(List.of(
                    new ST.WhileStmt(
                        new ST.BinOpExpr(ST.BIN_OP.LT,
                            new ST.IdentExpr("i"),
                            new ST.IntLiteralExpr(10)
                        ),
                        new ST.Block(new ArrayList<>(List.of(
                            new ST.LetStmt("i", Optional.empty(),
                                new ST.BinOpExpr(
                                    ST.BIN_OP.ADD,
                                    new ST.IdentExpr("i"),
                                    new ST.IntLiteralExpr(1)
                                )
                            )
                        )))
                    )
                )))
            )
        )));

        assertEquals(expected, actual);
    }


    @Test
    void testIfElseStmt() {
        var input = "func main() { if a < b { let x = 1; } else { let x = 2; } }";
        var actual = parseCode(input);

        var expected = new ST(new ArrayList<>(List.of(
            new ST.FuncStmt(
                "main",
                new ArrayList<>(),
                Optional.empty(),
                new ST.Block(new ArrayList<>(List.of(
                    new ST.IfStmt(
                        new ST.BinOpExpr(ST.BIN_OP.LT,
                            new ST.IdentExpr("a"),
                            new ST.IdentExpr("b")
                        ),
                        new ST.Block(new ArrayList<>(List.of(
                            new ST.LetStmt("x", Optional.empty(), new ST.IntLiteralExpr(1))
                        ))),
                        Optional.of(new ST.Block(new ArrayList<>(List.of(
                            new ST.LetStmt("x", Optional.empty(), new ST.IntLiteralExpr(2))
                        ))))
                    )
                )))
            )
        )));

        assertEquals(expected, actual);
    }

    @Test
    void testSwitchStmt() {
        var input = """
        func main() {
            switch val {
                case 1, 2 { let x = "low"; }
                case 3 { let x = "mid"; }
                default { let x = "high"; }
            }
        }
    """;
        var actual = parseCode(input);

        var cases = new ArrayList<ST.CaseStmt>(List.of(
            new ST.ValueCase(
                new ST.SeqComp(new ArrayList<>(List.of(
                    new ST.IntLiteralExpr(1),
                    new ST.IntLiteralExpr(2)
                ))),
                new ST.Block(new ArrayList<>(List.of(
                    new ST.LetStmt("x", Optional.empty(), new ST.StrLiteralExpr("low"))
                )))
            ),
            new ST.ValueCase(
                new ST.ConstComp(new ST.IntLiteralExpr(3)),
                new ST.Block(new ArrayList<>(List.of(
                        new ST.LetStmt("x", Optional.empty(), new ST.StrLiteralExpr("mid"))
                )))
            ),
            new ST.DefaultCase(new ST.Block(new ArrayList<>(List.of(
                new ST.LetStmt("x", Optional.empty(), new ST.StrLiteralExpr("high"))
            ))))
        ));

        var expected = new ST(new ArrayList<>(List.of(
            new ST.FuncStmt(
                "main",
                new ArrayList<>(),
                Optional.empty(),
                new ST.Block(new ArrayList<>(List.of(
                    new ST.SwitchStmt(new ST.IdentExpr("val"), cases)
                )))
            )
        )));
        assertEquals(expected, actual);
    }
}
