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

import com.google.common.util.concurrent.ListenableFuture;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;

import java.util.List;

public interface SubscriptionService {
    ListenableFuture<Void> subscribe(String clientId, List<MqttTopicSubscription> topicSubscriptions);

    ListenableFuture<Void> unsubscribe(String clientId, List<String> topics);
}
