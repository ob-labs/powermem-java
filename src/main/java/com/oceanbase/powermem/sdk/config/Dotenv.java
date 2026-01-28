package com.oceanbase.powermem.sdk.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal .env parser/loader (filesystem).
 *
 * <p>Design goals:</p>
 * - Compatible with common dotenv formats: {@code KEY=VALUE}, optional {@code export }, quotes, comments
 * - No external dependencies
 *
 * <p>Notes:</p>
 * - This does NOT mutate {@link System#getenv()}; it only returns a map.
 * - Variable expansion (${VAR}) is intentionally not implemented (keep it deterministic and safe).
 */
public final class Dotenv {
    private Dotenv() {}

    public static Map<String, String> loadIfExists(Path dotenvFile) {
        if (dotenvFile == null) {
            return new HashMap<>();
        }
        try {
            if (!Files.exists(dotenvFile) || !Files.isRegularFile(dotenvFile)) {
                return new HashMap<>();
            }
        } catch (Exception ignored) {
            return new HashMap<>();
        }
        try (BufferedReader reader = Files.newBufferedReader(dotenvFile, StandardCharsets.UTF_8)) {
            return parse(reader);
        } catch (IOException ignored) {
            return new HashMap<>();
        }
    }

    public static Path resolveDotenvPath(String dirOrFile) {
        if (dirOrFile == null || dirOrFile.isBlank()) {
            return null;
        }
        Path p = Path.of(dirOrFile).toAbsolutePath().normalize();
        try {
            if (Files.isDirectory(p)) {
                return p.resolve(".env");
            }
            return p;
        } catch (Exception ignored) {
            return p;
        }
    }

    public static Map<String, String> parse(Reader reader) throws IOException {
        BufferedReader br = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);
        Map<String, String> values = new HashMap<>();
        String line;
        while ((line = br.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (trimmed.startsWith("export ")) {
                trimmed = trimmed.substring("export ".length()).trim();
            }
            int idx = indexOfUnescapedEquals(trimmed);
            if (idx <= 0) {
                continue;
            }
            String key = trimmed.substring(0, idx).trim();
            if (key.isEmpty()) {
                continue;
            }
            String rawValue = trimmed.substring(idx + 1).trim();
            String value = parseValue(rawValue);
            values.put(key, value);
        }
        return values;
    }

    private static int indexOfUnescapedEquals(String s) {
        // dotenv keys usually don't contain '='; keep it simple but avoid "\=".
        boolean escaped = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '=') {
                return i;
            }
        }
        return -1;
    }

    private static String parseValue(String raw) {
        if (raw == null) {
            return null;
        }
        if (raw.isEmpty()) {
            return "";
        }

        // Quoted values: keep everything until closing quote, then ignore trailing comments.
        char first = raw.charAt(0);
        if (first == '"' || first == '\'') {
            char quote = first;
            StringBuilder out = new StringBuilder();
            boolean escaped = false;
            for (int i = 1; i < raw.length(); i++) {
                char c = raw.charAt(i);
                if (escaped) {
                    out.append(unescapeChar(c));
                    escaped = false;
                    continue;
                }
                if (quote == '"' && c == '\\') { // only unescape in double-quotes
                    escaped = true;
                    continue;
                }
                if (c == quote) {
                    return out.toString();
                }
                out.append(c);
            }
            // No closing quote -> return best-effort
            return out.toString();
        }

        // Unquoted: strip inline comment if it starts with whitespace + '#'
        String noComment = stripInlineComment(raw);
        return noComment.trim();
    }

    private static String stripInlineComment(String raw) {
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '#') {
                // treat as comment only if preceded by whitespace
                if (i == 0) {
                    return "";
                }
                char prev = raw.charAt(i - 1);
                if (Character.isWhitespace(prev)) {
                    return raw.substring(0, i).trim();
                }
            }
        }
        return raw;
    }

    private static char unescapeChar(char c) {
        switch (c) {
            case 'n':
                return '\n';
            case 'r':
                return '\r';
            case 't':
                return '\t';
            case '"':
                return '"';
            case '\\':
                return '\\';
            default:
                return c;
        }
    }
}

