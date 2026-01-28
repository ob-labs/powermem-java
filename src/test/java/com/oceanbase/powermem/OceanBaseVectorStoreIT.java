package com.oceanbase.powermem;

import com.oceanbase.powermem.sdk.config.EmbedderConfig;
import com.oceanbase.powermem.sdk.config.MemoryConfig;
import com.oceanbase.powermem.sdk.config.VectorStoreConfig;
import com.oceanbase.powermem.sdk.core.Memory;
import com.oceanbase.powermem.sdk.model.AddMemoryRequest;
import com.oceanbase.powermem.sdk.model.DeleteAllMemoriesRequest;
import com.oceanbase.powermem.sdk.model.SearchMemoriesRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OceanBase integration test.
 *
 * <p>Runs only when required OceanBase env vars are set.</p>
 */
@EnabledIfEnvironmentVariable(named = "OCEANBASE_HOST", matches = ".+")
@EnabledIfEnvironmentVariable(named = "OCEANBASE_USER", matches = ".+")
@EnabledIfEnvironmentVariable(named = "OCEANBASE_DATABASE", matches = ".+")
public class OceanBaseVectorStoreIT {

    @Test
    void testOceanBaseCrudSearch_smoke() {
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
        vs.setCollectionName("memories_java_it");
        vs.setEmbeddingModelDims(10);
        vs.setTimeoutSeconds(10);

        MemoryConfig cfg = new MemoryConfig();
        cfg.setVectorStore(vs);
        // No network: keep embedding deterministic and small (vector stored as JSON)
        EmbedderConfig emb = new EmbedderConfig();
        emb.setProvider("mock");
        cfg.setEmbedder(emb);
        cfg.getLlm().setProvider("mock");

        Memory mem = new Memory(cfg);

        String userId = "it_user";
        String agentId = "it_agent";

        DeleteAllMemoriesRequest delAll = new DeleteAllMemoriesRequest();
        delAll.setUserId(userId);
        delAll.setAgentId(agentId);
        mem.deleteAll(delAll);

        Map<String, Object> meta = new HashMap<>();
        meta.put("category", "it");
        AddMemoryRequest add = AddMemoryRequest.ofText("OceanBase integration test memory", userId);
        add.setInfer(false);
        add.setAgentId(agentId);
        add.setMetadata(meta);
        var addResp = mem.add(add);
        assertNotNull(addResp);
        assertNotNull(addResp.getResults());
        assertEquals(1, addResp.getResults().size());

        SearchMemoriesRequest search = SearchMemoriesRequest.ofQuery("integration", userId);
        search.setAgentId(agentId);
        search.setTopK(5);
        search.setFilters(Map.of("category", "it"));
        var searchResp = mem.search(search);
        assertNotNull(searchResp);
        assertNotNull(searchResp.getResults());
        assertFalse(searchResp.getResults().isEmpty());
    }
}

