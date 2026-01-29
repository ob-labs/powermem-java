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
import java.util.Map;
import java.util.Properties;

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

