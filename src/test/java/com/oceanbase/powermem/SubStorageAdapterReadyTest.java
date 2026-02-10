package com.oceanbase.powermem;

import com.oceanbase.powermem.sdk.integrations.embeddings.MockEmbedder;
import com.oceanbase.powermem.sdk.model.MemoryRecord;
import com.oceanbase.powermem.sdk.storage.adapter.SubStorageAdapter;
import com.oceanbase.powermem.sdk.storage.base.OutputData;
import com.oceanbase.powermem.sdk.storage.base.VectorStore;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SubStorageAdapterReadyTest {

    @Test
    void testNotReady_fallsBackToMainStore() {
        TestVectorStore main = new TestVectorStore("main");
        TestVectorStore sub = new TestVectorStore("sub");

        SubStorageAdapter adapter = new SubStorageAdapter(main, new MockEmbedder());
        Map<String, Object> routing = new HashMap<>();
        routing.put("category", "pref");
        adapter.registerSubStore("memories_pref", routing, sub, new MockEmbedder());

        // mark not ready
        adapter.setSubStoreReady("memories_pref", false);

        Map<String, Object> meta = new HashMap<>();
        meta.put("category", "pref");
        adapter.addMemory("hello", null, null, null, meta, null, null, null);

        assertEquals(1, main.upsertCount);
        assertEquals(0, sub.upsertCount);
    }

    @Test
    void testReady_routesToSubStore() {
        TestVectorStore main = new TestVectorStore("main");
        TestVectorStore sub = new TestVectorStore("sub");

        SubStorageAdapter adapter = new SubStorageAdapter(main, new MockEmbedder());
        Map<String, Object> routing = new HashMap<>();
        routing.put("category", "pref");
        adapter.registerSubStore("memories_pref", routing, sub, new MockEmbedder());

        Map<String, Object> meta = new HashMap<>();
        meta.put("category", "pref");
        adapter.addMemory("hello", null, null, null, meta, null, null, null);

        assertEquals(0, main.upsertCount);
        assertEquals(1, sub.upsertCount);
    }

    private static final class TestVectorStore implements VectorStore {
        int upsertCount = 0;

        TestVectorStore(String name) {
            // name is for readability in debugging; not used in assertions
        }

        @Override
        public void upsert(MemoryRecord record, float[] embedding) {
            upsertCount++;
        }

        @Override
        public MemoryRecord get(String memoryId, String userId, String agentId) {
            return null;
        }

        @Override
        public boolean delete(String memoryId, String userId, String agentId) {
            return false;
        }

        @Override
        public int deleteAll(String userId, String agentId, String runId) {
            return 0;
        }

        @Override
        public List<MemoryRecord> list(String userId, String agentId, String runId, int offset, int limit) {
            return java.util.Collections.emptyList();
        }

        @Override
        public List<OutputData> search(float[] queryEmbedding, int topK, String userId, String agentId, String runId, Map<String, Object> filters) {
            return java.util.Collections.emptyList();
        }
    }
}

