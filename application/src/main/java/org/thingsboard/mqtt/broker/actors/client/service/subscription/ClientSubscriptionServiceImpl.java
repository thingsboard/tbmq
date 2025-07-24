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
package org.thingsboard.mqtt.broker.actors.client.service.subscription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.adaptor.NettyMqttConverter;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionConsumer;
import org.thingsboard.mqtt.broker.service.subscription.SubscriptionPersistenceService;
import org.thingsboard.mqtt.broker.service.subscription.data.SubscriptionsSourceKey;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionCacheService;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionProcessor;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
    private final ServiceInfoProvider serviceInfoProvider;
    private final ClientMqttActorManager clientMqttActorManager;

    private ConcurrentMap<String, Set<TopicSubscription>> clientSubscriptionsMap;

    @Override
    public void init(Map<SubscriptionsSourceKey, Set<TopicSubscription>> clientTopicSubscriptions) {
        this.clientSubscriptionsMap = new ConcurrentHashMap<>();
        clientTopicSubscriptions.forEach((key, value) -> this.clientSubscriptionsMap.put(key.getId(), value));
        statsManager.registerClientSubscriptionsStats(clientSubscriptionsMap);

        log.info("Restoring persisted subscriptions for {} clients.", clientSubscriptionsMap.size());
        clientSubscriptionsMap.forEach((clientId, topicSubscriptions) -> {
            if (log.isTraceEnabled()) {
                log.trace("[{}] Restoring subscriptions - {}.", clientId, topicSubscriptions);
            }
            subscriptionService.subscribe(clientId, topicSubscriptions);
            sharedSubscriptionCacheService.put(clientId, topicSubscriptions);
        });
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
        if (log.isTraceEnabled()) {
            log.trace("[{}] Subscribing to {}.", clientId, topicSubscriptions);
        }
        Set<TopicSubscription> clientSubscriptions = subscribe(clientId, topicSubscriptions);

        subscriptionPersistenceService.persistClientSubscriptionsAsync(clientId, clientSubscriptions, callback);
    }

    @Override
    public void subscribeInternally(String clientId, Collection<TopicSubscription> topicSubscriptions) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Subscribing internally to {}.", clientId, topicSubscriptions);
        }
        subscribe(clientId, topicSubscriptions);
    }

    private Set<TopicSubscription> subscribe(String clientId, Collection<TopicSubscription> topicSubscriptions) {
        subscriptionService.subscribe(clientId, topicSubscriptions);

        Set<TopicSubscription> clientSubscriptions = clientSubscriptionsMap.computeIfAbsent(clientId, s -> new HashSet<>());
        clientSubscriptions.removeIf(sub -> {
            boolean existSubs = topicSubscriptions.contains(sub);
            if (existSubs && sub.isSharedSubscription()) {
                sharedSubscriptionCacheService.remove(clientId, sub);
            }
            return existSubs;
        });
        clientSubscriptions.addAll(topicSubscriptions);
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
        if (log.isTraceEnabled()) {
            log.trace("[{}] Unsubscribing from {}.", clientId, topicFilters);
        }
        Set<TopicSubscription> updatedClientSubscriptions = unsubscribe(clientId, topicFilters);

        subscriptionPersistenceService.persistClientSubscriptionsAsync(clientId, updatedClientSubscriptions, callback);
    }

    @Override
    public void unsubscribeInternally(String clientId, Collection<String> topicFilters) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Unsubscribing internally from {}.", clientId, topicFilters);
        }
        unsubscribe(clientId, topicFilters);
    }

    private Set<TopicSubscription> unsubscribe(String clientId, Collection<String> topicFilters) {
        List<String> topics = extractTopicFilterFromSharedTopic(topicFilters);
        subscriptionService.unsubscribe(clientId, topics);

        Set<TopicSubscription> clientSubscriptions = clientSubscriptionsMap.computeIfAbsent(clientId, s -> new HashSet<>());
        clientSubscriptions.removeIf(topicSubscription -> {
            boolean unsubscribe = topics.contains(topicSubscription.getTopicFilter());
            if (unsubscribe) {
                processSharedUnsubscribe(clientId, topicSubscription);
            }
            return unsubscribe;
        });
        return clientSubscriptions;
    }

    private List<String> extractTopicFilterFromSharedTopic(Collection<String> topicFilters) {
        return topicFilters.stream()
                .map(tf -> NettyMqttConverter.isSharedTopic(tf) ? NettyMqttConverter.getTopicFilter(tf) : tf)
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
        if (log.isTraceEnabled()) {
            log.trace("[{}] Clearing all subscriptions.", clientId);
        }
        clearSubscriptions(clientId);
        subscriptionPersistenceService.persistClientSubscriptionsAsync(clientId, Collections.emptySet(), callback);
    }

    @Override
    public void clearSubscriptionsInternally(String clientId) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Clearing all subscriptions internally.", clientId);
        }
        clearSubscriptions(clientId);
    }

    private void clearSubscriptions(String clientId) {
        Set<TopicSubscription> clientSubscriptions = clientSubscriptionsMap.remove(clientId);
        if (clientSubscriptions == null) {
            if (log.isDebugEnabled()) {
                log.debug("[{}] There were no active subscriptions for client.", clientId);
            }
            return;
        }
        List<String> unsubscribeTopics = clientSubscriptions.stream()
                .peek(topicSubscription -> processSharedUnsubscribe(clientId, topicSubscription))
                .map(TopicSubscription::getTopicFilter)
                .collect(Collectors.toList());
        subscriptionService.unsubscribe(clientId, unsubscribeTopics);
    }

    @Override
    public int getClientSubscriptionsCount() {
        return clientSubscriptionsMap == null ? 0 : clientSubscriptionsMap.values().stream().mapToInt(Set::size).sum();
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

    private boolean processSubscriptionsUpdate(String clientId, String serviceId, Set<TopicSubscription> topicSubscriptions) {
        if (serviceInfoProvider.getServiceId().equals(serviceId)) {
            log.trace("[{}] Subscription changed msg was already processed {}", clientId, topicSubscriptions);
            return false;
        }
        clientMqttActorManager.processSubscriptionsChanged(clientId, topicSubscriptions);
        return true;
    }
}
