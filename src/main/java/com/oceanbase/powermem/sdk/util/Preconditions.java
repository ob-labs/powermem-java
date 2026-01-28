package com.oceanbase.powermem.sdk.util;

/**
 * Internal precondition checks (argument validation helpers).
 *
 * <p>No direct Python equivalent; Python commonly uses {@code ValueError} checks inline.</p>
 */
public final class Preconditions {
    private Preconditions() {}

    public static <T> T requireNonNull(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message == null ? "value is null" : message);
        }
        return value;
    }

    public static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message == null ? "value is blank" : message);
        }
        return value;
    }
}

