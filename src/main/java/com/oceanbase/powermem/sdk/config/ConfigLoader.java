package com.oceanbase.powermem.sdk.config;

/**
 * Configuration loader that builds {@link MemoryConfig}-style config objects from environment variables
 * and/or properties files (pure Java core migration target).
 *
 * <p>Python reference: {@code src/powermem/config_loader.py}</p>
 */
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public final class ConfigLoader {
    private ConfigLoader() {}

    public static MemoryConfig fromEnv() {
        return fromMap(System.getenv());
    }

    /**
     * Load config from ".env" (filesystem) + environment variables.
     *
     * <p>Resolution order (later wins):</p>
     * - .env file (if present)
     * - {@link System#getenv()} (overrides .env)
     *
     * <p>Default search paths (first found wins):</p>
     * - ${user.dir}/.env
     * - ${user.dir}/config/.env
     * - ${user.dir}/conf/.env
     */
    public static MemoryConfig fromEnvAndDotEnv() {
        return fromEnvAndDotEnv((Path) null);
    }

    /**
     * Load config from specified directory/file + environment variables.
     *
     * <p>If {@code dirOrFile} is a directory, we will read {@code dirOrFile/.env}.</p>
     * <p>If {@code dirOrFile} is a file, we will read it as dotenv.</p>
     */
    public static MemoryConfig fromEnvAndDotEnv(String dirOrFile) {
        Path p = Dotenv.resolveDotenvPath(dirOrFile);
        return fromEnvAndDotEnv(p);
    }

    public static MemoryConfig fromEnvAndDotEnv(Path dotenvFile) {
        Map<String, String> merged = new HashMap<>();
        Map<String, String> dotenv = loadDotEnvFilesystem(dotenvFile);
        merged.putAll(dotenv);
        merged.putAll(System.getenv());
        return fromMap(merged);
    }

    /**
     * Load configuration from a .env file located on the classpath (resources).
     *
     * <p>If the resource is missing, this returns a default {@link MemoryConfig}.</p>
     */
    public static MemoryConfig fromDotEnvInResources() {
        return fromDotEnvResource(".env");
    }

    /**
     * Load configuration from a .env resource.
     *
     * @param resourceName classpath resource name (e.g. ".env", "powermem.env")
     * @return resolved {@link MemoryConfig}
     */
    public static MemoryConfig fromDotEnvResource(String resourceName) {
        if (resourceName == null || resourceName.isBlank()) {
            return new MemoryConfig();
        }
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        InputStream stream = loader.getResourceAsStream(resourceName);
        if (stream == null) {
            return new MemoryConfig();
        }
        try (InputStream input = stream;
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            Map<String, String> values = Dotenv.parse(reader);
            return fromMap(values);
        } catch (IOException ex) {
            return new MemoryConfig();
        }
    }

    public static MemoryConfig fromProperties(Properties properties) {
        Properties safe = properties == null ? new Properties() : properties;
        java.util.Map<String, String> values = new java.util.HashMap<>();
        for (String name : safe.stringPropertyNames()) {
            values.put(name, safe.getProperty(name));
        }
        return fromMap(values);
    }

    public static MemoryConfig fromMap(Map<String, ?> values) {
        MemoryConfig config = new MemoryConfig();
        if (values == null) {
            return config;
        }

        VectorStoreConfig vector = config.getVectorStore();
        setIfPresent(values, vector::setProvider, "DATABASE_PROVIDER", "vector_store.provider");
        setIfPresent(values, vector::setDatabasePath, "SQLITE_PATH", "vector_store.database_path");
        setIfPresent(values, v -> vector.setEnableWal(parseBoolean(v)), "SQLITE_ENABLE_WAL");
        setIfPresent(values, v -> vector.setTimeoutSeconds(parseInt(v)), "SQLITE_TIMEOUT");
        setIfPresent(values, vector::setHost, "OCEANBASE_HOST", "POSTGRES_HOST", "vector_store.host");
        setIfPresent(values, v -> vector.setPort(parseInt(v)), "OCEANBASE_PORT", "POSTGRES_PORT", "vector_store.port");
        setIfPresent(values, vector::setUser, "OCEANBASE_USER", "POSTGRES_USER", "vector_store.user");
        setIfPresent(values, vector::setPassword, "OCEANBASE_PASSWORD", "POSTGRES_PASSWORD", "vector_store.password");
        setIfPresent(values, vector::setDatabase, "OCEANBASE_DATABASE", "POSTGRES_DATABASE", "vector_store.database");
        setIfPresent(values, vector::setCollectionName, "OCEANBASE_COLLECTION", "POSTGRES_COLLECTION", "vector_store.collection_name");
        setIfPresent(values, v -> vector.setEmbeddingModelDims(parseInt(v)), "OCEANBASE_EMBEDDING_MODEL_DIMS", "EMBEDDING_DIMS");
        setIfPresent(values, vector::setIndexType, "OCEANBASE_INDEX_TYPE");
        setIfPresent(values, vector::setMetricType, "OCEANBASE_VECTOR_METRIC_TYPE", "OCEANBASE_METRIC_TYPE");
        setIfPresent(values, v -> vector.setHybridSearch(parseBoolean(v)), "OCEANBASE_HYBRID_SEARCH");
        setIfPresent(values, vector::setFulltextParser, "OCEANBASE_FULLTEXT_PARSER");
        setIfPresent(values, v -> vector.setVectorWeight(parseDouble(v)), "OCEANBASE_VECTOR_WEIGHT");
        setIfPresent(values, v -> vector.setFtsWeight(parseDouble(v)), "OCEANBASE_FTS_WEIGHT");
        setIfPresent(values, vector::setFusionMethod, "OCEANBASE_FUSION_METHOD");
        setIfPresent(values, v -> vector.setRrfK(parseInt(v)), "OCEANBASE_RRF_K");
        setIfPresent(values, vector::setPrimaryField, "OCEANBASE_PRIMARY_FIELD");
        setIfPresent(values, vector::setVectorField, "OCEANBASE_VECTOR_FIELD");
        setIfPresent(values, vector::setTextField, "OCEANBASE_TEXT_FIELD");
        setIfPresent(values, vector::setMetadataField, "OCEANBASE_METADATA_FIELD");
        setIfPresent(values, vector::setVectorIndexName, "OCEANBASE_VIDX_NAME");
        setIfPresent(values, vector::setSslmode, "DATABASE_SSLMODE");
        setIfPresent(values, v -> vector.setPoolSize(parseInt(v)), "DATABASE_POOL_SIZE");
        setIfPresent(values, v -> vector.setMaxOverflow(parseInt(v)), "DATABASE_MAX_OVERFLOW");

        // Graph store (optional). Mirrors Python graph_store.enable/provider and uses OceanBase by default.
        GraphStoreConfig graph = config.getGraphStore();
        setIfPresent(values, v -> graph.setEnabled(parseBoolean(v)), "GRAPH_STORE_ENABLED", "graph_store.enabled");
        setIfPresent(values, graph::setProvider, "GRAPH_STORE_PROVIDER", "graph_store.provider");
        // default to OceanBase connection if graph-specific env isn't set
        setIfPresent(values, graph::setHost, "GRAPH_STORE_HOST", "OCEANBASE_HOST");
        setIfPresent(values, v -> graph.setPort(parseInt(v)), "GRAPH_STORE_PORT", "OCEANBASE_PORT");
        setIfPresent(values, graph::setUser, "GRAPH_STORE_USER", "OCEANBASE_USER");
        setIfPresent(values, graph::setPassword, "GRAPH_STORE_PASSWORD", "OCEANBASE_PASSWORD");
        setIfPresent(values, graph::setDatabase, "GRAPH_STORE_DATABASE", "OCEANBASE_DATABASE");
        setIfPresent(values, v -> graph.setTimeoutSeconds(parseInt(v)), "GRAPH_STORE_TIMEOUT");
        // Python parity options
        setIfPresent(values, graph::setEntitiesTable, "GRAPH_STORE_ENTITIES_TABLE", "GRAPH_STORE_TABLE_ENTITIES");
        setIfPresent(values, graph::setRelationshipsTable, "GRAPH_STORE_RELATIONSHIPS_TABLE", "GRAPH_STORE_TABLE_RELATIONSHIPS");
        setIfPresent(values, v -> graph.setEmbeddingModelDims(parseInt(v)), "GRAPH_STORE_EMBEDDING_MODEL_DIMS", "OCEANBASE_EMBEDDING_MODEL_DIMS");
        setIfPresent(values, graph::setIndexType, "GRAPH_STORE_INDEX_TYPE", "OCEANBASE_INDEX_TYPE");
        setIfPresent(values, graph::setMetricType, "GRAPH_STORE_VECTOR_METRIC_TYPE", "GRAPH_STORE_METRIC_TYPE", "OCEANBASE_VECTOR_METRIC_TYPE", "OCEANBASE_METRIC_TYPE");
        setIfPresent(values, graph::setVectorIndexName, "GRAPH_STORE_VIDX_NAME", "OCEANBASE_VIDX_NAME");
        setIfPresent(values, v -> graph.setSimilarityThreshold(parseDouble(v)), "GRAPH_STORE_SIMILARITY_THRESHOLD");
        setIfPresent(values, v -> graph.setMaxHops(parseInt(v)), "GRAPH_STORE_MAX_HOPS");
        setIfPresent(values, v -> graph.setSearchLimit(parseInt(v)), "GRAPH_STORE_SEARCH_LIMIT");
        setIfPresent(values, v -> graph.setBm25TopN(parseInt(v)), "GRAPH_STORE_BM25_TOP_N");
        setIfPresent(values, graph::setCustomPrompt, "GRAPH_STORE_CUSTOM_PROMPT", "graph_store.custom_prompt");
        setIfPresent(values, graph::setCustomExtractRelationsPrompt, "GRAPH_STORE_CUSTOM_EXTRACT_RELATIONS_PROMPT");
        setIfPresent(values, graph::setCustomDeleteRelationsPrompt, "GRAPH_STORE_CUSTOM_DELETE_RELATIONS_PROMPT");

        // graph_store.llm.* overrides (if set, GraphStoreFactory will create a dedicated LLM instance)
        LlmConfig graphLlm = graph.getLlm();
        setIfPresent(values, graphLlm::setProvider, "GRAPH_STORE_LLM_PROVIDER");
        setIfPresent(values, graphLlm::setApiKey, "GRAPH_STORE_LLM_API_KEY");
        setIfPresent(values, graphLlm::setModel, "GRAPH_STORE_LLM_MODEL");
        setIfPresent(values, graphLlm::setBaseUrl, "GRAPH_STORE_LLM_BASE_URL");
        setIfPresent(values, v -> graphLlm.setTemperature(parseDouble(v)), "GRAPH_STORE_LLM_TEMPERATURE");
        setIfPresent(values, v -> graphLlm.setMaxTokens(parseInt(v)), "GRAPH_STORE_LLM_MAX_TOKENS");
        setIfPresent(values, v -> graphLlm.setTopP(parseDouble(v)), "GRAPH_STORE_LLM_TOP_P");

        // graph_store.embedder.* overrides (if set, GraphStoreFactory will create a dedicated Embedder instance)
        EmbedderConfig graphEmb = graph.getEmbedder();
        setIfPresent(values, graphEmb::setProvider, "GRAPH_STORE_EMBEDDING_PROVIDER", "GRAPH_STORE_EMBEDDER_PROVIDER");
        setIfPresent(values, graphEmb::setApiKey, "GRAPH_STORE_EMBEDDING_API_KEY", "GRAPH_STORE_EMBEDDER_API_KEY");
        setIfPresent(values, graphEmb::setModel, "GRAPH_STORE_EMBEDDING_MODEL", "GRAPH_STORE_EMBEDDER_MODEL");
        setIfPresent(values, v -> graphEmb.setEmbeddingDims(parseInt(v)), "GRAPH_STORE_EMBEDDING_DIMS", "GRAPH_STORE_EMBEDDER_DIMS");
        setIfPresent(values, graphEmb::setBaseUrl, "GRAPH_STORE_EMBEDDING_BASE_URL", "GRAPH_STORE_EMBEDDER_BASE_URL");

        LlmConfig llm = config.getLlm();
        setIfPresent(values, llm::setProvider, "LLM_PROVIDER", "llm.provider");
        setIfPresent(values, llm::setApiKey, "LLM_API_KEY");
        setIfPresent(values, llm::setModel, "LLM_MODEL");
        setIfPresent(values, llm::setBaseUrl, "QWEN_LLM_BASE_URL", "OPENAI_LLM_BASE_URL", "llm.base_url");
        setIfPresent(values, v -> llm.setTemperature(parseDouble(v)), "LLM_TEMPERATURE");
        setIfPresent(values, v -> llm.setMaxTokens(parseInt(v)), "LLM_MAX_TOKENS");
        setIfPresent(values, v -> llm.setTopP(parseDouble(v)), "LLM_TOP_P");
        setIfPresent(values, v -> llm.setTopK(parseInt(v)), "LLM_TOP_K");
        setIfPresent(values, v -> llm.setEnableSearch(parseBoolean(v)), "LLM_ENABLE_SEARCH");

        EmbedderConfig embedder = config.getEmbedder();
        setIfPresent(values, embedder::setProvider, "EMBEDDING_PROVIDER", "embedder.provider");
        setIfPresent(values, embedder::setApiKey, "EMBEDDING_API_KEY");
        setIfPresent(values, embedder::setModel, "EMBEDDING_MODEL");
        setIfPresent(values, v -> embedder.setEmbeddingDims(parseInt(v)), "EMBEDDING_DIMS");
        setIfPresent(values, embedder::setBaseUrl, "QWEN_EMBEDDING_BASE_URL", "OPEN_EMBEDDING_BASE_URL");

        // Sub stores (optional): route by metadata/filters to different store/embedder.
        // Python reference: Memory._init_sub_stores + SubStorageAdapter routing.
        loadSubStores(values, config);

        // Reranker (optional)
        RerankConfig rerank = config.getReranker();
        // Create on-demand if any rerank key is present
        if (rerank == null) {
            Object p = values.get("RERANKER_PROVIDER");
            if (p == null) p = values.get("reranker.provider");
            if (p != null && !p.toString().isBlank()) {
                rerank = new RerankConfig();
                config.setReranker(rerank);
            }
        }
        if (rerank != null) {
            final RerankConfig rr = rerank;
            setIfPresent(values, rr::setProvider, "RERANKER_PROVIDER", "reranker.provider");
            setIfPresent(values, rr::setApiKey, "RERANKER_API_KEY");
            setIfPresent(values, rr::setModel, "RERANKER_MODEL");
            setIfPresent(values, v -> rr.setTopK(parseInt(v)), "RERANKER_TOP_K");
            setIfPresent(values, rr::setBaseUrl, "RERANKER_BASE_URL");
        }

        IntelligentMemoryConfig intelligence = config.getIntelligentMemory();
        setIfPresent(values, v -> intelligence.setEnabled(parseBoolean(v)), "INTELLIGENT_MEMORY_ENABLED");
        setIfPresent(values, v -> intelligence.setInitialRetention(parseDouble(v)), "INTELLIGENT_MEMORY_INITIAL_RETENTION");
        setIfPresent(values, v -> intelligence.setDecayRate(parseDouble(v)), "INTELLIGENT_MEMORY_DECAY_RATE");
        setIfPresent(values, v -> intelligence.setReinforcementFactor(parseDouble(v)), "INTELLIGENT_MEMORY_REINFORCEMENT_FACTOR");
        setIfPresent(values, v -> intelligence.setWorkingThreshold(parseDouble(v)), "INTELLIGENT_MEMORY_WORKING_THRESHOLD");
        setIfPresent(values, v -> intelligence.setShortTermThreshold(parseDouble(v)), "INTELLIGENT_MEMORY_SHORT_TERM_THRESHOLD");
        setIfPresent(values, v -> intelligence.setLongTermThreshold(parseDouble(v)), "INTELLIGENT_MEMORY_LONG_TERM_THRESHOLD");
        setIfPresent(values, v -> intelligence.setDecayEnabled(parseBoolean(v)), "MEMORY_DECAY_ENABLED");
        setIfPresent(values, intelligence::setDecayAlgorithm, "MEMORY_DECAY_ALGORITHM");
        setIfPresent(values, v -> intelligence.setDecayBaseRetention(parseDouble(v)), "MEMORY_DECAY_BASE_RETENTION");
        setIfPresent(values, v -> intelligence.setDecayForgettingRate(parseDouble(v)), "MEMORY_DECAY_FORGETTING_RATE");
        setIfPresent(values, v -> intelligence.setDecayReinforcementFactor(parseDouble(v)), "MEMORY_DECAY_REINFORCEMENT_FACTOR");

        AgentMemoryConfig agent = config.getAgentMemory();
        setIfPresent(values, v -> agent.setEnabled(parseBoolean(v)), "AGENT_ENABLED");
        setIfPresent(values, agent::setMode, "AGENT_MEMORY_MODE");
        setIfPresent(values, agent::setDefaultScope, "AGENT_DEFAULT_SCOPE");
        setIfPresent(values, agent::setDefaultPrivacyLevel, "AGENT_DEFAULT_PRIVACY_LEVEL");
        setIfPresent(values, agent::setDefaultCollaborationLevel, "AGENT_DEFAULT_COLLABORATION_LEVEL");
        setIfPresent(values, agent::setDefaultAccessPermission, "AGENT_DEFAULT_ACCESS_PERMISSION");
        setIfPresent(values, v -> agent.setEnableCollaboration(parseBoolean(v)), "AGENT_ENABLE_COLLABORATION");

        TelemetryConfig telemetry = config.getTelemetry();
        setIfPresent(values, v -> telemetry.setEnableTelemetry(parseBoolean(v)), "TELEMETRY_ENABLED");
        setIfPresent(values, telemetry::setTelemetryEndpoint, "TELEMETRY_ENDPOINT");
        setIfPresent(values, telemetry::setTelemetryApiKey, "TELEMETRY_API_KEY");
        setIfPresent(values, v -> telemetry.setBatchSize(parseInt(v)), "TELEMETRY_BATCH_SIZE");
        setIfPresent(values, v -> telemetry.setFlushIntervalSeconds(parseInt(v)), "TELEMETRY_FLUSH_INTERVAL");

        AuditConfig audit = config.getAudit();
        setIfPresent(values, v -> audit.setEnabled(parseBoolean(v)), "AUDIT_ENABLED");
        setIfPresent(values, audit::setLogFile, "AUDIT_LOG_FILE");
        setIfPresent(values, audit::setLogLevel, "AUDIT_LOG_LEVEL");
        setIfPresent(values, v -> audit.setRetentionDays(parseInt(v)), "AUDIT_RETENTION_DAYS");
        setIfPresent(values, v -> audit.setCompressLogs(parseBoolean(v)), "AUDIT_COMPRESS_LOGS");
        setIfPresent(values, audit::setLogRotationSize, "AUDIT_LOG_ROTATION_SIZE");

        LoggingConfig logging = config.getLogging();
        setIfPresent(values, logging::setLevel, "LOGGING_LEVEL");
        setIfPresent(values, logging::setFormat, "LOGGING_FORMAT");
        setIfPresent(values, logging::setFile, "LOGGING_FILE");
        setIfPresent(values, logging::setMaxSize, "LOGGING_MAX_SIZE");
        setIfPresent(values, v -> logging.setBackupCount(parseInt(v)), "LOGGING_BACKUP_COUNT");
        setIfPresent(values, v -> logging.setCompressBackups(parseBoolean(v)), "LOGGING_COMPRESS_BACKUPS");
        setIfPresent(values, v -> logging.setConsoleEnabled(parseBoolean(v)), "LOGGING_CONSOLE_ENABLED");
        setIfPresent(values, logging::setConsoleLevel, "LOGGING_CONSOLE_LEVEL");
        setIfPresent(values, logging::setConsoleFormat, "LOGGING_CONSOLE_FORMAT");

        setIfPresent(values, config::setCustomFactExtractionPrompt, "CUSTOM_FACT_EXTRACTION_PROMPT", "custom_fact_extraction_prompt");
        setIfPresent(values, config::setCustomUpdateMemoryPrompt, "CUSTOM_UPDATE_MEMORY_PROMPT", "custom_update_memory_prompt");
        setIfPresent(values, config::setCustomImportanceEvaluationPrompt, "CUSTOM_IMPORTANCE_EVALUATION_PROMPT", "custom_importance_evaluation_prompt");
        return config;
    }

    /**
     * Load sub-store configs from environment-like map.
     *
     * <p>Supported formats:</p>
     * - {@code SUB_STORES_JSON}: JSON array of objects
     * - Indexed keys (autodetect indices from keys or specify {@code SUB_STORES_COUNT}):
     *   - {@code SUB_STORE_0_COLLECTION} or {@code SUB_STORE_0_NAME}
     *   - routing filter via:
     *     - {@code SUB_STORE_0_ROUTING_FILTER_JSON} (JSON map)
     *     - {@code SUB_STORE_0_ROUTE_<KEY>=<VALUE>} (e.g. {@code SUB_STORE_0_ROUTE_CATEGORY=pref})
     *   - optional overrides (prefixed with {@code SUB_STORE_0_}):
     *     - Vector store: {@code DATABASE_PROVIDER}, {@code SQLITE_PATH}, {@code OCEANBASE_HOST}, ...
     *     - Embedder: {@code EMBEDDING_PROVIDER}, {@code EMBEDDING_API_KEY}, {@code EMBEDDING_MODEL}, {@code EMBEDDING_DIMS}, {@code QWEN_EMBEDDING_BASE_URL}, ...
     */
    private static void loadSubStores(Map<String, ?> values, MemoryConfig config) {
        if (values == null || config == null) {
            return;
        }

        Object json = values.get("SUB_STORES_JSON");
        if (json != null && !json.toString().isBlank()) {
            List<com.oceanbase.powermem.sdk.config.SubStoreConfig> parsed = parseSubStoresJson(json.toString(), config);
            if (parsed != null && !parsed.isEmpty()) {
                config.setSubStores(parsed);
                return;
            }
        }

        Set<Integer> indices = collectSubStoreIndices(values);
        Object cnt = values.get("SUB_STORES_COUNT");
        if (cnt != null) {
            int c = parseInt(cnt.toString());
            for (int i = 0; i < c; i++) {
                indices.add(i);
            }
        }
        if (indices.isEmpty()) {
            return;
        }

        java.util.List<com.oceanbase.powermem.sdk.config.SubStoreConfig> out = new java.util.ArrayList<>();
        for (int idx : indices) {
            String p = "SUB_STORE_" + idx + "_";
            Object enabled = values.get(p + "ENABLED");
            if (enabled != null && !parseBoolean(enabled.toString())) {
                continue;
            }

            Object readyRaw = values.get(p + "READY");
            Boolean ready = null;
            if (readyRaw != null) {
                ready = parseBoolean(readyRaw.toString());
            }

            // name/collection
            String name = str(values.get(p + "COLLECTION"));
            if (name == null || name.isBlank()) {
                name = str(values.get(p + "NAME"));
            }

            // routing filter
            Map<String, Object> routing = new HashMap<>();
            Object rfJson = values.get(p + "ROUTING_FILTER_JSON");
            if (rfJson != null && !rfJson.toString().isBlank()) {
                routing.putAll(parseJsonMapLoose(rfJson.toString()));
            }
            // SUB_STORE_{i}_ROUTE_<KEY>=<VALUE>
            String routePrefix = p + "ROUTE_";
            for (Map.Entry<String, ?> e : values.entrySet()) {
                if (e == null || e.getKey() == null) continue;
                String k = e.getKey();
                if (!k.startsWith(routePrefix)) continue;
                String routeKey = k.substring(routePrefix.length());
                if (routeKey.isBlank()) continue;
                // Normalize to lower-case for Python-like keys (env variables are usually upper-case).
                routing.put(routeKey.toLowerCase(java.util.Locale.ROOT), e.getValue() == null ? null : e.getValue().toString());
            }
            if (routing.isEmpty()) {
                continue; // routing_filter is required for a sub store
            }

            Integer dims = null;
            Object dimsRaw = values.get(p + "EMBEDDING_MODEL_DIMS");
            if (dimsRaw == null) dimsRaw = values.get(p + "EMBEDDING_DIMS");
            if (dimsRaw != null) {
                int d = parseInt(dimsRaw.toString());
                if (d > 0) dims = d;
            }

            com.oceanbase.powermem.sdk.config.SubStoreConfig sc = new com.oceanbase.powermem.sdk.config.SubStoreConfig();
            if (name != null && !name.isBlank()) {
                sc.setName(name);
            }
            sc.setRoutingFilter(routing);
            sc.setEmbeddingModelDims(dims);
            sc.setReady(ready);

            // Build full sub-store VectorStoreConfig and EmbedderConfig by inheriting from main, then applying overrides.
            VectorStoreConfig subVs = (config.getVectorStore() == null) ? new VectorStoreConfig() : config.getVectorStore().copy();
            applyVectorStoreOverridesFromEnv(values, idx, subVs);
            EmbedderConfig subEmb = (config.getEmbedder() == null) ? new EmbedderConfig() : config.getEmbedder().copy();
            applyEmbedderOverridesFromEnv(values, idx, subEmb);
            if (dims != null && dims > 0) {
                subVs.setEmbeddingModelDims(dims);
                subEmb.setEmbeddingDims(dims);
            }

            sc.setVectorStore(subVs);
            sc.setEmbedder(subEmb);
            out.add(sc);
        }
        if (!out.isEmpty()) {
            config.setSubStores(out);
        }
    }

    private static Set<Integer> collectSubStoreIndices(Map<String, ?> values) {
        Set<Integer> out = new HashSet<>();
        if (values == null) return out;
        for (String k : values.keySet()) {
            if (k == null) continue;
            if (!k.startsWith("SUB_STORE_")) continue;
            int start = "SUB_STORE_".length();
            int end = k.indexOf('_', start);
            if (end <= start) continue;
            String num = k.substring(start, end);
            try {
                out.add(Integer.parseInt(num));
            } catch (Exception ignored) {
                // skip
            }
        }
        return out;
    }

    private static void applyVectorStoreOverridesFromEnv(Map<String, ?> values, int idx, VectorStoreConfig vs) {
        if (values == null || vs == null) return;
        String p = "SUB_STORE_" + idx + "_";
        setIfPresent(values, vs::setProvider, p + "DATABASE_PROVIDER");
        setIfPresent(values, vs::setDatabasePath, p + "SQLITE_PATH");
        setIfPresent(values, v -> vs.setEnableWal(parseBoolean(v)), p + "SQLITE_ENABLE_WAL");
        setIfPresent(values, v -> vs.setTimeoutSeconds(parseInt(v)), p + "SQLITE_TIMEOUT", p + "OCEANBASE_TIMEOUT_SECONDS");

        setIfPresent(values, vs::setHost, p + "OCEANBASE_HOST", p + "POSTGRES_HOST");
        setIfPresent(values, v -> vs.setPort(parseInt(v)), p + "OCEANBASE_PORT", p + "POSTGRES_PORT");
        setIfPresent(values, vs::setUser, p + "OCEANBASE_USER", p + "POSTGRES_USER");
        setIfPresent(values, vs::setPassword, p + "OCEANBASE_PASSWORD", p + "POSTGRES_PASSWORD");
        setIfPresent(values, vs::setDatabase, p + "OCEANBASE_DATABASE", p + "POSTGRES_DATABASE");
        setIfPresent(values, vs::setCollectionName, p + "OCEANBASE_COLLECTION", p + "POSTGRES_COLLECTION", p + "COLLECTION", p + "NAME");

        setIfPresent(values, v -> vs.setEmbeddingModelDims(parseInt(v)), p + "OCEANBASE_EMBEDDING_MODEL_DIMS", p + "EMBEDDING_DIMS", p + "EMBEDDING_MODEL_DIMS");
        setIfPresent(values, vs::setIndexType, p + "OCEANBASE_INDEX_TYPE");
        setIfPresent(values, vs::setMetricType, p + "OCEANBASE_VECTOR_METRIC_TYPE", p + "OCEANBASE_METRIC_TYPE");
        setIfPresent(values, vs::setVectorIndexName, p + "OCEANBASE_VIDX_NAME");
        setIfPresent(values, v -> vs.setHybridSearch(parseBoolean(v)), p + "OCEANBASE_HYBRID_SEARCH");
        setIfPresent(values, vs::setFulltextParser, p + "OCEANBASE_FULLTEXT_PARSER");
        setIfPresent(values, v -> vs.setVectorWeight(parseDouble(v)), p + "OCEANBASE_VECTOR_WEIGHT");
        setIfPresent(values, v -> vs.setFtsWeight(parseDouble(v)), p + "OCEANBASE_FTS_WEIGHT");
        setIfPresent(values, vs::setFusionMethod, p + "OCEANBASE_FUSION_METHOD");
        setIfPresent(values, v -> vs.setRrfK(parseInt(v)), p + "OCEANBASE_RRF_K");
    }

    private static void applyEmbedderOverridesFromEnv(Map<String, ?> values, int idx, EmbedderConfig emb) {
        if (values == null || emb == null) return;
        String p = "SUB_STORE_" + idx + "_";
        setIfPresent(values, emb::setProvider, p + "EMBEDDING_PROVIDER");
        setIfPresent(values, emb::setApiKey, p + "EMBEDDING_API_KEY");
        setIfPresent(values, emb::setModel, p + "EMBEDDING_MODEL");
        setIfPresent(values, v -> emb.setEmbeddingDims(parseInt(v)), p + "EMBEDDING_DIMS");
        // allow either generic or provider-specific base url keys
        setIfPresent(values, emb::setBaseUrl, p + "EMBEDDING_BASE_URL", p + "QWEN_EMBEDDING_BASE_URL", p + "OPEN_EMBEDDING_BASE_URL");
    }

    private static List<com.oceanbase.powermem.sdk.config.SubStoreConfig> parseSubStoresJson(String json, MemoryConfig base) {
        if (json == null || json.isBlank()) {
            return java.util.Collections.emptyList();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<Map<String, Object>> arr = mapper.readValue(
                    json,
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
            if (arr == null || arr.isEmpty()) {
                return java.util.Collections.emptyList();
            }
            java.util.List<com.oceanbase.powermem.sdk.config.SubStoreConfig> out = new java.util.ArrayList<>();
            for (Map<String, Object> m : arr) {
                if (m == null) continue;
                com.oceanbase.powermem.sdk.config.SubStoreConfig sc = new com.oceanbase.powermem.sdk.config.SubStoreConfig();
                Object n = m.get("name");
                if (n == null) n = m.get("collection_name");
                if (n == null) n = m.get("collectionName");
                if (n != null && !n.toString().isBlank()) sc.setName(n.toString());
                Object rf = m.get("routing_filter");
                if (rf instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mm = (Map<String, Object>) rf;
                    sc.setRoutingFilter(mm);
                }
                Object d = m.get("embedding_model_dims");
                if (d == null) d = m.get("embedding_dims");
                if (d != null) {
                    int dims = parseInt(String.valueOf(d));
                    if (dims > 0) sc.setEmbeddingModelDims(dims);
                }
                // inherit configs as-is (buildStorageAdapter will do the right thing)
                sc.setVectorStore(base == null || base.getVectorStore() == null ? null : base.getVectorStore().copy());
                sc.setEmbedder(base == null || base.getEmbedder() == null ? null : base.getEmbedder().copy());
                out.add(sc);
            }
            return out;
        } catch (Exception ignored) {
            return java.util.Collections.emptyList();
        }
    }

    private static Map<String, Object> parseJsonMapLoose(String json) {
        try {
            return new com.oceanbase.powermem.sdk.json.JacksonJsonCodec().fromJsonToMap(json);
        } catch (Exception ignored) {
            return new HashMap<>();
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static void setIfPresent(Map<String, ?> values, java.util.function.Consumer<String> setter, String... keys) {
        for (String key : keys) {
            Object raw = values.get(key);
            if (raw != null) {
                setter.accept(raw.toString());
                return;
            }
        }
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ex) {
            return 0;
        }
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception ex) {
            return 0.0;
        }
    }

    private static boolean parseBoolean(String value) {
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private static Map<String, String> loadDotEnvFilesystem(Path dotenvFile) {
        // explicit path
        if (dotenvFile != null) {
            return Dotenv.loadIfExists(dotenvFile);
        }
        // default search paths
        String userDir = System.getProperty("user.dir");
        if (userDir == null || userDir.isBlank()) {
            return new HashMap<>();
        }
        Path base = Path.of(userDir).toAbsolutePath().normalize();
        Path[] candidates = new Path[] {
                base.resolve(".env"),
                base.resolve("config").resolve(".env"),
                base.resolve("conf").resolve(".env")
        };
        for (Path c : candidates) {
            try {
                if (Files.exists(c) && Files.isRegularFile(c)) {
                    return Dotenv.loadIfExists(c);
                }
            } catch (Exception ignored) {
                // keep trying
            }
        }
        return new HashMap<>();
    }
}

