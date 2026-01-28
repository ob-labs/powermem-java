package com.oceanbase.powermem.sdk.integrations.llm;

/**
 * Mock LLM implementation for tests and local development.
 *
 * <p>Python reference: {@code src/powermem/integrations/llm/mock.py}</p>
 */
public class MockLLM implements LLM {
    @Override
    public String generateResponse(java.util.List<com.oceanbase.powermem.sdk.model.Message> messages,
                                   java.util.Map<String, Object> responseFormat) {
        // Minimal deterministic behavior:
        // - If prompt asks for {"facts": ...} return one deterministic fact (so intelligent pipeline can be exercised).
        // - If prompt asks for {"memory": ...} return one ADD action with a placeholder.
        String joined = "";
        if (messages != null) {
            StringBuilder sb = new StringBuilder();
            for (com.oceanbase.powermem.sdk.model.Message m : messages) {
                if (m != null && m.getContent() != null) {
                    sb.append(m.getContent()).append("\n");
                }
            }
            joined = sb.toString();
        }
        if (joined.contains("\"facts\"") || joined.toLowerCase().contains("extract facts")) {
            return "{\"facts\":[\"(mock) fact\"]}";
        }
        if (joined.contains("\"memory\"") || joined.toLowerCase().contains("memory")) {
            return "{\"memory\":[{\"id\":\"1\",\"text\":\"(mock) new memory\",\"event\":\"ADD\"}]}";
        }
        return "{}";
    }
}

