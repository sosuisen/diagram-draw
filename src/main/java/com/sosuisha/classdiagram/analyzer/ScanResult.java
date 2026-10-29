package com.sosuisha.classdiagram.analyzer;

import java.util.List;
import java.util.Objects;

/**
 * {@link ClassRelationScanner#scanAll}の結果。
 *
 * <p>{@code classes}には関係を1つも持たないクラスも含まれる。
 * {@code classes}と{@code relations}は同じ{@link ClassInfo}インスタンスを共有する。
 *
 * @param classes   スキャンしたすべてのクラス
 * @param relations 検出された関係
 * @throws NullPointerException classesまたはrelationsがnullの場合
 */
public record ScanResult(List<ClassInfo> classes, List<ClassRelation> relations) {
    public ScanResult {
        Objects.requireNonNull(classes, "classes must not be null");
        Objects.requireNonNull(relations, "relations must not be null");
        classes = List.copyOf(classes);
        relations = List.copyOf(relations);
    }
}
