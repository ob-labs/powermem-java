package com.oceanbase.powermem;

import com.oceanbase.powermem.sdk.config.MemoryConfig;
import com.oceanbase.powermem.sdk.config.ConfigLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Offline test for graph_store.llm/embedder override parsing.
 *
 * <p>We primarily validate that ConfigLoader wires the nested config objects and does not affect
 * the top-level llm/embedder config.</p>
 */
public class GraphStoreConfigOverrideTest {

    @Test
    void testGraphStoreNestedLlmAndEmbedder_areParsed() {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        m.put("GRAPH_STORE_ENABLED", "true");
        m.put("GRAPH_STORE_PROVIDER", "oceanbase");

        m.put("LLM_PROVIDER", "mock");
        m.put("EMBEDDING_PROVIDER", "mock");

        m.put("GRAPH_STORE_LLM_PROVIDER", "openai");
        m.put("GRAPH_STORE_LLM_API_KEY", "k1");
        m.put("GRAPH_STORE_LLM_MODEL", "gpt-4o-mini");
        m.put("GRAPH_STORE_LLM_BASE_URL", "https://example.com/v1");

        m.put("GRAPH_STORE_EMBEDDING_PROVIDER", "qwen");
        m.put("GRAPH_STORE_EMBEDDING_API_KEY", "k2");
        m.put("GRAPH_STORE_EMBEDDING_MODEL", "text-embedding-v4");
        m.put("GRAPH_STORE_EMBEDDING_DIMS", "1536");
        m.put("GRAPH_STORE_EMBEDDING_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1");

        MemoryConfig cfg = ConfigLoader.fromMap(m);

        assertNotNull(cfg.getGraphStore());
        assertTrue(cfg.getGraphStore().isEnabled());

        // top-level remains mock
        assertEquals("mock", cfg.getLlm().getProvider());
        assertEquals("mock", cfg.getEmbedder().getProvider());

        // graph_store overrides
        assertNotNull(cfg.getGraphStore().getLlm());
        assertEquals("openai", cfg.getGraphStore().getLlm().getProvider());
        assertEquals("k1", cfg.getGraphStore().getLlm().getApiKey());
        assertEquals("gpt-4o-mini", cfg.getGraphStore().getLlm().getModel());
        assertEquals("https://example.com/v1", cfg.getGraphStore().getLlm().getBaseUrl());

        assertNotNull(cfg.getGraphStore().getEmbedder());
        assertEquals("qwen", cfg.getGraphStore().getEmbedder().getProvider());
        assertEquals("k2", cfg.getGraphStore().getEmbedder().getApiKey());
        assertEquals("text-embedding-v4", cfg.getGraphStore().getEmbedder().getModel());
        assertEquals(1536, cfg.getGraphStore().getEmbedder().getEmbeddingDims());
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1", cfg.getGraphStore().getEmbedder().getBaseUrl());
    }
}

