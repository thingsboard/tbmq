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
import org.thingsboard.mqtt.broker.actors.client.messages.SubscriptionChangedEventMsg;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.util.CollectionsUtil;

import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionChangesManagerImpl implements SubscriptionChangesManager {

    private final ClientSubscriptionService clientSubscriptionService;

    @Override
    public void processSubscriptionChangedEvent(String clientId, SubscriptionChangedEventMsg msg) {
        Set<TopicSubscription> currentTopicSubscriptions = clientSubscriptionService.getClientSubscriptions(clientId);
        Set<TopicSubscription> newTopicSubscriptions = msg.getTopicSubscriptions();

        if (log.isDebugEnabled()) {
            log.debug("[{}] Updating Client's subscriptions, new subscriptions size - {}, current subscriptions size - {}.",
                    clientId, newTopicSubscriptions.size(), currentTopicSubscriptions.size());
        }

        if (newTopicSubscriptions.isEmpty()) {
            clientSubscriptionService.clearSubscriptionsInternally(clientId);
            return;
        }

        processUnsubscribe(clientId, newTopicSubscriptions, currentTopicSubscriptions);

        processSubscribe(clientId, newTopicSubscriptions, currentTopicSubscriptions);
    }

    private void processUnsubscribe(String clientId, Set<TopicSubscription> newTopicSubscriptions, Set<TopicSubscription> currentTopicSubscriptions) {
        Set<TopicSubscription> removedSubscriptions = getRemovedSubscriptions(newTopicSubscriptions, currentTopicSubscriptions);
        Set<String> unsubscribeTopics = getUnsubscribeTopics(removedSubscriptions);
        clientSubscriptionService.unsubscribeInternally(clientId, unsubscribeTopics);
    }

    private Set<TopicSubscription> getRemovedSubscriptions(Set<TopicSubscription> newTopicSubscriptions,
                                                           Set<TopicSubscription> currentTopicSubscriptions) {
        return CollectionsUtil.getRemovedValues(newTopicSubscriptions, currentTopicSubscriptions, getComparator());
    }

    private void processSubscribe(String clientId, Set<TopicSubscription> newTopicSubscriptions, Set<TopicSubscription> currentTopicSubscriptions) {
        Set<TopicSubscription> addedTopicSubscriptions = getAddedSubscriptions(newTopicSubscriptions, currentTopicSubscriptions);
        clientSubscriptionService.subscribeInternally(clientId, addedTopicSubscriptions);
    }

    private Set<TopicSubscription> getAddedSubscriptions(Set<TopicSubscription> newTopicSubscriptions,
                                                         Set<TopicSubscription> currentTopicSubscriptions) {
        return CollectionsUtil.getAddedValues(newTopicSubscriptions, currentTopicSubscriptions, getComparator());
    }

    private Comparator<TopicSubscription> getComparator() {
        return Comparator.comparing(TopicSubscription::getTopicFilter).thenComparing(TopicSubscription::getQos);
    }

    private Set<String> getUnsubscribeTopics(Set<TopicSubscription> removedSubscriptions) {
        return removedSubscriptions.stream()
                .map(TopicSubscription::getTopicFilter)
                .collect(Collectors.toSet());
    }
}
