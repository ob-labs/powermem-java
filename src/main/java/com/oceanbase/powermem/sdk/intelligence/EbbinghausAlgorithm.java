package com.oceanbase.powermem.sdk.intelligence;

/**
 * Ebbinghaus forgetting curve algorithm implementation.
 *
 * <p>Python reference: {@code src/powermem/intelligence/ebbinghaus_algorithm.py}</p>
 */
public class EbbinghausAlgorithm {
    /**
     * Calculate decay factor using the same formula as Python:
     * {@code decay = exp(-hours_elapsed / (24 * decay_rate))}.
     */
    public double calculateDecay(java.time.Instant createdAt,
                                 java.time.Instant now,
                                 com.oceanbase.powermem.sdk.config.IntelligentMemoryConfig config) {
        if (config == null || !config.isEnabled()) {
            return 1.0;
        }
        if (createdAt == null || now == null) {
            return 1.0;
        }
        long seconds = java.time.Duration.between(createdAt, now).getSeconds();
        if (seconds <= 0) {
            return 1.0;
        }
        double hours = seconds / 3600.0;
        double decayRate = config.getDecayRate() > 0 ? config.getDecayRate() : 0.1;
        double decay = Math.exp(-hours / (24.0 * decayRate));
        return Math.max(decay, 0.0);
    }

    /**
     * Apply decay to a base similarity score (vector score), matching Python's idea of time-based decay.
     */
    public double applyToScore(double baseScore,
                               java.time.Instant createdAt,
                               java.time.Instant now,
                               com.oceanbase.powermem.sdk.config.IntelligentMemoryConfig config) {
        if (config == null || !config.isEnabled() || !config.isDecayEnabled()) {
            return baseScore;
        }
        double decay = calculateDecay(createdAt, now, config);
        return baseScore * decay;
    }

    public boolean shouldForget(java.time.Instant createdAt,
                                int accessCount,
                                java.time.Instant now,
                                com.oceanbase.powermem.sdk.config.IntelligentMemoryConfig config) {
        if (config == null || !config.isEnabled()) {
            return false;
        }
        if (createdAt != null) {
            double decay = calculateDecay(createdAt, now, config);
            if (decay < config.getWorkingThreshold()) {
                return true;
            }
            if (accessCount <= 0) {
                long days = java.time.Duration.between(createdAt, now).toDays();
                if (days > 7) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean shouldPromote(java.time.Instant createdAt,
                                 int accessCount,
                                 double importanceScore,
                                 java.time.Instant now,
                                 com.oceanbase.powermem.sdk.config.IntelligentMemoryConfig config) {
        if (config == null || !config.isEnabled()) {
            return false;
        }
        if (accessCount >= 3) {
            return true;
        }
        if (createdAt != null) {
            long hours = java.time.Duration.between(createdAt, now).toHours();
            if (hours > 24) {
                return true;
            }
        }
        return importanceScore >= config.getShortTermThreshold();
    }

    public boolean shouldArchive(java.time.Instant createdAt,
                                 double importanceScore,
                                 java.time.Instant now,
                                 com.oceanbase.powermem.sdk.config.IntelligentMemoryConfig config) {
        if (config == null || !config.isEnabled()) {
            return false;
        }
        if (createdAt != null) {
            long days = java.time.Duration.between(createdAt, now).toDays();
            if (days > 30) {
                return true;
            }
        }
        return importanceScore < config.getWorkingThreshold();
    }

    /**
     * Build intelligence metadata structure similar to Python {@code process_memory_metadata()}.
     * Returned map is suitable for merging into payload top-level fields.
     */
    public java.util.Map<String, Object> processMemoryMetadata(String content,
                                                               double importanceScore,
                                                               String memoryType,
                                                               com.oceanbase.powermem.sdk.config.IntelligentMemoryConfig config) {
        java.time.Instant now = java.time.Instant.now();
        double initialRetention = (config == null ? 1.0 : config.getInitialRetention()) * importanceScore;
        double decayRate = config == null ? 0.1 : config.getDecayRate();
        java.util.Map<String, Object> intelligence = new java.util.HashMap<>();
        intelligence.put("importance_score", importanceScore);
        intelligence.put("memory_type", memoryType);
        intelligence.put("initial_retention", initialRetention);
        intelligence.put("decay_rate", decayRate);
        intelligence.put("current_retention", initialRetention);
        intelligence.put("last_reviewed", now.toString());
        intelligence.put("review_count", 0);
        intelligence.put("access_count", 0);
        intelligence.put("reinforcement_factor", config == null ? 0.3 : config.getReinforcementFactor());

        java.util.Map<String, Object> memoryManagement = new java.util.HashMap<>();
        memoryManagement.put("should_promote", false);
        memoryManagement.put("should_forget", false);
        memoryManagement.put("should_archive", false);
        memoryManagement.put("is_active", true);

        java.util.Map<String, Object> out = new java.util.HashMap<>();
        out.put("intelligence", intelligence);
        out.put("memory_management", memoryManagement);
        out.put("created_at", now.toString());
        out.put("updated_at", now.toString());
        return out;
    }
}

