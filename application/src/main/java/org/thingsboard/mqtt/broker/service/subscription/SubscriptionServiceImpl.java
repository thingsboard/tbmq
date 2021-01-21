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

import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.exception.SubscriptionTrieClearException;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.session.SessionListener;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SubscriptionServiceImpl implements SubscriptionService {
    // TODO probably move to some SessionService
    private final Map<UUID, SessionSubscriptionInfo> sessions = new ConcurrentHashMap<>();

    private final SubscriptionTrie<TopicSubscription> subscriptionTrie;
    private final AtomicInteger sessionsCount;

    public SubscriptionServiceImpl(SubscriptionTrie<TopicSubscription> subscriptionTrie, StatsManager statsManager) {
        this.subscriptionTrie = subscriptionTrie;
        this.sessionsCount = statsManager.createSessionsCounter();
    }

    @Override
    public void subscribe(UUID sessionId, List<MqttTopicSubscription> topicSubscriptions, SessionListener listener) {
        log.trace("Executing subscribe [{}] [{}]", sessionId, topicSubscriptions);
        SessionSubscriptionInfo sessionSubscriptionInfo = sessions.computeIfAbsent(sessionId, uuid -> {
            sessionsCount.incrementAndGet();
            return new SessionSubscriptionInfo(listener);
        });
        for (MqttTopicSubscription topicSubscription : topicSubscriptions) {
            subscriptionTrie.put(topicSubscription.topicName(), new TopicSubscription(sessionId, topicSubscription.qualityOfService()));
            sessionSubscriptionInfo.getTopicFilters().add(topicSubscription.topicName());
        }
    }

    @Override
    public void unsubscribe(UUID sessionId, List<String> topics) {
        log.trace("Executing unsubscribe [{}] [{}]", sessionId, topics);
        SessionSubscriptionInfo sessionSubscriptionInfo = sessions.get(sessionId);
        if (sessionSubscriptionInfo == null) {
            throw new RuntimeException("Cannot find session subscription info.");
        }
        for (String topic : topics) {
            subscriptionTrie.delete(topic, val -> sessionId.equals(val.getSessionId()));
            sessionSubscriptionInfo.getTopicFilters().remove(topic);
        }
    }

    @Override
    public void unsubscribe(UUID sessionId) {
        log.trace("Executing unsubscribe [{}]", sessionId);
        //TODO: make this transactional?
        SessionSubscriptionInfo sessionSubscriptionInfo = sessions.get(sessionId);
        if (sessionSubscriptionInfo == null) {
            return;
        }
        for (String topicFilter : sessionSubscriptionInfo.getTopicFilters()) {
            boolean successfullyDeleted = subscriptionTrie.delete(topicFilter, val -> sessionId.equals(val.getSessionId()));
            if (!successfullyDeleted) {
                log.error("[{}] Couldn't delete subscription for topic {}", sessionId, topicFilter);
            }
        }
        sessions.remove(sessionId);
        sessionsCount.decrementAndGet();
    }

    @Override
    public Collection<Subscription> getSubscriptions(String topic) {
        List<TopicSubscription> topicSubscriptions = subscriptionTrie.get(topic);
        return topicSubscriptions.stream()
                .map(topicSubscription -> new Subscription(topicSubscription.getMqttQoS(), sessions.get(topicSubscription.getSessionId()).getListener()))
                .collect(Collectors.toList());
    }

    @Override
    public void clearEmptyTopicNodes() throws SubscriptionTrieClearException {
        log.trace("Executing clearEmptyTopicNodes");
        subscriptionTrie.clearEmptyNodes();
    }

    @Scheduled(cron = "${application.mqtt.subscription-trie.clear-nodes-cron}", zone = "${application.mqtt.subscription-trie.clear-nodes-zone}")
    private void scheduleEmptyNodeClear() {
        log.info("Start clearing empty nodes in SubscriptionTrie");
        try {
            subscriptionTrie.clearEmptyNodes();
        } catch (SubscriptionTrieClearException e) {
            log.error("Failed to clear empty nodes. Reason - {}.", e.getMessage());
        }
    }

}
