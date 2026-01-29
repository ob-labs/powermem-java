package com.oceanbase.powermem.sdk.client;

/**
 * Convenience client focused on synchronous memory CRUD operations.
 *
 * <p>In a pure Java core design this becomes a thin wrapper over
 * {@link com.oceanbase.powermem.sdk.core.Memory}.</p>
 *
 * <p>Python reference: {@code src/powermem/core/memory.py} (Memory)</p>
 */
public class MemoryClient {
    private final com.oceanbase.powermem.sdk.core.Memory memory;

    public MemoryClient(com.oceanbase.powermem.sdk.core.Memory memory) {
        this.memory = memory;
    }

    public com.oceanbase.powermem.sdk.model.AddMemoryResponse add(com.oceanbase.powermem.sdk.model.AddMemoryRequest request) {
        return memory.add(request);
    }

    public com.oceanbase.powermem.sdk.model.SearchMemoriesResponse search(com.oceanbase.powermem.sdk.model.SearchMemoriesRequest request) {
        return memory.search(request);
    }

    public com.oceanbase.powermem.sdk.model.UpdateMemoryResponse update(com.oceanbase.powermem.sdk.model.UpdateMemoryRequest request) {
        return memory.update(request);
    }

    public com.oceanbase.powermem.sdk.model.GetMemoryResponse get(com.oceanbase.powermem.sdk.model.GetMemoryRequest request) {
        return memory.get(request);
    }

    public com.oceanbase.powermem.sdk.model.DeleteMemoryResponse delete(String memoryId, String userId, String agentId) {
        return memory.delete(memoryId, userId, agentId);
    }

    public com.oceanbase.powermem.sdk.model.GetAllMemoriesResponse getAll(com.oceanbase.powermem.sdk.model.GetAllMemoriesRequest request) {
        return memory.getAll(request);
    }

    public com.oceanbase.powermem.sdk.model.DeleteAllMemoriesResponse deleteAll(com.oceanbase.powermem.sdk.model.DeleteAllMemoriesRequest request) {
        return memory.deleteAll(request);
    }
}

