package com.oceanbase.powermem.sdk.client;

/**
 * High-level Java SDK facade.
 *
 * <p>In the pure-Java-core migration direction, this facade should primarily delegate to
 * {@link com.oceanbase.powermem.sdk.core.Memory}/{@link com.oceanbase.powermem.sdk.core.AsyncMemory} and provide a
 * convenient, Java-idiomatic entry point (builder/config, thread-safe reuse, etc.).</p>
 *
 * <p>Closest Python reference: {@code src/powermem/__init__.py} ({@code create_memory/from_config})</p>
 */
public class PowermemClient {
    private final com.oceanbase.powermem.sdk.config.MemoryConfig config;
    private final com.oceanbase.powermem.sdk.core.Memory memory;
    private final com.oceanbase.powermem.sdk.core.AsyncMemory asyncMemory;

    public PowermemClient(com.oceanbase.powermem.sdk.config.MemoryConfig config) {
        this.config = config == null ? new com.oceanbase.powermem.sdk.config.MemoryConfig() : config;
        this.memory = new com.oceanbase.powermem.sdk.core.Memory(this.config);
        this.asyncMemory = new com.oceanbase.powermem.sdk.core.AsyncMemory(this.config);
    }

    public static PowermemClientBuilder builder() {
        return PowermemClientBuilder.builder();
    }

    public com.oceanbase.powermem.sdk.config.MemoryConfig getConfig() {
        return config;
    }

    public com.oceanbase.powermem.sdk.core.Memory memory() {
        return memory;
    }

    public com.oceanbase.powermem.sdk.core.AsyncMemory asyncMemory() {
        return asyncMemory;
    }
}

