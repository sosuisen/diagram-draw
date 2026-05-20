package com.sosuisha.classdiagram.analyzer;

import java.util.Objects;

/**
 * 2クラス間の関係を表す。
 *
 * @param sourceClassInfo フィールドを持つクラス（所有側）
 * @param targetClassInfo フィールドの型クラス（所有される側）
 * @param type            COMPOSITION または AGGREGATION
 * @param isMany          コレクションフィールドの場合true
 * @throws NullPointerException sourceClassInfoまたはtargetClassInfoがnullの場合
 */
public record ClassRelation(
    ClassInfo sourceClassInfo,
    ClassInfo targetClassInfo,
    RelationType type,
    boolean isMany
) {
    public ClassRelation {
        Objects.requireNonNull(sourceClassInfo, "sourceClassInfo must not be null");
        Objects.requireNonNull(targetClassInfo, "targetClassInfo must not be null");
    }
}
