package com.oceanbase.powermem.sdk.model;

/**
 * Response DTO for retrieving a memory by id.
 */
public class GetMemoryResponse {
    private MemoryRecord memory;

    public GetMemoryResponse() {}

    public GetMemoryResponse(MemoryRecord memory) {
        this.memory = memory;
    }

    public MemoryRecord getMemory() {
        return memory;
    }

    public void setMemory(MemoryRecord memory) {
        this.memory = memory;
    }
}

