package com.oceanbase.powermem.sdk.core;

/**
 * Asynchronous PowerMem memory manager (pure Java core migration target).
 *
 * <p>Intended Java counterpart of Python {@code AsyncMemory}. In Java, async APIs typically expose
 * {@code CompletableFuture} or reactive types; the internal implementation may still share most of the
 * same orchestration code as {@link Memory}.</p>
 *
 * <p>Python reference: {@code src/powermem/core/async_memory.py}</p>
 */
public class AsyncMemory implements MemoryBase {
    private final Memory delegate;

    public AsyncMemory() {
        this(new com.oceanbase.powermem.sdk.config.MemoryConfig());
    }

    public AsyncMemory(com.oceanbase.powermem.sdk.config.MemoryConfig config) {
        this.delegate = new Memory(config);
    }

    @Override
    public com.oceanbase.powermem.sdk.model.AddMemoryResponse add(com.oceanbase.powermem.sdk.model.AddMemoryRequest request) {
        return delegate.add(request);
    }

    @Override
    public com.oceanbase.powermem.sdk.model.SearchMemoriesResponse search(com.oceanbase.powermem.sdk.model.SearchMemoriesRequest request) {
        return delegate.search(request);
    }

    @Override
    public com.oceanbase.powermem.sdk.model.UpdateMemoryResponse update(com.oceanbase.powermem.sdk.model.UpdateMemoryRequest request) {
        return delegate.update(request);
    }

    @Override
    public com.oceanbase.powermem.sdk.model.GetMemoryResponse get(com.oceanbase.powermem.sdk.model.GetMemoryRequest request) {
        return delegate.get(request);
    }

    @Override
    public com.oceanbase.powermem.sdk.model.DeleteMemoryResponse delete(String memoryId, String userId, String agentId) {
        return delegate.delete(memoryId, userId, agentId);
    }

    @Override
    public com.oceanbase.powermem.sdk.model.GetAllMemoriesResponse getAll(com.oceanbase.powermem.sdk.model.GetAllMemoriesRequest request) {
        return delegate.getAll(request);
    }

    @Override
    public com.oceanbase.powermem.sdk.model.DeleteAllMemoriesResponse deleteAll(
            com.oceanbase.powermem.sdk.model.DeleteAllMemoriesRequest request) {
        return delegate.deleteAll(request);
    }
}

