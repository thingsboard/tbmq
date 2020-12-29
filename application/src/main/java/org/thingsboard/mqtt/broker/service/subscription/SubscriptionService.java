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

import com.google.common.util.concurrent.ListenableFuture;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import org.thingsboard.mqtt.broker.session.SessionListener;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SubscriptionService {
    ListenableFuture<Void> subscribe(UUID sessionId, List<MqttTopicSubscription> topicSubscriptions, SessionListener listener);

    ListenableFuture<Void> unsubscribe(UUID sessionId, List<String> topics);

    void unsubscribe(UUID sessionId);

    Collection<Subscription> getSubscriptions(String topic);
}
