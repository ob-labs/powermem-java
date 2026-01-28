package com.oceanbase.powermem.sdk.config;

/**
 * Root configuration object for PowerMem (pure Java core migration target).
 *
 * <p>This mirrors the structure of the Python {@code MemoryConfig} model, aggregating vector store,
 * LLM, embedder, reranker, graph store, intelligent memory, and agent memory configs.</p>
 *
 * <p>Python reference: {@code src/powermem/configs.py}</p>
 */
public class MemoryConfig {
    private VectorStoreConfig vectorStore = new VectorStoreConfig();
    private LlmConfig llm = new LlmConfig();
    private EmbedderConfig embedder = new EmbedderConfig();
    private GraphStoreConfig graphStore = new GraphStoreConfig();
    private RerankConfig reranker;
    private IntelligentMemoryConfig intelligentMemory = new IntelligentMemoryConfig();
    private AgentMemoryConfig agentMemory = new AgentMemoryConfig();
    private TelemetryConfig telemetry = new TelemetryConfig();
    private AuditConfig audit = new AuditConfig();
    private LoggingConfig logging = new LoggingConfig();
    private String version = "v1.1";
    private String customFactExtractionPrompt;
    private String customUpdateMemoryPrompt;
    private String customImportanceEvaluationPrompt;

    public MemoryConfig() {}

    public VectorStoreConfig getVectorStore() {
        return vectorStore;
    }

    public void setVectorStore(VectorStoreConfig vectorStore) {
        this.vectorStore = vectorStore;
    }

    public LlmConfig getLlm() {
        return llm;
    }

    public void setLlm(LlmConfig llm) {
        this.llm = llm;
    }

    public EmbedderConfig getEmbedder() {
        return embedder;
    }

    public void setEmbedder(EmbedderConfig embedder) {
        this.embedder = embedder;
    }

    public GraphStoreConfig getGraphStore() {
        return graphStore;
    }

    public void setGraphStore(GraphStoreConfig graphStore) {
        this.graphStore = graphStore;
    }

    public RerankConfig getReranker() {
        return reranker;
    }

    public void setReranker(RerankConfig reranker) {
        this.reranker = reranker;
    }

    public IntelligentMemoryConfig getIntelligentMemory() {
        return intelligentMemory;
    }

    public void setIntelligentMemory(IntelligentMemoryConfig intelligentMemory) {
        this.intelligentMemory = intelligentMemory;
    }

    public AgentMemoryConfig getAgentMemory() {
        return agentMemory;
    }

    public void setAgentMemory(AgentMemoryConfig agentMemory) {
        this.agentMemory = agentMemory;
    }

    public TelemetryConfig getTelemetry() {
        return telemetry;
    }

    public void setTelemetry(TelemetryConfig telemetry) {
        this.telemetry = telemetry;
    }

    public AuditConfig getAudit() {
        return audit;
    }

    public void setAudit(AuditConfig audit) {
        this.audit = audit;
    }

    public LoggingConfig getLogging() {
        return logging;
    }

    public void setLogging(LoggingConfig logging) {
        this.logging = logging;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getCustomFactExtractionPrompt() {
        return customFactExtractionPrompt;
    }

    public void setCustomFactExtractionPrompt(String customFactExtractionPrompt) {
        this.customFactExtractionPrompt = customFactExtractionPrompt;
    }

    public String getCustomUpdateMemoryPrompt() {
        return customUpdateMemoryPrompt;
    }

    public void setCustomUpdateMemoryPrompt(String customUpdateMemoryPrompt) {
        this.customUpdateMemoryPrompt = customUpdateMemoryPrompt;
    }

    public String getCustomImportanceEvaluationPrompt() {
        return customImportanceEvaluationPrompt;
    }

    public void setCustomImportanceEvaluationPrompt(String customImportanceEvaluationPrompt) {
        this.customImportanceEvaluationPrompt = customImportanceEvaluationPrompt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final MemoryConfig config = new MemoryConfig();

        public Builder vectorStore(VectorStoreConfig vectorStore) {
            config.setVectorStore(vectorStore);
            return this;
        }

        public Builder llm(LlmConfig llm) {
            config.setLlm(llm);
            return this;
        }

        public Builder embedder(EmbedderConfig embedder) {
            config.setEmbedder(embedder);
            return this;
        }

        public Builder graphStore(GraphStoreConfig graphStore) {
            config.setGraphStore(graphStore);
            return this;
        }

        public Builder reranker(RerankConfig reranker) {
            config.setReranker(reranker);
            return this;
        }

        public Builder intelligentMemory(IntelligentMemoryConfig intelligentMemory) {
            config.setIntelligentMemory(intelligentMemory);
            return this;
        }

        public Builder agentMemory(AgentMemoryConfig agentMemory) {
            config.setAgentMemory(agentMemory);
            return this;
        }

        public Builder telemetry(TelemetryConfig telemetry) {
            config.setTelemetry(telemetry);
            return this;
        }

        public Builder audit(AuditConfig audit) {
            config.setAudit(audit);
            return this;
        }

        public Builder logging(LoggingConfig logging) {
            config.setLogging(logging);
            return this;
        }

        public Builder version(String version) {
            config.setVersion(version);
            return this;
        }

        public Builder customFactExtractionPrompt(String prompt) {
            config.setCustomFactExtractionPrompt(prompt);
            return this;
        }

        public Builder customUpdateMemoryPrompt(String prompt) {
            config.setCustomUpdateMemoryPrompt(prompt);
            return this;
        }

        public Builder customImportanceEvaluationPrompt(String prompt) {
            config.setCustomImportanceEvaluationPrompt(prompt);
            return this;
        }

        public MemoryConfig build() {
            return config;
        }
    }
}

