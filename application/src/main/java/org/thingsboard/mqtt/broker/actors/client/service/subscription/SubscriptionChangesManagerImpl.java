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
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.actors.client.messages.SubscriptionChangedEventMsg;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.util.TopicSubscriptionsUtil;

import java.util.Set;

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
            log.debug("[{}] Updating Client's subscriptions, new subscriptions - {}, current subscriptions - {}.",
                    clientId, newTopicSubscriptions, currentTopicSubscriptions);
        }

        if (newTopicSubscriptions.isEmpty()) {
            clientSubscriptionService.clearSubscriptionsInternally(clientId);
            return;
        }

        TopicSubscriptionsUtil.SubscriptionsUpdate subscriptionsUpdate = TopicSubscriptionsUtil.getSubscriptionsUpdate(currentTopicSubscriptions, newTopicSubscriptions);
        processUnsubscribe(clientId, subscriptionsUpdate);
        processSubscribe(clientId, subscriptionsUpdate);
    }

    private void processUnsubscribe(String clientId, TopicSubscriptionsUtil.SubscriptionsUpdate subscriptionsUpdate) {
        Set<TopicSubscription> removedSubscriptions = subscriptionsUpdate.getToUnsubscribe();
        if (CollectionUtils.isEmpty(removedSubscriptions)) {
            return;
        }
        Set<String> unsubscribeTopics = TopicSubscriptionsUtil.getUnsubscribeTopics(removedSubscriptions);
        clientSubscriptionService.unsubscribeInternally(clientId, unsubscribeTopics);
    }

    private void processSubscribe(String clientId, TopicSubscriptionsUtil.SubscriptionsUpdate subscriptionsUpdate) {
        Set<TopicSubscription> addedSubscriptions = subscriptionsUpdate.getToSubscribe();
        if (CollectionUtils.isEmpty(addedSubscriptions)) {
            return;
        }
        clientSubscriptionService.subscribeInternally(clientId, addedSubscriptions);
    }

}
