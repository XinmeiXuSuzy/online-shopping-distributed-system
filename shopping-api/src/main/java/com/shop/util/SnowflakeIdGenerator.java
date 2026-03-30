package com.shop.util;

import org.springframework.stereotype.Component;

/**
 * Snowflake ID generator: 64-bit IDs that are monotonically increasing,
 * distributed-safe, and expose no business volume.
 *
 * Bit layout (63 bits usable):
 *   [41 bits timestamp ms] [10 bits machine id] [12 bits sequence]
 */
@Component
public class SnowflakeIdGenerator {

    private static final long EPOCH = 1700000000000L; // 2023-11-14 ~

    private static final long MACHINE_ID_BITS = 10L;
    private static final long SEQUENCE_BITS   = 12L;

    private static final long MAX_MACHINE_ID = ~(-1L << MACHINE_ID_BITS);  // 1023
    private static final long MAX_SEQUENCE   = ~(-1L << SEQUENCE_BITS);    // 4095

    private static final long MACHINE_ID_SHIFT  = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT   = SEQUENCE_BITS + MACHINE_ID_BITS;

    private final long machineId;
    private long lastTimestamp = -1L;
    private long sequence      = 0L;

    public SnowflakeIdGenerator() {
        // Default machine id 1; in ECS, override via env var MACHINE_ID
        long id = 1L;
        String envId = System.getenv("MACHINE_ID");
        if (envId != null) {
            id = Long.parseLong(envId) & MAX_MACHINE_ID;
        }
        this.machineId = id;
    }

    public synchronized long nextId() {
        long now = System.currentTimeMillis();

        if (now == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                now = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = now;

        return ((now - EPOCH) << TIMESTAMP_SHIFT)
                | (machineId << MACHINE_ID_SHIFT)
                | sequence;
    }

    private long waitNextMillis(long lastTs) {
        long ts = System.currentTimeMillis();
        while (ts <= lastTs) {
            ts = System.currentTimeMillis();
        }
        return ts;
    }
}
