package com.oceanbase.powermem.sdk.storage.base;

/**
 * Normalized output record returned by vector store search/get/list operations.
 *
 * <p>Python reference: {@code src/powermem/storage/base.py} (OutputData)</p>
 */
public class OutputData {
    private com.oceanbase.powermem.sdk.model.MemoryRecord record;
    private double score;

    public OutputData() {}

    public OutputData(com.oceanbase.powermem.sdk.model.MemoryRecord record, double score) {
        this.record = record;
        this.score = score;
    }

    public com.oceanbase.powermem.sdk.model.MemoryRecord getRecord() {
        return record;
    }

    public void setRecord(com.oceanbase.powermem.sdk.model.MemoryRecord record) {
        this.record = record;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}

