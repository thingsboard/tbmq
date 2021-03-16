package org.thingsboard.mqtt.broker.dao.messages;

import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;

import java.util.List;

public interface DeviceMsgService {
    void save(List<DevicePublishMsg> devicePublishMessages);

    List<DevicePublishMsg> findPersistedMessages(String clientId);

    void removePersistedMessages(String clientId);

    void removePersistedMessage(String clientId, int packetId);

    void updatePacketId(String clientId, Long serialNumber, int packetId);
}
