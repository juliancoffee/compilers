package org.example;

import org.example.Pair;

import java.util.*;

public class SpanUtils {
    // Returns (linePos, charPos)
    public static Pair<Integer, Integer> locate(
        int numChar, ArrayList<Integer> lineIndex
    ) {
        var lineFind = Arrays.binarySearch(lineIndex.toArray(), numChar);

        int linePos;
        if (lineFind >= 0) {
            linePos = lineFind;
        } else {
            // binarySearch returns (-(insertion_point) - 1) so we reverse that
            linePos = -(lineFind + 1);
        }

        // it starts with 1, we need to start with 0
        int lineIdx = linePos - 1;

        var charPos = numChar - lineIndex.get(lineIdx);

        return new Pair<>(linePos, charPos);
    }

    public static int lineAt(int numChar, ArrayList<Integer> lineIndex) {
        return SpanUtils.locate(numChar, lineIndex).first();
    }

    public static int charAt(int numChar, ArrayList<Integer> lineIndex) {
        return SpanUtils.locate(numChar, lineIndex).second();
    }

    // Takes a span of two numChars
    public static String formatSpan(
        Pair<Integer, Integer> span,
        ArrayList<Integer> lineIndex
    ) {
        var first_pos = SpanUtils.locate(span.first(), lineIndex);
        var second_pos = SpanUtils.locate(span.second(), lineIndex);
        return String.format("%d,%d..%d,%d",
            first_pos.first(), first_pos.second(),
            second_pos.first(), second_pos.second()
        );
    }
}
