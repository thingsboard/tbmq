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
package org.thingsboard.mqtt.broker.actors.client.service.subscription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.adaptor.NettyMqttConverter;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionConsumer;
import org.thingsboard.mqtt.broker.service.subscription.SubscriptionPersistenceService;
import org.thingsboard.mqtt.broker.service.subscription.data.SubscriptionsSourceKey;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionCacheService;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionProcessor;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.locks.Lock;
import com.google.common.util.concurrent.Striped;
import java.util.function.Supplier;
import org.thingsboard.mqtt.broker.actors.client.messages.SubscriptionChangedEventMsg;
import org.thingsboard.mqtt.broker.service.subscription.SubscriptionWriteCallback;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.thingsboard.mqtt.broker.common.data.util.CallbackUtil.createCallback;

/**
 * not thread-safe for operations with the same 'clientId'
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientSubscriptionServiceImpl implements ClientSubscriptionService {

    private final SubscriptionPersistenceService subscriptionPersistenceService;
    private final SubscriptionService subscriptionService;
    private final SharedSubscriptionProcessor sharedSubscriptionProcessor;
    private final SharedSubscriptionCacheService sharedSubscriptionCacheService;
    private final StatsManager statsManager;
    private final ClientMqttActorManager clientMqttActorManager;

    private ConcurrentMap<String, Set<TopicSubscription>> clientSubscriptionsMap;
    // Running total of subscriptions across all clients, maintained on every subscribe/unsubscribe/clear so
    // the 'subscriptions' gauge and getClientSubscriptionsCount stay O(1) rather than summing every client's
    // set on each read (this map is the largest in the broker at the 100M-connection scale it targets).
    private final LongAdder subscriptionCount = new LongAdder();
    private volatile boolean initialized = false;
    private final SubscriptionUpdateOrder subscriptionUpdateOrder = new SubscriptionUpdateOrder();
    // Integration administration can write outside a MQTT client actor. Keep the
    // same per-client critical section for those writes and Kafka reconciliation,
    // with a fixed-size lock pool rather than a lock entry retained per device.
    private final Striped<Lock> subscriptionLocks = Striped.lock(4096);

    @Override
    public void init(Map<SubscriptionsSourceKey, Set<TopicSubscription>> clientTopicSubscriptions) {
        clientSubscriptionsMap = new ConcurrentHashMap<>();
        // Keys are unique by client id (SubscriptionsSourceKey equals/hashCode exclude 'source'),
        // so a single pass safely populates the map and subscribes each client exactly once.
        clientTopicSubscriptions.forEach((key, topicSubscriptions) -> {
            String clientId = key.getId();
            clientSubscriptionsMap.put(clientId, topicSubscriptions);
            subscriptionService.subscribe(clientId, topicSubscriptions);
            sharedSubscriptionCacheService.put(clientId, topicSubscriptions);
            subscriptionCount.add(topicSubscriptions.size());
        });
        statsManager.registerSubscriptionsStats(subscriptionCount);
        initialized = true;
        log.info("Subscriptions initialized. Clients with subscriptions: {}, total subscriptions: {}",
                clientSubscriptionsMap.size(), getClientSubscriptionsCount());
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public void startListening(ClientSubscriptionConsumer consumer) {
        consumer.listen(this::processSubscriptionsUpdate);
    }

    @Override
    public void subscribeAndPersist(String clientId, Collection<TopicSubscription> topicSubscriptions) {
        BasicCallback callback = createCallback(
                () -> log.trace("[{}] Persisted topic subscriptions", clientId),
                t -> log.warn("[{}] Failed to persist topic subscriptions", clientId, t));
        subscribeAndPersist(clientId, topicSubscriptions, callback);
    }

    @Override
    public void subscribeAndPersist(String clientId, Collection<TopicSubscription> topicSubscriptions, BasicCallback callback) {
        log.trace("[{}] Subscribing to {}.", clientId, topicSubscriptions);
        mutateAndPersist(clientId, () -> subscribe(clientId, topicSubscriptions), callback);
    }

    @Override
    public void subscribeInternally(String clientId, Collection<TopicSubscription> topicSubscriptions) {
        log.trace("[{}] Subscribing internally to {}.", clientId, topicSubscriptions);
        subscribe(clientId, topicSubscriptions);
    }

    private Set<TopicSubscription> subscribe(String clientId, Collection<TopicSubscription> topicSubscriptions) {
        subscriptionService.subscribe(clientId, topicSubscriptions);

        Set<TopicSubscription> clientSubscriptions = clientSubscriptionsMap.computeIfAbsent(clientId, s -> new HashSet<>());
        int sizeBefore = clientSubscriptions.size();
        clientSubscriptions.removeIf(sub -> {
            boolean existSubs = topicSubscriptions.contains(sub);
            if (existSubs && sub.isSharedSubscription()) {
                sharedSubscriptionCacheService.remove(clientId, sub);
            }
            return existSubs;
        });
        clientSubscriptions.addAll(topicSubscriptions);
        // Net change to this client's set == net change to the total (re-subscribes cancel out).
        subscriptionCount.add(clientSubscriptions.size() - sizeBefore);
        sharedSubscriptionCacheService.put(clientId, topicSubscriptions);
        return clientSubscriptions;
    }

    @Override
    public void unsubscribeAndPersist(String clientId, Collection<String> topicFilters) {
        BasicCallback callback = createCallback(
                () -> log.trace("[{}] Persisted unsubscribed topics", clientId),
                t -> log.warn("[{}] Failed to persist unsubscribed topics", clientId, t));
        unsubscribeAndPersist(clientId, topicFilters, callback);
    }

    @Override
    public void unsubscribeAndPersist(String clientId, Collection<String> topicFilters, BasicCallback callback) {
        log.trace("[{}] Unsubscribe and persist {}.", clientId, topicFilters);
        mutateAndPersist(clientId, () -> unsubscribe(clientId, topicFilters).survivingSubscriptions(), callback);
    }

    @Override
    public void unsubscribeAndPersistReportingRemoved(String clientId, Collection<String> topicFilters, UnsubscribeCallback callback) {
        log.trace("[{}] Unsubscribing from {} and reporting removed.", clientId, topicFilters);
        List<TopicSubscription> removed = new ArrayList<>();
        mutateAndPersist(clientId, () -> {
            UnsubscribeResult result = unsubscribe(clientId, topicFilters);
            removed.addAll(result.removedSubscriptions());
            return result.survivingSubscriptions();
        }, createCallback(() -> callback.onSuccess(removed), callback::onFailure));
    }

    @Override
    public void unsubscribeInternally(String clientId, Collection<String> topicFilters) {
        log.trace("[{}] Unsubscribing internally from {}.", clientId, topicFilters);
        unsubscribe(clientId, topicFilters);
    }

    private UnsubscribeResult unsubscribe(String clientId, Collection<String> topicFilters) {
        List<String> topics = extractTopicFilterFromSharedTopic(topicFilters);
        subscriptionService.unsubscribe(clientId, topics);

        Set<TopicSubscription> clientSubscriptions = clientSubscriptionsMap.computeIfAbsent(clientId, s -> new HashSet<>());
        List<TopicSubscription> removedSubscriptions = new ArrayList<>();
        clientSubscriptions.removeIf(topicSubscription -> {
            boolean unsubscribe = topics.contains(topicSubscription.getTopicFilter());
            if (unsubscribe) {
                processSharedUnsubscribe(clientId, topicSubscription);
                removedSubscriptions.add(topicSubscription);
            }
            return unsubscribe;
        });
        subscriptionCount.add(-removedSubscriptions.size());
        return new UnsubscribeResult(clientSubscriptions, removedSubscriptions);
    }

    // survivingSubscriptions are persisted; removedSubscriptions feed the UNSUBACK codes and lifecycle events.
    private record UnsubscribeResult(Set<TopicSubscription> survivingSubscriptions,
                                     List<TopicSubscription> removedSubscriptions) {
    }

    private List<String> extractTopicFilterFromSharedTopic(Collection<String> topicFilters) {
        return topicFilters.stream()
                .map(NettyMqttConverter::getTopicFilter)
                .collect(Collectors.toList());
    }

    @Override
    public void clearSubscriptionsAndPersist(String clientId) {
        BasicCallback callback = createCallback(
                () -> log.trace("[{}] Cleared subscriptions", clientId),
                t -> log.warn("[{}] Failed to clear subscriptions", clientId, t));
        clearSubscriptionsAndPersist(clientId, callback);
    }

    @Override
    public void clearSubscriptionsAndPersist(String clientId, BasicCallback callback) {
        log.trace("[{}] Clearing all subscriptions.", clientId);
        mutateAndPersist(clientId, () -> {
            clearSubscriptions(clientId);
            return Collections.emptySet();
        }, callback);
    }

    @Override
    public void clearSubscriptionsInternally(String clientId) {
        log.trace("[{}] Clearing all subscriptions internally.", clientId);
        clearSubscriptions(clientId);
    }

    private void clearSubscriptions(String clientId) {
        Set<TopicSubscription> clientSubscriptions = clientSubscriptionsMap.remove(clientId);
        if (clientSubscriptions == null) {
            log.debug("[{}] There were no active subscriptions for client.", clientId);
            return;
        }
        subscriptionCount.add(-clientSubscriptions.size());
        List<String> unsubscribeTopics = clientSubscriptions.stream()
                .peek(topicSubscription -> processSharedUnsubscribe(clientId, topicSubscription))
                .map(TopicSubscription::getTopicFilter)
                .collect(Collectors.toList());
        subscriptionService.unsubscribe(clientId, unsubscribeTopics);
    }

    @Override
    public long getClientSubscriptionsCount() {
        return subscriptionCount.sum();
    }

    @Override
    public Set<TopicSubscription> getClientSubscriptions(String clientId) {
        return clientSubscriptionsMap == null ? Collections.emptySet() :
                new HashSet<>(clientSubscriptionsMap.getOrDefault(clientId, Collections.emptySet()));
    }

    @Override
    public Set<String> getIntegrationSubscriptions(String integrationId) {
        Set<TopicSubscription> subscriptions = clientSubscriptionsMap.getOrDefault(integrationId, Collections.emptySet());
        return subscriptions.stream().map(TopicSubscription::getTopicFilter).collect(Collectors.toSet());
    }

    @Override
    public Set<TopicSharedSubscription> getClientSharedSubscriptions(String clientId) {
        Set<TopicSubscription> clientSubscriptions = getClientSubscriptions(clientId);
        return clientSubscriptions
                .stream()
                .filter(TopicSubscription::isSharedSubscription)
                .map(this::topicSubscriptionToTopicSharedSubscription)
                .collect(Collectors.toSet());
    }

    @Override
    public Map<String, Set<TopicSubscription>> getAllClientSubscriptions() {
        return new HashMap<>(clientSubscriptionsMap == null ? Map.of() : clientSubscriptionsMap);
    }

    private void processSharedUnsubscribe(String clientId, TopicSubscription topicSubscription) {
        if (topicSubscription.isSharedSubscription()) {
            unsubscribeSharedSubscription(topicSubscription);
            sharedSubscriptionCacheService.remove(clientId, topicSubscription);
        }
    }

    private void unsubscribeSharedSubscription(TopicSubscription topicSubscription) {
        sharedSubscriptionProcessor.unsubscribe(topicSubscriptionToTopicSharedSubscription(topicSubscription));
    }

    private TopicSharedSubscription topicSubscriptionToTopicSharedSubscription(TopicSubscription topicSubscription) {
        return TopicSharedSubscription.fromTopicSubscription(topicSubscription);
    }

    private void mutateAndPersist(String clientId, Supplier<Set<TopicSubscription>> mutation, BasicCallback callback) {
        Lock lock = subscriptionLocks.get(clientId);
        lock.lock();
        try {
            mutateAndPersistLocked(clientId, mutation, callback);
        } finally {
            lock.unlock();
        }
    }

    private void mutateAndPersistLocked(String clientId, Supplier<Set<TopicSubscription>> mutation, BasicCallback callback) {
        SubscriptionUpdateOrder.Pending pending = subscriptionUpdateOrder.begin(clientId, getClientSubscriptions(clientId));
        Set<TopicSubscription> snapshot;
        try {
            snapshot = Set.copyOf(mutation.get());
        } catch (RuntimeException error) {
            subscriptionUpdateOrder.complete(pending, Set.of(), -1);
            clientMqttActorManager.processSubscriptionWriteCompleted(clientId);
            throw error;
        }
        AtomicBoolean completed = new AtomicBoolean();
        SubscriptionWriteCallback completion = new SubscriptionWriteCallback() {
            @Override
            public void onSuccess(long offset) {
                if (offset < 0) {
                    onFailure(new IllegalStateException("Subscription persistence did not return a Kafka offset"));
                    return;
                }
                if (!completed.compareAndSet(false, true)) {
                    return;
                }
                subscriptionUpdateOrder.complete(pending, snapshot, offset);
                clientMqttActorManager.processSubscriptionWriteCompleted(clientId);
                if (callback != null) {
                    callback.onSuccess();
                }
            }

            @Override
            public void onFailure(Throwable error) {
                if (!completed.compareAndSet(false, true)) {
                    return;
                }
                subscriptionUpdateOrder.complete(pending, snapshot, -1);
                clientMqttActorManager.processSubscriptionWriteCompleted(clientId);
                if (callback != null) {
                    callback.onFailure(error);
                }
            }
        };
        try {
            subscriptionPersistenceService.persistClientSubscriptionsAsync(clientId, snapshot, completion);
        } catch (RuntimeException error) {
            completion.onFailure(error);
        }
    }

    @Override
    public void reconcileSubscriptionUpdate(String clientId, SubscriptionChangedEventMsg msg, Consumer<Set<TopicSubscription>> apply) {
        Lock lock = subscriptionLocks.get(clientId);
        lock.lock();
        try {
            subscriptionUpdateOrder.resolve(clientId,
                    msg.isLocalWriteCompleted() ? null : msg.getTopicSubscriptions(), msg.getOffset()).ifPresent(apply);
        } finally {
            lock.unlock();
        }
    }

    private boolean processSubscriptionsUpdate(String clientId, String serviceId, Set<TopicSubscription> topicSubscriptions, long offset) {
        // Consume local echoes too: skipping them lets an older remote cleanup erase
        // an already acknowledged local subscription permanently after reconnect.
        clientMqttActorManager.processSubscriptionsChanged(clientId, topicSubscriptions, offset);
        return true;
    }
}
