package com.oceanbase.powermem.sdk.model;

/**
 * Response DTO for bulk deletion.
 *
 * <p>Python reference: {@code Memory.delete_all(...)} return semantics in
 * {@code src/powermem/core/memory.py} (boolean) and benchmark server response shape.</p>
 */
public class DeleteAllMemoriesResponse {
    private int deletedCount;

    public DeleteAllMemoriesResponse() {}

    public DeleteAllMemoriesResponse(int deletedCount) {
        this.deletedCount = deletedCount;
    }

    public int getDeletedCount() {
        return deletedCount;
    }

    public void setDeletedCount(int deletedCount) {
        this.deletedCount = deletedCount;
    }
}

