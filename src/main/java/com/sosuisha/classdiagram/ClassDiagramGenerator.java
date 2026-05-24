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
    private int packageGapForGrouping = -1; // -1 = disabled
    private String classFillColor = null;
    private String interfaceFillColor = null;
    private String packageFillColor = null;
    private boolean showDetails = false;
    private boolean picturesque = false;

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
     * サブパッケージグルーピングを有効化する。
     *
     * @param packageGap サブパッケージスロット間の垂直隙間（px、0以上）
     * @return このジェネレーター自身（メソッドチェーン用）
     * @throws IllegalArgumentException packageGapが0未満の場合
     */
    public ClassDiagramGenerator enableSubPackageGrouping(int packageGap) {
        if (packageGap < 0) {
            throw new IllegalArgumentException("packageGap must be >= 0: " + packageGap);
        }
        this.packageGapForGrouping = packageGap;
        return this;
    }

    /**
     * クラス（非インタフェース）矩形の塗りつぶし色を設定する。デフォルト: {@code "#FFFFBB"}。
     *
     * @param hex SVG 互換色文字列
     * @return このジェネレーター自身（メソッドチェーン用）
     * @throws NullPointerException hexがnullの場合
     */
    public ClassDiagramGenerator classFillColor(String hex) {
        this.classFillColor = Objects.requireNonNull(hex, "hex must not be null");
        return this;
    }

    /**
     * インタフェース矩形の塗りつぶし色を設定する。デフォルト: {@code "#BDFFDE"}。
     *
     * @param hex SVG 互換色文字列
     * @return このジェネレーター自身（メソッドチェーン用）
     * @throws NullPointerException hexがnullの場合
     */
    public ClassDiagramGenerator interfaceFillColor(String hex) {
        this.interfaceFillColor = Objects.requireNonNull(hex, "hex must not be null");
        return this;
    }

    /**
     * パッケージ矩形の基準塗りつぶし色を設定する。階層が深くなるほど暗化する。
     * デフォルト: {@code "#f0f0f0"}。
     *
     * @param hex SVG 互換色文字列
     * @return このジェネレーター自身（メソッドチェーン用）
     * @throws NullPointerException hexがnullの場合
     */
    public ClassDiagramGenerator packageFillColor(String hex) {
        this.packageFillColor = Objects.requireNonNull(hex, "hex must not be null");
        return this;
    }

    /**
     * クラスボックスを詳細表示にする。ステレオタイプ、フィールド、メソッド、および区切り線を描画する。
     *
     * @return このジェネレーター自身（メソッドチェーン用）
     */
    public ClassDiagramGenerator showDetails() {
        this.showDetails = true;
        return this;
    }

    /**
     * 装飾的な描画表現を有効または無効にする。
     *
     * @param picturesque 有効にする場合は {@code true}
     * @return このジェネレーター自身（メソッドチェーン用）
     */
    public ClassDiagramGenerator picturesque(boolean picturesque) {
        this.picturesque = picturesque;
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
        var layoutEngine = new ClassDiagramLayout(horizontalGap, verticalGap, canvasPaddingX, canvasPaddingY, groupGap);
        if (packageGapForGrouping >= 0) {
            layoutEngine.enableSubPackageGrouping(packageName, packageGapForGrouping);
        }
        if (showDetails) layoutEngine.showDetails();
        layoutEngine.picturesque(picturesque);
        if (classFillColor != null) layoutEngine.classFillColor(classFillColor);
        if (interfaceFillColor != null) layoutEngine.interfaceFillColor(interfaceFillColor);
        if (packageFillColor != null) layoutEngine.packageFillColor(packageFillColor);
        var result = layoutEngine.layout(layers, relations);
        var builder = new SVGBuilder(result.canvasWidth(), result.canvasHeight());
        if (fontFamily != null) builder.fontFamily(fontFamily);
        result.packageGroups().forEach(builder::add);
        result.boxes().forEach(builder::add);
        result.dependencies().forEach(builder::add);
        return builder.build();
    }
}
