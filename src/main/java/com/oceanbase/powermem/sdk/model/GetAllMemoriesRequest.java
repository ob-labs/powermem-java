package com.oceanbase.powermem.sdk.model;

/**
 * Request DTO for listing memories with pagination and filters.
 *
 * <p>Python reference: {@code Memory.get_all(...)} in {@code src/powermem/core/memory.py}.</p>
 */
public class GetAllMemoriesRequest {
    private String userId;
    private String agentId;
    private String runId;
    private int offset = 0;
    private int limit = 100;

    public GetAllMemoriesRequest() {}

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

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }
}

