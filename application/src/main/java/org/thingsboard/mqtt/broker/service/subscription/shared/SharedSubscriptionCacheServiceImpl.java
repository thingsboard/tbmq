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
package org.thingsboard.mqtt.broker.service.subscription.shared;

import com.google.common.collect.Sets;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.subscription.EntitySubscription;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.service.subscription.ValueWithTopicFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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

    private final ClientSessionCache clientSessionCache;
    @Getter
    private final ConcurrentMap<TopicSharedSubscription, SharedSubscriptions> sharedSubscriptionsMap = new ConcurrentHashMap<>();

    @Override
    public void put(String clientId, Collection<TopicSubscription> topicSubscriptions) {
        log.trace("[{}] Executing put of shared subscriptions {}", clientId, topicSubscriptions);
        if (CollectionUtils.isEmpty(topicSubscriptions)) {
            return;
        }
        List<TopicSubscription> sharedTopicSubscriptions = filterSharedTopicSubscriptions(topicSubscriptions);
        if (CollectionUtils.isEmpty(sharedTopicSubscriptions)) {
            return;
        }

        var clientSessionInfo = findClientSessionInfo(clientId);
        if (clientSessionInfo == null) {
            log.debug("[{}] Client session is not found for client", clientId);
            return;
        }

        for (TopicSubscription topicSubscription : sharedTopicSubscriptions) {
            Subscription subscription = newSubscription(clientId, topicSubscription);

            SharedSubscriptions sharedSubscriptions = sharedSubscriptionsMap.computeIfAbsent(getKey(topicSubscription), tss -> SharedSubscriptions.newInstance());

            Set<Subscription> subscriptions = getSubscriptionsByClientType(clientSessionInfo, sharedSubscriptions);
            updateSharedSubscriptions(subscriptions, clientId, subscription);

            // to handle client type change (Device -> Application or vice versa)
            Set<Subscription> subscriptionsByClientTypeInverted = getSubscriptionsByClientTypeInverted(clientSessionInfo, sharedSubscriptions);
            removeSubscription(subscriptionsByClientTypeInverted, clientId, subscription.getTopicFilter());
        }
        log.trace("Shared subscriptions updated!");
    }

    private List<TopicSubscription> filterSharedTopicSubscriptions(Collection<TopicSubscription> topicSubscriptions) {
        return topicSubscriptions
                .stream()
                .filter(TopicSubscription::isSharedSubscription)
                .collect(Collectors.toList());
    }

    private void updateSharedSubscriptions(Set<Subscription> sharedSubscriptions, String clientId, Subscription subscription) {
        removeSubscription(sharedSubscriptions, clientId, subscription.getTopicFilter());
        sharedSubscriptions.add(subscription);
    }

    private void removeSubscription(Set<Subscription> sharedSubscriptions, String clientId, String topicFilter) {
        sharedSubscriptions.removeIf(subs -> clientId.equals(subs.getClientSessionInfo().getClientId()) && topicFilter.equals(subs.getTopicFilter()));
    }

    @Override
    public void remove(String clientId, TopicSubscription topicSubscription) {
        log.trace("[{}] Executing remove of shared subscription {}", clientId, topicSubscription);
        TopicSharedSubscription key = getKey(topicSubscription);
        SharedSubscriptions sharedSubscriptions = sharedSubscriptionsMap.get(key);
        if (sharedSubscriptions == null) {
            return;
        }
        var clientSessionInfo = findClientSessionInfo(clientId);
        var topicFilter = topicSubscription.getTopicFilter();
        if (clientSessionInfo == null) {
            removeSubscription(sharedSubscriptions.getApplicationSubscriptions(), clientId, topicFilter);
            removeSubscription(sharedSubscriptions.getDeviceSubscriptions(), clientId, topicFilter);
        } else {
            Set<Subscription> subscriptions = getSubscriptionsByClientType(clientSessionInfo, sharedSubscriptions);
            removeSubscription(subscriptions, clientId, topicFilter);
        }
        log.trace("Shared subscription removed from set!");
        if (sharedSubscriptions.isEmpty()) {
            sharedSubscriptionsMap.remove(key);
            log.trace("[{}] Shared subscriptions removed completely!", key);
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
        log.trace("[{}] Executing isAnyOtherDeviceClientConnected!", topicSharedSubscription);
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

    @Override
    public CompositeSubscriptions getSubscriptions(List<ValueWithTopicFilter<EntitySubscription>> clientSubscriptions) {
        Set<TopicSharedSubscription> topicSharedSubscriptions = null;
        List<ValueWithTopicFilter<EntitySubscription>> commonClientSubscriptions = new ArrayList<>(clientSubscriptions.size());

        for (ValueWithTopicFilter<EntitySubscription> clientSubscription : clientSubscriptions) {
            topicSharedSubscriptions = processSubscription(clientSubscription, commonClientSubscriptions, topicSharedSubscriptions);
        }

        SharedSubscriptions sharedSubscriptions = this.get(topicSharedSubscriptions);
        return new CompositeSubscriptions(sharedSubscriptions, commonClientSubscriptions);
    }

    private Set<TopicSharedSubscription> processSubscription(ValueWithTopicFilter<EntitySubscription> clientSubscription,
                                                             List<ValueWithTopicFilter<EntitySubscription>> commonClientSubscriptions,
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

    Collection<Subscription> filterSubscriptions(Set<Subscription> subscriptions) {
        if (subscriptions.isEmpty()) {
            return subscriptions;
        }
        return subscriptions.stream()
                .map(subscription -> {
                    var clientSessionInfo = findClientSessionInfo(subscription.getClientId());
                    if (clientSessionInfo == null) {
                        return null;
                    }
                    return newSubscription(subscription, clientSessionInfo);
                }).filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        subscription -> subscription.getClientSessionInfo().getClientId(),
                        Function.identity(),
                        this::getSubscriptionWithHigherQosAndAllSubscriptionIds)
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

    private Subscription getSubscriptionWithHigherQosAndAllSubscriptionIds(Subscription first, Subscription second) {
        return first.compareAndGetHigherQosAndAllSubscriptionIds(second);
    }

    private TopicSharedSubscription getKey(TopicSubscription topicSubscription) {
        return new TopicSharedSubscription(topicSubscription.getTopicFilter(), topicSubscription.getShareName());
    }

    private Subscription newSubscription(String clientId, TopicSubscription topicSubscription) {
        return new Subscription(
                topicSubscription.getTopicFilter(),
                topicSubscription.getQos(),
                ClientSessionInfo.builder().clientId(clientId).build(),
                topicSubscription.getShareName(),
                topicSubscription.getOptions(),
                topicSubscription.getSubscriptionId()
        );
    }

    private Subscription newSubscription(Subscription subscription, ClientSessionInfo clientSessionInfo) {
        return subscription.withClientSessionInfo(clientSessionInfo);
    }

    private ClientSessionInfo findClientSessionInfo(String clientId) {
        return clientSessionCache.getClientSessionInfo(clientId);
    }

    private Set<Subscription> getSubscriptionsByClientType(ClientSessionInfo clientSessionInfo, SharedSubscriptions sharedSubscriptions) {
        return clientSessionInfo.isAppClient() ? sharedSubscriptions.getApplicationSubscriptions() : sharedSubscriptions.getDeviceSubscriptions();
    }

    private Set<Subscription> getSubscriptionsByClientTypeInverted(ClientSessionInfo clientSessionInfo, SharedSubscriptions sharedSubscriptions) {
        return clientSessionInfo.isAppClient() ? sharedSubscriptions.getDeviceSubscriptions() : sharedSubscriptions.getApplicationSubscriptions();
    }

    private Set<TopicSharedSubscription> initTopicSharedSubscriptionSetIfNull(Set<TopicSharedSubscription> topicSharedSubscriptions) {
        return topicSharedSubscriptions == null ? new HashSet<>() : topicSharedSubscriptions;
    }
}
