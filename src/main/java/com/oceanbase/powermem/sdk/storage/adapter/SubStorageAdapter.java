package com.oceanbase.powermem.sdk.storage.adapter;

/**
 * Storage adapter for "sub stores" (data partitioning) support.
 *
 * <p>Sub-stores allow routing memories to different physical tables/collections based on metadata
 * filters (currently OceanBase-only in Python).</p>
 *
 * <p>Python reference: {@code src/powermem/storage/adapter.py} (SubStorageAdapter)</p>
 */
public class SubStorageAdapter extends StorageAdapter {
    private final java.util.List<SubStore> subStores = new java.util.ArrayList<>();
    private final java.util.Map<String, Boolean> readiness = new java.util.HashMap<>();

    public SubStorageAdapter(com.oceanbase.powermem.sdk.storage.base.VectorStore vectorStore,
                             com.oceanbase.powermem.sdk.integrations.embeddings.Embedder embedder) {
        super(vectorStore, embedder);
    }

    public void registerSubStore(String storeName,
                                 java.util.Map<String, Object> routingFilter,
                                 com.oceanbase.powermem.sdk.storage.base.VectorStore vectorStore,
                                 com.oceanbase.powermem.sdk.integrations.embeddings.Embedder embedder) {
        if (storeName == null || storeName.isBlank() || vectorStore == null) {
            return;
        }
        java.util.Map<String, Object> rf = routingFilter == null ? java.util.Collections.emptyMap() : new java.util.HashMap<>(routingFilter);
        this.subStores.add(new SubStore(storeName.trim(), rf, vectorStore, embedder));
        // Default to ready=true (Java doesn't implement DB-backed migration status yet).
        this.readiness.putIfAbsent(storeName.trim(), Boolean.TRUE);
    }

    /**
     * Python parity helper: list all registered sub store names.
     */
    public java.util.List<String> listSubStores() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (SubStore s : subStores) {
            if (s != null && s.name != null && !s.name.isBlank()) {
                names.add(s.name);
            }
        }
        return names;
    }

    /**
     * Python parity helper: returns the target store name for routing context.
     */
    public String getTargetStoreName(java.util.Map<String, Object> filtersOrMetadata) {
        SubStore s = routeToStore(filtersOrMetadata);
        return s == null ? null : s.name;
    }

    /**
     * Python parity helper: whether a sub store is ready for routing.
     *
     * <p>Python uses a DB-backed migration status table (COMPLETED means ready).
     * Java currently defaults to ready=true for all registered sub stores.</p>
     */
    public boolean isSubStoreReady(String storeName) {
        if (storeName == null || storeName.isBlank()) {
            return false;
        }
        Boolean b = readiness.get(storeName.trim());
        return b != null && b;
    }

    /**
     * Test/ops helper: toggle readiness for routing.
     */
    public void setSubStoreReady(String storeName, boolean ready) {
        if (storeName == null || storeName.isBlank()) {
            return;
        }
        readiness.put(storeName.trim(), ready);
    }

    private SubStore routeToStore(java.util.Map<String, Object> filtersOrMetadata) {
        if (subStores.isEmpty() || filtersOrMetadata == null || filtersOrMetadata.isEmpty()) {
            return null;
        }
        for (SubStore s : subStores) {
            if (s == null || s.routingFilter == null || s.routingFilter.isEmpty()) {
                continue;
            }
            // Python behavior: skip routing when sub store is not ready (migration not completed).
            if (!isSubStoreReady(s.name)) {
                continue;
            }
            boolean match = true;
            for (java.util.Map.Entry<String, Object> e : s.routingFilter.entrySet()) {
                String k = e.getKey();
                Object v = e.getValue();
                if (k == null) continue;
                Object actual = getValueIgnoreCase(filtersOrMetadata, k);
                if (actual == null && !containsKeyIgnoreCase(filtersOrMetadata, k)) {
                    match = false;
                    break;
                }
                if (v == null) {
                    if (actual != null) {
                        match = false;
                        break;
                    }
                } else if (!String.valueOf(v).equals(String.valueOf(actual))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return s;
            }
        }
        return null;
    }

    private static boolean containsKeyIgnoreCase(java.util.Map<String, Object> m, String key) {
        if (m == null || key == null) return false;
        if (m.containsKey(key)) return true;
        for (java.util.Map.Entry<String, Object> e : m.entrySet()) {
            if (e == null || e.getKey() == null) continue;
            if (e.getKey().equalsIgnoreCase(key)) return true;
        }
        return false;
    }

    private static Object getValueIgnoreCase(java.util.Map<String, Object> m, String key) {
        if (m == null || key == null) return null;
        if (m.containsKey(key)) return m.get(key);
        for (java.util.Map.Entry<String, Object> e : m.entrySet()) {
            if (e == null || e.getKey() == null) continue;
            if (e.getKey().equalsIgnoreCase(key)) return e.getValue();
        }
        return null;
    }

    @Override
    public float[] embed(String text, String memoryAction, java.util.Map<String, Object> filtersOrMetadata) {
        SubStore s = routeToStore(filtersOrMetadata);
        if (s != null && s.embedder != null) {
            return s.embedder.embed(text, memoryAction);
        }
        return super.embed(text, memoryAction, filtersOrMetadata);
    }

    @Override
    public com.oceanbase.powermem.sdk.model.MemoryRecord addMemory(String content,
                                                                  String userId,
                                                                  String agentId,
                                                                  String runId,
                                                                  java.util.Map<String, Object> metadata,
                                                                  java.util.Map<String, Object> attributes,
                                                                  String scope,
                                                                  String memoryType) {
        // Route based on metadata (Python behavior).
        SubStore s = routeToStore(metadata);
        if (s == null) {
            return super.addMemory(content, userId, agentId, runId, metadata, attributes, scope, memoryType);
        }
        com.oceanbase.powermem.sdk.storage.base.VectorStore target = s.vectorStore;
        com.oceanbase.powermem.sdk.integrations.embeddings.Embedder emb = s.embedder == null ? this.embedder : s.embedder;

        if (content == null || content.isBlank()) {
            throw new com.oceanbase.powermem.sdk.exception.ApiException("Cannot store empty content");
        }
        java.time.Instant now = java.time.Instant.now();
        com.oceanbase.powermem.sdk.model.MemoryRecord record = new com.oceanbase.powermem.sdk.model.MemoryRecord();
        record.setId(com.oceanbase.powermem.sdk.util.SnowflakeIdGenerator.defaultGenerator().nextId());
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

        float[] vec = emb.embed(content, "add");
        target.upsert(record, vec);
        return record;
    }

    @Override
    public java.util.List<com.oceanbase.powermem.sdk.storage.base.OutputData> searchMemories(String queryText,
                                                                                           float[] queryEmbedding,
                                                                                           int limit,
                                                                                           String userId,
                                                                                           String agentId,
                                                                                           String runId,
                                                                                           java.util.Map<String, Object> filters) {
        SubStore s = routeToStore(filters);
        com.oceanbase.powermem.sdk.storage.base.VectorStore target = s == null ? this.vectorStore : s.vectorStore;
        if (target instanceof com.oceanbase.powermem.sdk.storage.oceanbase.OceanBaseVectorStore) {
            return ((com.oceanbase.powermem.sdk.storage.oceanbase.OceanBaseVectorStore) target)
                    .searchHybrid(queryText, queryEmbedding, limit, userId, agentId, runId, filters);
        }
        return target.search(queryEmbedding, limit, userId, agentId, runId, filters);
    }

    @Override
    public com.oceanbase.powermem.sdk.model.MemoryRecord getMemory(String memoryId, String userId, String agentId) {
        com.oceanbase.powermem.sdk.model.MemoryRecord r = this.vectorStore.get(memoryId, userId, agentId);
        if (r != null) {
            return r;
        }
        for (SubStore s : subStores) {
            if (s == null || s.vectorStore == null) continue;
            r = s.vectorStore.get(memoryId, userId, agentId);
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    @Override
    public com.oceanbase.powermem.sdk.model.MemoryRecord updateMemory(String memoryId,
                                                                     String newContent,
                                                                     String userId,
                                                                     String agentId,
                                                                     java.util.Map<String, Object> metadata) {
        // Find which store contains this memory (Python behavior).
        StoreHit hit = findStore(memoryId, userId, agentId);
        if (hit == null || hit.record == null || hit.store == null) {
            return null;
        }
        com.oceanbase.powermem.sdk.model.MemoryRecord existing = hit.record;
        if (newContent != null && !newContent.isBlank()) {
            existing.setContent(newContent);
            existing.setHash(com.oceanbase.powermem.sdk.util.PowermemUtils.md5Hex(newContent));
        }
        if (metadata != null) {
            existing.setMetadata(metadata);
        }
        existing.setUpdatedAt(java.time.Instant.now());

        java.util.Map<String, Object> ctx = metadata == null ? existing.getMetadata() : metadata;
        com.oceanbase.powermem.sdk.integrations.embeddings.Embedder emb = resolveEmbedder(ctx);
        float[] vec = emb.embed(existing.getContent() == null ? "" : existing.getContent(), "update");
        hit.store.upsert(existing, vec);
        return existing;
    }

    @Override
    public boolean deleteMemory(String memoryId, String userId, String agentId) {
        if (this.vectorStore.delete(memoryId, userId, agentId)) {
            return true;
        }
        for (SubStore s : subStores) {
            if (s == null || s.vectorStore == null) continue;
            if (s.vectorStore.delete(memoryId, userId, agentId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void updatePayloadFields(String memoryId,
                                   String userId,
                                   String agentId,
                                   java.util.Map<String, Object> fieldUpdates) {
        if (memoryId == null || memoryId.isBlank() || fieldUpdates == null || fieldUpdates.isEmpty()) {
            return;
        }
        // Find the store containing the memory.
        StoreHit hit = findStore(memoryId, userId, agentId);
        if (hit == null || hit.store == null) {
            return;
        }
        com.oceanbase.powermem.sdk.storage.base.VectorStore store = hit.store;
        if (store instanceof com.oceanbase.powermem.sdk.storage.sqlite.SQLiteVectorStore) {
            ((com.oceanbase.powermem.sdk.storage.sqlite.SQLiteVectorStore) store).updatePayloadFields(memoryId, fieldUpdates);
            return;
        }
        if (store instanceof com.oceanbase.powermem.sdk.storage.oceanbase.OceanBaseVectorStore) {
            ((com.oceanbase.powermem.sdk.storage.oceanbase.OceanBaseVectorStore) store).updatePayloadFields(memoryId, fieldUpdates);
            return;
        }
        // Fallback: delegate to base implementation (may do get+upsert on main store).
        super.updatePayloadFields(memoryId, userId, agentId, fieldUpdates);
    }

    private com.oceanbase.powermem.sdk.integrations.embeddings.Embedder resolveEmbedder(java.util.Map<String, Object> filtersOrMetadata) {
        SubStore s = routeToStore(filtersOrMetadata);
        if (s != null && s.embedder != null) {
            return s.embedder;
        }
        return this.embedder;
    }

    private StoreHit findStore(String memoryId, String userId, String agentId) {
        com.oceanbase.powermem.sdk.model.MemoryRecord r = this.vectorStore.get(memoryId, userId, agentId);
        if (r != null) {
            return new StoreHit(this.vectorStore, r);
        }
        for (SubStore s : subStores) {
            if (s == null || s.vectorStore == null) continue;
            r = s.vectorStore.get(memoryId, userId, agentId);
            if (r != null) {
                return new StoreHit(s.vectorStore, r);
            }
        }
        return null;
    }

    private static final class StoreHit {
        final com.oceanbase.powermem.sdk.storage.base.VectorStore store;
        final com.oceanbase.powermem.sdk.model.MemoryRecord record;
        StoreHit(com.oceanbase.powermem.sdk.storage.base.VectorStore store,
                 com.oceanbase.powermem.sdk.model.MemoryRecord record) {
            this.store = store;
            this.record = record;
        }
    }

    private static final class SubStore {
        final String name;
        final java.util.Map<String, Object> routingFilter;
        final com.oceanbase.powermem.sdk.storage.base.VectorStore vectorStore;
        final com.oceanbase.powermem.sdk.integrations.embeddings.Embedder embedder;
        SubStore(String name,
                 java.util.Map<String, Object> routingFilter,
                 com.oceanbase.powermem.sdk.storage.base.VectorStore vectorStore,
                 com.oceanbase.powermem.sdk.integrations.embeddings.Embedder embedder) {
            this.name = name;
            this.routingFilter = routingFilter;
            this.vectorStore = vectorStore;
            this.embedder = embedder;
        }
    }
}

