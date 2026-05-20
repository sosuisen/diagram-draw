package com.sosuisha.classdiagram.analyzer.fixture;

import java.util.ArrayList;
import java.util.List;

public class FixtureOrder {
    private List<FixtureItem> items = new ArrayList<>();
    private FixtureCustomer customer;

    public FixtureOrder(FixtureCustomer customer) {
        this.customer = customer;
    }
}
