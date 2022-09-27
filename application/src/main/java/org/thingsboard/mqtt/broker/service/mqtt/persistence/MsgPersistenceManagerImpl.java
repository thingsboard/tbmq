/**
 * Copyright © 2016-2022 The Thingsboard Authors
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorStateInfo;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos.PublishMsgProto;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.ApplicationMsgQueuePublisher;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.ApplicationPersistenceProcessor;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.DevicePersistenceProcessor;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.queue.DeviceMsgQueuePublisher;
import org.thingsboard.mqtt.broker.service.processing.MultiplePublishMsgCallbackWrapper;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgCallback;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.service.subscription.SubscriptionType;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.thingsboard.mqtt.broker.common.data.ClientType.APPLICATION;
import static org.thingsboard.mqtt.broker.common.data.ClientType.DEVICE;

@Slf4j
@Service
@RequiredArgsConstructor
public class MsgPersistenceManagerImpl implements MsgPersistenceManager {

    private final GenericClientSessionCtxManager genericClientSessionCtxManager;
    private final ApplicationMsgQueuePublisher applicationMsgQueuePublisher;
    private final ApplicationPersistenceProcessor applicationPersistenceProcessor;
    private final DeviceMsgQueuePublisher deviceMsgQueuePublisher;
    private final DevicePersistenceProcessor devicePersistenceProcessor;
    private final ClientLogger clientLogger;

    // TODO: think about case when client is DEVICE and then is changed to APPLICATION and vice versa

    @Override
    public void processPublish(PublishMsgProto publishMsgProto, List<Subscription> persistentSubscriptions, PublishMsgCallback callback) {
        List<Subscription> deviceSubscriptions = new ArrayList<>();
        List<Subscription> applicationSubscriptions = new ArrayList<>();

        fillSubscriptions(persistentSubscriptions, deviceSubscriptions, applicationSubscriptions);

        Map<SubscriptionType, List<Subscription>> appSubscriptionsByType = collectAppSubscriptionsByType(applicationSubscriptions);
        List<Subscription> commonAppSubscriptions = appSubscriptionsByType.getOrDefault(SubscriptionType.COMMON, Collections.emptyList());
        List<Subscription> sharedAppSubscriptions = appSubscriptionsByType.getOrDefault(SubscriptionType.SHARED, Collections.emptyList());

        Set<String> sharedTopics = sharedAppSubscriptions.stream()
                .collect(Collectors.groupingBy(Subscription::getTopicFilter)).keySet();

        int callbackCount = getCallbackCount(deviceSubscriptions, commonAppSubscriptions) + sharedTopics.size();
        PublishMsgCallback callbackWrapper = new MultiplePublishMsgCallbackWrapper(callbackCount, callback);

        String senderClientId = ProtoConverter.getClientId(publishMsgProto);
        clientLogger.logEvent(senderClientId, this.getClass(), "Before msg persistence");

        deviceSubscriptions.forEach(deviceSubscription ->
                deviceMsgQueuePublisher.sendMsg(
                        getClientIdFromSubscription(deviceSubscription),
                        createReceiverPublishMsg(deviceSubscription, publishMsgProto),
                        callbackWrapper));
        commonAppSubscriptions.forEach(applicationSubscription ->
                applicationMsgQueuePublisher.sendMsg(
                        getClientIdFromSubscription(applicationSubscription),
                        createReceiverPublishMsg(applicationSubscription, publishMsgProto),
                        callbackWrapper));
        sharedTopics.forEach(sharedTopic ->
                applicationMsgQueuePublisher.sendMsgToSharedTopic(
                        sharedTopic,
                        createReceiverPublishMsg(publishMsgProto),
                        callbackWrapper));

        clientLogger.logEvent(senderClientId, this.getClass(), "After msg persistence");
    }

    private String getClientIdFromSubscription(Subscription subscription) {
        return subscription.getClientSession().getSessionInfo().getClientInfo().getClientId();
    }

    private int getCallbackCount(List<Subscription> deviceSubscriptions,
                                 List<Subscription> applicationSubscriptions) {
        return applicationSubscriptions.size() + deviceSubscriptions.size();
    }

    private void fillSubscriptions(List<Subscription> persistentSubscriptions,
                                   List<Subscription> deviceSubscriptions,
                                   List<Subscription> applicationSubscriptions) {
        for (Subscription msgSubscription : persistentSubscriptions) {
            ClientInfo clientInfo = msgSubscription.getClientSession().getSessionInfo().getClientInfo();
            ClientType clientType = clientInfo.getType();
            if (clientType == DEVICE) {
                deviceSubscriptions.add(msgSubscription);
            } else if (clientType == APPLICATION) {
                applicationSubscriptions.add(msgSubscription);
            } else {
                log.warn("[{}] Persistence for clientType {} is not supported.", clientInfo.getClientId(), clientType);
            }
        }
    }

    private PublishMsgProto createReceiverPublishMsg(Subscription clientSubscription, PublishMsgProto publishMsgProto) {
        int minQoSValue = Math.min(clientSubscription.getMqttQoSValue(), publishMsgProto.getQos());
        return publishMsgProto.toBuilder()
                .setPacketId(0)
                .setQos(minQoSValue)
                .build();
    }

    private PublishMsgProto createReceiverPublishMsg(PublishMsgProto publishMsgProto) {
        return publishMsgProto.toBuilder()
                .setPacketId(0)
                .build();
    }

    @Override
    public void startProcessingPersistedMessages(ClientActorStateInfo actorState, boolean wasPrevSessionPersistent) {
        ClientSessionCtx clientSessionCtx = actorState.getCurrentSessionCtx();
        genericClientSessionCtxManager.resendPersistedPubRelMessages(clientSessionCtx);

        ClientType clientType = clientSessionCtx.getSessionInfo().getClientInfo().getType();
        if (clientType == APPLICATION) {
            applicationPersistenceProcessor.startProcessingPersistedMessages(actorState);
        } else if (clientType == DEVICE) {
            if (!wasPrevSessionPersistent) {
                // TODO: it's still possible to get old msgs if DEVICE queue is overloaded
                // in case some messages got persisted after session clear
                devicePersistenceProcessor.clearPersistedMsgs(clientSessionCtx.getClientId());
            }
            devicePersistenceProcessor.startProcessingPersistedMessages(clientSessionCtx);
        }
    }

    @Override
    public void startProcessingSharedSubscriptions(ClientSessionCtx clientSessionCtx, Set<TopicSharedSubscription> subscriptions) {
        ClientType clientType = clientSessionCtx.getSessionInfo().getClientInfo().getType();
        if (clientType == APPLICATION) {
            applicationPersistenceProcessor.startProcessingSharedSubscriptions(clientSessionCtx, subscriptions);
        } else if (clientType == DEVICE) {
            devicePersistenceProcessor.startProcessingSharedSubscriptions(clientSessionCtx, subscriptions);
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
        // TODO: make async
        genericClientSessionCtxManager.clearAwaitingQoS2Packets(clientInfo.getClientId());
        if (clientInfo.getType() == APPLICATION) {
            applicationPersistenceProcessor.clearPersistedMsgs(clientInfo.getClientId());
        } else if (clientInfo.getType() == DEVICE) {
            devicePersistenceProcessor.clearPersistedMsgs(clientInfo.getClientId());
        }
    }

    @Override
    public void processPubAck(ClientSessionCtx clientSessionCtx, int packetId) {
        ClientInfo clientInfo = clientSessionCtx.getSessionInfo().getClientInfo();
        String clientId = clientInfo.getClientId();
        if (clientInfo.getType() == APPLICATION) {
            applicationPersistenceProcessor.processPubAck(clientId, packetId);
        } else if (clientInfo.getType() == DEVICE) {
            devicePersistenceProcessor.processPubAck(clientId, packetId);
        }
    }

    @Override
    public void processPubRec(ClientSessionCtx clientSessionCtx, int packetId) {
        ClientInfo clientInfo = clientSessionCtx.getSessionInfo().getClientInfo();
        String clientId = clientInfo.getClientId();
        if (clientInfo.getType() == APPLICATION) {
            applicationPersistenceProcessor.processPubRec(clientSessionCtx, packetId);
        } else if (clientInfo.getType() == DEVICE) {
            devicePersistenceProcessor.processPubRec(clientId, packetId);
        }
    }

    @Override
    public void processPubComp(ClientSessionCtx clientSessionCtx, int packetId) {
        ClientInfo clientInfo = clientSessionCtx.getSessionInfo().getClientInfo();
        String clientId = clientInfo.getClientId();
        if (clientInfo.getType() == APPLICATION) {
            applicationPersistenceProcessor.processPubComp(clientId, packetId);
        } else if (clientInfo.getType() == DEVICE) {
            devicePersistenceProcessor.processPubComp(clientId, packetId);
        }
    }

    private Map<SubscriptionType, List<Subscription>> collectAppSubscriptionsByType(List<Subscription> applicationSubscriptions) {
        return applicationSubscriptions
                .stream()
                .collect(Collectors.groupingBy(this::getSubscriptionType));
    }

    private SubscriptionType getSubscriptionType(Subscription subscription) {
        return StringUtils.isEmpty(subscription.getShareName()) ? SubscriptionType.COMMON : SubscriptionType.SHARED;
    }
}
