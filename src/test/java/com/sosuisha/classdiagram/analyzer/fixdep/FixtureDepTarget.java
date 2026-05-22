package com.sosuisha.classdiagram.analyzer.fixdep;

/** Fixture: dependency target. Has COMPOSITION to FixtureDepTargetPart. */
public class FixtureDepTarget {
    private FixtureDepTargetPart part;

    public FixtureDepTarget() {
        this.part = new FixtureDepTargetPart("default");
    }

    public String id() { return part.label(); }
}
