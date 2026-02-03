package com.oceanbase.powermem.sdk.storage.base;

/**
 * Graph store abstraction used for relation extraction and graph-based retrieval.
 *
 * <p>Python reference: {@code src/powermem/storage/graph/*} and {@code src/powermem/storage/factory.py}
 * (GraphStoreFactory).</p>
 */
public interface GraphStore {
    /**
     * Add raw text data to graph store (entity/relation extraction + upsert).
     *
     * <p>Python parity: {@code GraphStoreBase.add(data, filters)} returns a dict like:
     * {@code {"deleted_entities": [...], "added_entities": [{"source":..,"relationship":..,"target":..}, ...]}}.</p>
     *
     * @param data raw text content
     * @param filters scope filters (user_id/agent_id/run_id, etc)
     * @return relations summary (may be empty)
     */
    java.util.Map<String, Object> add(String data, java.util.Map<String, Object> filters);

    /**
     * Search graph store for relevant relations.
     *
     * <p>Python parity: {@code GraphStoreBase.search(query, filters)} returns a list of dict like
     * {@code [{"source":..,"relationship":..,"destination":..}, ...]}.</p>
     */
    java.util.List<java.util.Map<String, Object>> search(String query, java.util.Map<String, Object> filters, int limit);

    /**
     * Delete all graph data in the given scope.
     */
    void deleteAll(java.util.Map<String, Object> filters);

    /**
     * Retrieve all relations in the given scope.
     */
    java.util.List<java.util.Map<String, Object>> getAll(java.util.Map<String, Object> filters, int limit);

    /**
     * Reset graph store state.
     */
    void reset();
}

