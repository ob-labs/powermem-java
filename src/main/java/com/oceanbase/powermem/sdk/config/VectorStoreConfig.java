package com.oceanbase.powermem.sdk.config;

/**
 * Vector store configuration (pure Java core migration target).
 *
 * <p>Python reference: {@code src/powermem/storage/configs.py} and {@code src/powermem/storage/config/*}</p>
 */
public class VectorStoreConfig {
    private String provider = "sqlite";

    // SQLite
    private String databasePath = "./data/powermem_dev.db";
    private boolean enableWal = true;
    private int timeoutSeconds = 30;

    // OceanBase / Postgres shared
    private String host = "127.0.0.1";
    private int port = 0;
    private String user;
    private String password;
    private String database;
    private String collectionName = "memories";
    private int embeddingModelDims = 1536;

    // OceanBase-specific
    private String indexType = "IVF_FLAT";
    private String metricType = "cosine";
    // Hybrid search (OceanBase): vector + full-text fusion
    private boolean hybridSearch = true;
    private String fulltextParser = "ik";
    private double vectorWeight = 0.5;
    private double ftsWeight = 0.5;
    private String fusionMethod = "rrf"; // rrf | weighted
    private int rrfK = 60;
    private String textField = "document";
    private String vectorField = "embedding";
    private String metadataField = "metadata";
    private String primaryField = "id";
    private String vectorIndexName = "memories_vidx";

    // Postgres-specific
    private String sslmode = "prefer";
    private int poolSize = 10;
    private int maxOverflow = 20;

    public VectorStoreConfig() {}

    public static VectorStoreConfig sqlite(String databasePath) {
        VectorStoreConfig config = new VectorStoreConfig();
        config.setProvider("sqlite");
        config.setDatabasePath(databasePath);
        return config;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getDatabasePath() {
        return databasePath;
    }

    public void setDatabasePath(String databasePath) {
        this.databasePath = databasePath;
    }

    public boolean isEnableWal() {
        return enableWal;
    }

    public void setEnableWal(boolean enableWal) {
        this.enableWal = enableWal;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
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

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
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

    public boolean isHybridSearch() {
        return hybridSearch;
    }

    public void setHybridSearch(boolean hybridSearch) {
        this.hybridSearch = hybridSearch;
    }

    public String getFulltextParser() {
        return fulltextParser;
    }

    public void setFulltextParser(String fulltextParser) {
        this.fulltextParser = fulltextParser;
    }

    public double getVectorWeight() {
        return vectorWeight;
    }

    public void setVectorWeight(double vectorWeight) {
        this.vectorWeight = vectorWeight;
    }

    public double getFtsWeight() {
        return ftsWeight;
    }

    public void setFtsWeight(double ftsWeight) {
        this.ftsWeight = ftsWeight;
    }

    public String getFusionMethod() {
        return fusionMethod;
    }

    public void setFusionMethod(String fusionMethod) {
        this.fusionMethod = fusionMethod;
    }

    public int getRrfK() {
        return rrfK;
    }

    public void setRrfK(int rrfK) {
        this.rrfK = rrfK;
    }

    public String getTextField() {
        return textField;
    }

    public void setTextField(String textField) {
        this.textField = textField;
    }

    public String getVectorField() {
        return vectorField;
    }

    public void setVectorField(String vectorField) {
        this.vectorField = vectorField;
    }

    public String getMetadataField() {
        return metadataField;
    }

    public void setMetadataField(String metadataField) {
        this.metadataField = metadataField;
    }

    public String getPrimaryField() {
        return primaryField;
    }

    public void setPrimaryField(String primaryField) {
        this.primaryField = primaryField;
    }

    public String getVectorIndexName() {
        return vectorIndexName;
    }

    public void setVectorIndexName(String vectorIndexName) {
        this.vectorIndexName = vectorIndexName;
    }

    public String getSslmode() {
        return sslmode;
    }

    public void setSslmode(String sslmode) {
        this.sslmode = sslmode;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }

    public int getMaxOverflow() {
        return maxOverflow;
    }

    public void setMaxOverflow(int maxOverflow) {
        this.maxOverflow = maxOverflow;
    }
}

