package com.sosuisha.classdiagram;

import com.sosuisha.classdiagram.analyzer.ClassInfo;
import com.sosuisha.classdiagram.analyzer.ClassRelation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * クラス関係情報からレイアウト位置を計算するエンジン。
 *
 * <p>ClassRelationSorterのKahn法レイヤーを使用し、groupIndex が同じノードを
 * 1つのグループとして垂直スタックにまとめ、グループ間は groupGap ピクセルで
 * 水平方向に並べる。各レイヤーはグループ幅基準で中央揃えされる。
 */
public class ClassDiagramLayout {

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

        // Step 2: groupIndex ごとにサブレイヤーを構築（空レイヤーは除去）
        var groupIndices = layers.stream()
            .flatMap(List::stream)
            .mapToInt(ClassInfo::groupIndex)
            .distinct()
            .sorted()
            .toArray();
        int numGroups = groupIndices.length;

        var groupSubLayers = new ArrayList<List<List<ClassInfo>>>(numGroups);
        for (int gi : groupIndices) {
            var subLayers = new ArrayList<List<ClassInfo>>();
            for (var layer : layers) {
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

        return new LayoutResult(
            List.copyOf(boxMap.values()),
            List.copyOf(dependencies),
            canvasWidth,
            canvasHeight
        );
    }
}
