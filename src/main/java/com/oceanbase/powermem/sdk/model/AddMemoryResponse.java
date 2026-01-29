package com.oceanbase.powermem.sdk.model;

/**
 * Response DTO for add memory operations.
 *
 * <p>Python reference: return shape described in {@code Memory.add} docstring in
 * {@code src/powermem/core/memory.py}.</p>
 */
public class AddMemoryResponse {
    /**
     * Python-compatible shape: {"results":[...]}.
     */
    public static final class Result {
        @com.fasterxml.jackson.annotation.JsonProperty("id")
        private String id;
        @com.fasterxml.jackson.annotation.JsonProperty("memory")
        private String memory;
        @com.fasterxml.jackson.annotation.JsonProperty("event")
        private String event;
        @com.fasterxml.jackson.annotation.JsonProperty("user_id")
        private String userId;
        @com.fasterxml.jackson.annotation.JsonProperty("agent_id")
        private String agentId;
        @com.fasterxml.jackson.annotation.JsonProperty("run_id")
        private String runId;
        @com.fasterxml.jackson.annotation.JsonProperty("metadata")
        private java.util.Map<String, Object> metadata;
        @com.fasterxml.jackson.annotation.JsonProperty("created_at")
        private String createdAt;
        @com.fasterxml.jackson.annotation.JsonProperty("previous_memory")
        private String previousMemory;

        public Result() {}

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getMemory() {
            return memory;
        }

        public void setMemory(String memory) {
            this.memory = memory;
        }

        public String getEvent() {
            return event;
        }

        public void setEvent(String event) {
            this.event = event;
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

        public String getRunId() {
            return runId;
        }

        public void setRunId(String runId) {
            this.runId = runId;
        }

        public java.util.Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(java.util.Map<String, Object> metadata) {
            this.metadata = metadata;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }

        public String getPreviousMemory() {
            return previousMemory;
        }

        public void setPreviousMemory(String previousMemory) {
            this.previousMemory = previousMemory;
        }
    }

    private java.util.List<Result> results = new java.util.ArrayList<>();
    /**
     * Python-compatible: {"action_counts":{"ADD":1,"UPDATE":0,"DELETE":0,"NONE":2}}
     */
    @com.fasterxml.jackson.annotation.JsonProperty("action_counts")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.util.Map<String, Integer> actionCounts;

    /**
     * Python-compatible: {"relations": {...}} (graph store; reserved for future).
     */
    @com.fasterxml.jackson.annotation.JsonProperty("relations")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Object relations;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private java.util.List<MemoryRecord> memories = new java.util.ArrayList<>();

    public AddMemoryResponse() {}

    public AddMemoryResponse(java.util.List<MemoryRecord> memories) {
        if (memories != null) {
            this.memories = memories;
        }
    }

    public java.util.List<MemoryRecord> getMemories() {
        return memories;
    }

    public void setMemories(java.util.List<MemoryRecord> memories) {
        this.memories = memories == null ? new java.util.ArrayList<>() : memories;
    }

    public java.util.List<Result> getResults() {
        return results;
    }

    public void setResults(java.util.List<Result> results) {
        this.results = results == null ? new java.util.ArrayList<>() : results;
    }

    public java.util.Map<String, Integer> getActionCounts() {
        return actionCounts;
    }

    public void setActionCounts(java.util.Map<String, Integer> actionCounts) {
        this.actionCounts = actionCounts == null ? new java.util.HashMap<>() : actionCounts;
    }

    public Object getRelations() {
        return relations;
    }

    public void setRelations(Object relations) {
        this.relations = relations;
    }
}

