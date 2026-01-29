package com.oceanbase.powermem.sdk.config;

/**
 * Application logging configuration (level/format/output).
 *
 * <p>Python reference: {@code src/powermem/configs.py} (LoggingConfig)</p>
 */
public class LoggingConfig {
    private String level = "DEBUG";
    private String format = "%(asctime)s - %(name)s - %(levelname)s - %(message)s";
    private String file = "./logs/powermem.log";
    private String maxSize = "100MB";
    private int backupCount = 5;
    private boolean compressBackups = true;

    private boolean consoleEnabled = true;
    private String consoleLevel = "INFO";
    private String consoleFormat = "%(levelname)s - %(message)s";

    public LoggingConfig() {}

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public String getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(String maxSize) {
        this.maxSize = maxSize;
    }

    public int getBackupCount() {
        return backupCount;
    }

    public void setBackupCount(int backupCount) {
        this.backupCount = backupCount;
    }

    public boolean isCompressBackups() {
        return compressBackups;
    }

    public void setCompressBackups(boolean compressBackups) {
        this.compressBackups = compressBackups;
    }

    public boolean isConsoleEnabled() {
        return consoleEnabled;
    }

    public void setConsoleEnabled(boolean consoleEnabled) {
        this.consoleEnabled = consoleEnabled;
    }

    public String getConsoleLevel() {
        return consoleLevel;
    }

    public void setConsoleLevel(String consoleLevel) {
        this.consoleLevel = consoleLevel;
    }

    public String getConsoleFormat() {
        return consoleFormat;
    }

    public void setConsoleFormat(String consoleFormat) {
        this.consoleFormat = consoleFormat;
    }
}

