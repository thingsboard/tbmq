package org.thingsboard.mqtt.broker.common.data.queue;

import lombok.Data;

@Data
public class KafkaBroker {

    private final int brokerId;
    private final String address;
    private final long brokerSize; // in bytes

}
