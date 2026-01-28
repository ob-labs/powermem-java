package com.oceanbase.powermem.sdk.exception;

/**
 * Exception representing HTTP transport failures.
 *
 * <p>No direct Python equivalent class; Python's HTTP clients raise their own exception types.</p>
 */
public class HttpException extends PowermemException {
    public HttpException() {
        super();
    }

    public HttpException(String message) {
        super(message);
    }

    public HttpException(String message, Throwable cause) {
        super(message, cause);
    }
}

