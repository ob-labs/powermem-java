package com.oceanbase.powermem.sdk.config;

/**
 * Graph store configuration (pure Java core migration target).
 *
 * <p>Python reference: {@code src/powermem/storage/configs.py} and OceanBase graph store modules.</p>
 */
public class GraphStoreConfig {
    private boolean enabled = false;
    private String provider = "oceanbase";
    private String host = "127.0.0.1";
    private int port = 2881;
    private String user = "root";
    private String password;
    private String database = "powermem";

    public GraphStoreConfig() {}

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }
}

