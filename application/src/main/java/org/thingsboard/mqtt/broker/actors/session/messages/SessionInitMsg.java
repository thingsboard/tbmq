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
package org.thingsboard.mqtt.broker.actors.session.messages;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.actors.TbActorId;
import org.thingsboard.mqtt.broker.actors.msg.MsgType;
import org.thingsboard.mqtt.broker.actors.msg.TbActorMsg;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

@Slf4j
@Getter
@RequiredArgsConstructor
public class SessionInitMsg implements TbActorMsg {
    private final ClientSessionCtx clientSessionCtx;

    @Override
    public MsgType getMsgType() {
        return MsgType.SESSION_INIT_MSG;
    }

    @Override
    public void onTbActorStopped(TbActorId actorId) {
        log.warn("[{}] Actor was stopped before processing {}, sessionId - {}.", actorId, getMsgType(), clientSessionCtx.getSessionId());
    }
}
