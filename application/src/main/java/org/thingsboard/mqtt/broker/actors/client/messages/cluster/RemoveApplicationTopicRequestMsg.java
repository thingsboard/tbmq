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
package org.thingsboard.mqtt.broker.actors.client.messages.cluster;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.actors.TbActorId;
import org.thingsboard.mqtt.broker.actors.client.messages.CallbackMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.ClientCallback;
import org.thingsboard.mqtt.broker.actors.msg.MsgType;

@Slf4j
@Getter
public class RemoveApplicationTopicRequestMsg extends CallbackMsg implements SessionClusterManagementMsg {
    public RemoveApplicationTopicRequestMsg(ClientCallback callback) {
        super(callback);
    }

    @Override
    public MsgType getMsgType() {
        return MsgType.REMOVE_APPLICATION_TOPIC_REQUEST_MSG;
    }

    @Override
    public void onTbActorStopped(TbActorId actorId) {
        log.debug("[{}] Actor was stopped before processing {}.", actorId, getMsgType());
    }
}
