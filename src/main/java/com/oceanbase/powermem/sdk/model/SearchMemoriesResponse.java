package com.oceanbase.powermem.sdk.model;

/**
 * Response DTO for semantic search.
 *
 * <p>Python reference: return shape in {@code Memory.search} docstring in
 * {@code src/powermem/core/memory.py}.</p>
 */
public class SearchMemoriesResponse {
    public static final class SearchResult {
        /**
         * Python-compatible field: "memory" is a string.
         */
        @com.fasterxml.jackson.annotation.JsonProperty("memory")
        private String memory;
        /**
         * Python-compatible field: nested metadata object.
         */
        @com.fasterxml.jackson.annotation.JsonProperty("metadata")
        private java.util.Map<String, Object> metadata;
        @com.fasterxml.jackson.annotation.JsonProperty("score")
        private double score;
        @com.fasterxml.jackson.annotation.JsonProperty("id")
        private String id;
        @com.fasterxml.jackson.annotation.JsonProperty("created_at")
        private String createdAt;
        @com.fasterxml.jackson.annotation.JsonProperty("updated_at")
        private String updatedAt;
        @com.fasterxml.jackson.annotation.JsonProperty("user_id")
        private String userId;
        @com.fasterxml.jackson.annotation.JsonProperty("agent_id")
        private String agentId;
        @com.fasterxml.jackson.annotation.JsonProperty("run_id")
        private String runId;

        public SearchResult() {}

        public SearchResult(String memory, java.util.Map<String, Object> metadata, double score) {
            this.memory = memory;
            this.metadata = metadata;
            this.score = score;
        }

        public String getMemory() {
            return memory;
        }

        public void setMemory(String memory) {
            this.memory = memory;
        }

        public java.util.Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(java.util.Map<String, Object> metadata) {
            this.metadata = metadata;
        }

        public double getScore() {
            return score;
        }

        public void setScore(double score) {
            this.score = score;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }

        public String getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(String updatedAt) {
            this.updatedAt = updatedAt;
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
    }

    @com.fasterxml.jackson.annotation.JsonProperty("results")
    private java.util.List<SearchResult> results = new java.util.ArrayList<>();

    @com.fasterxml.jackson.annotation.JsonProperty("relations")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Object relations;

    public SearchMemoriesResponse() {}

    public SearchMemoriesResponse(java.util.List<SearchResult> results) {
        if (results != null) {
            this.results = results;
        }
    }

    public java.util.List<SearchResult> getResults() {
        return results;
    }

    public void setResults(java.util.List<SearchResult> results) {
        this.results = results == null ? new java.util.ArrayList<>() : results;
    }

    public Object getRelations() {
        return relations;
    }

    public void setRelations(Object relations) {
        this.relations = relations;
    }
}

