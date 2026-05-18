package com.sosuisha;

/**
 * 矩形（クラスボックス）を表す値オブジェクト。
 */
public record Box(int x, int y, int width, int height) implements SvgElement {

    /**
     * Boxを生成する。
     *
     * @throws IllegalArgumentException widthまたはheightが0以下の場合
     */
    public Box {
        if (width <= 0) {
            throw new IllegalArgumentException("width must be positive: " + width);
        }
        if (height <= 0) {
            throw new IllegalArgumentException("height must be positive: " + height);
        }
    }

    /**
     * BoxのSVG表現として&lt;rect&gt;タグ文字列を返す。
     *
     * @return SVGのrectタグ文字列
     */
    public String draw() {
        return "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"none\" stroke=\"black\"/>".formatted(x, y, width, height);
    }
}
