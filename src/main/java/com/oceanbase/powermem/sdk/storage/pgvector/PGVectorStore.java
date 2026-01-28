package com.oceanbase.powermem.sdk.storage.pgvector;

import com.oceanbase.powermem.sdk.storage.base.VectorStore;

/**
 * pgvector/Postgres vector store implementation (Java migration target).
 *
 * <p>Python reference: {@code src/powermem/storage/pgvector/pgvector.py} (PGVectorStore)</p>
 */
public class PGVectorStore implements VectorStore {
    @Override
    public void upsert(com.oceanbase.powermem.sdk.model.MemoryRecord record, float[] embedding) {
        throw new UnsupportedOperationException("PGVectorStore is not implemented yet.");
    }

    @Override
    public com.oceanbase.powermem.sdk.model.MemoryRecord get(String memoryId, String userId, String agentId) {
        throw new UnsupportedOperationException("PGVectorStore is not implemented yet.");
    }

    @Override
    public boolean delete(String memoryId, String userId, String agentId) {
        throw new UnsupportedOperationException("PGVectorStore is not implemented yet.");
    }

    @Override
    public int deleteAll(String userId, String agentId, String runId) {
        throw new UnsupportedOperationException("PGVectorStore is not implemented yet.");
    }

    @Override
    public java.util.List<com.oceanbase.powermem.sdk.model.MemoryRecord> list(String userId, String agentId, String runId, int offset, int limit) {
        throw new UnsupportedOperationException("PGVectorStore is not implemented yet.");
    }

    @Override
    public java.util.List<com.oceanbase.powermem.sdk.storage.base.OutputData> search(
            float[] queryEmbedding,
            int topK,
            String userId,
            String agentId,
            String runId,
            java.util.Map<String, Object> filters) {
        throw new UnsupportedOperationException("PGVectorStore is not implemented yet.");
    }
}

