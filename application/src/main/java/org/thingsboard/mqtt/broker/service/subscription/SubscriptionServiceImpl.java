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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.exception.SubscriptionTrieClearException;

import java.util.Collection;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService, SubscriptionReader, SubscriptionMaintenanceService {
    private final SubscriptionTrie<ClientSubscription> subscriptionTrie;

    @Override
    public void subscribe(String clientId, Collection<TopicSubscription> topicSubscriptions) {
        log.trace("Executing subscribe [{}] [{}]", clientId, topicSubscriptions);
        for (TopicSubscription topicSubscription : topicSubscriptions) {
            subscriptionTrie.put(topicSubscription.getTopic(), new ClientSubscription(clientId, topicSubscription.getQos()));
        }
    }

    @Override
    public void unsubscribe(String clientId, Collection<String> topicFilters) {
        log.trace("Executing unsubscribe [{}] [{}]", clientId, topicFilters);
        for (String topicFilter : topicFilters) {
            boolean successfullyDeleted = subscriptionTrie.delete(topicFilter, val -> clientId.equals(val.getClientId()));
            if (!successfullyDeleted) {
                log.error("[{}] Couldn't delete subscription for topic {}", clientId, topicFilter);
            }
        }
    }

    @Override
    public Collection<ValueWithTopicFilter<ClientSubscription>> getSubscriptions(String topic) {
        return subscriptionTrie.get(topic);
    }

    @Override
    public void clearEmptyTopicNodes() throws SubscriptionTrieClearException {
        log.trace("Executing clearEmptyTopicNodes");
        subscriptionTrie.clearEmptyNodes();
    }

    @Scheduled(cron = "${mqtt.subscription-trie.clear-nodes-cron}", zone = "${mqtt.subscription-trie.clear-nodes-zone}")
    private void scheduleEmptyNodeClear() {
        log.info("Start clearing empty nodes in SubscriptionTrie");
        try {
            subscriptionTrie.clearEmptyNodes();
        } catch (SubscriptionTrieClearException e) {
            log.error("Failed to clear empty nodes. Reason - {}.", e.getMessage());
        }
    }
}
