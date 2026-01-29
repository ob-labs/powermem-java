package com.oceanbase.powermem.sdk.integrations.llm;

/**
 * Qwen/DashScope LLM implementation (Java migration target).
 *
 * <p>Python reference: {@code src/powermem/integrations/llm/qwen.py} and {@code qwen_asr.py} (audio)</p>
 */
public class QwenLLM implements LLM {
    private final com.oceanbase.powermem.sdk.config.LlmConfig config;
    private final com.oceanbase.powermem.sdk.transport.JavaHttpTransport http;

    public QwenLLM(com.oceanbase.powermem.sdk.config.LlmConfig config) {
        this(config, new com.oceanbase.powermem.sdk.transport.JavaHttpTransport());
    }

    public QwenLLM(com.oceanbase.powermem.sdk.config.LlmConfig config, com.oceanbase.powermem.sdk.transport.JavaHttpTransport http) {
        this.config = config;
        this.http = http;
    }

    @Override
    public String generateResponse(java.util.List<com.oceanbase.powermem.sdk.model.Message> messages,
                                   java.util.Map<String, Object> responseFormat) {
        if (config == null || config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new com.oceanbase.powermem.sdk.exception.ApiException("Missing LLM apiKey");
        }
        String baseUrl = config.getBaseUrl();
        if (baseUrl != null && baseUrl.contains("compatible-mode")) {
            com.oceanbase.powermem.sdk.config.LlmConfig openAiLike = new com.oceanbase.powermem.sdk.config.LlmConfig();
            openAiLike.setProvider("openai");
            openAiLike.setApiKey(config.getApiKey());
            openAiLike.setModel(config.getModel());
            openAiLike.setBaseUrl(baseUrl);
            openAiLike.setTemperature(config.getTemperature());
            openAiLike.setMaxTokens(config.getMaxTokens());
            openAiLike.setTopP(config.getTopP());
            return new OpenAiLLM(openAiLike, http).generateResponse(messages, responseFormat);
        }
        // DashScope native text generation (best-effort).
        // Endpoint commonly used: /services/aigc/text-generation/generation
        String url = stripTrailingSlash(baseUrl == null ? "https://dashscope.aliyuncs.com/api/v1" : baseUrl)
                + "/services/aigc/text-generation/generation";

        java.util.List<java.util.Map<String, Object>> msgList = new java.util.ArrayList<>();
        if (messages != null) {
            for (com.oceanbase.powermem.sdk.model.Message m : messages) {
                if (m == null) {
                    continue;
                }
                java.util.Map<String, Object> mm = new java.util.HashMap<>();
                mm.put("role", m.getRole());
                mm.put("content", m.getContent());
                msgList.add(mm);
            }
        }

        java.util.Map<String, Object> input = new java.util.HashMap<>();
        input.put("messages", msgList);

        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("temperature", config.getTemperature());
        params.put("top_p", config.getTopP());
        if (config.getMaxTokens() > 0) {
            params.put("max_tokens", config.getMaxTokens());
        }
        // Ask for message-like response if supported; otherwise we still parse best-effort.
        params.put("result_format", "message");

        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("model", config.getModel());
        body.put("input", input);
        body.put("parameters", params);

        java.util.Map<String, String> headers = new java.util.HashMap<>();
        headers.put("Authorization", "Bearer " + config.getApiKey());

        com.oceanbase.powermem.sdk.json.JsonCodec json = new com.oceanbase.powermem.sdk.json.JacksonJsonCodec();
        String resp = http.postJson(url, headers, json.toJson(body), java.time.Duration.ofSeconds(120));
        java.util.Map<String, Object> respMap = json.fromJsonToMap(resp);
        Object output = respMap.get("output");
        if (output instanceof java.util.Map) {
            java.util.Map<?, ?> outMap = (java.util.Map<?, ?>) output;
            Object text = outMap.get("text");
            if (text != null) {
                return String.valueOf(text);
            }
            Object choices = outMap.get("choices");
            if (choices instanceof java.util.List && !((java.util.List<?>) choices).isEmpty()) {
                Object first = ((java.util.List<?>) choices).get(0);
                if (first instanceof java.util.Map) {
                    Object msg = ((java.util.Map<?, ?>) first).get("message");
                    if (msg instanceof java.util.Map) {
                        Object content = ((java.util.Map<?, ?>) msg).get("content");
                        if (content != null) {
                            return String.valueOf(content);
                        }
                    }
                }
            }
        }
        // Fallback: try OpenAI-like shape if gateway returns it.
        Object choicesObj = respMap.get("choices");
        if (choicesObj instanceof java.util.List && !((java.util.List<?>) choicesObj).isEmpty()) {
            Object first = ((java.util.List<?>) choicesObj).get(0);
            if (first instanceof java.util.Map) {
                Object msg = ((java.util.Map<?, ?>) first).get("message");
                if (msg instanceof java.util.Map) {
                    Object content = ((java.util.Map<?, ?>) msg).get("content");
                    if (content != null) {
                        return String.valueOf(content);
                    }
                }
            }
        }
        throw new com.oceanbase.powermem.sdk.exception.ApiException("Unexpected Qwen LLM response: " + resp);
    }

    @Override
    public LlmResponse generateResponseWithTools(java.util.List<com.oceanbase.powermem.sdk.model.Message> messages,
                                                 java.util.Map<String, Object> responseFormat,
                                                 java.util.List<java.util.Map<String, Object>> tools,
                                                 Object toolChoice) {
        // Best-effort: compatible-mode supports tools via OpenAI schema.
        String baseUrl = config == null ? null : config.getBaseUrl();
        if (baseUrl != null && baseUrl.contains("compatible-mode")) {
            com.oceanbase.powermem.sdk.config.LlmConfig openAiLike = new com.oceanbase.powermem.sdk.config.LlmConfig();
            openAiLike.setProvider("openai");
            openAiLike.setApiKey(config.getApiKey());
            openAiLike.setModel(config.getModel());
            openAiLike.setBaseUrl(baseUrl);
            openAiLike.setTemperature(config.getTemperature());
            openAiLike.setMaxTokens(config.getMaxTokens());
            openAiLike.setTopP(config.getTopP());
            return new OpenAiLLM(openAiLike, http).generateResponseWithTools(messages, responseFormat, tools, toolChoice);
        }
        // Native API: we keep content-only behavior (DashScope tool-calls vary by model/version).
        String content = generateResponse(messages, responseFormat);
        return new LlmResponse(content, null);
    }

    private static String stripTrailingSlash(String s) {
        if (s == null) {
            return "";
        }
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}

