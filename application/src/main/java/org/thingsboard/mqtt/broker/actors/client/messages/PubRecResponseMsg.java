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
package org.thingsboard.mqtt.broker.actors.client.messages;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.actors.client.state.MqttMsgWrapper;
import org.thingsboard.mqtt.broker.actors.msg.MsgType;

import java.util.UUID;

@Slf4j
@Getter
public class PubRecResponseMsg extends SessionDependentMsg {

    private final MqttMsgWrapper mqttMsgWrapper;

    public PubRecResponseMsg(UUID sessionId, MqttMsgWrapper mqttMsgWrapper) {
        super(sessionId);
        this.mqttMsgWrapper = mqttMsgWrapper;
    }

    @Override
    public MsgType getMsgType() {
        return MsgType.PUBREC_RESPONSE_MSG;
    }

    public int getMessageId() {
        return mqttMsgWrapper.getMsgId();
    }
}
