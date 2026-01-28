package com.oceanbase.powermem.sdk.agent.types;

/**
 * Memory scope enumeration (agent/user/group/system).
 *
 * <p>Python reference: {@code src/powermem/agent/types.py}</p>
 */
public enum MemoryScope {
    AGENT,
    USER,
    GROUP,
    SYSTEM,
    PRIVATE,
    PUBLIC,
    RESTRICTED
}

