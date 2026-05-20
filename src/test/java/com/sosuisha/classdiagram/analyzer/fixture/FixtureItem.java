package com.sosuisha.classdiagram.analyzer.fixture;

import java.util.Objects;

/**
 * ClassRelationScannerのテスト用フィクスチャ。同パッケージフィールドを持たないPOJO。
 */
public class FixtureItem {
    private String name;

    /**
     * FixtureItemを生成する。
     *
     * @param name アイテム名
     * @throws NullPointerException nameがnullの場合
     */
    public FixtureItem(String name) {
        this.name = Objects.requireNonNull(name, "name must not be null");
    }
}
