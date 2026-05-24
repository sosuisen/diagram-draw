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
        // Initialize with GROUP_PADDING_LEFT offset so the first non-root box border lands at canvasPaddingX.
        int currentGroupX = canvasPaddingX + GROUP_PADDING_LEFT;
        // Clamp slot startY so the PackageGroupBox top edge is never negative.
        int slotStartY = Math.max(canvasPaddingY, GROUP_PADDING_TOP);

        for (var groupEntry : byGroup.entrySet()) {
            var members = groupEntry.getValue();

            // Partition members by slot key ("" for root, relative pkg name otherwise).
            Map<String, List<ClassInfo>> slotMembers = new LinkedHashMap<>();
            for (var info : members) {
                slotMembers.computeIfAbsent(slotKeyFor(info), k -> new ArrayList<>()).add(info);
            }

            var slotOrder = orderSlotsByBarycenter(slotMembers, relations);

            // First pass: initial placement (all slots left-aligned at currentGroupX).
            var slotPlacements = new ArrayList<SlotPlacement>();
            int contentY = slotStartY;
            for (var key : slotOrder) {
                var slot = slotMembers.get(key);
                var dims = layoutSingleSlot(slot, originalLayerIndex, boxMap, currentGroupX, contentY);
                int boxIdx = -1;
                if (!key.isEmpty()) {
                    result.add(new PackageGroupBox(
                        key,
                        currentGroupX - GROUP_PADDING_LEFT,
                        contentY - GROUP_PADDING_TOP,
                        dims.width() + GROUP_PADDING_LEFT + GROUP_PADDING_RIGHT,
                        dims.height() + GROUP_PADDING_TOP + GROUP_PADDING_BOTTOM
                    ));
                    boxIdx = result.size() - 1;
                }
                slotPlacements.add(new SlotPlacement(key, currentGroupX, dims.width(), slot, boxIdx));
                int slotBottomEdge = contentY + dims.height() + (key.isEmpty() ? 0 : GROUP_PADDING_BOTTOM);
                // Next slot is always non-root (root only ever appears as the first slot),
                // so unconditionally reserve GROUP_PADDING_TOP for the next rectangle's label area.
                contentY = slotBottomEdge + packageGap + GROUP_PADDING_TOP;
            }

            // Second pass: shift slots horizontally so connected slots align in X (reduces arrow crossings).
            var shifts = computeHorizontalShifts(slotPlacements, slotMembers, relations);
            applySlotShifts(slotPlacements, shifts, boxMap, result);

            int ccMaxRight = currentGroupX;
            for (var sp : slotPlacements) {
                int finalX = sp.initialX() + shifts.get(sp.key());
                int rightEdge = finalX + sp.width() + (sp.key().isEmpty() ? 0 : GROUP_PADDING_RIGHT);
                ccMaxRight = Math.max(ccMaxRight, rightEdge);
            }
            currentGroupX = ccMaxRight + groupGap;
        }

        return result;
    }

    /**
     * スロット順序を決定: ルートを先頭（上端）固定、残りを単一パス重心法で並べる。
     *
     * <p>初期インデックスはアルファベット順。各非ルートスロットの重心 = そのスロットメンバーが
     * source または target に含まれる relation のうち、相手側クラスが別スロットに属するものについて、
     * 相手側スロットの初期インデックスを平均した値。relation が 0 件のスロットは初期インデックスを
     * そのまま重心とする。単一パスのため発散しない。
     */
    private List<String> orderSlotsByBarycenter(
            Map<String, List<ClassInfo>> slotMembers,
            List<ClassRelation> relations) {

        // Per-class → slot key lookup.
        Map<ClassInfo, String> classToSlot = new HashMap<>();
        for (var entry : slotMembers.entrySet()) {
            for (var info : entry.getValue()) {
                classToSlot.put(info, entry.getKey());
            }
        }

        boolean hasRoot = slotMembers.containsKey("");
        var nonRoot = slotMembers.keySet().stream()
            .filter(k -> !k.isEmpty())
            .sorted()
            .collect(Collectors.toCollection(ArrayList::new));

        // Initial index map: root = 0 (if present), then non-root in alphabetical order.
        Map<String, Integer> idx = new HashMap<>();
        int next = 0;
        if (hasRoot) idx.put("", next++);
        for (var k : nonRoot) idx.put(k, next++);

        // Single-pass barycenter.
        Map<String, Double> bary = new HashMap<>();
        for (var key : nonRoot) {
            double sum = 0;
            int count = 0;
            for (var rel : relations) {
                var srcSlot = classToSlot.get(rel.sourceClassInfo());
                var tgtSlot = classToSlot.get(rel.targetClassInfo());
                if (key.equals(srcSlot) && tgtSlot != null && !key.equals(tgtSlot)) {
                    sum += idx.get(tgtSlot);
                    count++;
                } else if (key.equals(tgtSlot) && srcSlot != null && !key.equals(srcSlot)) {
                    sum += idx.get(srcSlot);
                    count++;
                }
            }
            bary.put(key, count > 0 ? sum / count : (double) idx.get(key));
        }

        // Stable sort non-root by barycenter; ties preserve alphabetical (initial) order.
        nonRoot.sort((a, b) -> {
            int cmp = Double.compare(bary.get(a), bary.get(b));
            if (cmp != 0) return cmp;
            return Integer.compare(idx.get(a), idx.get(b));
        });

        var result = new ArrayList<String>();
        if (hasRoot) result.add("");
        result.addAll(nonRoot);
        return result;
    }

    private record SlotDimensions(int width, int height) {}

    private record SlotPlacement(
        String key,
        int initialX,
        int width,
        List<ClassInfo> members,
        int boxIndex
    ) {}

    /**
     * 各スロットの水平シフト量を単一パス重心法で算出する。
     *
     * <p>各スロットの目標中心 X = クロススロット relation で繋がる相手側スロットの初期中心 X の平均。
     * シフト = max(0, 目標中心 - 現在中心)。非負クランプにより右ドリフトと発散を防止する。
     */
    private Map<String, Integer> computeHorizontalShifts(
            List<SlotPlacement> slotPlacements,
            Map<String, List<ClassInfo>> slotMembers,
            List<ClassRelation> relations) {

        Map<ClassInfo, String> classToSlot = new HashMap<>();
        for (var entry : slotMembers.entrySet()) {
            for (var info : entry.getValue()) {
                classToSlot.put(info, entry.getKey());
            }
        }

        Map<String, Double> centers = new HashMap<>();
        for (var sp : slotPlacements) {
            centers.put(sp.key(), sp.initialX() + sp.width() / 2.0);
        }

        Map<String, Integer> shifts = new HashMap<>();
        for (var sp : slotPlacements) {
            var key = sp.key();
            double sum = 0;
            int count = 0;
            for (var rel : relations) {
                var srcSlot = classToSlot.get(rel.sourceClassInfo());
                var tgtSlot = classToSlot.get(rel.targetClassInfo());
                if (key.equals(srcSlot) && tgtSlot != null && !key.equals(tgtSlot)) {
                    sum += centers.get(tgtSlot);
                    count++;
                } else if (key.equals(tgtSlot) && srcSlot != null && !key.equals(srcSlot)) {
                    sum += centers.get(srcSlot);
                    count++;
                }
            }
            if (count > 0) {
                int shift = (int) Math.round(sum / count - centers.get(key));
                shifts.put(key, Math.max(0, shift));
            } else {
                shifts.put(key, 0);
            }
        }
        return shifts;
    }

    private void applySlotShifts(
            List<SlotPlacement> slotPlacements,
            Map<String, Integer> shifts,
            Map<ClassInfo, ClassBox> boxMap,
            List<PackageGroupBox> packageGroups) {
        for (var sp : slotPlacements) {
            int shift = shifts.get(sp.key());
            if (shift == 0) continue;
            for (var info : sp.members()) {
                var box = boxMap.get(info);
                box.setPosition(box.x() + shift, box.y());
            }
            if (sp.boxIndex() >= 0) {
                var oldBox = packageGroups.get(sp.boxIndex());
                packageGroups.set(sp.boxIndex(), new PackageGroupBox(
                    oldBox.label(),
                    oldBox.x() + shift,
                    oldBox.y(),
                    oldBox.width(),
                    oldBox.height()
                ));
            }
        }
    }

    private String slotKeyFor(ClassInfo info) {
        if (info.packageName().equals(rootPackageForGrouping)) return "";
        if (info.packageName().startsWith(rootPackageForGrouping + ".")) {
            return info.packageName().substring(rootPackageForGrouping.length() + 1);
        }
        // Defensive: package outside root → use full package name as key.
        return info.packageName();
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
