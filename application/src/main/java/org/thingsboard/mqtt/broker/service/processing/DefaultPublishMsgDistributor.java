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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.PacketIdAndOffset;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.ApplicationPersistenceProcessor;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.PersistenceSessionHandler;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

import static org.thingsboard.mqtt.broker.common.data.ClientType.APPLICATION;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultPublishMsgDistributor implements PublishMsgDistributor {

    @Qualifier("ApplicationPersistenceSessionHandler")
    private final PersistenceSessionHandler applicationHandler;

    private final MqttMessageGenerator mqttMessageGenerator;

    private final ApplicationPersistenceProcessor applicationPersistenceProcessor;

    @Override
    public void processPublish(QueueProtos.PublishMsgProto publishMsgProto, Collection<Subscription> msgSubscriptions) {
        List<Subscription> applicationSubscriptions = new ArrayList<>();
        List<Subscription> deviceSubscriptions = new ArrayList<>();
        List<Subscription> unPersistedSubscriptions = new ArrayList<>();
        for (Subscription msgSubscription : msgSubscriptions) {
            ClientSession clientSession = msgSubscription.getClientSession();
            if (!clientSession.isPersistent()
                    || msgSubscription.getMqttQoSValue() == MqttQoS.AT_MOST_ONCE.value()
                    || publishMsgProto.getQos() == MqttQoS.AT_MOST_ONCE.value()) {
                unPersistedSubscriptions.add(msgSubscription);
                continue;
            }
            ClientType clientType = clientSession.getClientInfo().getType();
            switch (clientType) {
                case DEVICE:
                    deviceSubscriptions.add(msgSubscription);
                    break;
                case APPLICATION:
                    applicationSubscriptions.add(msgSubscription);
                    break;
                default:
                    throw new MqttException("Clients of type " + clientType + " are not supported.");
            }
        }
        processNotPersistedMsg(publishMsgProto, unPersistedSubscriptions);
        processNotPersistedMsg(publishMsgProto, deviceSubscriptions);
        applicationHandler.processMsgPersistenceSessions(publishMsgProto, applicationSubscriptions);
    }

    @Override
    public void startSendingPersistedMessages(ClientSessionCtx clientSessionCtx) {
        ClientInfo clientInfo = clientSessionCtx.getSessionInfo().getClientInfo();
        ClientType clientType = clientInfo.getType();
        switch (clientType) {
            case DEVICE:
                break;
            case APPLICATION:
                applicationPersistenceProcessor.startConsumingPersistedMsgs(clientInfo.getClientId(), clientSessionCtx);
                break;
            default:
                throw new MqttException("Clients of type " + clientType + " are not supported.");
        }
    }

    @Override
    public void clearPersistedMessages(ClientInfo clientInfo) {
        ClientType clientType = clientInfo.getType();
        switch (clientType) {
            case DEVICE:
                break;
            case APPLICATION:
                applicationHandler.clearPersistedMsgs(clientInfo.getClientId());
                break;
            default:
                throw new MqttException("Clients of type " + clientType + " are not supported.");
        }
    }

    @Override
    public void acknowledgeSuccessfulDelivery(int packetId, ClientSessionCtx clientSessionCtx) {
        ClientInfo clientInfo = clientSessionCtx.getSessionInfo().getClientInfo();
        String clientId = clientInfo.getClientId();
        if (clientInfo.getType() != APPLICATION) {
               return;
        }
        UUID sessionId = clientSessionCtx.getSessionId();
        Queue<PacketIdAndOffset> processingPacketsQueue = clientSessionCtx.getPacketsInfoQueue();
        PacketIdAndOffset nextPacketIdAndOffset = processingPacketsQueue.peek();
        if (nextPacketIdAndOffset == null) {
            log.error("[{}][{}] No unacknowledged packets in the queue. Received PUBACK for packet {}.",
                    clientId, sessionId, packetId);
            return;
        }
        if (nextPacketIdAndOffset.getPacketId() == packetId) {
            processingPacketsQueue.remove();
            applicationPersistenceProcessor.acknowledgeSuccessfulDelivery(clientId, nextPacketIdAndOffset.getOffset());
            return;
        }

        boolean packetExistsInQueue = processingPacketsQueue.stream().anyMatch(packetIdAndOffset -> packetId == packetIdAndOffset.getPacketId());
        if (!packetExistsInQueue) {
            log.error("[{}][{}] Cannot find packetId in the queue. Received PUBACK for packet {}.",
                    clientId, sessionId, packetId);
            return;
        }
        while (!processingPacketsQueue.isEmpty()) {
            nextPacketIdAndOffset = processingPacketsQueue.remove();
            if (nextPacketIdAndOffset.getPacketId() == packetId) {
                applicationPersistenceProcessor.acknowledgeSuccessfulDelivery(clientId, nextPacketIdAndOffset.getOffset());
                return;
            } else {
                log.debug("[{}][{}] Skipping packetId - {}.", clientId, sessionId, nextPacketIdAndOffset.getPacketId());
            }
        }
    }

    private void processNotPersistedMsg(QueueProtos.PublishMsgProto publishMsgProto, List<Subscription> unPersistedSubscriptions) {
        for (Subscription subscription : unPersistedSubscriptions) {
            ClientSession clientSession = subscription.getClientSession();
            ClientSessionCtx sessionCtx = subscription.getSessionCtx();
            if (sessionCtx == null) {
                log.warn("[{}][{}] Persistent session is not allowed for client.",
                        clientSession.getClientInfo().getType(), clientSession.getClientInfo().getClientId());
                continue;
            }
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
}
