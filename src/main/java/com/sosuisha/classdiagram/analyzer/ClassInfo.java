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
     * コンポーネントnullチェックを行うコンパクトコンストラクタ。
     *
     * @throws NullPointerException packageNameまたはsimpleNameがnullの場合
     */
    public ClassInfo {
        Objects.requireNonNull(packageName, "packageName must not be null");
        Objects.requireNonNull(simpleName, "simpleName must not be null");
    }

    /**
     * 完全修飾名からClassInfoを生成する。
     *
     * @param fqn 完全修飾名（例: {@code "com.sosuisha.classdiagram.Order"}）
     * @return ClassInfoインスタンス
     * @throws NullPointerException fqnがnullの場合
     * @throws IllegalArgumentException fqnが完全修飾名でない場合（ドットを含まない）
     */
    public static ClassInfo fromFullyQualifiedName(String fqn) {
        Objects.requireNonNull(fqn, "fqn must not be null");
        int dot = fqn.lastIndexOf('.');
        if (dot <= 0 || dot == fqn.length() - 1) {
            throw new IllegalArgumentException(
                "fqn must be a fully qualified name containing at least one '.': " + fqn);
        }
        return new ClassInfo(fqn.substring(0, dot), fqn.substring(dot + 1));
    }
}
