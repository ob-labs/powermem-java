package com.oceanbase.powermem.sdk.exception;

/**
 * Base runtime exception for the Java SDK.
 *
 * <p>Python typically raises built-in exceptions; there is no single base exception type.</p>
 *
 * <p>Closest Python reference: error handling paths in {@code src/powermem/core/memory.py} and
 * {@code src/powermem/core/async_memory.py}.</p>
 */
public class PowermemException extends RuntimeException {
    public PowermemException() {
        super();
    }

    public PowermemException(String message) {
        super(message);
    }

    public PowermemException(String message, Throwable cause) {
        super(message, cause);
    }

    public PowermemException(Throwable cause) {
        super(cause);
    }
}

