package com.oceanbase.powermem.sdk.storage.base;

/**
 * Vector store abstraction.
 *
 * <p>Implementations include OceanBase, pgvector/Postgres, and SQLite providers.</p>
 *
 * <p>Python reference: {@code src/powermem/storage/base.py} (VectorStoreBase) and
 * {@code src/powermem/storage/factory.py}.</p>
 */
public interface VectorStore {
    /**
     * Save or update a memory record plus its embedding vector.
     *
     * @param record memory record
     * @param embedding embedding vector
     */
    void upsert(com.oceanbase.powermem.sdk.model.MemoryRecord record, float[] embedding);

    /**
     * Retrieve a memory record by id, with optional access scope.
     *
     * @param memoryId memory id
     * @param userId optional user id filter
     * @param agentId optional agent id filter
     * @return record if found, otherwise null
     */
    com.oceanbase.powermem.sdk.model.MemoryRecord get(String memoryId, String userId, String agentId);

    /**
     * Delete a memory record by id, optionally scoped by user/agent.
     *
     * @param memoryId memory id
     * @param userId optional user id filter
     * @param agentId optional agent id filter
     * @return true if something was deleted
     */
    boolean delete(String memoryId, String userId, String agentId);

    /**
     * Delete all memories for the given user/agent scope.
     *
     * @param userId optional user id filter
     * @param agentId optional agent id filter
     * @param runId optional run id filter
     * @return deleted count
     */
    int deleteAll(String userId, String agentId, String runId);

    /**
     * List all memories with basic pagination.
     */
    java.util.List<com.oceanbase.powermem.sdk.model.MemoryRecord> list(String userId, String agentId, String runId, int offset, int limit);

    /**
     * Vector similarity search.
     *
     * @param queryEmbedding query embedding
     * @param topK max results
     * @param userId optional user id filter
     * @param agentId optional agent id filter
     * @param runId optional run id filter
     * @param filters optional extra filters (applied on payload JSON fields, python-compatible)
     * @return scored results
     */
    java.util.List<com.oceanbase.powermem.sdk.storage.base.OutputData> search(
            float[] queryEmbedding,
            int topK,
            String userId,
            String agentId,
            String runId,
            java.util.Map<String, Object> filters);
}

