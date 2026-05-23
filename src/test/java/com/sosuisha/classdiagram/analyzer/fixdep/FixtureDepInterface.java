package com.sosuisha.classdiagram.analyzer.fixdep;

/**
 * Fixture: interface whose abstract method takes a same-package type as parameter.
 * handle() has param FixtureDepTarget → should be detected as DEPENDENCY.
 */
public interface FixtureDepInterface {

    /**
     * Handles a target.
     *
     * @param target the target to handle
     */
    void handle(FixtureDepTarget target);
}
