package com.sosuisha.classdiagram;

/**
 * UMLクラスのステレオタイプを表す列挙型。
 */
public enum ClassStereotype {

    /** ステレオタイプなし（通常クラス）。 */
    NONE(""),

    /** インタフェース（{@code «interface»}）。 */
    INTERFACE("«interface»");

    private final String label;

    ClassStereotype(String label) {
        this.label = label;
    }

    /**
     * このステレオタイプのSVG描画用ラベル文字列を返す。
     *
     * @return ラベル文字列（NONEの場合は空文字列）
     */
    public String label() {
        return label;
    }
}
