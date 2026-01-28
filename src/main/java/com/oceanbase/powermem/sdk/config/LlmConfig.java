package com.oceanbase.powermem.sdk.config;

/**
 * LLM configuration (pure Java core migration target).
 *
 * <p>Python reference: {@code src/powermem/integrations/llm/configs.py} and {@code src/powermem/integrations/llm/config/*}</p>
 */
public class LlmConfig {
    private String provider = "qwen";
    private String apiKey;
    private String model = "qwen-plus";
    private String baseUrl = "https://dashscope.aliyuncs.com/api/v1";
    private double temperature = 0.7;
    private int maxTokens = 1000;
    private double topP = 0.8;
    private int topK = 50;
    private boolean enableSearch = false;

    public LlmConfig() {}

    public static LlmConfig qwen(String apiKey, String model) {
        LlmConfig config = new LlmConfig();
        config.setProvider("qwen");
        config.setApiKey(apiKey);
        if (model != null) {
            config.setModel(model);
        }
        return config;
    }

    public static LlmConfig openAi(String apiKey, String model) {
        LlmConfig config = new LlmConfig();
        config.setProvider("openai");
        config.setApiKey(apiKey);
        if (model != null) {
            config.setModel(model);
        }
        config.setBaseUrl("https://api.openai.com/v1");
        config.setTopP(1.0);
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

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public double getTopP() {
        return topP;
    }

    public void setTopP(double topP) {
        this.topP = topP;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public boolean isEnableSearch() {
        return enableSearch;
    }

    public void setEnableSearch(boolean enableSearch) {
        this.enableSearch = enableSearch;
    }
}

