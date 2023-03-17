package org.thingsboard.mqtt.broker.common.data.queue;

import lombok.Data;

@Data
public class KafkaConsumerGroup {

    private KafkaConsumerGroupState state;
    private String groupId;
    private short members;
    private int lag;

}
