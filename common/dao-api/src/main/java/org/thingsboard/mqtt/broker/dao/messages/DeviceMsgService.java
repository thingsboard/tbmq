package org.thingsboard.mqtt.broker.dao.messages;

import com.google.common.util.concurrent.ListenableFuture;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;

import java.util.List;
import java.util.Set;

public interface DeviceMsgService {
    ListenableFuture<Void> save(DevicePublishMsg devicePublishMsg);

    List<DevicePublishMsg> findPersistedMessages(List<TopicFilterQuery> topicFilterQueries);

    List<String> getAllTopics();
}
