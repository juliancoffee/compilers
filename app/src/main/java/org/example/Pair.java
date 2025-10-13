package org.example;

import java.text.MessageFormat;
import java.util.Comparator;

public record Pair<K extends Comparable<K>, V extends Comparable<V>>(K first, V second) implements Comparable<Pair<K, V>> {
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
