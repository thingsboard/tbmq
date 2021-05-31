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
import org.thingsboard.mqtt.broker.actors.client.messages.CallbackMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.ClearSessionMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.ClientSessionCallback;
import org.thingsboard.mqtt.broker.actors.client.messages.ConnectionAcceptedMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.ConnectionFinishedMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.ConnectionRequestMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.DisconnectMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.IncomingMqttMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.SessionDisconnectedMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.SessionInitMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.StopActorCommandMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.SubscriptionChangedEventMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.TryConnectMsg;
import org.thingsboard.mqtt.broker.actors.client.service.ActorProcessor;
import org.thingsboard.mqtt.broker.actors.client.service.session.ClientSessionManager;
import org.thingsboard.mqtt.broker.actors.client.service.session.MsgProcessor;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.SubscriptionChangesManager;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorState;
import org.thingsboard.mqtt.broker.actors.client.state.DefaultClientActorState;
import org.thingsboard.mqtt.broker.actors.client.state.SessionState;
import org.thingsboard.mqtt.broker.actors.client.util.ClientActorUtil;
import org.thingsboard.mqtt.broker.actors.msg.TbActorMsg;
import org.thingsboard.mqtt.broker.actors.service.ContextAwareActor;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ClientActor extends ContextAwareActor {
    // TODO: rename packet and maybe classes

    private final MsgProcessor msgProcessor;
    private final ClientSessionManager clientSessionManager;
    private final SubscriptionChangesManager subscriptionChangesManager;
    private final ActorProcessor actorProcessor;
    private final ClientActorConfiguration actorConfiguration;

    private final ClientActorState state;

    // TODO: create actor for persistent clients with Subscriptions in SessionState

    public ClientActor(ActorSystemContext systemContext, String clientId, boolean isClientIdGenerated) {
        super(systemContext);
        this.msgProcessor = systemContext.getClientActorContext().getMsgProcessor();
        this.clientSessionManager = systemContext.getClientActorContext().getClientSessionManager();
        this.subscriptionChangesManager = systemContext.getClientActorContext().getSubscriptionChangesManager();
        this.actorProcessor = systemContext.getClientActorContext().getActorProcessor();
        this.actorConfiguration = systemContext.getClientActorConfiguration();
        this.state = new DefaultClientActorState(clientId, isClientIdGenerated);
    }

    @Override
    public void init(TbActorCtx ctx) throws TbActorException {
        super.init(ctx);
        this.state.setDisconnectListener(reason -> ctx.tellWithHighPriority(new DisconnectMsg(state.getCurrentSessionId(), reason)));
    }

    @Override
    protected boolean doProcess(TbActorMsg msg) {
        log.trace("[{}][{}] Received {} msg.", state.getClientId(), state.getCurrentSessionId(), msg.getMsgType());
        if (msg instanceof CallbackMsg) {
            return processCallbackMsg(msg);
        }
        switch (msg.getMsgType()) {
            case SESSION_INIT_MSG:
                actorProcessor.onInit(state, (SessionInitMsg) msg);
                break;
            case STOP_ACTOR_COMMAND_MSG:
                processActorStop((StopActorCommandMsg) msg);
                break;
            case DISCONNECT_MSG:
                processDisconnectMsg((DisconnectMsg) msg);
                actorProcessor.onDisconnect(state, (DisconnectMsg) msg);
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

            case TRY_CONNECT_MSG:
                clientSessionManager.tryConnectSession((TryConnectMsg) msg);
                break;

            case SUBSCRIPTION_CHANGED_EVENT_MSG:
                subscriptionChangesManager.processSubscriptionChangedEvent(state.getClientId(), (SubscriptionChangedEventMsg) msg);
                break;
            default:
                return false;
        }
        return true;
    }

    private boolean processCallbackMsg(TbActorMsg msg) {
        ClientSessionCallback callback = ((CallbackMsg) msg).getCallback();
        try {
            switch (msg.getMsgType()) {
                case CONNECTION_REQUEST_MSG:
                    clientSessionManager.processConnectionRequest((ConnectionRequestMsg) msg,
                            tryConnectMsg -> ctx.tellWithHighPriority(tryConnectMsg));
                    break;
                case SESSION_DISCONNECTED_MSG:
                    clientSessionManager.processSessionDisconnected(state.getClientId(), (SessionDisconnectedMsg) msg);
                    break;
                case CLEAR_SESSION_MSG:
                    clientSessionManager.processClearSession(state.getClientId(), (ClearSessionMsg) msg);
                    break;
                default:
                    callback.onFailure(new RuntimeException("Unknown msg type"));
                    return false;
            }
            callback.onSuccess();
            return true;
        } catch (Exception e) {
            callback.onFailure(e);
            return true;
        }
    }

    private void processDisconnectMsg(DisconnectMsg disconnectMsg) {
        actorProcessor.onDisconnect(state, disconnectMsg);
        requestActorStop();
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

    private void requestActorStop() {
        state.setStopActorCommandId(UUID.randomUUID());
        StopActorCommandMsg stopActorCommandMsg = new StopActorCommandMsg(state.getStopActorCommandId());
        if (state.isClientIdGenerated()) {
            ctx.tell(stopActorCommandMsg);
        } else {
            systemContext.scheduleMsgWithDelay(ctx, stopActorCommandMsg, TimeUnit.MINUTES.toMillis(actorConfiguration.getTimeToWaitBeforeActorStopMinutes()));
        }
    }
}
