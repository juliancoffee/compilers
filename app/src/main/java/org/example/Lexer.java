package org.example;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;

import com.google.gson.GsonBuilder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.stream.Collectors;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.example.Pair;
import org.example.Token;

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
            // Got minus, maybe it's a type arrow?
            StateBranch.f(0, CharClass.MINUS, 30),
            // Finished, end with Minus and continue
            StateBranch.f(30, CharClass.OTHER, 31),
            // Finished, end with TypeArrow and continue
            StateBranch.f(30, CharClass.GREATER, 32),
            // Other simple things
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


public class Lexer {
    /*
     * Static data
     */
    static final int initState = 0;
    static final Set<Integer> statesEnd = Set.of(2, 3, 7, 8, 11, 12, 14, 15, 17, 18, 20, 23, 25, 26, 31, 32, 101, 102, 103, 104);
    static final Set<Integer> statesEndSpecial = Set.of(2, 7, 8, 12, 18, 23, 26, 31);
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
     * Globals
     */
    private static final Logger log = LogManager.getLogger("lexer");

    /*
     * Lexer state
     */
    int numChar = 0;
    int lexemeStartChar = 0;
    int state = Lexer.initState;
    String lexemeBuffer = "";

    /*
     * Output
     */
    public ArrayList<Integer> lineIndex = new ArrayList(Set.of(0));
    public TreeMap<Pair<Integer, Integer>, Token> tokenTable = new TreeMap<>();

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
                this.lineIndex.add(this.numChar);
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
    //
    // Populates tokenTable (may throw an exception on invalid lexer input)
    public void lex() {
        while (true) {
            log.debug("======== " + this.numChar + " state: " + this.state);
            var ch = nextChar();
            if (ch == null) {
                if (this.state == Lexer.initState) {
                    log.debug("the end");
                    return;
                } else {
                    // arguably a hack, but neccessary to catch malformed strings
                    ch = '\n';
                }
            }

            log.debug("ch: " + ch);

            var cls = CharClass.classOfChar(ch);
            log.debug(cls);

            if (this.state == Lexer.initState) {
                this.lexemeStartChar = this.numChar;
            }

            this.state = nextState(state, cls);

            log.debug("nexState: " + this.state);
            log.debug("lexeme: " + '"' + this.lexemeBuffer + '"');

            this.lexemeBuffer += ch;
            if (statesEnd.contains(this.state)) {
                semanticallyProcess();
            } else if (this.state == Lexer.initState) {
                this.lexemeBuffer = "";
            }
        }
    }

    void semanticallyProcess() {
        if (statesError.contains(this.state)) {
            // add error span
            var error_span = new Pair<>(
                this.lexemeStartChar, this.numChar
            );
            tokenTable.put(error_span, new Error(this.lexemeBuffer));

            var msg = switch(this.state) {
                case 101 -> "\nErr: unexpected symbol: " + this.lexemeBuffer;
                case 102 -> "\nErr: malformed number literal: " + this.lexemeBuffer;
                case 103 -> "\nErr: malformed string literal: " + this.lexemeBuffer;
                case 104 -> "\nErr: malformed || or && : " + this.lexemeBuffer;
                default -> "\nErr: ?? : " + this.lexemeBuffer;
            };

            var span = SpanUtils.formatSpan(error_span, this.lineIndex);
            throw new RuntimeException(
                "E" + this.state +
                ": in range of " + span
                + msg
            );
        }

        // Put the peeked character back, if needed
        if (statesEndSpecial.contains(this.state)) {
            this.numChar -= 1;
            this.lexemeBuffer = this.lexemeBuffer.substring(
                0,
                this.lexemeBuffer.length() - 1
            );
        }

        // Locate the span
        var span = new Pair<>(
            this.lexemeStartChar, this.numChar
        );


        // Grab the token
        Token token;
        switch (this.state) {
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
        //
        // Unless it's newline or a comment, which we want to ignore
        if (this.state != 11 && this.state != 3) {
            tokenTable.put(span, token);
        }
        log.debug(token);


        this.lexemeBuffer = "";
        this.state = Lexer.initState;
    }

    public Lexer(String sourceCode) {
        this._sourceCode = sourceCode;
    }
}
