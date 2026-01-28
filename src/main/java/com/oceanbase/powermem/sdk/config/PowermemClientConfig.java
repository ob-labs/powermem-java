package com.oceanbase.powermem.sdk.config;

/**
 * SDK client-level configuration (endpoints, timeouts, retries, auth, etc.).
 *
 * <p>For a pure Java core migration, the "client" becomes an in-process orchestrator, but config still
 * needs to express provider selections and connection parameters.</p>
 *
 * <p>Closest Python reference: {@code src/powermem/config_loader.py} and {@code src/powermem/configs.py}</p>
 */
public class PowermemClientConfig {
    private String baseUrl = "http://localhost:8080";
    private String userAgent = "powermem-java-sdk";
    private AuthConfig auth = new AuthConfig();
    private TimeoutConfig timeout = new TimeoutConfig();
    private RetryConfig retry = new RetryConfig();

    public PowermemClientConfig() {}

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public AuthConfig getAuth() {
        return auth;
    }

    public void setAuth(AuthConfig auth) {
        this.auth = auth;
    }

    public TimeoutConfig getTimeout() {
        return timeout;
    }

    public void setTimeout(TimeoutConfig timeout) {
        this.timeout = timeout;
    }

    public RetryConfig getRetry() {
        return retry;
    }

    public void setRetry(RetryConfig retry) {
        this.retry = retry;
    }
}

