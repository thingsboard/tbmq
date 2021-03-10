package org.thingsboard.mqtt.broker.dao.messages;

import com.google.common.util.concurrent.ListenableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceMsgServiceImpl implements DeviceMsgService {
    @Value("${application.mqtt.persistent-session.device.persisted-messages-limit}")
    private int messagesLimit;

    private final DeviceMsgDao deviceMsgDao;

    // TODO: clear old messages from DB

    @Override
    public ListenableFuture<Void> save(DevicePublishMsg devicePublishMsg) {
        log.trace("Saving device publish msg. Timestamp - {}, topic - {}, qos - {}.",
                devicePublishMsg.getTimestamp(), devicePublishMsg.getTopic(), devicePublishMsg.getQos());
        return deviceMsgDao.save(devicePublishMsg);
    }

    @Override
    public List<DevicePublishMsg> findPersistedMessages(List<TopicFilterQuery> topicFilterQueries) {
        log.trace("Loading persisted messages for topics - {}.", topicFilterQueries);
        return deviceMsgDao.findPersistedMessages(topicFilterQueries, messagesLimit);
    }

    @Override
    public List<String> getAllTopics() {
        log.trace("Loading all distinct topics.");
        return deviceMsgDao.getAllTopics();
    }
}
