package com.sosuisha;

import java.util.List;
import java.util.Objects;

/**
 * UMLクラスボックスを表すオブジェクト。
 *
 * <p>名前・フィールド・メソッドの3コンパートメントを持つ。
 * 内容（名前・フィールド・メソッド）は構築後イミュータブル。
 * 描画位置は {@link #setPosition(int, int)} で設定する。
 * 幅・高さはコンテンツから自動計算される。
 */
public final class ClassBox implements SvgElement {

    private static final int FONT_SIZE = 14;
    private static final int ASCENT    = FONT_SIZE * 4 / 5;
    private static final int LINE_GAP  = 4;
    private static final int PADDING_X = 8;
    private static final int PADDING_Y = 4;
    private static final int CHAR_WIDTH = FONT_SIZE / 2 + 1;
    private static final int MIN_WIDTH = 100;

    private final String name;
    private final List<String> fields;
    private final List<String> methods;
    private int x = 0;
    private int y = 0;

    /**
     * フィールドとメソッドを指定せずにClassBoxを生成する。
     *
     * @param name クラス名
     * @throws NullPointerException nameがnullの場合
     */
    public ClassBox(String name) {
        this(name, List.of(), List.of());
    }

    /**
     * ClassBoxを生成する。
     *
     * @param name クラス名
     * @param fields フィールド一覧
     * @param methods メソッド一覧
     * @throws NullPointerException name、fields、またはmethodsがnullの場合
     */
    public ClassBox(String name, List<String> fields, List<String> methods) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(fields, "fields must not be null");
        Objects.requireNonNull(methods, "methods must not be null");
        this.name = name;
        this.fields = List.copyOf(fields);
        this.methods = List.copyOf(methods);
    }

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
     * コンテンツから自動計算した幅を返す。
     *
     * @return 幅（px）
     */
    public int width() {
        int maxLen = name.length();
        for (var f : fields) {
            maxLen = Math.max(maxLen, f.length());
        }
        for (var m : methods) {
            maxLen = Math.max(maxLen, m.length());
        }
        return Math.max(MIN_WIDTH, maxLen * CHAR_WIDTH + PADDING_X * 2);
    }

    /**
     * コンテンツから自動計算した高さを返す。
     *
     * @return 高さ（px）
     */
    public int height() {
        return compartmentHeight(1)
             + compartmentHeight(fields.size())
             + compartmentHeight(methods.size());
    }

    /**
     * ClassBoxのSVG表現を返す。
     *
     * @return SVGのgタグ文字列
     */
    @Override
    public String draw() {
        int w = width();
        var content = new StringBuilder();

        int h = 0;
        h += appendNameCompartment(content, w, h);
        appendDivider(content, w, h);
        h += appendTextCompartment(content, fields, w, h);
        appendDivider(content, w, h);
        h += appendTextCompartment(content, methods, w, h);

        var sb = new StringBuilder();
        if (x != 0 || y != 0) {
            sb.append("<g data-diagram-draw=\"box\" data-diagram-draw-type=\"class\" data-diagram-draw-name=\"%s\" transform=\"translate(%d,%d)\">".formatted(name, x, y));
        } else {
            sb.append("<g data-diagram-draw=\"box\" data-diagram-draw-type=\"class\" data-diagram-draw-name=\"%s\">".formatted(name));
        }
        sb.append("<rect x=\"0\" y=\"0\" width=\"%d\" height=\"%d\" fill=\"none\" stroke=\"black\"/>".formatted(w, h));
        sb.append(content);
        sb.append("</g>");
        return sb.toString();
    }

    private static int compartmentHeight(int lineCount) {
        if (lineCount == 0) {
            return PADDING_Y * 2;
        }
        return lineCount * FONT_SIZE + (lineCount - 1) * LINE_GAP + PADDING_Y * 2;
    }

    private int appendNameCompartment(StringBuilder sb, int width, int startY) {
        int textY = startY + PADDING_Y + ASCENT;
        sb.append("<text x=\"%d\" y=\"%d\" font-size=\"%d\" text-anchor=\"middle\">%s</text>".formatted(width / 2, textY, FONT_SIZE, name));
        return compartmentHeight(1);
    }

    private static void appendDivider(StringBuilder sb, int width, int y) {
        sb.append("<line x1=\"0\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"black\"/>".formatted(y, width, y));
    }

    private int appendTextCompartment(StringBuilder sb, List<String> lines, int width, int startY) {
        for (int i = 0; i < lines.size(); i++) {
            int baseline = startY + PADDING_Y + ASCENT + i * (FONT_SIZE + LINE_GAP);
            sb.append("<text x=\"%d\" y=\"%d\" font-size=\"%d\">%s</text>".formatted(PADDING_X, baseline, FONT_SIZE, lines.get(i)));
        }
        return compartmentHeight(lines.size());
    }
}
