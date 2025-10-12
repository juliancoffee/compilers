package org.example;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

// ToString stuff
import java.text.MessageFormat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

// Streams
import java.util.stream.Collectors;
import java.util.stream.Stream;

enum CharClass {
    // basic
    LETTER, DIGIT, DOT,
    // strings
    QUOTE,
    // whitespaces
    NL, WS,
    // arithmetic
    PLUS, MINUS,
    // special
    COMMA, COLON, SEMICOLON,
    // parens and braces
    LPAREN, RPAREN, LBRACE, RBRACE,
    // logical
    EQUALS, LESS, GREATER, NOT, AMPERSAND, PIPE,
    // multiplication or power
    STAR,
    // division or comment
    SLASH,
    /*
     * special
     */
    // wildcard, MUST NOT be result of `classOfChar`
    OTHER,
    // represents a character outside of allowed alphabet
    NOT_A_CHAR;

    static CharClass classOfChar(Character c) {
        // First, check for character groups (letters and digits)
        if (Character.isLetter(c)) return LETTER;
        if (Character.isDigit(c)) return DIGIT;

        // Then, switch on specific single characters
        return switch (c) {
            case '.' -> DOT;
            case '"' -> QUOTE;
            case '\n', '\r' -> NL;
            case ' ', '\t' -> WS;
            case '+' -> PLUS;
            case '-' -> MINUS;
            case ',' -> COMMA;
            case ':' -> COLON;
            case ';' -> SEMICOLON;
            case '(' -> LPAREN;
            case ')' -> RPAREN;
            case '{' -> LBRACE;
            case '}' -> RBRACE;
            case '=' -> EQUALS;
            case '<' -> LESS;
            case '>' -> GREATER;
            case '!' -> NOT;
            case '&' -> AMPERSAND;
            case '|' -> PIPE;
            case '*' -> STAR;
            case '/' -> SLASH;
            default -> NOT_A_CHAR;
        };
    }
}

record StateBranch(Integer thisState, CharClass nextChar, Integer nextState) {
    public static StateBranch f(Integer thisState, CharClass nextChar, Integer nextState) {
        return new StateBranch(thisState, nextChar, nextState);
    }
}

record Pair<K extends Comparable<K>, V extends Comparable<V>>(K first, V second) implements Comparable<Pair<K, V>> {
    @Override
    public String toString() {
        return MessageFormat.format("({0}, {1})", first, second);
    }

    @Override
    public int compareTo(Pair<K, V> other) {
        return Comparator.comparing((Pair<K, V> p) -> p.first())
            .thenComparing((Pair<K, V> p) -> p.second())
            .compare(this, other);
    }
}

class STF {
    TreeMap<Pair<Integer, CharClass>, Integer> transitions;

    @Override
    public String toString() {
        var gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

        return gson.toJson(this);
    }

    int nextState(int thisState, CharClass cls) {
        return this.transitions.getOrDefault(
            new Pair<>(thisState, cls),
            // All characters naturally belong to some class, but
            // not all classes can be expected here.
            //
            // In such cases, we try the special wildcard OTHER case
            this.transitions.get(new Pair<>(thisState, CharClass.OTHER))
        );
    }

    static STF buildTable() {
        var stf = new STF();

        var table = Stream.of(new StateBranch[] {
            // Identifiers
            StateBranch.f(0, CharClass.LETTER, 1),
            // Continue to collect symbols
            StateBranch.f(1, CharClass.LETTER, 1),
            StateBranch.f(1, CharClass.DIGIT, 1),
            // Finished, end with Ident? and continue
            StateBranch.f(1, CharClass.OTHER, 2),

            // Integers/Real numbers
            StateBranch.f(0, CharClass.DIGIT, 4),
            StateBranch.f(4, CharClass.DIGIT, 4),
            // Got a dot, floating point is on its way
            StateBranch.f(4, CharClass.DOT, 5),
            // Finished with no dot, end with Integer and continue
            StateBranch.f(4, CharClass.OTHER, 8),
            // Got the dot, continue collecting
            StateBranch.f(5, CharClass.DIGIT, 6),
            // Expected digit after the dot, got smth else, error
            StateBranch.f(5, CharClass.OTHER, 102),
            // Continue to collect symbols
            StateBranch.f(6, CharClass.DIGIT, 6),
            // Finished, end with Float and continue
            StateBranch.f(6, CharClass.OTHER, 7),

            // String literals
            StateBranch.f(0, CharClass.QUOTE, 13),
            // Grab any symbol not a quote
            StateBranch.f(13, CharClass.OTHER, 13),
            // Finished, end with String and continue
            StateBranch.f(13, CharClass.QUOTE, 14),
            // Expected no newline, error
            StateBranch.f(13, CharClass.NL, 103),

            // Whitespace
            StateBranch.f(0, CharClass.WS, 0),
            StateBranch.f(0, CharClass.NL, 3),
            // Started with unexpected symbol, error
            StateBranch.f(0, CharClass.OTHER, 101),

            // Arithmetic and Punctuation
            StateBranch.f(0, CharClass.PLUS, 15),
            StateBranch.f(0, CharClass.MINUS, 15),
            StateBranch.f(0, CharClass.COMMA, 15),
            StateBranch.f(0, CharClass.COLON, 15),
            StateBranch.f(0, CharClass.SEMICOLON, 15),
            StateBranch.f(0, CharClass.LPAREN, 15),
            StateBranch.f(0, CharClass.RPAREN, 15),
            StateBranch.f(0, CharClass.LBRACE, 15),
            StateBranch.f(0, CharClass.RBRACE, 15),
            // Got a star, will we get two?
            StateBranch.f(0, CharClass.STAR, 16),
            // Finished, end with Multiplication and continue
            StateBranch.f(16, CharClass.OTHER, 18),
            // Finished, end with Power and continue
            StateBranch.f(16, CharClass.STAR, 17),

            // Comment (or division)
            StateBranch.f(0, CharClass.SLASH, 9),
            // Finished, not a comment, end with Division and continue
            StateBranch.f(9, CharClass.OTHER, 12),
            // Actually a comment
            StateBranch.f(9, CharClass.SLASH, 10),
            // Read everything
            StateBranch.f(10, CharClass.OTHER, 10),
            // Until newline, and then finish
            StateBranch.f(10, CharClass.NL, 11),

            // Relations and Assignment
            StateBranch.f(0, CharClass.EQUALS, 22),
            StateBranch.f(22, CharClass.EQUALS, 25),
            StateBranch.f(22, CharClass.OTHER, 23),
            StateBranch.f(0, CharClass.LESS, 24),
            StateBranch.f(0, CharClass.GREATER, 24),
            StateBranch.f(0, CharClass.NOT, 24),
            StateBranch.f(24, CharClass.EQUALS, 25),
            StateBranch.f(24, CharClass.OTHER, 26),

            // Logical
            StateBranch.f(0, CharClass.AMPERSAND, 19),
            StateBranch.f(19, CharClass.AMPERSAND, 20),
            // Expected another Ampersand, error
            StateBranch.f(19, CharClass.OTHER, 104),
            StateBranch.f(0, CharClass.PIPE, 21),
            StateBranch.f(21, CharClass.PIPE, 20),
            // Expected another Pipe, error
            StateBranch.f(21, CharClass.OTHER, 104)
        }).collect(Collectors.toMap(
            data -> new Pair<>(data.thisState(), data.nextChar()),
            data -> data.nextState(),
            (v1, v2) -> v2,
            TreeMap::new
        ));

        stf.transitions = table;
        return stf;
    }

}

sealed interface Token permits
    Keyword,
    Ident,
    IntLiteral,
    FloatLiteral,
    StrLiteral,
    Symbol {}

record Keyword(String keyword) implements Token {
    @Override
    public String toString() {
        return "Keyword: " + '"' + keyword + '"';
    }
}

record Ident(String ident) implements Token {
    @Override
    public String toString() {
        return "Ident: " + '"' + ident + '"';
    }
}

record IntLiteral(String intLiteral) implements Token {
    @Override
    public String toString() {
        return "Int: " + '"' + intLiteral + '"';
    }
}

record FloatLiteral(String floatLiteral) implements Token {
    @Override
    public String toString() {
        return "Float: " + '"' + floatLiteral + '"';
    }
}

record StrLiteral(String strLiteral) implements Token {
    @Override
    public String toString() {
        return "Str: " + '"' + strLiteral + '"';
    }
}

record Symbol(String symbol) implements Token {
    @Override
    public String toString() {
        var theSymbol = symbol.replace("\n", "\\n");
        return "Symbol: " + '"' + theSymbol + '"';
    }
}

public class Lexer {
    /*
     * Static data
     */
    static final int initState = 0;
    static final Set<Integer> statesEnd = Set.of(2, 3, 7, 8, 11, 12, 14, 15, 17, 18, 20, 23, 25, 26, 101, 102, 103, 104);
    static final Set<Integer> statesEndSpecial = Set.of(2, 7, 8, 12, 18, 23, 26);
    static final Set<Integer> statesError = Set.of(101, 102, 103, 104);
    static final STF stf = STF.buildTable();
    static Set<String> keywords = Set.of(
        // declarators
        "var", "let",
        // input
        "input",
        // control flow
        "if", "else", "switch", "case", "default",
        // loops
        "for", "while", "in", "range",
        // func
        "func", "return",
        // print
        "print",
        // types
        "Void", "Int", "Double", "String", "Bool",
        // literals
        "true", "false"
    );
    /*
     * Lexer state
     */
    int numChar = 0;
    int lineCounter = 1;
    int lexemeStartLine = 1;
    int lexemeStartChar = 0;
    int state = Lexer.initState;
    String lexemeBuffer = "";
    public TreeMap<Pair<Pair<Integer, Integer>, Pair<Integer, Integer>>, Token> tokenTable = new TreeMap<>();

    /*
     * Lexer data
     */
    // Preferably access this thing via nextChar() only, or things may break
    String _sourceCode;

    /*
     * Here go dragons
     */

    // Get the next char and manage the lexer inner state
    //
    // Will return null if source code ends
    Character nextChar() {
        try {
            var ch = this._sourceCode.charAt(numChar);
            this.numChar++;
            if (ch == '\n') {
                this.lineCounter++;
            }

            return ch;
        } catch (StringIndexOutOfBoundsException e) {
            return null;
        }
    }

    // Get the next state from current state and the next character's class
    int nextState(int thisState, CharClass cls) {
        return this.stf.nextState(thisState, cls);
    }

    // Does the thing
    public void lex() {
        while (true) {
            System.out.println("======== " + this.numChar + " state: " + this.state);
            var ch = nextChar();
            if (ch == null) {
                if (this.state == Lexer.initState) {
                    System.out.println("the end");
                    return;
                } else {
                    // arguably a hack, but neccessary to catch malformed strings
                    ch = '\n';
                }
            }

            System.out.println("ch: " + ch);

            var cls = CharClass.classOfChar(ch);
            System.out.println(cls);

            if (this.state == Lexer.initState) {
                this.lexemeStartLine = this.lineCounter;
                this.lexemeStartChar = this.numChar;
            }

            this.state = nextState(state, cls);

            System.out.println("nexState: " + this.state);
            System.out.println("lexeme: " + this.lexemeBuffer);

            this.lexemeBuffer += ch;
            if (statesEnd.contains(this.state)) {
                semanticallyProcess();
            } else if (this.state == Lexer.initState) {
                this.lexemeBuffer = "";
            } else {
                // this.lexemeBuffer += ch;
            }
        }
    }

    void semanticallyProcess() {
        if (statesError.contains(this.state)) {
            throw new RuntimeException(
                "E" + this.state + ": on" + this.lineCounter
            );
        }

        // Put the peeked character back, if needed
        if (statesEndSpecial.contains(this.state)) {
            // FIXME: handle newlines?
            this.numChar -= 1;
            this.lexemeBuffer = this.lexemeBuffer.substring(
                0,
                this.lexemeBuffer.length() - 1
            );
        }

        // Locate the span
        var span = new Pair<>(
            new Pair<>(this.lexemeStartLine, this.lexemeStartChar),
            new Pair<>(this.lineCounter, this.numChar)
        );

        // Grab the token
        Token token;
        switch (this.state) {
            // Keyword or Ident
            case 2 -> {
                if (keywords.contains(this.lexemeBuffer)) {
                    token = new Keyword(this.lexemeBuffer);
                } else {
                    token = new Ident(this.lexemeBuffer);
                }
            }
            case 7 -> {
                token = new FloatLiteral(this.lexemeBuffer);
            }
            case 8 -> {
                token = new IntLiteral(this.lexemeBuffer);
            }
            case 14 -> {
                // strip quotes from ends of string literal
                var content = this.lexemeBuffer.substring(
                    1,
                    this.lexemeBuffer.length() - 1
                );
                token = new StrLiteral(content);
            }
            default -> {
                token = new Symbol(this.lexemeBuffer);
            }
        }

        // Put the token into the table along with the span info
        // FIXME: fix
        if (this.state != 11 || this.state != 3) {
            tokenTable.put(span, token);
        }
        System.out.println(token);


        this.lexemeBuffer = "";
        this.state = Lexer.initState;
    }

    public Lexer(String sourceCode) {
        this._sourceCode = sourceCode;
    }

    public static void main(String[] args) {
        var lexer = new Lexer("let x = 5;\n");
        // System.out.println(Lexer.stf);
        lexer.lex();
        System.out.println(lexer.tokenTable);
    }
}
