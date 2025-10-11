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
    // wildcard
    OTHER
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

record Span(Integer line, Integer character) {}
sealed interface Token permits
    Keyword,
    Ident,
    IntLiteral,
    FloatLiteral,
    StrLiteral,
    Symbol {}

record Keyword(String keyword) implements Token {}
record Ident(String name) implements Token {}
record IntLiteral(int value) implements Token {}
record FloatLiteral(double value) implements Token {}
record StrLiteral(String value) implements Token {}
record Symbol(String value) implements Token {}

public class Lexer {
    /*
     * Static data
     */
    static final int initState = 0;
    static final Set<Integer> statesEnd = Set.of(2, 3, 7, 8, 11, 12, 14, 15, 17, 18, 20, 23, 25, 26, 101, 102, 103, 104);
    static final Set<Integer> statesSpecial = Set.of(2, 7, 8, 12, 18, 23, 26);
    static final Set<Integer> statesError = Set.of(101, 102, 103, 104);
    static final STF stf = STF.buildTable();

    /*
     * Lexer state
     */
    int numChar = 0;
    int numLine = 1;
    TreeMap<Span, Token> tokenTable = new TreeMap<>();

    public static void main(String[] args) {
        var app = new Lexer();
        System.out.println(Lexer.stf);
    }
}
