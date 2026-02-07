package com.oceanbase.powermem.sdk.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for a sub store used by {@code SubStorageAdapter}.
 *
 * <p>Python reference: {@code src/powermem/core/memory.py} (_init_sub_stores) and
 * {@code src/powermem/storage/adapter.py} (SubStoreConfig).</p>
 *
 * <p>Routing rule: if all (key,value) pairs in {@link #routingFilter} match the request metadata/filters,
 * the memory/search will be routed to this store.</p>
 */
public class SubStoreConfig {
    /**
     * Target collection/table name for the sub store.
     * Python key: collection_name (defaults to {main}_sub_{i}).
     */
    private String name;

    /**
     * Required routing filter (exact match). Python key: routing_filter.
     */
    private Map<String, Object> routingFilter = new HashMap<>();

    /**
     * Optional embedding dims for the sub store (defaults to main store dims).
     */
    private Integer embeddingModelDims;

    /**
     * Optional readiness flag for routing.
     *
     * <p>Python uses a DB-backed migration status table to decide readiness. Java does not persist
     * migration status yet; this flag can be used to emulate "pending/ready" routing semantics.</p>
     *
     * <p>If null, defaults to {@code true}.</p>
     */
    private Boolean ready;

    /**
     * Optional vector store overrides. If null, the main vector store config is reused with only
     * collectionName/dims overridden.
     */
    private VectorStoreConfig vectorStore;

    /**
     * Optional embedder overrides. If null, the main embedder config is reused.
     */
    private EmbedderConfig embedder;

    public SubStoreConfig() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getRoutingFilter() {
        return routingFilter;
    }

    public void setRoutingFilter(Map<String, Object> routingFilter) {
        this.routingFilter = routingFilter == null ? new HashMap<>() : new HashMap<>(routingFilter);
    }

    public Integer getEmbeddingModelDims() {
        return embeddingModelDims;
    }

    public void setEmbeddingModelDims(Integer embeddingModelDims) {
        this.embeddingModelDims = embeddingModelDims;
    }

    public Boolean getReady() {
        return ready;
    }

    public void setReady(Boolean ready) {
        this.ready = ready;
    }

    public VectorStoreConfig getVectorStore() {
        return vectorStore;
    }

    public void setVectorStore(VectorStoreConfig vectorStore) {
        this.vectorStore = vectorStore;
    }

    public EmbedderConfig getEmbedder() {
        return embedder;
    }

    public void setEmbedder(EmbedderConfig embedder) {
        this.embedder = embedder;
    }
}

