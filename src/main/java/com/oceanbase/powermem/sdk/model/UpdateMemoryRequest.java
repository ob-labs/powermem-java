package com.oceanbase.powermem.sdk.model;

/**
 * Request DTO for updating memory content and/or metadata.
 *
 * <p>Python reference: {@code Memory.update(...)} in {@code src/powermem/core/memory.py}.</p>
 */
public class UpdateMemoryRequest {
    private String memoryId;
    private String newContent;
    private String userId;
    private String agentId;
    private java.util.Map<String, Object> metadata;

    public UpdateMemoryRequest() {}

    public String getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(String memoryId) {
        this.memoryId = memoryId;
    }

    public String getNewContent() {
        return newContent;
    }

    public void setNewContent(String newContent) {
        this.newContent = newContent;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public java.util.Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(java.util.Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}

