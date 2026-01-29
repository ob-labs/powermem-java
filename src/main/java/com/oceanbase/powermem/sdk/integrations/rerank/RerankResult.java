package com.oceanbase.powermem.sdk.integrations.rerank;

/**
 * Rerank result entry: (document index, relevance score).
 */
public final class RerankResult {
    private final int index;
    private final double score;

    public RerankResult(int index, double score) {
        this.index = index;
        this.score = score;
    }

    public int getIndex() {
        return index;
    }

    public double getScore() {
        return score;
    }
}

