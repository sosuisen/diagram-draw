package com.sosuisha.classdiagram;

import java.util.Objects;

/**
 * 同一サブパッケージのクラス群を囲むスケッチ風矩形を表すSVG要素。
 *
 * <p>位置・寸法・ラベルは構築後イミュータブル。
 */
public final class PackageGroupBox implements SvgElement {

    private final String label;
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    /**
     * PackageGroupBoxを生成する。
     *
     * @param label  サブパッケージラベル
     * @param x      左上X座標
     * @param y      左上Y座標
     * @param width  幅（px、正数）
     * @param height 高さ（px、正数）
     * @throws NullPointerException     labelがnullの場合
     * @throws IllegalArgumentException widthまたはheightが0以下の場合
     */
    public PackageGroupBox(String label, int x, int y, int width, int height) {
        Objects.requireNonNull(label, "label must not be null");
        if (width <= 0) {
            throw new IllegalArgumentException("width must be positive: " + width);
        }
        if (height <= 0) {
            throw new IllegalArgumentException("height must be positive: " + height);
        }
        this.label = label;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /** @return サブパッケージラベル */
    public String label() { return label; }

    /** @return 左上X座標 */
    public int x() { return x; }

    /** @return 左上Y座標 */
    public int y() { return y; }

    /** @return 幅（px） */
    public int width() { return width; }

    /** @return 高さ（px） */
    public int height() { return height; }

    /**
     * 暫定draw実装。Task 2で完成版に置き換える。
     *
     * @return 空文字列
     */
    @Override
    public String draw() {
        return "";
    }
}
