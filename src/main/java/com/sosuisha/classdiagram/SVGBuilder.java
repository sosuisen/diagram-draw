package com.sosuisha.classdiagram;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * SVGドキュメントを構築するビルダー。
 */
public class SVGBuilder {

    private final int width;
    private final int height;
    private final List<SvgElement> elements = new ArrayList<>();
    private String fontFamily = null;

    /**
     * SVGBuilderを生成する。
     *
     * @param width SVGの幅
     * @param height SVGの高さ
     * @throws IllegalArgumentException widthまたはheightが0以下の場合
     */
    public SVGBuilder(int width, int height) {
        if (width <= 0) {
            throw new IllegalArgumentException("width must be positive: " + width);
        }
        if (height <= 0) {
            throw new IllegalArgumentException("height must be positive: " + height);
        }
        this.width = width;
        this.height = height;
    }

    /**
     * テキストに適用するフォントファミリーを設定する。
     *
     * @param fontFamily フォントファミリー名（例: "HackGen"）
     * @return このビルダー自身（メソッドチェーン用）
     * @throws NullPointerException fontFamilyがnullの場合
     */
    public SVGBuilder fontFamily(String fontFamily) {
        Objects.requireNonNull(fontFamily, "fontFamily must not be null");
        this.fontFamily = fontFamily;
        return this;
    }

    /**
     * SVG要素を追加する。
     *
     * @param element 追加するSVG要素
     * @return このビルダー自身（メソッドチェーン用）
     * @throws NullPointerException elementがnullの場合
     */
    public SVGBuilder add(SvgElement element) {
        Objects.requireNonNull(element, "element must not be null");
        elements.add(element);
        return this;
    }

    /**
     * SVGドキュメント文字列を返す。
     *
     * @return SVGドキュメント文字列
     */
    public String build() {
        var sb = new StringBuilder();
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\">".formatted(width, height));
        if (fontFamily != null) {
            sb.append("<style>text { font-family: '%s'; }</style>".formatted(fontFamily));
        }
        sb.append("<rect data-diagram-draw=\"background\" width=\"%d\" height=\"%d\" fill=\"white\"/>".formatted(width, height));
        for (var element : elements) {
            sb.append(element.draw());
        }
        sb.append("</svg>");
        return sb.toString();
    }
}
