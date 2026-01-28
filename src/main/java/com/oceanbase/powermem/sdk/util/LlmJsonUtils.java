package com.oceanbase.powermem.sdk.util;

/**
 * Utilities for parsing LLM outputs that are supposed to be JSON.
 *
 * <p>Handles common real-world issues: code fences, leading/trailing chatter, partial JSON.</p>
 */
public final class LlmJsonUtils {
    private static final com.oceanbase.powermem.sdk.json.JsonCodec JSON = new com.oceanbase.powermem.sdk.json.JacksonJsonCodec();

    private LlmJsonUtils() {}

    public static java.util.Map<String, Object> parseJsonObjectLoose(String raw) {
        String cleaned = PowermemUtils.removeCodeBlocks(raw);
        java.util.Map<String, Object> map = tryParse(cleaned);
        if (map != null) {
            return map;
        }
        String extracted = extractFirstJsonObject(cleaned);
        map = tryParse(extracted);
        if (map != null) {
            return map;
        }
        return new java.util.HashMap<>();
    }

    private static java.util.Map<String, Object> tryParse(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return JSON.fromJsonToMap(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Extract the first balanced {...} JSON object substring.
     */
    public static String extractFirstJsonObject(String s) {
        if (s == null) {
            return "";
        }
        int start = -1;
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == '}') {
                if (depth > 0) {
                    depth--;
                    if (depth == 0 && start >= 0) {
                        return s.substring(start, i + 1);
                    }
                }
            }
        }
        return "";
    }
}

