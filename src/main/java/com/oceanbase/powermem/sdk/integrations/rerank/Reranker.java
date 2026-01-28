package com.oceanbase.powermem.sdk.integrations.rerank;

/**
 * Reranker interface used to rerank candidate memories/documents.
 *
 * <p>Python reference: {@code src/powermem/integrations/rerank/base.py}</p>
 */
public interface Reranker {
    /**
     * Rerank documents by relevance to query.
     *
     * @param query query text
     * @param documents candidate document texts
     * @param topN max results to return (if <=0, return all)
     * @return list of (index, score) pairs sorted by score desc
     */
    java.util.List<RerankResult> rerank(String query, java.util.List<String> documents, int topN);
}

