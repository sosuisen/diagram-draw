package com.sosuisha.classdiagram;

/**
 * 手描き風の揺らぎに使う乱数のシードを作る。
 */
final class SketchSeed {

    // A large odd constant spreads nearby seeds such as 1 and 2 far apart.
    private static final long MULTIPLIER = 0x9E3779B97F4A7C15L;

    private SketchSeed() {
    }

    /**
     * 図形の内容から作ったハッシュ値に、図全体のシードを混ぜる。
     * シードが0のときは内容のハッシュ値をそのまま返す。
     *
     * @param contentHash 図形の内容から作ったハッシュ値
     * @param seed        図全体のシード
     * @return 乱数生成器に渡すシード
     */
    static long mix(int contentHash, long seed) {
        return contentHash + seed * MULTIPLIER;
    }
}
