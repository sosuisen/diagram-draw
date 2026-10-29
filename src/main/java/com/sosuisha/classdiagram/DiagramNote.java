package com.sosuisha.classdiagram;

import java.util.Objects;

/**
 * 図の下に書く1行の注記。図から省いた情報を読み手に伝えるために使う。
 */
public final class DiagramNote implements SvgElement {

    private static final int FONT_SIZE = 12;
    private static final int CHAR_WIDTH = FONT_SIZE / 2 + 1;

    private final String text;
    private final int x;
    private final int y;

    /**
     * DiagramNoteを生成する。
     *
     * @param text 注記の文
     * @param x    文の左端のX座標
     * @param y    文のベースラインのY座標
     * @throws NullPointerException textがnullの場合
     */
    public DiagramNote(String text, int x, int y) {
        this.text = Objects.requireNonNull(text, "text must not be null");
        this.x = x;
        this.y = y;
    }

    /** @return 注記の文 */
    public String text() { return text; }

    /** @return 文の左端のX座標 */
    public int x() { return x; }

    /** @return 文のベースラインのY座標 */
    public int y() { return y; }

    /** @return 文の幅の概算（px） */
    public int width() { return text.length() * CHAR_WIDTH; }

    /**
     * 注記のSVG表現を返す。
     *
     * @return SVGのtextタグ文字列
     */
    @Override
    public String draw() {
        return "<text data-diagram-draw=\"note\" x=\"%d\" y=\"%d\" font-size=\"%d\">%s</text>"
            .formatted(x, y, FONT_SIZE, text);
    }
}
