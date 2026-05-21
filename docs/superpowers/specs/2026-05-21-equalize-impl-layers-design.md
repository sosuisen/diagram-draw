# Equalize Implementation Layers — Design Spec

**Date**: 2026-05-21  
**Status**: Approved

## Summary

After the longest-path layer reassignment in `ClassDiagramLayout`, add a post-processing step that places implementation classes sharing the same interface into the same layer. The target layer is the minimum (shallowest) layer index among the group. Classes that implement two or more interfaces from the scanned package are excluded from equalization.

---

## Scope

- In scope: REALIZATION relationships only; impls that implement exactly one in-package interface
- Out of scope: classes implementing 2+ interfaces (left unchanged); COMPOSITION/AGGREGATION layout

---

## Architecture

### Insertion point

Inside `ClassDiagramLayout.layout()`, between Step 1 and Step 2:

```
Step 1: reassignLayers(layers, relations)     ← unchanged
[NEW]   equalizeImplementationLayers(...)     ← new post-processing step
Step 2: Build ClassInfo → ClassBox map        ← unchanged, now receives equalized layers
...
```

### New method signature

```java
private List<List<ClassInfo>> equalizeImplementationLayers(
    List<List<ClassInfo>> layers,
    List<ClassRelation> relations)
```

Returns a new layer list (same structure, some nodes moved, empty layers removed).

---

## Algorithm

1. **Build `layerOf`** (`Map<ClassInfo, Integer>`): for each layer index `i` and each `ClassInfo` in `layers.get(i)`, store `layerOf.put(info, i)`.

2. **Count interface implementations per class** (`Map<ClassInfo, Long> ifaceCount`): from `relations` where `type == REALIZATION`, count how many distinct target (interface) entries each source (impl) class has.

3. **Group impls by interface** (`Map<ClassInfo, List<ClassInfo>> ifaceToImpls`): for each REALIZATION relation, if `ifaceCount.get(source) == 1`, add `source` to the list keyed by `target` (the interface).

4. **Equalize groups**: for each entry in `ifaceToImpls` where the impl list has ≥2 entries:
   - `minLayer = min(layerOf.get(impl) for each impl in list)`
   - For each impl in list: `layerOf.put(impl, minLayer)`

5. **Rebuild layer list**: create `numLayers` empty lists, populate from `layerOf`, preserve per-layer sort order (by `ClassInfo::simpleName` then `ClassInfo::packageName`), drop empty layers.

---

## Edge Cases

| Case | Behavior |
|------|----------|
| Interface with exactly 1 impl | Group size = 1, no equalization (step 4 skipped) |
| Class implements 2+ in-package interfaces | Excluded by `ifaceCount == 1` filter; left at its depth-assigned layer |
| All impls already at same layer | `minLayer` = current layer, no change |
| Moving impls empties a layer | Empty layers are dropped in step 5 |

---

## Tests

| Test | Assertion |
|------|-----------|
| Two impls of same interface at different depths → equalized to min layer | Both at same layer index after equalization |
| Impl with 2+ interfaces → not moved | Layer index unchanged |
| Single impl of an interface → not moved | Layer index unchanged |
| Equalization does not create empty layers in output | Output layer list has no empty entries |
