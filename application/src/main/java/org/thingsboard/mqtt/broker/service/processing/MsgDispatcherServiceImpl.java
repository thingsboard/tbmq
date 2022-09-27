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
import org.springframework.util.StringUtils;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.SubscriptionService;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.MqttQoS;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.stats.MessagesStats;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos.PublishMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.processing.downlink.DownLinkProxy;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.stats.timer.PublishMsgProcessingTimerStats;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscription;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.service.subscription.SubscriptionType;
import org.thingsboard.mqtt.broker.service.subscription.ValueWithTopicFilter;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscription;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionProcessingStrategy;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionProcessingStrategyFactory;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;
import org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private MessagesStats producerStats;
    private PublishMsgProcessingTimerStats publishMsgProcessingTimerStats;

    @PostConstruct
    public void init() {
        this.producerStats = statsManager.createMsgDispatcherPublishStats();
        this.publishMsgProcessingTimerStats = statsManager.getPublishMsgProcessingTimerStats();
    }

    @Override
    public void persistPublishMsg(SessionInfo sessionInfo, PublishMsg publishMsg, TbQueueCallback callback) {
        log.trace("[{}] Persisting publish msg [topic:[{}], qos:[{}]].", sessionInfo.getClientInfo().getClientId(), publishMsg.getTopicName(), publishMsg.getQosLevel());
        PublishMsgProto publishMsgProto = ProtoConverter.convertToPublishProtoMessage(sessionInfo, publishMsg);
        producerStats.incrementTotal();
        callback = statsManager.wrapTbQueueCallback(callback, producerStats);
        publishMsgQueuePublisher.sendMsg(publishMsgProto, callback);
    }

    @Override
    public void processPublishMsg(PublishMsgProto publishMsgProto, PublishMsgCallback callback) {
        String senderClientId = ProtoConverter.getClientId(publishMsgProto);

        clientLogger.logEvent(senderClientId, this.getClass(), "Start msg processing");

        List<Subscription> msgSubscriptions = getAllSubscriptionsForPubMsg(publishMsgProto);

        clientLogger.logEvent(senderClientId, this.getClass(), "Found msg subscribers");

        List<Subscription> persistentSubscriptions = processBasicAndCollectPersistentSubscription(msgSubscriptions, publishMsgProto);

        if (!persistentSubscriptions.isEmpty()) {
            processPersistentSubscriptions(publishMsgProto, persistentSubscriptions, callback);
        } else {
            callback.onSuccess();
        }

        clientLogger.logEvent(senderClientId, this.getClass(), "Finished msg processing");
    }

    private void processPersistentSubscriptions(PublishMsgProto publishMsgProto, List<Subscription> persistentSubscriptions, PublishMsgCallback callback) {
        long persistentMessagesProcessingStartTime = System.nanoTime();
        msgPersistenceManager.processPublish(publishMsgProto, persistentSubscriptions, callback);
        publishMsgProcessingTimerStats.logPersistentMessagesProcessing(System.nanoTime() - persistentMessagesProcessingStartTime, TimeUnit.NANOSECONDS);
    }

    private List<Subscription> processBasicAndCollectPersistentSubscription(List<Subscription> msgSubscriptions, PublishMsgProto publishMsgProto) {
        List<Subscription> persistentSubscriptions = new ArrayList<>();
        long notPersistentMessagesProcessingStartTime = System.nanoTime();
        for (Subscription msgSubscription : msgSubscriptions) {
            if (needToBePersisted(publishMsgProto, msgSubscription)) {
                persistentSubscriptions.add(msgSubscription);
            } else {
                sendToNode(createBasicPublishMsg(msgSubscription, publishMsgProto), msgSubscription);
            }
        }
        if (msgSubscriptions.size() != persistentSubscriptions.size()) {
            publishMsgProcessingTimerStats.logNotPersistentMessagesProcessing(System.nanoTime() - notPersistentMessagesProcessingStartTime, TimeUnit.NANOSECONDS);
        }
        return persistentSubscriptions;
    }

    private List<Subscription> getAllSubscriptionsForPubMsg(PublishMsgProto publishMsgProto) {
        List<ValueWithTopicFilter<ClientSubscription>> clientSubscriptions =
                subscriptionService.getSubscriptions(publishMsgProto.getTopicName());

        Map<SubscriptionType, List<Subscription>> subscriptionsByType = collectToSubscriptionsMapByType(clientSubscriptions);
        List<Subscription> sharedSubscriptions = getSubscriptionsFromMapByType(subscriptionsByType, SubscriptionType.SHARED);
        Map<ClientType, List<Subscription>> sharedSubscriptionsByClientType = collectToSubscriptionsByClientType(sharedSubscriptions);

        return collectAllSubscriptions(
                getSubscriptionsFromMapByType(subscriptionsByType, SubscriptionType.COMMON),
                getSharedSubscriptionsByType(sharedSubscriptionsByClientType, ClientType.APPLICATION),
                getDeviceSharedSubscriptions(sharedSubscriptionsByClientType, publishMsgProto.getQos()));
    }

    private List<Subscription> getDeviceSharedSubscriptions(Map<ClientType, List<Subscription>> sharedSubscriptionsByClientType, int qos) {
        List<Subscription> deviceSubscriptions = getSharedSubscriptionsByType(sharedSubscriptionsByClientType, ClientType.DEVICE);
        List<SharedSubscription> deviceSharedSubscriptions = toSharedSubscriptionList(deviceSubscriptions);
        return collectOneSubscriptionFromEveryDeviceSharedSubscription(deviceSharedSubscriptions, qos);
    }

    private List<Subscription> collectAllSubscriptions(List<Subscription> commonSubscriptions,
                                                       List<Subscription> appSharedSubscriptions,
                                                       List<Subscription> deviceSharedSubscription) {
        return Stream.of(commonSubscriptions, appSharedSubscriptions, deviceSharedSubscription)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private List<Subscription> getSharedSubscriptionsByType(Map<ClientType, List<Subscription>> sharedSubscriptionsByClientType,
                                                            ClientType type) {
        return sharedSubscriptionsByClientType.getOrDefault(type, Collections.emptyList());
    }

    private Map<ClientType, List<Subscription>> collectToSubscriptionsByClientType(List<Subscription> sharedSubscriptions) {
        return sharedSubscriptions
                .stream()
                .collect(Collectors.groupingBy(subscription -> getSessionInfo(subscription).getClientInfo().getType()));
    }

    private List<Subscription> getSubscriptionsFromMapByType(Map<SubscriptionType, List<Subscription>> msgSubscriptionsByType,
                                                             SubscriptionType type) {
        return msgSubscriptionsByType.getOrDefault(type, Collections.emptyList());
    }

    private Map<SubscriptionType, List<Subscription>> collectToSubscriptionsMapByType(
            List<ValueWithTopicFilter<ClientSubscription>> clientSubscriptionWithTopicFilters) {
        long startTime = System.nanoTime();
        Map<SubscriptionType, List<Subscription>> msgSubscriptionsByType = collectToSubscriptionsByType(clientSubscriptionWithTopicFilters);
        publishMsgProcessingTimerStats.logClientSessionsLookup(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
        return msgSubscriptionsByType;
    }

    private List<Subscription> collectOneSubscriptionFromEveryDeviceSharedSubscription(List<SharedSubscription> sharedSubscriptionList, int qos) {
        List<Subscription> result = new ArrayList<>(sharedSubscriptionList.size());
        for (SharedSubscription sharedSubscription : sharedSubscriptionList) {
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

    private Subscription createDummySubscription(SharedSubscription sharedSubscription, int qos) {
        return new Subscription(
                sharedSubscription.getTopicSharedSubscription().getTopic(),
                qos,
                createDummyClientSession(sharedSubscription),
                sharedSubscription.getTopicSharedSubscription().getShareName()
        );
    }

    private ClientSession createDummyClientSession(SharedSubscription sharedSubscription) {
        return new ClientSession(false,
                SessionInfo.builder()
                        .clientInfo(ClientSessionInfoFactory.getClientInfo(sharedSubscription.getTopicSharedSubscription().getKey()))
                        .persistent(true)
                        .build());
    }

    Subscription findAnyConnectedSubscription(List<Subscription> subscriptions) {
        return subscriptions
                .stream()
                .filter(subscription -> subscription.getClientSession().isConnected())
                .findAny()
                .orElse(null);
    }

    List<SharedSubscription> toSharedSubscriptionList(List<Subscription> sharedSubscriptions) {
        return sharedSubscriptions.stream()
                .collect(Collectors.groupingBy(subscription ->
                        new TopicSharedSubscription(subscription.getTopicFilter(), subscription.getShareName(), subscription.getMqttQoSValue())))
                .entrySet().stream()
                .map(entry -> new SharedSubscription(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    Map<SubscriptionType, List<Subscription>> collectToSubscriptionsByType(
            List<ValueWithTopicFilter<ClientSubscription>> clientSubscriptionWithTopicFilters) {

        List<ValueWithTopicFilter<ClientSubscription>> filteredClientSubscriptions =
                filterHighestQosClientSubscriptions(clientSubscriptionWithTopicFilters);

        return filteredClientSubscriptions.stream()
                .collect(Collectors.groupingBy(this::getSubscriptionType))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .map(this::convertToSubscription)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList())
                ));
    }

    private Subscription convertToSubscription(ValueWithTopicFilter<ClientSubscription> clientSubscription) {
        String clientId = clientSubscription.getValue().getClientId();
        ClientSession clientSession = clientSessionCache.getClientSession(clientId);
        if (clientSession == null) {
            log.debug("[{}] Client session not found for existent client subscription.", clientId);
            return null;
        }
        return new Subscription(clientSubscription.getTopicFilter(), clientSubscription.getValue().getQosValue(),
                clientSession, clientSubscription.getValue().getShareName());
    }

    List<ValueWithTopicFilter<ClientSubscription>> filterHighestQosClientSubscriptions(
            List<ValueWithTopicFilter<ClientSubscription>> clientSubscriptionWithTopicFilters) {

        Stream<ValueWithTopicFilter<ClientSubscription>> sharedSubscriptions = clientSubscriptionWithTopicFilters
                .stream()
                .filter(subs -> !StringUtils.isEmpty(subs.getValue().getShareName()));

        Stream<ValueWithTopicFilter<ClientSubscription>> commonSubscriptions = clientSubscriptionWithTopicFilters
                .stream()
                .filter(subs -> StringUtils.isEmpty(subs.getValue().getShareName()));

        return Stream.concat(
                        filterHighestQosClientSubscriptions(sharedSubscriptions),
                        filterHighestQosClientSubscriptions(commonSubscriptions))
                .collect(Collectors.toList());
    }

    Stream<ValueWithTopicFilter<ClientSubscription>> filterHighestQosClientSubscriptions(
            Stream<ValueWithTopicFilter<ClientSubscription>> stream) {
        return stream
                .collect(Collectors.toMap(
                        clientSubsWithTopicFilter -> clientSubsWithTopicFilter.getValue().getClientId(),
                        Function.identity(),
                        this::getSubscriptionWithHigherQos)
                )
                .values().stream();
    }

    private ValueWithTopicFilter<ClientSubscription> getSubscriptionWithHigherQos(ValueWithTopicFilter<ClientSubscription> first,
                                                                                  ValueWithTopicFilter<ClientSubscription> second) {
        return first.getValue().getQosValue() > second.getValue().getQosValue() ? first : second;
    }

    private boolean needToBePersisted(QueueProtos.PublishMsgProto publishMsgProto, Subscription subscription) {
        return getSessionInfo(subscription).isPersistent()
                && subscription.getMqttQoSValue() != MqttQoS.AT_MOST_ONCE.value()
                && publishMsgProto.getQos() != MqttQoS.AT_MOST_ONCE.value();
    }

    private void sendToNode(QueueProtos.PublishMsgProto publishMsgProto, Subscription subscription) {
        String targetServiceId = getSessionInfo(subscription).getServiceId();
        String clientId = getSessionInfo(subscription).getClientInfo().getClientId();
        downLinkProxy.sendBasicMsg(targetServiceId, clientId, publishMsgProto);
    }

    private QueueProtos.PublishMsgProto createBasicPublishMsg(Subscription clientSubscription, QueueProtos.PublishMsgProto publishMsgProto) {
        int minQoSValue = Math.min(clientSubscription.getMqttQoSValue(), publishMsgProto.getQos());
        return publishMsgProto.toBuilder()
                .setQos(minQoSValue)
                .build();
    }

    private SessionInfo getSessionInfo(Subscription subscription) {
        return subscription.getClientSession().getSessionInfo();
    }

    private SubscriptionType getSubscriptionType(ValueWithTopicFilter<ClientSubscription> clientSubscription) {
        return StringUtils.isEmpty(clientSubscription.getValue().getShareName()) ? SubscriptionType.COMMON : SubscriptionType.SHARED;
    }
}
