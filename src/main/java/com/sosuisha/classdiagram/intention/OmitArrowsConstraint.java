package com.sosuisha.classdiagram.intention;

import java.util.Objects;

/**
 * パース済みの {@code omit arrows of} 文を表すレコード。
 * 指定クラスに接続するすべての線を描画しない。
 *
 * @param className  線を省略するクラスの単純名
 * @param lineNumber エラー報告用の元行番号（1始まり）
 * @throws NullPointerException classNameがnullの場合
 */
public record OmitArrowsConstraint(String className, int lineNumber) {
    public OmitArrowsConstraint {
        Objects.requireNonNull(className, "className must not be null");
    }
}
