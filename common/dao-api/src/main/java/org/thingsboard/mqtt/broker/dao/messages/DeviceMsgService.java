package org.thingsboard.mqtt.broker.dao.messages;

import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;

import java.util.List;

public interface DeviceMsgService {
    void save(List<DevicePublishMsg> devicePublishMessages);

    List<DevicePublishMsg> findPersistedMessages(String clientId);
}
