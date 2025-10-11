import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import au.com.origin.snapshots.Expect;
import au.com.origin.snapshots.annotations.SnapshotName;
import au.com.origin.snapshots.junit5.SnapshotExtension;

import org.junit.jupiter.api.extension.ExtendWith;

import org.example.Lexer;

@ExtendWith({SnapshotExtension.class})
public class AppTest {
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
