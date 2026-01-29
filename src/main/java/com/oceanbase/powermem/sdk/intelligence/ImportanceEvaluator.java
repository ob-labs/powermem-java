package com.oceanbase.powermem.sdk.intelligence;

/**
 * Importance evaluator for memories (rule-based and/or LLM-based).
 *
 * <p>Python reference: {@code src/powermem/intelligence/importance_evaluator.py}</p>
 */
public class ImportanceEvaluator {
    public double evaluateImportance(String content, java.util.Map<String, Object> metadata) {
        if (metadata != null) {
            Object v = metadata.get("importance_score");
            if (v instanceof Number) {
                double d = ((Number) v).doubleValue();
                if (!Double.isNaN(d) && d >= 0.0 && d <= 1.0) {
                    return d;
                }
            }
            if (v != null) {
                try {
                    double d = Double.parseDouble(String.valueOf(v));
                    if (!Double.isNaN(d) && d >= 0.0 && d <= 1.0) {
                        return d;
                    }
                } catch (Exception ignored) {
                    // fallthrough
                }
            }
        }
        // Minimal rule-based default; can be upgraded to LLM-based later.
        return 0.5;
    }
}

