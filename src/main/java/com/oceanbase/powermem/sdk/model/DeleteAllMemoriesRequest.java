package com.oceanbase.powermem.sdk.model;

/**
 * Request DTO for bulk deletion by user_id/agent_id/run_id.
 *
 * <p>Python reference: {@code Memory.delete_all(...)} in {@code src/powermem/core/memory.py}.</p>
 */
public class DeleteAllMemoriesRequest {
    private String userId;
    private String agentId;
    private String runId;

    public DeleteAllMemoriesRequest() {}

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
}

