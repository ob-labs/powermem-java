package com.oceanbase.powermem.sdk.intelligence.plugin;

/**
 * Plugin contract for intelligent memory lifecycle hooks (onAdd/onSearch/onGet/...).
 *
 * <p>Python reference: {@code src/powermem/intelligence/plugin.py}</p>
 */
public interface IntelligentMemoryPlugin {
    boolean isEnabled();

    /**
     * Hook invoked before persisting a memory. Return extra fields to merge into payload.
     */
    java.util.Map<String, Object> onAdd(String content, java.util.Map<String, Object> metadata);

    /**
     * Hook invoked on single memory access. Return (updates, deleteFlag).
     */
    OnGetResult onGet(java.util.Map<String, Object> memoryPayload);

    /**
     * Hook invoked on batch search results. Return updates and delete ids.
     */
    OnSearchResult onSearch(java.util.List<java.util.Map<String, Object>> memoryPayloads);

    class OnGetResult {
        private final java.util.Map<String, Object> updates;
        private final boolean delete;

        public OnGetResult(java.util.Map<String, Object> updates, boolean delete) {
            this.updates = updates;
            this.delete = delete;
        }

        public java.util.Map<String, Object> getUpdates() {
            return updates;
        }

        public boolean isDelete() {
            return delete;
        }
    }

    class OnSearchResult {
        private final java.util.List<java.util.Map.Entry<String, java.util.Map<String, Object>>> updates;
        private final java.util.List<String> deletes;

        public OnSearchResult(java.util.List<java.util.Map.Entry<String, java.util.Map<String, Object>>> updates,
                              java.util.List<String> deletes) {
            this.updates = updates;
            this.deletes = deletes;
        }

        public java.util.List<java.util.Map.Entry<String, java.util.Map<String, Object>>> getUpdates() {
            return updates;
        }

        public java.util.List<String> getDeletes() {
            return deletes;
        }
    }
}

