package com.oceanbase.powermem.sdk.config;

/**
 * Graph store configuration (pure Java core migration target).
 *
 * <p>Python reference: {@code src/powermem/storage/configs.py} and OceanBase graph store modules.</p>
 */
public class GraphStoreConfig {
    private boolean enabled = false;
    private String provider = "oceanbase";
    private String host = "127.0.0.1";
    private int port = 2881;
    private String user = "root";
    private String password;
    private String database = "powermem";
    // Python parity: entities/relationships tables (defaults in powermem.storage.oceanbase.constants)
    private String entitiesTable = "graph_entities";
    private String relationshipsTable = "graph_relationships";

    // Vector / ANN configuration (best-effort in JDBC).
    private int embeddingModelDims = 0;
    private String indexType = "HNSW";
    private String metricType = "l2";
    private String vectorIndexName = "vidx";
    private double similarityThreshold = 0.7;

    // Graph search behavior.
    private int maxHops = 3;
    private int searchLimit = 100;
    private int bm25TopN = 15;

    // Prompts customization (Python: custom_prompt or custom_*_prompt)
    private String customPrompt;
    private String customExtractRelationsPrompt;
    private String customDeleteRelationsPrompt;

    /**
     * Optional per-graph-store LLM config override (Python parity: graph_store.llm.*).
     * If provider/apiKey is not set, the Memory-level LLM is used.
     */
    private LlmConfig llm;

    /**
     * Optional per-graph-store embedder config override (Python parity: graph_store.embedder.*).
     * If provider/apiKey is not set, the Memory-level Embedder is used.
     */
    private EmbedderConfig embedder;

    private int timeoutSeconds = 10;

    public GraphStoreConfig() {}

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getEntitiesTable() {
        return entitiesTable;
    }

    public void setEntitiesTable(String entitiesTable) {
        this.entitiesTable = entitiesTable;
    }

    public String getRelationshipsTable() {
        return relationshipsTable;
    }

    public void setRelationshipsTable(String relationshipsTable) {
        this.relationshipsTable = relationshipsTable;
    }

    public int getEmbeddingModelDims() {
        return embeddingModelDims;
    }

    public void setEmbeddingModelDims(int embeddingModelDims) {
        this.embeddingModelDims = embeddingModelDims;
    }

    public String getIndexType() {
        return indexType;
    }

    public void setIndexType(String indexType) {
        this.indexType = indexType;
    }

    public String getMetricType() {
        return metricType;
    }

    public void setMetricType(String metricType) {
        this.metricType = metricType;
    }

    public String getVectorIndexName() {
        return vectorIndexName;
    }

    public void setVectorIndexName(String vectorIndexName) {
        this.vectorIndexName = vectorIndexName;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public int getMaxHops() {
        return maxHops;
    }

    public void setMaxHops(int maxHops) {
        this.maxHops = maxHops;
    }

    public int getSearchLimit() {
        return searchLimit;
    }

    public void setSearchLimit(int searchLimit) {
        this.searchLimit = searchLimit;
    }

    public int getBm25TopN() {
        return bm25TopN;
    }

    public void setBm25TopN(int bm25TopN) {
        this.bm25TopN = bm25TopN;
    }

    public String getCustomPrompt() {
        return customPrompt;
    }

    public void setCustomPrompt(String customPrompt) {
        this.customPrompt = customPrompt;
    }

    public String getCustomExtractRelationsPrompt() {
        return customExtractRelationsPrompt;
    }

    public void setCustomExtractRelationsPrompt(String customExtractRelationsPrompt) {
        this.customExtractRelationsPrompt = customExtractRelationsPrompt;
    }

    public String getCustomDeleteRelationsPrompt() {
        return customDeleteRelationsPrompt;
    }

    public void setCustomDeleteRelationsPrompt(String customDeleteRelationsPrompt) {
        this.customDeleteRelationsPrompt = customDeleteRelationsPrompt;
    }

    public LlmConfig getLlm() {
        if (llm == null) {
            llm = new LlmConfig();
        }
        return llm;
    }

    public void setLlm(LlmConfig llm) {
        this.llm = llm;
    }

    public EmbedderConfig getEmbedder() {
        if (embedder == null) {
            embedder = new EmbedderConfig();
        }
        return embedder;
    }

    public void setEmbedder(EmbedderConfig embedder) {
        this.embedder = embedder;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}

