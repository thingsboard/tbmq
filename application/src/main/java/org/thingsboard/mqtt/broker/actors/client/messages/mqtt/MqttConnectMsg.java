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
import org.thingsboard.mqtt.broker.actors.client.messages.SessionDependentMsg;
import org.thingsboard.mqtt.broker.actors.msg.MsgType;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;

import java.util.UUID;

@Slf4j
@Getter
public class MqttConnectMsg extends SessionDependentMsg {

    private final String clientIdentifier;
    private final boolean cleanStart;
    private final int keepAliveTimeSeconds;
    private final PublishMsg lastWillMsg;
    private final MqttProperties properties;

    public MqttConnectMsg(UUID sessionId, String clientIdentifier, boolean cleanStart, int keepAliveTimeSeconds,
                          PublishMsg lastWillMsg, MqttProperties properties) {
        super(sessionId);
        this.clientIdentifier = clientIdentifier;
        this.cleanStart = cleanStart;
        this.keepAliveTimeSeconds = keepAliveTimeSeconds;
        this.lastWillMsg = lastWillMsg;
        this.properties = properties;
    }

    public MqttConnectMsg(UUID sessionId, String clientIdentifier, boolean cleanStart, int keepAliveTimeSeconds,
                          PublishMsg lastWillMsg) {
        this(sessionId, clientIdentifier, cleanStart, keepAliveTimeSeconds, lastWillMsg, MqttProperties.NO_PROPERTIES);
    }

    @Override
    public MsgType getMsgType() {
        return MsgType.MQTT_CONNECT_MSG;
    }
}
