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
package org.thingsboard.mqtt.broker.actors.client;

import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.actors.ActorSystemContext;
import org.thingsboard.mqtt.broker.actors.TbActorCtx;
import org.thingsboard.mqtt.broker.actors.TbActorException;
import org.thingsboard.mqtt.broker.actors.client.messages.ConnectionAcceptedMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.DisconnectMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.SessionDependentMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.SessionInitMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.StopActorCommandMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.SubscriptionChangedEventMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.cluster.ClearSessionMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.cluster.ConnectionRequestMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.cluster.SessionClusterManagementMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.cluster.SessionDisconnectedMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttConnectMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.QueueableMqttMsg;
import org.thingsboard.mqtt.broker.actors.client.service.ActorProcessor;
import org.thingsboard.mqtt.broker.actors.client.service.MqttMessageHandler;
import org.thingsboard.mqtt.broker.actors.client.service.connect.ConnectService;
import org.thingsboard.mqtt.broker.actors.client.service.session.SessionClusterManager;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.SubscriptionChangesManager;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorState;
import org.thingsboard.mqtt.broker.actors.client.state.DefaultClientActorState;
import org.thingsboard.mqtt.broker.actors.client.state.SessionState;
import org.thingsboard.mqtt.broker.actors.msg.TbActorMsg;
import org.thingsboard.mqtt.broker.actors.service.ContextAwareActor;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ClientActor extends ContextAwareActor {

    private final SessionClusterManager sessionClusterManager;
    private final SubscriptionChangesManager subscriptionChangesManager;
    private final ActorProcessor actorProcessor;
    private final ConnectService connectService;
    private final MqttMessageHandler mqttMessageHandler;
    private final ClientActorConfiguration actorConfiguration;

    private final ClientActorState state;

    public ClientActor(ActorSystemContext systemContext, String clientId, boolean isClientIdGenerated) {
        super(systemContext);
        this.sessionClusterManager = systemContext.getClientActorContext().getSessionClusterManager();
        this.subscriptionChangesManager = systemContext.getClientActorContext().getSubscriptionChangesManager();
        this.actorProcessor = systemContext.getClientActorContext().getActorProcessor();
        this.connectService = systemContext.getClientActorContext().getConnectService();
        this.mqttMessageHandler = systemContext.getClientActorContext().getMqttMessageHandler();
        this.actorConfiguration = systemContext.getClientActorConfiguration();
        this.state = new DefaultClientActorState(clientId, isClientIdGenerated);
    }

    @Override
    public void init(TbActorCtx ctx) throws TbActorException {
        super.init(ctx);
    }

    @Override
    protected boolean doProcess(TbActorMsg msg) {
        log.trace("[{}][{}] Received {} msg.", state.getClientId(), state.getCurrentSessionId(), msg.getMsgType());

        if (sessionNotMatch(msg)) {
            log.debug("[{}][{}] Received {} for another sessionId - {}.",
                    state.getClientId(), state.getCurrentSessionId(), msg.getMsgType(), ((SessionDependentMsg) msg).getSessionId());
            return true;
        }

        boolean success = true;
        if (msg instanceof SessionClusterManagementMsg) {
            success = processSessionClusterManagementMsg((SessionClusterManagementMsg) msg);
            if (state.getCurrentSessionState() == SessionState.DISCONNECTED) {
                requestActorStop();
            }
            return success;
        }
        if (msg instanceof QueueableMqttMsg) {
            return processQueueableMqttMsg((QueueableMqttMsg) msg);
        }
        switch (msg.getMsgType()) {
            case SESSION_INIT_MSG:
                actorProcessor.onInit(state, (SessionInitMsg) msg);
                break;
            case STOP_ACTOR_COMMAND_MSG:
                processActorStop((StopActorCommandMsg) msg);
                break;
            case DISCONNECT_MSG:
                actorProcessor.onDisconnect(state, (DisconnectMsg) msg);
                requestActorStop();
                break;

            case MQTT_CONNECT_MSG:
                processConnectMsg((MqttConnectMsg) msg);
                break;
            case CONNECTION_ACCEPTED_MSG:
                processConnectionAcceptedMsg((ConnectionAcceptedMsg) msg);
                break;

            case SUBSCRIPTION_CHANGED_EVENT_MSG:
                subscriptionChangesManager.processSubscriptionChangedEvent(state.getClientId(), (SubscriptionChangedEventMsg) msg);
                break;
            default:
                success = false;
        }
        return success;
    }

    private boolean processSessionClusterManagementMsg(SessionClusterManagementMsg msg) {
        state.clearStopActorCommandId();
        switch (msg.getMsgType()) {
            case CONNECTION_REQUEST_MSG:
                processConnectionRequestMsg((ConnectionRequestMsg) msg);
                break;
            case SESSION_DISCONNECTED_MSG:
                sessionClusterManager.processSessionDisconnected(state.getClientId(), ((SessionDisconnectedMsg) msg).getSessionId());
                break;
            case CLEAR_SESSION_MSG:
                sessionClusterManager.processClearSession(state.getClientId(), ((ClearSessionMsg) msg).getSessionId());
                break;
            default:
                return false;
        }
        return true;
    }

    private void processConnectionRequestMsg(ConnectionRequestMsg msg) {
        try {
            sessionClusterManager.processConnectionRequest(msg.getSessionInfo(), msg.getRequestInfo());
            msg.getCallback().onSuccess();
        } catch (Exception e) {
            msg.getCallback().onFailure(e);
        }
    }

    private boolean processQueueableMqttMsg(QueueableMqttMsg msg) {
        if (state.getCurrentSessionState() == SessionState.DISCONNECTED) {
            log.debug("[{}][{}] Session is in {} state, ignoring message, msg type - {}.",
                    state.getClientId(), state.getCurrentSessionId(), SessionState.DISCONNECTED, msg.getMsgType());
            return true;
        }
        if (state.getCurrentSessionState() != SessionState.CONNECTING
                && state.getCurrentSessionState() != SessionState.CONNECTED) {
            log.warn("[{}][{}] Msg {} cannot be processed in state - {}.", state.getClientId(), state.getCurrentSessionId(),
                    msg.getMsgType(), state.getCurrentSessionState());
            ctx.tellWithHighPriority(new DisconnectMsg(state.getCurrentSessionId(), new DisconnectReason(DisconnectReasonType.ON_ERROR,
                    "Failed to process message")));
            return true;
        }

        if (state.getCurrentSessionState() == SessionState.CONNECTING) {
            state.getQueuedMessages().add(msg);
            return true;
        }

        try {
            return mqttMessageHandler.process(state.getCurrentSessionCtx(), msg);
        } catch (Exception e) {
            log.info("[{}][{}] Failed to process MQTT message. Exception - {}, message - {}.", state.getClientId(), state.getCurrentSessionId(),
                    e.getClass().getSimpleName(), e.getMessage());
            log.trace("Detailed error:", e);
            ctx.tellWithHighPriority(new DisconnectMsg(state.getCurrentSessionId(), new DisconnectReason(DisconnectReasonType.ON_ERROR,
                    "Failed to process MQTT message. Exception message - " + e.getMessage())));
            return true;
        }
    }

    private void processConnectMsg(MqttConnectMsg msg) {
        if (state.getCurrentSessionState() == SessionState.DISCONNECTED) {
            log.debug("[{}][{}] Session is in state {}, ignoring {}",  state.getClientId(), state.getCurrentSessionId(),
                    SessionState.DISCONNECTED, msg.getMsgType());
            return;
        }

        if (state.getCurrentSessionState() != SessionState.INITIALIZED) {
            log.warn("[{}][{}] Msg {} can only be processed in {} state, current state - {}.", state.getClientId(), state.getCurrentSessionId(),
                    msg.getMsgType(), SessionState.INITIALIZED, state.getCurrentSessionState());
            ctx.tellWithHighPriority(new DisconnectMsg(state.getCurrentSessionId(), new DisconnectReason(DisconnectReasonType.ON_ERROR,
                    "Failed to process message")));
            return;
        }

        try {
            state.updateSessionState(SessionState.CONNECTING);
            connectService.startConnection(state, msg);
        } catch (Exception e) {
            log.info("[{}][{}] Failed to process {}. Exception - {}, message - {}.", state.getClientId(), state.getCurrentSessionId(),
                    msg.getMsgType(), e.getClass().getSimpleName(), e.getMessage());
            log.trace("Detailed error:", e);
            ctx.tellWithHighPriority(new DisconnectMsg(state.getCurrentSessionId(), new DisconnectReason(DisconnectReasonType.ON_ERROR,
                    "Failed to process message")));
        }
    }

    private void processConnectionAcceptedMsg(ConnectionAcceptedMsg msg) {
        if (state.getCurrentSessionState() == SessionState.DISCONNECTED) {
            log.debug("[{}][{}] Session is in state {}, ignoring {}",  state.getClientId(), state.getCurrentSessionId(),
                    SessionState.DISCONNECTED, msg.getMsgType());
            return;
        }

        if (state.getCurrentSessionState() != SessionState.CONNECTING) {
            log.warn("[{}][{}] Msg {} can only be processed in {} state, current state - {}.", state.getClientId(), state.getCurrentSessionId(),
                    msg.getMsgType(), SessionState.CONNECTING, state.getCurrentSessionState());
            ctx.tellWithHighPriority(new DisconnectMsg(state.getCurrentSessionId(), new DisconnectReason(DisconnectReasonType.ON_ERROR,
                    "Failed to process message")));
            return;
        }

        try {
            connectService.acceptConnection(state, msg);
            state.updateSessionState(SessionState.CONNECTED);
        } catch (Exception e) {
            log.info("[{}][{}] Failed to process {}. Exception - {}, message - {}.", state.getClientId(), state.getCurrentSessionId(),
                    msg.getMsgType(), e.getClass().getSimpleName(), e.getMessage());
            log.trace("Detailed error:", e);
            ctx.tellWithHighPriority(new DisconnectMsg(state.getCurrentSessionId(), new DisconnectReason(DisconnectReasonType.ON_ERROR,
                    "Failed to process message")));
        }
    }

    private void processActorStop(StopActorCommandMsg msg) {
        if (!msg.getCommandUUID().equals(state.getStopActorCommandId())) {
            log.debug("[{}] Ignoring {}.", state.getClientId(), msg.getMsgType());
            return;
        }

        log.debug("[{}] Stopping actor, current sessionId - {}, current session state - {}",
                state.getClientId(), state.getCurrentSessionId(), state.getCurrentSessionState());
        ctx.stop(ctx.getSelf());
    }

    private void requestActorStop() {
        if (state.getStopActorCommandId() != null) {
            log.debug("[{}] Currently waiting for {} stop command.", state.getClientId(), state.getStopActorCommandId());
            return;
        }

        state.setStopActorCommandId(UUID.randomUUID());
        StopActorCommandMsg stopActorCommandMsg = new StopActorCommandMsg(state.getStopActorCommandId());

        long delay = state.isClientIdGenerated() ? TimeUnit.SECONDS.toMillis(actorConfiguration.getTimeToWaitBeforeGeneratedActorStopSeconds())
                : TimeUnit.SECONDS.toMillis(actorConfiguration.getTimeToWaitBeforeNamedActorStopSeconds());
        systemContext.scheduleMsgWithDelay(ctx, stopActorCommandMsg, delay);
    }

    private boolean sessionNotMatch(TbActorMsg msg) {
        return msg instanceof SessionDependentMsg && !((SessionDependentMsg) msg).getSessionId().equals(state.getCurrentSessionId());
    }
}
