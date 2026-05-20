package com.sosuisha.classdiagram.analyzer.fixture;

import java.util.Objects;

/**
 * ClassRelationScannerのテスト用フィクスチャ。顧客情報を表すPOJO。
 */
public class FixtureCustomer {
    private String email;

    /**
     * FixtureCustomerを生成する。
     *
     * @param email メールアドレス
     * @throws NullPointerException emailがnullの場合
     */
    public FixtureCustomer(String email) {
        this.email = Objects.requireNonNull(email, "email must not be null");
    }
}
