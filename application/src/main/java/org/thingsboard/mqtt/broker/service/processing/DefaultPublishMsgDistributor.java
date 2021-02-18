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
package org.thingsboard.mqtt.broker.service.processing;

import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.ApplicationPersistenceSessionService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationPersistenceProcessor;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.Collection;
import java.util.UUID;

import static org.thingsboard.mqtt.broker.common.data.ClientType.APPLICATION;
import static org.thingsboard.mqtt.broker.common.data.ClientType.DEVICE;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultPublishMsgDistributor implements PublishMsgDistributor {

    private final ApplicationPersistenceSessionService applicationPersistenceSessionService;

    private final MqttMessageGenerator mqttMessageGenerator;

    private final ApplicationPersistenceProcessor applicationPersistenceProcessor;

    @Override
    public void processPublish(QueueProtos.PublishMsgProto publishMsgProto, Collection<Subscription> msgSubscriptions) {
        for (Subscription msgSubscription : msgSubscriptions) {
            ClientSession clientSession = msgSubscription.getClientSession();
            String clientId = clientSession.getClientInfo().getClientId();
            ClientType clientType = clientSession.getClientInfo().getType();
            if (isNotPersisted(publishMsgProto, msgSubscription, clientSession)) {
                processNotPersistedMsg(publishMsgProto, msgSubscription);
            } else if (clientType == DEVICE) {
                // TODO implement device persistence
                processNotPersistedMsg(publishMsgProto, msgSubscription);
            } else if (clientType == APPLICATION) {
                applicationPersistenceSessionService.processMsgPersistence(clientId, msgSubscription.getMqttQoSValue(), publishMsgProto);
            } else {
                log.warn("[{}] Persistence for clientType {} is not supported.", clientId, clientType);
            }
        }
    }

    @Override
    public void processPersistedMessages(ClientSessionCtx clientSessionCtx) {
        ClientInfo clientInfo = clientSessionCtx.getSessionInfo().getClientInfo();
        ClientType clientType = clientInfo.getType();
        String clientId = clientInfo.getClientId();
        if (clientType == APPLICATION) {
            applicationPersistenceProcessor.startProcessingPersistedMessages(clientId, clientSessionCtx);
        } else {
            log.debug("[{}] Persisted messages are not supported for client type {}.", clientId, clientType);
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
    public void acknowledgeDelivery(int packetId, ClientSessionCtx clientSessionCtx) {
        ClientInfo clientInfo = clientSessionCtx.getSessionInfo().getClientInfo();
        String clientId = clientInfo.getClientId();
        if (clientInfo.getType() == APPLICATION) {
            applicationPersistenceProcessor.acknowledgeDelivery(clientId, packetId);
        }
    }

    private boolean isNotPersisted(QueueProtos.PublishMsgProto publishMsgProto, Subscription subscription, ClientSession clientSession) {
        return !clientSession.isPersistent()
                || subscription.getMqttQoSValue() == MqttQoS.AT_MOST_ONCE.value()
                || publishMsgProto.getQos() == MqttQoS.AT_MOST_ONCE.value();
    }

    private void processNotPersistedMsg(QueueProtos.PublishMsgProto publishMsgProto, Subscription subscription) {
        ClientSession clientSession = subscription.getClientSession();
        if (!clientSession.isConnected()) {
            return;
        }
        ClientSessionCtx sessionCtx = subscription.getSessionCtx();
        int packetId = subscription.getMqttQoSValue() == MqttQoS.AT_MOST_ONCE.value() ? -1 : sessionCtx.nextMsgId();
        int minQoSValue = Math.min(subscription.getMqttQoSValue(), publishMsgProto.getQos());
        MqttQoS mqttQoS = MqttQoS.valueOf(minQoSValue);
        MqttPublishMessage mqttPubMsg = mqttMessageGenerator.createPubMsg(packetId, publishMsgProto.getTopicName(),
                mqttQoS, publishMsgProto.getPayload().toByteArray());
        String clientId = sessionCtx.getSessionInfo().getClientInfo().getClientId();
        UUID sessionId = sessionCtx.getSessionId();
        try {
            sessionCtx.getChannel().writeAndFlush(mqttPubMsg);
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to send publish msg to MQTT client.", clientId, sessionId);
            log.trace("Detailed error:", e);
        }
    }
}
