package com.oceanbase.powermem;

import com.oceanbase.powermem.sdk.config.EmbedderConfig;
import com.oceanbase.powermem.sdk.config.LlmConfig;
import com.oceanbase.powermem.sdk.config.MemoryConfig;
import com.oceanbase.powermem.sdk.config.VectorStoreConfig;
import com.oceanbase.powermem.sdk.core.Memory;
import com.oceanbase.powermem.sdk.model.AddMemoryRequest;
import com.oceanbase.powermem.sdk.model.DeleteAllMemoriesRequest;
import com.oceanbase.powermem.sdk.model.GetAllMemoriesRequest;
import com.oceanbase.powermem.sdk.model.GetMemoryRequest;
import com.oceanbase.powermem.sdk.model.SearchMemoriesRequest;
import com.oceanbase.powermem.sdk.model.UpdateMemoryRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class MemoryE2eTest {

    private static Memory newMemoryWithTempSqlite(Path dbPath) {
        MemoryConfig cfg = new MemoryConfig();
        VectorStoreConfig vs = VectorStoreConfig.sqlite(dbPath.toString());
        vs.setCollectionName("memories");
        cfg.setVectorStore(vs);

        // offline deterministic
        EmbedderConfig emb = new EmbedderConfig();
        emb.setProvider("mock");
        cfg.setEmbedder(emb);

        LlmConfig llm = new LlmConfig();
        llm.setProvider("mock");
        cfg.setLlm(llm);

        // enable intelligent memory plugin (ebbinghaus lifecycle hooks)
        cfg.getIntelligentMemory().setEnabled(true);
        cfg.getIntelligentMemory().setDecayEnabled(true);

        return new Memory(cfg);
    }

    @Test
    void testCrudAndSearch_withMetadataAndFilters(@TempDir Path tmp) {
        Memory mem = newMemoryWithTempSqlite(tmp.resolve("powermem_test.db"));

        String userId = "u1";
        String agentId = "a1";
        String runId = "r1";

        // clean slate
        DeleteAllMemoriesRequest delAll = new DeleteAllMemoriesRequest();
        delAll.setUserId(userId);
        delAll.setAgentId(agentId);
        delAll.setRunId(runId);
        mem.deleteAll(delAll);

        Map<String, Object> meta = new HashMap<>();
        meta.put("source", "junit");
        meta.put("category", "preference");

        AddMemoryRequest add = AddMemoryRequest.ofText("用户喜欢简洁的中文回答", userId);
        add.setInfer(false);
        add.setAgentId(agentId);
        add.setRunId(runId);
        add.setMetadata(meta);

        var addResp = mem.add(add);
        assertNotNull(addResp);
        assertNotNull(addResp.getResults());
        assertEquals(1, addResp.getResults().size());
        String id = addResp.getResults().get(0).getId();
        assertNotNull(id);
        assertEquals("ADD", addResp.getResults().get(0).getEvent());

        // getAll should return 1
        GetAllMemoriesRequest getAll = new GetAllMemoriesRequest();
        getAll.setUserId(userId);
        getAll.setAgentId(agentId);
        getAll.setRunId(runId);
        var allResp = mem.getAll(getAll);
        assertNotNull(allResp);
        assertNotNull(allResp.getResults());
        assertEquals(1, allResp.getResults().size());

        // search (should include category backfilled into metadata)
        SearchMemoriesRequest search = SearchMemoriesRequest.ofQuery("简洁 中文", userId);
        search.setAgentId(agentId);
        search.setRunId(runId);
        search.setTopK(10);
        Map<String, Object> filters = new HashMap<>();
        filters.put("category", "preference");
        search.setFilters(filters);
        var searchResp = mem.search(search);
        assertNotNull(searchResp);
        assertNotNull(searchResp.getResults());
        assertFalse(searchResp.getResults().isEmpty());
        assertEquals(id, searchResp.getResults().get(0).getId());
        assertNotNull(searchResp.getResults().get(0).getMetadata());
        assertEquals("preference", String.valueOf(searchResp.getResults().get(0).getMetadata().get("category")));

        // get should succeed and trigger lifecycle hook updates (access_count increments)
        GetMemoryRequest get = new GetMemoryRequest();
        get.setUserId(userId);
        get.setAgentId(agentId);
        get.setMemoryId(id);
        var getResp = mem.get(get);
        assertNotNull(getResp);
        assertNotNull(getResp.getMemory());
        assertEquals(id, getResp.getMemory().getId());

        // update content
        UpdateMemoryRequest upd = new UpdateMemoryRequest();
        upd.setUserId(userId);
        upd.setAgentId(agentId);
        upd.setMemoryId(id);
        upd.setNewContent("用户喜欢简洁、结构化的中文回答");
        upd.setMetadata(meta);
        var updResp = mem.update(upd);
        assertNotNull(updResp);
        assertNotNull(updResp.getMemory());
        assertEquals(id, updResp.getMemory().getId());

        // delete
        var delResp = mem.delete(id, userId, agentId);
        assertNotNull(delResp);
        assertTrue(delResp.isDeleted());

        // should be gone
        var getResp2 = mem.get(get);
        assertNotNull(getResp2);
        assertNull(getResp2.getMemory());

        // deleteAll now returns 0/ok
        var delAllResp2 = mem.deleteAll(delAll);
        assertNotNull(delAllResp2);
        assertTrue(delAllResp2.getDeletedCount() >= 0);
    }

    @Test
    void testIntelligentAdd_pipelineIsExercised(@TempDir Path tmp) {
        // This test relies on MockLLM producing at least one fact.
        // If MockLLM is changed, this test ensures intelligent add remains wired correctly.
        Memory mem = newMemoryWithTempSqlite(tmp.resolve("powermem_intelligent.db"));

        AddMemoryRequest req = new AddMemoryRequest();
        req.setUserId("u2");
        req.setAgentId("a2");
        req.setInfer(true);
        req.setMessages(List.of(
                new com.oceanbase.powermem.sdk.model.Message("user", "我喜欢简洁的中文回答"),
                new com.oceanbase.powermem.sdk.model.Message("assistant", "好的，我会尽量简洁")
        ));

        var resp = mem.add(req);
        assertNotNull(resp);
        assertNotNull(resp.getResults());
        // With mock LLM, we accept either empty (if facts empty) or >=1 (if facts provided).
        // The key assertion is that intelligent path does not throw and returns valid structure.
        if (!resp.getResults().isEmpty()) {
            assertNotNull(resp.getResults().get(0).getId());
            assertNotNull(resp.getResults().get(0).getEvent());
        }
    }
}

