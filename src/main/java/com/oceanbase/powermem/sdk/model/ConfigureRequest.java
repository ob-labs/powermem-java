package com.oceanbase.powermem.sdk.model;

/**
 * Request DTO for applying configuration to a running service (remote mode).
 *
 * <p>Pure Java core mode will not need this; configuration is local. This exists to optionally mirror
 * benchmark REST server behavior.</p>
 *
 * <p>Python reference: {@code benchmark/server/main.py} ({@code /configure})</p>
 */
public class ConfigureRequest {
}

