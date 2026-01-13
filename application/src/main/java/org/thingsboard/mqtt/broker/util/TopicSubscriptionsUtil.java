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
package org.thingsboard.mqtt.broker.util;

import lombok.Data;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class TopicSubscriptionsUtil {

    public static SubscriptionsUpdate getSubscriptionsUpdate(Set<TopicSubscription> currentSubscriptions, Set<TopicSubscription> newSubscriptions) {
        Set<TopicSubscription> toUnsubscribe = new HashSet<>(currentSubscriptions);
        Set<TopicSubscription> toSubscribe = new HashSet<>(newSubscriptions);

        toUnsubscribe.removeAll(newSubscriptions);

        return new SubscriptionsUpdate(toSubscribe, toUnsubscribe);
    }

    public static Set<String> getUnsubscribeTopics(Set<TopicSubscription> removedSubscriptions) {
        return removedSubscriptions.stream()
                .map(TopicSubscription::getTopicFilter)
                .collect(Collectors.toSet());
    }

    @Data
    public static class SubscriptionsUpdate {
        private final Set<TopicSubscription> toSubscribe;
        private final Set<TopicSubscription> toUnsubscribe;
    }
}
