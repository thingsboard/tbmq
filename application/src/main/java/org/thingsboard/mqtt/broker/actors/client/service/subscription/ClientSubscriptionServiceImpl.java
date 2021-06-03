/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.subscription.SubscriptionPersistenceService;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
// not thread-safe for operations with the same 'clientId'
public class ClientSubscriptionServiceImpl implements ClientSubscriptionService {
    private ConcurrentMap<String, Set<TopicSubscription>> clientSubscriptionsMap;

    private final SubscriptionPersistenceService subscriptionPersistenceService;
    private final SubscriptionService subscriptionService;
    private final StatsManager statsManager;

    // TODO: sync subscriptions (and probably ClientSession)
    //      - store events for each action in separate topic + sometimes make snapshots (apply events on 'value' sequentially)
    //      - manage subscriptions in one thread and one node (probably merge subscriptions with ClientSession)


    @Override
    public void init(Map<String, Set<TopicSubscription>> clientTopicSubscriptions) {
        this.clientSubscriptionsMap = new ConcurrentHashMap<>(clientTopicSubscriptions);
        statsManager.registerClientSubscriptionsStats(clientSubscriptionsMap);

        log.info("Restoring persisted subscriptions for {} clients.", clientSubscriptionsMap.size());
        clientSubscriptionsMap.forEach((clientId, topicSubscriptions) -> {
            log.trace("[{}] Restoring subscriptions - {}.", clientId, topicSubscriptions);
            subscriptionService.subscribe(clientId, topicSubscriptions);
        });
    }

    @Override
    public void subscribe(String clientId, Collection<TopicSubscription> topicSubscriptions) {
        subscribeInternally(clientId, topicSubscriptions);

        Set<TopicSubscription> clientSubscriptions = clientSubscriptionsMap.computeIfAbsent(clientId, s -> new HashSet<>());
        subscriptionPersistenceService.persistClientSubscriptions(clientId, clientSubscriptions);
    }

    @Override
    public void subscribeInternally(String clientId, Collection<TopicSubscription> topicSubscriptions) {
        subscriptionService.subscribe(clientId, topicSubscriptions);

        Set<TopicSubscription> clientSubscriptions = clientSubscriptionsMap.computeIfAbsent(clientId, s -> new HashSet<>());
        clientSubscriptions.addAll(topicSubscriptions);
    }

    @Override
    public void unsubscribe(String clientId, Collection<String> topicFilters) {
        unsubscribeInternally(clientId, topicFilters);

        Set<TopicSubscription> updatedClientSubscriptions = clientSubscriptionsMap.get(clientId);
        subscriptionPersistenceService.persistClientSubscriptions(clientId, updatedClientSubscriptions);
    }

    @Override
    public void unsubscribeInternally(String clientId, Collection<String> topicFilters) {
        subscriptionService.unsubscribe(clientId, topicFilters);

        Set<TopicSubscription> clientSubscriptions = clientSubscriptionsMap.computeIfAbsent(clientId, s -> new HashSet<>());
        clientSubscriptions.removeIf(topicSubscription -> topicFilters.contains(topicSubscription.getTopic()));
    }

    @Override
    public void clearSubscriptions(String clientId) {
        clearSubscriptionsInternally(clientId);
        subscriptionPersistenceService.persistClientSubscriptions(clientId, Collections.emptySet());
    }

    @Override
    public void clearSubscriptionsInternally(String clientId) {
        log.trace("[{}] Clearing all subscriptions.", clientId);
        Set<TopicSubscription> clientSubscriptions = clientSubscriptionsMap.remove(clientId);
        if (clientSubscriptions == null) {
            log.debug("[{}] There were no active subscriptions for client.", clientId);
            return;
        }
        List<String> unsubscribeTopics = clientSubscriptions.stream()
                .map(TopicSubscription::getTopic)
                .collect(Collectors.toList());
        subscriptionService.unsubscribe(clientId, unsubscribeTopics);
    }

    @Override
    public Set<TopicSubscription> getClientSubscriptions(String clientId) {
        return new HashSet<>(clientSubscriptionsMap.getOrDefault(clientId, Collections.emptySet()));
    }


}
