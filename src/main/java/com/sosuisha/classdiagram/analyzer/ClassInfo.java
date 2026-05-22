package com.sosuisha.classdiagram.analyzer;

import com.sosuisha.classdiagram.ClassStereotype;
import java.util.Objects;

/**
 * クラスのパッケージ名・単純名・ステレオタイプを保持する識別子。
 *
 * <p>同一性は packageName + simpleName + stereotype で決まる。
 * groupIndex はレイアウト用メタデータであり同一性に含まれない。
 */
public final class ClassInfo {

    private final String packageName;
    private final String simpleName;
    private final ClassStereotype stereotype;
    private int groupIndex;

    /**
     * ClassInfoを生成する。
     *
     * @param packageName パッケージ名
     * @param simpleName  単純名
     * @param stereotype  ステレオタイプ
     * @throws NullPointerException packageName、simpleName、またはstereotypeがnullの場合
     */
    public ClassInfo(String packageName, String simpleName, ClassStereotype stereotype) {
        this.packageName = Objects.requireNonNull(packageName, "packageName must not be null");
        this.simpleName = Objects.requireNonNull(simpleName, "simpleName must not be null");
        this.stereotype = Objects.requireNonNull(stereotype, "stereotype must not be null");
        this.groupIndex = 0;
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

    /** @return パッケージ名 */
    public String packageName() { return packageName; }

    /** @return 単純名 */
    public String simpleName() { return simpleName; }

    /** @return ステレオタイプ */
    public ClassStereotype stereotype() { return stereotype; }

    /** @return グループインデックス（デフォルト0） */
    public int groupIndex() { return groupIndex; }

    /**
     * グループインデックスを設定する。{@link ConnectedComponentSplitter} が呼び出す。
     *
     * @param groupIndex グループインデックス（0以上）
     */
    public void setGroupIndex(int groupIndex) {
        this.groupIndex = groupIndex;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClassInfo other)) return false;
        return packageName.equals(other.packageName)
            && simpleName.equals(other.simpleName)
            && stereotype == other.stereotype;
    }

    @Override
    public int hashCode() {
        return Objects.hash(packageName, simpleName, stereotype);
    }

    @Override
    public String toString() {
        return packageName + "." + simpleName + "[" + stereotype + ",g=" + groupIndex + "]";
    }
}
