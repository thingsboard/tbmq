package org.thingsboard.mqtt.broker.service.mqtt.persistence.device;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode
@AllArgsConstructor
@Getter
public class TopicTimestamp {
    private final String topic;
    private final Long timestamp;
}
