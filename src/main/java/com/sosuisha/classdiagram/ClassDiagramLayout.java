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

/**
 * クラス関係情報からレイアウト位置を計算するエンジン。
 *
 * <p>Kahn法のレイヤー結果を最長パス法で再割り当てし、末端クラスを最下層へ集める。
 * 各レイヤー内のボックスはキャンバス中央揃えで配置される。
 */
public class ClassDiagramLayout {

    private final int horizontalGap;
    private final int verticalGap;
    private final int canvasPaddingX;
    private final int canvasPaddingY;

    /**
     * ClassDiagramLayoutを生成する。
     *
     * @param horizontalGap  同一レイヤー内のボックス間水平隙間（px）
     * @param verticalGap    レイヤー間の垂直隙間（px）
     * @param canvasPaddingX キャンバス左右の余白（px）
     * @param canvasPaddingY キャンバス上下の余白（px）
     */
    public ClassDiagramLayout(int horizontalGap, int verticalGap,
                               int canvasPaddingX, int canvasPaddingY) {
        this.horizontalGap = horizontalGap;
        this.verticalGap = verticalGap;
        this.canvasPaddingX = canvasPaddingX;
        this.canvasPaddingY = canvasPaddingY;
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

        // Step 1: 最長パス法でレイヤーを再割り当て
        var reassigned = reassignLayers(layers, relations);

        // Step 2: ClassInfo → ClassBox マップ作成（挿入順保持）
        Map<ClassInfo, ClassBox> boxMap = new LinkedHashMap<>();
        for (var layer : reassigned) {
            for (var info : layer) {
                boxMap.put(info, new ClassBox(info.simpleName()));
            }
        }

        // Step 3: レイヤーごとに幅と最大高さを計算
        int numLayers = reassigned.size();
        int[] maxBoxHeight = new int[numLayers];
        int[] layerWidth = new int[numLayers];

        for (int i = 0; i < numLayers; i++) {
            var layer = reassigned.get(i);
            int w = 0;
            int maxH = 0;
            for (var info : layer) {
                var box = boxMap.get(info);
                w += box.width();
                maxH = Math.max(maxH, box.height());
            }
            if (layer.size() > 1) {
                w += (layer.size() - 1) * horizontalGap;
            }
            layerWidth[i] = w;
            maxBoxHeight[i] = maxH;
        }

        // canvasContentWidth = 全レイヤーの最大幅
        int canvasContentWidth = 0;
        for (int w : layerWidth) {
            canvasContentWidth = Math.max(canvasContentWidth, w);
        }

        // Step 4: 中央揃えで各ボックスに座標を設定
        int currentY = canvasPaddingY;
        for (int i = 0; i < numLayers; i++) {
            var layer = reassigned.get(i);
            int startX = canvasPaddingX + (canvasContentWidth - layerWidth[i]) / 2;
            int x = startX;
            for (var info : layer) {
                var box = boxMap.get(info);
                box.setPosition(x, currentY);
                x += box.width() + horizontalGap;
            }
            currentY += maxBoxHeight[i] + verticalGap;
        }

        // Step 5: キャンバスサイズ計算
        int totalHeight = 0;
        for (int h : maxBoxHeight) totalHeight += h;
        totalHeight += (numLayers - 1) * verticalGap;
        int canvasWidth = canvasContentWidth + 2 * canvasPaddingX;
        int canvasHeight = totalHeight + 2 * canvasPaddingY;

        // Step 6: Dependency 生成
        var dependencies = new ArrayList<Dependency>();
        for (var rel : relations) {
            var src = boxMap.get(rel.sourceClassInfo());
            var tgt = boxMap.get(rel.targetClassInfo());
            if (src != null && tgt != null) {
                dependencies.add(new Dependency(src, tgt, rel.type()));
            }
        }

        return new LayoutResult(
            List.copyOf(boxMap.values()),
            List.copyOf(dependencies),
            canvasWidth,
            canvasHeight
        );
    }

    /**
     * Kahn法のレイヤーを最長パス法で再割り当てする。
     *
     * <p>depth[v] = vから最も遠い末端までの最長パス長。末端ノードは depth=0。
     * layer[v] = maxDepth - depth[v] により末端が最下層に集まる。
     */
    private List<List<ClassInfo>> reassignLayers(
            List<List<ClassInfo>> kahnsLayers,
            List<ClassRelation> relations) {

        // 隣接マップ（ソース→サクセッサ集合）を構築
        Map<ClassInfo, Set<ClassInfo>> adjacency = new HashMap<>();
        for (var rel : relations) {
            adjacency.computeIfAbsent(rel.sourceClassInfo(), k -> new HashSet<>())
                     .add(rel.targetClassInfo());
        }

        // Kahnレイヤーの逆順 = reverse topological order でdepthを計算
        Map<ClassInfo, Integer> depth = new HashMap<>();
        for (int i = kahnsLayers.size() - 1; i >= 0; i--) {
            for (var node : kahnsLayers.get(i)) {
                int maxSuccDepth = -1;
                for (var succ : adjacency.getOrDefault(node, Set.of())) {
                    maxSuccDepth = Math.max(maxSuccDepth, depth.getOrDefault(succ, 0));
                }
                depth.put(node, maxSuccDepth + 1);
            }
        }

        int maxDepth = depth.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int numLayers = maxDepth + 1;

        List<List<ClassInfo>> result = new ArrayList<>();
        for (int i = 0; i < numLayers; i++) {
            result.add(new ArrayList<>());
        }
        for (var entry : depth.entrySet()) {
            int layerIdx = maxDepth - entry.getValue();
            result.get(layerIdx).add(entry.getKey());
        }

        // 各レイヤー内を名前でソート（決定論的順序）
        for (var layer : result) {
            layer.sort(Comparator.comparing(ClassInfo::simpleName)
                                 .thenComparing(ClassInfo::packageName));
        }

        return result;
    }
}
