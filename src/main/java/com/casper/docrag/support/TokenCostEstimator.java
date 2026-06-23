package com.casper.docrag.support;

import org.springframework.stereotype.Component;

/**
 * 粗略 token 成本估算（M4 成本記錄）。CJK 字元計 1 token，其餘非空白字元約每 4 字元 1 token。
 * 僅供成本量級觀察與趨勢比較，非計費精度；精確計費可改用模型 tokenizer。
 */
@Component
public class TokenCostEstimator {

    public int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int cjk = 0;
        int other = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (CjkText.isCjk(c)) {
                cjk++;
            } else {
                other++;
            }
        }
        return cjk + (int) Math.ceil(other / 4.0);
    }

    /** prompt + completion 的合計估算。 */
    public int estimate(String prompt, String completion) {
        return estimate(prompt) + estimate(completion);
    }
}
