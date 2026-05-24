package com.sosuisha.classdiagram;

import com.sosuisha.classdiagram.analyzer.ClassInfo;
import com.sosuisha.classdiagram.analyzer.ClassRelation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * クラス関係情報からレイアウト位置を計算するエンジン。
 *
 * <p>ClassRelationSorterのKahn法レイヤーを使用し、groupIndex が同じノードを
 * 1つのグループとして垂直スタックにまとめ、グループ間は groupGap ピクセルで
 * 水平方向に並べる。各レイヤーはグループ幅基準で中央揃えされる。
 */
public class ClassDiagramLayout {

    private static final int MAX_CROSSING_PASSES = 12;

    private static final int GROUP_PADDING_LEFT = 15;
    private static final int GROUP_PADDING_RIGHT = 15;
    private static final int GROUP_PADDING_TOP = 25;
    private static final int GROUP_PADDING_BOTTOM = 10;

    private final int horizontalGap;
    private final int verticalGap;
    private final int canvasPaddingX;
    private final int canvasPaddingY;
    private final int groupGap;
    private String rootPackageForGrouping = null;
    private int packageGap = 0;
    private String classFillColor = "#FFFFBB";
    private String interfaceFillColor = "#BDFFDE";
    private String packageFillColor = "#f0f0f0";
    private String packageStrokeColor = "#000000";
    private String classBoxStrokeColor = "#000000";
    private String edgeColor = "#000000";
    private boolean showDetails = false;
    private boolean picturesque = false;
    private static final double PACKAGE_DEPTH_DARKEN_FACTOR = 0.9;

    /**
     * ClassDiagramLayoutを生成する。
     *
     * @param horizontalGap  同一レイヤー内のボックス間水平隙間（px）
     * @param verticalGap    レイヤー間の垂直隙間（px）
     * @param canvasPaddingX キャンバス左右の余白（px）
     * @param canvasPaddingY キャンバス上下の余白（px）
     * @param groupGap       連結成分グループ間の水平隙間（px）
     */
    public ClassDiagramLayout(int horizontalGap, int verticalGap,
                               int canvasPaddingX, int canvasPaddingY, int groupGap) {
        this.horizontalGap = horizontalGap;
        this.verticalGap = verticalGap;
        this.canvasPaddingX = canvasPaddingX;
        this.canvasPaddingY = canvasPaddingY;
        this.groupGap = groupGap;
    }

    /**
     * サブパッケージグルーピングを有効化する。設定すると {@link #layout} が
     * 各ConnectedComponent内を {@code (groupIndex, packageName)} ごとの垂直スロットに分割し、
     * 非ルートスロットを {@link PackageGroupBox} で囲む。ルートパッケージのクラスは上端に配置（矩形なし）。
     * 未呼出時は既存レイアウトと同一出力。
     *
     * @param rootPackage スキャン対象パッケージ名（相対サブパッケージラベル算出に使用）
     * @param packageGap  サブパッケージスロット間の垂直隙間（px、0以上）
     * @return このレイアウト自身（メソッドチェーン用）
     * @throws NullPointerException     rootPackageがnullの場合
     * @throws IllegalArgumentException packageGapが0未満の場合
     */
    public ClassDiagramLayout enableSubPackageGrouping(String rootPackage, int packageGap) {
        Objects.requireNonNull(rootPackage, "rootPackage must not be null");
        if (packageGap < 0) {
            throw new IllegalArgumentException("packageGap must be >= 0: " + packageGap);
        }
        this.rootPackageForGrouping = rootPackage;
        this.packageGap = packageGap;
        return this;
    }

    /**
     * クラス（非インタフェース）矩形の塗りつぶし色を設定する。
     *
     * @param hex SVG 互換色文字列（例: {@code "#FFFFBB"}）
     * @return このレイアウト自身（メソッドチェーン用）
     * @throws NullPointerException hexがnullの場合
     */
    public ClassDiagramLayout classFillColor(String hex) {
        this.classFillColor = Objects.requireNonNull(hex, "hex must not be null");
        return this;
    }

    /**
     * インタフェース矩形の塗りつぶし色を設定する。
     *
     * @param hex SVG 互換色文字列（例: {@code "#BDFFDE"}）
     * @return このレイアウト自身（メソッドチェーン用）
     * @throws NullPointerException hexがnullの場合
     */
    public ClassDiagramLayout interfaceFillColor(String hex) {
        this.interfaceFillColor = Objects.requireNonNull(hex, "hex must not be null");
        return this;
    }

    /**
     * パッケージ矩形の基準塗りつぶし色を設定する。実際の塗り色は階層が深くなるほど暗くなる
     * （階層 1 では基準色、以降は階層数に応じて係数 0.9 を累乗して暗化）。
     *
     * @param hex SVG 互換色文字列（例: {@code "#f0f0f0"}）
     * @return このレイアウト自身（メソッドチェーン用）
     * @throws NullPointerException hexがnullの場合
     */
    public ClassDiagramLayout packageFillColor(String hex) {
        this.packageFillColor = Objects.requireNonNull(hex, "hex must not be null");
        return this;
    }

    /**
     * パッケージ枠線色を設定する。デフォルト: {@code "#000000"}。
     *
     * @param hex SVG 互換色文字列
     * @return このレイアウト自身（メソッドチェーン用）
     * @throws NullPointerException hexがnullの場合
     */
    public ClassDiagramLayout packageStrokeColor(String hex) {
        this.packageStrokeColor = Objects.requireNonNull(hex, "hex must not be null");
        return this;
    }

    /**
     * クラスボックス枠線色を設定する。デフォルト: {@code "#000000"}。
     *
     * @param hex SVG 互換色文字列
     * @return このレイアウト自身（メソッドチェーン用）
     * @throws NullPointerException hexがnullの場合
     */
    public ClassDiagramLayout classBoxStrokeColor(String hex) {
        this.classBoxStrokeColor = Objects.requireNonNull(hex, "hex must not be null");
        return this;
    }

    /**
     * エッジ色を設定する。デフォルト: {@code "#000000"}。
     *
     * @param hex SVG 互換色文字列
     * @return このレイアウト自身（メソッドチェーン用）
     * @throws NullPointerException hexがnullの場合
     */
    public ClassDiagramLayout edgeColor(String hex) {
        this.edgeColor = Objects.requireNonNull(hex, "hex must not be null");
        return this;
    }

    /**
     * クラスボックスを詳細表示にする。ステレオタイプ、フィールド、メソッド、および区切り線を描画する。
     *
     * @return このレイアウト自身（メソッドチェーン用）
     */
    public ClassDiagramLayout showDetails() {
        this.showDetails = true;
        return this;
    }

    /**
     * 装飾的な描画表現を有効または無効にする。
     *
     * @param picturesque 有効にする場合は {@code true}
     * @return このレイアウト自身（メソッドチェーン用）
     */
    public ClassDiagramLayout picturesque(boolean picturesque) {
        this.picturesque = picturesque;
        return this;
    }

    /**
     * 16進カラー文字列を階層深度に応じて暗化する。深度 1 は基準色をそのまま返す。
     */
    private static String darkenForDepth(String hex, int depth) {
        int r = Integer.parseInt(hex.substring(1, 3), 16);
        int g = Integer.parseInt(hex.substring(3, 5), 16);
        int b = Integer.parseInt(hex.substring(5, 7), 16);
        double factor = Math.pow(PACKAGE_DEPTH_DARKEN_FACTOR, Math.max(0, depth - 1));
        int rr = Math.max(0, Math.min(255, (int) Math.round(r * factor)));
        int gg = Math.max(0, Math.min(255, (int) Math.round(g * factor)));
        int bb = Math.max(0, Math.min(255, (int) Math.round(b * factor)));
        return String.format("#%02x%02x%02x", rr, gg, bb);
    }

    /**
     * レイヤーと関係リストからレイアウト結果を計算する。
     *
     * @param layers    ClassRelationSorterが出力したレイヤーリスト
     * @param relations ClassRelationのリスト
     * @return レイアウト計算結果
     * @throws NullPointerException layersまたはrelationsがnullの場合
     */
    public LayoutResult layout(List<List<ClassInfo>> layers, List<ClassRelation> relations) {
        Objects.requireNonNull(layers, "layers must not be null");
        Objects.requireNonNull(relations, "relations must not be null");

        if (layers.isEmpty()) {
            return new LayoutResult(List.of(), List.of(), canvasPaddingX * 2, canvasPaddingY * 2);
        }

        // Step 1: ClassInfo → ClassBox マップ作成（挿入順保持）
        Map<ClassInfo, ClassBox> boxMap = new LinkedHashMap<>();
        for (var layer : layers) {
            for (var info : layer) {
                var box = new ClassBox(info.simpleName(), info.stereotype());
                if (showDetails) {
                    box.showDetails();
                }
                box.picturesque(picturesque);
                box.setStrokeColor(classBoxStrokeColor);
                box.setFillColor(info.stereotype() == ClassStereotype.INTERFACE
                    ? interfaceFillColor : classFillColor);
                boxMap.put(info, box);
            }
        }

        // 辺交差の最小化（重心法）
        var orderedLayers = minimizeCrossings(layers, relations);

        // Step 2: groupIndex ごとにサブレイヤーを構築（空レイヤーは除去）
        var groupIndices = orderedLayers.stream()
            .flatMap(List::stream)
            .mapToInt(ClassInfo::groupIndex)
            .distinct()
            .sorted()
            .toArray();
        int numGroups = groupIndices.length;

        var groupSubLayers = new ArrayList<List<List<ClassInfo>>>(numGroups);
        for (int gi : groupIndices) {
            var subLayers = new ArrayList<List<ClassInfo>>();
            for (var layer : orderedLayers) {
                var filtered = layer.stream()
                    .filter(info -> info.groupIndex() == gi)
                    .collect(Collectors.toCollection(ArrayList::new));
                if (!filtered.isEmpty()) subLayers.add(filtered);
            }
            groupSubLayers.add(subLayers);
        }

        // Step 3: グループごとのコンテンツ幅・レイヤー幅・最大ボックス高さを計算
        int[] groupContentWidth = new int[numGroups];
        var groupLayerWidths = new ArrayList<int[]>(numGroups);
        var groupMaxBoxHeights = new ArrayList<int[]>(numGroups);

        for (int g = 0; g < numGroups; g++) {
            var subLayers = groupSubLayers.get(g);
            int numSub = subLayers.size();
            int[] layerWidths = new int[numSub];
            int[] maxBoxHeights = new int[numSub];

            for (int i = 0; i < numSub; i++) {
                var sub = subLayers.get(i);
                int w = 0, maxH = 0;
                for (var info : sub) {
                    var box = boxMap.get(info);
                    w += box.width();
                    maxH = Math.max(maxH, box.height());
                }
                if (sub.size() > 1) w += (sub.size() - 1) * horizontalGap;
                layerWidths[i] = w;
                maxBoxHeights[i] = maxH;
            }

            int contentW = 0;
            for (int w : layerWidths) contentW = Math.max(contentW, w);
            groupContentWidth[g] = contentW;
            groupLayerWidths.add(layerWidths);
            groupMaxBoxHeights.add(maxBoxHeights);
        }

        // Step 4: グループ開始X座標を決定
        int[] groupStartX = new int[numGroups];
        groupStartX[0] = canvasPaddingX;
        for (int g = 1; g < numGroups; g++) {
            groupStartX[g] = groupStartX[g - 1] + groupContentWidth[g - 1] + groupGap;
        }

        // Step 5: 各ボックスに座標を設定（グループ幅基準で中央揃え、上揃え）
        for (int g = 0; g < numGroups; g++) {
            var subLayers = groupSubLayers.get(g);
            int[] layerWidths = groupLayerWidths.get(g);
            int[] maxBoxHeights = groupMaxBoxHeights.get(g);
            int currentY = canvasPaddingY;

            for (int i = 0; i < subLayers.size(); i++) {
                var sub = subLayers.get(i);
                int startX = groupStartX[g] + (groupContentWidth[g] - layerWidths[i]) / 2;
                int x = startX;
                for (var info : sub) {
                    var box = boxMap.get(info);
                    box.setPosition(x, currentY);
                    x += box.width() + horizontalGap;
                }
                currentY += maxBoxHeights[i] + verticalGap;
            }
        }

        // Step 6: キャンバスサイズ計算
        int canvasWidth = groupStartX[numGroups - 1] + groupContentWidth[numGroups - 1] + canvasPaddingX;
        int maxGroupHeight = 0;
        for (int g = 0; g < numGroups; g++) {
            int[] maxBoxHeights = groupMaxBoxHeights.get(g);
            int h = 0;
            for (int mh : maxBoxHeights) h += mh;
            if (maxBoxHeights.length > 1) h += (maxBoxHeights.length - 1) * verticalGap;
            maxGroupHeight = Math.max(maxGroupHeight, h);
        }
        int canvasHeight = maxGroupHeight + 2 * canvasPaddingY;

        // Step 7: Dependency 生成
        var dependencies = new ArrayList<Dependency>();
        for (var rel : relations) {
            var src = boxMap.get(rel.sourceClassInfo());
            var tgt = boxMap.get(rel.targetClassInfo());
            if (src != null && tgt != null) {
                dependencies.add(new Dependency(src, tgt, rel.type()).edgeColor(edgeColor));
            }
        }

        // Step 8: cross-group DEPENDENCY 矢印生成
        Map<String, ClassInfo> fqnToInfo = new HashMap<>();
        for (var info : boxMap.keySet()) {
            fqnToInfo.put(info.packageName() + "." + info.simpleName(), info);
        }
        var implToIfaceInfos = buildImplToIfaceInfosMap(relations, fqnToInfo);
        for (var srcInfo : boxMap.keySet()) {
            for (var fqn : srcInfo.dependencyTargetFqns()) {
                var tgtInfo = fqnToInfo.get(fqn);
                if (tgtInfo == null) continue;
                if (srcInfo.groupIndex() == tgtInfo.groupIndex()) continue;
                if (isCoveredByInterface(fqn, srcInfo, implToIfaceInfos)) continue;
                var src = boxMap.get(srcInfo);
                var tgt = boxMap.get(tgtInfo);
                if (src != null && tgt != null) {
                    dependencies.add(new Dependency(src, tgt, DependencyType.DEPENDENCY).edgeColor(edgeColor));
                }
            }
        }

        // Step 9: optional sub-package grouping — rebuild positions per (groupIndex, packageName) slot.
        var packageGroups = new ArrayList<PackageGroupBox>();
        if (rootPackageForGrouping != null) {
            packageGroups.addAll(applySubPackageGrouping(orderedLayers, relations, boxMap));
            // Recompute canvas size after re-positioning.
            int maxRight = 0;
            int maxBottom = 0;
            for (var box : boxMap.values()) {
                maxRight = Math.max(maxRight, box.x() + box.width());
                maxBottom = Math.max(maxBottom, box.y() + box.height());
            }
            for (var pg : packageGroups) {
                maxRight = Math.max(maxRight, pg.x() + pg.width());
                maxBottom = Math.max(maxBottom, pg.y() + pg.height());
            }
            canvasWidth = maxRight + canvasPaddingX;
            canvasHeight = maxBottom + canvasPaddingY;
        }

        // Step 10: spread endpoints of edges sharing the same (box, edge) slot so that
        // multiple incident edges don't bunch up at a single point.
        spreadDependencyEndpoints(dependencies);

        return new LayoutResult(
            List.copyOf(boxMap.values()),
            List.copyOf(dependencies),
            List.copyOf(packageGroups),
            canvasWidth,
            canvasHeight
        );
    }

    private List<List<ClassInfo>> minimizeCrossings(
            List<List<ClassInfo>> layers, List<ClassRelation> relations) {
        if (layers.size() <= 1) {
            var copy = new ArrayList<List<ClassInfo>>();
            for (var layer : layers) copy.add(new ArrayList<>(layer));
            return copy;
        }

        Map<ClassInfo, List<ClassInfo>> upNeighbors = new HashMap<>();
        Map<ClassInfo, List<ClassInfo>> downNeighbors = new HashMap<>();
        for (var rel : relations) {
            if (rel.type() == DependencyType.REALIZATION) {
                upNeighbors.computeIfAbsent(rel.sourceClassInfo(), k -> new ArrayList<>())
                       .add(rel.targetClassInfo());
                downNeighbors.computeIfAbsent(rel.targetClassInfo(), k -> new ArrayList<>())
                        .add(rel.sourceClassInfo());
            } else if (rel.type() == DependencyType.COMPOSITION
                    || rel.type() == DependencyType.AGGREGATION) {
                upNeighbors.computeIfAbsent(rel.targetClassInfo(), k -> new ArrayList<>())
                       .add(rel.sourceClassInfo());
                downNeighbors.computeIfAbsent(rel.sourceClassInfo(), k -> new ArrayList<>())
                        .add(rel.targetClassInfo());
            }
        }

        Map<ClassInfo, ClassInfo> ifaceOfImpl = new HashMap<>();
        for (var rel : relations) {
            if (rel.type() == DependencyType.REALIZATION) {
                ifaceOfImpl.put(rel.sourceClassInfo(), rel.targetClassInfo());
            }
        }

        var result = new ArrayList<List<ClassInfo>>();
        for (var layer : layers) {
            result.add(new ArrayList<>(layer));
        }

        for (int pass = 0; pass < MAX_CROSSING_PASSES; pass++) {
            boolean changed = false;
            if (pass % 2 == 0) {
                for (int i = 0; i + 1 < result.size(); i++) {
                    changed |= sortLayerByBarycenter(result.get(i + 1), result.get(i), upNeighbors, ifaceOfImpl);
                }
            } else {
                for (int i = result.size() - 2; i >= 0; i--) {
                    changed |= sortLayerByBarycenter(result.get(i), result.get(i + 1), downNeighbors, ifaceOfImpl);
                }
            }
            if (!changed) break;
        }

        return result;
    }

    private boolean sortLayerByBarycenter(
            List<ClassInfo> layer, List<ClassInfo> referenceLayer,
            Map<ClassInfo, List<ClassInfo>> adj,
            Map<ClassInfo, ClassInfo> ifaceOfImpl) {
        Map<ClassInfo, Integer> pos = new HashMap<>();
        for (int i = 0; i < referenceLayer.size(); i++) {
            pos.put(referenceLayer.get(i), i);
        }

        Map<ClassInfo, Double> bary = new HashMap<>();
        for (int i = 0; i < layer.size(); i++) {
            var node = layer.get(i);
            var neighbors = adj.getOrDefault(node, List.of());
            double sum = 0;
            int count = 0;
            for (var neighbor : neighbors) {
                var p = pos.get(neighbor);
                if (p != null) {
                    sum += p;
                    count++;
                }
            }
            bary.put(node, count > 0 ? sum / count : (double) i);
        }

        // Group nodes by their group key: ifaceOfImpl(node) or the node itself.
        // Co-implementors of the same interface share a group key -> become a contiguous block.
        Map<ClassInfo, List<ClassInfo>> groups = new LinkedHashMap<>();
        for (var node : layer) {
            var key = ifaceOfImpl.getOrDefault(node, node);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(node);
        }

        // Group barycenter = mean of individual barycenters in the group.
        Map<ClassInfo, Double> groupBary = new HashMap<>();
        for (var entry : groups.entrySet()) {
            double sum = 0;
            for (var n : entry.getValue()) sum += bary.get(n);
            groupBary.put(entry.getKey(), sum / entry.getValue().size());
        }

        // Stable sort groups by group barycenter; within each group, stable sort by individual barycenter.
        var sortedGroups = new ArrayList<>(groups.entrySet());
        sortedGroups.sort((e1, e2) -> Double.compare(
            groupBary.get(e1.getKey()), groupBary.get(e2.getKey())));

        var sorted = new ArrayList<ClassInfo>();
        for (var entry : sortedGroups) {
            var members = new ArrayList<>(entry.getValue());
            members.sort(Comparator.comparingDouble(bary::get));
            sorted.addAll(members);
        }

        if (!sorted.equals(layer)) {
            layer.clear();
            layer.addAll(sorted);
            return true;
        }
        return false;
    }

    private List<PackageGroupBox> applySubPackageGrouping(
            List<List<ClassInfo>> orderedLayers,
            List<ClassRelation> relations,
            Map<ClassInfo, ClassBox> boxMap) {

        // Build originalLayerIndex per ClassInfo (independent of groupIndex).
        Map<ClassInfo, Integer> originalLayerIndex = new HashMap<>();
        for (int i = 0; i < orderedLayers.size(); i++) {
            for (var info : orderedLayers.get(i)) {
                originalLayerIndex.put(info, i);
            }
        }

        // Group classes by groupIndex (preserve insertion order).
        Map<Integer, List<ClassInfo>> byGroup = new LinkedHashMap<>();
        for (var layer : orderedLayers) {
            for (var info : layer) {
                byGroup.computeIfAbsent(info.groupIndex(), k -> new ArrayList<>()).add(info);
            }
        }

        var result = new ArrayList<PackageGroupBox>();
        int currentGroupX = canvasPaddingX;
        // Clamp slot startY so any non-root PackageGroupBox top edge is never negative.
        int slotStartY = Math.max(canvasPaddingY, GROUP_PADDING_TOP);

        for (var groupEntry : byGroup.entrySet()) {
            var ccRoot = buildPackageTree(groupEntry.getValue());
            var dims = layoutPackageNode(ccRoot, currentGroupX, slotStartY, 0,
                originalLayerIndex, boxMap, result, relations);
            currentGroupX += dims.width() + groupGap;
        }

        // Render order: outer rectangles must be drawn first so deeper (nested) ones
        // sit on top. layoutPackageNode appends innermost-first (post-order recursion);
        // reverse the list so the outermost ends up first in SVG output.
        Collections.reverse(result);
        return result;
    }

    /**
     * 1パッケージを表すツリーノード。ルートノードは {@code localLabel == ""} で矩形なし。
     */
    private static final class PackageNode {
        final String localLabel;
        final List<ClassInfo> directClasses = new ArrayList<>();
        final Map<String, PackageNode> children = new LinkedHashMap<>();

        PackageNode(String localLabel) {
            this.localLabel = localLabel;
        }
    }

    /**
     * ConnectedComponent のクラス群からパッケージツリーを構築する。
     * 直接クラスを持たない中間パッケージもノードとして含める。
     */
    private PackageNode buildPackageTree(List<ClassInfo> members) {
        var root = new PackageNode("");
        for (var info : members) {
            var pkg = info.packageName();
            if (pkg.equals(rootPackageForGrouping)
                    || !pkg.startsWith(rootPackageForGrouping + ".")) {
                // Root-package class, or defensive fallback for classes outside the scanned root.
                root.directClasses.add(info);
                continue;
            }
            var relative = pkg.substring(rootPackageForGrouping.length() + 1);
            var current = root;
            for (var part : relative.split("\\.")) {
                current = current.children.computeIfAbsent(part, PackageNode::new);
            }
            current.directClasses.add(info);
        }
        return root;
    }

    /**
     * ノードを再帰的に配置。直接クラスは上部、子ノード矩形は下部に 2D スカイライン詰め込みで配置する。
     * 非ルートノードは {@link PackageGroupBox} に囲まれる。
     *
     * <p>子ノードはまず一時座標 (0, 0) で再帰的に配置してサイズを取得し、その後スカイラインで決定した
     * 位置に部分木全体（クラスボックスとパッケージ矩形）をシフトする。挿入順は重心ソートで決定され、
     * 関連の深い子同士が空間的に近接配置されるよう寄与する。
     *
     * @param x     ノード自身の矩形（あれば）の左上 X 座標
     * @param y     ノード自身の矩形（あれば）の左上 Y 座標
     * @param depth このノードの矩形のネスト階層（0 = CC ルート、1 = 最外パッケージ矩形、以降深くなるほど増加）
     * @return ノード全体（矩形を含む）の幅・高さ
     */
    private SlotDimensions layoutPackageNode(
            PackageNode node,
            int x, int y, int depth,
            Map<ClassInfo, Integer> originalLayerIndex,
            Map<ClassInfo, ClassBox> boxMap,
            List<PackageGroupBox> packageGroups,
            List<ClassRelation> relations) {

        boolean hasRect = !node.localLabel.isEmpty();
        int contentX = x + (hasRect ? GROUP_PADDING_LEFT : 0);
        int contentY = y + (hasRect ? GROUP_PADDING_TOP : 0);

        int directWidth = 0;
        int directHeight = 0;
        if (!node.directClasses.isEmpty()) {
            var dims = layoutSingleSlot(
                node.directClasses, originalLayerIndex, boxMap, contentX, contentY);
            directWidth = dims.width();
            directHeight = dims.height();
        }

        int childrenStartY = contentY + directHeight + (directHeight > 0 ? packageGap : 0);
        int childrenWidth = 0;
        int childrenHeight = 0;

        var orderedChildren = orderChildrenByBarycenter(node, relations);
        if (!orderedChildren.isEmpty()) {
            var placements = new ArrayList<ChildPlacement>();
            for (var child : orderedChildren) {
                int pgStartIdx = packageGroups.size();
                var descendants = new ArrayList<ClassInfo>();
                collectAllDescendants(child, descendants);
                var dims = layoutPackageNode(child, 0, 0, depth + 1,
                    originalLayerIndex, boxMap, packageGroups, relations);
                placements.add(new ChildPlacement(
                    dims.width(), dims.height(),
                    pgStartIdx, packageGroups.size(), descendants));
            }

            long totalArea = 0;
            int maxChildWidth = 0;
            for (var cp : placements) {
                totalArea += (long) cp.width() * cp.height();
                maxChildWidth = Math.max(maxChildWidth, cp.width());
            }
            int targetMaxWidth = Math.max(maxChildWidth,
                (int) Math.ceil(Math.sqrt((double) totalArea * 1.5)));

            var skyline = new Skyline();
            for (var cp : placements) {
                int[] pos = skyline.findFit(cp.width() + packageGap, targetMaxWidth + packageGap);
                shiftChildSubtree(cp,
                    contentX + pos[0], childrenStartY + pos[1],
                    boxMap, packageGroups);
                skyline.place(pos[0], pos[1], cp.width() + packageGap, cp.height() + packageGap);
                childrenWidth = Math.max(childrenWidth, pos[0] + cp.width());
                childrenHeight = Math.max(childrenHeight, pos[1] + cp.height());
            }
        }

        int innerMaxWidth = Math.max(directWidth, childrenWidth);
        int innerHeight = directHeight
            + ((directHeight > 0 && childrenHeight > 0) ? packageGap : 0)
            + childrenHeight;

        int totalWidth = innerMaxWidth + (hasRect ? GROUP_PADDING_LEFT + GROUP_PADDING_RIGHT : 0);
        int totalHeight = innerHeight + (hasRect ? GROUP_PADDING_TOP + GROUP_PADDING_BOTTOM : 0);

        if (hasRect) {
            packageGroups.add(new PackageGroupBox(
                node.localLabel, x, y, totalWidth, totalHeight,
                darkenForDepth(packageFillColor, depth), picturesque, packageStrokeColor));
        }

        return new SlotDimensions(totalWidth, totalHeight);
    }

    private record ChildPlacement(
        int width,
        int height,
        int packageGroupStartIdx,
        int packageGroupEndIdx,
        List<ClassInfo> descendants
    ) {}

    private void collectAllDescendants(PackageNode node, List<ClassInfo> out) {
        out.addAll(node.directClasses);
        for (var c : node.children.values()) {
            collectAllDescendants(c, out);
        }
    }

    private void shiftChildSubtree(
            ChildPlacement cp, int dx, int dy,
            Map<ClassInfo, ClassBox> boxMap,
            List<PackageGroupBox> packageGroups) {
        for (var info : cp.descendants()) {
            var box = boxMap.get(info);
            box.setPosition(box.x() + dx, box.y() + dy);
        }
        for (int i = cp.packageGroupStartIdx(); i < cp.packageGroupEndIdx(); i++) {
            var old = packageGroups.get(i);
            packageGroups.set(i, new PackageGroupBox(
                old.label(), old.x() + dx, old.y() + dy,
                old.width(), old.height(), old.fillColor(), old.picturesque(), old.strokeColor()));
        }
    }

    /**
     * 2D スカイラインを表す可変オブジェクト。{@code (x, height)} のノットでスカイラインを表現し、
     * 各ノットの高さは次のノットの x まで持続する。
     */
    private static final class Skyline {
        private final List<int[]> knots = new ArrayList<>();

        Skyline() {
            knots.add(new int[]{0, 0});
        }

        /**
         * 幅 {@code width} の矩形を置ける位置のうち、最も低い y を返す。
         * 同じ y なら最も左を選ぶ（Bottom-Left fill）。
         *
         * @return {x, y}（見つからない場合は最大高さに積み上げる）
         */
        int[] findFit(int width, int maxWidth) {
            int bestX = -1;
            int bestY = Integer.MAX_VALUE;
            for (int i = 0; i < knots.size(); i++) {
                int x = knots.get(i)[0];
                if (x + width > maxWidth) continue;
                int maxH = knots.get(i)[1];
                for (int j = i + 1; j < knots.size(); j++) {
                    int kx = knots.get(j)[0];
                    if (kx >= x + width) break;
                    maxH = Math.max(maxH, knots.get(j)[1]);
                }
                if (maxH < bestY) {
                    bestY = maxH;
                    bestX = x;
                }
            }
            if (bestX < 0) {
                int top = 0;
                for (var k : knots) top = Math.max(top, k[1]);
                return new int[]{0, top};
            }
            return new int[]{bestX, bestY};
        }

        /**
         * {@code [x, x+width)} の範囲を高さ {@code y+height} で占有したとマークする。
         */
        void place(int x, int y, int width, int height) {
            int endX = x + width;
            int newTop = y + height;
            int heightAtEndX = heightAt(endX);
            knots.removeIf(k -> k[0] >= x && k[0] < endX);
            insertKnot(x, newTop);
            if (!hasKnotAt(endX)) {
                insertKnot(endX, heightAtEndX);
            }
        }

        private int heightAt(int x) {
            int h = 0;
            for (var k : knots) {
                if (k[0] > x) break;
                h = k[1];
            }
            return h;
        }

        private boolean hasKnotAt(int x) {
            for (var k : knots) {
                if (k[0] == x) return true;
            }
            return false;
        }

        private void insertKnot(int x, int height) {
            int i = 0;
            while (i < knots.size() && knots.get(i)[0] < x) i++;
            knots.add(i, new int[]{x, height});
        }
    }

    /**
     * 子ノードの順序を単一パス重心法で決定。子サブツリー内のクラスを位置として扱い、
     * クロスサブツリー relation で繋がる兄弟側のインデックス平均で並べ替える。
     */
    private List<PackageNode> orderChildrenByBarycenter(
            PackageNode parent, List<ClassRelation> relations) {
        var children = new ArrayList<>(parent.children.values());
        if (children.size() <= 1) return children;
        children.sort(Comparator.comparing(c -> c.localLabel));

        Map<ClassInfo, Integer> classToChildIdx = new HashMap<>();
        for (int i = 0; i < children.size(); i++) {
            collectDescendantClasses(children.get(i), classToChildIdx, i);
        }

        Map<Integer, Double> bary = new HashMap<>();
        for (int i = 0; i < children.size(); i++) {
            double sum = 0;
            int count = 0;
            for (var rel : relations) {
                var srcIdx = classToChildIdx.get(rel.sourceClassInfo());
                var tgtIdx = classToChildIdx.get(rel.targetClassInfo());
                if (srcIdx == null || tgtIdx == null) continue;
                if (srcIdx == i && tgtIdx != i) {
                    sum += tgtIdx;
                    count++;
                } else if (tgtIdx == i && srcIdx != i) {
                    sum += srcIdx;
                    count++;
                }
            }
            bary.put(i, count > 0 ? sum / count : (double) i);
        }

        var indices = new ArrayList<Integer>();
        for (int i = 0; i < children.size(); i++) indices.add(i);
        indices.sort((a, b) -> {
            int cmp = Double.compare(bary.get(a), bary.get(b));
            return cmp != 0 ? cmp : Integer.compare(a, b);
        });
        var sorted = new ArrayList<PackageNode>(children.size());
        for (var i : indices) sorted.add(children.get(i));
        return sorted;
    }

    private void collectDescendantClasses(
            PackageNode node, Map<ClassInfo, Integer> map, int idx) {
        for (var c : node.directClasses) map.put(c, idx);
        for (var child : node.children.values()) {
            collectDescendantClasses(child, map, idx);
        }
    }

    private record SlotDimensions(int width, int height) {}

    private static final double EDGE_SPREAD_MARGIN_MAX = 20.0;
    private static final double EDGE_SPREAD_MARGIN_RATIO = 0.15;

    private record AnchorInfo(
        Dependency dep, boolean isSource, ClassBox box,
        Dependency.BoxEdge edge, double naturalPos
    ) {}

    private record EdgeKey(ClassBox box, Dependency.BoxEdge edge) {}

    /**
     * 同じ (box, 辺) を共有する複数エッジの端点を、辺の自然交差順を保ったまま等間隔に分散する。
     * 1 本だけのエッジは現状の自然位置のまま。
     */
    private void spreadDependencyEndpoints(List<Dependency> dependencies) {
        Map<EdgeKey, List<AnchorInfo>> groups = new LinkedHashMap<>();
        for (var dep : dependencies) {
            var src = dep.source();
            var tgt = dep.target();
            double scx = src.x() + src.width() / 2.0;
            double scy = src.y() + src.height() / 2.0;
            double tcx = tgt.x() + tgt.width() / 2.0;
            double tcy = tgt.y() + tgt.height() / 2.0;
            double dx = tcx - scx;
            double dy = tcy - scy;
            double len = Math.sqrt(dx * dx + dy * dy);
            if (len < 0.001) continue;
            double nx = dx / len;
            double ny = dy / len;
            double[] sp = Dependency.edgeIntersection(src, nx, ny);
            double[] tp = Dependency.edgeIntersection(tgt, -nx, -ny);
            var srcEdge = Dependency.whichEdge(src, sp[0], sp[1]);
            var tgtEdge = Dependency.whichEdge(tgt, tp[0], tp[1]);
            double srcNatural = (srcEdge == Dependency.BoxEdge.TOP || srcEdge == Dependency.BoxEdge.BOTTOM) ? sp[0] : sp[1];
            double tgtNatural = (tgtEdge == Dependency.BoxEdge.TOP || tgtEdge == Dependency.BoxEdge.BOTTOM) ? tp[0] : tp[1];
            groups.computeIfAbsent(new EdgeKey(src, srcEdge), k -> new ArrayList<>())
                .add(new AnchorInfo(dep, true, src, srcEdge, srcNatural));
            groups.computeIfAbsent(new EdgeKey(tgt, tgtEdge), k -> new ArrayList<>())
                .add(new AnchorInfo(dep, false, tgt, tgtEdge, tgtNatural));
        }

        for (var entry : groups.entrySet()) {
            var list = entry.getValue();
            if (list.size() <= 1) continue;
            list.sort(Comparator.comparingDouble(AnchorInfo::naturalPos));
            var box = entry.getKey().box();
            var edge = entry.getKey().edge();
            boolean horizontal = (edge == Dependency.BoxEdge.TOP || edge == Dependency.BoxEdge.BOTTOM);
            double rangeStart = horizontal ? box.x() : box.y();
            double rangeLen = horizontal ? box.width() : box.height();
            double margin = Math.min(EDGE_SPREAD_MARGIN_MAX, rangeLen * EDGE_SPREAD_MARGIN_RATIO);
            double available = rangeLen - 2 * margin;
            int n = list.size();
            for (int i = 0; i < n; i++) {
                double t = (double) (i + 1) / (n + 1);
                double posOnEdge = rangeStart + margin + t * available;
                double x;
                double y;
                double dirX;
                double dirY;
                switch (edge) {
                    case TOP -> { x = posOnEdge; y = box.y(); dirX = 0; dirY = -1; }
                    case BOTTOM -> { x = posOnEdge; y = box.y() + box.height(); dirX = 0; dirY = 1; }
                    case LEFT -> { x = box.x(); y = posOnEdge; dirX = -1; dirY = 0; }
                    case RIGHT -> { x = box.x() + box.width(); y = posOnEdge; dirX = 1; dirY = 0; }
                    default -> { continue; }
                }
                var info = list.get(i);
                if (info.isSource()) {
                    info.dep().setSourceAnchor(x, y, dirX, dirY);
                } else {
                    info.dep().setTargetAnchor(x, y, dirX, dirY);
                }
            }
        }
    }

    /**
     * 1スロットを縦配置し、配置後のスロット幅・高さを返す。
     *
     * @return スロットの幅・高さを保持する {@link SlotDimensions}
     */
    private SlotDimensions layoutSingleSlot(
            List<ClassInfo> members,
            Map<ClassInfo, Integer> originalLayerIndex,
            Map<ClassInfo, ClassBox> boxMap,
            int startX, int startY) {
        // Group by original layer index, sorted ascending.
        var byLayer = new TreeMap<Integer, List<ClassInfo>>();
        for (var info : members) {
            byLayer.computeIfAbsent(originalLayerIndex.get(info), k -> new ArrayList<>()).add(info);
        }

        // Compute each row width and the max width (= slot width).
        var rowWidths = new ArrayList<Integer>();
        var rowMaxHeights = new ArrayList<Integer>();
        int slotWidth = 0;
        for (var rowMembers : byLayer.values()) {
            int w = 0;
            int h = 0;
            for (var info : rowMembers) {
                var b = boxMap.get(info);
                w += b.width();
                h = Math.max(h, b.height());
            }
            if (rowMembers.size() > 1) w += (rowMembers.size() - 1) * horizontalGap;
            rowWidths.add(w);
            rowMaxHeights.add(h);
            slotWidth = Math.max(slotWidth, w);
        }

        // Place each row centered within the slot width.
        int currentY = startY;
        int rowIdx = 0;
        for (var rowMembers : byLayer.values()) {
            int rowStartX = startX + (slotWidth - rowWidths.get(rowIdx)) / 2;
            int x = rowStartX;
            for (var info : rowMembers) {
                var b = boxMap.get(info);
                b.setPosition(x, currentY);
                x += b.width() + horizontalGap;
            }
            currentY += rowMaxHeights.get(rowIdx) + verticalGap;
            rowIdx++;
        }

        int slotHeight = (currentY - startY) - (byLayer.isEmpty() ? 0 : verticalGap);
        return new SlotDimensions(slotWidth, slotHeight);
    }

    private static Map<ClassInfo, Set<ClassInfo>> buildImplToIfaceInfosMap(
            List<ClassRelation> relations, Map<String, ClassInfo> fqnToInfo) {
        var map = new HashMap<ClassInfo, Set<ClassInfo>>();
        for (var rel : relations) {
            if (rel.type() != DependencyType.REALIZATION) {
                continue;
            }
            var ifaceFqn = rel.targetClassInfo().packageName() + "." + rel.targetClassInfo().simpleName();
            var ifaceInfo = fqnToInfo.get(ifaceFqn);
            if (ifaceInfo == null) {
                continue;
            }
            map.computeIfAbsent(rel.sourceClassInfo(), k -> new HashSet<>()).add(ifaceInfo);
        }
        return map;
    }

    private static boolean isCoveredByInterface(String targetFqn, ClassInfo srcInfo,
            Map<ClassInfo, Set<ClassInfo>> implToIfaceInfos) {
        var ifaceInfos = implToIfaceInfos.get(srcInfo);
        if (ifaceInfos == null) {
            return false;
        }
        return ifaceInfos.stream().anyMatch(iface -> iface.dependencyTargetFqns().contains(targetFqn));
    }
}
