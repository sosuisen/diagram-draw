package com.sosuisha.classdiagram;

import com.sosuisha.classdiagram.analyzer.ConnectedComponentSplitter;
import com.sosuisha.classdiagram.analyzer.ClassRelationScanner;
import com.sosuisha.classdiagram.analyzer.ClassRelationSorter;
import java.nio.file.Path;
import java.util.Objects;

/**
 * クラス図SVGを生成するファサード。
 *
 * <p>ClassRelationScanner → ConnectedComponentSplitter → ClassRelationSorter
 * → ClassDiagramLayout → SVGBuilder のパイプラインを一括実行し、SVG文字列を返す。
 */
public class ClassDiagramGenerator {

    private final int horizontalGap;
    private final int verticalGap;
    private final int canvasPaddingX;
    private final int canvasPaddingY;
    private final int groupGap;
    private String fontFamily = null;

    /**
     * ClassDiagramGeneratorを生成する。
     *
     * @param horizontalGap  同一レイヤー内のボックス間水平隙間（px）
     * @param verticalGap    レイヤー間の垂直隙間（px）
     * @param canvasPaddingX キャンバス左右の余白（px）
     * @param canvasPaddingY キャンバス上下の余白（px）
     * @param groupGap       連結成分グループ間の水平隙間（px）
     */
    public ClassDiagramGenerator(int horizontalGap, int verticalGap,
                                  int canvasPaddingX, int canvasPaddingY, int groupGap) {
        this.horizontalGap = horizontalGap;
        this.verticalGap = verticalGap;
        this.canvasPaddingX = canvasPaddingX;
        this.canvasPaddingY = canvasPaddingY;
        this.groupGap = groupGap;
    }

    /**
     * テキストに適用するフォントファミリーを設定する。
     *
     * @param fontFamily フォントファミリー名（例: "HackGen"）
     * @return このジェネレーター自身（メソッドチェーン用）
     * @throws NullPointerException fontFamilyがnullの場合
     */
    public ClassDiagramGenerator fontFamily(String fontFamily) {
        Objects.requireNonNull(fontFamily, "fontFamily must not be null");
        this.fontFamily = fontFamily;
        return this;
    }

    /**
     * 指定パッケージのクラス図SVGを生成して返す。
     *
     * @param classRoot   コンパイル済みクラスのルートディレクトリ
     * @param packageName 分析対象パッケージ名
     * @return SVG文字列
     * @throws NullPointerException classRootまたはpackageNameがnullの場合
     * @throws com.sosuisha.classdiagram.analyzer.CircularRelationException 循環参照が検出された場合
     */
    public String generate(Path classRoot, String packageName) {
        Objects.requireNonNull(classRoot, "classRoot must not be null");
        Objects.requireNonNull(packageName, "packageName must not be null");

        var relations = new ClassRelationScanner().scan(classRoot, packageName);
        if (relations.isEmpty()) {
            var builder = new SVGBuilder(canvasPaddingX * 2, canvasPaddingY * 2);
            if (fontFamily != null) builder.fontFamily(fontFamily);
            return builder.build();
        }

        new ConnectedComponentSplitter().split(relations);
        var layers = new ClassRelationSorter().sort(relations);
        var result = new ClassDiagramLayout(horizontalGap, verticalGap, canvasPaddingX, canvasPaddingY, groupGap)
                         .layout(layers, relations);
        var builder = new SVGBuilder(result.canvasWidth(), result.canvasHeight());
        if (fontFamily != null) builder.fontFamily(fontFamily);
        result.boxes().forEach(builder::add);
        result.dependencies().forEach(builder::add);
        return builder.build();
    }
}
