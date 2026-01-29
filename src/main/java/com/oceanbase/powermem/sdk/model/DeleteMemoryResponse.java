package com.oceanbase.powermem.sdk.model;

/**
 * Response DTO for delete-by-id operations.
 *
 * <p>Python reference: {@code Memory.delete(...)} in {@code src/powermem/core/memory.py}.</p>
 */
public class DeleteMemoryResponse {
    private boolean deleted;

    public DeleteMemoryResponse() {}

    public DeleteMemoryResponse(boolean deleted) {
        this.deleted = deleted;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}

