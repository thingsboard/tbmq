/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.common.data.subscription;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.Objects;

@AllArgsConstructor
@Getter
@Builder(toBuilder = true)
@ToString
public class TopicSubscription {

    private final String topicFilter;
    private final int qos;
    private final String shareName;
    private final SubscriptionOptions options;

    public TopicSubscription(String topicFilter, int qos) {
        this(topicFilter, qos, null, SubscriptionOptions.newInstance());
    }

    public TopicSubscription(String topicFilter, int qos, String shareName) {
        this(topicFilter, qos, shareName, SubscriptionOptions.newInstance());
    }

    public TopicSubscription(String topicFilter, int qos, SubscriptionOptions options) {
        this(topicFilter, qos, null, options);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TopicSubscription that = (TopicSubscription) o;
        return Objects.equals(topicFilter, that.topicFilter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(topicFilter);
    }

    public boolean isSharedSubscription() {
        return shareName != null;
    }
}
