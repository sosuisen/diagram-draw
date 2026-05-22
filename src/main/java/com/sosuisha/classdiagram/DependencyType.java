package com.sosuisha.classdiagram;

/**
 * クラス間の依存関係の種類。
 */
public enum DependencyType {
    /** コンポジション（強い所有関係）*/
    COMPOSITION,
    /** 集約（弱い所有関係）*/
    AGGREGATION,
    /** 実現（インタフェースと実装クラスの関係）*/
    REALIZATION,
    /** 依存（ローカル変数・メソッドパラメータ経由の使用関係）*/
    DEPENDENCY
}
