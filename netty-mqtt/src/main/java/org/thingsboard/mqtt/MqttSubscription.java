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

import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.AccessLevel;
import lombok.Getter;

/**
 * A registered topic filter and its handler. The registry keys entries by the raw topic filter string alone - a filter
 * has at most one handler (see {@link MqttClientImpl#register(MqttSubscription)}). Handler equality only matters to
 * {@link MqttClient#off(String, MqttHandler)}, which removes the filter's entry when its handler is equal to the given one.
 */
final class MqttSubscription {

    @Getter(AccessLevel.PACKAGE)
    private final String topic;
    private final MqttTopicFilter filter;
    @Getter
    private final MqttHandler handler;

    /** The QoS the caller's on() asked for, which a resubscribe asks for again. */
    @Getter(AccessLevel.PACKAGE)
    private final MqttQoS requestedQos;

    MqttSubscription(String topic, MqttHandler handler, MqttQoS requestedQos) {
        if (topic == null) {
            throw new NullPointerException("topic");
        }
        if (handler == null) {
            throw new NullPointerException("handler");
        }
        if (requestedQos == null) {
            throw new NullPointerException("requestedQos");
        }
        this.topic = topic;
        this.handler = handler;
        this.requestedQos = requestedQos;
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
