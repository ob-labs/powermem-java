package com.oceanbase.powermem;

import com.oceanbase.powermem.sdk.config.ConfigLoader;
import com.oceanbase.powermem.sdk.config.MemoryConfig;
import com.oceanbase.powermem.sdk.config.SubStoreConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SubStoreConfigLoaderTest {

    @Test
    void testFromMap_parsesIndexedSubStoreRouting() {
        Map<String, String> m = new HashMap<>();
        m.put("DATABASE_PROVIDER", "sqlite");
        m.put("SQLITE_PATH", "./data/main.db");

        m.put("SUB_STORES_COUNT", "1");
        m.put("SUB_STORE_0_COLLECTION", "memories_pref");
        m.put("SUB_STORE_0_ROUTE_CATEGORY", "pref");
        m.put("SUB_STORE_0_SQLITE_PATH", "./data/sub0.db");
        m.put("SUB_STORE_0_EMBEDDING_PROVIDER", "mock");

        MemoryConfig cfg = ConfigLoader.fromMap(m);
        List<SubStoreConfig> subs = cfg.getSubStores();
        assertNotNull(subs);
        assertEquals(1, subs.size());

        SubStoreConfig sc = subs.get(0);
        assertEquals("memories_pref", sc.getName());
        assertNotNull(sc.getRoutingFilter());
        assertEquals("pref", String.valueOf(sc.getRoutingFilter().get("category")));

        assertNotNull(sc.getVectorStore());
        assertEquals("./data/sub0.db", sc.getVectorStore().getDatabasePath());

        assertNotNull(sc.getEmbedder());
        assertEquals("mock", sc.getEmbedder().getProvider());
    }
}

