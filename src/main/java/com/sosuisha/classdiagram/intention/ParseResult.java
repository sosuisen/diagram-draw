package com.sosuisha.classdiagram.intention;

import java.util.List;
import java.util.Objects;

/**
 * {@link IntentionDslParser#parse} の解析結果。
 * {@link PlaceConstraint}、{@link ArrowConstraint}、{@link OmitArrowsConstraint} のリストを保持する。
 *
 * @param placeConstraints      パース済み {@code place} 制約のリスト（変更不可）
 * @param arrowConstraints      パース済み {@code arrow} 制約のリスト（変更不可）
 * @param omitArrowsConstraints パース済み {@code omit arrows of} 制約のリスト（変更不可）
 * @throws NullPointerException いずれかのリストがnullの場合
 */
public record ParseResult(
    List<PlaceConstraint> placeConstraints,
    List<ArrowConstraint> arrowConstraints,
    List<OmitArrowsConstraint> omitArrowsConstraints
) {
    public ParseResult {
        Objects.requireNonNull(placeConstraints, "placeConstraints must not be null");
        Objects.requireNonNull(arrowConstraints, "arrowConstraints must not be null");
        Objects.requireNonNull(omitArrowsConstraints, "omitArrowsConstraints must not be null");
        placeConstraints = List.copyOf(placeConstraints);
        arrowConstraints = List.copyOf(arrowConstraints);
        omitArrowsConstraints = List.copyOf(omitArrowsConstraints);
    }

    /**
     * {@code omit arrows of} 制約を持たない解析結果を生成する。
     *
     * @param placeConstraints パース済み {@code place} 制約のリスト
     * @param arrowConstraints パース済み {@code arrow} 制約のリスト
     * @throws NullPointerException いずれかのリストがnullの場合
     */
    public ParseResult(List<PlaceConstraint> placeConstraints, List<ArrowConstraint> arrowConstraints) {
        this(placeConstraints, arrowConstraints, List.of());
    }
}
