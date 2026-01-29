package com.oceanbase.powermem.sdk.integrations.embeddings;

/**
 * Qwen/DashScope embedding implementation (Java migration target).
 *
 * <p>Python reference: {@code src/powermem/integrations/embeddings/qwen.py}</p>
 */
public class QwenEmbedder implements Embedder {
    private final com.oceanbase.powermem.sdk.config.EmbedderConfig config;
    private final com.oceanbase.powermem.sdk.transport.JavaHttpTransport http;
    private final com.oceanbase.powermem.sdk.json.JsonCodec json = new com.oceanbase.powermem.sdk.json.JacksonJsonCodec();

    public QwenEmbedder(com.oceanbase.powermem.sdk.config.EmbedderConfig config) {
        this(config, new com.oceanbase.powermem.sdk.transport.JavaHttpTransport());
    }

    public QwenEmbedder(com.oceanbase.powermem.sdk.config.EmbedderConfig config, com.oceanbase.powermem.sdk.transport.JavaHttpTransport http) {
        this.config = config;
        this.http = http;
    }

    @Override
    public float[] embed(String text) {
        java.util.List<String> one = new java.util.ArrayList<>();
        one.add(text == null ? "" : text);
        java.util.List<float[]> out = embedBatch(one, null);
        return out.isEmpty() ? new float[0] : out.get(0);
    }

    @Override
    public float[] embed(String text, String memoryAction) {
        java.util.List<String> one = new java.util.ArrayList<>();
        one.add(text == null ? "" : text);
        java.util.List<float[]> out = embedBatch(one, memoryAction);
        return out.isEmpty() ? new float[0] : out.get(0);
    }

    @Override
    public java.util.List<float[]> embedBatch(java.util.List<String> texts) {
        return embedBatch(texts, null);
    }

    @Override
    public java.util.List<float[]> embedBatch(java.util.List<String> texts, String memoryAction) {
        if (config == null || config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new com.oceanbase.powermem.sdk.exception.ApiException("Missing embedding apiKey");
        }

        // Prefer OpenAI-compatible mode if user points baseUrl to a compatible endpoint.
        String baseUrl = config.getBaseUrl();
        if (baseUrl != null && baseUrl.contains("compatible-mode")) {
            // compatible-mode is OpenAI-compatible
            com.oceanbase.powermem.sdk.config.EmbedderConfig openAiLike = new com.oceanbase.powermem.sdk.config.EmbedderConfig();
            openAiLike.setProvider("openai");
            openAiLike.setApiKey(config.getApiKey());
            openAiLike.setModel(config.getModel());
            openAiLike.setBaseUrl(baseUrl);
            return new OpenAiEmbedder(openAiLike, http).embedBatch(texts);
        }

        // DashScope native embedding API (best-effort).
        // Endpoint commonly used: /services/embeddings/text-embedding/text-embedding
        String url = stripTrailingSlash(baseUrl == null ? "https://dashscope.aliyuncs.com/api/v1" : baseUrl)
                + "/services/embeddings/text-embedding/text-embedding";

        // Python parity:
        // - DashScope text-embedding-v4 supports text_type: "document" (add/update) vs "query" (search)
        // - and dimension parameter.
        String textType = "document";
        if (memoryAction != null && "search".equalsIgnoreCase(memoryAction)) {
            textType = "query";
        }

        java.util.Map<String, Object> input = new java.util.HashMap<>();
        input.put("texts", texts == null ? java.util.Collections.emptyList() : texts);

        java.util.Map<String, Object> parameters = new java.util.HashMap<>();
        parameters.put("text_type", textType);
        if (config.getEmbeddingDims() > 0) {
            parameters.put("dimension", config.getEmbeddingDims());
        }

        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("model", config.getModel());
        body.put("input", input);
        body.put("parameters", parameters);

        java.util.Map<String, String> headers = new java.util.HashMap<>();
        headers.put("Authorization", "Bearer " + config.getApiKey());

        String resp = http.postJson(url, headers, json.toJson(body), java.time.Duration.ofSeconds(60));
        java.util.Map<String, Object> respMap = json.fromJsonToMap(resp);

        // Try multiple response shapes.
        java.util.List<float[]> results = new java.util.ArrayList<>();
        Object outObj = respMap.get("output");
        if (outObj instanceof java.util.Map) {
            Object embObj = ((java.util.Map<?, ?>) outObj).get("embeddings");
            if (embObj instanceof java.util.List) {
                for (Object item : (java.util.List<?>) embObj) {
                    if (!(item instanceof java.util.Map)) {
                        continue;
                    }
                    Object embedding = ((java.util.Map<?, ?>) item).get("embedding");
                    results.add(OpenAiEmbedder_toFloatArray(embedding));
                }
                return results;
            }
        }
        // fallback openai-like
        Object dataObj = respMap.get("data");
        if (dataObj instanceof java.util.List) {
            for (Object item : (java.util.List<?>) dataObj) {
                if (!(item instanceof java.util.Map)) {
                    continue;
                }
                Object embedding = ((java.util.Map<?, ?>) item).get("embedding");
                results.add(OpenAiEmbedder_toFloatArray(embedding));
            }
            return results;
        }
        throw new com.oceanbase.powermem.sdk.exception.ApiException("Unexpected Qwen embedding response: " + resp);
    }

    private static float[] OpenAiEmbedder_toFloatArray(Object emb) {
        if (!(emb instanceof java.util.List)) {
            return new float[0];
        }
        java.util.List<?> l = (java.util.List<?>) emb;
        float[] out = new float[l.size()];
        for (int i = 0; i < l.size(); i++) {
            Object v = l.get(i);
            if (v instanceof Number) {
                out[i] = ((Number) v).floatValue();
            } else {
                try {
                    out[i] = Float.parseFloat(String.valueOf(v));
                } catch (Exception ex) {
                    out[i] = 0.0f;
                }
            }
        }
        return out;
    }

    private static String stripTrailingSlash(String s) {
        if (s == null) {
            return "";
        }
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}

