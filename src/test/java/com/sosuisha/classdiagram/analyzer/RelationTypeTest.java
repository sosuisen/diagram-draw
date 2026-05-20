package com.sosuisha.classdiagram.analyzer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RelationTypeTest {

    @Test
    void compositionEnumConstantExists() {
        assertNotNull(RelationType.COMPOSITION);
    }

    @Test
    void aggregationEnumConstantExists() {
        assertNotNull(RelationType.AGGREGATION);
    }
}
