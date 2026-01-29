package com.oceanbase.powermem.sdk.config;

/**
 * Audit logging configuration.
 *
 * <p>Python reference: {@code src/powermem/configs.py} (AuditConfig)</p>
 */
public class AuditConfig {
    private boolean enabled = true;
    private String logFile = "./logs/audit.log";
    private String logLevel = "INFO";
    private int retentionDays = 90;
    private boolean compressLogs = true;
    private String logRotationSize = "100MB";

    public AuditConfig() {}

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getLogFile() {
        return logFile;
    }

    public void setLogFile(String logFile) {
        this.logFile = logFile;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public boolean isCompressLogs() {
        return compressLogs;
    }

    public void setCompressLogs(boolean compressLogs) {
        this.compressLogs = compressLogs;
    }

    public String getLogRotationSize() {
        return logRotationSize;
    }

    public void setLogRotationSize(String logRotationSize) {
        this.logRotationSize = logRotationSize;
    }
}

