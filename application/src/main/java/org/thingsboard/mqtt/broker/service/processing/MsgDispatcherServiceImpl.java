/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
import org.thingsboard.mqtt.broker.service.processing.shared.DeviceSharedSubscriptionProcessor;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.stats.timer.PublishMsgProcessingTimerStats;
import org.thingsboard.mqtt.broker.service.subscription.EntitySubscription;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.service.subscription.ValueWithTopicFilter;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionCacheService;
import org.thingsboard.mqtt.broker.util.MqttPropertiesUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.DROPPED_MSGS;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.INCOMING_MSGS;

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
        tbMessageStatsReportClient.reportStats(INCOMING_MSGS);
        tbMessageStatsReportClient.reportClientSendStats(sessionInfo.getClientId(), publishMsg.getQos());
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
        if (clientSubscriptions.isEmpty()) {
            return null;
        }

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
                map.put(clientId, getSubscriptionWithHigherQosAndAllSubscriptionIds(value, subscription));
            } else {
                map.put(clientId, subscription);
            }
        }
        return map.isEmpty() ? null : new ArrayList<>(map.values());
    }

    private Subscription convertToSubscription(ValueWithTopicFilter<EntitySubscription> clientSubscription) {
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
