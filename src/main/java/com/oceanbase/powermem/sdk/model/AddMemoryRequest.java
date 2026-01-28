package com.oceanbase.powermem.sdk.model;

/**
 * Request DTO for adding memory.
 *
 * <p>Python reference: {@code Memory.add(...)} signature in {@code src/powermem/core/memory.py} and
 * REST request model {@code MemoryCreate} in {@code benchmark/server/main.py}.</p>
 */
public class AddMemoryRequest {
    /**
     * Optional raw text input. If {@link #messages} is provided, {@code text} may be omitted.
     */
    private String text;

    /**
     * Optional chat messages (role+content) input.
     */
    private java.util.List<Message> messages;

    private String userId;
    private String agentId;
    private String runId;
    private java.util.Map<String, Object> metadata;
    private java.util.Map<String, Object> filters;
    private String scope;
    private String memoryType;
    private String prompt;
    private boolean infer = true;

    public AddMemoryRequest() {}

    public static AddMemoryRequest ofText(String text, String userId) {
        AddMemoryRequest r = new AddMemoryRequest();
        r.setText(text);
        r.setUserId(userId);
        return r;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public java.util.List<Message> getMessages() {
        return messages;
    }

    public void setMessages(java.util.List<Message> messages) {
        this.messages = messages;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public java.util.Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(java.util.Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public java.util.Map<String, Object> getFilters() {
        return filters;
    }

    public void setFilters(java.util.Map<String, Object> filters) {
        this.filters = filters;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getMemoryType() {
        return memoryType;
    }

    public void setMemoryType(String memoryType) {
        this.memoryType = memoryType;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public boolean isInfer() {
        return infer;
    }

    public void setInfer(boolean infer) {
        this.infer = infer;
    }
}

