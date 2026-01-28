package com.oceanbase.powermem.sdk.storage.adapter;

/**
 * Storage adapter bridging memory orchestration and the underlying vector store / embedding service.
 *
 * <p>In Python, this adapter owns the CRUD glue code and normalizes provider-specific behavior into a
 * consistent shape for {@code Memory}/{@code AsyncMemory}.</p>
 *
 * <p>Python reference: {@code src/powermem/storage/adapter.py}</p>
 */
public class StorageAdapter {
    private final com.oceanbase.powermem.sdk.storage.base.VectorStore vectorStore;
    private final com.oceanbase.powermem.sdk.integrations.embeddings.Embedder embedder;
    private final com.oceanbase.powermem.sdk.util.SnowflakeIdGenerator idGenerator = com.oceanbase.powermem.sdk.util.SnowflakeIdGenerator.defaultGenerator();

    public StorageAdapter(com.oceanbase.powermem.sdk.storage.base.VectorStore vectorStore,
                          com.oceanbase.powermem.sdk.integrations.embeddings.Embedder embedder) {
        this.vectorStore = vectorStore;
        this.embedder = embedder;
    }

    public com.oceanbase.powermem.sdk.model.MemoryRecord addMemory(String content,
                                                         String userId,
                                                         String agentId,
                                                         String runId,
                                                         java.util.Map<String, Object> metadata) {
        return addMemory(content, userId, agentId, runId, metadata, null, null, null);
    }

    public com.oceanbase.powermem.sdk.model.MemoryRecord addMemory(String content,
                                                         String userId,
                                                         String agentId,
                                                         String runId,
                                                         java.util.Map<String, Object> metadata,
                                                         java.util.Map<String, Object> attributes) {
        return addMemory(content, userId, agentId, runId, metadata, attributes, null, null);
    }

    public com.oceanbase.powermem.sdk.model.MemoryRecord addMemory(String content,
                                                                   String userId,
                                                                   String agentId,
                                                                   String runId,
                                                                   java.util.Map<String, Object> metadata,
                                                                   java.util.Map<String, Object> attributes,
                                                                   String scope,
                                                                   String memoryType) {
        if (content == null || content.isBlank()) {
            throw new com.oceanbase.powermem.sdk.exception.ApiException("Cannot store empty content");
        }
        if (userId == null || userId.isBlank()) {
            throw new com.oceanbase.powermem.sdk.exception.ApiException("userId is required");
        }
        java.time.Instant now = java.time.Instant.now();
        com.oceanbase.powermem.sdk.model.MemoryRecord record = new com.oceanbase.powermem.sdk.model.MemoryRecord();
        record.setId(idGenerator.nextId());
        record.setContent(content);
        record.setUserId(userId);
        record.setAgentId(agentId);
        record.setRunId(runId);
        // Python parity: promote "category" (and potentially other simple fields) to top-level payload
        java.util.Map<String, Object> safeMeta = metadata == null ? new java.util.HashMap<>() : new java.util.HashMap<>(metadata);
        Object category = safeMeta.get("category");
        if (category != null && !String.valueOf(category).isBlank()) {
            record.setCategory(String.valueOf(category));
            safeMeta.remove("category");
        }
        record.setMetadata(safeMeta);
        java.util.Map<String, Object> attrs = attributes == null ? null : new java.util.HashMap<>(attributes);
        if (memoryType != null && !memoryType.isBlank()) {
            if (attrs == null) {
                attrs = new java.util.HashMap<>();
            }
            // Python parity: memory_type can be forced by API param
            attrs.put("memory_type", memoryType);
        }
        record.setAttributes(attrs);
        if (scope != null && !scope.isBlank()) {
            record.setScope(scope);
        }
        record.setHash(com.oceanbase.powermem.sdk.util.PowermemUtils.md5Hex(content));
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        record.setLastAccessedAt(now);

        float[] vec = embedder.embed(content, "add");
        vectorStore.upsert(record, vec);
        return record;
    }

    public java.util.List<com.oceanbase.powermem.sdk.storage.base.OutputData> searchMemories(float[] queryEmbedding,
                                                                                   int limit,
                                                                                   String userId,
                                                                                   String agentId,
                                                                                   String runId,
                                                                                   java.util.Map<String, Object> filters) {
        return searchMemories(null, queryEmbedding, limit, userId, agentId, runId, filters);
    }

    /**
     * Search memories with optional query text (for OceanBase hybrid search).
     *
     * <p>Python parity: OceanBase supports hybrid (vector + full-text) search when query text is present.</p>
     */
    public java.util.List<com.oceanbase.powermem.sdk.storage.base.OutputData> searchMemories(String queryText,
                                                                                   float[] queryEmbedding,
                                                                                   int limit,
                                                                                   String userId,
                                                                                   String agentId,
                                                                                   String runId,
                                                                                   java.util.Map<String, Object> filters) {
        if (vectorStore instanceof com.oceanbase.powermem.sdk.storage.oceanbase.OceanBaseVectorStore) {
            return ((com.oceanbase.powermem.sdk.storage.oceanbase.OceanBaseVectorStore) vectorStore)
                    .searchHybrid(queryText, queryEmbedding, limit, userId, agentId, runId, filters);
        }
        return vectorStore.search(queryEmbedding, limit, userId, agentId, runId, filters);
    }

    public com.oceanbase.powermem.sdk.model.MemoryRecord getMemory(String memoryId, String userId, String agentId) {
        return vectorStore.get(memoryId, userId, agentId);
    }

    public com.oceanbase.powermem.sdk.model.MemoryRecord updateMemory(String memoryId,
                                                            String newContent,
                                                            String userId,
                                                            String agentId,
                                                            java.util.Map<String, Object> metadata) {
        com.oceanbase.powermem.sdk.model.MemoryRecord existing = vectorStore.get(memoryId, userId, agentId);
        if (existing == null) {
            return null;
        }
        if (newContent != null && !newContent.isBlank()) {
            existing.setContent(newContent);
            existing.setHash(com.oceanbase.powermem.sdk.util.PowermemUtils.md5Hex(newContent));
        }
        if (metadata != null) {
            existing.setMetadata(metadata);
        }
        existing.setUpdatedAt(java.time.Instant.now());
        float[] vec = embedder.embed(existing.getContent() == null ? "" : existing.getContent(), "update");
        vectorStore.upsert(existing, vec);
        return existing;
    }

    /**
     * Update payload fields without changing content (for intelligent lifecycle hooks).
     * Uses SQLite fast-path when available; otherwise falls back to upsert.
     */
    public void updatePayloadFields(String memoryId,
                                    String userId,
                                    String agentId,
                                    java.util.Map<String, Object> fieldUpdates) {
        if (memoryId == null || memoryId.isBlank() || fieldUpdates == null || fieldUpdates.isEmpty()) {
            return;
        }
        if (vectorStore instanceof com.oceanbase.powermem.sdk.storage.sqlite.SQLiteVectorStore) {
            ((com.oceanbase.powermem.sdk.storage.sqlite.SQLiteVectorStore) vectorStore).updatePayloadFields(memoryId, fieldUpdates);
            return;
        }
        if (vectorStore instanceof com.oceanbase.powermem.sdk.storage.oceanbase.OceanBaseVectorStore) {
            ((com.oceanbase.powermem.sdk.storage.oceanbase.OceanBaseVectorStore) vectorStore).updatePayloadFields(memoryId, fieldUpdates);
            return;
        }
        com.oceanbase.powermem.sdk.model.MemoryRecord existing = vectorStore.get(memoryId, userId, agentId);
        if (existing == null) {
            return;
        }
        if (existing.getAttributes() == null) {
            existing.setAttributes(new java.util.HashMap<>());
        }
        for (java.util.Map.Entry<String, Object> e : fieldUpdates.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank()) {
                continue;
            }
            if ("metadata".equals(e.getKey()) && e.getValue() instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> m = (java.util.Map<String, Object>) e.getValue();
                existing.setMetadata(m);
            } else if ("updated_at".equals(e.getKey()) || "created_at".equals(e.getKey())) {
                // ignore - timestamps handled below / never override created_at
            } else {
                existing.getAttributes().put(e.getKey(), e.getValue());
            }
        }
        existing.setUpdatedAt(java.time.Instant.now());
        float[] vec = embedder.embed(existing.getContent() == null ? "" : existing.getContent(), "update");
        vectorStore.upsert(existing, vec);
    }

    public boolean deleteMemory(String memoryId, String userId, String agentId) {
        return vectorStore.delete(memoryId, userId, agentId);
    }

    public java.util.List<com.oceanbase.powermem.sdk.model.MemoryRecord> getAllMemories(String userId,
                                                                              String agentId,
                                                                              String runId,
                                                                              int limit,
                                                                              int offset) {
        return vectorStore.list(userId, agentId, runId, offset, limit);
    }

    public int clearMemories(String userId, String agentId, String runId) {
        return vectorStore.deleteAll(userId, agentId, runId);
    }
}

