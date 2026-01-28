package com.oceanbase.powermem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oceanbase.powermem.sdk.config.RerankConfig;
import com.oceanbase.powermem.sdk.integrations.rerank.QwenReranker;
import com.oceanbase.powermem.sdk.integrations.rerank.RerankResult;
import com.oceanbase.powermem.sdk.json.JacksonJsonCodec;
import com.oceanbase.powermem.sdk.transport.JavaHttpTransport;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class QwenRerankerTest {

    static class FakeTransport extends JavaHttpTransport {
        String lastUrl;
        Map<String, String> lastHeaders;
        String lastBody;
        String responseJson;

        FakeTransport(String responseJson) {
            super(Duration.ofSeconds(1));
            this.responseJson = responseJson;
        }

        @Override
        public String postJson(String url, Map<String, String> headers, String jsonBody, Duration timeout) {
            this.lastUrl = url;
            this.lastHeaders = headers;
            this.lastBody = jsonBody;
            return responseJson;
        }
    }

    @Test
    void testGteRerankV2_requestBodyAndParsing() throws Exception {
        ObjectMapper om = new ObjectMapper();
        String resp = om.writeValueAsString(Map.of(
                "output", Map.of(
                        "results", List.of(
                                Map.of("index", 1, "relevance_score", 0.9),
                                Map.of("index", 0, "relevance_score", 0.1)
                        )
                )
        ));
        FakeTransport http = new FakeTransport(resp);

        RerankConfig cfg = new RerankConfig();
        cfg.setProvider("qwen");
        cfg.setApiKey("test-key");
        cfg.setModel("gte-rerank-v2");
        cfg.setTopK(2);
        cfg.setBaseUrl("https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank");

        QwenReranker rr = new QwenReranker(cfg, http, new JacksonJsonCodec());
        List<RerankResult> results = rr.rerank("query", List.of("docA", "docB"), 2);
        assertEquals(2, results.size());
        assertEquals(1, results.get(0).getIndex());
        assertEquals(0.9, results.get(0).getScore(), 1e-9);
        assertEquals(0, results.get(1).getIndex());

        assertNotNull(http.lastHeaders);
        assertEquals("Bearer test-key", http.lastHeaders.get("Authorization"));
        assertEquals(cfg.getBaseUrl(), http.lastUrl);

        @SuppressWarnings("unchecked")
        Map<String, Object> sent = om.readValue(http.lastBody, Map.class);
        assertEquals("gte-rerank-v2", String.valueOf(sent.get("model")));
        assertTrue(sent.containsKey("input"));
        assertTrue(sent.containsKey("parameters"));
        @SuppressWarnings("unchecked")
        Map<String, Object> input = (Map<String, Object>) sent.get("input");
        assertEquals("query", String.valueOf(input.get("query")));
        assertEquals(2, ((List<?>) input.get("documents")).size());
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) sent.get("parameters");
        assertEquals(2, Integer.parseInt(String.valueOf(params.get("top_n"))));
        assertEquals("false", String.valueOf(params.get("return_documents")));
    }

    @Test
    void testQwen3Rerank_requestBodyAndParsing() throws Exception {
        ObjectMapper om = new ObjectMapper();
        String resp = om.writeValueAsString(Map.of(
                "output", Map.of(
                        "results", List.of(
                                Map.of("index", 0, "relevance_score", 0.7)
                        )
                )
        ));
        FakeTransport http = new FakeTransport(resp);

        RerankConfig cfg = new RerankConfig();
        cfg.setProvider("qwen");
        cfg.setApiKey("test-key");
        cfg.setModel("qwen3-rerank");
        cfg.setTopK(1);
        cfg.setBaseUrl("https://dashscope-intl.aliyuncs.com/compatible-api/v1/reranks");

        QwenReranker rr = new QwenReranker(cfg, http, new JacksonJsonCodec());
        List<RerankResult> results = rr.rerank("query", List.of("docA", "docB"), 1);
        assertEquals(1, results.size());
        assertEquals(0, results.get(0).getIndex());
        assertEquals(0.7, results.get(0).getScore(), 1e-9);

        @SuppressWarnings("unchecked")
        Map<String, Object> sent = om.readValue(http.lastBody, Map.class);
        assertEquals("qwen3-rerank", String.valueOf(sent.get("model")));
        assertEquals("query", String.valueOf(sent.get("query")));
        assertTrue(sent.containsKey("documents"));
        assertEquals(1, Integer.parseInt(String.valueOf(sent.get("top_n"))));
        assertFalse(sent.containsKey("input"));
    }
}

