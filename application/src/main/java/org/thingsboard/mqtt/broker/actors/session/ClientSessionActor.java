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
package org.thingsboard.mqtt.broker.actors.session;

import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.actors.ActorSystemContext;
import org.thingsboard.mqtt.broker.actors.TbActorCtx;
import org.thingsboard.mqtt.broker.actors.TbActorException;
import org.thingsboard.mqtt.broker.actors.msg.TbActorMsg;
import org.thingsboard.mqtt.broker.actors.service.ContextAwareActor;
import org.thingsboard.mqtt.broker.actors.session.data.ClientSessionActorState;
import org.thingsboard.mqtt.broker.actors.session.data.DefaultClientSessionActorState;
import org.thingsboard.mqtt.broker.actors.session.data.SessionState;
import org.thingsboard.mqtt.broker.actors.session.messages.ConnectionFinishedMsg;
import org.thingsboard.mqtt.broker.actors.session.messages.DisconnectMsg;
import org.thingsboard.mqtt.broker.actors.session.messages.IncomingMqttMsg;
import org.thingsboard.mqtt.broker.actors.session.messages.ConnectionAcceptedMsg;
import org.thingsboard.mqtt.broker.actors.session.messages.SessionInitMsg;
import org.thingsboard.mqtt.broker.actors.session.messages.StopActorCommandMsg;
import org.thingsboard.mqtt.broker.actors.session.service.DisconnectService;
import org.thingsboard.mqtt.broker.actors.session.service.MsgProcessor;
import org.thingsboard.mqtt.broker.actors.session.util.ClientActorUtil;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ClientSessionActor extends ContextAwareActor {

    private final MsgProcessor msgProcessor;
    private final DisconnectService disconnectService;
    private final ClientSessionActorConfiguration clientSessionActorConfiguration;

    private final ClientSessionActorState state;

    // TODO: create actor for persistent clients with Subscriptions in SessionState

    public ClientSessionActor(ActorSystemContext systemContext, String clientId, boolean isClientIdGenerated) {
        super(systemContext);
        this.msgProcessor = systemContext.getClientSessionActorContext().getMsgProcessor();
        this.disconnectService = systemContext.getClientSessionActorContext().getDisconnectService();
        this.clientSessionActorConfiguration = systemContext.getClientSessionActorConfiguration();
        this.state = new DefaultClientSessionActorState(clientId, isClientIdGenerated);
    }

    @Override
    public void init(TbActorCtx ctx) throws TbActorException {
        super.init(ctx);
        this.state.setDisconnectListener(reason -> ctx.tellWithHighPriority(new DisconnectMsg(state.getCurrentSessionId(), reason)));
    }

    @Override
    protected boolean doProcess(TbActorMsg msg) {
        log.trace("[{}][{}] Received {} msg.", state.getClientId(), state.getCurrentSessionId(), msg.getMsgType());
        switch (msg.getMsgType()) {
            case SESSION_INIT_MSG:
                initClientSession(((SessionInitMsg) msg).getClientSessionCtx());
                break;
            case STOP_ACTOR_COMMAND_MSG:
                processActorStop((StopActorCommandMsg) msg);
                break;
            case DISCONNECT_MSG:
                disconnectClient((DisconnectMsg) msg);
                break;
            case INCOMING_MQTT_MSG:
                processMqttMessage((IncomingMqttMsg) msg);
                break;
            case CONNECTION_ACCEPTED_MSG:
                processConnectionAcceptedMsg((ConnectionAcceptedMsg) msg);
                break;
            case CONNECTION_FINISHED_MSG:
                processConnectionFinishedMsg((ConnectionFinishedMsg) msg);
                break;
            default:
                return false;
        }
        return true;
    }

    private void initClientSession(ClientSessionCtx clientSessionCtx) {
        // TODO: pass more info on INIT to be able to auth client and check if we should connect it
        if (clientSessionCtx.getSessionId().equals(state.getCurrentSessionId())) {
            log.warn("[{}][{}] Trying to initialize the same session.", state.getClientId(), clientSessionCtx.getSessionId());
            if (state.getCurrentSessionState() != SessionState.DISCONNECTED) {
                ctx.tellWithHighPriority(new DisconnectMsg(state.getCurrentSessionId(), new DisconnectReason(DisconnectReasonType.ON_ERROR,
                        "Trying to init the same session")));
            }
            return;
        }

        SessionState sessionState = state.getCurrentSessionState();
        if (sessionState != SessionState.DISCONNECTED) {
            // TODO: think if it's better to send DISCONNECT + INIT commands to actor instead (but need some limit logic to not got stuck in the loop)
            log.debug("[{}] Session was in {} state while Actor received INIT message, prev sessionId - {}, new sessionId - {}.",
                    state.getClientId(), sessionState, state.getCurrentSessionId(), clientSessionCtx.getSessionId());
            state.updateSessionState(SessionState.DISCONNECTING);
            disconnectService.disconnect(state, new DisconnectReason(DisconnectReasonType.ON_CONFLICTING_SESSIONS));
        }

        state.updateSessionState(SessionState.INITIALIZED);
        state.setClientSessionCtx(clientSessionCtx);
        state.clearStopActorCommandId();
    }

    private void disconnectClient(DisconnectMsg disconnectMsg) {
        boolean isSessionValid = ClientActorUtil.validateAndLogSession(state, disconnectMsg);
        if (!isSessionValid) {
            return;
        }

        if (state.getCurrentSessionState() == SessionState.DISCONNECTED) {
            log.debug("[{}][{}] Session is already disconnected.", state.getClientId(), state.getCurrentSessionId());
            return;
        }

        if (state.getCurrentSessionState() == SessionState.DISCONNECTING) {
            log.warn("[{}][{}] Session is in {} state. Will try to disconnect again.", state.getClientId(), state.getCurrentSessionId(), SessionState.DISCONNECTING);
        }

        state.updateSessionState(SessionState.DISCONNECTING);
        disconnectService.disconnect(state, disconnectMsg.getReason());
        state.updateSessionState(SessionState.DISCONNECTED);

        state.setStopActorCommandId(UUID.randomUUID());
        StopActorCommandMsg stopActorCommandMsg = new StopActorCommandMsg(state.getStopActorCommandId());
        if (state.isClientIdGenerated()) {
            ctx.tell(stopActorCommandMsg);
        } else {
            systemContext.scheduleMsgWithDelay(ctx, stopActorCommandMsg, TimeUnit.MINUTES.toMillis(clientSessionActorConfiguration.getTimeToWaitBeforeActorStopMinutes()));
        }
    }

    private void processMqttMessage(IncomingMqttMsg msg) {
        try {
            msgProcessor.process(state, msg);
        } catch (Exception e) {
            log.info("[{}][{}] Failed to process MQTT message. Exception - {}, message - {}.", state.getClientId(), state.getCurrentSessionId(),
                    e.getClass().getSimpleName(), e.getMessage());
            log.trace("Detailed error:", e);
            ctx.tellWithHighPriority(new DisconnectMsg(state.getCurrentSessionId(), new DisconnectReason(DisconnectReasonType.ON_ERROR,
                    "Failed to process MQTT message. Exception message - " + e.getMessage())));
        }
    }

    private void processConnectionAcceptedMsg(ConnectionAcceptedMsg msg) {
        boolean isSessionValid = ClientActorUtil.validateAndLogSession(state, msg);
        if (!isSessionValid) {
            return;
        }
        try {
            msgProcessor.processConnectionAccepted(state, msg.isPrevSessionPersistent(), msg.getLastWillMsg());
        } catch (Exception e) {
            log.info("[{}][{}] Failed to process connection accepted. Exception - {}, message - {}.", state.getClientId(), state.getCurrentSessionId(),
                    e.getClass().getSimpleName(), e.getMessage());
            log.trace("Detailed error:", e);
            ctx.tellWithHighPriority(new DisconnectMsg(state.getCurrentSessionId(), new DisconnectReason(DisconnectReasonType.ON_ERROR,
                    "Failed to process connection accepted. Exception message - " + e.getMessage())));
        }
    }

    private void processConnectionFinishedMsg(ConnectionFinishedMsg msg) {
        boolean isSessionValid = ClientActorUtil.validateAndLogSession(state, msg);
        if (!isSessionValid) {
            return;
        }
        try {
            msgProcessor.processConnectionFinished(state);
            state.updateSessionState(SessionState.CONNECTED);
        } catch (Exception e) {
            log.info("[{}][{}] Failed to process connection finished. Exception - {}, message - {}.", state.getClientId(), state.getCurrentSessionId(),
                    e.getClass().getSimpleName(), e.getMessage());
            log.trace("Detailed error:", e);
            ctx.tellWithHighPriority(new DisconnectMsg(state.getCurrentSessionId(), new DisconnectReason(DisconnectReasonType.ON_ERROR,
                    "Failed to process connection finished. Exception message - " + e.getMessage())));
        }
    }


    private void processActorStop(StopActorCommandMsg msg) {
        if (msg.getCommandUUID().equals(state.getStopActorCommandId())) {
            ctx.stop(ctx.getSelf());
        } else {
            log.debug("[{}] Client was reconnected, ignoring {}.", state.getClientId(), msg.getMsgType());
        }
    }
}
