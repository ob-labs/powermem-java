package com.oceanbase.powermem.sdk.integrations.llm;

/**
 * OpenAI-compatible LLM implementation (Java migration target).
 *
 * <p>Python reference: {@code src/powermem/integrations/llm/openai.py} and
 * {@code src/powermem/integrations/llm/openai_structured.py}</p>
 */
public class OpenAiLLM implements LLM {
    private final com.oceanbase.powermem.sdk.config.LlmConfig config;
    private final com.oceanbase.powermem.sdk.transport.JavaHttpTransport http;
    private final com.oceanbase.powermem.sdk.json.JsonCodec json = new com.oceanbase.powermem.sdk.json.JacksonJsonCodec();

    public OpenAiLLM(com.oceanbase.powermem.sdk.config.LlmConfig config) {
        this(config, new com.oceanbase.powermem.sdk.transport.JavaHttpTransport());
    }

    public OpenAiLLM(com.oceanbase.powermem.sdk.config.LlmConfig config, com.oceanbase.powermem.sdk.transport.JavaHttpTransport http) {
        this.config = config;
        this.http = http;
    }

    @Override
    public String generateResponse(java.util.List<com.oceanbase.powermem.sdk.model.Message> messages,
                                   java.util.Map<String, Object> responseFormat) {
        if (config == null || config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new com.oceanbase.powermem.sdk.exception.ApiException("Missing LLM apiKey");
        }
        String baseUrl = config.getBaseUrl() == null || config.getBaseUrl().isBlank()
                ? "https://api.openai.com/v1"
                : config.getBaseUrl();
        String url = stripTrailingSlash(baseUrl) + "/chat/completions";

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

        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("model", config.getModel());
        body.put("messages", msgList);
        body.put("temperature", config.getTemperature());
        if (config.getMaxTokens() > 0) {
            body.put("max_tokens", config.getMaxTokens());
        }
        if (config.getTopP() > 0) {
            body.put("top_p", config.getTopP());
        }
        if (responseFormat != null && !responseFormat.isEmpty()) {
            body.put("response_format", responseFormat);
        }

        java.util.Map<String, String> headers = new java.util.HashMap<>();
        headers.put("Authorization", "Bearer " + config.getApiKey());

        String resp = http.postJson(url, headers, json.toJson(body), java.time.Duration.ofSeconds(120));
        java.util.Map<String, Object> respMap = json.fromJsonToMap(resp);
        Object choicesObj = respMap.get("choices");
        if (!(choicesObj instanceof java.util.List) || ((java.util.List<?>) choicesObj).isEmpty()) {
            throw new com.oceanbase.powermem.sdk.exception.ApiException("Unexpected LLM response: missing choices");
        }
        Object first = ((java.util.List<?>) choicesObj).get(0);
        if (!(first instanceof java.util.Map)) {
            throw new com.oceanbase.powermem.sdk.exception.ApiException("Unexpected LLM response: invalid choice type");
        }
        Object messageObj = ((java.util.Map<?, ?>) first).get("message");
        if (!(messageObj instanceof java.util.Map)) {
            throw new com.oceanbase.powermem.sdk.exception.ApiException("Unexpected LLM response: missing message");
        }
        Object content = ((java.util.Map<?, ?>) messageObj).get("content");
        return content == null ? "" : String.valueOf(content);
    }

    @Override
    public LlmResponse generateResponseWithTools(java.util.List<com.oceanbase.powermem.sdk.model.Message> messages,
                                                 java.util.Map<String, Object> responseFormat,
                                                 java.util.List<java.util.Map<String, Object>> tools,
                                                 Object toolChoice) {
        if (config == null || config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new com.oceanbase.powermem.sdk.exception.ApiException("Missing LLM apiKey");
        }
        String baseUrl = config.getBaseUrl() == null || config.getBaseUrl().isBlank()
                ? "https://api.openai.com/v1"
                : config.getBaseUrl();
        String url = stripTrailingSlash(baseUrl) + "/chat/completions";

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

        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("model", config.getModel());
        body.put("messages", msgList);
        body.put("temperature", config.getTemperature());
        if (config.getMaxTokens() > 0) {
            body.put("max_tokens", config.getMaxTokens());
        }
        if (config.getTopP() > 0) {
            body.put("top_p", config.getTopP());
        }
        if (responseFormat != null && !responseFormat.isEmpty()) {
            body.put("response_format", responseFormat);
        }
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            if (toolChoice != null) {
                body.put("tool_choice", toolChoice);
            }
        }

        java.util.Map<String, String> headers = new java.util.HashMap<>();
        headers.put("Authorization", "Bearer " + config.getApiKey());

        String resp = http.postJson(url, headers, json.toJson(body), java.time.Duration.ofSeconds(120));
        java.util.Map<String, Object> respMap = json.fromJsonToMap(resp);
        Object choicesObj = respMap.get("choices");
        if (!(choicesObj instanceof java.util.List) || ((java.util.List<?>) choicesObj).isEmpty()) {
            throw new com.oceanbase.powermem.sdk.exception.ApiException("Unexpected LLM response: missing choices");
        }
        Object first = ((java.util.List<?>) choicesObj).get(0);
        if (!(first instanceof java.util.Map)) {
            throw new com.oceanbase.powermem.sdk.exception.ApiException("Unexpected LLM response: invalid choice type");
        }
        Object messageObj = ((java.util.Map<?, ?>) first).get("message");
        if (!(messageObj instanceof java.util.Map)) {
            throw new com.oceanbase.powermem.sdk.exception.ApiException("Unexpected LLM response: missing message");
        }
        java.util.Map<?, ?> msg = (java.util.Map<?, ?>) messageObj;
        Object contentObj = msg.get("content");

        java.util.List<java.util.Map<String, Object>> toolCallsOut = null;
        Object toolCallsObj = msg.get("tool_calls");
        if (toolCallsObj instanceof java.util.List) {
            toolCallsOut = new java.util.ArrayList<>();
            for (Object tc : (java.util.List<?>) toolCallsObj) {
                if (tc instanceof java.util.Map) {
                    java.util.Map<String, Object> safe = new java.util.HashMap<>();
                    java.util.Map<?, ?> raw = (java.util.Map<?, ?>) tc;
                    for (java.util.Map.Entry<?, ?> e : raw.entrySet()) {
                        if (e.getKey() == null) continue;
                        safe.put(String.valueOf(e.getKey()), e.getValue());
                    }
                    toolCallsOut.add(safe);
                }
            }
        }
        return new LlmResponse(contentObj == null ? "" : String.valueOf(contentObj), toolCallsOut);
    }

    private static String stripTrailingSlash(String s) {
        if (s == null) {
            return "";
        }
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}

