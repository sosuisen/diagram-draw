# 設計: UML Dependency 矢印（cross-group 限定）

**日付**: 2026-05-23  
**対象機能**: ローカル変数・メソッドパラメータの型からDependency依存を検出し、groupIndexが異なるクラス間にのみ矢印を描画する

---

## 概要

`ClassRelationScanner` がスキャン時にメソッド・コンストラクタのパラメータ型とローカル変数型を解析し、スキャン対象パッケージ内のクラスへの依存を `ClassInfo` に記録する。`ClassDiagramLayout` はこの情報を参照し、`groupIndex` が異なるクラス間のみに UML Dependency 矢印（破線 + 開き矢印）を描画する。

---

## アーキテクチャ・パイプライン

```
ClassRelationScanner.scan()
        ↓ List<ClassRelation>  + ClassInfo.dependencyTargetFqns に FQN 追記済み
ConnectedComponentSplitter.split()   ← DEPENDENCY 依存は無視（変更なし）
        ↓ List<ClassRelation>  (groupIndex 割り当て済み)
ClassRelationSorter.sort()
        ↓ List<List<ClassInfo>>
ClassDiagramLayout.layout()          ← cross-group FQN を解決して Dependency 生成
        ↓ LayoutResult
SVGBuilder.build()
        ↓ String (SVG)
```

---

## 変更クラス一覧

| クラス | 変更種別 | 内容 |
|--------|---------|------|
| `ClassInfo` | 変更 | `Set<String> dependencyTargetFqns` フィールド追加 |
| `DependencyType` | 変更 | `DEPENDENCY` 値を追加 |
| `Dependency` | 変更 | `DEPENDENCY` の SVG 描画（破線 + 開き矢印）を追加 |
| `ClassRelationScanner` | 変更 | メソッド・コンストラクタのパラメータ・ローカル変数をスキャンして FQN を収集 |
| `ClassDiagramLayout` | 変更 | FQN を解決し cross-group のみ Dependency を生成 |
| `ConnectedComponentSplitter` | 変更なし | DEPENDENCY 依存はグループ検出に使わない |

---

## `ClassInfo` 変更設計

### 追加フィールド・メソッド

```java
private final Set<String> dependencyTargetFqns = new LinkedHashSet<>();

/**
 * @throws NullPointerException fqn が null の場合
 */
public void addDependencyTargetFqn(String fqn) {
    Objects.requireNonNull(fqn, "fqn must not be null");
    dependencyTargetFqns.add(fqn);
}

public Set<String> dependencyTargetFqns() {
    return Collections.unmodifiableSet(dependencyTargetFqns);
}
```

### 制約

- `equals`/`hashCode` には含めない（アイデンティティは `packageName + simpleName + stereotype` のみ）
- `LinkedHashSet` で挿入順を保持し、テストを安定させる

---

## `DependencyType` 変更設計

```java
public enum DependencyType { COMPOSITION, AGGREGATION, REALIZATION, DEPENDENCY }
```

---

## `Dependency` 変更設計

### DEPENDENCY の SVG 描画

破線（`stroke-dasharray="8,4"`）と開き V 字矢印（`<polyline fill="none">`）を組み合わせる。

```xml
<!-- 破線 -->
<line x1="..." y1="..." x2="..." y2="..."
      stroke="black" stroke-dasharray="8,4"/>
<!-- 開き矢印（2本の斜め線） -->
<polyline points="ax1,ay1 x2,y2 ax2,ay2"
          fill="none" stroke="black"/>
```

- `ax1,ay1` / `ax2,ay2` は target 点から線の角度 ±30° の方向へ 10px 戻った2点
- REALIZATION（中抜き三角形）と形状で区別可能

---

## `ClassRelationScanner` 変更設計

### スキャンアルゴリズム（追加部分）

1. フィールドスキャン中に `Set<String> fieldFqns`（既に COMPOSITION/AGGREGATION として追跡中の完全修飾名）を構築する
2. 全メソッド（`<init>` 含む）をイテレート:
   - パラメータのディスクリプタ／ジェネリクス署名から FQN を収集
   - `LocalVariableTable` / `LocalVariableTypeTable` から FQN を収集
3. 収集した各 FQN について以下をすべて満たす場合に `sourceClassInfo.addDependencyTargetFqn(fqn)` を呼ぶ:
   - スキャン対象パッケージ配下にある
   - `fieldFqns` に含まれない（コンポジション・集約は除外）
   - 自分自身でない（自己依存を除外）

### 前提・制約

- `LocalVariableTable` はデバッグ情報付きコンパイル（`javac -g`）が必要。Maven の `maven-compiler-plugin` はデフォルトで `-g` を含むため、通常の `mvn test` で生成される `target/test-classes` では利用可能。`.class` ファイルに `LocalVariableTable` が存在しない場合は、ローカル変数型の収集をスキップして空として扱う（パラメータ型の収集には影響しない）。
- `LocalVariableTypeTable` はジェネリクス付きローカル変数の署名を持つ。存在しない場合は `LocalVariableTable` のディスクリプタのみを使用する。

### ジェネリクス署名のパース

`Ljava/util/List<Lcom/example/Foo;>;` のような署名から `<` と `>` 内の `L...;` パターンを全て抽出するプライベートユーティリティメソッドを追加する。

```java
private Set<String> extractFqnsFromSignature(String signature)
```

- 外側の型（例: `java.util.List`）はスキャン対象パッケージ外なので自動的に除外される
- 再帰的なジェネリクス（`Map<Foo, Bar>`）にも対応する

---

## `ClassDiagramLayout` 変更設計

### 追加処理（既存 Step 7 の後）

1. `boxMap` のキーから FQN → ClassInfo の逆引きマップを構築:

```java
Map<String, ClassInfo> fqnToInfo = new HashMap<>();
for (var info : boxMap.keySet()) {
    fqnToInfo.put(info.packageName() + "." + info.simpleName(), info);
}
```

2. 各 ClassInfo の `dependencyTargetFqns()` をイテレートし、以下を全て満たす場合のみ `Dependency` を生成:
   - `fqnToInfo` でターゲットが解決できる（レイアウト対象クラスに存在する）
   - `source.groupIndex() != target.groupIndex()`（cross-group のみ）

3. 生成した `Dependency(src, tgt, DependencyType.DEPENDENCY)` を既存 `dependencies` リストに追加

---

## テスト方針

| テスト対象 | 内容 |
|-----------|------|
| `ClassInfoTest` | `dependencyTargetFqns` のデフォルト空セット、`addDependencyTargetFqn()` 追加、`null` で NPE、返却値が unmodifiable、`equals`/`hashCode` が FQN セットを無視 |
| `ClassRelationScannerTest` | ローカル変数・メソッドパラメータ・コンストラクタパラメータの FQN 収集、ジェネリクス型パラメータの抽出、フィールド型の除外、スキャン対象外パッケージの除外 |
| `DependencyTest` | `DependencyType.DEPENDENCY` で破線 + 開き矢印の SVG が生成される |
| `ClassDiagramLayoutTest` | cross-group FQN → DEPENDENCY 矢印が生成される、同 groupIndex → 矢印なし、存在しない FQN → 無視 |
| 統合テスト | 新フィクスチャを追加し、SVG に破線矢印が含まれることを確認 |

### 新フィクスチャ

`FixtureDependencySource`（グループ0相当）がメソッド内で `FixtureDependencyTarget`（グループ1相当・他クラスとの継承・フィールド依存なし）のローカル変数を持つ → cross-group DEPENDENCY が検出される。
