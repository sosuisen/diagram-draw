package com.sosuisha.classdiagram.analyzer;

import com.sosuisha.classdiagram.ClassStereotype;
import java.util.Objects;

/**
 * クラスのパッケージ名・単純名・ステレオタイプを保持する識別子。
 *
 * @param packageName パッケージ名（例: {@code "com.sosuisha.classdiagram"}）
 * @param simpleName  単純名（パッケージを除く。例: {@code "Order"}）
 * @param stereotype  ステレオタイプ（例: {@code ClassStereotype.INTERFACE}）
 */
public record ClassInfo(String packageName, String simpleName, ClassStereotype stereotype) {

    /**
     * コンポーネントnullチェックを行うコンパクトコンストラクタ。
     *
     * @throws NullPointerException packageName、simpleName、またはstereotypeがnullの場合
     */
    public ClassInfo {
        Objects.requireNonNull(packageName, "packageName must not be null");
        Objects.requireNonNull(simpleName, "simpleName must not be null");
        Objects.requireNonNull(stereotype, "stereotype must not be null");
    }

    /**
     * ステレオタイプを {@code NONE} としてClassInfoを生成する後方互換コンストラクタ。
     *
     * @param packageName パッケージ名
     * @param simpleName  単純名
     * @throws NullPointerException packageNameまたはsimpleNameがnullの場合
     */
    public ClassInfo(String packageName, String simpleName) {
        this(packageName, simpleName, ClassStereotype.NONE);
    }

    /**
     * 完全修飾名からClassInfoを生成する（stereotype = NONE）。
     *
     * @param fqn 完全修飾名（例: {@code "com.sosuisha.classdiagram.Order"}）
     * @return ClassInfoインスタンス（stereotype = NONE）
     * @throws NullPointerException fqnがnullの場合
     * @throws IllegalArgumentException fqnが完全修飾名でない場合（ドットを含まない）
     */
    public static ClassInfo fromFullyQualifiedName(String fqn) {
        return fromFullyQualifiedName(fqn, ClassStereotype.NONE);
    }

    /**
     * 完全修飾名とステレオタイプからClassInfoを生成する。
     *
     * @param fqn        完全修飾名（例: {@code "com.sosuisha.classdiagram.IService"}）
     * @param stereotype ステレオタイプ
     * @return ClassInfoインスタンス
     * @throws NullPointerException fqnまたはstereotypeがnullの場合
     * @throws IllegalArgumentException fqnが完全修飾名でない場合（ドットを含まない）
     */
    public static ClassInfo fromFullyQualifiedName(String fqn, ClassStereotype stereotype) {
        Objects.requireNonNull(fqn, "fqn must not be null");
        Objects.requireNonNull(stereotype, "stereotype must not be null");
        int dot = fqn.lastIndexOf('.');
        if (dot <= 0 || dot == fqn.length() - 1) {
            throw new IllegalArgumentException(
                "fqn must be a fully qualified name containing at least one '.': " + fqn);
        }
        return new ClassInfo(fqn.substring(0, dot), fqn.substring(dot + 1), stereotype);
    }
}
