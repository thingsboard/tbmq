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
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DevicePersistenceSessionServiceImpl implements DevicePersistenceSessionService {

    private final PublishMsgDeliveryService publishMsgDeliveryService;

    @PostConstruct
    public void init() {
    }

    @Override
    public void processMsgPersistence(List<Subscription> deviceSubscriptions, QueueProtos.PublishMsgProto publishMsgProto) {

    }

    @Override
    public void clearPersistedCtx(String clientId) {
        // TODO: think about clearing persisted messages
    }

    @Override
    public void acknowledgeDelivery(String clientId, int packetId) {
    }

    @Override
    public void startProcessingPersistedMessages(ClientSessionCtx clientSessionCtx) {
        // TODO: load messages for selected device
        String clientId = clientSessionCtx.getClientId();
    }

    @Override
    public void stopProcessingPersistedMessages(String clientId) {
    }

    private void sendMsg(Long timestamp, String subscriptionTopicFilter, int subscriptionQoS,
                         String msgTopic, int msgQoS, byte[] payload, ClientSessionCtx sessionCtx) {
        String clientId = sessionCtx.getClientId();

        // TODO:
        int packetId = -1;

        int minQoSValue = Math.min(subscriptionQoS, msgQoS);
        publishMsgDeliveryService.sendPublishMsgToClient(sessionCtx, packetId, msgTopic, MqttQoS.valueOf(minQoSValue), false, payload);
    }

    private void resendMsg(int packetId, int subscriptionQoS, String msgTopic, int msgQoS, byte[] payload, ClientSessionCtx sessionCtx) {
        int minQoSValue = Math.min(subscriptionQoS, msgQoS);
        publishMsgDeliveryService.sendPublishMsgToClient(sessionCtx, packetId, msgTopic, MqttQoS.valueOf(minQoSValue), true, payload);
    }


    @PreDestroy
    public void destroy() {
    }
}
