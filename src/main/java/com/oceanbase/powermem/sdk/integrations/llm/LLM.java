package com.oceanbase.powermem.sdk.integrations.llm;

/**
 * LLM interface used by the PowerMem core to perform fact extraction, memory update decisions, etc.
 *
 * <p>Python reference: {@code src/powermem/integrations/llm/base.py}</p>
 */
public interface LLM {
    /**
     * Generate a response from chat messages.
     *
     * <p>Python reference: {@code llm.generate_response(messages=[...], response_format={...})}.</p>
     *
     * @param messages chat messages
     * @param responseFormat optional structured output hint (e.g. {"type":"json_object"})
     * @return model text output (typically JSON string when responseFormat requests JSON)
     */
    String generateResponse(java.util.List<com.oceanbase.powermem.sdk.model.Message> messages,
                            java.util.Map<String, Object> responseFormat);

    /**
     * Generate response with OpenAI-style tools/tool_choice support.
     *
     * <p>Default: call {@link #generateResponse(java.util.List, java.util.Map)} and return content only.</p>
     *
     * @param messages chat messages
     * @param responseFormat optional structured output hint
     * @param tools optional tool schemas (OpenAI-compatible)
     * @param toolChoice tool selection (e.g. "auto", "none", or {"type":"function","function":{"name":"..."}})
     */
    default LlmResponse generateResponseWithTools(java.util.List<com.oceanbase.powermem.sdk.model.Message> messages,
                                                  java.util.Map<String, Object> responseFormat,
                                                  java.util.List<java.util.Map<String, Object>> tools,
                                                  Object toolChoice) {
        String content = generateResponse(messages, responseFormat);
        return new LlmResponse(content, null);
    }
}

