package com.sosuisha.classdiagram;

import com.sosuisha.classdiagram.analyzer.ClassRelationScanner;
import com.sosuisha.classdiagram.analyzer.ClassRelationSorter;
import java.nio.file.Path;
import java.util.Objects;

/**
 * クラス図SVGを生成するファサード。
 *
 * <p>ClassRelationScanner → ClassRelationSorter → ClassDiagramLayout → SVGBuilder の
 * パイプラインを一括実行し、SVG文字列を返す。
 */
public class ClassDiagramGenerator {

    private final int horizontalGap;
    private final int verticalGap;
    private final int canvasPaddingX;
    private final int canvasPaddingY;

    /**
     * ClassDiagramGeneratorを生成する。
     *
     * @param horizontalGap  同一レイヤー内のボックス間水平隙間（px）
     * @param verticalGap    レイヤー間の垂直隙間（px）
     * @param canvasPaddingX キャンバス左右の余白（px）
     * @param canvasPaddingY キャンバス上下の余白（px）
     */
    public ClassDiagramGenerator(int horizontalGap, int verticalGap,
                                  int canvasPaddingX, int canvasPaddingY) {
        this.horizontalGap = horizontalGap;
        this.verticalGap = verticalGap;
        this.canvasPaddingX = canvasPaddingX;
        this.canvasPaddingY = canvasPaddingY;
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
            return new SVGBuilder(canvasPaddingX * 2, canvasPaddingY * 2).build();
        }

        var layers = new ClassRelationSorter().sort(relations);
        var result = new ClassDiagramLayout(horizontalGap, verticalGap, canvasPaddingX, canvasPaddingY)
                         .layout(layers, relations);
        var builder = new SVGBuilder(result.canvasWidth(), result.canvasHeight());
        result.boxes().forEach(builder::add);
        result.dependencies().forEach(builder::add);
        return builder.build();
    }
}
