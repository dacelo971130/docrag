package com.casper.docrag.support;

/** CJK 字元判斷工具（中日韓）。供分塊與 token 估算共用。 */
public final class CjkText {

    private CjkText() {
    }

    public static boolean isCjk(char c) {
        Character.UnicodeScript script;
        try {
            script = Character.UnicodeScript.of(c);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }
}
