package com.sosuisha.classdiagram.analyzer;

import java.util.Objects;

/**
 * クラスのパッケージ名と単純名を保持する識別子。
 *
 * @param packageName パッケージ名（例: {@code "com.sosuisha.classdiagram"}）
 * @param simpleName  単純名（パッケージを除く。例: {@code "Order"}）
 */
public record ClassInfo(String packageName, String simpleName) {

    /**
     * 完全修飾名からClassInfoを生成する。
     *
     * @param fqn 完全修飾名（例: {@code "com.sosuisha.classdiagram.Order"}）
     * @return ClassInfoインスタンス
     * @throws NullPointerException fqnがnullの場合
     */
    public static ClassInfo fromFullyQualifiedName(String fqn) {
        Objects.requireNonNull(fqn, "fqn must not be null");
        int dot = fqn.lastIndexOf('.');
        return new ClassInfo(fqn.substring(0, dot), fqn.substring(dot + 1));
    }
}
