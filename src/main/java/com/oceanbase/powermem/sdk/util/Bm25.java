package com.oceanbase.powermem.sdk.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal BM25 scorer (Okapi BM25).
 *
 * <p>Used for graph triple reranking (Python parity: rank_bm25.BM25Okapi).</p>
 */
public final class Bm25 {
    private final double k1;
    private final double b;
    private final List<List<String>> corpus;
    private final Map<String, Integer> docFreq = new HashMap<>();
    private final List<Map<String, Integer>> termFreqs = new ArrayList<>();
    private final double avgDocLen;

    public Bm25(List<List<String>> tokenizedCorpus) {
        this(tokenizedCorpus, 1.5, 0.75);
    }

    public Bm25(List<List<String>> tokenizedCorpus, double k1, double b) {
        this.k1 = k1;
        this.b = b;
        this.corpus = tokenizedCorpus == null ? new ArrayList<>() : tokenizedCorpus;

        long totalLen = 0;
        for (List<String> doc : this.corpus) {
            Map<String, Integer> tf = new HashMap<>();
            if (doc != null) {
                totalLen += doc.size();
                for (String t : doc) {
                    if (t == null || t.isBlank()) continue;
                    tf.put(t, tf.getOrDefault(t, 0) + 1);
                }
            }
            termFreqs.add(tf);
            for (String term : tf.keySet()) {
                docFreq.put(term, docFreq.getOrDefault(term, 0) + 1);
            }
        }
        this.avgDocLen = this.corpus.isEmpty() ? 0.0 : (double) totalLen / (double) this.corpus.size();
    }

    public double[] getScores(List<String> queryTokens) {
        int nDocs = corpus.size();
        double[] scores = new double[nDocs];
        if (nDocs == 0) {
            return scores;
        }
        if (queryTokens == null || queryTokens.isEmpty()) {
            return scores;
        }

        for (int i = 0; i < nDocs; i++) {
            List<String> doc = corpus.get(i);
            Map<String, Integer> tf = termFreqs.get(i);
            int docLen = doc == null ? 0 : doc.size();
            double denomNorm = (1.0 - b) + b * (avgDocLen > 0 ? ((double) docLen / avgDocLen) : 1.0);

            double s = 0.0;
            for (String q : queryTokens) {
                if (q == null || q.isBlank()) continue;
                Integer df = docFreq.get(q);
                if (df == null || df == 0) continue;
                // IDF with smoothing (Okapi)
                double idf = Math.log(1.0 + (nDocs - df + 0.5) / (df + 0.5));
                int f = tf.getOrDefault(q, 0);
                if (f == 0) continue;
                double numer = f * (k1 + 1.0);
                double denom = f + k1 * denomNorm;
                s += idf * (numer / denom);
            }
            scores[i] = s;
        }
        return scores;
    }
}

