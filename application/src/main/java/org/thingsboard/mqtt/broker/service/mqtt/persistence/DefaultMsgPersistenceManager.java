/**
 * Copyright © 2016-2020 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.mqtt.broker.service.mqtt.persistence;

import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.ApplicationPersistenceSessionService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationPersistenceProcessor;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.SessionState;

import java.util.Collection;

import static org.thingsboard.mqtt.broker.common.data.ClientType.APPLICATION;
import static org.thingsboard.mqtt.broker.common.data.ClientType.DEVICE;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultMsgPersistenceManager implements MsgPersistenceManager {

    private final ApplicationPersistenceSessionService applicationPersistenceSessionService;
    private final ApplicationPersistenceProcessor applicationPersistenceProcessor;

    private final PublishMsgDeliveryService publishMsgDeliveryService;

    @Override
    public void processPublish(QueueProtos.PublishMsgProto publishMsgProto, Collection<Subscription> persistentSubscriptions) {
        for (Subscription msgSubscription : persistentSubscriptions) {
            ClientInfo clientInfo = msgSubscription.getSessionInfo().getClientInfo();
            String clientId = clientInfo.getClientId();
            ClientType clientType = clientInfo.getType();
            if (clientType == DEVICE) {
                // TODO implement device persistence
                trySendMsg(publishMsgProto, msgSubscription);
            } else if (clientType == APPLICATION) {
                applicationPersistenceSessionService.processMsgPersistence(clientId, msgSubscription.getMqttQoSValue(), publishMsgProto);
            } else {
                log.warn("[{}] Persistence for clientType {} is not supported.", clientId, clientType);
            }
        }
    }

    @Override
    public void processPersistedMessages(ClientSessionCtx clientSessionCtx) {
        ClientType clientType = clientSessionCtx.getSessionInfo().getClientInfo().getType();
        if (clientType == APPLICATION) {
            applicationPersistenceProcessor.startProcessingPersistedMessages(clientSessionCtx);
        }
    }

    @Override
    public void stopProcessingPersistedMessages(ClientInfo clientInfo) {
        if (clientInfo.getType() == APPLICATION) {
            applicationPersistenceProcessor.stopProcessingPersistedMessages(clientInfo.getClientId());
        }
    }

    @Override
    public void clearPersistedMessages(ClientInfo clientInfo) {
        if (clientInfo.getType() == APPLICATION) {
            applicationPersistenceProcessor.clearPersistedMsgs(clientInfo.getClientId());
            applicationPersistenceSessionService.clearPersistedCtx(clientInfo.getClientId());
        } else {
            log.debug("[{}] Persisted messages are not supported for client type {}.", clientInfo.getClientId(), clientInfo.getType());
        }
    }

    @Override
    public void acknowledgePersistedMsgDelivery(int packetId, ClientSessionCtx clientSessionCtx) {
        ClientInfo clientInfo = clientSessionCtx.getSessionInfo().getClientInfo();
        String clientId = clientInfo.getClientId();
        if (clientInfo.getType() == APPLICATION) {
            applicationPersistenceProcessor.acknowledgeDelivery(clientId, packetId);
        }
    }

    private void trySendMsg(QueueProtos.PublishMsgProto publishMsgProto, Subscription subscription) {
        ClientSessionCtx sessionCtx = subscription.getSessionCtx();
        if (sessionCtx == null || sessionCtx.getSessionState() != SessionState.CONNECTED) {
            return;
        }
        int packetId = subscription.getMqttQoSValue() == MqttQoS.AT_MOST_ONCE.value() ? -1 : sessionCtx.nextMsgId();
        int minQoSValue = Math.min(subscription.getMqttQoSValue(), publishMsgProto.getQos());
        MqttQoS mqttQoS = MqttQoS.valueOf(minQoSValue);
        publishMsgDeliveryService.sendPublishMsgToClient(sessionCtx, packetId, publishMsgProto.getTopicName(),
                mqttQoS, publishMsgProto.getPayload().toByteArray());
    }
}
