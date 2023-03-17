package org.thingsboard.mqtt.broker.common.data.queue;

import lombok.Data;

@Data
public class KafkaTopic {

    private String name;
    private int partitions;
    private int replicationFactor;
    private long size; // in bytes

}
