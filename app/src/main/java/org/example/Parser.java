package org.example;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

sealed interface TopLevelStmt extends Stmt
permits
    LetStmt, FuncStmt {}

sealed interface Expression
permits
    LiteralExpr {}

sealed interface Stmt
permits
    TopLevelStmt, PrintStmt, AssignStmt {}

// Program = { LetStmt | FuncStmt }
record ParseTree(ArrayList<TopLevelStmt> stmt) {}

// LetStmt = 'let' Ident '=' Expression ';'
record LetStmt(String ident, Expression expr) implements TopLevelStmt {}

// LiteralExpr = IntLiteral
record LiteralExpr(Integer intLiteral) implements Expression {};

// FuncStmt = 'func' Ident '(' ')' Block
// Block = { Stmt }
record FuncStmt(String ident, ArrayList<Stmt> block) implements TopLevelStmt {}

// PrintStmt = 'print' '(' Expression { ',' Expression } ')' ';'
record PrintStmt(ArrayList<Expression> exprs) implements Stmt {}

// AssignStmt = Ident '=' Expression ';'
record AssignStmt(String ident, Expression expr) implements Stmt {}

public class Parser {
    /*
     * Parser state
     */
    int numToken = 0;

    /*
     * Output
     */
    public ParseTree parseTree = new ParseTree(new ArrayList<>());

    /*
     * Parser data
     */
    ArrayList<Pair<Pair<Integer, Integer>, Token>> _tokenList;
    int tokenListLen;

    void parseLetStmt() {
    }

    void parseTopStmt() {
        var token = this.nextPair().second();
        System.out.println(token);
    }


    void parseTopStatementList() {
        while (this.numToken < tokenListLen) {
            parseTopStmt();
        }
    }

    public void parse() {
        parseTopStatementList();
    }

    Pair<Pair<Integer, Integer>, Token> nextPair() {
        try {
            var token = _tokenList.get(this.numToken);
            this.numToken++;
            return token;
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    public Parser(TreeMap<Pair<Integer, Integer>, Token> tokenTable) {
        this._tokenList = tokenTable
            .entrySet()
            .stream()
            .map(kv -> new Pair<>(kv.getKey(), kv.getValue()))
            .collect(Collectors.toCollection(ArrayList::new));
        this.tokenListLen = this._tokenList.size() - 1;
    }
}
