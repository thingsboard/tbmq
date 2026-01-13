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
import io.netty.handler.codec.mqtt.MqttReasonCodes;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.actors.msg.MsgType;

import java.util.UUID;

@Slf4j
@Getter
public class MqttPubRelMsg extends QueueableMqttMsg {

    private final int messageId;
    private final MqttProperties properties;
    private final MqttReasonCodes.PubRel reasonCode;

    public MqttPubRelMsg(UUID sessionId, int messageId, MqttProperties properties, MqttReasonCodes.PubRel reasonCode) {
        super(sessionId);
        this.messageId = messageId;
        this.properties = properties;
        this.reasonCode = reasonCode;
    }

    @Override
    public MsgType getMsgType() {
        return MsgType.MQTT_PUBREL_MSG;
    }
}
