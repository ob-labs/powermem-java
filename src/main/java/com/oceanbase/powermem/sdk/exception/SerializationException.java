package com.oceanbase.powermem.sdk.exception;

/**
 * Exception raised when JSON (de)serialization fails.
 *
 * <p>Python reference: places using {@code json.loads} and response parsing, e.g.
 * {@code src/powermem/core/memory.py}.</p>
 */
public class SerializationException extends PowermemException {
    public SerializationException() {
        super();
    }

    public SerializationException(String message) {
        super(message);
    }

    public SerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}

