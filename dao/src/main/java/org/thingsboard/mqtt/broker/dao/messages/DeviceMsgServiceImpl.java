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
    @Value("${mqtt.persistent-session.device.persisted-messages-limit}")
    private int messagesLimit;

    private final DeviceMsgDao deviceMsgDao;

    // TODO: clear old messages from DB

    @Override
    public void save(List<DevicePublishMsg> devicePublishMessages) {
        log.trace("Saving device publish messages - {}.", devicePublishMessages);
        deviceMsgDao.save(devicePublishMessages);
    }

    @Override
    public List<DevicePublishMsg> findPersistedMessages(String clientId) {
        log.trace("Loading persisted messages for client - {}.", clientId);
        return deviceMsgDao.findPersistedMessages(clientId, messagesLimit);
    }
}
