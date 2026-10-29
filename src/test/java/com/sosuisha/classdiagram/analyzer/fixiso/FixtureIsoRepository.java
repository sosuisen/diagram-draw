package com.sosuisha.classdiagram.analyzer.fixiso;

import java.util.List;

/** Fixture: uses FixtureIsoModel and FixtureIsoItem only as return types. */
public class FixtureIsoRepository {
    private final FixtureIsoStore store = new FixtureIsoStore();

    public FixtureIsoModel find() {
        return null;
    }

    public List<FixtureIsoItem> findAll() {
        return List.of();
    }
}
