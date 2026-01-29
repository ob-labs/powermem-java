package com.oceanbase.powermem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oceanbase.powermem.sdk.config.LlmConfig;
import com.oceanbase.powermem.sdk.integrations.llm.LlmResponse;
import com.oceanbase.powermem.sdk.integrations.llm.OpenAiLLM;
import com.oceanbase.powermem.sdk.model.Message;
import com.oceanbase.powermem.sdk.transport.JavaHttpTransport;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class LlmToolsTest {

    static class FakeTransport extends JavaHttpTransport {
        String lastUrl;
        String lastBody;

        FakeTransport() {
            super(Duration.ofSeconds(1));
        }

        @Override
        public String postJson(String url, java.util.Map<String, String> headers, String jsonBody, Duration timeout) {
            this.lastUrl = url;
            this.lastBody = jsonBody;
            try {
                ObjectMapper om = new ObjectMapper();
                // Build a valid OpenAI-like response JSON without manual escaping.
                Map<String, Object> resp = Map.of(
                        "choices", List.of(
                                Map.of(
                                        "message", Map.of(
                                                "content", "ok",
                                                "tool_calls", List.of(
                                                        Map.of(
                                                                "id", "call_1",
                                                                "type", "function",
                                                                "function", Map.of(
                                                                        "name", "do_something",
                                                                        "arguments", "{\"x\":1}"
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                );
                return om.writeValueAsString(resp);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    void testOpenAiToolsToolChoice_areSentAndParsed() throws Exception {
        FakeTransport http = new FakeTransport();
        LlmConfig cfg = LlmConfig.openAi("test-key", "gpt-4o-mini");
        cfg.setBaseUrl("https://example.com/v1");
        OpenAiLLM llm = new OpenAiLLM(cfg, http);

        List<Message> messages = List.of(new Message("user", "hi"));
        List<Map<String, Object>> tools = List.of(
                Map.of(
                        "type", "function",
                        "function", Map.of(
                                "name", "do_something",
                                "description", "demo",
                                "parameters", Map.of("type", "object")
                        )
                )
        );
        Object toolChoice = "auto";

        LlmResponse r = llm.generateResponseWithTools(messages, null, tools, toolChoice);
        assertNotNull(r);
        assertEquals("ok", r.getContent());
        assertNotNull(r.getToolCalls());
        assertEquals(1, r.getToolCalls().size());
        assertEquals("function", String.valueOf(r.getToolCalls().get(0).get("type")));

        // verify request body contains tools/tool_choice
        ObjectMapper om = new ObjectMapper();
        @SuppressWarnings("unchecked")
        Map<String, Object> sent = om.readValue(http.lastBody, Map.class);
        assertTrue(sent.containsKey("tools"));
        assertEquals("auto", String.valueOf(sent.get("tool_choice")));
        assertTrue(http.lastUrl.endsWith("/chat/completions"));
    }
}

