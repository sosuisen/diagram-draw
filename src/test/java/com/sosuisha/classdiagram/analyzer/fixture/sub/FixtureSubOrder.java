package com.sosuisha.classdiagram.analyzer.fixture.sub;

import com.sosuisha.classdiagram.analyzer.fixture.FixtureItem;
import java.util.List;
import java.util.Objects;

public class FixtureSubOrder {
    private final List<FixtureItem> items;

    public FixtureSubOrder(List<FixtureItem> items) {
        this.items = Objects.requireNonNull(items);
    }
}
