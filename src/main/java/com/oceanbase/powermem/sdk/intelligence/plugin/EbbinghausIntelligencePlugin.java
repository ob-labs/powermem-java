package com.oceanbase.powermem.sdk.intelligence.plugin;

/**
 * Ebbinghaus-based intelligent memory plugin implementation.
 *
 * <p>Python reference: {@code src/powermem/intelligence/plugin.py} (EbbinghausIntelligencePlugin)</p>
 */
public class EbbinghausIntelligencePlugin implements IntelligentMemoryPlugin {
    private final com.oceanbase.powermem.sdk.config.IntelligentMemoryConfig config;
    private final com.oceanbase.powermem.sdk.intelligence.ImportanceEvaluator importance = new com.oceanbase.powermem.sdk.intelligence.ImportanceEvaluator();
    private final com.oceanbase.powermem.sdk.intelligence.EbbinghausAlgorithm algo = new com.oceanbase.powermem.sdk.intelligence.EbbinghausAlgorithm();

    public EbbinghausIntelligencePlugin(com.oceanbase.powermem.sdk.config.IntelligentMemoryConfig config) {
        this.config = config;
    }

    @Override
    public boolean isEnabled() {
        return config != null && config.isEnabled();
    }

    private String classify(double score) {
        if (score >= config.getLongTermThreshold()) {
            return "long_term";
        }
        if (score >= config.getShortTermThreshold()) {
            return "short_term";
        }
        return "working";
    }

    @Override
    public java.util.Map<String, Object> onAdd(String content, java.util.Map<String, Object> metadata) {
        if (!isEnabled()) {
            return java.util.Collections.emptyMap();
        }
        double score = importance.evaluateImportance(content, metadata);
        String memoryType = classify(score);
        java.util.Map<String, Object> intelligenceMetadata = algo.processMemoryMetadata(content, score, memoryType, config);

        java.util.Map<String, Object> out = new java.util.HashMap<>();
        out.put("importance_score", score);
        out.put("memory_type", memoryType);
        out.put("access_count", 0);
        Object intel = intelligenceMetadata.get("intelligence");
        Object mm = intelligenceMetadata.get("memory_management");
        out.put("intelligence", intel instanceof java.util.Map ? intel : new java.util.HashMap<>());
        out.put("memory_management", mm instanceof java.util.Map ? mm : new java.util.HashMap<>());
        out.put("processing_applied", true);
        return out;
    }

    @Override
    public OnGetResult onGet(java.util.Map<String, Object> memoryPayload) {
        if (!isEnabled() || memoryPayload == null) {
            return new OnGetResult(null, false);
        }
        java.time.Instant now = java.time.Instant.now();
        int accessCount = asInt(memoryPayload.get("access_count"), 0) + 1;
        double importanceScore = asDouble(memoryPayload.get("importance_score"), 0.5);
        String memoryType = asString(memoryPayload.get("memory_type"));

        java.time.Instant createdAt = parseInstant(memoryPayload.get("created_at"));

        // should forget?
        if (algo.shouldForget(createdAt, accessCount - 1, now, config)) {
            return new OnGetResult(null, true);
        }

        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("access_count", accessCount);
        updates.put("updated_at", now.toString());

        // promote?
        if (algo.shouldPromote(createdAt, accessCount - 1, importanceScore, now, config)) {
            if ("working".equals(memoryType)) {
                updates.put("memory_type", "short_term");
            } else if ("short_term".equals(memoryType)) {
                updates.put("memory_type", "long_term");
            }
        }

        // archive?
        if (algo.shouldArchive(createdAt, importanceScore, now, config)) {
            java.util.Map<String, Object> meta = safeMap(memoryPayload.get("metadata"));
            meta.put("archived", true);
            updates.put("metadata", meta);
        }

        // reprocess if type changed or every 5 accesses
        String nextType = updates.get("memory_type") == null ? memoryType : String.valueOf(updates.get("memory_type"));
        if ((nextType != null && memoryType != null && !nextType.equals(memoryType)) || (accessCount % 5 == 0)) {
            java.util.Map<String, Object> intelligenceMetadata =
                    algo.processMemoryMetadata(asString(memoryPayload.get("content")), importanceScore, nextType, config);
            Object intel = intelligenceMetadata.get("intelligence");
            Object mm = intelligenceMetadata.get("memory_management");
            if (intel instanceof java.util.Map) {
                updates.put("intelligence", intel);
            }
            if (mm instanceof java.util.Map) {
                updates.put("memory_management", mm);
            }
            updates.put("last_reprocessed_at", now.toString());
            updates.put("processing_applied", true);
        }

        return new OnGetResult(updates, false);
    }

    @Override
    public OnSearchResult onSearch(java.util.List<java.util.Map<String, Object>> memoryPayloads) {
        if (!isEnabled() || memoryPayloads == null) {
            return new OnSearchResult(java.util.Collections.emptyList(), java.util.Collections.emptyList());
        }
        java.util.List<java.util.Map.Entry<String, java.util.Map<String, Object>>> updates = new java.util.ArrayList<>();
        java.util.List<String> deletes = new java.util.ArrayList<>();
        for (java.util.Map<String, Object> item : memoryPayloads) {
            if (item == null) {
                continue;
            }
            String memId = asString(item.get("id"));
            if (memId == null) {
                memId = asString(item.get("memory_id"));
            }
            if (memId == null) {
                continue;
            }
            OnGetResult r = onGet(item);
            if (r.isDelete()) {
                deletes.add(memId);
                continue;
            }
            if (r.getUpdates() == null || r.getUpdates().isEmpty()) {
                continue;
            }
            java.util.Map<String, Object> enhanced = enhanceForSearch(item, r.getUpdates());
            updates.add(new java.util.AbstractMap.SimpleEntry<>(memId, enhanced));
        }
        return new OnSearchResult(updates, deletes);
    }

    private java.util.Map<String, Object> enhanceForSearch(java.util.Map<String, Object> memory,
                                                           java.util.Map<String, Object> baseUpdates) {
        java.time.Instant now = java.time.Instant.now();
        java.util.Map<String, Object> meta = safeMap(memory.get("metadata"));
        meta.put("last_searched_at", now.toString());
        meta.put("search_count", asInt(meta.get("search_count"), 0) + 1);

        java.util.Map<String, Object> enhanced = new java.util.HashMap<>(baseUpdates);
        enhanced.put("metadata", meta);

        if (!memory.containsKey("search_relevance_score")) {
            int access = asInt(memory.get("access_count"), 0);
            double importanceScore = asDouble(memory.get("importance_score"), 0.5);
            double searchRel = Math.min(1.0, (access * 0.1) + (importanceScore * 0.5));
            enhanced.put("search_relevance_score", searchRel);
        }
        return enhanced;
    }

    private static int asInt(Object v, int def) {
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        if (v == null) {
            return def;
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception ignored) {
            return def;
        }
    }

    private static double asDouble(Object v, double def) {
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        if (v == null) {
            return def;
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception ignored) {
            return def;
        }
    }

    private static String asString(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v);
        return s.isBlank() ? null : s;
    }

    private static java.time.Instant parseInstant(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof String) {
            String s = (String) v;
            if (s.isBlank()) {
                return null;
            }
            try {
                return java.time.Instant.parse(s);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static java.util.Map<String, Object> safeMap(Object v) {
        java.util.Map<String, Object> out = new java.util.HashMap<>();
        if (v instanceof java.util.Map) {
            java.util.Map<?, ?> raw = (java.util.Map<?, ?>) v;
            for (java.util.Map.Entry<?, ?> e : raw.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return out;
    }
}

