package com.oceanbase.powermem.sdk.util;

/**
 * Simple vector math utilities (cosine similarity, norm).
 *
 * <p>Python reference: cosine similarity usage in vector store implementations and rerank logic.</p>
 */
public final class VectorMath {
    private VectorMath() {}

    public static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null) {
            return 0.0;
        }
        int n = Math.min(a.length, b.length);
        if (n == 0) {
            return 0.0;
        }
        double dot = 0.0;
        double na = 0.0;
        double nb = 0.0;
        for (int i = 0; i < n; i++) {
            double da = a[i];
            double db = b[i];
            dot += da * db;
            na += da * da;
            nb += db * db;
        }
        if (na == 0.0 || nb == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}

