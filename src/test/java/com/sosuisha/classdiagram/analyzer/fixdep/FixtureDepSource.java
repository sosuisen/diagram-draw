package com.sosuisha.classdiagram.analyzer.fixdep;

/**
 * Fixture: dependency source.
 * AGGREGATION to FixtureDepSourcePart (stored constructor param).
 * process() has local var FixtureDepTarget → DEPENDENCY.
 * check() has param FixtureDepTargetPart → DEPENDENCY.
 */
public class FixtureDepSource {
    private FixtureDepSourcePart part;

    public FixtureDepSource(FixtureDepSourcePart part) {
        this.part = part;
    }

    public String process() {
        FixtureDepTarget target = new FixtureDepTarget();
        return target.id();
    }

    public String check(FixtureDepTargetPart query) {
        return query.label().equals(part.value()) ? "match" : "no";
    }
}
