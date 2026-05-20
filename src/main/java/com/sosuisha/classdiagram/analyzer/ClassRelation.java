package com.sosuisha.classdiagram.analyzer;

/**
 * 2クラス間の関係を表す。
 *
 * @param sourceClassInfo フィールドを持つクラス（所有側）
 * @param targetClassInfo フィールドの型クラス（所有される側）
 * @param type            COMPOSITION または AGGREGATION
 * @param isMany          コレクションフィールドの場合true
 */
public record ClassRelation(
    ClassInfo sourceClassInfo,
    ClassInfo targetClassInfo,
    RelationType type,
    boolean isMany
) {}
