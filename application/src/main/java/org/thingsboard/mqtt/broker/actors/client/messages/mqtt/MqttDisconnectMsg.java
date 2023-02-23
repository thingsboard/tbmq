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
package org.thingsboard.mqtt.broker.actors.client.messages.mqtt;

import io.netty.handler.codec.mqtt.MqttProperties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.actors.client.messages.SessionDependentMsg;
import org.thingsboard.mqtt.broker.actors.msg.MsgType;
import org.thingsboard.mqtt.broker.session.DisconnectReason;

import java.util.UUID;

@Slf4j
@Getter
public class MqttDisconnectMsg extends SessionDependentMsg {

    private final DisconnectReason reason;
    private final boolean newSessionCleanStart;
    private final MqttProperties properties;

    public MqttDisconnectMsg(UUID sessionId, DisconnectReason reason) {
        this(sessionId, reason, true);
    }

    public MqttDisconnectMsg(UUID sessionId, DisconnectReason reason, boolean newSessionCleanStart) {
        this(sessionId, reason, newSessionCleanStart, MqttProperties.NO_PROPERTIES);
    }

    public MqttDisconnectMsg(UUID sessionId, DisconnectReason reason, MqttProperties properties) {
        this(sessionId, reason, true, properties);
    }

    public MqttDisconnectMsg(UUID sessionId, DisconnectReason reason, boolean newSessionCleanStart, MqttProperties properties) {
        super(sessionId);
        this.reason = reason;
        this.newSessionCleanStart = newSessionCleanStart;
        this.properties = properties;
    }

    @Override
    public MsgType getMsgType() {
        return MsgType.DISCONNECT_MSG;
    }
}
