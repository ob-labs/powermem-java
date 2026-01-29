package com.oceanbase.powermem.sdk.model;

/**
 * Response DTO for listing memories.
 *
 * <p>Python reference: return shape in {@code Memory.get_all} docstring in
 * {@code src/powermem/core/memory.py}.</p>
 */
public class GetAllMemoriesResponse {
    /**
     * Python-compatible shape: {"results":[...]}.
     */
    @com.fasterxml.jackson.annotation.JsonProperty("results")
    private java.util.List<java.util.Map<String, Object>> results = new java.util.ArrayList<>();

    @com.fasterxml.jackson.annotation.JsonProperty("relations")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Object relations;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private java.util.List<MemoryRecord> memories = new java.util.ArrayList<>();

    public GetAllMemoriesResponse() {}

    public GetAllMemoriesResponse(java.util.List<MemoryRecord> memories) {
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

    public java.util.List<java.util.Map<String, Object>> getResults() {
        return results;
    }

    public void setResults(java.util.List<java.util.Map<String, Object>> results) {
        this.results = results == null ? new java.util.ArrayList<>() : results;
    }

    public Object getRelations() {
        return relations;
    }

    public void setRelations(Object relations) {
        this.relations = relations;
    }
}

