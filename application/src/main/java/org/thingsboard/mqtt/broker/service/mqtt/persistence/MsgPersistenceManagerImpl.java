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

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.session.state.ClientSessionActorStateReader;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.ApplicationMsgQueueService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.ApplicationPersistenceProcessor;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.DeviceMsgQueueService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.DevicePersistenceProcessor;
import org.thingsboard.mqtt.broker.service.processing.MultiplePublishMsgCallbackWrapper;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgCallback;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.thingsboard.mqtt.broker.common.data.ClientType.APPLICATION;
import static org.thingsboard.mqtt.broker.common.data.ClientType.DEVICE;

@Slf4j
@Service
public class MsgPersistenceManagerImpl implements MsgPersistenceManager {

    @Autowired
    private GenericClientSessionCtxManager genericClientSessionCtxManager;

    @Autowired
    private ApplicationMsgQueueService applicationMsgQueueService;
    @Autowired
    private ApplicationPersistenceProcessor applicationPersistenceProcessor;

    @Autowired
    private DeviceMsgQueueService deviceMsgQueueService;
    @Autowired
    private DevicePersistenceProcessor devicePersistenceProcessor;

    // TODO: think about case when client is DEVICE and then is changed to APPLICATION and vice versa

    @Override
    public void processPublish(QueueProtos.PublishMsgProto publishMsgProto, Collection<Subscription> persistentSubscriptions, PublishMsgCallback callback) {
        List<Subscription> deviceSubscriptions = new ArrayList<>();
        List<Subscription> applicationSubscriptions = new ArrayList<>();

        for (Subscription msgSubscription : persistentSubscriptions) {
            ClientInfo clientInfo = msgSubscription.getSessionInfo().getClientInfo();
            ClientType clientType = clientInfo.getType();
            if (clientType == DEVICE) {
                deviceSubscriptions.add(msgSubscription);
            } else if (clientType == APPLICATION) {
                applicationSubscriptions.add(msgSubscription);
            } else {
                log.warn("[{}] Persistence for clientType {} is not supported.", clientInfo.getClientId(), clientType);
            }
        }
        int callbackCount = applicationSubscriptions.size() + deviceSubscriptions.size();
        PublishMsgCallback callbackWrapper = new MultiplePublishMsgCallbackWrapper(callbackCount, callback);
        for (Subscription deviceSubscription : deviceSubscriptions) {
            String deviceClientId = deviceSubscription.getSessionInfo().getClientInfo().getClientId();
            deviceMsgQueueService.sendMsg(deviceClientId, createReceiverPublishMsg(deviceSubscription, publishMsgProto), callbackWrapper);
        }
        for (Subscription applicationSubscription : applicationSubscriptions) {
            String applicationClientId = applicationSubscription.getSessionInfo().getClientInfo().getClientId();
            applicationMsgQueueService.sendMsg(applicationClientId, createReceiverPublishMsg(applicationSubscription, publishMsgProto),callbackWrapper);
        }
    }

    private QueueProtos.PublishMsgProto createReceiverPublishMsg(Subscription clientSubscription, QueueProtos.PublishMsgProto publishMsgProto) {
        int minQoSValue = Math.min(clientSubscription.getMqttQoSValue(), publishMsgProto.getQos());
        return publishMsgProto.toBuilder()
                .setPacketId(0)
                .setQos(minQoSValue)
                .build();
    }

    @Override
    public void processPersistedMessages(ClientSessionActorStateReader actorState) {
        ClientSessionCtx clientSessionCtx = actorState.getCurrentSessionCtx();
        genericClientSessionCtxManager.resendPersistedPubRelMessages(clientSessionCtx);
        ClientType clientType = clientSessionCtx.getSessionInfo().getClientInfo().getType();
        if (clientType == APPLICATION) {
            applicationPersistenceProcessor.startProcessingPersistedMessages(actorState);
        } else if (clientType == DEVICE) {
            devicePersistenceProcessor.startProcessingPersistedMessages(clientSessionCtx);
        }
    }

    @Override
    public void stopProcessingPersistedMessages(ClientInfo clientInfo) {
        if (clientInfo.getType() == APPLICATION) {
            applicationPersistenceProcessor.stopProcessingPersistedMessages(clientInfo.getClientId());
        } else if (clientInfo.getType() == DEVICE) {
            devicePersistenceProcessor.stopProcessingPersistedMessages(clientInfo.getClientId());
            // TODO: stop select query if it's running
        } else {
            log.warn("[{}] Persisted messages are not supported for client type {}.", clientInfo.getClientId(), clientInfo.getType());
        }
    }

    @Override
    public void saveAwaitingQoS2Packets(ClientSessionCtx clientSessionCtx) {
        genericClientSessionCtxManager.saveAwaitingQoS2Packets(clientSessionCtx);
    }

    @Override
    public void clearPersistedMessages(ClientInfo clientInfo) {
        genericClientSessionCtxManager.clearAwaitingQoS2Packets(clientInfo.getClientId());
        if (clientInfo.getType() == APPLICATION) {
            applicationPersistenceProcessor.clearPersistedMsgs(clientInfo.getClientId());
            applicationMsgQueueService.clearQueueContext(clientInfo.getClientId());
        } else if (clientInfo.getType() == DEVICE) {
            devicePersistenceProcessor.clearPersistedMsgs(clientInfo.getClientId());
        }
    }

    @Override
    public void processPubAck(int packetId, ClientSessionCtx clientSessionCtx) {
        ClientInfo clientInfo = clientSessionCtx.getSessionInfo().getClientInfo();
        String clientId = clientInfo.getClientId();
        if (clientInfo.getType() == APPLICATION) {
            applicationPersistenceProcessor.processPubAck(clientId, packetId);
        } else if (clientInfo.getType() == DEVICE) {
            devicePersistenceProcessor.processPubAck(clientId, packetId);
        }
    }

    @Override
    public void processPubRec(int packetId, ClientSessionCtx clientSessionCtx) {
        ClientInfo clientInfo = clientSessionCtx.getSessionInfo().getClientInfo();
        String clientId = clientInfo.getClientId();
        if (clientInfo.getType() == APPLICATION) {
            applicationPersistenceProcessor.processPubRec(clientSessionCtx, packetId);
        } else if (clientInfo.getType() == DEVICE) {
            devicePersistenceProcessor.processPubRec(clientId, packetId);
        }
    }

    @Override
    public void processPubRel(int packetId, ClientSessionCtx clientSessionCtx) {
        genericClientSessionCtxManager.processPubRel(packetId, clientSessionCtx);
    }

    @Override
    public void processPubComp(int packetId, ClientSessionCtx clientSessionCtx) {
        ClientInfo clientInfo = clientSessionCtx.getSessionInfo().getClientInfo();
        String clientId = clientInfo.getClientId();
        if (clientInfo.getType() == APPLICATION) {
            applicationPersistenceProcessor.processPubComp(clientId, packetId);
        } else if (clientInfo.getType() == DEVICE) {
            devicePersistenceProcessor.processPubComp(clientId, packetId);
        }
    }
}
