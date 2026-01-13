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
package org.thingsboard.mqtt.broker.actors.client.messages.mqtt;

import io.netty.handler.codec.mqtt.MqttProperties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.actors.msg.MsgType;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;

import java.util.List;
import java.util.UUID;

@Slf4j
@Getter
public class MqttSubscribeMsg extends QueueableMqttMsg {

    private final int messageId;
    private final List<TopicSubscription> topicSubscriptions;
    private final MqttProperties properties;

    public MqttSubscribeMsg(UUID sessionId, int messageId, List<TopicSubscription> topicSubscriptions) {
        this(sessionId, messageId, topicSubscriptions, MqttProperties.NO_PROPERTIES);
    }

    public MqttSubscribeMsg(UUID sessionId, int messageId, List<TopicSubscription> topicSubscriptions, MqttProperties properties) {
        super(sessionId);
        this.messageId = messageId;
        this.topicSubscriptions = topicSubscriptions;
        this.properties = properties;
    }

    @Override
    public MsgType getMsgType() {
        return MsgType.MQTT_SUBSCRIBE_MSG;
    }
}
