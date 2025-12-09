/**
 * Copyright © 2016-2025 The Thingsboard Authors
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

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.subscription.IntegrationTopicSubscription;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.exception.SubscriptionTrieClearException;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.stats.timer.SubscriptionTimerStats;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscription;
import org.thingsboard.mqtt.broker.service.subscription.EntitySubscription;
import org.thingsboard.mqtt.broker.service.subscription.SubscriptionTrie;
import org.thingsboard.mqtt.broker.service.subscription.ValueWithTopicFilter;
import org.thingsboard.mqtt.broker.service.subscription.integration.IntegrationSubscription;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionTrie<EntitySubscription> subscriptionTrie;
    private final SubscriptionTimerStats subscriptionTimerStats;

    public SubscriptionServiceImpl(SubscriptionTrie<EntitySubscription> subscriptionTrie, StatsManager statsManager) {
        this.subscriptionTrie = subscriptionTrie;
        this.subscriptionTimerStats = statsManager.getSubscriptionTimerStats();
    }

    @Override
    public void subscribe(String clientId, Collection<TopicSubscription> topicSubscriptions) {
        if (log.isTraceEnabled()) {
            log.trace("Executing subscribe [{}] [{}]", clientId, topicSubscriptions);
        }
        for (TopicSubscription topicSubscription : topicSubscriptions) {
            if (topicSubscription instanceof IntegrationTopicSubscription) {
                subscriptionTrie.put(topicSubscription.getTopicFilter(), new IntegrationSubscription(clientId));
            } else {
                subscriptionTrie.put(
                        topicSubscription.getTopicFilter(),
                        new ClientSubscription(
                                clientId,
                                topicSubscription.getQos(),
                                topicSubscription.getShareName(),
                                topicSubscription.getOptions(),
                                topicSubscription.getSubscriptionId())
                );
            }
        }
    }

    @Override
    public void unsubscribe(String clientId, Collection<String> topicFilters) {
        if (log.isTraceEnabled()) {
            log.trace("Executing unsubscribe [{}] [{}]", clientId, topicFilters);
        }
        for (String topicFilter : topicFilters) {
            boolean successfullyDeleted = subscriptionTrie.delete(topicFilter, val -> clientId.equals(val.getClientId()));
            if (!successfullyDeleted) {
                if (log.isDebugEnabled()) {
                    log.debug("[{}] Client wasn't subscribed to the topic filter {}", clientId, topicFilter);
                }
            }
        }
    }

    @Override
    public List<ValueWithTopicFilter<EntitySubscription>> getSubscriptions(String topic) {
        long startTime = System.nanoTime();
        List<ValueWithTopicFilter<EntitySubscription>> subscriptions = subscriptionTrie.get(topic);
        subscriptionTimerStats.logSubscriptionsLookup(startTime, TimeUnit.NANOSECONDS);
        return subscriptions;
    }

    @Override
    public void clearEmptyTopicNodes() throws SubscriptionTrieClearException {
        if (log.isTraceEnabled()) {
            log.trace("Executing clearEmptyTopicNodes");
        }
        subscriptionTrie.clearEmptyNodes();
    }

    @Scheduled(cron = "${mqtt.subscription-trie.clear-nodes-cron}", zone = "${mqtt.subscription-trie.clear-nodes-zone}")
    public void scheduleEmptyNodeClear() {
        log.info("Start clearing empty nodes in SubscriptionTrie");
        try {
            subscriptionTrie.clearEmptyNodes();
        } catch (SubscriptionTrieClearException e) {
            log.error("Failed to clear empty nodes.", e);
        }
    }
}
