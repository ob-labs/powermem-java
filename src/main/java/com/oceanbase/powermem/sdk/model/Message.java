package com.oceanbase.powermem.sdk.model;

/**
 * Chat message in OpenAI-compatible format: {@code role} + {@code content}.
 *
 * <p>Python reference: message dict usage in {@code src/powermem/core/memory.py} and request model
 * {@code Message} in {@code benchmark/server/main.py}.</p>
 */
public class Message {
    private String role;
    private String content;

    public Message() {}

    public Message(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}

