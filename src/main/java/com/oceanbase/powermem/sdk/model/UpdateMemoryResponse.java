package com.oceanbase.powermem.sdk.model;

/**
 * Response DTO for updating memory.
 *
 * <p>Python reference: return shape in {@code Memory.update} docstring in
 * {@code src/powermem/core/memory.py}.</p>
 */
public class UpdateMemoryResponse {
    private MemoryRecord memory;

    public UpdateMemoryResponse() {}

    public UpdateMemoryResponse(MemoryRecord memory) {
        this.memory = memory;
    }

    public MemoryRecord getMemory() {
        return memory;
    }

    public void setMemory(MemoryRecord memory) {
        this.memory = memory;
    }
}

