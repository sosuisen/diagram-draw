# Co-Implementor 隣接制約 — 設計ドキュメント

**日付**: 2026-05-24
**対象クラス**: `ClassDiagramLayout`
**前提**: `2026-05-23-minimize-crossings-design.md` の重心法実装が既に存在する

---

## 概要

同一レイヤー内のボックスを並べ替える際、**同じインタフェースを実装するクラスは隣接して配置されなければならない**という制約を追加する。
バリセンター法のソートロジックを2段階ソート（グループ単位→グループ内）に変更し、co-implementor が常に連続した位置を占めることを保証する。

---

## 前提条件 (スコープ)

- 1クラスは最大で1つのインタフェースしか実装しない（現状のスキャン範囲ではマルチインタフェース実装は発生しない）
- 制約はハード制約: バリセンター最適化より優先される

---

## グループキーの定義

各ノード `N` のグループキーは次のとおり:

```
groupKey(N) = ifaceOfImpl(N) if N implements some interface
            = N otherwise
```

- `ifaceOfImpl`: REALIZATION関係から構築する `Map<ClassInfo, ClassInfo>`（implementer → interface）
- 同じインタフェースを実装するノード同士は同じグループキーを持つ
- インタフェースを実装しないクラス・インタフェース自身はそれぞれ単独グループ

---

## アルゴリズム変更点

### 1. `minimizeCrossings()`

既存の `upNeighbors` / `downNeighbors` マップ構築に加えて、`ifaceOfImpl` を構築:

```java
Map<ClassInfo, ClassInfo> ifaceOfImpl = new HashMap<>();
for (var rel : relations) {
    if (rel.type() == DependencyType.REALIZATION) {
        ifaceOfImpl.put(rel.sourceClassInfo(), rel.targetClassInfo());
    }
}
```

`sortLayerByBarycenter` 呼び出し時に `ifaceOfImpl` を渡す。

### 2. `sortLayerByBarycenter()` の2段階ソート

```
1. 各ノードの個別バリセンター bary[N] を計算（現状通り）
2. ノードをグループキーでグループ化（LinkedHashMap で挿入順保持）
3. グループバリセンター groupBary[G] = avg(bary[n] for n in G)
4. グループを groupBary[G] で安定ソート
5. 各グループ内ノードを bary[N] で安定ソート
6. グループ順に展開して結果リストを構築
```

co-implementor は必ず同じグループに属するため、ステップ6で連続した位置に並ぶ。

### シグネチャ

```java
private boolean sortLayerByBarycenter(
    List<ClassInfo> layer,
    List<ClassInfo> referenceLayer,
    Map<ClassInfo, List<ClassInfo>> adj,
    Map<ClassInfo, ClassInfo> ifaceOfImpl)
```

---

## 既存挙動への影響

- 各ノードが単独グループになる場合（インタフェース実装関係なし、または同一インタフェースの実装者が1人だけのレイヤー）、ステップ3-6 は単純なバリセンターソートと等価
- 既存テスト（`minimizeCrossingsRealizationReducesCrossings` 等）は単独実装者ばかりなので結果不変

---

## テスト方針

### 既存テスト

すべて変更なしでパス。

### 新規テスト

#### `coImplementorsStayAdjacentWhenSameBarycenter`

干渉ノードが co-implementor と同じバリセンターを持つケース。グループ化なしではタイブレークで混入する可能性があるが、グループ化により co-implementor が連続することを検証。

- Layer 0: `[A, IFoo, B]` (A at 0, IFoo at 1, B at 2)
- Layer 1: `[ImplFoo1, X, ImplFoo2]`
  - `ImplFoo1 → IFoo` (REALIZATION): bary=1
  - `X → A`, `X → B` (COMPOSITION): bary=(0+2)/2=1
  - `ImplFoo2 → IFoo` (REALIZATION): bary=1
- 期待結果: `ImplFoo1` と `ImplFoo2` が隣接（`X` がそれらの間に来ない）
- 検証: `|boxImplFoo1.x() - boxImplFoo2.x()| == 1つ分の幅+gap` （位置で隣接判定）

実装方法: `Math.abs(boxImplFoo1.x() - boxImplFoo2.x())` が `boxImplFoo1.width() + horizontalGap` 以下であることを確認することで、間に他のボックスが入っていないと判定。

#### `coImplementorsStayAdjacentWithInterveningBarycenter`

干渉ノードのバリセンターが co-implementor の間に来るケース。グループ化なしではバリセンター順に並んで分離してしまうが、グループ化によりまとめられる。

- Layer 0: `[IFoo, X, IFoo` …これは同じノードを2回置けないので工夫が必要。
- 代替案: Layer 0 `[A, X, B]`、Layer 1 `[ImplFoo1, Y, ImplFoo2]` で:
  - `ImplFoo1 → A` 経由（A は IFoo を実装するインタフェースノードと仮定）はモデル違反になる。
  - 純粋に Layer 1 内で interleave するシナリオを構築するため:
    - Layer 0: `[IFoo, M]`
    - Layer 1: `[ImplFoo1, Y, ImplFoo2]`
      - `ImplFoo1 → IFoo`: bary=0
      - `Y → M`: bary=1
      - `ImplFoo2 → IFoo`: bary=0
    - グループ化なしのソート: `[ImplFoo1(0), ImplFoo2(0), Y(1)]` → 既に隣接

実は **多くのケースで通常のバリセンターソートは自然に co-implementor を連続にする**（同じバリセンターを共有するため）。
真に制約が必要になるのは「同点」のケース（test 1）が中心。

そのため `coImplementorsStayAdjacentWithInterveningBarycenter` は省略し、`coImplementorsStayAdjacentWhenSameBarycenter` 一つに集中する。

---

## 変更ファイル

- `src/main/java/com/sosuisha/classdiagram/ClassDiagramLayout.java` — `minimizeCrossings()` で `ifaceOfImpl` 構築、`sortLayerByBarycenter()` の2段階ソート化
- `src/test/java/com/sosuisha/classdiagram/ClassDiagramLayoutTest.java` — 新規テスト1件追加
