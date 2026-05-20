package com.sosuisha.classdiagram.analyzer.fixture;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * ClassRelationScannerのテスト用フィクスチャ。注文情報を表すPOJO。
 */
public class FixtureOrder {
    private List<FixtureItem> items = new ArrayList<>();
    private FixtureCustomer customer;

    /**
     * FixtureOrderを生成する。
     *
     * @param customer 顧客情報
     * @throws NullPointerException customerがnullの場合
     */
    public FixtureOrder(FixtureCustomer customer) {
        this.customer = Objects.requireNonNull(customer, "customer must not be null");
    }
}
