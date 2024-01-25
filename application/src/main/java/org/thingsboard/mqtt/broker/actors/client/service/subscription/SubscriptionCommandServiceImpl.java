/**
 * Copyright © 2016-2023 The Thingsboard Authors
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

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;

import java.util.Collection;

@Slf4j
@Service
@AllArgsConstructor
public class SubscriptionCommandServiceImpl implements SubscriptionCommandService {

    private final ClientSubscriptionService clientSubscriptionService;

    @Override
    public void subscribe(String clientId, Collection<TopicSubscription> topicSubscriptions) {
        if (log.isDebugEnabled()) {
            log.debug("[{}] Subscribing to {}.", clientId, topicSubscriptions);
        }
        clientSubscriptionService.subscribeAndPersist(clientId, topicSubscriptions);
    }

    @Override
    public void unsubscribe(String clientId, Collection<String> topics) {
        if (log.isDebugEnabled()) {
            log.debug("[{}] Unsubscribing from {}.", clientId, topics);
        }
        clientSubscriptionService.unsubscribeAndPersist(clientId, topics);
    }
}
