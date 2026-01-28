package com.oceanbase.powermem.sdk.model;

/**
 * Request DTO for retrieving a memory by id.
 *
 * <p>Python reference: {@code Memory.get(...)} in {@code src/powermem/core/memory.py}.</p>
 */
public class GetMemoryRequest {
    private String memoryId;
    private String userId;
    private String agentId;

    public GetMemoryRequest() {}

    public GetMemoryRequest(String memoryId, String userId, String agentId) {
        this.memoryId = memoryId;
        this.userId = userId;
        this.agentId = agentId;
    }

    public String getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(String memoryId) {
        this.memoryId = memoryId;
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
}

