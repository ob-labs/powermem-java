package com.oceanbase.powermem;

import com.oceanbase.powermem.sdk.config.EmbedderConfig;
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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OceanBase end-to-end integration test (CRUD + search).
 *
 * <p>Runs only when required OceanBase env vars are set.</p>
 */
@EnabledIfEnvironmentVariable(named = "OCEANBASE_HOST", matches = ".+")
@EnabledIfEnvironmentVariable(named = "OCEANBASE_USER", matches = ".+")
@EnabledIfEnvironmentVariable(named = "OCEANBASE_DATABASE", matches = ".+")
public class OceanBaseMemoryE2eIT {

    @Test
    void testOceanBaseMemory_crud_search_e2e() {
        String host = System.getenv("OCEANBASE_HOST");
        String user = System.getenv("OCEANBASE_USER");
        String password = System.getenv().getOrDefault("OCEANBASE_PASSWORD", "");
        String db = System.getenv("OCEANBASE_DATABASE");
        int port = 2881;
        try {
            String p = System.getenv("OCEANBASE_PORT");
            if (p != null && !p.isBlank()) port = Integer.parseInt(p);
        } catch (Exception ignored) {}

        VectorStoreConfig vs = new VectorStoreConfig();
        vs.setProvider("oceanbase");
        vs.setHost(host);
        vs.setPort(port);
        vs.setUser(user);
        vs.setPassword(password);
        vs.setDatabase(db);
        vs.setCollectionName("memories_java_it_e2e");
        vs.setEmbeddingModelDims(10); // MockEmbedder dims
        vs.setTimeoutSeconds(10);
        // Hybrid enabled by default; should be safe even if FULLTEXT isn't available (falls back to LIKE)
        vs.setHybridSearch(true);
        vs.setFusionMethod("weighted");

        MemoryConfig cfg = new MemoryConfig();
        cfg.setVectorStore(vs);
        EmbedderConfig emb = new EmbedderConfig();
        emb.setProvider("mock");
        cfg.setEmbedder(emb);
        cfg.getLlm().setProvider("mock");

        Memory mem = new Memory(cfg);

        String userId = "it_user";
        String agentId = "it_agent";
        String runId = "it_run";

        DeleteAllMemoriesRequest delAll = new DeleteAllMemoriesRequest();
        delAll.setUserId(userId);
        delAll.setAgentId(agentId);
        delAll.setRunId(runId);
        mem.deleteAll(delAll);

        Map<String, Object> meta1 = new HashMap<>();
        meta1.put("category", "preference");
        meta1.put("source", "it");
        AddMemoryRequest add1 = AddMemoryRequest.ofText("用户喜欢简洁的中文回答", userId);
        add1.setInfer(false);
        add1.setAgentId(agentId);
        add1.setRunId(runId);
        add1.setMetadata(meta1);
        var addResp1 = mem.add(add1);
        assertNotNull(addResp1);
        assertNotNull(addResp1.getResults());
        assertEquals(1, addResp1.getResults().size());

        GetAllMemoriesRequest getAll = new GetAllMemoriesRequest();
        getAll.setUserId(userId);
        getAll.setAgentId(agentId);
        getAll.setRunId(runId);
        getAll.setLimit(100);
        var all = mem.getAll(getAll);
        assertNotNull(all);
        assertNotNull(all.getResults());
        assertFalse(all.getResults().isEmpty());
        String anyId = String.valueOf(all.getResults().get(0).get("id"));
        assertNotNull(anyId);

        SearchMemoriesRequest search = SearchMemoriesRequest.ofQuery("用户偏好是什么？", userId);
        search.setAgentId(agentId);
        search.setRunId(runId);
        search.setTopK(5);
        search.setFilters(Map.of("category", "preference"));
        var sr = mem.search(search);
        assertNotNull(sr);
        assertNotNull(sr.getResults());
        assertFalse(sr.getResults().isEmpty());

        var got = mem.get(new GetMemoryRequest(anyId, userId, agentId));
        assertNotNull(got);
        assertNotNull(got.getMemory());
        assertEquals(anyId, got.getMemory().getId());

        UpdateMemoryRequest upd = new UpdateMemoryRequest();
        upd.setMemoryId(anyId);
        upd.setUserId(userId);
        upd.setAgentId(agentId);
        upd.setNewContent("用户喜欢非常简洁的中文回答（已更新）");
        var u = mem.update(upd);
        assertNotNull(u);
        assertNotNull(u.getMemory());
        assertEquals(anyId, u.getMemory().getId());

        assertTrue(mem.delete(anyId, userId, agentId).isDeleted());
        assertTrue(mem.deleteAll(delAll).getDeletedCount() >= 0);
    }
}

