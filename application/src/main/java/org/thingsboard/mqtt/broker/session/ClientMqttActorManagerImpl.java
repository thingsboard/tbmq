/**
 * Copyright © 2016-2024 The Thingsboard Authors
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

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.ActorSystemContext;
import org.thingsboard.mqtt.broker.actors.TbActorRef;
import org.thingsboard.mqtt.broker.actors.TbActorSystem;
import org.thingsboard.mqtt.broker.actors.TbTypeActorId;
import org.thingsboard.mqtt.broker.actors.client.ClientActorCreator;
import org.thingsboard.mqtt.broker.actors.client.messages.ConnectionAcceptedMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.SessionInitMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.SubscribeCommandMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.UnsubscribeCommandMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttConnectMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.QueueableMqttMsg;
import org.thingsboard.mqtt.broker.actors.config.ActorSystemLifecycle;
import org.thingsboard.mqtt.broker.common.data.id.ActorType;

@Slf4j
@Service
public class ClientMqttActorManagerImpl implements ClientMqttActorManager {

    private final ActorSystemContext actorSystemContext;
    private final TbActorSystem actorSystem;

    public ClientMqttActorManagerImpl(@Lazy ActorSystemContext actorSystemContext, TbActorSystem actorSystem) {
        this.actorSystemContext = actorSystemContext;
        this.actorSystem = actorSystem;
    }

    @Override
    public void initSession(String clientId, boolean isClientIdGenerated, SessionInitMsg sessionInitMsg) {
        TbActorRef clientActorRef = getActor(clientId);
        if (clientActorRef == null) {
            clientActorRef = createRootActor(clientId, isClientIdGenerated);
        }
        clientActorRef.tellWithHighPriority(sessionInitMsg);
    }

    @Override
    public void disconnect(String clientId, MqttDisconnectMsg disconnectMsg) {
        TbActorRef clientActorRef = getActor(clientId);
        if (clientActorRef == null) {
            if (log.isDebugEnabled()) {
                log.debug("[{}] Cannot find client actor for disconnect, sessionId - {}.", clientId, disconnectMsg.getSessionId());
            }
        } else {
            clientActorRef.tellWithHighPriority(disconnectMsg);
        }
    }

    @Override
    public void connect(String clientId, MqttConnectMsg connectMsg) {
        TbActorRef clientActorRef = getActor(clientId);
        if (clientActorRef == null) {
            if (log.isDebugEnabled()) {
                log.debug("[{}] Cannot find client actor for connect, sessionId - {}.", clientId, connectMsg.getSessionId());
            }
        } else {
            clientActorRef.tell(connectMsg);
        }
    }

    @Override
    public void processMqttMsg(String clientId, QueueableMqttMsg mqttMsg) {
        TbActorRef clientActorRef = getActor(clientId);
        if (clientActorRef == null) {
            log.warn("[{}] Cannot find client actor to process MQTT message, sessionId - {}, msgType - {}.", clientId, mqttMsg.getSessionId(), mqttMsg.getMsgType());
        } else {
            clientActorRef.tell(mqttMsg);
        }
    }

    @Override
    public void notifyConnectionAccepted(String clientId, ConnectionAcceptedMsg connectionAcceptedMsg) {
        TbActorRef clientActorRef = getActor(clientId);
        if (clientActorRef == null) {
            log.warn("[{}] Cannot find client actor to process connection accepted, sessionId - {}.", clientId, connectionAcceptedMsg.getSessionId());
        } else {
            clientActorRef.tell(connectionAcceptedMsg);
        }
    }

    @Override
    public void subscribe(String clientId, SubscribeCommandMsg subscribeCommandMsg) {
        TbActorRef clientActorRef = getActor(clientId);
        if (clientActorRef == null) {
            clientActorRef = createRootActor(clientId, true);
        }
        clientActorRef.tellWithHighPriority(subscribeCommandMsg);
    }

    @Override
    public void unsubscribe(String clientId, UnsubscribeCommandMsg unsubscribeCommandMsg) {
        TbActorRef clientActorRef = getActor(clientId);
        if (clientActorRef == null) {
            clientActorRef = createRootActor(clientId, true);
        }
        clientActorRef.tellWithHighPriority(unsubscribeCommandMsg);
    }

    private TbActorRef createRootActor(String clientId, boolean isClientIdGenerated) {
        return actorSystem.createRootActor(ActorSystemLifecycle.CLIENT_DISPATCHER_NAME,
                new ClientActorCreator(actorSystemContext, clientId, isClientIdGenerated));
    }

    private TbActorRef getActor(String clientId) {
        return actorSystem.getActor(new TbTypeActorId(ActorType.CLIENT, clientId));
    }
}
