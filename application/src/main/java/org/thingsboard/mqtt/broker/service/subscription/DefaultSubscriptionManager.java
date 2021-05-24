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
package org.thingsboard.mqtt.broker.service.subscription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.exception.SubscriptionTrieClearException;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionService;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;

import javax.annotation.PostConstruct;
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
public class DefaultSubscriptionManager implements SubscriptionManager {
    private ConcurrentMap<String, Set<TopicSubscription>> clientSubscriptionsMap;

    private final ClientSessionService clientSessionService;
    private final SubscriptionPersistenceService subscriptionPersistenceService;
    private final SubscriptionService subscriptionService;
    private final StatsManager statsManager;

    // TODO: sync subscriptions (and probably ClientSession)
    //      - store events for each action in separate topic + sometimes make snapshots (apply events on 'value' sequentially)
    //      - manage subscriptions in one thread and one node (probably merge subscriptions with ClientSession)


    @PostConstruct
    public void init() {
        this.clientSubscriptionsMap = loadPersistedClientSubscriptions();
        statsManager.registerClientSubscriptionsStats(clientSubscriptionsMap);

        log.info("Restoring persisted subscriptions for {} clients.", clientSubscriptionsMap.size());
        clientSubscriptionsMap.forEach((clientId, topicSubscriptions) -> {
            log.trace("[{}] Restoring subscriptions - {}.", clientId, topicSubscriptions);
            subscriptionService.subscribe(clientId, topicSubscriptions);
        });
    }

    @Override
    public void subscribe(String clientId, List<TopicSubscription> topicSubscriptions) {
        subscriptionService.subscribe(clientId, topicSubscriptions);

        Set<TopicSubscription> clientSubscriptions = clientSubscriptionsMap.computeIfAbsent(clientId, s -> new HashSet<>());
        clientSubscriptions.addAll(topicSubscriptions);
        // TODO: add 'version' to at least detect issues
        subscriptionPersistenceService.persistClientSubscriptions(clientId, clientSubscriptions);
    }

    @Override
    public void unsubscribe(String clientId, List<String> topicFilters) {
        subscriptionService.unsubscribe(clientId, topicFilters);

        Set<TopicSubscription> clientSubscriptions = clientSubscriptionsMap.computeIfAbsent(clientId, s -> new HashSet<>());
        clientSubscriptions.removeIf(topicSubscription -> topicFilters.contains(topicSubscription.getTopic()));
        subscriptionPersistenceService.persistClientSubscriptions(clientId, clientSubscriptions);
    }

    @Override
    public void clearSubscriptions(String clientId) {
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
        subscriptionPersistenceService.persistClientSubscriptions(clientId, Collections.emptySet());
    }

    @Override
    public Set<TopicSubscription> getClientSubscriptions(String clientId) {
        return clientSubscriptionsMap.getOrDefault(clientId, Collections.emptySet());
    }

    private ConcurrentMap<String, Set<TopicSubscription>> loadPersistedClientSubscriptions() {
        log.info("Load persisted client subscriptions.");
        Map<String, Set<TopicSubscription>> allClientSubscriptions = subscriptionPersistenceService.loadAllClientSubscriptions();
        Set<String> persistedClientIds = clientSessionService.getPersistedClientSessionInfos().keySet();
        ConcurrentMap<String, Set<TopicSubscription>> clientSubscriptions = new ConcurrentHashMap<>();
        allClientSubscriptions.forEach((clientId, topicSubscriptions) -> {
            if (persistedClientIds.contains(clientId)) {
                clientSubscriptions.put(clientId, new HashSet<>(topicSubscriptions));
            } else {
                log.debug("[{}] Clearing not persistent client subscriptions.", clientId);
                subscriptionPersistenceService.persistClientSubscriptions(clientId, Collections.emptySet());
            }
        });
        return clientSubscriptions;
    }
}
