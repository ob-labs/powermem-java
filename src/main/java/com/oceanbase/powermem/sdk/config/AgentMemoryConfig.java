package com.oceanbase.powermem.sdk.config;

/**
 * Agent memory configuration (multi-agent/multi-user/hybrid modes, scope/privacy defaults).
 *
 * <p>Python reference: {@code src/powermem/configs.py} (AgentMemoryConfig) and {@code src/powermem/agent/*}</p>
 */
public class AgentMemoryConfig {
    private boolean enabled = true;
    private String mode = "multi_agent";
    private String defaultScope = "private";
    private String defaultPrivacyLevel = "standard";
    private String defaultCollaborationLevel = "isolated";
    private String defaultAccessPermission = "read";
    private boolean enableCollaboration = true;

    public AgentMemoryConfig() {}

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getDefaultScope() {
        return defaultScope;
    }

    public void setDefaultScope(String defaultScope) {
        this.defaultScope = defaultScope;
    }

    public String getDefaultPrivacyLevel() {
        return defaultPrivacyLevel;
    }

    public void setDefaultPrivacyLevel(String defaultPrivacyLevel) {
        this.defaultPrivacyLevel = defaultPrivacyLevel;
    }

    public String getDefaultCollaborationLevel() {
        return defaultCollaborationLevel;
    }

    public void setDefaultCollaborationLevel(String defaultCollaborationLevel) {
        this.defaultCollaborationLevel = defaultCollaborationLevel;
    }

    public String getDefaultAccessPermission() {
        return defaultAccessPermission;
    }

    public void setDefaultAccessPermission(String defaultAccessPermission) {
        this.defaultAccessPermission = defaultAccessPermission;
    }

    public boolean isEnableCollaboration() {
        return enableCollaboration;
    }

    public void setEnableCollaboration(boolean enableCollaboration) {
        this.enableCollaboration = enableCollaboration;
    }
}

