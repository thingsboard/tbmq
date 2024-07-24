/**
 * Copyright © 2016-2024 The Thingsboard Authors
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

import com.google.common.collect.Maps;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.SubscriptionService;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.MqttQoS;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.StringUtils;
import org.thingsboard.mqtt.broker.common.data.subscription.SubscriptionOptions;
import org.thingsboard.mqtt.broker.common.stats.MessagesStats;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos.PublishMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.common.DefaultTbQueueMsgHeaders;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.service.limits.RateLimitService;
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
import org.thingsboard.mqtt.broker.service.subscription.ValueWithTopicFilter;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscription;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionCacheService;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionProcessingStrategy;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionProcessingStrategyFactory;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptions;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;
import org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory;
import org.thingsboard.mqtt.broker.util.MqttPropertiesUtil;

import java.util.ArrayList;
import java.util.Collections;
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
    private final ServiceInfoProvider serviceInfoProvider;
    private final RateLimitService rateLimitService;

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
        PublishMsgProto publishMsgProto = ProtoConverter.convertToPublishMsgProto(sessionInfo, publishMsg);
        producerStats.incrementTotal();
        tbMessageStatsReportClient.reportStats(INCOMING_MSGS);
        callback = statsManager.wrapTbQueueCallback(callback, producerStats);

        DefaultTbQueueMsgHeaders headers = createHeaders(publishMsg);
        TbProtoQueueMsg<PublishMsgProto> msgProto = new TbProtoQueueMsg<>(publishMsgProto.getTopicName(), publishMsgProto, headers);
        publishMsgQueuePublisher.sendMsg(msgProto, callback);
    }

    @Override
    public void processPublishMsg(PublishMsgWithId publishMsgWithId, PublishMsgCallback callback) {
        PublishMsgProto publishMsgProto = publishMsgWithId.getPublishMsgProto();
        String senderClientId = ProtoConverter.getClientId(publishMsgProto);

        clientLogger.logEvent(senderClientId, this.getClass(), "Start msg processing");

        MsgSubscriptions msgSubscriptions = getAllSubscriptionsForPubMsg(publishMsgProto, senderClientId);
        if (msgSubscriptions == null) {
            if (log.isDebugEnabled()) {
                log.debug("[{}] No subscriptions found for publish message!", publishMsgProto.getTopicName());
            }
            tbMessageStatsReportClient.reportStats(DROPPED_MSGS);
            callback.onSuccess();
            return;
        }

        clientLogger.logEvent(senderClientId, this.getClass(), "Found msg subscribers");

        PersistentMsgSubscriptions persistentMsgSubscriptions = processBasicAndCollectPersistentSubscriptions(msgSubscriptions, publishMsgProto);

        if (persistentMsgSubscriptions.isNotEmpty()) {
            processPersistentSubscriptions(publishMsgWithId, persistentMsgSubscriptions, callback);
        } else {
            callback.onSuccess();
        }

        clientLogger.logEvent(senderClientId, this.getClass(), "Finished msg processing");
    }

    private void processPersistentSubscriptions(PublishMsgWithId publishMsgWithId, PersistentMsgSubscriptions persistentSubscriptions, PublishMsgCallback callback) {
        long startTime = System.nanoTime();
        msgPersistenceManager.processPublish(publishMsgWithId, persistentSubscriptions, callback);
        publishMsgProcessingTimerStats.logPersistentMessagesProcessing(startTime, TimeUnit.NANOSECONDS);
    }

    PersistentMsgSubscriptions processBasicAndCollectPersistentSubscriptions(MsgSubscriptions msgSubscriptions,
                                                                             PublishMsgProto publishMsgProto) {
        final PersistentMsgSubscriptions persistentSubscriptions = new PersistentMsgSubscriptions(
                null, null, msgSubscriptions.getAllApplicationSharedSubscriptions()
        );
        long startTime = System.nanoTime();

        if (!CollectionUtils.isEmpty(msgSubscriptions.getCommonSubscriptions())) {
            processSubscriptions(msgSubscriptions.getCommonSubscriptions(), publishMsgProto, persistentSubscriptions);
        }
        if (!CollectionUtils.isEmpty(msgSubscriptions.getTargetDeviceSharedSubscriptions())) {
            processSubscriptions(msgSubscriptions.getTargetDeviceSharedSubscriptions(), publishMsgProto, persistentSubscriptions);
        }

        if (publishMsgProcessingTimerStats != null) {
            publishMsgProcessingTimerStats.logNotPersistentMessagesProcessing(startTime, TimeUnit.NANOSECONDS);
        }
        return persistentSubscriptions;
    }

    private void processSubscriptions(List<Subscription> subscriptions, PublishMsgProto publishMsgProto,
                                      final PersistentMsgSubscriptions persistentMsgSubscriptions) {
        boolean nonPersistentByPubQos = publishMsgProto.getQos() == MqttQoS.AT_MOST_ONCE.value();
        if (nonPersistentByPubQos) {
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
            persistentMsgSubscriptions.setDeviceSubscriptions(initSubscriptionListIfNull(persistentMsgSubscriptions.getDeviceSubscriptions(), subscriptions.size()));
            persistentMsgSubscriptions.setApplicationSubscriptions(initSubscriptionListIfNull(persistentMsgSubscriptions.getApplicationSubscriptions(), subscriptions.size()));
            if (processSubscriptionsInParallel) {
                subscriptions
                        .parallelStream()
                        .forEach(subscription -> processSubscription(
                                subscription,
                                publishMsgProto,
                                persistentMsgSubscriptions.getApplicationSubscriptions(),
                                persistentMsgSubscriptions.getDeviceSubscriptions())
                        );
            } else {
                for (Subscription subscription : subscriptions) {
                    processSubscription(
                            subscription,
                            publishMsgProto,
                            persistentMsgSubscriptions.getApplicationSubscriptions(),
                            persistentMsgSubscriptions.getDeviceSubscriptions()
                    );
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
            log.trace("Found 0 subscriptions for [{}] msg", publishMsgProto);
            return null;
        }
        clientSubscriptions = applyTotalMsgsRateLimits(clientSubscriptions);
        if (clientSubscriptions.isEmpty()) {
            return null;
        }

        if (sharedSubscriptionCacheService.sharedSubscriptionsInitialized()) {
            Set<TopicSharedSubscription> topicSharedSubscriptions = null;
            List<ValueWithTopicFilter<ClientSubscription>> commonClientSubscriptions = new ArrayList<>(clientSubscriptionsSize);

            for (ValueWithTopicFilter<ClientSubscription> clientSubscription : clientSubscriptions) {
                topicSharedSubscriptions = addSubscription(clientSubscription, commonClientSubscriptions, topicSharedSubscriptions);
            }

            SharedSubscriptions sharedSubscriptions = sharedSubscriptionCacheService.get(topicSharedSubscriptions);

            return new MsgSubscriptions(
                    collectCommonSubscriptions(commonClientSubscriptions, senderClientId),
                    sharedSubscriptions == null ? null : sharedSubscriptions.getApplicationSubscriptions(),
                    getTargetDeviceSharedSubscriptions(sharedSubscriptions, publishMsgProto.getQos())
            );
        } else {
            return new MsgSubscriptions(
                    collectCommonSubscriptions(clientSubscriptions, senderClientId),
                    null,
                    null
            );
        }
    }

    List<ValueWithTopicFilter<ClientSubscription>> applyTotalMsgsRateLimits(List<ValueWithTopicFilter<ClientSubscription>> clientSubscriptions) {
        if (rateLimitService.isTotalMsgsLimitEnabled() && clientSubscriptions.size() > 1) {
            int availableTokens = (int) rateLimitService.tryConsumeAsMuchAsPossibleTotalMsgs(clientSubscriptions.size());
            if (availableTokens == 0) {
                log.debug("No available tokens left for total msgs bucket");
                return Collections.emptyList();
            }
            if (log.isDebugEnabled() && availableTokens < clientSubscriptions.size()) {
                log.debug("Hitting total messages rate limits on subscriptions processing. Skipping {} messages", clientSubscriptions.size() - availableTokens);
            }
            return clientSubscriptions.subList(0, availableTokens);
        }
        return clientSubscriptions;
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

        if (clientSubscriptionWithTopicFilterList.isEmpty()) {
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
                sharedSubscription.getTopicSharedSubscription().getTopicFilter(),
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
                .serviceId(serviceInfoProvider.getServiceId())
                .clientId(clientInfo.getClientId())
                .type(clientInfo.getType())
                .clientIpAdr(clientInfo.getClientIpAdr())
                .cleanStart(false)
                .sessionExpiryInterval(1000)
                .build();
    }

    List<Subscription> collectSubscriptions(
            List<ValueWithTopicFilter<ClientSubscription>> clientSubscriptionWithTopicFilterList, String senderClientId) {
        Map<String, Subscription> map = Maps.newHashMapWithExpectedSize(clientSubscriptionWithTopicFilterList.size());

        for (var clientSubsWithTopicFilter : clientSubscriptionWithTopicFilterList) {
            boolean noLocalOptionMet = isNoLocalOptionMet(clientSubsWithTopicFilter, senderClientId);
            if (noLocalOptionMet) {
                if (log.isDebugEnabled()) {
                    log.debug("[{}] No local option is met for sender client!", senderClientId);
                }
                tbMessageStatsReportClient.reportStats(DROPPED_MSGS);
                continue;
            }

            Subscription subscription = convertToSubscription(clientSubsWithTopicFilter);
            if (subscription == null) {
                continue;
            }

            var clientId = subscription.getClientId();
            var value = map.get(clientId);
            if (value != null) {
                map.put(clientId, getSubscriptionWithHigherQos(value, subscription));
            } else {
                map.put(clientId, subscription);
            }
        }
        return map.isEmpty() ? null : new ArrayList<>(map.values());
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
                clientSubscription.getValue().getQos(),
                clientSessionInfo,
                clientSubscription.getValue().getShareName(),
                clientSubscription.getValue().getOptions());
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

    Subscription getSubscriptionWithHigherQos(Subscription first, Subscription second) {
        return first.getQos() > second.getQos() ? first : second;
    }

    private boolean isPersistentBySubInfo(Subscription subscription) {
        return subscription.getClientSessionInfo().isPersistent() && subscription.getQos() != MqttQoS.AT_MOST_ONCE.value();
    }

    private void deliver(PublishMsgProto publishMsgProto, Subscription subscription) {
        downLinkProxy.sendBasicMsg(subscription, publishMsgProto);
    }

    private DefaultTbQueueMsgHeaders createHeaders(PublishMsg publishMsg) {
        return MqttPropertiesUtil.createHeaders(publishMsg);
    }
}
