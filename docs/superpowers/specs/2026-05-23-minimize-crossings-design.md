# 辺交差の最小化 — 設計ドキュメント

**日付**: 2026-05-23  
**対象クラス**: `ClassDiagramLayout`  
**アルゴリズム**: Sugiyamaフレームワーク 重心法（バリセンター法）

---

## 概要

同一レイヤー内のボックスの並び順を入れ替えることで、依存関係を表す線の交差数を最小化する。
`ClassDiagramLayout.layout()` に private メソッド `minimizeCrossings()` を追加し、
Step 1（boxMap作成）の直後・Step 2（groupSubLayers構築）の前に呼び出す。

---

## 挿入位置

```
Step 1: boxMap 作成（ClassInfo → ClassBox）
        ↓
[NEW] orderedLayers = minimizeCrossings(layers, relations)
        ↓
Step 2: orderedLayers から groupSubLayers 構築（空レイヤー除去）
        ↓
Step 3〜: 幅計算・座標設定（変更なし）
```

---

## メソッド設計

### `minimizeCrossings`

```java
private List<List<ClassInfo>> minimizeCrossings(
    List<List<ClassInfo>> layers, List<ClassRelation> relations)
```

- `layers` をそのまま変更せず、ノードの順序を最適化した新しいリストを返す
- レイヤーが1つ以下の場合はそのまま返す（最適化不要）
- 最大12パス、収束（1パスで順序変化なし）で早期終了

### `sortLayerByBarycenter`

```java
private boolean sortLayerByBarycenter(
    List<ClassInfo> layer,
    List<ClassInfo> referenceLayer,
    Map<ClassInfo, List<ClassInfo>> adj)
```

- `layer` の各ノードのバリセンターを計算し、安定ソートで並び替える
- バリセンター = `adj` 内の隣接ノードのうち `referenceLayer` に存在するものの インデックス平均
- 隣接なし → バリセンター = 現在のインデックス（相対順序を保持）
- 戻り値: 順序が変化した場合 `true`

---

## 隣接マップの構築

`relations` を走査して2つのマップを構築する:

| relation type | parents への追加 | children への追加 |
|---|---|---|
| COMPOSITION / AGGREGATION (src→tgt) | `parents[tgt] += src` | `children[src] += tgt` |
| REALIZATION (impl→iface) | `parents[impl] += iface` | `children[iface] += impl` |

DEPENDENCY型はグループ間の矢印であり、グループ内の交差最小化には使用しない。

---

## パスの動作

### 偶数パス（上→下）

```
for i in 0..layers.size()-2:
    layer[i] を固定
    layer[i+1] の各ノードのバリセンター = parents[node] ∩ layer[i] のインデックス平均
    layer[i+1] をバリセンターで安定ソート
```

### 奇数パス（下→上）

```
for i in layers.size()-2..0:
    layer[i+1] を固定
    layer[i] の各ノードのバリセンター = children[node] ∩ layer[i+1] のインデックス平均
    layer[i] をバリセンターで安定ソート
```

### 収束条件

1パスで `sortLayerByBarycenter` が一度も `true` を返さなければ終了。

---

## グループ混在レイヤーの扱い

1つのグローバルレイヤーに複数グループのノードが混在する場合でも、
relations は同一グループ内でのみ存在するため、異なるグループのノードは
互いのバリセンターに影響しない。
Step 2 のフィルタリングが各グループ内の相対順序を保持するため、
グローバルレイヤー上での操作で正しく動作する。

---

## テスト方針

### 既存テスト

すべて変更なしでパスすること（レイアウト座標・サイズ計算ロジックは不変）。

### 新規テスト（`ClassDiagramLayoutTest`）

| テスト名 | 内容 |
|---|---|
| `minimizeCrossingsReducesCrossings` | 明示的交差ケースで最小化後の順序を検証 |
| `minimizeCrossingsNoChangeForSingleLayer` | 1レイヤーのみの場合に元の順序を保持 |
| `minimizeCrossingsHandlesNoNeighbor` | 隣接なしノードでエラーなし・順序保持 |

---

## 変更ファイル

- `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java` — メソッド追加・`layout()` 修正
- `src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java` — 新規テスト追加
