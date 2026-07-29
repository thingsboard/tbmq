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
package org.thingsboard.mqtt.broker.actors.client.service.subscription.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.util.TopicSubscriptionsUtil;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationSubscriptionUpdateServiceImpl implements IntegrationSubscriptionUpdateService {

    private final ClientSubscriptionService clientSubscriptionService;

    @Override
    public boolean processSubscriptionsUpdate(String integrationId, Set<TopicSubscription> newSubscriptions) {
        Set<TopicSubscription> currentSubscriptions = clientSubscriptionService.getClientSubscriptions(integrationId);

        if (log.isDebugEnabled()) {
            log.debug("[{}] Updating integration subscriptions, new subscriptions - {}, current subscriptions - {}.",
                    integrationId, newSubscriptions, currentSubscriptions);
        }

        if (newSubscriptions.isEmpty()) {
            if (currentSubscriptions.isEmpty()) {
                // Nothing to clear: persisting an empty set would rewrite the same state cluster-wide.
                return false;
            }
            clientSubscriptionService.clearSubscriptionsAndPersist(integrationId);
            return true;
        }

        TopicSubscriptionsUtil.SubscriptionsUpdate subscriptionsUpdate = TopicSubscriptionsUtil.getSubscriptionsUpdate(currentSubscriptions, newSubscriptions);
        boolean unsubscribed = processUnsubscribe(integrationId, subscriptionsUpdate);
        boolean subscribed = processSubscribe(integrationId, subscriptionsUpdate);
        return unsubscribed || subscribed;
    }

    private boolean processUnsubscribe(String integrationId, TopicSubscriptionsUtil.SubscriptionsUpdate subscriptionsUpdate) {
        Set<TopicSubscription> removedSubscriptions = subscriptionsUpdate.getToUnsubscribe();
        if (CollectionUtils.isEmpty(removedSubscriptions)) {
            return false;
        }
        Set<String> unsubscribeTopics = TopicSubscriptionsUtil.getUnsubscribeTopics(removedSubscriptions);
        clientSubscriptionService.unsubscribeAndPersist(integrationId, unsubscribeTopics);
        return true;
    }

    private boolean processSubscribe(String integrationId, TopicSubscriptionsUtil.SubscriptionsUpdate subscriptionsUpdate) {
        Set<TopicSubscription> addedSubscriptions = subscriptionsUpdate.getToSubscribe();
        if (CollectionUtils.isEmpty(addedSubscriptions)) {
            return false;
        }
        clientSubscriptionService.subscribeAndPersist(integrationId, addedSubscriptions);
        return true;
    }

}
