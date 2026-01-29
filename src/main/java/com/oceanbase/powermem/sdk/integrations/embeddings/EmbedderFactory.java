package com.oceanbase.powermem.sdk.integrations.embeddings;

/**
 * Factory for creating {@link Embedder} implementations from config/provider name.
 *
 * <p>Python reference: {@code src/powermem/integrations/embeddings/factory.py}</p>
 */
public final class EmbedderFactory {
    private EmbedderFactory() {}

    public static Embedder fromConfig(com.oceanbase.powermem.sdk.config.EmbedderConfig config) {
        if (config == null) {
            return new MockEmbedder();
        }
        String provider = config.getProvider();
        if (provider == null || provider.isBlank() || "mock".equalsIgnoreCase(provider)) {
            return new MockEmbedder();
        }
        // If apiKey is missing, default to mock to keep local usage working.
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            return new MockEmbedder();
        }
        if ("openai".equalsIgnoreCase(provider)) {
            return new OpenAiEmbedder(config);
        }
        if ("qwen".equalsIgnoreCase(provider)) {
            return new QwenEmbedder(config);
        }
        return new MockEmbedder();
    }
}

