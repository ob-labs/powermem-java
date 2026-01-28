package com.oceanbase.powermem.sdk.config;

/**
 * Telemetry configuration (enablement, endpoint, batching).
 *
 * <p>Python reference: {@code src/powermem/configs.py} (TelemetryConfig)</p>
 */
public class TelemetryConfig {
    private boolean enableTelemetry = false;
    private String telemetryEndpoint = "https://telemetry.powermem.ai";
    private String telemetryApiKey;
    private int batchSize = 100;
    private int flushIntervalSeconds = 30;

    public TelemetryConfig() {}

    public boolean isEnableTelemetry() {
        return enableTelemetry;
    }

    public void setEnableTelemetry(boolean enableTelemetry) {
        this.enableTelemetry = enableTelemetry;
    }

    public String getTelemetryEndpoint() {
        return telemetryEndpoint;
    }

    public void setTelemetryEndpoint(String telemetryEndpoint) {
        this.telemetryEndpoint = telemetryEndpoint;
    }

    public String getTelemetryApiKey() {
        return telemetryApiKey;
    }

    public void setTelemetryApiKey(String telemetryApiKey) {
        this.telemetryApiKey = telemetryApiKey;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getFlushIntervalSeconds() {
        return flushIntervalSeconds;
    }

    public void setFlushIntervalSeconds(int flushIntervalSeconds) {
        this.flushIntervalSeconds = flushIntervalSeconds;
    }
}

