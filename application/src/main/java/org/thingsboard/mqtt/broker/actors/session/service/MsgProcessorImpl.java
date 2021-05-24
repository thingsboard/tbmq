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
package org.thingsboard.mqtt.broker.actors.session.service;

import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.util.ReferenceCountUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.session.state.ClientSessionActorStateReader;
import org.thingsboard.mqtt.broker.actors.session.state.ClientSessionActorStateSessionUpdater;
import org.thingsboard.mqtt.broker.actors.session.state.SessionState;
import org.thingsboard.mqtt.broker.actors.session.messages.IncomingMqttMsg;
import org.thingsboard.mqtt.broker.actors.session.util.ClientActorUtil;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.exception.ProtocolViolationException;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.client.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.service.mqtt.client.connect.ConnectService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.PersistenceSessionClearer;
import org.thingsboard.mqtt.broker.service.mqtt.will.LastWillService;
import org.thingsboard.mqtt.broker.session.ClientSessionActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_ACCEPTED;

@Slf4j
@Service
@RequiredArgsConstructor
public class MsgProcessorImpl implements MsgProcessor {
    private final ConnectService connectService;
    private final MqttMessageHandler messageHandler;
    private final ClientSessionCtxService clientSessionCtxService;
    private final LastWillService lastWillService;
    private final PersistenceSessionClearer persistenceSessionClearer;
    private final MqttMessageGenerator mqttMessageGenerator;
    private final MsgPersistenceManager msgPersistenceManager;
    private final ClientSessionActorManager clientSessionActorManager;

    @Override
    public void process(ClientSessionActorStateSessionUpdater actorState, IncomingMqttMsg incomingMqttMsg) {
        boolean messageNeedsToBeReleased = true;
        try {
            MqttMessageType msgType = incomingMqttMsg.getMsg().fixedHeader().messageType();
            boolean isSessionValid = ClientActorUtil.validateAndLogSession(actorState, incomingMqttMsg);
            if (!isSessionValid) {
                return;
            }
            if (actorState.getCurrentSessionState() == SessionState.DISCONNECTED) {
                log.debug("[{}][{}] Session is in {} state, ignoring message, msg type - {}.",
                        actorState.getClientId(), actorState.getCurrentSessionId(), SessionState.DISCONNECTED, msgType);
                return;
            }
            messageNeedsToBeReleased = handleMessage(actorState, incomingMqttMsg.getMsg());
        } finally {
            if (messageNeedsToBeReleased) {
                ReferenceCountUtil.safeRelease(incomingMqttMsg.getMsg());
            }
        }
    }

    @Override
    public void processConnectionAccepted(ClientSessionActorStateReader actorState, boolean isPrevSessionPersistent, PublishMsg lastWillMsg) {
        ClientSessionCtx sessionCtx = actorState.getCurrentSessionCtx();
        SessionInfo sessionInfo = sessionCtx.getSessionInfo();

        if (lastWillMsg != null) {
            lastWillService.saveLastWillMsg(sessionInfo, lastWillMsg);
        }

        boolean isCurrentSessionPersistent = sessionInfo.isPersistent();
        if (isPrevSessionPersistent && !isCurrentSessionPersistent) {
            persistenceSessionClearer.clearPersistedSession(sessionInfo.getClientInfo());
        }

        sessionCtx.getChannel().writeAndFlush(mqttMessageGenerator.createMqttConnAckMsg(CONNECTION_ACCEPTED, isPrevSessionPersistent && isCurrentSessionPersistent));
        log.info("[{}] [{}] Client connected!", actorState.getClientId(), actorState.getCurrentSessionId());

        clientSessionCtxService.registerSession(sessionCtx);

        if (sessionCtx.getSessionInfo().isPersistent()) {
            msgPersistenceManager.processPersistedMessages(actorState);
        }
        clientSessionActorManager.processConnectionFinished(sessionCtx.getClientId(), sessionCtx.getSessionId());
    }

    @Override
    public void processConnectionFinished(ClientSessionActorStateReader actorState) {
        ClientSessionCtx sessionCtx = actorState.getCurrentSessionCtx();
        actorState.getQueuedMessages().process(msg -> messageHandler.process(sessionCtx, msg));
    }

    private boolean handleMessage(ClientSessionActorStateSessionUpdater actorState, MqttMessage msg) {
        MqttMessageType msgType = msg.fixedHeader().messageType();

        validateState(msgType, actorState.getCurrentSessionState());

        boolean messageNeedsToBeReleased = true;
        ClientSessionCtx sessionCtx = actorState.getCurrentSessionCtx();
        switch (msgType) {
            case CONNECT:
                actorState.updateSessionState(SessionState.CONNECTING);
                connectService.startConnection(actorState.getClientId(), sessionCtx, (MqttConnectMessage) msg);
                break;
            case DISCONNECT:
                actorState.getDisconnectListener().disconnect(new DisconnectReason(DisconnectReasonType.ON_DISCONNECT_MSG));
                break;
            default:
                if (actorState.getCurrentSessionState() == SessionState.CONNECTING) {
                    actorState.getQueuedMessages().add(msg);
                    messageNeedsToBeReleased = false;
                } else {
                    messageHandler.process(sessionCtx, msg);
                }
                break;
        }

        return messageNeedsToBeReleased;
    }

    private void validateState(MqttMessageType msgType, SessionState currentSessionState) {
        // TODO: extract these rules to Enum (or something else)
        if (msgType == MqttMessageType.CONNECT && currentSessionState != SessionState.INITIALIZED) {
            throw new ProtocolViolationException("Session should be in the " + SessionState.INITIALIZED + " state to process " +
                    MqttMessageType.CONNECT + " message");
        }
        if (currentSessionState == SessionState.INITIALIZED && msgType != MqttMessageType.CONNECT) {
            throw new ProtocolViolationException("Session is in the " + SessionState.INITIALIZED + " state. It can process only " +
                    MqttMessageType.CONNECT + " message");
        }
        if (currentSessionState == SessionState.DISCONNECTING && msgType != MqttMessageType.DISCONNECT) {
            throw new ProtocolViolationException("Session is in " + SessionState.DISCONNECTING + " state. It can process only " +
                    MqttMessageType.DISCONNECT + " message");
        }
    }
}
