package com.oceanbase.powermem;

import com.oceanbase.powermem.sdk.config.EmbedderConfig;
import com.oceanbase.powermem.sdk.config.MemoryConfig;
import com.oceanbase.powermem.sdk.config.SubStoreConfig;
import com.oceanbase.powermem.sdk.config.VectorStoreConfig;
import com.oceanbase.powermem.sdk.core.Memory;
import com.oceanbase.powermem.sdk.model.AddMemoryRequest;
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

public class SubStorageAdapterRoutingTest {

    @TempDir
    Path tempDir;

    @Test
    void testRouting_addSearchGetUpdateDelete() {
        Path mainDb = tempDir.resolve("main.db");
        Path subDb = tempDir.resolve("sub.db");

        MemoryConfig cfg = new MemoryConfig();
        VectorStoreConfig mainVs = VectorStoreConfig.sqlite(mainDb.toString());
        mainVs.setCollectionName("memories_main");
        cfg.setVectorStore(mainVs);

        EmbedderConfig emb = new EmbedderConfig();
        emb.setProvider("mock");
        cfg.setEmbedder(emb);

        SubStoreConfig sub = new SubStoreConfig();
        sub.setName("memories_sub_0");
        Map<String, Object> rf = new HashMap<>();
        rf.put("category", "pref");
        sub.setRoutingFilter(rf);

        VectorStoreConfig subVs = VectorStoreConfig.sqlite(subDb.toString());
        subVs.setCollectionName("memories_sub_0");
        sub.setVectorStore(subVs);

        EmbedderConfig subEmb = new EmbedderConfig();
        subEmb.setProvider("mock");
        sub.setEmbedder(subEmb);

        cfg.setSubStores(java.util.Collections.singletonList(sub));

        Memory mem = new Memory(cfg);

        // readiness: default ready=true; if not ready, routing should fall back to main store.
        // (We can't directly access adapter instance here, so we validate behavior through filters.)

        Map<String, Object> metaPref = new HashMap<>();
        metaPref.put("category", "pref");
        AddMemoryRequest add1 = AddMemoryRequest.ofText("用户喜欢简洁的中文回答");
        add1.setMetadata(metaPref);
        String idPref = mem.add(add1).getResults().get(0).getId();
        assertNotNull(idPref);

        Map<String, Object> metaOther = new HashMap<>();
        metaOther.put("category", "other");
        AddMemoryRequest add2 = AddMemoryRequest.ofText("用户正在学习Java SDK");
        add2.setMetadata(metaOther);
        String idMain = mem.add(add2).getResults().get(0).getId();
        assertNotNull(idMain);

        // getAll: Python parity - only main store listing, sub store is not included.
        GetAllMemoriesRequest allReq = new GetAllMemoriesRequest();
        allReq.setLimit(100);
        List<?> all = mem.getAll(allReq).getResults();
        assertEquals(1, all.size());

        // search in sub store via filters routing
        SearchMemoriesRequest s1 = SearchMemoriesRequest.ofQuery("简洁 中文");
        Map<String, Object> f1 = new HashMap<>();
        f1.put("category", "pref");
        s1.setFilters(f1);
        assertFalse(mem.search(s1).getResults().isEmpty());
        assertEquals(idPref, mem.search(s1).getResults().get(0).getId());

        // search in main store via different filters
        SearchMemoriesRequest s2 = SearchMemoriesRequest.ofQuery("Java SDK");
        Map<String, Object> f2 = new HashMap<>();
        f2.put("category", "other");
        s2.setFilters(f2);
        assertFalse(mem.search(s2).getResults().isEmpty());
        assertEquals(idMain, mem.search(s2).getResults().get(0).getId());

        // get: should search main then sub stores
        assertNotNull(mem.get(new GetMemoryRequest(idPref, null, null)).getMemory());

        // update: should locate correct store and update there
        UpdateMemoryRequest upd = new UpdateMemoryRequest();
        upd.setMemoryId(idPref);
        upd.setNewContent("用户喜欢非常简洁的中文回答");
        upd.setMetadata(metaPref);
        assertNotNull(mem.update(upd).getMemory());

        // delete: should locate correct store (sub store) and delete
        assertTrue(mem.delete(idPref, null, null).isDeleted());
        assertNull(mem.get(new GetMemoryRequest(idPref, null, null)).getMemory());
    }
}

