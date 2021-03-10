package org.thingsboard.mqtt.broker.service.mqtt.persistence.device;

import java.util.Set;

public interface PersistedTopicsService {
    void addTopic(String topic);

    Set<String> getMatchingTopics(String topicFilter);
}
