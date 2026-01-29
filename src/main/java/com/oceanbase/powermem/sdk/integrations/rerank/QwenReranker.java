package com.oceanbase.powermem.sdk.integrations.rerank;

/**
 * Qwen reranker implementation (Java migration target).
 *
 * <p>Python reference: {@code src/powermem/integrations/rerank/qwen.py}</p>
 */
public class QwenReranker implements Reranker {
    private final com.oceanbase.powermem.sdk.config.RerankConfig config;
    private final com.oceanbase.powermem.sdk.transport.JavaHttpTransport transport;
    private final com.oceanbase.powermem.sdk.json.JsonCodec json;

    public QwenReranker(com.oceanbase.powermem.sdk.config.RerankConfig config,
                        com.oceanbase.powermem.sdk.transport.JavaHttpTransport transport,
                        com.oceanbase.powermem.sdk.json.JsonCodec json) {
        this.config = config == null ? new com.oceanbase.powermem.sdk.config.RerankConfig() : config;
        this.transport = transport == null ? new com.oceanbase.powermem.sdk.transport.JavaHttpTransport() : transport;
        this.json = json == null ? new com.oceanbase.powermem.sdk.json.JacksonJsonCodec() : json;
    }

    @Override
    public java.util.List<RerankResult> rerank(String query, java.util.List<String> documents, int topN) {
        if (query == null || query.isBlank()) {
            return java.util.Collections.emptyList();
        }
        if (documents == null || documents.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        String apiKey = config.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new com.oceanbase.powermem.sdk.exception.ApiException("Reranker apiKey is required (RERANKER_API_KEY)");
        }
        String model = config.getModel();
        if (model == null || model.isBlank()) {
            // Prefer China region model by default (qwen3-rerank is intl-only per docs)
            model = "gte-rerank-v2";
        }
        int effectiveTopN = topN > 0 ? topN : documents.size();

        String url = resolveRerankUrl(config.getBaseUrl(), model);
        java.util.Map<String, Object> body = buildRequestBody(url, model, query.trim(), documents, effectiveTopN);
        String resp = transport.postJson(url,
                java.util.Map.of("Authorization", "Bearer " + apiKey),
                json.toJson(body),
                java.time.Duration.ofSeconds(60));

        java.util.Map<String, Object> root = json.fromJsonToMap(resp);
        Object outObj = root.get("output");
        if (!(outObj instanceof java.util.Map)) {
            // some endpoints might return results at top-level
            outObj = root;
        }
        java.util.Map<?, ?> out = outObj instanceof java.util.Map ? (java.util.Map<?, ?>) outObj : java.util.Collections.emptyMap();
        Object resultsObj = out.get("results");
        if (!(resultsObj instanceof java.util.List)) {
            return java.util.Collections.emptyList();
        }
        java.util.List<?> results = (java.util.List<?>) resultsObj;
        java.util.List<RerankResult> parsed = new java.util.ArrayList<>();
        for (Object r : results) {
            if (!(r instanceof java.util.Map)) continue;
            java.util.Map<?, ?> m = (java.util.Map<?, ?>) r;
            Object idx = m.get("index");
            Object sc = m.get("relevance_score");
            if (idx == null) continue;
            int i;
            try {
                i = Integer.parseInt(String.valueOf(idx));
            } catch (Exception ignored) {
                continue;
            }
            double s = 0.0;
            if (sc != null) {
                try {
                    s = Double.parseDouble(String.valueOf(sc));
                } catch (Exception ignored) {
                    s = 0.0;
                }
            }
            parsed.add(new RerankResult(i, s));
        }
        parsed.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return parsed;
    }

    private static java.util.Map<String, Object> buildRequestBody(String url, String model, String query, java.util.List<String> documents, int topN) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("model", model);

        // compatible-api endpoint uses flat params (qwen3-rerank)
        boolean compatible = url.contains("/compatible-api/") || url.endsWith("/reranks");
        if (compatible && "qwen3-rerank".equalsIgnoreCase(model)) {
            body.put("query", query);
            body.put("documents", documents);
            body.put("top_n", topN);
            // instruct optional; keep default behavior
            return body;
        }

        // dashscope China endpoint uses nested input/parameters (gte-rerank-v2)
        java.util.Map<String, Object> input = new java.util.HashMap<>();
        input.put("query", query);
        input.put("documents", documents);
        body.put("input", input);
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("top_n", topN);
        params.put("return_documents", false);
        body.put("parameters", params);
        return body;
    }

    private static String resolveRerankUrl(String baseUrl, String model) {
        String b = baseUrl == null ? "" : baseUrl.trim();
        if (!b.isEmpty()) {
            // If user provided full endpoint, trust it.
            if (b.contains("/services/rerank/text-rerank/text-rerank") || b.contains("/compatible-api/v1/reranks") || b.endsWith("/reranks")) {
                return b;
            }
            // If user provided "https://.../api/v1"
            if (b.endsWith("/api/v1")) {
                return b + "/services/rerank/text-rerank/text-rerank";
            }
            // If user provided "https://.../compatible-mode/v1" (LLM base), convert to host/api/v1
            if (b.endsWith("/compatible-mode/v1")) {
                try {
                    java.net.URI u = java.net.URI.create(b);
                    String origin = u.getScheme() + "://" + u.getHost();
                    return origin + "/api/v1/services/rerank/text-rerank/text-rerank";
                } catch (Exception ignored) {
                    // fallthrough
                }
            }
            // If user provided origin
            if (b.startsWith("http://") || b.startsWith("https://")) {
                return b + "/api/v1/services/rerank/text-rerank/text-rerank";
            }
        }

        // Defaults:
        // - qwen3-rerank is intl-only and uses compatible-api /reranks
        if ("qwen3-rerank".equalsIgnoreCase(model)) {
            return "https://dashscope-intl.aliyuncs.com/compatible-api/v1/reranks";
        }
        // - gte-rerank-v2 is China region
        return "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";
    }
}

