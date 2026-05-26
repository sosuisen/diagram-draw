package com.sosuisha.classdiagram.intention;

import java.util.Objects;

/**
 * パース済みの {@code place} 制約文を表すレコード。
 *
 * @param target     配置対象クラスの単純名
 * @param direction  配置方向
 * @param reference  基準クラスの単純名
 * @param lineNumber エラー報告用の元行番号（1始まり）
 */
public record PlaceConstraint(
    String target,
    PlaceDirection direction,
    String reference,
    int lineNumber
) {
    public PlaceConstraint {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(reference, "reference must not be null");
    }
}
