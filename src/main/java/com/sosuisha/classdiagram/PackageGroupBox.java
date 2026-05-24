package com.sosuisha.classdiagram;

import java.util.Objects;
import java.util.Random;

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
    private final String fillColor;

    /**
     * PackageGroupBoxを生成する（塗りつぶしなし）。
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
        this(label, x, y, width, height, null);
    }

    /**
     * PackageGroupBoxを生成する。
     *
     * @param label     サブパッケージラベル
     * @param x         左上X座標
     * @param y         左上Y座標
     * @param width     幅（px、正数）
     * @param height    高さ（px、正数）
     * @param fillColor 塗りつぶし色（{@code null} で塗りなし）
     * @throws NullPointerException     labelがnullの場合
     * @throws IllegalArgumentException widthまたはheightが0以下の場合
     */
    public PackageGroupBox(String label, int x, int y, int width, int height, String fillColor) {
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
        this.fillColor = fillColor;
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

    /** @return 塗りつぶし色（未設定時は {@code null}） */
    public String fillColor() { return fillColor; }

    private static final int FONT_SIZE = 12;
    private static final int CHAR_WIDTH = FONT_SIZE / 2 + 1;
    private static final int LABEL_PADDING_X = 6;
    private static final int LABEL_PADDING_Y = 4;
    private static final double SKETCH_MAX = 1.5;

    /**
     * PackageGroupBoxのSVG表現を返す。
     *
     * @return SVGのgタグ文字列
     */
    @Override
    public String draw() {
        var rng = new Random(Objects.hash(label, width, height));
        var sb = new StringBuilder();
        sb.append("<g data-diagram-draw=\"package-group\" data-diagram-draw-name=\"%s\" transform=\"translate(%d,%d)\">"
            .formatted(label, x, y));
        if (fillColor != null) {
            sb.append("<rect width=\"%d\" height=\"%d\" fill=\"%s\"/>".formatted(width, height, fillColor));
        }
        // 4 sketchy edges (top, right, bottom, left)
        sb.append(sketchyLine(0, 0, width, 0, rng));
        sb.append(sketchyLine(width, 0, width, height, rng));
        sb.append(sketchyLine(width, height, 0, height, rng));
        sb.append(sketchyLine(0, height, 0, 0, rng));
        // Label background (white rect to "cut" the top edge under the text)
        int labelWidth = label.length() * CHAR_WIDTH + LABEL_PADDING_X * 2;
        sb.append("<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"white\"/>"
            .formatted(LABEL_PADDING_X, -FONT_SIZE / 2, labelWidth, FONT_SIZE + LABEL_PADDING_Y));
        int textY = LABEL_PADDING_Y + FONT_SIZE * 4 / 5 - FONT_SIZE / 2;
        sb.append("<text x=\"%d\" y=\"%d\" font-size=\"%d\">%s</text>"
            .formatted(LABEL_PADDING_X * 2, textY, FONT_SIZE, label));
        sb.append("</g>");
        return sb.toString();
    }

    private static String sketchyLine(int x1, int y1, int x2, int y2, Random rng) {
        double wobble = rng.nextDouble() * SKETCH_MAX * 2 - SKETCH_MAX;
        int mx = (x1 + x2) / 2;
        int my = (y1 + y2) / 2;
        double cp1x = (x1 + mx) / 2.0;
        double cp1y = (y1 + my) / 2.0;
        double cp2x = (mx + x2) / 2.0;
        double cp2y = (my + y2) / 2.0;
        if (Math.abs(x2 - x1) >= Math.abs(y2 - y1)) {
            cp1y += wobble;
            cp2y -= wobble;
        } else {
            cp1x += wobble;
            cp2x -= wobble;
        }
        return "<path d=\"M %d,%d Q %.1f,%.1f %d,%d Q %.1f,%.1f %d,%d\" fill=\"none\" stroke=\"black\"/>"
            .formatted(x1, y1, cp1x, cp1y, mx, my, cp2x, cp2y, x2, y2);
    }
}
