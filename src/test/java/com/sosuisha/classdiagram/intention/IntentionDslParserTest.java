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
        assertEquals(1, result.placeConstraints().size());
        var c = result.placeConstraints().get(0);
        assertEquals("Item", c.target());
        assertEquals(PlaceDirection.BELOW, c.direction());
        assertEquals("Order", c.reference());
        assertEquals(1, c.lineNumber());
    }

    @Test
    void parsesPlaceAbove() {
        var result = parser.parse("place Item above Order");
        assertEquals(1, result.placeConstraints().size());
        assertEquals(PlaceDirection.ABOVE, result.placeConstraints().get(0).direction());
        assertEquals("Item", result.placeConstraints().get(0).target());
        assertEquals("Order", result.placeConstraints().get(0).reference());
    }

    @Test
    void parsesPlaceRightOf() {
        var result = parser.parse("place Repository right of Service");
        assertEquals(1, result.placeConstraints().size());
        assertEquals(PlaceDirection.RIGHT_OF, result.placeConstraints().get(0).direction());
        assertEquals("Repository", result.placeConstraints().get(0).target());
        assertEquals("Service", result.placeConstraints().get(0).reference());
    }

    @Test
    void parsesPlaceLeftOf() {
        var result = parser.parse("place A left of B");
        assertEquals(1, result.placeConstraints().size());
        assertEquals(PlaceDirection.LEFT_OF, result.placeConstraints().get(0).direction());
        assertEquals("A", result.placeConstraints().get(0).target());
        assertEquals("B", result.placeConstraints().get(0).reference());
    }

    @Test
    void skipsBlankLines() {
        var result = parser.parse("\n\n  \nplace A below B");
        assertEquals(1, result.placeConstraints().size());
        assertEquals(4, result.placeConstraints().get(0).lineNumber());
    }

    @Test
    void skipsCommentLines() {
        var result = parser.parse("# comment\nplace A below B");
        assertEquals(1, result.placeConstraints().size());
        assertEquals(2, result.placeConstraints().get(0).lineNumber());
    }

    @Test
    void parsesMultipleStatements() {
        var result = parser.parse("place A below B\nplace C right of D");
        assertEquals(2, result.placeConstraints().size());
        assertEquals(PlaceDirection.BELOW, result.placeConstraints().get(0).direction());
        assertEquals(PlaceDirection.RIGHT_OF, result.placeConstraints().get(1).direction());
        assertEquals(1, result.placeConstraints().get(0).lineNumber());
        assertEquals(2, result.placeConstraints().get(1).lineNumber());
    }

    @Test
    void returnsImmutableList() {
        var result = parser.parse("place A below B");
        assertThrows(UnsupportedOperationException.class,
            () -> result.placeConstraints().add(null));
        assertThrows(UnsupportedOperationException.class,
            () -> result.arrowConstraints().add(null));
    }

    @Test
    void throwsForNullDsl() {
        assertThrows(NullPointerException.class, () -> parser.parse(null));
    }

    @Test
    void throwsForUnknownVerb() {
        var ex = assertThrows(IntentionParseException.class,
            () -> parser.parse("connect A B"));
        assertEquals(1, ex.lineNumber());
        assertTrue(ex.getMessage().contains("1"));
        assertTrue(ex.getMessage().contains("connect"));
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
            () -> parser.parse("place A below B\nconnect X Y"));
        assertEquals(2, ex.lineNumber());
    }

    // --- ArrowConstraint ---

    @Test
    void arrowConstraintFieldsAreAccessible() {
        var c = new ArrowConstraint("A", "B", ArrowEdge.BOTTOM, ArrowEdge.TOP, 2);
        assertEquals("A", c.source());
        assertEquals("B", c.target());
        assertEquals(ArrowEdge.BOTTOM, c.fromEdge());
        assertEquals(ArrowEdge.TOP, c.toEdge());
        assertEquals(2, c.lineNumber());
    }

    @Test
    void arrowConstraintToEdgeCanBeNull() {
        var c = new ArrowConstraint("A", "B", ArrowEdge.BOTTOM, null, 1);
        assertNull(c.toEdge());
    }

    @Test
    void arrowConstraintNullSourceThrows() {
        assertThrows(NullPointerException.class,
            () -> new ArrowConstraint(null, "B", ArrowEdge.BOTTOM, null, 1));
    }

    @Test
    void arrowConstraintNullTargetThrows() {
        assertThrows(NullPointerException.class,
            () -> new ArrowConstraint("A", null, ArrowEdge.BOTTOM, null, 1));
    }

    @Test
    void arrowConstraintNullFromEdgeThrows() {
        assertThrows(NullPointerException.class,
            () -> new ArrowConstraint("A", "B", null, null, 1));
    }

    // --- ParseResult ---

    @Test
    void parseResultNullPlaceListThrows() {
        assertThrows(NullPointerException.class,
            () -> new ParseResult(null, List.of()));
    }

    @Test
    void parseResultNullArrowListThrows() {
        assertThrows(NullPointerException.class,
            () -> new ParseResult(List.of(), null));
    }

    @Test
    void parseResultListsAreImmutable() {
        var result = new ParseResult(new java.util.ArrayList<>(), new java.util.ArrayList<>());
        assertThrows(UnsupportedOperationException.class,
            () -> result.placeConstraints().add(null));
        assertThrows(UnsupportedOperationException.class,
            () -> result.arrowConstraints().add(null));
    }
}
