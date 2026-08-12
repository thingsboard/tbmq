/**
 * Copyright © 2016-2026 The Thingsboard Authors
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
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorStateInfo;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.util.BytesUtil;
import org.thingsboard.mqtt.broker.gen.integration.PublishIntegrationMsgProto;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgHeaders;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.util.IntegrationProtoConverter;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.service.limits.RateLimitService;
import org.thingsboard.mqtt.broker.service.limits.ThroughputQuotaService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.ApplicationMsgQueuePublisher;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.ApplicationPersistenceProcessor;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.DevicePersistenceProcessor;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.queue.DeviceMsgQueuePublisher;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.integration.IntegrationMsgQueuePublisher;
import org.thingsboard.mqtt.broker.service.processing.MultiplePublishMsgCallbackWrapper;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgCallback;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgWithId;
import org.thingsboard.mqtt.broker.service.processing.data.PersistentMsgSubscriptions;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.Collection;
import java.util.List;
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
    private final RateLimitService rateLimitService;
    private final ThroughputQuotaService throughputQuotaService;
    private final IntegrationMsgQueuePublisher integrationMsgQueuePublisher;
    private final TbMessageStatsReportClient tbMessageStatsReportClient;

    @Override
    public void processPublish(PublishMsgWithId publishMsgWithId, PersistentMsgSubscriptions persistentSubscriptions, PublishMsgCallback callback) {
        PublishMsgProto publishMsgProto = publishMsgWithId.getPublishMsgProto();

        List<Subscription> deviceSubscriptions = getSubscriptionsIfNotNull(persistentSubscriptions.getDeviceSubscriptions());
        List<Subscription> applicationSubscriptions = getSubscriptionsIfNotNull(persistentSubscriptions.getApplicationSubscriptions());
        Set<String> sharedTopics = getUniqueSharedTopics(persistentSubscriptions.getAllApplicationSharedSubscriptions());
        List<Subscription> integrationSubscriptions = getSubscriptionsIfNotNull(persistentSubscriptions.getIntegrationSubscriptions());

        int callbackCount = getCallbackCount(deviceSubscriptions, applicationSubscriptions, sharedTopics, integrationSubscriptions);
        if (callbackCount == 0) {
            callback.onSuccess();
            return;
        }
        PublishMsgCallback callbackWrapper = new MultiplePublishMsgCallbackWrapper(callbackCount, callback);

        String senderClientId = ProtoConverter.getClientId(publishMsgProto);
        clientLogger.logEvent(senderClientId, this.getClass(), "Before msg persistence");

        if (deviceSubscriptions != null) {
            // the narrower device-persisted-msgs limit truncates first; the total quota then pays only for what
            // actually gets stored, so it never bills for messages that limit already dropped
            List<Subscription> admittedDeviceSubscriptions = rateLimitService.isDevicePersistedMsgsLimitEnabled()
                    ? applyDevicePersistedMsgsLimit(deviceSubscriptions, callbackWrapper)
                    : deviceSubscriptions;
            processDeviceSubscriptionsWithThroughputQuota(admittedDeviceSubscriptions, publishMsgWithId, callbackWrapper);
        }
        if (applicationSubscriptions != null) {
            processApplicationSubscriptionsWithThroughputQuota(applicationSubscriptions, publishMsgWithId, callbackWrapper);
        }
        if (sharedTopics != null) {
            processSharedTopicsWithThroughputQuota(sharedTopics, publishMsgWithId, callbackWrapper);
        }
        if (integrationSubscriptions != null) {
            processIntegrationSubscriptionsWithThroughputQuota(integrationSubscriptions, publishMsgWithId, callbackWrapper);
        }

        clientLogger.logEvent(senderClientId, this.getClass(), "After msg persistence");
    }

    List<Subscription> applyDevicePersistedMsgsLimit(List<Subscription> deviceSubscriptions,
                                                     PublishMsgCallback callbackWrapper) {
        int totalCount = deviceSubscriptions.size();
        int availableTokens = (int) rateLimitService.tryConsumeDevicePersistedMsgs(totalCount);
        if (availableTokens >= totalCount) {
            return deviceSubscriptions;
        }
        int dropped = totalCount - Math.max(availableTokens, 0);
        log.trace("Hitting device persisted messages rate limits. Dropping {} messages", dropped);
        tbMessageStatsReportClient.reportDroppedMsgs(dropped);
        callbackWrapper.onBatchSuccess(dropped);
        // a view over the caller's list, not a copy: safe because nothing downstream mutates either one
        return availableTokens <= 0 ? List.of() : deviceSubscriptions.subList(0, availableTokens);
    }

    void processDeviceSubscriptionsWithThroughputQuota(List<Subscription> deviceSubscriptions,
                                                       PublishMsgWithId publishMsgWithId,
                                                       PublishMsgCallback callbackWrapper) {
        int totalCount = deviceSubscriptions.size();
        if (totalCount == 0) {
            return; // the device limit took everything: nothing left to charge for
        }
        int granted = throughputQuotaService.tryConsumeOutgoingWaiting(totalCount);
        for (int i = 0; i < granted; i++) {
            sendDeviceMsg(deviceSubscriptions.get(i), publishMsgWithId, callbackWrapper);
        }
        settleQuotaDropped(totalCount - granted, callbackWrapper);
    }

    void processApplicationSubscriptionsWithThroughputQuota(List<Subscription> applicationSubscriptions,
                                                            PublishMsgWithId publishMsgWithId,
                                                            PublishMsgCallback callbackWrapper) {
        int totalCount = applicationSubscriptions.size();
        int granted = throughputQuotaService.tryConsumeOutgoingWaiting(totalCount);
        for (int i = 0; i < granted; i++) {
            sendApplicationMsg(applicationSubscriptions.get(i), publishMsgWithId, callbackWrapper);
        }
        settleQuotaDropped(totalCount - granted, callbackWrapper);
    }

    void processSharedTopicsWithThroughputQuota(Set<String> sharedTopics,
                                                PublishMsgWithId publishMsgWithId,
                                                PublishMsgCallback callbackWrapper) {
        PublishMsgProto publishMsgProto = publishMsgWithId.getPublishMsgProto();
        int totalCount = sharedTopics.size();
        int granted = throughputQuotaService.tryConsumeOutgoingWaiting(totalCount);
        int sent = 0;
        for (String sharedTopic : sharedTopics) {
            if (sent == granted) {
                break;
            }
            applicationMsgQueuePublisher.sendMsgToSharedTopic(
                    sharedTopic,
                    new TbProtoQueueMsg<>(ProtoConverter.createReceiverPublishMsg(publishMsgProto), getAppMsgHeaders(publishMsgWithId)),
                    callbackWrapper);
            sent++;
        }
        settleQuotaDropped(totalCount - granted, callbackWrapper);
    }

    // callbackCount was computed over the FULL fan-out before the quota trimmed it, so every subscription the
    // quota refused still owes the wrapper a completion. Skip this and the publish is neither delivered nor
    // acknowledged: the wrapper never counts down to zero and the offset is never committed.
    private void settleQuotaDropped(int dropped, PublishMsgCallback callbackWrapper) {
        if (dropped <= 0) {
            return;
        }
        log.trace("Total throughput quota exceeded. Dropping {} persisted messages", dropped);
        tbMessageStatsReportClient.reportDroppedMsgs(dropped);
        callbackWrapper.onBatchSuccess(dropped);
    }

    void processIntegrationSubscriptionsWithThroughputQuota(List<Subscription> integrationSubscriptions,
                                                            PublishMsgWithId publishMsgWithId,
                                                            PublishMsgCallback callbackWrapper) {
        int totalCount = integrationSubscriptions.size();
        int granted = throughputQuotaService.tryConsumeOutgoingWaiting(totalCount);
        for (int i = 0; i < granted; i++) {
            sendIntegrationMsg(integrationSubscriptions.get(i), publishMsgWithId, callbackWrapper);
        }
        settleQuotaDropped(totalCount - granted, callbackWrapper);
    }

    private void sendDeviceMsg(Subscription deviceSubscription, PublishMsgWithId publishMsgWithId, PublishMsgCallback callbackWrapper) {
        String clientId = deviceSubscription.getClientId();
        PublishMsgProto publishMsg = ProtoConverter.createReceiverPublishMsg(deviceSubscription, publishMsgWithId.getPublishMsgProto());
        deviceMsgQueuePublisher.sendMsg(
                clientId,
                new TbProtoQueueMsg<>(clientId, publishMsg, publishMsgWithId.getHeaders().copy()),
                callbackWrapper);
    }

    private void sendApplicationMsg(Subscription applicationSubscription, PublishMsgWithId publishMsgWithId, PublishMsgCallback callbackWrapper) {
        PublishMsgProto publishMsg = ProtoConverter.createReceiverPublishMsg(applicationSubscription, publishMsgWithId.getPublishMsgProto());
        applicationMsgQueuePublisher.sendMsg(
                applicationSubscription.getClientId(),
                new TbProtoQueueMsg<>(publishMsg.getTopicName(), publishMsg, getAppMsgHeaders(publishMsgWithId)),
                callbackWrapper);
    }

    private void sendIntegrationMsg(Subscription integrationSubscription, PublishMsgWithId publishMsgWithId, PublishMsgCallback callbackWrapper) {
        PublishIntegrationMsgProto publishMsg = IntegrationProtoConverter.toProto(publishMsgWithId.getPublishMsgProto(), integrationSubscription.getServiceId());
        integrationMsgQueuePublisher.sendMsg(
                integrationSubscription.getClientId(),
                new TbProtoQueueMsg<>(publishMsg.getPublishMsgProto().getTopicName(), publishMsg),
                callbackWrapper
        );
    }

    private TbQueueMsgHeaders getAppMsgHeaders(PublishMsgWithId publishMsgWithId) {
        TbQueueMsgHeaders headers = publishMsgWithId.getHeaders().copy();
        headers.put(BrokerConstants.CREATED_TIME, BytesUtil.longToBytes(System.currentTimeMillis()));
        return headers;
    }

    private List<Subscription> getSubscriptionsIfNotNull(List<Subscription> persistentSubscriptions) {
        return CollectionUtils.isEmpty(persistentSubscriptions) ? null : persistentSubscriptions;
    }

    private Set<String> getUniqueSharedTopics(Set<Subscription> allAppSharedSubscriptions) {
        if (CollectionUtils.isEmpty(allAppSharedSubscriptions)) {
            return null;
        }
        return allAppSharedSubscriptions
                .stream()
                .collect(Collectors.groupingBy(Subscription::getTopicFilter))
                .keySet();
    }

    private int getCallbackCount(List<Subscription> deviceSubscriptions,
                                 List<Subscription> applicationSubscriptions,
                                 Set<String> sharedTopics,
                                 List<Subscription> integrationSubscriptions) {
        return getCollectionSize(deviceSubscriptions) +
                getCollectionSize(applicationSubscriptions) +
                getCollectionSize(sharedTopics) +
                getCollectionSize(integrationSubscriptions);
    }

    private <T> int getCollectionSize(Collection<T> collection) {
        return collection == null ? 0 : collection.size();
    }

    @Override
    public void startProcessingPersistedMessages(ClientActorStateInfo actorState) {
        ClientSessionCtx clientSessionCtx = actorState.getCurrentSessionCtx();
        genericClientSessionCtxManager.resendPersistedPubRelMessages(clientSessionCtx);

        ClientType clientType = clientSessionCtx.getClientType();
        if (clientType == APPLICATION) {
            applicationPersistenceProcessor.startProcessingPersistedMessages(actorState);
        } else if (clientType == DEVICE) {
            devicePersistenceProcessor.startProcessingPersistedMessages(clientSessionCtx);
        }
    }

    @Override
    public void startProcessingSharedSubscriptions(ClientSessionCtx clientSessionCtx, Set<TopicSharedSubscription> subscriptions) {
        ClientType clientType = clientSessionCtx.getClientType();
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
        }
    }

    @Override
    public void saveAwaitingQoS2Packets(ClientSessionCtx clientSessionCtx) {
        genericClientSessionCtxManager.saveAwaitingQoS2Packets(clientSessionCtx);
    }

    @Override
    public void clearPersistedMessages(String clientId, ClientType type) {
        genericClientSessionCtxManager.clearAwaitingQoS2Packets(clientId);
        if (type == APPLICATION) {
            applicationPersistenceProcessor.clearPersistedMessages(clientId);
        } else if (type == DEVICE) {
            devicePersistenceProcessor.clearPersistedMessages(clientId);
        }
    }

    @Override
    public void processPubAck(ClientSessionCtx clientSessionCtx, int packetId) {
        if (clientSessionCtx.getClientType() == APPLICATION) {
            applicationPersistenceProcessor.processPubAck(clientSessionCtx.getClientId(), packetId);
        } else if (clientSessionCtx.getClientType() == DEVICE) {
            devicePersistenceProcessor.processPubAck(clientSessionCtx.getClientId(), packetId);
        }
    }

    @Override
    public void processPubRec(ClientSessionCtx clientSessionCtx, int packetId) {
        if (clientSessionCtx.getClientType() == APPLICATION) {
            applicationPersistenceProcessor.processPubRec(clientSessionCtx, packetId);
        } else if (clientSessionCtx.getClientType() == DEVICE) {
            devicePersistenceProcessor.processPubRec(clientSessionCtx.getClientId(), packetId);
        }
    }

    @Override
    public void processPubRecNoPubRelDelivery(ClientSessionCtx clientSessionCtx, int packetId) {
        if (clientSessionCtx.getClientType() == APPLICATION) {
            applicationPersistenceProcessor.processPubRecNoPubRelDelivery(clientSessionCtx, packetId);
        } else if (clientSessionCtx.getClientType() == DEVICE) {
            devicePersistenceProcessor.processPubRecNoPubRelDelivery(clientSessionCtx.getClientId(), packetId);
        }
    }

    @Override
    public void processPubComp(ClientSessionCtx clientSessionCtx, int packetId) {
        if (clientSessionCtx.getClientType() == APPLICATION) {
            applicationPersistenceProcessor.processPubComp(clientSessionCtx.getClientId(), packetId);
        } else if (clientSessionCtx.getClientType() == DEVICE) {
            devicePersistenceProcessor.processPubComp(clientSessionCtx.getClientId(), packetId);
        }
    }
}
