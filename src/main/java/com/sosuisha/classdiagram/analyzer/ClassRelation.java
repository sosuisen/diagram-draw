package com.sosuisha.classdiagram.analyzer;

/**
 * Represents a single detected relationship between two classes.
 *
 * @param sourceClass  fully qualified name of the class that owns the field
 * @param targetClass  fully qualified name of the referenced class
 * @param type         COMPOSITION or AGGREGATION classification
 * @param isMany       true if the field is a Collection<T>
 */
public record ClassRelation(
    String sourceClass,
    String targetClass,
    RelationType type,
    boolean isMany
) {}
