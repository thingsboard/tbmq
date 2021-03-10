package org.thingsboard.mqtt.broker.common.data;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@EqualsAndHashCode
@NoArgsConstructor
public class PublishedMsgInfo {
    private long timestamp;
    private String subscriptionTopicFilter;
    private String topic;
    private int packetId;
}
