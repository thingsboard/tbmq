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
package org.thingsboard.mqtt.broker.actors.client.service.subscription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.adaptor.NettyMqttConverter;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.data.StringUtils;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.subscription.SubscriptionPersistenceService;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionCacheService;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionProcessor;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static org.thingsboard.mqtt.broker.common.data.util.CallbackUtil.createCallback;

@Slf4j
@Service
@RequiredArgsConstructor
// not thread-safe for operations with the same 'clientId'
public class ClientSubscriptionServiceImpl implements ClientSubscriptionService {

    private final SubscriptionPersistenceService subscriptionPersistenceService;
    private final SubscriptionService subscriptionService;
    private final SharedSubscriptionProcessor sharedSubscriptionProcessor;
    private final SharedSubscriptionCacheService sharedSubscriptionCacheService;
    private final StatsManager statsManager;

    private ConcurrentMap<String, Set<TopicSubscription>> clientSubscriptionsMap;

    // TODO: sync subscriptions (and probably ClientSession)
    //      - store events for each action in separate topic + sometimes make snapshots (apply events on 'value' sequentially)
    //      - manage subscriptions in one thread and one node (probably merge subscriptions with ClientSession)

    @Override
    public void init(Map<String, Set<TopicSubscription>> clientTopicSubscriptions) {
        this.clientSubscriptionsMap = new ConcurrentHashMap<>(clientTopicSubscriptions);
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
    public void subscribeAndPersist(String clientId, Collection<TopicSubscription> topicSubscriptions) {
        BasicCallback callback = createCallback(
                () -> {
                    if (log.isTraceEnabled()) {
                        log.trace("[{}] Persisted topic subscriptions", clientId);
                    }
                },
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

        sharedSubscriptionCacheService.put(clientId, topicSubscriptions);

        Set<TopicSubscription> clientSubscriptions = clientSubscriptionsMap.computeIfAbsent(clientId, s -> new HashSet<>());
        clientSubscriptions.removeIf(topicSubscriptions::contains);
        clientSubscriptions.addAll(topicSubscriptions);
        return clientSubscriptions;
    }

    @Override
    public void unsubscribeAndPersist(String clientId, Collection<String> topicFilters) {
        BasicCallback callback = createCallback(
                () -> {
                    if (log.isTraceEnabled()) {
                        log.trace("[{}] Persisted unsubscribed topics", clientId);
                    }
                },
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
                .map(tf -> NettyMqttConverter.isSharedTopic(tf) ? NettyMqttConverter.getTopicName(tf) : tf)
                .collect(Collectors.toList());
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
    public Set<TopicSubscription> getClientSubscriptions(String clientId) {
        return new HashSet<>(clientSubscriptionsMap.getOrDefault(clientId, Collections.emptySet()));
    }

    private void processSharedUnsubscribe(String clientId, TopicSubscription topicSubscription) {
        if (isSharedSubscription(topicSubscription)) {
            unsubscribeSharedSubscription(topicSubscription);
            sharedSubscriptionCacheService.remove(clientId, topicSubscription);
        }
    }

    private boolean isSharedSubscription(TopicSubscription topicSubscription) {
        return !StringUtils.isEmpty(topicSubscription.getShareName());
    }

    private void unsubscribeSharedSubscription(TopicSubscription topicSubscription) {
        sharedSubscriptionProcessor.unsubscribe(getSharedSubscriptionTopicFilter(topicSubscription));
    }

    private TopicSharedSubscription getSharedSubscriptionTopicFilter(TopicSubscription topicSubscription) {
        return new TopicSharedSubscription(topicSubscription.getTopicFilter(), topicSubscription.getShareName());
    }
}
