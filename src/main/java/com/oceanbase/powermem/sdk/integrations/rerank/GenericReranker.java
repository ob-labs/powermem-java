package com.oceanbase.powermem.sdk.integrations.rerank;

/**
 * Generic reranker placeholder (Java migration target).
 *
 * <p>Python reference: {@code src/powermem/integrations/rerank/generic.py}</p>
 */
public class GenericReranker implements Reranker {
    @Override
    public java.util.List<RerankResult> rerank(String query, java.util.List<String> documents, int topN) {
        throw new com.oceanbase.powermem.sdk.exception.ApiException("GenericReranker is not implemented yet");
    }
}

