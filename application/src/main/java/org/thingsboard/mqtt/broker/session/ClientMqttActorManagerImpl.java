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
package org.thingsboard.mqtt.broker.session;

import io.netty.handler.codec.mqtt.MqttMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.ActorSystemContext;
import org.thingsboard.mqtt.broker.actors.TbActorRef;
import org.thingsboard.mqtt.broker.actors.TbActorSystem;
import org.thingsboard.mqtt.broker.actors.TbTypeActorId;
import org.thingsboard.mqtt.broker.actors.config.ActorSystemLifecycle;
import org.thingsboard.mqtt.broker.actors.client.ClientActorCreator;
import org.thingsboard.mqtt.broker.actors.client.messages.ConnectionAcceptedMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.ConnectionFinishedMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.DisconnectMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.IncomingMqttMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.SessionInitMsg;
import org.thingsboard.mqtt.broker.common.data.id.ActorType;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;

import java.util.UUID;

@Slf4j
@Service
public class ClientMqttActorManagerImpl implements ClientMqttActorManager {
    private ActorSystemContext actorSystemContext;
    private final TbActorSystem actorSystem;

    public ClientMqttActorManagerImpl(@Lazy ActorSystemContext actorSystemContext, TbActorSystem actorSystem) {
        this.actorSystemContext = actorSystemContext;
        this.actorSystem = actorSystem;
    }

    @Override
    public void initSession(String clientId, boolean isClientIdGenerated, ClientSessionCtx clientSessionCtx) {
        TbActorRef clientActorRef = actorSystem.getActor(new TbTypeActorId(ActorType.CLIENT, clientId));
        if (clientActorRef == null) {
            clientActorRef = actorSystem.createRootActor(ActorSystemLifecycle.CLIENT_DISPATCHER_NAME,
                    new ClientActorCreator(actorSystemContext, clientId, isClientIdGenerated));
        }
        clientActorRef.tellWithHighPriority(new SessionInitMsg(clientSessionCtx));
    }

    @Override
    public void disconnect(String clientId, UUID sessionId, DisconnectReason reason) {
        TbActorRef clientActorRef = actorSystem.getActor(new TbTypeActorId(ActorType.CLIENT, clientId));
        if (clientActorRef == null) {
            log.warn("[{}] Cannot find client actor for disconnect, sessionId - {}.", clientId, sessionId);
        } else {
            clientActorRef.tellWithHighPriority(new DisconnectMsg(sessionId, reason));
        }
    }

    @Override
    public void processMqttMsg(String clientId, UUID sessionId, MqttMessage msg) {
        TbActorRef clientActorRef = actorSystem.getActor(new TbTypeActorId(ActorType.CLIENT, clientId));
        if (clientActorRef == null) {
            log.warn("[{}] Cannot find client actor to process MQTT message.", clientId);
        } else {
            clientActorRef.tell(new IncomingMqttMsg(sessionId, msg));
        }
    }

    @Override
    public void processConnectionAccepted(String clientId, UUID sessionId, boolean isPrevSessionPersistent, PublishMsg lastWillMsg) {
        TbActorRef clientActorRef = actorSystem.getActor(new TbTypeActorId(ActorType.CLIENT, clientId));
        if (clientActorRef == null) {
            log.warn("[{}] Cannot find client actor to process connection accepted.", clientId);
        } else {
            clientActorRef.tell(new ConnectionAcceptedMsg(sessionId, isPrevSessionPersistent, lastWillMsg));
        }
    }

    // TODO: group in one method
    @Override
    public void processConnectionFinished(String clientId, UUID sessionId) {
        TbActorRef clientActorRef = actorSystem.getActor(new TbTypeActorId(ActorType.CLIENT, clientId));
        if (clientActorRef == null) {
            log.warn("[{}] Cannot find client actor to process connection finished.", clientId);
        } else {
            clientActorRef.tell(new ConnectionFinishedMsg(sessionId));
        }
    }
}
