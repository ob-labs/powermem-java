package com.oceanbase.powermem.sdk.transport;

/**
 * Transport abstraction for communicating with external services.
 *
 * <p>Pure Java core migration may not require an HTTP transport (if everything is in-process), but we
 * keep this abstraction to optionally support remote modes (REST/MCP) or provider calls.</p>
 *
 * <p>No direct Python equivalent; Python uses {@code httpx} for HTTP calls in integrations.</p>
 */
public interface Transport {
}

