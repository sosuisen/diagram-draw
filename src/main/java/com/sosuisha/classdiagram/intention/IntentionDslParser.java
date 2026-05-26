package com.sosuisha.classdiagram.intention;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * intention DSL文字列を解析して {@link PlaceConstraint} のリストを返すパーサー。
 *
 * <p>1行に1文を記述する。空行および {@code #} で始まるコメント行はスキップ。
 * 現在サポートする動詞は {@code place} のみ。
 */
public class IntentionDslParser {

    /**
     * DSL文字列を解析して配置制約のリストを返す。
     *
     * @param dsl intention DSL文字列（複数行可）
     * @return パース済み配置制約のリスト（変更不可）
     * @throws NullPointerException    dslがnullの場合
     * @throws IntentionParseException 構文エラーがある場合（行番号を含むメッセージ）
     */
    public List<PlaceConstraint> parse(String dsl) {
        Objects.requireNonNull(dsl, "dsl must not be null");
        var result = new ArrayList<PlaceConstraint>();
        var lines = dsl.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            int lineNumber = i + 1;
            var line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            var tokens = line.split("\\s+");
            if (!tokens[0].equals("place")) {
                throw new IntentionParseException(lineNumber,
                    "unknown verb: '" + tokens[0] + "'");
            }
            result.add(parsePlaceStatement(tokens, lineNumber));
        }
        return List.copyOf(result);
    }

    private PlaceConstraint parsePlaceStatement(String[] tokens, int lineNumber) {
        if (tokens.length < 4) {
            throw new IntentionParseException(lineNumber, "invalid place statement");
        }
        var target = tokens[1];
        switch (tokens[2]) {
            case "above" -> {
                if (tokens.length != 4)
                    throw new IntentionParseException(lineNumber, "invalid place statement");
                return new PlaceConstraint(target, PlaceDirection.ABOVE, tokens[3], lineNumber);
            }
            case "below" -> {
                if (tokens.length != 4)
                    throw new IntentionParseException(lineNumber, "invalid place statement");
                return new PlaceConstraint(target, PlaceDirection.BELOW, tokens[3], lineNumber);
            }
            case "right" -> {
                if (tokens.length != 5 || !tokens[3].equals("of"))
                    throw new IntentionParseException(lineNumber, "invalid place statement");
                return new PlaceConstraint(target, PlaceDirection.RIGHT_OF, tokens[4], lineNumber);
            }
            case "left" -> {
                if (tokens.length != 5 || !tokens[3].equals("of"))
                    throw new IntentionParseException(lineNumber, "invalid place statement");
                return new PlaceConstraint(target, PlaceDirection.LEFT_OF, tokens[4], lineNumber);
            }
            default -> throw new IntentionParseException(lineNumber,
                "unknown direction: '" + tokens[2] + "'");
        }
    }
}
