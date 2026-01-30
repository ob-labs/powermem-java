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
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OceanBaseGraphStore integration test.
 *
 * <p>Runs only when required OceanBase env vars are set.</p>
 */
@EnabledIfEnvironmentVariable(named = "OCEANBASE_HOST", matches = ".+")
@EnabledIfEnvironmentVariable(named = "OCEANBASE_USER", matches = ".+")
@EnabledIfEnvironmentVariable(named = "OCEANBASE_DATABASE", matches = ".+")
public class OceanBaseGraphStoreIT {

    @Test
    void testGraphRelations_addAndSearch(@TempDir Path tmp) {
        String host = System.getenv("OCEANBASE_HOST");
        String user = System.getenv("OCEANBASE_USER");
        String password = System.getenv().getOrDefault("OCEANBASE_PASSWORD", "");
        String db = System.getenv("OCEANBASE_DATABASE");
        int port = 2881;
        try {
            String p = System.getenv("OCEANBASE_PORT");
            if (p != null && !p.isBlank()) port = Integer.parseInt(p);
        } catch (Exception ignored) {}

        // Vector store can stay local (SQLite). Graph store uses OceanBase.
        MemoryConfig cfg = new MemoryConfig();
        VectorStoreConfig vs = VectorStoreConfig.sqlite(tmp.resolve("powermem_graph_it.db").toString());
        vs.setCollectionName("memories_graph_it");
        cfg.setVectorStore(vs);

        EmbedderConfig emb = new EmbedderConfig();
        emb.setProvider("mock");
        cfg.setEmbedder(emb);
        cfg.getLlm().setProvider("mock");

        cfg.getGraphStore().setEnabled(true);
        cfg.getGraphStore().setProvider("oceanbase");
        cfg.getGraphStore().setHost(host);
        cfg.getGraphStore().setPort(port);
        cfg.getGraphStore().setUser(user);
        cfg.getGraphStore().setPassword(password);
        cfg.getGraphStore().setDatabase(db);
        cfg.getGraphStore().setEntitiesTable("graph_entities_java_it");
        cfg.getGraphStore().setRelationshipsTable("graph_relationships_java_it");
        cfg.getGraphStore().setTimeoutSeconds(10);

        Memory mem = new Memory(cfg);

        String userId = null; // Python parity: optional
        String agentId = "it_agent_graph";
        String runId = "it_run_graph";

        DeleteAllMemoriesRequest delAll = new DeleteAllMemoriesRequest();
        delAll.setUserId(userId);
        delAll.setAgentId(agentId);
        delAll.setRunId(runId);
        mem.deleteAll(delAll);

        AddMemoryRequest add = new AddMemoryRequest();
        add.setUserId(userId);
        add.setAgentId(agentId);
        add.setRunId(runId);
        add.setInfer(false);
        add.setText("user -- likes -- coffee\ncoffee -- is_a -- beverage");
        var addResp = mem.add(add);
        assertNotNull(addResp);
        assertNotNull(addResp.getRelations());

        // Multi-hop: user -> coffee -> beverage
        SearchMemoriesRequest search = SearchMemoriesRequest.ofQuery("user beverage");
        search.setUserId(userId);
        search.setAgentId(agentId);
        search.setRunId(runId);
        var sr = mem.search(search);
        assertNotNull(sr);
        assertNotNull(sr.getRelations());
        assertTrue(sr.getRelations() instanceof java.util.List);
        assertFalse(((java.util.List<?>) sr.getRelations()).isEmpty());
    }
}

