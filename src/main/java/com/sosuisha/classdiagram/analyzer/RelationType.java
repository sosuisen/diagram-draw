package com.sosuisha.classdiagram.analyzer;

/**
 * Classifies the type of relationship between a field and its containing class.
 *
 * <p>
 * COMPOSITION: The field is not a constructor parameter (the containing class creates/owns the
 * object).
 * </p>
 * <p>
 * AGGREGATION: The field IS a constructor parameter (the containing class receives/uses the
 * object).
 * </p>
 */
public enum RelationType {
    /**
     * The field is not in the constructor parameters.
     */
    COMPOSITION,

    /**
     * The field IS in the constructor parameters.
     */
    AGGREGATION
}
