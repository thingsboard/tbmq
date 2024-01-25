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
package org.thingsboard.mqtt.broker.service.subscription.shared;

import com.google.common.collect.Sets;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.StringUtils;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SharedSubscriptionCacheServiceImpl implements SharedSubscriptionCacheService {

    // TODO: 01.11.23 improve data persistence - probably only clientId is needed in the map since later the current session state is fetched

    private final ClientSessionCache clientSessionCache;
    @Getter
    private final ConcurrentMap<TopicSharedSubscription, SharedSubscriptions> sharedSubscriptionsMap = new ConcurrentHashMap<>();

    @Override
    public void put(String clientId, Collection<TopicSubscription> topicSubscriptions) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Executing put of shared subscriptions {}", clientId, topicSubscriptions);
        }
        if (CollectionUtils.isEmpty(topicSubscriptions)) {
            return;
        }
        List<TopicSubscription> sharedTopicSubscriptions = filterSharedTopicSubscriptions(topicSubscriptions);
        if (CollectionUtils.isEmpty(sharedTopicSubscriptions)) {
            return;
        }

        var clientSessionInfo = findClientSessionInfo(clientId);
        if (clientSessionInfo == null) {
            if (log.isDebugEnabled()) {
                log.debug("[{}] Client session is not found for client.", clientId);
            }
            return;
        }

        for (TopicSubscription topicSubscription : sharedTopicSubscriptions) {
            Subscription subscription = newSubscription(topicSubscription, clientSessionInfo);

            SharedSubscriptions sharedSubscriptions = sharedSubscriptionsMap.computeIfAbsent(getKey(topicSubscription), tss -> SharedSubscriptions.newInstance());
            if (ClientType.APPLICATION == clientSessionInfo.getType()) {
                updateSharedSubscriptions(sharedSubscriptions.getApplicationSubscriptions(), clientId, topicSubscription, subscription);
            } else {
                updateSharedSubscriptions(sharedSubscriptions.getDeviceSubscriptions(), clientId, topicSubscription, subscription);
            }
        }
        if (log.isTraceEnabled()) {
            log.trace("Shared subscriptions updated!");
        }
    }

    private List<TopicSubscription> filterSharedTopicSubscriptions(Collection<TopicSubscription> topicSubscriptions) {
        return topicSubscriptions
                .stream()
                .filter(topicSubscription -> StringUtils.isNotEmpty(topicSubscription.getShareName()))
                .collect(Collectors.toList());
    }

    private void updateSharedSubscriptions(Set<Subscription> sharedSubscriptions, String clientId,
                                           TopicSubscription topicSubscription, Subscription subscription) {
        removeSubscription(sharedSubscriptions, clientId, topicSubscription);
        sharedSubscriptions.add(subscription);
    }

    private void removeSubscription(Set<Subscription> sharedSubscriptions, String clientId, TopicSubscription topicSubscription) {
        sharedSubscriptions.removeIf(subs -> clientId.equals(subs.getClientSessionInfo().getClientId()) && topicSubscription.getTopicFilter().equals(subs.getTopicFilter()));
    }

    @Override
    public void remove(String clientId, TopicSubscription topicSubscription) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Executing remove of shared subscription {}", clientId, topicSubscription);
        }
        TopicSharedSubscription key = getKey(topicSubscription);
        SharedSubscriptions sharedSubscriptions = sharedSubscriptionsMap.get(key);
        if (sharedSubscriptions == null) {
            return;
        }
        var clientSessionInfo = findClientSessionInfo(clientId);
        if (clientSessionInfo == null) {
            removeSubscription(sharedSubscriptions.getApplicationSubscriptions(), clientId, topicSubscription);
            removeSubscription(sharedSubscriptions.getDeviceSubscriptions(), clientId, topicSubscription);
        } else {
            if (ClientType.APPLICATION == clientSessionInfo.getType()) {
                removeSubscription(sharedSubscriptions.getApplicationSubscriptions(), clientId, topicSubscription);
            } else {
                removeSubscription(sharedSubscriptions.getDeviceSubscriptions(), clientId, topicSubscription);
            }
        }
        if (log.isTraceEnabled()) {
            log.trace("Shared subscription removed from set!");
        }
        if (sharedSubscriptions.isEmpty()) {
            sharedSubscriptionsMap.remove(key);
            if (log.isTraceEnabled()) {
                log.trace("[{}] Shared subscriptions removed completely!", key);
            }
        }
    }

    @Override
    public SharedSubscriptions get(Set<TopicSharedSubscription> topicSharedSubscriptions) {
        if (CollectionUtils.isEmpty(topicSharedSubscriptions)) {
            return null;
        }
        SharedSubscriptions subscriptions = collectAllSubscriptions(topicSharedSubscriptions);

        Set<Subscription> applicationSubscriptions = Sets.newConcurrentHashSet(filterSubscriptions(subscriptions.getApplicationSubscriptions()));
        Set<Subscription> deviceSubscriptions = Sets.newConcurrentHashSet(filterSubscriptions(subscriptions.getDeviceSubscriptions()));

        return new SharedSubscriptions(applicationSubscriptions, deviceSubscriptions);
    }

    @Override
    public boolean isAnyOtherDeviceClientConnected(String clientId, TopicSharedSubscription topicSharedSubscription) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Executing isAnyOtherDeviceClientConnected!", topicSharedSubscription);
        }
        SharedSubscriptions sharedSubscriptions = sharedSubscriptionsMap.get(topicSharedSubscription);
        if (sharedSubscriptions == null) {
            log.error("Failed to find any shared subscriptions for the key {}", topicSharedSubscription);
            throw new RuntimeException("Failed to find any shared subscriptions for the key " + topicSharedSubscription);
        }

        Set<Subscription> deviceSubscriptions = sharedSubscriptions.getDeviceSubscriptions();
        long count = deviceSubscriptions
                .stream()
                .filter(subscription -> !subscription.getClientSessionInfo().getClientId().equals(clientId))
                .filter(subscription -> findClientSessionInfo(subscription.getClientSessionInfo().getClientId()).isConnected())
                .filter(subscription -> subscription.getQos() > 0)
                .count();
        return count > 0;
    }

    @Override
    public boolean sharedSubscriptionsInitialized() {
        return !sharedSubscriptionsMap.isEmpty();
    }

    @Override
    public Map<TopicSharedSubscription, SharedSubscriptions> getAllSharedSubscriptions() {
        return new HashMap<>(sharedSubscriptionsMap);
    }

    private Collection<Subscription> filterSubscriptions(Set<Subscription> subscriptions) {
        if (subscriptions.isEmpty()) {
            return subscriptions;
        }
        return subscriptions.stream()
                .map(subscription -> {
                    var clientSessionInfo = findClientSessionInfo(subscription.getClientSessionInfo().getClientId());
                    if (clientSessionInfo == null) {
                        return null;
                    }
                    return newSubscription(subscription, clientSessionInfo);
                }).filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        subscription -> subscription.getClientSessionInfo().getClientId(),
                        Function.identity(),
                        this::getSubscriptionWithHigherQos)
                )
                .values();
    }

    private SharedSubscriptions collectAllSubscriptions(Set<TopicSharedSubscription> topicSharedSubscriptions) {
        SharedSubscriptions result = SharedSubscriptions.newInstance();

        for (TopicSharedSubscription topicSharedSubscription : topicSharedSubscriptions) {
            SharedSubscriptions sharedSubscriptions = sharedSubscriptionsMap.get(topicSharedSubscription);
            if (sharedSubscriptions == null) {
                continue;
            }
            result.getApplicationSubscriptions().addAll(sharedSubscriptions.getApplicationSubscriptions());
            result.getDeviceSubscriptions().addAll(sharedSubscriptions.getDeviceSubscriptions());
        }
        return result;
    }

    private Subscription getSubscriptionWithHigherQos(Subscription first, Subscription second) {
        return first.getQos() > second.getQos() ? first : second;
    }

    private TopicSharedSubscription getKey(TopicSubscription topicSubscription) {
        return new TopicSharedSubscription(topicSubscription.getTopicFilter(), topicSubscription.getShareName());
    }

    private Subscription newSubscription(TopicSubscription topicSubscription, ClientSessionInfo clientSessionInfo) {
        return new Subscription(
                topicSubscription.getTopicFilter(),
                topicSubscription.getQos(),
                clientSessionInfo,
                topicSubscription.getShareName(),
                topicSubscription.getOptions()
        );
    }

    private Subscription newSubscription(Subscription subscription, ClientSessionInfo clientSessionInfo) {
        return new Subscription(
                subscription.getTopicFilter(),
                subscription.getQos(),
                clientSessionInfo,
                subscription.getShareName(),
                subscription.getOptions()
        );
    }

    private ClientSessionInfo findClientSessionInfo(String clientId) {
        return clientSessionCache.getClientSessionInfo(clientId);
    }
}
