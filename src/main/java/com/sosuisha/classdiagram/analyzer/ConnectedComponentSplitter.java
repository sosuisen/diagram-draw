package com.sosuisha.classdiagram.analyzer;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * {@link ClassRelation} のリストから連結成分を検出し、
 * 各 {@link ClassInfo} に {@code groupIndex} を設定する。
 *
 * <p>辺の方向を無視した無向グラフとして Union-Find を実行する。
 * relations リスト先頭から最初に出現した成分が groupIndex=0 となる。
 */
public class ConnectedComponentSplitter {

    /**
     * relations 内の全 ClassInfo に groupIndex を設定し、relations をそのまま返す。
     *
     * @param relations 関係リスト
     * @return 同一の relations インスタンス（ClassInfo の groupIndex が書き換わっている）
     * @throws NullPointerException relations が null の場合
     */
    public List<ClassRelation> split(List<ClassRelation> relations) {
        Objects.requireNonNull(relations, "relations must not be null");
        if (relations.isEmpty()) return relations;

        // Union-Find (ClassInfo の equality ベース)
        Map<ClassInfo, ClassInfo> parent = new LinkedHashMap<>();
        for (var rel : relations) {
            parent.putIfAbsent(rel.sourceClassInfo(), rel.sourceClassInfo());
            parent.putIfAbsent(rel.targetClassInfo(), rel.targetClassInfo());
        }
        for (var rel : relations) {
            union(parent, rel.sourceClassInfo(), rel.targetClassInfo());
        }

        // 根ノードに groupIndex を割り当て（出現順）
        Map<ClassInfo, Integer> rootToGroup = new LinkedHashMap<>();
        int nextGroup = 0;
        for (var node : List.copyOf(parent.keySet())) {
            var root = find(parent, node);
            if (!rootToGroup.containsKey(root)) {
                rootToGroup.put(root, nextGroup++);
            }
        }

        // 全 ClassInfo インスタンス（重複含む）に groupIndex を設定
        Set<ClassInfo> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var rel : relations) {
            for (var info : List.of(rel.sourceClassInfo(), rel.targetClassInfo())) {
                if (visited.add(info)) {
                    var root = find(parent, info);
                    info.setGroupIndex(rootToGroup.get(root));
                }
            }
        }

        return relations;
    }

    private ClassInfo find(Map<ClassInfo, ClassInfo> parent, ClassInfo node) {
        var p = parent.getOrDefault(node, node);
        if (p.equals(node)) return node;
        var root = find(parent, p);
        parent.put(node, root);
        return root;
    }

    private void union(Map<ClassInfo, ClassInfo> parent, ClassInfo a, ClassInfo b) {
        var rootA = find(parent, a);
        var rootB = find(parent, b);
        if (!rootA.equals(rootB)) {
            parent.put(rootA, rootB);
        }
    }
}
