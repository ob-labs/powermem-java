package com.oceanbase.powermem.sdk.storage.factory;

/**
 * Factory for creating {@link com.oceanbase.powermem.sdk.storage.base.VectorStore} implementations based on
 * configuration.
 *
 * <p>Python reference: {@code src/powermem/storage/factory.py} (VectorStoreFactory)</p>
 */
public final class VectorStoreFactory {
    private VectorStoreFactory() {}

    public static com.oceanbase.powermem.sdk.storage.base.VectorStore fromConfig(com.oceanbase.powermem.sdk.config.VectorStoreConfig config) {
        String provider = config == null ? null : config.getProvider();
        if (provider == null || provider.isBlank() || "sqlite".equalsIgnoreCase(provider)) {
            String path = config == null ? null : config.getDatabasePath();
            String table = config == null ? null : config.getCollectionName();
            boolean wal = config != null && config.isEnableWal();
            int timeout = config == null ? 30 : config.getTimeoutSeconds();
            return new com.oceanbase.powermem.sdk.storage.sqlite.SQLiteVectorStore(path, table, wal, timeout);
        }
        if ("oceanbase".equalsIgnoreCase(provider) || "ob".equalsIgnoreCase(provider)) {
            return new com.oceanbase.powermem.sdk.storage.oceanbase.OceanBaseVectorStore(config);
        }
        if ("pgvector".equalsIgnoreCase(provider) || "postgres".equalsIgnoreCase(provider) || "postgresql".equalsIgnoreCase(provider)) {
            return new com.oceanbase.powermem.sdk.storage.pgvector.PGVectorStore();
        }
        // Default to sqlite/in-memory for now.
        return new com.oceanbase.powermem.sdk.storage.sqlite.SQLiteVectorStore();
    }
}

