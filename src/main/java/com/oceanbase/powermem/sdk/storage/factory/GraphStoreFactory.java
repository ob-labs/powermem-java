package com.oceanbase.powermem.sdk.storage.factory;

/**
 * Factory for creating {@link com.oceanbase.powermem.sdk.storage.base.GraphStore} implementations based on
 * configuration.
 *
 * <p>Python reference: {@code src/powermem/storage/factory.py} (GraphStoreFactory)</p>
 */
public final class GraphStoreFactory {
    private GraphStoreFactory() {}

    public static com.oceanbase.powermem.sdk.storage.base.GraphStore fromConfig(
            com.oceanbase.powermem.sdk.config.GraphStoreConfig config) {
        return fromConfig(config, null, null);
    }

    public static com.oceanbase.powermem.sdk.storage.base.GraphStore fromConfig(
            com.oceanbase.powermem.sdk.config.GraphStoreConfig config,
            com.oceanbase.powermem.sdk.integrations.embeddings.Embedder embedder,
            com.oceanbase.powermem.sdk.integrations.llm.LLM llm) {
        if (config == null || !config.isEnabled()) {
            return null;
        }
        String provider = config.getProvider();
        if (provider == null || provider.isBlank()) {
            provider = "oceanbase";
        }
        String p = provider.trim().toLowerCase();

        // Offline-friendly: in-memory implementation for tests and local experiments.
        if ("memory".equals(p) || "inmemory".equals(p) || "in-memory".equals(p) || "mock".equals(p)) {
            return new com.oceanbase.powermem.sdk.storage.memory.InMemoryGraphStore();
        }

        // graph_store.llm/embedder override: if configured, create dedicated instances.
        com.oceanbase.powermem.sdk.integrations.llm.LLM effLlm = llm;
        try {
            com.oceanbase.powermem.sdk.config.LlmConfig gl = config.getLlm();
            if (gl != null && gl.getProvider() != null && !gl.getProvider().isBlank()) {
                effLlm = com.oceanbase.powermem.sdk.integrations.llm.LLMFactory.fromConfig(gl);
            }
        } catch (Exception ignored) {}

        com.oceanbase.powermem.sdk.integrations.embeddings.Embedder effEmb = embedder;
        try {
            com.oceanbase.powermem.sdk.config.EmbedderConfig ge = config.getEmbedder();
            if (ge != null && ge.getProvider() != null && !ge.getProvider().isBlank()) {
                effEmb = com.oceanbase.powermem.sdk.integrations.embeddings.EmbedderFactory.fromConfig(ge);
            }
        } catch (Exception ignored) {}

        if ("oceanbase".equals(p)) {
            return new com.oceanbase.powermem.sdk.storage.oceanbase.OceanBaseGraphStore(config, effEmb, effLlm);
        }

        // Fallback: treat unknown as oceanbase (but likely not implemented).
        return new com.oceanbase.powermem.sdk.storage.oceanbase.OceanBaseGraphStore(config, effEmb, effLlm);
    }
}

