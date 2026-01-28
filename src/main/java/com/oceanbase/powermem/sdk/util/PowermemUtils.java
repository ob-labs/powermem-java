package com.oceanbase.powermem.sdk.util;

/**
 * Shared internal utilities for the Java core migration (hashing, time helpers, message parsing, etc.).
 *
 * <p>This file is expected to host equivalents of commonly used helpers from the Python codebase.</p>
 *
 * <p>Python reference: {@code src/powermem/utils/utils.py}</p>
 */
public final class PowermemUtils {
    private PowermemUtils() {}

    /**
     * Normalize either raw text or message list into a single string for embedding/storage.
     */
    public static String normalizeInput(String text, java.util.List<com.oceanbase.powermem.sdk.model.Message> messages) {
        if (text != null && !text.isBlank()) {
            return text;
        }
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (com.oceanbase.powermem.sdk.model.Message m : messages) {
            if (m == null) {
                continue;
            }
            String role = m.getRole() == null ? "" : m.getRole().trim();
            String content = m.getContent() == null ? "" : m.getContent().trim();
            if (content.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n");
            }
            if (!role.isEmpty()) {
                sb.append(role).append(": ");
            }
            sb.append(content);
        }
        return sb.toString();
    }

    public static String md5Hex(String value) {
        if (value == null) {
            return "";
        }
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            return "";
        }
    }

    /**
     * Remove markdown code fences from a model response (best-effort, Python-compatible behavior).
     */
    public static String removeCodeBlocks(String text) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            if (firstNewline >= 0) {
                t = t.substring(firstNewline + 1);
            }
            int lastFence = t.lastIndexOf("```");
            if (lastFence >= 0) {
                t = t.substring(0, lastFence);
            }
        }
        return t.trim();
    }
}

