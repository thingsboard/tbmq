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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Service
public class SubscriptionServiceImpl implements SubscriptionService {
    private final ConcurrentMap<UUID, ConcurrentMap<String, Subscription>> subscriptions = new ConcurrentHashMap<>();

    @Override
    public ListenableFuture<Void> subscribe(UUID sessionId, List<MqttTopicSubscription> topicSubscriptions, SessionListener listener) {
        for (MqttTopicSubscription topicSubscription : topicSubscriptions) {
            subscriptions.computeIfAbsent(sessionId, uuid -> new ConcurrentHashMap<>())
                    .put(topicSubscription.topicName(), new Subscription(topicSubscription.qualityOfService(), listener));
        }
        return Futures.immediateFuture(null);
    }

    @Override
    public ListenableFuture<Void> unsubscribe(UUID sessionId, List<String> topics) {
        ConcurrentMap<String, Subscription> subscriptionsByTopic = subscriptions.get(sessionId);
        for (String topic : topics) {
            subscriptionsByTopic.remove(topic);
        }
        return Futures.immediateFuture(null);
    }

    @Override
    public Collection<Subscription> getSubscriptions(String topic) {
        return subscriptions.values().stream().map(subscriptionsByTopic -> subscriptionsByTopic.get(topic)).collect(Collectors.toList());
    }
}
