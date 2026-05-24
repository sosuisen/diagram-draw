package com.sosuisha.classdiagram;

import java.util.List;
import java.util.Objects;

/**
 * レイアウト計算の結果を格納するイミュータブルなスナップショット。
 *
 * @param boxes         配置済みClassBoxのリスト（変更不可コピー）
 * @param dependencies  Dependencyのリスト（変更不可コピー）
 * @param packageGroups PackageGroupBoxのリスト（変更不可コピー、無効時は空）
 * @param canvasWidth   キャンバスの幅（px）
 * @param canvasHeight  キャンバスの高さ（px）
 * @throws NullPointerException boxes、dependencies、またはpackageGroupsがnullの場合
 */
public record LayoutResult(
    List<ClassBox> boxes,
    List<Dependency> dependencies,
    List<PackageGroupBox> packageGroups,
    int canvasWidth,
    int canvasHeight
) {
    public LayoutResult {
        Objects.requireNonNull(boxes, "boxes must not be null");
        Objects.requireNonNull(dependencies, "dependencies must not be null");
        Objects.requireNonNull(packageGroups, "packageGroups must not be null");
        boxes = List.copyOf(boxes);
        dependencies = List.copyOf(dependencies);
        packageGroups = List.copyOf(packageGroups);
    }

    /**
     * 後方互換用コンストラクタ。{@code packageGroups} は空リスト扱い。
     *
     * @param boxes        配置済みClassBoxのリスト
     * @param dependencies Dependencyのリスト
     * @param canvasWidth  キャンバスの幅（px）
     * @param canvasHeight キャンバスの高さ（px）
     * @throws NullPointerException boxesまたはdependenciesがnullの場合
     */
    public LayoutResult(List<ClassBox> boxes, List<Dependency> dependencies,
                        int canvasWidth, int canvasHeight) {
        this(boxes, dependencies, List.of(), canvasWidth, canvasHeight);
    }
}
