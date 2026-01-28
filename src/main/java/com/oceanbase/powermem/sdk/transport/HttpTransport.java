package com.oceanbase.powermem.sdk.transport;

/**
 * Marker interface for HTTP-based transport implementations.
 *
 * <p>No direct Python equivalent; similar responsibilities live in Python's HTTP client usage (e.g.
 * {@code httpx}) when calling LLM/embedding services.</p>
 */
public interface HttpTransport extends Transport {
}

