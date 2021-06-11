/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
import org.thingsboard.mqtt.broker.actors.msg.MsgType;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;

import java.util.UUID;

@Slf4j
public class ConnectionAcceptedMsg extends SessionDependentMsg {
    private final boolean wasPrevSessionPersistent;
    @Getter
    private final PublishMsg lastWillMsg;

    public ConnectionAcceptedMsg(UUID sessionId, boolean wasPrevSessionPersistent, PublishMsg lastWillMsg) {
        super(sessionId);
        this.wasPrevSessionPersistent = wasPrevSessionPersistent;
        this.lastWillMsg = lastWillMsg;
    }

    public boolean wasPrevSessionPersistent() {
        return wasPrevSessionPersistent;
    }

    @Override
    public MsgType getMsgType() {
        return MsgType.CONNECTION_ACCEPTED_MSG;
    }
}
