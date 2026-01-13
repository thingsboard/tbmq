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
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationSessionInfo;
import org.thingsboard.mqtt.broker.common.data.subscription.SubscriptionOptions;
import org.thingsboard.mqtt.broker.common.stats.MessagesStats;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
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
import org.thingsboard.mqtt.broker.service.processing.shared.DeviceSharedSubscriptionProcessor;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.stats.timer.PublishMsgProcessingTimerStats;
import org.thingsboard.mqtt.broker.service.subscription.EntitySubscription;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.service.subscription.ValueWithTopicFilter;
import org.thingsboard.mqtt.broker.service.subscription.data.EntitySubscriptionType;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionCacheService;
import org.thingsboard.mqtt.broker.util.MqttPropertiesUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.DROPPED_MSGS;

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
    private final DeviceSharedSubscriptionProcessor deviceSharedSubscriptionProcessor;
    private final SharedSubscriptionCacheService sharedSubscriptionCacheService;
    private final TbMessageStatsReportClient tbMessageStatsReportClient;
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
        final PersistentMsgSubscriptions persistentSubscriptions = PersistentMsgSubscriptions.newInstance(msgSubscriptions, processSubscriptionsInParallel);
        long startTime = System.nanoTime();

        if (!CollectionUtils.isEmpty(msgSubscriptions.getCommonSubscriptions())) {
            persistentSubscriptions.processSubscriptions(msgSubscriptions.getCommonSubscriptions(), publishMsgProto, subscription -> deliver(publishMsgProto, subscription));
        }
        if (!CollectionUtils.isEmpty(msgSubscriptions.getTargetDeviceSharedSubscriptions())) {
            persistentSubscriptions.processSubscriptions(msgSubscriptions.getTargetDeviceSharedSubscriptions(), publishMsgProto, subscription -> deliver(publishMsgProto, subscription));
        }

        if (publishMsgProcessingTimerStats != null) {
            publishMsgProcessingTimerStats.logNotPersistentMessagesProcessing(startTime, TimeUnit.NANOSECONDS);
        }
        return persistentSubscriptions;
    }

    MsgSubscriptions getAllSubscriptionsForPubMsg(PublishMsgProto publishMsgProto, String senderClientId) {
        var clientSubscriptions = subscriptionService.getSubscriptions(publishMsgProto.getTopicName());
        if (clientSubscriptions.isEmpty()) {
            log.trace("Found 0 subscriptions for [{}] msg", publishMsgProto);
            return null;
        }
        clientSubscriptions = applyTotalMsgsRateLimits(clientSubscriptions);

        if (sharedSubscriptionCacheService.sharedSubscriptionsInitialized()) {
            var compositeSubscriptions = sharedSubscriptionCacheService.getSubscriptions(clientSubscriptions);
            var sharedSubscriptions = compositeSubscriptions.getSharedSubscriptions();
            clientSubscriptions = compositeSubscriptions.getCommonSubscriptions();

            if (sharedSubscriptions != null) {
                return new MsgSubscriptions(
                        collectCommonSubscriptions(clientSubscriptions, senderClientId),
                        sharedSubscriptions.getApplicationSubscriptions(),
                        deviceSharedSubscriptionProcessor.getTargetSubscriptions(sharedSubscriptions.getDeviceSubscriptions(), publishMsgProto.getQos())
                );
            }
        }

        return new MsgSubscriptions(collectCommonSubscriptions(clientSubscriptions, senderClientId));
    }

    List<ValueWithTopicFilter<EntitySubscription>> applyTotalMsgsRateLimits(List<ValueWithTopicFilter<EntitySubscription>> clientSubscriptions) {
        int total = clientSubscriptions.size();

        if (!rateLimitService.isTotalMsgsLimitEnabled() || total <= 1) {
            return clientSubscriptions;
        }

        // We have already consumed 1 token for one subscription in PublishMsgConsumerServiceImpl.
        // Here we only check if we can send to the remaining (total - 1) subscriptions.
        int extraCandidates = total - 1;

        long consumed = rateLimitService.tryConsumeTotalMsgs(extraCandidates);
        int allowedExtra = (int) Math.min(consumed, extraCandidates);

        if (allowedExtra == extraCandidates) {
            return clientSubscriptions;
        }

        int deliverCount = 1 + allowedExtra;
        int dropped = total - deliverCount;

        if (allowedExtra == 0) {
            log.debug("No available extra tokens left for total msgs bucket. Delivering to 1 subscription, dropping {}",
                    dropped);
        } else {
            log.debug("Hitting total messages rate limits on subscriptions processing. Delivering to {}, dropping {}",
                    deliverCount, dropped);
        }

        if (dropped > 0) {
            tbMessageStatsReportClient.reportStats(DROPPED_MSGS, dropped);
        }

        return clientSubscriptions.subList(0, deliverCount);
    }

    private List<Subscription> collectCommonSubscriptions(
            List<ValueWithTopicFilter<EntitySubscription>> clientSubscriptionWithTopicFilterList, String senderClientId) {

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

    List<Subscription> collectSubscriptions(
            List<ValueWithTopicFilter<EntitySubscription>> clientSubscriptionWithTopicFilterList, String senderClientId) {
        Map<String, Subscription> map = Maps.newHashMapWithExpectedSize(clientSubscriptionWithTopicFilterList.size());

        for (var clientSubsWithTopicFilter : clientSubscriptionWithTopicFilterList) {
            boolean noLocalOptionMet = isNoLocalOptionMet(clientSubsWithTopicFilter, senderClientId);
            if (noLocalOptionMet) {
                log.debug("[{}] No local option is met for sender client!", senderClientId);
                continue;
            }

            Subscription subscription = convertToSubscription(clientSubsWithTopicFilter);
            if (subscription == null) {
                continue;
            }

            var clientId = subscription.getClientId();
            var value = map.get(clientId);
            if (value != null) {
                map.put(clientId, getSubscriptionWithHigherQosAndAllSubscriptionIds(value, subscription));
            } else {
                map.put(clientId, subscription);
            }
        }
        return map.isEmpty() ? null : new ArrayList<>(map.values());
    }

    private Subscription convertToSubscription(ValueWithTopicFilter<EntitySubscription> clientSubscription) {
        if (EntitySubscriptionType.INTEGRATION.equals(clientSubscription.getValue().getType())) {
            return new Subscription(
                    clientSubscription.getTopicFilter(),
                    clientSubscription.getValue().getQos(),
                    getIntegrationSessionInfo(clientSubscription),
                    clientSubscription.getValue().getShareName(),
                    clientSubscription.getValue().getOptions(),
                    clientSubscription.getValue().getSubscriptionId()
            );
        }
        String clientId = clientSubscription.getValue().getClientId();
        ClientSessionInfo clientSessionInfo = clientSessionCache.getClientSessionInfo(clientId);
        if (clientSessionInfo == null) {
            log.debug("[{}] Client session not found for existent client subscription.", clientId);
            return null;
        }
        return new Subscription(
                clientSubscription.getTopicFilter(),
                clientSubscription.getValue().getQos(),
                clientSessionInfo,
                clientSubscription.getValue().getShareName(),
                clientSubscription.getValue().getOptions(),
                clientSubscription.getValue().getSubscriptionId());
    }

    private IntegrationSessionInfo getIntegrationSessionInfo(ValueWithTopicFilter<EntitySubscription> clientSubscription) {
        return new IntegrationSessionInfo(
                clientSubscription.getValue().getClientId(),
                downLinkProxy.getServiceId()
        );
    }

    private boolean isNoLocalOptionMet(ValueWithTopicFilter<EntitySubscription> clientSubscriptionWithTopicFilter,
                                       String senderClientId) {
        SubscriptionOptions options = clientSubscriptionWithTopicFilter.getValue().getOptions();
        return options != null && options.isNoLocalOptionMet(clientSubscriptionWithTopicFilter.getValue().getClientId(), senderClientId);
    }

    Subscription getSubscriptionWithHigherQosAndAllSubscriptionIds(Subscription first, Subscription second) {
        return first.compareAndGetHigherQosAndAllSubscriptionIds(second);
    }

    private void deliver(PublishMsgProto publishMsgProto, Subscription subscription) {
        downLinkProxy.sendBasicMsg(subscription, publishMsgProto);
    }

    private DefaultTbQueueMsgHeaders createHeaders(PublishMsg publishMsg) {
        return MqttPropertiesUtil.createHeaders(publishMsg);
    }
}
