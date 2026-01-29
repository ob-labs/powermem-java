package com.oceanbase.powermem.sdk.config;

/**
 * Timeout policy configuration for outbound calls.
 *
 * <p>No direct Python equivalent; timeouts are configured per client library (e.g. httpx/OpenAI SDK)
 * rather than a central object.</p>
 */
public class TimeoutConfig {
    private long connectTimeoutMillis = 5000;
    private long readTimeoutMillis = 30000;
    private long writeTimeoutMillis = 30000;

    public TimeoutConfig() {}

    public long getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(long connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public long getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public void setReadTimeoutMillis(long readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
    }

    public long getWriteTimeoutMillis() {
        return writeTimeoutMillis;
    }

    public void setWriteTimeoutMillis(long writeTimeoutMillis) {
        this.writeTimeoutMillis = writeTimeoutMillis;
    }
}

