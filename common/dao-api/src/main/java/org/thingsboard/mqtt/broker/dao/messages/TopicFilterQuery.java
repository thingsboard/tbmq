package org.thingsboard.mqtt.broker.dao.messages;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.util.Set;

@Getter
@ToString
@AllArgsConstructor
public class TopicFilterQuery {
    private final Set<String> matchingTopics;
    private final Long lastTimestamp;
}
