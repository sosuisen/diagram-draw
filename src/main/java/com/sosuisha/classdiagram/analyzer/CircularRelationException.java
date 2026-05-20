package com.sosuisha.classdiagram.analyzer;

/**
 * ClassRelationSorterが循環参照を検出した際にスローする例外。
 */
public class CircularRelationException extends RuntimeException {
    public CircularRelationException(String message) {
        super(message);
    }
}
