package com.sosuisha.classdiagram.analyzer.fixself;

/** Fixture: holds a static constant of its own type, like a DEFAULT or INSTANCE field. */
public class FixtureSelfSettings {
    public static final FixtureSelfSettings DEFAULT = new FixtureSelfSettings(FixtureSelfPart.LEFT);

    private final FixtureSelfPart part;

    public FixtureSelfSettings(FixtureSelfPart part) {
        this.part = part;
    }
}
