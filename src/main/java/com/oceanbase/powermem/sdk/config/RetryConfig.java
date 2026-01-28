package com.oceanbase.powermem.sdk.config;

/**
 * Retry policy configuration for outbound calls (LLM/embedding/remote APIs).
 *
 * <p>No direct Python equivalent; Python typically relies on the underlying client defaults or custom
 * retry wrappers per integration.</p>
 */
public class RetryConfig {
    private int maxAttempts = 3;
    private long backoffMillis = 500;
    private long maxBackoffMillis = 5000;
    private boolean jitter = true;

    public RetryConfig() {}

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getBackoffMillis() {
        return backoffMillis;
    }

    public void setBackoffMillis(long backoffMillis) {
        this.backoffMillis = backoffMillis;
    }

    public long getMaxBackoffMillis() {
        return maxBackoffMillis;
    }

    public void setMaxBackoffMillis(long maxBackoffMillis) {
        this.maxBackoffMillis = maxBackoffMillis;
    }

    public boolean isJitter() {
        return jitter;
    }

    public void setJitter(boolean jitter) {
        this.jitter = jitter;
    }
}

