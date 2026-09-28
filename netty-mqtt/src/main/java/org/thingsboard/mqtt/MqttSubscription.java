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
package org.thingsboard.mqtt;

import lombok.AccessLevel;
import lombok.Getter;

/**
 * A subscription is identified by its raw topic filter string together with handler equality: two subscriptions are
 * the same when both are equal. Handler identity semantics are therefore the caller's to decide - an
 * {@link MqttHandler} with value-based equality (a record, say) makes two genuinely distinct handlers collapse into
 * a single registry entry.
 */
final class MqttSubscription {

    @Getter(AccessLevel.PACKAGE)
    private final String topic;
    private final MqttTopicFilter filter;
    @Getter
    private final MqttHandler handler;

    MqttSubscription(String topic, MqttHandler handler) {
        if (topic == null) {
            throw new NullPointerException("topic");
        }
        if (handler == null) {
            throw new NullPointerException("handler");
        }
        this.topic = topic;
        this.handler = handler;
        this.filter = MqttTopicFilter.of(topic);
    }

    /**
     * {@code topicLevels} must be {@link MqttTopicFilter#split(String)} of the very same {@code topic}.
     */
    boolean matches(String topic, String[] topicLevels) {
        return this.filter.matches(topic, topicLevels);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MqttSubscription that = (MqttSubscription) o;
        return topic.equals(that.topic) && handler.equals(that.handler);
    }

    @Override
    public int hashCode() {
        return 31 * topic.hashCode() + handler.hashCode();
    }
}
