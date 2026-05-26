package com.sosuisha.classdiagram.intention;

import java.io.Serial;

/**
 * intention DSLのパースエラーまたは適用エラーを表す例外。
 *
 * <p>行番号を保持し、メッセージに "{@code line <N>: <detail>}" 形式で含める。
 */
public class IntentionParseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int lineNumber;

    /**
     * IntentionParseExceptionを生成する。
     *
     * @param lineNumber エラーが発生した行番号（1始まり）
     * @param message    エラーの詳細メッセージ
     */
    public IntentionParseException(int lineNumber, String message) {
        super("line " + lineNumber + ": " + message);
        this.lineNumber = lineNumber;
    }

    /**
     * エラーが発生した行番号を返す（1始まり）。
     *
     * @return 行番号
     */
    public int lineNumber() {
        return lineNumber;
    }
}
