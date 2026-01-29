package com.oceanbase.powermem.sdk.intelligence;

/**
 * Facade/manager for intelligent memory features (pure Java core migration target).
 *
 * <p>Python reference: {@code src/powermem/intelligence/manager.py}</p>
 */
public class IntelligenceManager {
    private final com.oceanbase.powermem.sdk.config.IntelligentMemoryConfig config;
    private final EbbinghausAlgorithm ebbinghaus = new EbbinghausAlgorithm();

    public IntelligenceManager(com.oceanbase.powermem.sdk.config.IntelligentMemoryConfig config) {
        this.config = config;
    }

    public boolean isEnabled() {
        return config != null && config.isEnabled() && config.isDecayEnabled();
    }

    public java.util.List<com.oceanbase.powermem.sdk.model.SearchMemoriesResponse.SearchResult> postProcess(
            java.util.List<com.oceanbase.powermem.sdk.storage.base.OutputData> raw) {
        java.util.List<com.oceanbase.powermem.sdk.model.SearchMemoriesResponse.SearchResult> results = new java.util.ArrayList<>();
        if (raw == null) {
            return results;
        }
        java.time.Instant now = java.time.Instant.now();
        for (com.oceanbase.powermem.sdk.storage.base.OutputData d : raw) {
            if (d == null) {
                continue;
            }
            com.oceanbase.powermem.sdk.model.MemoryRecord r = d.getRecord();
            double score = d.getScore();
            if (isEnabled() && r != null) {
                // Python parity: decay by created_at, not last_accessed_at
                score = ebbinghaus.applyToScore(score, r.getCreatedAt(), now, config);
            }
            if (r == null) {
                continue;
            }
            java.util.Map<String, Object> meta = r.getMetadata() == null ? new java.util.HashMap<>() : new java.util.HashMap<>(r.getMetadata());
            // Python parity: category is promoted to top-level payload but should appear in returned metadata.
            if (r.getCategory() != null && !r.getCategory().isBlank()) {
                meta.put("category", r.getCategory());
            }
            if (r.getScope() != null && !r.getScope().isBlank()) {
                meta.put("scope", r.getScope());
            }
            // Python parity: extra top-level payload fields should be visible to callers.
            // Examples: _fusion_info, importance_score, memory_type, access_count...
            if (r.getAttributes() != null && !r.getAttributes().isEmpty()) {
                for (java.util.Map.Entry<String, Object> e : r.getAttributes().entrySet()) {
                    if (e == null || e.getKey() == null || e.getKey().isBlank()) {
                        continue;
                    }
                    // Do not overwrite user metadata unless caller explicitly set the key.
                    meta.putIfAbsent(e.getKey(), e.getValue());
                }
            }
            com.oceanbase.powermem.sdk.model.SearchMemoriesResponse.SearchResult sr =
                    new com.oceanbase.powermem.sdk.model.SearchMemoriesResponse.SearchResult(r.getContent(), meta, score);
            sr.setId(r.getId());
            sr.setUserId(r.getUserId());
            sr.setAgentId(r.getAgentId());
            sr.setRunId(r.getRunId());
            sr.setCreatedAt(r.getCreatedAt() == null ? null : r.getCreatedAt().toString());
            sr.setUpdatedAt(r.getUpdatedAt() == null ? null : r.getUpdatedAt().toString());
            results.add(sr);
        }
        results.sort(java.util.Comparator.comparingDouble(com.oceanbase.powermem.sdk.model.SearchMemoriesResponse.SearchResult::getScore).reversed());
        return results;
    }
}

