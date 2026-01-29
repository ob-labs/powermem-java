package com.oceanbase.powermem.sdk.config;

/**
 * Embedding model configuration (pure Java core migration target).
 *
 * <p>Python reference: {@code src/powermem/integrations/embeddings/configs.py} and {@code src/powermem/integrations/embeddings/config/*}</p>
 */
public class EmbedderConfig {
    private String provider = "qwen";
    private String apiKey;
    private String model = "text-embedding-v4";
    private int embeddingDims = 1536;
    private String baseUrl = "https://dashscope.aliyuncs.com/api/v1";

    public EmbedderConfig() {}

    public static EmbedderConfig qwen(String apiKey, String model, int dims) {
        EmbedderConfig config = new EmbedderConfig();
        config.setProvider("qwen");
        config.setApiKey(apiKey);
        if (model != null) {
            config.setModel(model);
        }
        if (dims > 0) {
            config.setEmbeddingDims(dims);
        }
        return config;
    }

    public static EmbedderConfig openAi(String apiKey, String model, int dims) {
        EmbedderConfig config = new EmbedderConfig();
        config.setProvider("openai");
        config.setApiKey(apiKey);
        if (model != null) {
            config.setModel(model);
        }
        if (dims > 0) {
            config.setEmbeddingDims(dims);
        }
        config.setBaseUrl("https://api.openai.com/v1");
        return config;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getEmbeddingDims() {
        return embeddingDims;
    }

    public void setEmbeddingDims(int embeddingDims) {
        this.embeddingDims = embeddingDims;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}

