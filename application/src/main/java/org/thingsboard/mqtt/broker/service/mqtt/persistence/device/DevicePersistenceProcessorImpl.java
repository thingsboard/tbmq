/**
 * Copyright © 2016-2020 The Thingsboard Authors
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.mqtt.broker.service.mqtt.persistence.device;

import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgService;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import javax.annotation.PostConstruct;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DevicePersistenceProcessorImpl implements DevicePersistenceProcessor {

    private final PublishMsgDeliveryService publishMsgDeliveryService;
    private final DeviceMsgService deviceMsgService;

    @PostConstruct
    public void init() {
    }

    @Override
    public void clearPersistedMsgs(String clientId) {
        deviceMsgService.removePersistedMessages(clientId);
    }

    @Override
    public void acknowledgeDelivery(String clientId, int packetId) {
        deviceMsgService.removePersistedMessage(clientId, packetId);
    }

    @Override
    public void startProcessingPersistedMessages(ClientSessionCtx clientSessionCtx) {
        // TODO: move this to Device Actor
        String clientId = clientSessionCtx.getClientId();
        List<DevicePublishMsg> persistedMessages = deviceMsgService.findPersistedMessages(clientId);
        for (DevicePublishMsg persistedMessage : persistedMessages) {
            Integer persistedPacketId = persistedMessage.getPacketId();
            boolean isDup = persistedPacketId != null;
            int packetId;
            if (persistedPacketId != null) {
                // TODO: change this after Actors implementation
                clientSessionCtx.updateMsgId(persistedPacketId);
                packetId = persistedPacketId;
            } else {
                packetId = clientSessionCtx.nextMsgId();
                // TODO: think about making this batch-update
                deviceMsgService.updatePacketId(clientId, persistedMessage.getSerialNumber(), packetId);
            }
            publishMsgDeliveryService.sendPublishMsgToClient(clientSessionCtx, packetId,
                    persistedMessage.getTopic(), MqttQoS.valueOf(persistedMessage.getQos()), isDup,
                    persistedMessage.getPayload());

        }
    }

    @Override
    public void stopProcessingPersistedMessages(String clientId) {
        // TODO: think how to properly stop processing in actor architecture
    }

}
