package org.example;

public sealed interface Token {};

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

record Error(String error) implements Token {
    @Override
    public String toString() {
        return "Error: " + '"' + error + '"';
    }
}
