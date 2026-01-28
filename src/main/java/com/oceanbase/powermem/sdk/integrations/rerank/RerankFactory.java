package com.oceanbase.powermem.sdk.integrations.rerank;

/**
 * Factory for creating {@link Reranker} implementations from config/provider name.
 *
 * <p>Python reference: {@code src/powermem/integrations/rerank/factory.py}</p>
 */
public final class RerankFactory {
    private RerankFactory() {}

    public static Reranker fromConfig(com.oceanbase.powermem.sdk.config.RerankConfig cfg) {
        if (cfg == null || cfg.getProvider() == null || cfg.getProvider().isBlank()) {
            return null;
        }
        String p = cfg.getProvider().trim().toLowerCase();
        if ("qwen".equals(p) || "dashscope".equals(p)) {
            return new QwenReranker(cfg, new com.oceanbase.powermem.sdk.transport.JavaHttpTransport(), new com.oceanbase.powermem.sdk.json.JacksonJsonCodec());
        }
        if ("generic".equals(p)) {
            return new GenericReranker();
        }
        throw new com.oceanbase.powermem.sdk.exception.ApiException("Unsupported reranker provider: " + cfg.getProvider());
    }
}

