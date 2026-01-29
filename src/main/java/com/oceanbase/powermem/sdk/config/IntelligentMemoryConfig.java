package com.oceanbase.powermem.sdk.config;

/**
 * Intelligent memory configuration (Ebbinghaus parameters, switches) for pure Java core migration.
 *
 * <p>Python reference: {@code src/powermem/configs.py} (IntelligentMemoryConfig) and {@code src/powermem/intelligence/*}</p>
 */
public class IntelligentMemoryConfig {
    private boolean enabled = true;
    private double initialRetention = 1.0;
    private double decayRate = 0.1;
    private double reinforcementFactor = 0.3;
    private double workingThreshold = 0.3;
    private double shortTermThreshold = 0.6;
    private double longTermThreshold = 0.8;

    private boolean decayEnabled = true;
    private String decayAlgorithm = "ebbinghaus";
    private double decayBaseRetention = 1.0;
    private double decayForgettingRate = 0.1;
    private double decayReinforcementFactor = 0.3;

    public IntelligentMemoryConfig() {}

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getInitialRetention() {
        return initialRetention;
    }

    public void setInitialRetention(double initialRetention) {
        this.initialRetention = initialRetention;
    }

    public double getDecayRate() {
        return decayRate;
    }

    public void setDecayRate(double decayRate) {
        this.decayRate = decayRate;
    }

    public double getReinforcementFactor() {
        return reinforcementFactor;
    }

    public void setReinforcementFactor(double reinforcementFactor) {
        this.reinforcementFactor = reinforcementFactor;
    }

    public double getWorkingThreshold() {
        return workingThreshold;
    }

    public void setWorkingThreshold(double workingThreshold) {
        this.workingThreshold = workingThreshold;
    }

    public double getShortTermThreshold() {
        return shortTermThreshold;
    }

    public void setShortTermThreshold(double shortTermThreshold) {
        this.shortTermThreshold = shortTermThreshold;
    }

    public double getLongTermThreshold() {
        return longTermThreshold;
    }

    public void setLongTermThreshold(double longTermThreshold) {
        this.longTermThreshold = longTermThreshold;
    }

    public boolean isDecayEnabled() {
        return decayEnabled;
    }

    public void setDecayEnabled(boolean decayEnabled) {
        this.decayEnabled = decayEnabled;
    }

    public String getDecayAlgorithm() {
        return decayAlgorithm;
    }

    public void setDecayAlgorithm(String decayAlgorithm) {
        this.decayAlgorithm = decayAlgorithm;
    }

    public double getDecayBaseRetention() {
        return decayBaseRetention;
    }

    public void setDecayBaseRetention(double decayBaseRetention) {
        this.decayBaseRetention = decayBaseRetention;
    }

    public double getDecayForgettingRate() {
        return decayForgettingRate;
    }

    public void setDecayForgettingRate(double decayForgettingRate) {
        this.decayForgettingRate = decayForgettingRate;
    }

    public double getDecayReinforcementFactor() {
        return decayReinforcementFactor;
    }

    public void setDecayReinforcementFactor(double decayReinforcementFactor) {
        this.decayReinforcementFactor = decayReinforcementFactor;
    }
}

