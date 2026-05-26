package com.sosuisha.classdiagram.intention;

import java.util.Objects;

/**
 * パース済みの {@code arrow} 制約文を表すレコード。
 *
 * @param source     ソースクラスの単純名
 * @param target     ターゲットクラスの単純名
 * @param fromEdge   ソース側出口辺
 * @param toEdge     ターゲット側入口辺（{@code null} = 自動計算）
 * @param lineNumber エラー報告用の元行番号（1始まり）
 */
public record ArrowConstraint(
    String source,
    String target,
    ArrowEdge fromEdge,
    ArrowEdge toEdge,
    int lineNumber
) {
    public ArrowConstraint {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(fromEdge, "fromEdge must not be null");
    }
}
