package com.oceanbase.powermem.sdk.client;

/**
 * Builder for constructing {@link PowermemClient} instances with validated configuration.
 *
 * <p>Python loads config via {@code auto_config/load_config_from_env} and then constructs {@code Memory}.
 * This builder is the Java-idiomatic counterpart.</p>
 *
 * <p>Python reference: {@code src/powermem/config_loader.py} and {@code src/powermem/__init__.py}</p>
 */
public class PowermemClientBuilder {
    private com.oceanbase.powermem.sdk.config.MemoryConfig config;

    public PowermemClientBuilder() {}

    public static PowermemClientBuilder builder() {
        return new PowermemClientBuilder();
    }

    public PowermemClientBuilder config(com.oceanbase.powermem.sdk.config.MemoryConfig config) {
        this.config = config;
        return this;
    }

    public PowermemClientBuilder fromEnv() {
        this.config = com.oceanbase.powermem.sdk.config.ConfigLoader.fromEnv();
        return this;
    }

    public PowermemClientBuilder fromDotEnvInResources() {
        this.config = com.oceanbase.powermem.sdk.config.ConfigLoader.fromDotEnvInResources();
        return this;
    }

    public PowermemClient build() {
        com.oceanbase.powermem.sdk.config.MemoryConfig cfg = config == null ? new com.oceanbase.powermem.sdk.config.MemoryConfig() : config;
        return new PowermemClient(cfg);
    }
}

