package com.oceanbase.powermem.sdk.config;

/**
 * Authentication configuration for external providers or remote services.
 *
 * <p>In Python, credentials are typically loaded via environment variables / {@code .env} and placed
 * into the config dict.</p>
 *
 * <p>Python reference: {@code src/powermem/config_loader.py}</p>
 */
public class AuthConfig {
    private String apiKey;
    private String bearerToken;
    private String username;
    private String password;

    public AuthConfig() {}

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBearerToken() {
        return bearerToken;
    }

    public void setBearerToken(String bearerToken) {
        this.bearerToken = bearerToken;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}

