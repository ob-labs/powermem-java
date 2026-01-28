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
    public SubStorageAdapter(com.oceanbase.powermem.sdk.storage.base.VectorStore vectorStore,
                             com.oceanbase.powermem.sdk.integrations.embeddings.Embedder embedder) {
        super(vectorStore, embedder);
    }
}

