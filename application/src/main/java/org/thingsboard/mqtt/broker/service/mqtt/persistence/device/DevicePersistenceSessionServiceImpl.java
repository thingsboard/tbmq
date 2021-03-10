/**
 * Copyright © 2016-2020 The Thingsboard Authors
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.mqtt.broker.service.mqtt.persistence.device;

import com.google.common.util.concurrent.ListenableFuture;
import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.PublishedMsgInfo;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgService;
import org.thingsboard.mqtt.broker.dao.messages.TopicFilterQuery;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.service.subscription.SubscriptionManager;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.SessionState;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DevicePersistenceSessionServiceImpl implements DevicePersistenceSessionService {
    private final ThreadLocal<Long> lastPublishTimestamp = ThreadLocal.withInitial(() -> 0L);

    private List<ExecutorService> msgDistributionExecutors;

    @Value("${application.mqtt.persistent-session.device.distributor-threads-count}")
    private int distributorThreadsCount;
    @Value("${application.mqtt.persistent-session.device.distributor-threads-shutdown-timeout}")
    private int gracefulShutdownTimeout;

    private final DeviceMsgService deviceMsgService;
    private final DeviceLastPublishCtxService lastPublishCtxService;
    private final PublishMsgDeliveryService publishMsgDeliveryService;
    private final DeviceTopicEventService topicEventService;
    private final DeviceInFlightMessagesService inFlightMessagesService;
    private final SubscriptionManager subscriptionManager;
    private final PersistedTopicsService persistedTopicsService;

    @PostConstruct
    public void init() {
        this.msgDistributionExecutors = new ArrayList<>(distributorThreadsCount);
        for (int i = 0; i < distributorThreadsCount; i++) {
            msgDistributionExecutors.add(Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName("persisted-device-distributor-" + i)));
        }
    }

    @Override
    public void processMsgPersistence(List<Subscription> deviceSubscriptions, QueueProtos.PublishMsgProto publishMsgProto) {
        if (deviceSubscriptions.isEmpty()) {
            return;
        }
        // TODO: move to separate service
        long publishTimestamp = getPublishTimestamp();
        ListenableFuture<Void> saveFuture = deviceMsgService.save(createDeviceMsg(publishTimestamp, publishMsgProto));
        saveFuture.addListener(() -> {
            persistedTopicsService.addTopic(publishMsgProto.getTopicName());
            for (Subscription subscription : deviceSubscriptions) {
                ClientSessionCtx sessionCtx = subscription.getSessionCtx();
                if (sessionCtx != null && sessionCtx.getSessionState() == SessionState.CONNECTED) {
                    sendMsg(publishTimestamp, subscription.getTopicFilter(), subscription.getMqttQoSValue(), publishMsgProto.getTopicName(),
                            publishMsgProto.getQos(), publishMsgProto.getPayload().toByteArray(), subscription.getSessionCtx());
                }
            }
        }, getMsgDistributionExecutor(publishMsgProto.getTopicName()));

    }

    @Override
    public void processSubscribe(String clientId, List<TopicSubscription> topicSubscriptions) {
        long now = System.currentTimeMillis();
        for (TopicSubscription topicSubscription : topicSubscriptions) {
            topicEventService.saveTopicEvent(clientId, topicSubscription.getTopic(), TopicEventType.SUBSCRIBED, now);
        }
    }

    @Override
    public void processUnsubscribe(String clientId, List<String> topicFilters) {
        long now = System.currentTimeMillis();
        for (String topicFilter : topicFilters) {
            topicEventService.saveTopicEvent(clientId, topicFilter, TopicEventType.UNSUBSCRIBED, now);
        }
    }

    @Override
    public void clearPersistedCtx(String clientId) {
        // TODO: think about clearing persisted messages
        lastPublishCtxService.clearContext(clientId);
        topicEventService.clearPersistedEvents(clientId);
        inFlightMessagesService.clearInFlightMessages(clientId);
    }

    @Override
    public void acknowledgeDelivery(String clientId, int packetId) {
        PublishedMsgInfo publishedMsgInfo = inFlightMessagesService.msgAcknowledged(clientId, packetId);
        if (publishedMsgInfo == null) {
            log.debug("[{}] No persisted packet for ID {}.", clientId, packetId);
        } else {
            topicEventService.saveTopicEvent(clientId, publishedMsgInfo.getSubscriptionTopicFilter(), TopicEventType.ACKNOWLEDGED, publishedMsgInfo.getTimestamp());
        }
    }

    @Override
    public void startProcessingPersistedMessages(ClientSessionCtx clientSessionCtx) {
        String clientId = clientSessionCtx.getClientId();
        inFlightMessagesService.clientConnected(clientId);
        Set<TopicSubscription> deviceSubscriptions = subscriptionManager.getClientSubscriptions(clientId);
        Map<String, List<Pair<Long, TopicSubscription>>> subscriptionsByTopic = new HashMap<>();
        List<TopicFilterQuery> topicFilterQueries = deviceSubscriptions.stream()
                .map(subscription -> {
                    String topicFilter = subscription.getTopic();
                    // TODO: log time
                    Set<String> matchingTopics = persistedTopicsService.getMatchingTopics(topicFilter);
                    Long lastMsgTime;
                    Long lastAcknowledgedMsgTime = topicEventService.getLastEventTime(clientId, topicFilter, TopicEventType.ACKNOWLEDGED);
                    if (lastAcknowledgedMsgTime == null) {
                        lastMsgTime = topicEventService.getLastEventTime(clientId, topicFilter, TopicEventType.SUBSCRIBED);
                    } else {
                        lastMsgTime = lastAcknowledgedMsgTime;
                    }
                    if (lastMsgTime == null) {
                        log.warn("[{}] For topic filter {} cannot find last message timestamp.", clientId, topicFilter);
                        return null;
                    }
                    for (String matchingTopic : matchingTopics) {
                        subscriptionsByTopic.computeIfAbsent(matchingTopic, s -> new ArrayList<>()).add(Pair.of(lastMsgTime, subscription));
                    }
                    return new TopicFilterQuery(matchingTopics, lastMsgTime);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        List<DevicePublishMsg> persistedMessages = deviceMsgService.findPersistedMessages(topicFilterQueries);
        Map<TopicTimestamp, PublishedMsgInfo> unacknowledgedMessagesByTopicTimestamp = inFlightMessagesService.getUnacknowledgedMessages(clientId).stream()
                .collect(Collectors.toMap(
                        publishedMsgInfo -> new TopicTimestamp(publishedMsgInfo.getTopic(), publishedMsgInfo.getTimestamp()),
                        Function.identity()));

        for (DevicePublishMsg persistedMessage : persistedMessages) {
            TopicSubscription topicSubscription = subscriptionsByTopic.get(persistedMessage.getTopic()).stream()
                    .filter(timeQosPair -> timeQosPair.getLeft() <= persistedMessage.getTimestamp())
                    .map(Pair::getRight)
                    .max(Comparator.comparingInt(TopicSubscription::getQos))
                    .orElse(null);
            if (topicSubscription == null) {
                log.warn("[{}] For topic {} and time {} cannot find corresponding topic subscription.", clientId, persistedMessage.getTopic(), persistedMessage.getTimestamp());
                continue;
            }
            TopicTimestamp topicTimestamp = new TopicTimestamp(persistedMessage.getTopic(), persistedMessage.getTimestamp());
            PublishedMsgInfo unacknowledgedMsgInfo = unacknowledgedMessagesByTopicTimestamp.get(topicTimestamp);
            if (unacknowledgedMsgInfo != null) {
                resendMsg(unacknowledgedMsgInfo.getPacketId(), topicSubscription.getQos(), persistedMessage.getTopic(),
                        persistedMessage.getQos(), persistedMessage.getPayload(), clientSessionCtx);
            } else {
                sendMsg(persistedMessage.getTimestamp(), topicSubscription.getTopic(), topicSubscription.getQos(), persistedMessage.getTopic(),
                        persistedMessage.getQos(), persistedMessage.getPayload(), clientSessionCtx);
            }
        }

    }

    @Override
    public void stopProcessingPersistedMessages(String clientId) {
        inFlightMessagesService.clientDisconnected(clientId);
    }

    private void sendMsg(Long timestamp, String subscriptionTopicFilter, int subscriptionQoS,
                         String msgTopic, int msgQoS, byte[] payload, ClientSessionCtx sessionCtx) {
        String clientId = sessionCtx.getClientId();

        int packetId = lastPublishCtxService.getNextPacketId(clientId);
        lastPublishCtxService.saveLastPublishCtx(clientId, packetId);

        PublishedMsgInfo publishedMsgInfo = PublishedMsgInfo.builder().timestamp(timestamp)
                .subscriptionTopicFilter(subscriptionTopicFilter)
                .topic(msgTopic)
                .packetId(packetId)
                .build();
        inFlightMessagesService.msgPublished(clientId, publishedMsgInfo);

        int minQoSValue = Math.min(subscriptionQoS, msgQoS);
        publishMsgDeliveryService.sendPublishMsgToClient(sessionCtx, packetId, msgTopic, MqttQoS.valueOf(minQoSValue), payload);

        topicEventService.saveTopicEvent(clientId, subscriptionTopicFilter, TopicEventType.PUBLISHED, timestamp);
    }

    private void resendMsg(int packetId, int subscriptionQoS, String msgTopic, int msgQoS, byte[] payload, ClientSessionCtx sessionCtx) {
        int minQoSValue = Math.min(subscriptionQoS, msgQoS);
        publishMsgDeliveryService.sendPublishMsgToClient(sessionCtx, packetId, msgTopic, MqttQoS.valueOf(minQoSValue), payload);
    }

    private DevicePublishMsg createDeviceMsg(long publishTimestamp, QueueProtos.PublishMsgProto publishMsgProto) {
        return DevicePublishMsg.builder()
                .timestamp(publishTimestamp)
                .topic(publishMsgProto.getTopicName())
                .qos(publishMsgProto.getQos())
                .payload(publishMsgProto.getPayload().toByteArray())
                .build();
    }

    private long getPublishTimestamp() {
        long currentTimestamp = System.currentTimeMillis();
        if (lastPublishTimestamp.get() >= currentTimestamp) {
            lastPublishTimestamp.set(lastPublishTimestamp.get() + 1);
        } else {
            lastPublishTimestamp.set(currentTimestamp);
        }
        return lastPublishTimestamp.get();
    }

    private Executor getMsgDistributionExecutor(String topic) {
        int executorIndex = (topic.hashCode() & 0x7FFFFFFF) % msgDistributionExecutors.size();
        return msgDistributionExecutors.get(executorIndex);
    }

    @PreDestroy
    public void destroy() {
        if (msgDistributionExecutors != null) {
            gracefullyStopDistributionExecutors();
        }
    }

    private void gracefullyStopDistributionExecutors() {
        for (int i = 0; i < msgDistributionExecutors.size(); i++) {
            ExecutorService executorService = msgDistributionExecutors.get(i);
            log.debug("Shutting down executor #{}", i);
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(gracefulShutdownTimeout, TimeUnit.MILLISECONDS)) {
                    log.warn("Failed to await termination of executor #{}", i);
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.warn("Failed to await termination of executor #{}", i);
                executorService.shutdownNow();
            }
        }
    }
}
