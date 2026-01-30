package com.oceanbase.powermem.sdk.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Minimal tokenizer used for BM25 in graph store.
 *
 * <p>Python parity: jieba is used when available; here we use a lightweight heuristic:
 * - ASCII words: split by non-alphanumerics
 * - CJK: treat each CJK character as a token
 * - Also keep numeric tokens</p>
 */
public final class TextTokenizer {
    private TextTokenizer() {}

    public static List<String> tokenize(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        String s = text.toLowerCase(Locale.ROOT);

        StringBuilder ascii = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (isCjk(c)) {
                flushAscii(ascii, out);
                out.add(String.valueOf(c));
            } else if (Character.isLetterOrDigit(c)) {
                ascii.append(c);
            } else {
                flushAscii(ascii, out);
            }
        }
        flushAscii(ascii, out);
        return out;
    }

    private static void flushAscii(StringBuilder sb, List<String> out) {
        if (sb.length() == 0) return;
        String tok = sb.toString();
        sb.setLength(0);
        if (!tok.isBlank()) {
            out.add(tok);
        }
    }

    private static boolean isCjk(char c) {
        Character.UnicodeBlock b = Character.UnicodeBlock.of(c);
        return b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || b == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || b == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || b == Character.UnicodeBlock.HIRAGANA
                || b == Character.UnicodeBlock.KATAKANA
                || b == Character.UnicodeBlock.HANGUL_SYLLABLES;
    }
}

