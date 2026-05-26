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
}
