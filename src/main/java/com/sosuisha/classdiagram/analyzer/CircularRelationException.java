package com.sosuisha.classdiagram.analyzer;

import java.io.Serial;

/**
 * ClassRelationSorterが循環参照を検出した際にスローする例外。
 */
public class CircularRelationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CircularRelationException(String message) {
        super(message);
    }
}
