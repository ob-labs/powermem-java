package com.oceanbase.powermem.sdk.integrations.embeddings;

/**
 * Mock embedding implementation for tests and local development.
 *
 * <p>Python reference: {@code src/powermem/integrations/embeddings/mock.py}</p>
 */
public class MockEmbedder implements Embedder {
    @Override
    public float[] embed(String text) {
        return new float[] {0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f};
    }

    @Override
    public java.util.List<float[]> embedBatch(java.util.List<String> texts) {
        java.util.List<float[]> results = new java.util.ArrayList<>();
        if (texts == null) {
            return results;
        }
        for (int i = 0; i < texts.size(); i++) {
            results.add(embed(texts.get(i)));
        }
        return results;
    }
}

