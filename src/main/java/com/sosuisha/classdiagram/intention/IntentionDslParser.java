package com.sosuisha.classdiagram.intention;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * intention DSL文字列を解析して {@link ParseResult} を返すパーサー。
 *
 * <p>1行に1文を記述する。空行および {@code #} で始まるコメント行はスキップ。
 * サポートする動詞: {@code place}, {@code arrow}。
 */
public class IntentionDslParser {

    /**
     * DSL文字列を解析して {@link ParseResult} を返す。
     *
     * @param dsl intention DSL文字列（複数行可）
     * @return パース済み制約の結果（各リストは変更不可）
     * @throws NullPointerException    dslがnullの場合
     * @throws IntentionParseException 構文エラーがある場合（行番号を含むメッセージ）
     */
    public ParseResult parse(String dsl) {
        Objects.requireNonNull(dsl, "dsl must not be null");
        var placeList = new ArrayList<PlaceConstraint>();
        var arrowList = new ArrayList<ArrowConstraint>();
        var lines = dsl.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            int lineNumber = i + 1;
            var line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            var tokens = line.split("\\s+");
            switch (tokens[0]) {
                case "place" -> placeList.add(parsePlaceStatement(tokens, lineNumber));
                case "arrow" -> arrowList.add(parseArrowStatement(tokens, lineNumber));
                default -> throw new IntentionParseException(lineNumber,
                    "unknown verb: '" + tokens[0] + "'");
            }
        }
        return new ParseResult(List.copyOf(placeList), List.copyOf(arrowList));
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

    private ArrowConstraint parseArrowStatement(String[] tokens, int lineNumber) {
        // arrow <source> <target> from <fromEdge> [to <toEdge>]
        // 最小: 5トークン (arrow A B from bottom)
        // 最大: 7トークン (arrow A B from bottom to top)
        if (tokens.length < 5 || !tokens[3].equals("from")) {
            throw new IntentionParseException(lineNumber, "invalid arrow statement");
        }
        if (tokens.length == 6 || tokens.length > 7) {
            throw new IntentionParseException(lineNumber, "invalid arrow statement");
        }
        var source = tokens[1];
        var target = tokens[2];
        var fromEdge = parseEdge(tokens[4], lineNumber);
        ArrowEdge toEdge = null;
        if (tokens.length == 7) {
            if (!tokens[5].equals("to")) {
                throw new IntentionParseException(lineNumber, "invalid arrow statement");
            }
            toEdge = parseEdge(tokens[6], lineNumber);
        }
        return new ArrowConstraint(source, target, fromEdge, toEdge, lineNumber);
    }

    private ArrowEdge parseEdge(String token, int lineNumber) {
        return switch (token) {
            case "top"    -> ArrowEdge.TOP;
            case "bottom" -> ArrowEdge.BOTTOM;
            case "left"   -> ArrowEdge.LEFT;
            case "right"  -> ArrowEdge.RIGHT;
            default -> throw new IntentionParseException(lineNumber,
                "unknown edge: '" + token + "'");
        };
    }
}
