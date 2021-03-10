package org.thingsboard.mqtt.broker.service.mqtt.persistence.device;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTopicEventServiceImpl implements DeviceTopicEventService {
    // TODO: or maybe store whole DeviceTopicEvents (all events for one topic)

    @Override
    public void saveTopicEvent(String clientId, String topicFilter, TopicEventType eventType, Long timestamp) {

    }

    @Override
    public void clearPersistedEvents(String clientId) {

    }

    @Override
    public Long getLastEventTime(String clientId, String topicFilter, TopicEventType eventType) {
        return null;
    }
}
