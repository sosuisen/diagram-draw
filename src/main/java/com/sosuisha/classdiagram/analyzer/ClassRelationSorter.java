package com.sosuisha.classdiagram.analyzer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link ClassRelation} のリストをトポロジカルソートし、
 * 描画レイヤーごとに分類された {@link ClassInfo} のリストを返す。
 *
 * <p>アルゴリズム: Kahn's BFS（幅優先探索によるトポロジカルソート）。
 * 各クラスを入次数（自分を所有するクラスの数）で管理し、
 * 入次数が0のクラスから順にレイヤーへ割り当てる。
 * 全クラスを処理できなかった場合は循環参照と判定して例外をスローする。
 */
public class ClassRelationSorter {

    /**
     * 関係リストをトポロジカルソートし、レイヤーごとの {@link ClassInfo} リストを返す。
     * index 0 が最上位レイヤー（入次数0のクラス群）。
     *
     * @param relations 関係リスト
     * @return トポロジカル順に並べたレイヤーのリスト
     * @throws NullPointerException      relationsがnullの場合
     * @throws CircularRelationException 循環参照が検出された場合
     */
    public List<List<ClassInfo>> sort(List<ClassRelation> relations) {
        Objects.requireNonNull(relations, "relations must not be null");

        if (relations.isEmpty()) {
            return List.of();
        }

        // Kahn's BFS: 隣接リストと入次数マップを構築する
        Map<ClassInfo, Set<ClassInfo>> adjacency = new HashMap<>();
        Map<ClassInfo, Integer> inDegree = new HashMap<>();

        // Deduplicate: same (source, target) pair may appear more than once in input
        var uniqueEdges = relations.stream()
            .map(r -> Map.entry(r.sourceClassInfo(), r.targetClassInfo()))
            .collect(Collectors.toSet());

        for (var edge : uniqueEdges) {
            var src = edge.getKey();
            var tgt = edge.getValue();
            inDegree.putIfAbsent(src, 0);
            inDegree.putIfAbsent(tgt, 0);
            adjacency.computeIfAbsent(src, k -> new HashSet<>()).add(tgt);
            inDegree.merge(tgt, 1, Integer::sum);
        }

        // 入次数が0のノードを最初のレイヤーとして投入する
        var currentLayer = inDegree.entrySet().stream()
            .filter(e -> e.getValue() == 0)
            .map(Map.Entry::getKey)
            .sorted(Comparator.comparing(ClassInfo::simpleName))
            .collect(Collectors.toCollection(ArrayList::new));

        var result = new ArrayList<List<ClassInfo>>();
        int emitted = 0;

        // 各レイヤーを順に処理し、後継ノードの入次数を減らす
        while (!currentLayer.isEmpty()) {
            result.add(List.copyOf(currentLayer));
            emitted += currentLayer.size();

            var nextLayer = new ArrayList<ClassInfo>();
            for (var node : currentLayer) {
                for (var successor : adjacency.getOrDefault(node, Set.of())) {
                    var newDegree = inDegree.merge(successor, -1, Integer::sum);
                    if (newDegree == 0) {
                        nextLayer.add(successor);
                    }
                }
            }
            nextLayer.sort(Comparator.comparing(ClassInfo::simpleName));
            currentLayer = nextLayer;
        }

        // 入次数が残っているノードは循環参照に含まれる
        if (emitted < inDegree.size()) {
            var cycleNames = inDegree.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(e -> e.getKey().simpleName())
                .sorted()
                .collect(Collectors.joining(", "));
            throw new CircularRelationException(
                "Circular relation detected among: [" + cycleNames + "]");
        }

        return List.copyOf(result);
    }
}
