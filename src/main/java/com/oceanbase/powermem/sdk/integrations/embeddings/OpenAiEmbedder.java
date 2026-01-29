package com.oceanbase.powermem.sdk.integrations.embeddings;

/**
 * OpenAI-compatible embedding implementation (Java migration target).
 *
 * <p>Python reference: {@code src/powermem/integrations/embeddings/openai.py}</p>
 */
public class OpenAiEmbedder implements Embedder {
    private final com.oceanbase.powermem.sdk.config.EmbedderConfig config;
    private final com.oceanbase.powermem.sdk.transport.JavaHttpTransport http;
    private final com.oceanbase.powermem.sdk.json.JsonCodec json = new com.oceanbase.powermem.sdk.json.JacksonJsonCodec();

    public OpenAiEmbedder(com.oceanbase.powermem.sdk.config.EmbedderConfig config) {
        this(config, new com.oceanbase.powermem.sdk.transport.JavaHttpTransport());
    }

    public OpenAiEmbedder(com.oceanbase.powermem.sdk.config.EmbedderConfig config, com.oceanbase.powermem.sdk.transport.JavaHttpTransport http) {
        this.config = config;
        this.http = http;
    }

    @Override
    public float[] embed(String text) {
        java.util.List<String> one = new java.util.ArrayList<>();
        one.add(text == null ? "" : text);
        java.util.List<float[]> out = embedBatch(one);
        return out.isEmpty() ? new float[0] : out.get(0);
    }

    @Override
    public java.util.List<float[]> embedBatch(java.util.List<String> texts) {
        if (config == null || config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new com.oceanbase.powermem.sdk.exception.ApiException("Missing embedding apiKey");
        }
        String baseUrl = config.getBaseUrl() == null || config.getBaseUrl().isBlank()
                ? "https://api.openai.com/v1"
                : config.getBaseUrl();
        String url = stripTrailingSlash(baseUrl) + "/embeddings";

        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("model", config.getModel());
        body.put("input", texts == null ? java.util.Collections.emptyList() : texts);
        // Python parity: OpenAI embedding supports optional dimensions parameter
        if (config.getEmbeddingDims() > 0) {
            body.put("dimensions", config.getEmbeddingDims());
        }

        java.util.Map<String, String> headers = new java.util.HashMap<>();
        headers.put("Authorization", "Bearer " + config.getApiKey());

        String resp = http.postJson(url, headers, json.toJson(body), java.time.Duration.ofSeconds(60));
        java.util.Map<String, Object> respMap = json.fromJsonToMap(resp);
        Object dataObj = respMap.get("data");
        if (!(dataObj instanceof java.util.List)) {
            throw new com.oceanbase.powermem.sdk.exception.ApiException("Unexpected embeddings response: missing data");
        }
        java.util.List<?> data = (java.util.List<?>) dataObj;
        java.util.List<float[]> results = new java.util.ArrayList<>();
        for (Object item : data) {
            if (!(item instanceof java.util.Map)) {
                continue;
            }
            Object emb = ((java.util.Map<?, ?>) item).get("embedding");
            results.add(toFloatArray(emb));
        }
        return results;
    }

    private static float[] toFloatArray(Object emb) {
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

