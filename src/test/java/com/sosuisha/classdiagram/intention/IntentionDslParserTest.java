package com.sosuisha.classdiagram.intention;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class IntentionDslParserTest {

    // --- IntentionParseException ---

    @Test
    void exceptionLineNumberIsStoredAndAppearsInMessage() {
        var ex = new IntentionParseException(3, "bad input");
        assertEquals(3, ex.lineNumber());
        assertTrue(ex.getMessage().contains("3"), "message must contain line number");
        assertTrue(ex.getMessage().contains("bad input"));
    }

    // --- PlaceConstraint ---

    @Test
    void placeConstraintFieldsAreAccessible() {
        var c = new PlaceConstraint("Item", PlaceDirection.BELOW, "Order", 1);
        assertEquals("Item", c.target());
        assertEquals(PlaceDirection.BELOW, c.direction());
        assertEquals("Order", c.reference());
        assertEquals(1, c.lineNumber());
    }

    @Test
    void placeConstraintNullTargetThrows() {
        assertThrows(NullPointerException.class,
            () -> new PlaceConstraint(null, PlaceDirection.BELOW, "Order", 1));
    }

    @Test
    void placeConstraintNullDirectionThrows() {
        assertThrows(NullPointerException.class,
            () -> new PlaceConstraint("Item", null, "Order", 1));
    }

    @Test
    void placeConstraintNullReferenceThrows() {
        assertThrows(NullPointerException.class,
            () -> new PlaceConstraint("Item", PlaceDirection.BELOW, null, 1));
    }

    // --- IntentionDslParser ---

    private final IntentionDslParser parser = new IntentionDslParser();

    @Test
    void parsesPlaceBelow() {
        var result = parser.parse("place Item below Order");
        assertEquals(1, result.size());
        var c = result.get(0);
        assertEquals("Item", c.target());
        assertEquals(PlaceDirection.BELOW, c.direction());
        assertEquals("Order", c.reference());
        assertEquals(1, c.lineNumber());
    }

    @Test
    void parsesPlaceAbove() {
        var result = parser.parse("place Item above Order");
        assertEquals(1, result.size());
        assertEquals(PlaceDirection.ABOVE, result.get(0).direction());
        assertEquals("Item", result.get(0).target());
        assertEquals("Order", result.get(0).reference());
    }

    @Test
    void parsesPlaceRightOf() {
        var result = parser.parse("place Repository right of Service");
        assertEquals(1, result.size());
        assertEquals(PlaceDirection.RIGHT_OF, result.get(0).direction());
        assertEquals("Repository", result.get(0).target());
        assertEquals("Service", result.get(0).reference());
    }

    @Test
    void parsesPlaceLeftOf() {
        var result = parser.parse("place A left of B");
        assertEquals(1, result.size());
        assertEquals(PlaceDirection.LEFT_OF, result.get(0).direction());
        assertEquals("A", result.get(0).target());
        assertEquals("B", result.get(0).reference());
    }

    @Test
    void skipsBlankLines() {
        var result = parser.parse("\n\n  \nplace A below B");
        assertEquals(1, result.size());
        assertEquals(4, result.get(0).lineNumber());
    }

    @Test
    void skipsCommentLines() {
        var result = parser.parse("# comment\nplace A below B");
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).lineNumber());
    }

    @Test
    void parsesMultipleStatements() {
        var result = parser.parse("place A below B\nplace C right of D");
        assertEquals(2, result.size());
        assertEquals(PlaceDirection.BELOW, result.get(0).direction());
        assertEquals(PlaceDirection.RIGHT_OF, result.get(1).direction());
        assertEquals(1, result.get(0).lineNumber());
        assertEquals(2, result.get(1).lineNumber());
    }

    @Test
    void returnsImmutableList() {
        var result = parser.parse("place A below B");
        assertThrows(UnsupportedOperationException.class, () -> result.add(null));
    }

    @Test
    void throwsForNullDsl() {
        assertThrows(NullPointerException.class, () -> parser.parse(null));
    }

    @Test
    void throwsForUnknownVerb() {
        var ex = assertThrows(IntentionParseException.class,
            () -> parser.parse("arrow A B from bottom"));
        assertEquals(1, ex.lineNumber());
        assertTrue(ex.getMessage().contains("1"));
        assertTrue(ex.getMessage().contains("arrow"));
    }

    @Test
    void throwsForUnknownDirection() {
        var ex = assertThrows(IntentionParseException.class,
            () -> parser.parse("place A under B"));
        assertEquals(1, ex.lineNumber());
        assertTrue(ex.getMessage().contains("under"));
    }

    @Test
    void throwsForTooFewTokens() {
        var ex = assertThrows(IntentionParseException.class,
            () -> parser.parse("place A"));
        assertEquals(1, ex.lineNumber());
    }

    @Test
    void throwsForIncompleteRightOf() {
        var ex = assertThrows(IntentionParseException.class,
            () -> parser.parse("place A right B"));
        assertEquals(1, ex.lineNumber());
    }

    @Test
    void lineNumberReflectsActualLineInMultilineInput() {
        var ex = assertThrows(IntentionParseException.class,
            () -> parser.parse("place A below B\narrow X Y from top"));
        assertEquals(2, ex.lineNumber());
    }
}
