/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.SubscriptionService;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.MqttQoS;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.StringUtils;
import org.thingsboard.mqtt.broker.common.stats.MessagesStats;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos.PublishMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.thingsboard.mqtt.broker.common.util.BrokerConstants.DROPPED_MSGS;
import static org.thingsboard.mqtt.broker.common.util.BrokerConstants.INCOMING_MSGS;

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
    private final TbMessageStatsReportClient tbMessageStatsReportClient;

    private MessagesStats producerStats;
    private PublishMsgProcessingTimerStats publishMsgProcessingTimerStats;

    @Value("${mqtt.msg-subscriptions-parallel-processing:false}")
    private boolean processSubscriptionsInParallel;

    @PostConstruct
    public void init() {
        this.producerStats = statsManager.createMsgDispatcherPublishStats();
        this.publishMsgProcessingTimerStats = statsManager.getPublishMsgProcessingTimerStats();
    }

    @Override
    public void persistPublishMsg(SessionInfo sessionInfo, PublishMsg publishMsg, TbQueueCallback callback) {
        PublishMsgProto publishMsgProto = ProtoConverter.convertToPublishProtoMessage(sessionInfo, publishMsg);
        producerStats.incrementTotal();
        tbMessageStatsReportClient.reportStats(INCOMING_MSGS);
        callback = statsManager.wrapTbQueueCallback(callback, producerStats);
        publishMsgQueuePublisher.sendMsg(publishMsgProto, callback);
    }

    @Override
    public void processPublishMsg(PublishMsgProto publishMsgProto, PublishMsgCallback callback) {
        String senderClientId = ProtoConverter.getClientId(publishMsgProto);

        clientLogger.logEvent(senderClientId, this.getClass(), "Start msg processing");

        MsgSubscriptions msgSubscriptions = getAllSubscriptionsForPubMsg(publishMsgProto, senderClientId);
        if (msgSubscriptions == null) {
            tbMessageStatsReportClient.reportStats(DROPPED_MSGS);
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
        long startTime = System.nanoTime();
        msgPersistenceManager.processPublish(publishMsgProto, persistentSubscriptions, callback);
        publishMsgProcessingTimerStats.logPersistentMessagesProcessing(startTime, TimeUnit.NANOSECONDS);
    }

    PersistentMsgSubscriptions processBasicAndCollectPersistentSubscriptions(MsgSubscriptions msgSubscriptions,
                                                                             PublishMsgProto publishMsgProto) {
        List<Subscription> applicationSubscriptions = null;
        List<Subscription> deviceSubscriptions = null;
        long startTime = System.nanoTime();

        if (!CollectionUtils.isEmpty(msgSubscriptions.getCommonSubscriptions())) {
            int commonSubsSize = msgSubscriptions.getCommonSubscriptions().size();
            applicationSubscriptions = initArrayList(commonSubsSize);
            deviceSubscriptions = initArrayList(commonSubsSize);
            processSubscriptions(msgSubscriptions.getCommonSubscriptions(), publishMsgProto,
                    applicationSubscriptions, deviceSubscriptions);
        }

        if (!CollectionUtils.isEmpty(msgSubscriptions.getTargetDeviceSharedSubscriptions())) {
            int targetDeviceSharedSubsSize = msgSubscriptions.getTargetDeviceSharedSubscriptions().size();
            applicationSubscriptions = initSubscriptionListIfNull(applicationSubscriptions, targetDeviceSharedSubsSize);
            deviceSubscriptions = initSubscriptionListIfNull(deviceSubscriptions, targetDeviceSharedSubsSize);
            processSubscriptions(msgSubscriptions.getTargetDeviceSharedSubscriptions(), publishMsgProto,
                    applicationSubscriptions, deviceSubscriptions);
        }

        if (publishMsgProcessingTimerStats != null) {
            publishMsgProcessingTimerStats.logNotPersistentMessagesProcessing(startTime, TimeUnit.NANOSECONDS);
        }
        return new PersistentMsgSubscriptions(
                deviceSubscriptions,
                applicationSubscriptions,
                msgSubscriptions.getAllApplicationSharedSubscriptions()
        );
    }

    private void processSubscriptions(List<Subscription> subscriptions, PublishMsgProto publishMsgProto,
                                      List<Subscription> applicationSubscriptions, List<Subscription> deviceSubscriptions) {
        boolean nonPersistentByPubQos = publishMsgProto.getQos() == MqttQoS.AT_MOST_ONCE.value();
        if (nonPersistentByPubQos) {
            if (subscriptions.size() == 1) {
                Subscription subscription = subscriptions.get(0);
                deliver(publishMsgProto, subscription);
                return;
            }
            if (processSubscriptionsInParallel) {
                subscriptions
                        .parallelStream()
                        .forEach(subscription -> deliver(publishMsgProto, subscription));
            } else {
                for (Subscription subscription : subscriptions) {
                    deliver(publishMsgProto, subscription);
                }
            }
        } else {
            if (subscriptions.size() == 1) {
                Subscription subscription = subscriptions.get(0);
                processSubscription(subscription, publishMsgProto, applicationSubscriptions, deviceSubscriptions);
                return;
            }
            if (processSubscriptionsInParallel) {
                subscriptions
                        .parallelStream()
                        .forEach(subscription -> processSubscription(subscription, publishMsgProto, applicationSubscriptions, deviceSubscriptions));
            } else {
                for (Subscription subscription : subscriptions) {
                    processSubscription(subscription, publishMsgProto, applicationSubscriptions, deviceSubscriptions);
                }
            }
        }
    }

    private void processSubscription(Subscription subscription, PublishMsgProto publishMsgProto,
                                     List<Subscription> applicationSubscriptions, List<Subscription> deviceSubscriptions) {
        if (isPersistentBySubInfo(subscription)) {
            if (ClientType.APPLICATION == subscription.getClientSessionInfo().getType()) {
                applicationSubscriptions.add(subscription);
            } else {
                deviceSubscriptions.add(subscription);
            }
        } else {
            deliver(publishMsgProto, subscription);
        }
    }

    private List<Subscription> initSubscriptionListIfNull(List<Subscription> subscriptions, int size) {
        return subscriptions == null ? initArrayList(size) : subscriptions;
    }

    private List<Subscription> initArrayList(int size) {
        return processSubscriptionsInParallel ? Collections.synchronizedList(new ArrayList<>(size)) : new ArrayList<>(size);
    }

    private Set<TopicSharedSubscription> initTopicSharedSubscriptionSetIfNull(Set<TopicSharedSubscription> topicSharedSubscriptions) {
        return topicSharedSubscriptions == null ? new HashSet<>() : topicSharedSubscriptions;
    }

    MsgSubscriptions getAllSubscriptionsForPubMsg(PublishMsgProto publishMsgProto, String senderClientId) {
        List<ValueWithTopicFilter<ClientSubscription>> clientSubscriptions =
                subscriptionService.getSubscriptions(publishMsgProto.getTopicName());
        int clientSubscriptionsSize = clientSubscriptions.size();
        if (clientSubscriptionsSize == 0) {
            return null;
        }

        Set<TopicSharedSubscription> topicSharedSubscriptions = null;
        List<ValueWithTopicFilter<ClientSubscription>> commonClientSubscriptions = new ArrayList<>(clientSubscriptionsSize);

        if (clientSubscriptionsSize == 1) {
            ValueWithTopicFilter<ClientSubscription> clientSubscription = clientSubscriptions.get(0);
            topicSharedSubscriptions = addSubscription(clientSubscription, commonClientSubscriptions, topicSharedSubscriptions);
        } else {
            for (ValueWithTopicFilter<ClientSubscription> clientSubscription : clientSubscriptions) {
                topicSharedSubscriptions = addSubscription(clientSubscription, commonClientSubscriptions, topicSharedSubscriptions);
            }
        }

        SharedSubscriptions sharedSubscriptions = sharedSubscriptionCacheService.get(topicSharedSubscriptions);

        return new MsgSubscriptions(
                collectCommonSubscriptions(commonClientSubscriptions, senderClientId),
                sharedSubscriptions == null ? null : sharedSubscriptions.getApplicationSubscriptions(),
                getTargetDeviceSharedSubscriptions(sharedSubscriptions, publishMsgProto.getQos())
        );
    }

    private Set<TopicSharedSubscription> addSubscription(ValueWithTopicFilter<ClientSubscription> clientSubscription,
                                                         List<ValueWithTopicFilter<ClientSubscription>> commonClientSubscriptions,
                                                         Set<TopicSharedSubscription> topicSharedSubscriptions) {
        var topicFilter = clientSubscription.getTopicFilter();
        var shareName = clientSubscription.getValue().getShareName();

        if (!StringUtils.isEmpty(shareName)) {
            topicSharedSubscriptions = initTopicSharedSubscriptionSetIfNull(topicSharedSubscriptions);
            topicSharedSubscriptions.add(new TopicSharedSubscription(topicFilter, shareName));
        } else {
            commonClientSubscriptions.add(clientSubscription);
        }
        return topicSharedSubscriptions;
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

        if (clientSubscriptionWithTopicFilterList.size() == 0) {
            return null;
        }

        long startTime = System.nanoTime();
        List<Subscription> msgSubscriptions = collectSubscriptions(clientSubscriptionWithTopicFilterList, senderClientId);
        if (publishMsgProcessingTimerStats != null) {
            publishMsgProcessingTimerStats.logClientSessionsLookup(startTime, TimeUnit.NANOSECONDS);
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
                .filter(subscription -> subscription.getClientSessionInfo().isConnected())
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

    private ClientSessionInfo createDummyClientSession(SharedSubscription sharedSubscription) {
        ClientInfo clientInfo = ClientSessionInfoFactory.getClientInfo(sharedSubscription.getTopicSharedSubscription().getKey());
        return ClientSessionInfo.builder()
                .connected(false)
                .clientId(clientInfo.getClientId())
                .type(clientInfo.getType())
                .clientIpAdr(clientInfo.getClientIpAdr())
                .cleanStart(false)
                .sessionExpiryInterval(1000)
                .build();
    }

    List<Subscription> collectSubscriptions(
            List<ValueWithTopicFilter<ClientSubscription>> clientSubscriptionWithTopicFilterList, String senderClientId) {

        Collection<ValueWithTopicFilter<ClientSubscription>> filteredClientSubscriptions =
                filterClientSubscriptions(clientSubscriptionWithTopicFilterList, senderClientId);
        if (CollectionUtils.isEmpty(filteredClientSubscriptions)) {
            return null;
        }

        return getSubscriptions(filteredClientSubscriptions);
    }

    private List<Subscription> getSubscriptions(Collection<ValueWithTopicFilter<ClientSubscription>> filteredClientSubscriptions) {
        List<Subscription> subscriptions = new ArrayList<>(filteredClientSubscriptions.size());
        for (var filteredClientSubscription : filteredClientSubscriptions) {
            Subscription subscription = convertToSubscription(filteredClientSubscription);
            if (subscription != null) {
                subscriptions.add(subscription);
            }
        }
        return subscriptions;
    }

    private Subscription convertToSubscription(ValueWithTopicFilter<ClientSubscription> clientSubscription) {
        String clientId = clientSubscription.getValue().getClientId();
        ClientSessionInfo clientSessionInfo = clientSessionCache.getClientSessionInfo(clientId);
        if (clientSessionInfo == null) {
            if (log.isDebugEnabled()) {
                log.debug("[{}] Client session not found for existent client subscription.", clientId);
            }
            return null;
        }
        return new Subscription(
                clientSubscription.getTopicFilter(),
                clientSubscription.getValue().getQosValue(),
                clientSessionInfo,
                clientSubscription.getValue().getShareName(),
                clientSubscription.getValue().getOptions());
    }

    Collection<ValueWithTopicFilter<ClientSubscription>> filterClientSubscriptions(
            List<ValueWithTopicFilter<ClientSubscription>> clientSubscriptionWithTopicFilterList, String senderClientId) {
        if (clientSubscriptionWithTopicFilterList.size() == 1) {
            var clientSubsWithTopicFilter = clientSubscriptionWithTopicFilterList.get(0);
            boolean noLocalOptionMet = isNoLocalOptionMet(clientSubsWithTopicFilter, senderClientId);
            if (noLocalOptionMet) {
                return null;
            }
            return clientSubscriptionWithTopicFilterList;
        }

        Map<String, ValueWithTopicFilter<ClientSubscription>> map = new HashMap<>();

        for (var clientSubsWithTopicFilter : clientSubscriptionWithTopicFilterList) {
            boolean noLocalOptionMet = isNoLocalOptionMet(clientSubsWithTopicFilter, senderClientId);
            if (noLocalOptionMet) {
                continue;
            }

            var clientId = clientSubsWithTopicFilter.getValue().getClientId();
            var value = map.get(clientId);
            if (value != null) {
                map.put(clientId, getSubscriptionWithHigherQos(value, clientSubsWithTopicFilter));
            } else {
                map.put(clientId, clientSubsWithTopicFilter);
            }
        }
        return map.values();
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

    ValueWithTopicFilter<ClientSubscription> getSubscriptionWithHigherQos(ValueWithTopicFilter<ClientSubscription> first,
                                                                          ValueWithTopicFilter<ClientSubscription> second) {
        return first.getValue().getQosValue() > second.getValue().getQosValue() ? first : second;
    }

    private boolean isPersistentBySubInfo(Subscription subscription) {
        return subscription.getClientSessionInfo().isPersistent() && subscription.getQos() != MqttQoS.AT_MOST_ONCE.value();
    }

    private void sendToNode(PublishMsgProto publishMsgProto, Subscription subscription) {
        var targetServiceId = subscription.getClientSessionInfo().getServiceId();
        var clientId = subscription.getClientSessionInfo().getClientId();
        downLinkProxy.sendBasicMsg(targetServiceId, clientId, publishMsgProto);
    }

    private PublishMsgProto createBasicPublishMsg(Subscription subscription, PublishMsgProto publishMsgProto) {
        var minQos = Math.min(subscription.getQos(), publishMsgProto.getQos());
        var retain = subscription.getOptions().isRetain(publishMsgProto);
        return publishMsgProto.toBuilder()
                .setQos(minQos)
                .setRetain(retain)
                .build();
    }

    private void deliver(PublishMsgProto publishMsgProto, Subscription subscription) {
        PublishMsgProto publishMsg = createBasicPublishMsg(subscription, publishMsgProto);
        sendToNode(publishMsg, subscription);
    }
}
