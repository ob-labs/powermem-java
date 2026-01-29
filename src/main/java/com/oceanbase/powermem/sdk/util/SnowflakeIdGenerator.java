package com.oceanbase.powermem.sdk.util;

/**
 * Minimal Snowflake-style ID generator.
 *
 * <p>Generates sortable 64-bit IDs as decimal strings. This is intentionally dependency-free and
 * suitable for cross-language uniqueness requirements described in the Java SDK plan.</p>
 */
public final class SnowflakeIdGenerator {
    // Custom epoch (2020-01-01T00:00:00Z)
    private static final long EPOCH = 1577836800000L;

    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private final long workerId;
    private final long datacenterId;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(long datacenterId, long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId must be between 0 and " + MAX_WORKER_ID);
        }
        if (datacenterId < 0 || datacenterId > MAX_DATACENTER_ID) {
            throw new IllegalArgumentException("datacenterId must be between 0 and " + MAX_DATACENTER_ID);
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    public static SnowflakeIdGenerator defaultGenerator() {
        return new SnowflakeIdGenerator(0, 0);
    }

    public synchronized String nextId() {
        long timestamp = currentTime();
        if (timestamp < lastTimestamp) {
            // clock moved backwards; fall back to last timestamp
            timestamp = lastTimestamp;
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        long id = ((timestamp - EPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
        // This implementation stays within 63 bits, so it fits in signed long and stays compatible
        // with SQLite INTEGER storage and Python-side Snowflake IDs.
        return Long.toString(id);
    }

    private static long waitNextMillis(long lastTimestamp) {
        long ts = currentTime();
        while (ts <= lastTimestamp) {
            ts = currentTime();
        }
        return ts;
    }

    private static long currentTime() {
        return System.currentTimeMillis();
    }
}

