/**
 * Copyright © 2016-2022 The Thingsboard Authors
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
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.StringUtils;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;

import java.util.Collection;
import java.util.List;
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
        if (CollectionUtils.isEmpty(topicSubscriptions)) {
            return;
        }
        List<TopicSubscription> sharedTopicSubscriptions = filterSharedTopicSubscriptions(topicSubscriptions);
        if (CollectionUtils.isEmpty(sharedTopicSubscriptions)) {
            return;
        }

        var clientSession = findClientSession(clientId);
        if (clientSession == null) {
            if (log.isDebugEnabled()) {
                log.debug("[{}] Client session is not found for client.", clientId);
            }
            return;
        }

        for (TopicSubscription topicSubscription : sharedTopicSubscriptions) {
            Subscription subscription = newSubscription(topicSubscription, clientSession);

            SharedSubscriptions sharedSubscriptions = sharedSubscriptionsMap.computeIfAbsent(getKey(topicSubscription), tss -> SharedSubscriptions.newInstance());
            if (ClientType.APPLICATION == clientSession.getClientType()) {
                updateSharedSubscriptions(sharedSubscriptions.getApplicationSubscriptions(), clientId, topicSubscription, subscription);
            } else {
                updateSharedSubscriptions(sharedSubscriptions.getDeviceSubscriptions(), clientId, topicSubscription, subscription);
            }
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
        sharedSubscriptions.removeIf(subs -> clientId.equals(subs.getClientSession().getClientId()) && topicSubscription.getTopicFilter().equals(subs.getTopicFilter()));
    }

    @Override
    public void remove(String clientId, TopicSubscription topicSubscription) {
        TopicSharedSubscription key = getKey(topicSubscription);
        SharedSubscriptions sharedSubscriptions = sharedSubscriptionsMap.get(key);
        if (sharedSubscriptions == null) {
            return;
        }
        var clientSession = findClientSession(clientId);
        if (clientSession == null) {
            removeSubscription(sharedSubscriptions.getApplicationSubscriptions(), clientId, topicSubscription);
            removeSubscription(sharedSubscriptions.getDeviceSubscriptions(), clientId, topicSubscription);
        } else {
            if (ClientType.APPLICATION == clientSession.getClientType()) {
                removeSubscription(sharedSubscriptions.getApplicationSubscriptions(), clientId, topicSubscription);
            } else {
                removeSubscription(sharedSubscriptions.getDeviceSubscriptions(), clientId, topicSubscription);
            }
        }
        if (sharedSubscriptions.isEmpty()) {
            sharedSubscriptionsMap.remove(key);
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

    private Collection<Subscription> filterSubscriptions(Set<Subscription> subscriptions) {
        if (subscriptions.isEmpty()) {
            return subscriptions;
        }
        return subscriptions.stream()
                .map(subscription -> {
                    var clientSession = findClientSession(subscription.getClientSession().getClientId());
                    if (clientSession == null) {
                        return null;
                    }
                    return newSubscription(subscription, clientSession);
                }).filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        subscription -> subscription.getClientSession().getClientId(),
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

    private Subscription newSubscription(TopicSubscription topicSubscription, ClientSession clientSession) {
        return new Subscription(
                topicSubscription.getTopicFilter(),
                topicSubscription.getQos(),
                clientSession,
                topicSubscription.getShareName(),
                topicSubscription.getOptions()
        );
    }

    private Subscription newSubscription(Subscription subscription, ClientSession clientSession) {
        return new Subscription(
                subscription.getTopicFilter(),
                subscription.getQos(),
                clientSession,
                subscription.getShareName(),
                subscription.getOptions()
        );
    }

    private ClientSession findClientSession(String clientId) {
        return clientSessionCache.getClientSession(clientId);
    }
}
