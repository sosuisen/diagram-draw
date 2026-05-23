package com.sosuisha.classdiagram;

import com.sosuisha.classdiagram.analyzer.ClassInfo;
import com.sosuisha.classdiagram.analyzer.ClassRelation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    private final int horizontalGap;
    private final int verticalGap;
    private final int canvasPaddingX;
    private final int canvasPaddingY;
    private final int groupGap;

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
                boxMap.put(info, new ClassBox(info.simpleName(), info.stereotype()));
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
                dependencies.add(new Dependency(src, tgt, rel.type()));
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
                    dependencies.add(new Dependency(src, tgt, DependencyType.DEPENDENCY));
                }
            }
        }

        return new LayoutResult(
            List.copyOf(boxMap.values()),
            List.copyOf(dependencies),
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
