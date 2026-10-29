package com.sosuisha.classdiagram;

import com.sosuisha.classdiagram.analyzer.ConnectedComponentSplitter;
import com.sosuisha.classdiagram.analyzer.ClassRelationScanner;
import com.sosuisha.classdiagram.analyzer.ClassRelationSorter;
import com.sosuisha.classdiagram.analyzer.ClassInfo;
import com.sosuisha.classdiagram.analyzer.ClassRelation;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
    private String packageStrokeColor = null;
    private String classBoxStrokeColor = null;
    private String edgeColor = null;
    private boolean showDetails = false;
    private boolean picturesque = false;
    private long seed = 0;
    private String intentionDsl = null;
    private Path intentionFilePath = null;

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
     * パッケージ枠線色を設定する。デフォルト: {@code "#000000"}。
     *
     * @param hex SVG 互換色文字列
     * @return このジェネレーター自身（メソッドチェーン用）
     * @throws NullPointerException hexがnullの場合
     */
    public ClassDiagramGenerator packageStrokeColor(String hex) {
        this.packageStrokeColor = Objects.requireNonNull(hex, "hex must not be null");
        return this;
    }

    /**
     * クラスボックス枠線色を設定する。デフォルト: {@code "#000000"}。
     *
     * @param hex SVG 互換色文字列
     * @return このジェネレーター自身（メソッドチェーン用）
     * @throws NullPointerException hexがnullの場合
     */
    public ClassDiagramGenerator classBoxStrokeColor(String hex) {
        this.classBoxStrokeColor = Objects.requireNonNull(hex, "hex must not be null");
        return this;
    }

    /**
     * エッジ色を設定する。デフォルト: {@code "#000000"}。
     *
     * @param hex SVG 互換色文字列
     * @return このジェネレーター自身（メソッドチェーン用）
     * @throws NullPointerException hexがnullの場合
     */
    public ClassDiagramGenerator edgeColor(String hex) {
        this.edgeColor = Objects.requireNonNull(hex, "hex must not be null");
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
     * 輪郭の揺らぎに使う乱数のシードを設定する。
     * シードを変えると、すべてのクラスボックスとパッケージ矩形の揺れ方が変わる。0は既定の揺れ方になる。
     *
     * @param seed 図全体のシード
     * @return このジェネレーター自身（メソッドチェーン用）
     */
    public ClassDiagramGenerator seed(long seed) {
        this.seed = seed;
        return this;
    }

    /**
     * intention DSL文字列を設定する。{@link #generate} 時にレイアウトへ渡される。
     * {@link #intentionFile} と両方設定した場合は {@code intentionFile} が優先される。
     *
     * @param dsl intention DSL文字列
     * @return このジェネレーター自身（メソッドチェーン用）
     * @throws NullPointerException dslがnullの場合
     */
    public ClassDiagramGenerator intention(String dsl) {
        Objects.requireNonNull(dsl, "dsl must not be null");
        this.intentionDsl = dsl;
        return this;
    }

    /**
     * intention DSLファイルのパスを設定する。{@link #generate} 時にレイアウトへ渡される。
     * {@link #intention} より優先される。
     *
     * @param path intention DSLファイルのパス
     * @return このジェネレーター自身（メソッドチェーン用）
     * @throws NullPointerException pathがnullの場合
     */
    public ClassDiagramGenerator intentionFile(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        this.intentionFilePath = path;
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

        var scanResult = new ClassRelationScanner().scanAll(classRoot, packageName);
        if (scanResult.classes().isEmpty()) {
            var builder = new SVGBuilder(canvasPaddingX * 2, canvasPaddingY * 2);
            if (fontFamily != null) builder.fontFamily(fontFamily);
            return builder.build();
        }

        var relations = scanResult.relations();
        new ConnectedComponentSplitter().split(relations);
        var layers = addClassesWithoutRelations(
            new ClassRelationSorter().sort(relations), scanResult.classes(), relations);
        var layoutEngine = new ClassDiagramLayout(horizontalGap, verticalGap, canvasPaddingX, canvasPaddingY, groupGap);
        if (packageGapForGrouping >= 0) {
            layoutEngine.enableSubPackageGrouping(packageName, packageGapForGrouping);
        }
        if (showDetails) layoutEngine.showDetails();
        layoutEngine.picturesque(picturesque);
        layoutEngine.seed(seed);
        if (classFillColor != null) layoutEngine.classFillColor(classFillColor);
        if (interfaceFillColor != null) layoutEngine.interfaceFillColor(interfaceFillColor);
        if (packageFillColor != null) layoutEngine.packageFillColor(packageFillColor);
        if (packageStrokeColor != null) layoutEngine.packageStrokeColor(packageStrokeColor);
        if (classBoxStrokeColor != null) layoutEngine.classBoxStrokeColor(classBoxStrokeColor);
        if (edgeColor != null) layoutEngine.edgeColor(edgeColor);
        if (intentionFilePath != null) {
            layoutEngine.intentionFile(intentionFilePath);
        } else if (intentionDsl != null) {
            layoutEngine.intention(intentionDsl);
        }
        var result = layoutEngine.layout(layers, relations);
        var builder = new SVGBuilder(result.canvasWidth(), result.canvasHeight());
        if (fontFamily != null) builder.fontFamily(fontFamily);
        result.packageGroups().forEach(builder::add);
        result.boxes().forEach(builder::add);
        result.dependencies().forEach(builder::add);
        result.notes().forEach(builder::add);
        return builder.build();
    }

    /**
     * 関係を持たないクラスを1つの追加グループにまとめ、最上位レイヤーに加える。
     */
    private static List<List<ClassInfo>> addClassesWithoutRelations(
            List<List<ClassInfo>> layers, List<ClassInfo> classes, List<ClassRelation> relations) {
        var related = new HashSet<ClassInfo>();
        int nextGroupIndex = 0;
        for (var rel : relations) {
            related.add(rel.sourceClassInfo());
            related.add(rel.targetClassInfo());
            nextGroupIndex = Math.max(nextGroupIndex, rel.sourceClassInfo().groupIndex() + 1);
        }
        var unrelated = classes.stream().filter(c -> !related.contains(c)).toList();
        if (unrelated.isEmpty()) {
            return layers;
        }
        // One shared group, not one group per class. With sub-package grouping,
        // one group per class would draw a separate package frame around every class.
        for (var info : unrelated) {
            info.setGroupIndex(nextGroupIndex);
        }
        var result = new ArrayList<List<ClassInfo>>();
        if (layers.isEmpty()) {
            result.add(unrelated);
        } else {
            var topLayer = new ArrayList<>(layers.getFirst());
            topLayer.addAll(unrelated);
            result.add(topLayer);
            result.addAll(layers.subList(1, layers.size()));
        }
        return result;
    }
}
