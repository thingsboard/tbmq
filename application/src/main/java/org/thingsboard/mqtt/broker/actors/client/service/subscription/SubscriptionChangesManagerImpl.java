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
import org.thingsboard.mqtt.broker.actors.client.messages.SubscriptionChangedEventMsg;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;

import java.util.HashSet;
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

        if (newTopicSubscriptions.isEmpty()) {
            clientSubscriptionService.clearSubscriptionsInternally(clientId);
            return;
        }

        Set<TopicSubscription> subscribeTopics = getSubscribeTopics(newTopicSubscriptions, currentTopicSubscriptions);
        Set<TopicSubscription> unsubscribeTopics = getUnsubscribeTopics(newTopicSubscriptions, currentTopicSubscriptions);


        clientSubscriptionService.unsubscribeInternally(clientId, unsubscribeTopics.stream().map(TopicSubscription::getTopic).collect(Collectors.toList()));
        clientSubscriptionService.subscribeInternally(clientId, subscribeTopics);
    }

    private Set<TopicSubscription> getSubscribeTopics(Set<TopicSubscription> newTopicSubscriptions, Set<TopicSubscription> currentTopicSubscriptions) {
        Set<TopicSubscription> toSubscribe = new HashSet<>(newTopicSubscriptions);
        toSubscribe.removeAll(currentTopicSubscriptions);
        return toSubscribe;
    }

    private Set<TopicSubscription> getUnsubscribeTopics(Set<TopicSubscription> newTopicSubscriptions, Set<TopicSubscription> currentTopicSubscriptions) {
        Set<TopicSubscription> toUnsubscribe = new HashSet<>(currentTopicSubscriptions);
        toUnsubscribe.removeAll(newTopicSubscriptions);
        return toUnsubscribe;
    }
}
