package com.sosuisha.classdiagram.intention;

import java.util.List;
import java.util.Objects;

/**
 * {@link IntentionDslParser#parse} の解析結果。
 * {@link PlaceConstraint} と {@link ArrowConstraint} の両リストを保持する。
 *
 * @param placeConstraints パース済み {@code place} 制約のリスト（変更不可）
 * @param arrowConstraints パース済み {@code arrow} 制約のリスト（変更不可）
 */
public record ParseResult(
    List<PlaceConstraint> placeConstraints,
    List<ArrowConstraint> arrowConstraints
) {
    public ParseResult {
        Objects.requireNonNull(placeConstraints, "placeConstraints must not be null");
        Objects.requireNonNull(arrowConstraints, "arrowConstraints must not be null");
        placeConstraints = List.copyOf(placeConstraints);
        arrowConstraints = List.copyOf(arrowConstraints);
    }
}
