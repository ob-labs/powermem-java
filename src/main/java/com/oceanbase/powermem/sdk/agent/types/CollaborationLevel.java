package com.oceanbase.powermem.sdk.agent.types;

/**
 * Collaboration levels (isolated/shared) for multi-agent memory.
 *
 * <p>Python reference: {@code src/powermem/agent/types.py}</p>
 */
public enum CollaborationLevel {
    ISOLATED,
    COLLABORATIVE,
    READ_ONLY,
    READ_WRITE
}

