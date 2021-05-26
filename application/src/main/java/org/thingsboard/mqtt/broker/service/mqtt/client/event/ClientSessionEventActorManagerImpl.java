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
package org.thingsboard.mqtt.broker.service.mqtt.client.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.ActorSystemContext;
import org.thingsboard.mqtt.broker.actors.TbActorRef;
import org.thingsboard.mqtt.broker.actors.TbActorSystem;
import org.thingsboard.mqtt.broker.actors.TbTypeActorId;
import org.thingsboard.mqtt.broker.actors.config.ActorSystemLifecycle;
import org.thingsboard.mqtt.broker.actors.session.ClientSessionActorCreator;
import org.thingsboard.mqtt.broker.actors.session.messages.CallbackMsg;
import org.thingsboard.mqtt.broker.common.data.id.ActorType;

@Slf4j
@Service
public class ClientSessionEventActorManagerImpl implements ClientSessionEventActorManager {
    private ActorSystemContext actorSystemContext;
    private final TbActorSystem actorSystem;

    public ClientSessionEventActorManagerImpl(@Lazy ActorSystemContext actorSystemContext, TbActorSystem actorSystem) {
        this.actorSystemContext = actorSystemContext;
        this.actorSystem = actorSystem;
    }


    @Override
    public void sendCallbackMsg(String clientId, CallbackMsg callbackMsg) {
        TbActorRef clientActorRef = actorSystem.getActor(new TbTypeActorId(ActorType.CLIENT_SESSION, clientId));
        if (clientActorRef == null) {
            clientActorRef = actorSystem.createRootActor(ActorSystemLifecycle.CLIENT_SESSION_DISPATCHER_NAME,
                    // TODO: pass correct 'isClientIdGenerated' here
                    new ClientSessionActorCreator(actorSystemContext, clientId, true));
        }
        clientActorRef.tellWithHighPriority(callbackMsg);
    }
}
