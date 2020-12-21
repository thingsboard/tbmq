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
package org.thingsboard.mqtt.broker.sevice.subscription;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.session.SessionListener;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Service
public class SubscriptionServiceImpl implements SubscriptionService {
    private final TopicTrie<TopicSubscription> topicTrie = new ConcurrentMapTopicTrie<>();
    private final Map<UUID, SessionSubscriptionInfo> sessions = new ConcurrentHashMap<>();

    @Override
    public ListenableFuture<Void> subscribe(UUID sessionId, List<MqttTopicSubscription> topicSubscriptions, SessionListener listener) {
        SessionSubscriptionInfo sessionSubscriptionInfo = sessions.computeIfAbsent(sessionId, uuid -> new SessionSubscriptionInfo(listener));
        for (MqttTopicSubscription topicSubscription : topicSubscriptions) {
            topicTrie.put(topicSubscription.topicName(), new TopicSubscription(sessionId, topicSubscription.qualityOfService()));
            sessionSubscriptionInfo.getTopicFilters().add(topicSubscription.topicName());
        }
        return Futures.immediateFuture(null);
    }

    @Override
    public ListenableFuture<Void> unsubscribe(UUID sessionId, List<String> topics) {
        SessionSubscriptionInfo sessionSubscriptionInfo = sessions.get(sessionId);
        if (sessionSubscriptionInfo == null) {
            throw new RuntimeException("Cannot find session subscription info.");
        }
        for (String topic : topics) {
            topicTrie.delete(topic, val -> sessionId.equals(val.getSessionId()));
            sessionSubscriptionInfo.getTopicFilters().remove(topic);
        }
        return Futures.immediateFuture(null);
    }

    @Override
    public void unsubscribe(UUID sessionId) {
        //TODO: make this transactional?
        SessionSubscriptionInfo sessionSubscriptionInfo = sessions.get(sessionId);
        if (sessionSubscriptionInfo == null) {
            return;
        }
        for (String topicFilter : sessionSubscriptionInfo.getTopicFilters()) {
            topicTrie.delete(topicFilter, val -> sessionId.equals(val.getSessionId()));
        }
        sessions.remove(sessionId);
    }

    @Override
    public Collection<Subscription> getSubscriptions(String topic) {
        List<TopicSubscription> topicSubscriptions = topicTrie.get(topic);
        return topicSubscriptions.stream()
                .map(topicSubscription -> new Subscription(topicSubscription.getMqttQoS(), sessions.get(topicSubscription.getSessionId()).getListener()))
                .collect(Collectors.toList());
    }
}
