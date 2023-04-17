package org.thingsboard.mqtt.broker.common.data.kv;

import lombok.Data;

@Data
public class CleanUpResult {

    private final int deletedPartitions;
    private final long deletedRows;

    public static CleanUpResult newInstance() {
        return new CleanUpResult(0, 0L);
    }
}
