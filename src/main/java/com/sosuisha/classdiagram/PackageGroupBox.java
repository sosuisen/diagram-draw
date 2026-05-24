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
    private final boolean picturesque;
    private final String strokeColor;

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
        this(label, x, y, width, height, fillColor, false);
    }

    /**
     * PackageGroupBoxを生成する。
     *
     * @param label       サブパッケージラベル
     * @param x           左上X座標
     * @param y           左上Y座標
     * @param width       幅（px、正数）
     * @param height      高さ（px、正数）
     * @param fillColor   塗りつぶし色（{@code null} で塗りなし）
     * @param picturesque 装飾的な影表現を描画する場合は {@code true}
     * @throws NullPointerException     labelがnullの場合
     * @throws IllegalArgumentException widthまたはheightが0以下の場合
     */
    public PackageGroupBox(String label, int x, int y, int width, int height,
                           String fillColor, boolean picturesque) {
        this(label, x, y, width, height, fillColor, picturesque, DEFAULT_STROKE_COLOR);
    }

    /**
     * PackageGroupBoxを生成する。
     *
     * @param label       サブパッケージラベル
     * @param x           左上X座標
     * @param y           左上Y座標
     * @param width       幅（px、正数）
     * @param height      高さ（px、正数）
     * @param fillColor   塗りつぶし色（{@code null} で塗りなし）
     * @param picturesque 装飾的な影表現を描画する場合は {@code true}
     * @param strokeColor 枠線色
     * @throws NullPointerException     labelまたはstrokeColorがnullの場合
     * @throws IllegalArgumentException widthまたはheightが0以下の場合
     */
    public PackageGroupBox(String label, int x, int y, int width, int height,
                           String fillColor, boolean picturesque, String strokeColor) {
        Objects.requireNonNull(label, "label must not be null");
        Objects.requireNonNull(strokeColor, "strokeColor must not be null");
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
        this.picturesque = picturesque;
        this.strokeColor = strokeColor;
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

    /** @return 装飾的な影表現を描画する場合は {@code true} */
    public boolean picturesque() { return picturesque; }

    /** @return 枠線色 */
    public String strokeColor() { return strokeColor; }

    private static final int FONT_SIZE = 12;
    private static final int CHAR_WIDTH = FONT_SIZE / 2 + 1;
    private static final int LABEL_PADDING_X = 6;
    private static final int LABEL_PADDING_Y = 4;
    private static final int TAB_HEIGHT = FONT_SIZE + LABEL_PADDING_Y * 2;
    private static final int SHADOW_LINE_COUNT = 5;
    private static final int SHADOW_START_OFFSET = 2;
    private static final int SHADOW_LINE_GAP = 4;
    private static final double SKETCH_MAX = 1.5;
    private static final String DEFAULT_STROKE_COLOR = "#000000";

    /**
     * PackageGroupBoxのSVG表現を返す。UML 標準のタブ付きフォルダ形状で描画する。
     * バウンディングボックス内の上部 {@code TAB_HEIGHT} 分がタブ領域、その下が本体矩形。
     *
     * @return SVGのgタグ文字列
     */
    @Override
    public String draw() {
        var rng = new Random(Objects.hash(label, width, height));
        int tabWidth = Math.min(label.length() * CHAR_WIDTH + LABEL_PADDING_X * 2, width);
        var sb = new StringBuilder();
        sb.append("<g data-diagram-draw=\"package-group\" data-diagram-draw-name=\"%s\" transform=\"translate(%d,%d)\">"
            .formatted(label, x, y));
        if (fillColor != null) {
            // Single polygon traces the combined tab + main outline so the fill matches the shape.
            sb.append("<polygon points=\"%d,%d %d,%d %d,%d %d,%d %d,%d %d,%d\" fill=\"%s\" stroke=\"none\"/>"
                .formatted(
                    0, 0,
                    tabWidth, 0,
                    tabWidth, TAB_HEIGHT,
                    width, TAB_HEIGHT,
                    width, height,
                    0, height,
                    fillColor));
        }
        // Outline tracing the tabbed shape clockwise. Tab right uses a straight line
        // because the segment is too short for the wobble to look natural.
        sb.append(sketchyLine(0, 0, tabWidth, 0, rng, strokeColor));                     // tab top
        sb.append(straightPath(tabWidth, 0, tabWidth, TAB_HEIGHT, strokeColor));         // tab right (straight)
        sb.append(sketchyLine(tabWidth, TAB_HEIGHT, width, TAB_HEIGHT, rng, strokeColor)); // main top (right of tab)
        sb.append(sketchyLine(width, TAB_HEIGHT, width, height, rng, strokeColor));      // main right
        sb.append(sketchyLine(width, height, 0, height, rng, strokeColor));              // main bottom
        sb.append(sketchyLine(0, height, 0, 0, rng, strokeColor));                       // left (full height)
        if (picturesque) {
            appendCornerShadow(sb);
        }
        // Label inside the tab.
        int textY = LABEL_PADDING_Y + FONT_SIZE * 4 / 5;
        sb.append("<text x=\"%d\" y=\"%d\" font-size=\"%d\">%s</text>"
            .formatted(LABEL_PADDING_X, textY, FONT_SIZE, label));
        sb.append("</g>");
        return sb.toString();
    }

    private static String sketchyLine(int x1, int y1, int x2, int y2, Random rng, String strokeColor) {
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
        return "<path d=\"M %d,%d Q %.1f,%.1f %d,%d Q %.1f,%.1f %d,%d\" fill=\"none\" stroke=\"%s\"/>"
            .formatted(x1, y1, cp1x, cp1y, mx, my, cp2x, cp2y, x2, y2, strokeColor);
    }

    private static String straightPath(int x1, int y1, int x2, int y2, String strokeColor) {
        return "<path d=\"M %d,%d L %d,%d\" fill=\"none\" stroke=\"%s\"/>"
            .formatted(x1, y1, x2, y2, strokeColor);
    }

    private void appendCornerShadow(StringBuilder sb) {
        for (int i = 0; i < SHADOW_LINE_COUNT; i++) {
            int offset = SHADOW_START_OFFSET + i * SHADOW_LINE_GAP;
            int x1 = width - offset;
            int y1 = height;
            int x2 = width;
            int y2 = height - offset;
            double solidRatio = 0.6 + (SHADOW_LINE_COUNT - 1 - i) * 0.1;
            if (solidRatio >= 1.0) {
                sb.append("<path data-diagram-draw=\"package-shadow-solid\" d=\"M %d,%d L %d,%d\" fill=\"none\" stroke=\"%s\"/>"
                    .formatted(x1, y1, x2, y2, strokeColor));
                continue;
            }
            double splitX = x1 + (x2 - x1) * solidRatio;
            double splitY = y1 + (y2 - y1) * solidRatio;
            sb.append("<path data-diagram-draw=\"package-shadow-solid\" d=\"M %d,%d L %.1f,%.1f\" fill=\"none\" stroke=\"%s\"/>"
                .formatted(x1, y1, splitX, splitY, strokeColor));
            if (solidRatio < 1.0) {
                sb.append("<path data-diagram-draw=\"package-shadow-dashed\" d=\"M %.1f,%.1f L %d,%d\" fill=\"none\" stroke=\"%s\" stroke-dasharray=\"2,2\"/>"
                    .formatted(splitX, splitY, x2, y2, strokeColor));
            }
        }
    }
}
