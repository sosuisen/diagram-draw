package com.sosuisha.classdiagram;

import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * UMLクラスボックスを表すオブジェクト。
 *
 * <p>名前・フィールド・メソッドの3コンパートメントを持つ。
 * 内容（ステレオタイプ・名前・フィールド・メソッド）は構築後イミュータブル。
 * 描画位置は {@link #setPosition(int, int)} で設定する。
 * 幅・高さはコンテンツから自動計算される。
 * 輪郭線・区切り線はコンテンツのハッシュを種としたゆらぎを持つ。
 * デフォルトではクラス名のみを描画し、{@link #showDetails()} で詳細表示に切り替える。
 */
public final class ClassBox implements SvgElement {

    private static final int FONT_SIZE  = 14;
    private static final int ASCENT     = FONT_SIZE * 4 / 5;
    private static final int LINE_GAP   = 4;
    private static final int PADDING_X  = 8;
    private static final int PADDING_Y  = 4;
    private static final int NAME_ONLY_PADDING_Y = 6;
    private static final int CHAR_WIDTH = FONT_SIZE / 2 + 1;
    private static final int MIN_WIDTH  = 100;
    private static final double SKETCH_MAX_NONE = 1.0;
    private static final double SKETCH_MAX_SOME = 1.5;
    private static final double SKETCH_MAX_FULL = 2.0;

    private final ClassStereotype stereotype;
    private final String name;
    private final List<String> fields;
    private final List<String> methods;
    private int x = 0;
    private int y = 0;
    private String fillColor = null;
    private boolean showDetails = false;
    private boolean picturesque = false;

    /**
     * フィールドとメソッドを指定せずにClassBoxを生成する（stereotype = NONE）。
     *
     * @param name クラス名
     * @throws NullPointerException nameがnullの場合
     */
    public ClassBox(String name) {
        this(name, ClassStereotype.NONE, List.of(), List.of());
    }

    /**
     * ステレオタイプを指定してClassBoxを生成する（フィールド・メソッドなし）。
     *
     * @param name       クラス名
     * @param stereotype ステレオタイプ
     * @throws NullPointerException nameまたはstereotypeがnullの場合
     */
    public ClassBox(String name, ClassStereotype stereotype) {
        this(name, stereotype, List.of(), List.of());
    }

    /**
     * フィールドとメソッドを指定してClassBoxを生成する（stereotype = NONE）。
     *
     * @param name クラス名
     * @param fields フィールド一覧
     * @param methods メソッド一覧
     * @throws NullPointerException name、fields、またはmethodsがnullの場合
     */
    public ClassBox(String name, List<String> fields, List<String> methods) {
        this(name, ClassStereotype.NONE, fields, methods);
    }

    /**
     * ClassBoxを生成する。
     *
     * @param name       クラス名
     * @param stereotype ステレオタイプ
     * @param fields     フィールド一覧
     * @param methods    メソッド一覧
     * @throws NullPointerException name、stereotype、fields、またはmethodsがnullの場合
     */
    public ClassBox(String name, ClassStereotype stereotype, List<String> fields, List<String> methods) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(stereotype, "stereotype must not be null");
        Objects.requireNonNull(fields, "fields must not be null");
        Objects.requireNonNull(methods, "methods must not be null");
        this.name = name;
        this.stereotype = stereotype;
        this.fields = List.copyOf(fields);
        this.methods = List.copyOf(methods);
    }

    /** @return ステレオタイプ */
    public ClassStereotype stereotype() { return stereotype; }

    /** @return クラス名 */
    public String name() { return name; }

    /** @return フィールド一覧（イミュータブル） */
    public List<String> fields() { return fields; }

    /** @return メソッド一覧（イミュータブル） */
    public List<String> methods() { return methods; }

    /** @return 描画位置のX座標 */
    public int x() { return x; }

    /** @return 描画位置のY座標 */
    public int y() { return y; }

    /**
     * 描画位置を設定する。
     *
     * @param x X座標
     * @param y Y座標
     */
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /**
     * 矩形内部の塗りつぶし色を設定する。{@code null} で塗りなし（透明）。
     *
     * @param fillColor SVG 互換の色文字列（例: {@code "#FFFFBB"}）または {@code null}
     */
    public void setFillColor(String fillColor) {
        this.fillColor = fillColor;
    }

    /** @return 塗りつぶし色（未設定時は {@code null}） */
    public String fillColor() { return fillColor; }

    /**
     * ステレオタイプ、フィールド、メソッド、および区切り線を描画する詳細表示に切り替える。
     *
     * @return このClassBox自身（メソッドチェーン用）
     */
    public ClassBox showDetails() {
        this.showDetails = true;
        return this;
    }

    /**
     * 装飾的な描画表現を有効または無効にする。
     *
     * @param picturesque 有効にする場合は {@code true}
     * @return このClassBox自身（メソッドチェーン用）
     */
    public ClassBox picturesque(boolean picturesque) {
        this.picturesque = picturesque;
        return this;
    }

    /**
     * コンテンツから自動計算した幅を返す。
     *
     * @return 幅（px）
     */
    public int width() {
        int maxLen = name.length();
        if (stereotype != ClassStereotype.NONE) {
            maxLen = Math.max(maxLen, stereotype.label().length());
        }
        if (showDetails) {
            for (var f : fields) {
                maxLen = Math.max(maxLen, f.length());
            }
            for (var m : methods) {
                maxLen = Math.max(maxLen, m.length());
            }
        }
        return Math.max(MIN_WIDTH, maxLen * CHAR_WIDTH + PADDING_X * 2);
    }

    /**
     * コンテンツから自動計算した高さを返す。
     *
     * @return 高さ（px）
     */
    public int height() {
        int nameLines = stereotype == ClassStereotype.NONE ? 1 : 2;
        int h = showDetails ? compartmentHeight(nameLines) : nameOnlyCompartmentHeight(nameLines);
        if (showDetails) {
            h += compartmentHeight(fields.size())
               + compartmentHeight(methods.size());
        }
        return h;
    }

    /**
     * ClassBoxのSVG表現を返す。
     *
     * @return SVGのgタグ文字列
     */
    @Override
    public String draw() {
        int w = width();
        int h = height();
        var rng = createRandom();
        var boldRng = createRandomForBold();
        double sketchMax = sketchMax();
        var content = new StringBuilder();

        int ch = 0;
        ch += appendNameCompartment(content, w, ch);
        if (showDetails) {
            content.append(sketchyLine(0, ch, w, ch, rng, sketchMax));
            ch += appendTextCompartment(content, fields, w, ch);
            content.append(sketchyLine(0, ch, w, ch, rng, sketchMax));
            appendTextCompartment(content, methods, w, ch);
        }

        var sb = new StringBuilder();
        if (x != 0 || y != 0) {
            sb.append("<g data-diagram-draw=\"box\" data-diagram-draw-type=\"class\" data-diagram-draw-name=\"%s\" transform=\"translate(%d,%d)\">".formatted(name, x, y));
        } else {
            sb.append("<g data-diagram-draw=\"box\" data-diagram-draw-type=\"class\" data-diagram-draw-name=\"%s\">".formatted(name));
        }
        if (fillColor != null) {
            sb.append("<rect width=\"%d\" height=\"%d\" fill=\"%s\"/>".formatted(w, h, fillColor));
        }
        sb.append(outlineLine(0, 0, w, 0, rng, boldRng, sketchMax));
        sb.append(outlineLine(w, 0, w, h, rng, boldRng, sketchMax));
        sb.append(outlineLine(w, h, 0, h, rng, boldRng, sketchMax));
        sb.append(outlineLine(0, h, 0, 0, rng, boldRng, sketchMax));
        sb.append(content);
        sb.append("</g>");
        return sb.toString();
    }

    private double sketchMax() {
        if (!showDetails) {
            return SKETCH_MAX_NONE;
        }
        if (!fields.isEmpty() && !methods.isEmpty()) {
            return SKETCH_MAX_FULL;
        }
        if (!fields.isEmpty() || !methods.isEmpty()) {
            return SKETCH_MAX_SOME;
        }
        return SKETCH_MAX_NONE;
    }

    private Random createRandom() {
        if (!showDetails) {
            return new Random(Objects.hash(name));
        }
        return new Random(Objects.hash(name, stereotype, fields, methods));
    }

    private Random createRandomForBold() {
        return new Random(Objects.hash(name + "_bold"));
    }

    private static String sketchBoldLine(int x1, int y1, int x2, int y2,
                                         Random rng, Random boldRng, double sketchMax) {
        return sketchyLine(x1, y1, x2, y2, rng, sketchMax)
            + sketchyLine(x1 + 1, y1 + 1, x2 + 1, y2 + 1, boldRng, sketchMax);
    }

    private String outlineLine(int x1, int y1, int x2, int y2,
                               Random rng, Random boldRng, double sketchMax) {
        if (picturesque) {
            return sketchBoldLine(x1, y1, x2, y2, rng, boldRng, sketchMax);
        }
        return sketchyLine(x1, y1, x2, y2, rng, sketchMax);
    }

    private static String sketchyLine(int x1, int y1, int x2, int y2, Random rng, double sketchMax) {
        double wobble = rng.nextDouble() * sketchMax * 2 - sketchMax;
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
        return "<path d=\"M %d,%d Q %.1f,%.1f %d,%d Q %.1f,%.1f %d,%d\" fill=\"none\" stroke=\"black\"/>".formatted(
            x1, y1, cp1x, cp1y, mx, my, cp2x, cp2y, x2, y2);
    }

    private static int compartmentHeight(int lineCount) {
        if (lineCount == 0) {
            return PADDING_Y * 2;
        }
        return lineCount * FONT_SIZE + (lineCount - 1) * LINE_GAP + PADDING_Y * 2;
    }

    private static int nameOnlyCompartmentHeight(int lineCount) {
        return lineCount * FONT_SIZE + (lineCount - 1) * LINE_GAP + NAME_ONLY_PADDING_Y * 2;
    }

    private int appendNameCompartment(StringBuilder sb, int width, int startY) {
        if (!showDetails) {
            if (stereotype == ClassStereotype.NONE) {
                int textY = startY + NAME_ONLY_PADDING_Y + ASCENT;
                sb.append("<text x=\"%d\" y=\"%d\" font-size=\"%d\" text-anchor=\"middle\">%s</text>".formatted(
                    width / 2, textY, FONT_SIZE, name));
                return nameOnlyCompartmentHeight(1);
            }
            int stereoY = startY + NAME_ONLY_PADDING_Y + ASCENT;
            int nameY = startY + NAME_ONLY_PADDING_Y + ASCENT + FONT_SIZE + LINE_GAP;
            sb.append("<text x=\"%d\" y=\"%d\" font-size=\"%d\" text-anchor=\"middle\">%s</text>".formatted(
                width / 2, stereoY, FONT_SIZE - 2, stereotype.label()));
            sb.append("<text x=\"%d\" y=\"%d\" font-size=\"%d\" text-anchor=\"middle\">%s</text>".formatted(
                width / 2, nameY, FONT_SIZE, name));
            return nameOnlyCompartmentHeight(2);
        }
        if (stereotype == ClassStereotype.NONE) {
            int textY = startY + PADDING_Y + ASCENT;
            sb.append("<text x=\"%d\" y=\"%d\" font-size=\"%d\" text-anchor=\"middle\">%s</text>".formatted(
                width / 2, textY, FONT_SIZE, name));
            return compartmentHeight(1);
        }
        int stereoY = startY + PADDING_Y + ASCENT;
        int nameY = startY + PADDING_Y + ASCENT + FONT_SIZE + LINE_GAP;
        sb.append("<text x=\"%d\" y=\"%d\" font-size=\"%d\" text-anchor=\"middle\">%s</text>".formatted(
            width / 2, stereoY, FONT_SIZE - 2, stereotype.label()));
        sb.append("<text x=\"%d\" y=\"%d\" font-size=\"%d\" text-anchor=\"middle\">%s</text>".formatted(
            width / 2, nameY, FONT_SIZE, name));
        return compartmentHeight(2);
    }

    private int appendTextCompartment(StringBuilder sb, List<String> lines, int width, int startY) {
        for (int i = 0; i < lines.size(); i++) {
            int baseline = startY + PADDING_Y + ASCENT + i * (FONT_SIZE + LINE_GAP);
            sb.append("<text x=\"%d\" y=\"%d\" font-size=\"%d\">%s</text>".formatted(PADDING_X, baseline, FONT_SIZE, lines.get(i)));
        }
        return compartmentHeight(lines.size());
    }
}
