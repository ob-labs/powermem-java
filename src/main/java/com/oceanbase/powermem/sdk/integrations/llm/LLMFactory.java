package com.oceanbase.powermem.sdk.integrations.llm;

/**
 * Factory for creating {@link LLM} implementations from config/provider name.
 *
 * <p>Python reference: {@code src/powermem/integrations/llm/factory.py}</p>
 */
public final class LLMFactory {
    private LLMFactory() {}

    public static LLM fromConfig(com.oceanbase.powermem.sdk.config.LlmConfig config) {
        if (config == null) {
            return new MockLLM();
        }
        String provider = config.getProvider();
        if (provider == null || provider.isBlank() || "mock".equalsIgnoreCase(provider)) {
            return new MockLLM();
        }
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            return new MockLLM();
        }
        if ("openai".equalsIgnoreCase(provider)) {
            return new OpenAiLLM(config);
        }
        if ("qwen".equalsIgnoreCase(provider)) {
            return new QwenLLM(config);
        }
        return new MockLLM();
    }
}

