package com.oceanbase.powermem.sdk.model;

/**
 * Request DTO for semantic search.
 *
 * <p>Python reference: {@code Memory.search(...)} in {@code src/powermem/core/memory.py} and
 * REST request model {@code SearchRequest} in {@code benchmark/server/main.py}.</p>
 */
public class SearchMemoriesRequest {
    @com.fasterxml.jackson.annotation.JsonProperty("query")
    private String query;
    @com.fasterxml.jackson.annotation.JsonProperty("user_id")
    private String userId;
    @com.fasterxml.jackson.annotation.JsonProperty("agent_id")
    private String agentId;
    @com.fasterxml.jackson.annotation.JsonProperty("run_id")
    private String runId;
    // Python search has limit=30 by default; benchmark server doesn't send it, but we support it.
    @com.fasterxml.jackson.annotation.JsonProperty("limit")
    private Integer limit;
    @com.fasterxml.jackson.annotation.JsonProperty("threshold")
    private Double threshold;
    private int topK = 5;
    @com.fasterxml.jackson.annotation.JsonProperty("filters")
    private java.util.Map<String, Object> filters;

    public SearchMemoriesRequest() {}

    public static SearchMemoriesRequest ofQuery(String query, String userId) {
        SearchMemoriesRequest r = new SearchMemoriesRequest();
        r.setQuery(query);
        r.setUserId(userId);
        return r;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
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

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public Double getThreshold() {
        return threshold;
    }

    public void setThreshold(Double threshold) {
        this.threshold = threshold;
    }

    public java.util.Map<String, Object> getFilters() {
        return filters;
    }

    public void setFilters(java.util.Map<String, Object> filters) {
        this.filters = filters;
    }
}

