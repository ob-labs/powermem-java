package com.oceanbase.powermem.sdk.config;

/**
 * Reranker configuration (pure Java core migration target).
 *
 * <p>Python reference: {@code src/powermem/integrations/rerank/configs.py}</p>
 */
public class RerankConfig {
    private String provider;
    private String apiKey;
    private String model;
    private int topK = 10;
    private String baseUrl;

    public RerankConfig() {}

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

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}

