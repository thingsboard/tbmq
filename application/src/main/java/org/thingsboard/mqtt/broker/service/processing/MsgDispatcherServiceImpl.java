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
package org.thingsboard.mqtt.broker.service.processing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.SubscriptionService;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.MqttQoS;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.stats.MessagesStats;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos.PublishMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.processing.data.MsgSubscriptions;
import org.thingsboard.mqtt.broker.service.processing.data.PersistentMsgSubscriptions;
import org.thingsboard.mqtt.broker.service.processing.downlink.DownLinkProxy;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.stats.timer.PublishMsgProcessingTimerStats;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscription;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.service.subscription.SubscriptionOptions;
import org.thingsboard.mqtt.broker.service.subscription.ValueWithTopicFilter;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscription;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionCacheService;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionProcessingStrategy;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionProcessingStrategyFactory;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptions;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;
import org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MsgDispatcherServiceImpl implements MsgDispatcherService {

    private final SubscriptionService subscriptionService;
    private final StatsManager statsManager;
    private final MsgPersistenceManager msgPersistenceManager;
    private final ClientSessionCache clientSessionCache;
    private final DownLinkProxy downLinkProxy;
    private final ClientLogger clientLogger;
    private final PublishMsgQueuePublisher publishMsgQueuePublisher;
    private final SharedSubscriptionProcessingStrategyFactory sharedSubscriptionProcessingStrategyFactory;
    private final SharedSubscriptionCacheService sharedSubscriptionCacheService;

    private MessagesStats producerStats;
    private PublishMsgProcessingTimerStats publishMsgProcessingTimerStats;

    @PostConstruct
    public void init() {
        this.producerStats = statsManager.createMsgDispatcherPublishStats();
        this.publishMsgProcessingTimerStats = statsManager.getPublishMsgProcessingTimerStats();
    }

    @Override
    public void persistPublishMsg(SessionInfo sessionInfo, PublishMsg publishMsg, TbQueueCallback callback) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Persisting publish msg [topic:[{}], qos:[{}]].",
                    sessionInfo.getClientInfo().getClientId(), publishMsg.getTopicName(), publishMsg.getQosLevel());
        }
        PublishMsgProto publishMsgProto = ProtoConverter.convertToPublishProtoMessage(sessionInfo, publishMsg);
        producerStats.incrementTotal();
        callback = statsManager.wrapTbQueueCallback(callback, producerStats);
        publishMsgQueuePublisher.sendMsg(publishMsgProto, callback);
    }

    @Override
    public void processPublishMsg(PublishMsgProto publishMsgProto, PublishMsgCallback callback) {
        String senderClientId = ProtoConverter.getClientId(publishMsgProto);

        clientLogger.logEvent(senderClientId, this.getClass(), "Start msg processing");

        MsgSubscriptions msgSubscriptions = getAllSubscriptionsForPubMsg(publishMsgProto, senderClientId);
        if (msgSubscriptions == null) {
            callback.onSuccess();
            return;
        }

        clientLogger.logEvent(senderClientId, this.getClass(), "Found msg subscribers");

        PersistentMsgSubscriptions persistentMsgSubscriptions = processBasicAndCollectPersistentSubscriptions(msgSubscriptions, publishMsgProto);

        if (persistentMsgSubscriptions.isNotEmpty()) {
            processPersistentSubscriptions(publishMsgProto, persistentMsgSubscriptions, callback);
        } else {
            callback.onSuccess();
        }

        clientLogger.logEvent(senderClientId, this.getClass(), "Finished msg processing");
    }

    private void processPersistentSubscriptions(PublishMsgProto publishMsgProto, PersistentMsgSubscriptions persistentSubscriptions, PublishMsgCallback callback) {
        long persistentMessagesProcessingStartTime = System.nanoTime();
        msgPersistenceManager.processPublish(publishMsgProto, persistentSubscriptions, callback);
        publishMsgProcessingTimerStats.logPersistentMessagesProcessing(System.nanoTime() - persistentMessagesProcessingStartTime, TimeUnit.NANOSECONDS);
    }

    PersistentMsgSubscriptions processBasicAndCollectPersistentSubscriptions(MsgSubscriptions msgSubscriptions,
                                                                             PublishMsgProto publishMsgProto) {
        List<Subscription> applicationSubscriptions = null;
        List<Subscription> deviceSubscriptions = null;
        long notPersistentMsgProcessingStartTime = System.nanoTime();

        if (!CollectionUtils.isEmpty(msgSubscriptions.getCommonSubscriptions())) {
            applicationSubscriptions = new ArrayList<>();
            deviceSubscriptions = new ArrayList<>();
            processSubscriptions(msgSubscriptions.getCommonSubscriptions(), publishMsgProto,
                    applicationSubscriptions, deviceSubscriptions);
        }

        if (!CollectionUtils.isEmpty(msgSubscriptions.getTargetDeviceSharedSubscriptions())) {
            applicationSubscriptions = initSubscriptionListIfNull(applicationSubscriptions);
            deviceSubscriptions = initSubscriptionListIfNull(deviceSubscriptions);
            processSubscriptions(msgSubscriptions.getTargetDeviceSharedSubscriptions(), publishMsgProto,
                    applicationSubscriptions, deviceSubscriptions);
        }

        if (publishMsgProcessingTimerStats != null) {
            publishMsgProcessingTimerStats.logNotPersistentMessagesProcessing(System.nanoTime() - notPersistentMsgProcessingStartTime, TimeUnit.NANOSECONDS);
        }
        return new PersistentMsgSubscriptions(
                deviceSubscriptions,
                applicationSubscriptions,
                msgSubscriptions.getAllApplicationSharedSubscriptions()
        );
    }

    private void processSubscriptions(List<Subscription> subscriptions, PublishMsgProto publishMsgProto,
                                      List<Subscription> applicationSubscriptions, List<Subscription> deviceSubscriptions) {
        for (Subscription subscription : subscriptions) {
            if (needToBePersisted(publishMsgProto, subscription)) {
                if (ClientType.APPLICATION == subscription.getClientSession().getClientType()) {
                    applicationSubscriptions.add(subscription);
                } else {
                    deviceSubscriptions.add(subscription);
                }
            } else {
                sendToNode(createBasicPublishMsg(subscription, publishMsgProto), subscription);
            }
        }
    }

    private List<Subscription> initSubscriptionListIfNull(List<Subscription> subscriptions) {
        return subscriptions == null ? new ArrayList<>() : subscriptions;
    }

    private Set<TopicSharedSubscription> initTopicSharedSubscriptionSetIfNull(Set<TopicSharedSubscription> topicSharedSubscriptions) {
        return topicSharedSubscriptions == null ? new HashSet<>() : topicSharedSubscriptions;
    }

    MsgSubscriptions getAllSubscriptionsForPubMsg(PublishMsgProto publishMsgProto, String senderClientId) {
        List<ValueWithTopicFilter<ClientSubscription>> clientSubscriptions =
                subscriptionService.getSubscriptions(publishMsgProto.getTopicName());
        if (CollectionUtils.isEmpty(clientSubscriptions)) {
            return null;
        }

        Set<TopicSharedSubscription> topicSharedSubscriptions = null;
        List<ValueWithTopicFilter<ClientSubscription>> commonClientSubscriptions = new ArrayList<>();

        for (ValueWithTopicFilter<ClientSubscription> clientSubscription : clientSubscriptions) {
            var topicFilter = clientSubscription.getTopicFilter();
            var shareName = clientSubscription.getValue().getShareName();

            if (!StringUtils.isEmpty(shareName)) {
                topicSharedSubscriptions = initTopicSharedSubscriptionSetIfNull(topicSharedSubscriptions);
                topicSharedSubscriptions.add(new TopicSharedSubscription(topicFilter, shareName));
            } else {
                commonClientSubscriptions.add(clientSubscription);
            }
        }

        SharedSubscriptions sharedSubscriptions = sharedSubscriptionCacheService.get(topicSharedSubscriptions);

        return new MsgSubscriptions(
                collectCommonSubscriptions(commonClientSubscriptions, senderClientId),
                sharedSubscriptions == null ? null : sharedSubscriptions.getApplicationSubscriptions(),
                getTargetDeviceSharedSubscriptions(sharedSubscriptions, publishMsgProto.getQos())
        );
    }

    private List<Subscription> getTargetDeviceSharedSubscriptions(SharedSubscriptions sharedSubscriptions, int qos) {
        if (sharedSubscriptions == null || CollectionUtils.isEmpty(sharedSubscriptions.getDeviceSubscriptions())) {
            return null;
        }
        List<SharedSubscription> sharedSubscriptionList = toSharedSubscriptionList(sharedSubscriptions.getDeviceSubscriptions());
        return collectOneSubscriptionFromEveryDeviceSharedSubscription(sharedSubscriptionList, qos);
    }

    List<SharedSubscription> toSharedSubscriptionList(Set<Subscription> sharedSubscriptions) {
        return sharedSubscriptions.stream()
                .collect(Collectors.groupingBy(subscription ->
                        new TopicSharedSubscription(subscription.getTopicFilter(), subscription.getShareName(), subscription.getQos())))
                .entrySet().stream()
                .map(entry -> new SharedSubscription(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    private List<Subscription> collectCommonSubscriptions(
            List<ValueWithTopicFilter<ClientSubscription>> clientSubscriptionWithTopicFilterList, String senderClientId) {

        if (CollectionUtils.isEmpty(clientSubscriptionWithTopicFilterList)) {
            return null;
        }

        long startTime = System.nanoTime();
        List<Subscription> msgSubscriptions = collectSubscriptions(clientSubscriptionWithTopicFilterList, senderClientId);
        if (publishMsgProcessingTimerStats != null) {
            publishMsgProcessingTimerStats.logClientSessionsLookup(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
        }
        return msgSubscriptions;
    }

    private List<Subscription> collectOneSubscriptionFromEveryDeviceSharedSubscription(List<SharedSubscription> sharedSubscriptions, int qos) {
        List<Subscription> result = new ArrayList<>(sharedSubscriptions.size());
        for (SharedSubscription sharedSubscription : sharedSubscriptions) {
            result.add(getSubscription(sharedSubscription, qos));
        }
        return result;
    }

    private Subscription getSubscription(SharedSubscription sharedSubscription, int qos) {
        Subscription anyActive = findAnyConnectedSubscription(sharedSubscription.getSubscriptions());
        if (anyActive == null) {
            log.info("[{}] No active subscription found for shared subscription - all are persisted and disconnected", sharedSubscription.getTopicSharedSubscription());
            return createDummySubscription(sharedSubscription, qos);
        } else {
            SharedSubscriptionProcessingStrategy strategy = sharedSubscriptionProcessingStrategyFactory.newInstance();
            return strategy.analyze(sharedSubscription);
        }
    }

    Subscription findAnyConnectedSubscription(List<Subscription> subscriptions) {
        if (CollectionUtils.isEmpty(subscriptions)) {
            return null;
        }
        return subscriptions
                .stream()
                .filter(subscription -> subscription.getClientSession().isConnected())
                .findAny()
                .orElse(null);
    }

    private Subscription createDummySubscription(SharedSubscription sharedSubscription, int qos) {
        return new Subscription(
                sharedSubscription.getTopicSharedSubscription().getTopic(),
                qos,
                createDummyClientSession(sharedSubscription),
                sharedSubscription.getTopicSharedSubscription().getShareName(),
                SubscriptionOptions.newInstance()
        );
    }

    private ClientSession createDummyClientSession(SharedSubscription sharedSubscription) {
        return new ClientSession(false,
                SessionInfo.builder()
                        .clientInfo(ClientSessionInfoFactory.getClientInfo(sharedSubscription.getTopicSharedSubscription().getKey()))
                        .cleanStart(false)
                        .sessionExpiryInterval(1000)
                        .build());
    }

    List<Subscription> collectSubscriptions(
            List<ValueWithTopicFilter<ClientSubscription>> clientSubscriptionWithTopicFilterList, String senderClientId) {

        Collection<ValueWithTopicFilter<ClientSubscription>> filteredClientSubscriptions =
                filterClientSubscriptions(clientSubscriptionWithTopicFilterList, senderClientId);
        if (CollectionUtils.isEmpty(filteredClientSubscriptions)) {
            return null;
        }

        return filteredClientSubscriptions.stream()
                .map(this::convertToSubscription)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private Subscription convertToSubscription(ValueWithTopicFilter<ClientSubscription> clientSubscription) {
        String clientId = clientSubscription.getValue().getClientId();
        ClientSession clientSession = clientSessionCache.getClientSession(clientId);
        if (clientSession == null) {
            if (log.isDebugEnabled()) {
                log.debug("[{}] Client session not found for existent client subscription.", clientId);
            }
            return null;
        }
        return new Subscription(
                clientSubscription.getTopicFilter(),
                clientSubscription.getValue().getQosValue(),
                clientSession,
                clientSubscription.getValue().getShareName(),
                clientSubscription.getValue().getOptions());
    }

    Collection<ValueWithTopicFilter<ClientSubscription>> filterClientSubscriptions(
            List<ValueWithTopicFilter<ClientSubscription>> clientSubscriptionWithTopicFilterList, String senderClientId) {
        return clientSubscriptionWithTopicFilterList.stream()
                .filter(clientSubsWithTopicFilter -> !isNoLocalOptionMet(clientSubsWithTopicFilter, senderClientId))
                .collect(Collectors.toMap(
                        clientSubsWithTopicFilter -> clientSubsWithTopicFilter.getValue().getClientId(),
                        Function.identity(),
                        this::getSubscriptionWithHigherQos)
                )
                .values();
    }

    private boolean isNoLocalOptionMet(ValueWithTopicFilter<ClientSubscription> clientSubscriptionWithTopicFilter,
                                       String senderClientId) {
        return clientSubscriptionWithTopicFilter
                .getValue()
                .getOptions()
                .isNoLocalOptionMet(
                        clientSubscriptionWithTopicFilter.getValue().getClientId(),
                        senderClientId
                );
    }

    private ValueWithTopicFilter<ClientSubscription> getSubscriptionWithHigherQos(ValueWithTopicFilter<ClientSubscription> first,
                                                                                  ValueWithTopicFilter<ClientSubscription> second) {
        return first.getValue().getQosValue() > second.getValue().getQosValue() ? first : second;
    }

    private boolean needToBePersisted(PublishMsgProto publishMsgProto, Subscription subscription) {
        return getSessionInfo(subscription).isPersistent()
                && subscription.getQos() != MqttQoS.AT_MOST_ONCE.value()
                && publishMsgProto.getQos() != MqttQoS.AT_MOST_ONCE.value();
    }

    private void sendToNode(PublishMsgProto publishMsgProto, Subscription subscription) {
        var targetServiceId = getSessionInfo(subscription).getServiceId();
        var clientId = getSessionInfo(subscription).getClientInfo().getClientId();
        downLinkProxy.sendBasicMsg(targetServiceId, clientId, publishMsgProto);
    }

    private SessionInfo getSessionInfo(Subscription subscription) {
        return subscription.getClientSession().getSessionInfo();
    }

    private PublishMsgProto createBasicPublishMsg(Subscription subscription, PublishMsgProto publishMsgProto) {
        var minQos = Math.min(subscription.getQos(), publishMsgProto.getQos());
        var retain = subscription.getOptions().isRetain(publishMsgProto);
        return publishMsgProto.toBuilder()
                .setQos(minQos)
                .setRetain(retain)
                .build();
    }
}
