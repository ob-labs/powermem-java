package com.oceanbase.powermem.sdk.integrations.llm;

/**
 * Structured response for LLM calls when tools are enabled.
 *
 * <p>Python reference: OpenAI/Qwen tool calls response contains message content and optional tool_calls.</p>
 */
public class LlmResponse {
    private String content;
    private java.util.List<java.util.Map<String, Object>> toolCalls;

    public LlmResponse() {}

    public LlmResponse(String content, java.util.List<java.util.Map<String, Object>> toolCalls) {
        this.content = content;
        this.toolCalls = toolCalls;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public java.util.List<java.util.Map<String, Object>> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(java.util.List<java.util.Map<String, Object>> toolCalls) {
        this.toolCalls = toolCalls;
    }
}

