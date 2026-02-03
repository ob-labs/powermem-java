package com.oceanbase.powermem.sdk.storage.oceanbase;

/**
 * OceanBase storage constants (table names, index names, default field names, etc.).
 *
 * <p>Python reference: {@code src/powermem/storage/oceanbase/constants.py}</p>
 */
public final class OceanBaseConstants {
    private OceanBaseConstants() {}

    // Graph storage table names
    public static final String TABLE_GRAPH_ENTITIES = "graph_entities";
    public static final String TABLE_GRAPH_RELATIONSHIPS = "graph_relationships";

    // Vector config defaults
    public static final String DEFAULT_OCEANBASE_VECTOR_METRIC_TYPE = "l2";
    public static final String DEFAULT_VIDX_NAME = "vidx";
    public static final String DEFAULT_INDEX_TYPE = "HNSW";

    // Graph search defaults
    public static final double DEFAULT_SIMILARITY_THRESHOLD = 0.7;
    public static final int DEFAULT_SEARCH_LIMIT = 100;
    public static final int DEFAULT_BM25_TOP_N = 15;
    public static final int DEFAULT_MAX_HOPS = 3;
}

