package com.sosuisha.classdiagram.analyzer.fixture;

/**
 * ClassRelationScannerのテスト用フィクスチャ。2つのインタフェースを実装するクラス。
 */
public class FixtureMultiImpl implements FixtureService, FixtureAnotherService {
    @Override
    public void execute() {}

    @Override
    public void process() {}
}
