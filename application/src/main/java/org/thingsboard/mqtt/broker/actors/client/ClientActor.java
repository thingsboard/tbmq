/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
import org.thingsboard.mqtt.broker.actors.client.messages.EnhancedAuthInitMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.PubAckResponseMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.PubRecResponseMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.SessionDependentMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.SessionInitMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.StopActorCommandMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.SubscribeCommandMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.SubscriptionChangedEventMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.UnsubscribeCommandMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.cluster.ClearSessionMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.cluster.ConnectionRequestMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.cluster.RemoveApplicationTopicRequestMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.cluster.SessionDisconnectedMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttAuthMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttConnectMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.QueueableMqttMsg;
import org.thingsboard.mqtt.broker.actors.client.service.ActorProcessor;
import org.thingsboard.mqtt.broker.actors.client.service.MqttMessageHandler;
import org.thingsboard.mqtt.broker.actors.client.service.channel.ChannelBackpressureManager;
import org.thingsboard.mqtt.broker.actors.client.service.connect.ConnectService;
import org.thingsboard.mqtt.broker.actors.client.service.session.SessionClusterManager;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.SubscriptionChangesManager;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.SubscriptionCommandService;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorState;
import org.thingsboard.mqtt.broker.actors.client.state.DefaultClientActorState;
import org.thingsboard.mqtt.broker.actors.client.state.SessionState;
import org.thingsboard.mqtt.broker.actors.msg.MsgType;
import org.thingsboard.mqtt.broker.actors.msg.TbActorMsg;
import org.thingsboard.mqtt.broker.actors.service.ContextAwareActor;
import org.thingsboard.mqtt.broker.actors.shared.TimedMsg;
import org.thingsboard.mqtt.broker.common.stats.StatsConstantNames;
import org.thingsboard.mqtt.broker.exception.FullMsgQueueException;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.service.stats.ClientActorStats;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.thingsboard.mqtt.broker.actors.client.state.SessionState.MQTT_PROCESSABLE_STATES;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.DROPPED_MSGS;

@Slf4j
public class ClientActor extends ContextAwareActor {

    private final SessionClusterManager sessionClusterManager;
    private final SubscriptionChangesManager subscriptionChangesManager;
    private final SubscriptionCommandService subscriptionCommandService;
    private final ActorProcessor actorProcessor;
    private final ConnectService connectService;
    private final MqttMessageHandler mqttMessageHandler;
    private final ClientLogger clientLogger;
    private final ClientActorStats clientActorStats;
    private final TbMessageStatsReportClient tbMessageStatsReportClient;

    private final ClientActorConfiguration actorConfiguration;
    private final ChannelBackpressureManager backpressureManager;

    private final ClientActorState state;

    public ClientActor(ActorSystemContext systemContext, String clientId, boolean isClientIdGenerated) {
        super(systemContext);
        this.sessionClusterManager = systemContext.getClientActorContext().getSessionClusterManager();
        this.subscriptionChangesManager = systemContext.getClientActorContext().getSubscriptionChangesManager();
        this.subscriptionCommandService = systemContext.getClientActorContext().getSubscriptionCommandService();
        this.actorProcessor = systemContext.getClientActorContext().getActorProcessor();
        this.connectService = systemContext.getClientActorContext().getConnectService();
        this.mqttMessageHandler = systemContext.getClientActorContext().getMqttMessageHandler();
        this.clientLogger = systemContext.getClientActorContext().getClientLogger();
        this.clientActorStats = systemContext.getClientActorContext().getStatsManager().getClientActorStats();
        this.tbMessageStatsReportClient = systemContext.getClientActorContext().getTbMessageStatsReportClient();
        this.actorConfiguration = systemContext.getClientActorConfiguration();
        this.backpressureManager = systemContext.getChannelBackpressureManager();
        this.state = new DefaultClientActorState(clientId, isClientIdGenerated, systemContext.getClientActorContext().getMaxPreConnectQueueSize());
    }

    @Override
    public void init(TbActorCtx ctx) throws TbActorException {
        super.init(ctx);
    }

    @Override
    protected boolean doProcess(TbActorMsg msg) {
        if (msg instanceof TimedMsg) {
            clientActorStats.logMsgQueueTime(msg, TimeUnit.NANOSECONDS);
        }
        clientLogger.logEventWithDetails(state.getClientId(), getClass(), ctx -> ctx
                .msg("Process actor msg")
                .kv(StatsConstantNames.MSG_TYPE, msg.getMsgType())
        );

        long startTime = System.nanoTime();

        try {
            if (sessionNotMatch(msg)) {
                log.debug("[{}][{}] Received {} for another sessionId - {}.",
                        state.getClientId(), state.getCurrentSessionId(), msg.getMsgType(), ((SessionDependentMsg) msg).getSessionId());
                if (msg instanceof QueueableMqttMsg) {
                    ((QueueableMqttMsg) msg).release();
                }
                return true;
            }

            boolean success = true;
            if (msg instanceof QueueableMqttMsg) {
                success = processQueueableMqttMsg((QueueableMqttMsg) msg);
            } else {
                switch (msg.getMsgType()) {
                    case SESSION_INIT_MSG:
                        actorProcessor.onInit(state, (SessionInitMsg) msg);
                        break;
                    case STOP_ACTOR_COMMAND_MSG:
                        processActorStop((StopActorCommandMsg) msg);
                        break;
                    case DISCONNECT_MSG:
                        actorProcessor.onDisconnect(state, (MqttDisconnectMsg) msg);
                        break;

                    case CONNECTION_REQUEST_MSG:
                        processConnectionRequestMsg((ConnectionRequestMsg) msg);
                        break;
                    case SESSION_DISCONNECTED_MSG:
                        processSessionDisconnectedMsg((SessionDisconnectedMsg) msg);
                        break;
                    case CLEAR_SESSION_MSG:
                        processClearSessionMsg((ClearSessionMsg) msg);
                        break;
                    case REMOVE_APPLICATION_TOPIC_REQUEST_MSG:
                        sessionClusterManager.processRemoveApplicationTopicRequest(state.getClientId(), ((RemoveApplicationTopicRequestMsg) msg).getCallback());
                        break;

                    case SUBSCRIBE_COMMAND_MSG:
                        SubscribeCommandMsg subscribeCommandMsg = (SubscribeCommandMsg) msg;
                        subscriptionCommandService.subscribe(state.getClientId(), subscribeCommandMsg.getTopicSubscriptions());
                        break;
                    case UNSUBSCRIBE_COMMAND_MSG:
                        UnsubscribeCommandMsg unsubscribeCommandMsg = (UnsubscribeCommandMsg) msg;
                        subscriptionCommandService.unsubscribe(state.getClientId(), unsubscribeCommandMsg.getTopics());
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

                    case PUBACK_RESPONSE_MSG:
                        processPubAckResponseMsg((PubAckResponseMsg) msg);
                        break;

                    case PUBREC_RESPONSE_MSG:
                        processPubRecResponseMsg((PubRecResponseMsg) msg);
                        break;

                    case ENHANCED_AUTH_INIT_MSG:
                        actorProcessor.onEnhancedAuthInit(state, (EnhancedAuthInitMsg) msg);
                        break;
                    case MQTT_AUTH_MSG:
                        MqttAuthMsg authMsg = (MqttAuthMsg) msg;
                        switch (authMsg.getReasonCode()) {
                            case CONTINUE_AUTHENTICATION -> actorProcessor.onEnhancedAuthContinue(state, authMsg);
                            case REAUTHENTICATE -> actorProcessor.onEnhancedReAuth(state, authMsg);
                            default -> {
                                log.warn("[{}][{}] Received unexpected auth reason code - {}",
                                        state.getClientId(), state.getCurrentSessionId(), authMsg.getReasonCode());
                                success = false;
                            }
                        }
                        break;

                    case WRITABLE_CHANNEL_MSG:
                        backpressureManager.onChannelWritable(state);
                        break;
                    case NON_WRITABLE_CHANNEL_MSG:
                        backpressureManager.onChannelNonWritable(state);
                        break;

                    default:
                        success = false;
                }
            }
            if (msg.getMsgType() != MsgType.STOP_ACTOR_COMMAND_MSG) {
                if (actorNeedsToBeStopped(success)) {
                    requestActorStop();
                } else {
                    state.clearStopActorCommandId();
                }
            }
            return success;
        } finally {
            clientActorStats.logMsgProcessingTime(msg.getMsgType(), startTime, TimeUnit.NANOSECONDS);
            clientLogger.logEventWithDetails(state.getClientId(), getClass(), ctx -> ctx
                    .msg("Finished msg processing")
                    .kv(StatsConstantNames.MSG_TYPE, msg.getMsgType())
            );
        }
    }

    private void processPubRecResponseMsg(PubRecResponseMsg msg) {
        try {
            mqttMessageHandler.processPubRecResponse(state.getCurrentSessionCtx(), msg);
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to process PUBREC response for message {}.",
                    state.getClientId(), state.getCurrentSessionId(), msg.getMessageId(), e);
            ctx.tellWithHighPriority(new MqttDisconnectMsg(state.getCurrentSessionId(), new DisconnectReason(DisconnectReasonType.ON_ERROR,
                    "Failed to PUBREC response. Exception message - " + e.getMessage())));
        }
    }

    private void processPubAckResponseMsg(PubAckResponseMsg msg) {
        try {
            mqttMessageHandler.processPubAckResponse(state.getCurrentSessionCtx(), msg);
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to process PUBACK response for message {}.",
                    state.getClientId(), state.getCurrentSessionId(), msg.getMessageId(), e);
            ctx.tellWithHighPriority(new MqttDisconnectMsg(state.getCurrentSessionId(), new DisconnectReason(DisconnectReasonType.ON_ERROR,
                    "Failed to PUBACK response. Exception message - " + e.getMessage())));
        }
    }

    private void processConnectionRequestMsg(ConnectionRequestMsg msg) {
        try {
            log.trace("[{}] Processing CONNECTION_REQUEST_MSG processConnectionRequestMsg {}", state.getClientId(), msg);
            sessionClusterManager.processConnectionRequest(msg.getSessionInfo(), msg.getRequestInfo());
            msg.getCallback().onSuccess();
        } catch (Exception e) {
            msg.getCallback().onFailure(e);
        }
    }

    private void processSessionDisconnectedMsg(SessionDisconnectedMsg msg) {
        try {
            log.trace("[{}] Processing SESSION_DISCONNECTED_MSG processSessionDisconnectedMsg {}", state.getClientId(), msg);
            sessionClusterManager.processSessionDisconnected(state.getClientId(), msg);
            msg.getCallback().onSuccess();
        } catch (Exception e) {
            msg.getCallback().onFailure(e);
        }
    }

    private void processClearSessionMsg(ClearSessionMsg msg) {
        try {
            log.trace("[{}] Processing CLEAR_SESSION_MSG processClearSessionMsg {}", state.getClientId(), msg);
            sessionClusterManager.processClearSession(state.getClientId(), msg.getSessionId());
            msg.getCallback().onSuccess();
        } catch (Exception e) {
            msg.getCallback().onFailure(e);
        }
    }

    private boolean processQueueableMqttMsg(QueueableMqttMsg msg) {
        if (state.getCurrentSessionState() == SessionState.DISCONNECTED) {
            log.debug("[{}][{}] Session is in {} state, ignoring message, msg type - {}.",
                    state.getClientId(), state.getCurrentSessionId(), SessionState.DISCONNECTED, msg.getMsgType());
            release(msg);
            return true;
        }
        if (!MQTT_PROCESSABLE_STATES.contains(state.getCurrentSessionState())) {
            log.warn("[{}][{}] Msg {} cannot be processed in state - {}.", state.getClientId(), state.getCurrentSessionId(),
                    msg.getMsgType(), state.getCurrentSessionState());
            release(msg);
            ctx.tellWithHighPriority(new MqttDisconnectMsg(state.getCurrentSessionId(), new DisconnectReason(DisconnectReasonType.ON_ERROR,
                    "Failed to process message")));
            return true;
        }

        if (state.getCurrentSessionState() == SessionState.CONNECTING) {
            enqueuePreConnectMessage(msg);
            return true;
        }

        try {
            return mqttMessageHandler.process(state.getCurrentSessionCtx(), msg, getActorRef());
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to process MQTT message.", state.getClientId(), state.getCurrentSessionId(), e);
            ctx.tellWithHighPriority(new MqttDisconnectMsg(state.getCurrentSessionId(), new DisconnectReason(DisconnectReasonType.ON_ERROR,
                    "Failed to process MQTT message. Exception message - " + e.getMessage())));
            return true;
        } finally {
            release(msg);
        }
    }

    private void enqueuePreConnectMessage(QueueableMqttMsg msg) {
        try {
            state.getQueuedMessages().add(msg);
        } catch (FullMsgQueueException e) {
            log.debug("[{}][{}] Too many messages in the pre-connect queue", state.getClientId(), state.getCurrentSessionId());
            release(msg);
            tbMessageStatsReportClient.reportStats(DROPPED_MSGS, state.getQueuedMessages().getPublishMsgCountAndClear());
            ctx.tellWithHighPriority(new MqttDisconnectMsg(state.getCurrentSessionId(), new DisconnectReason(DisconnectReasonType.ON_QUOTA_EXCEEDED,
                    "Too many messages in the pre-connect queue")));
        }
    }

    private void release(QueueableMqttMsg msg) {
        msg.release();
    }

    private void processConnectMsg(MqttConnectMsg msg) {
        if (currentStateIsDisconnected(msg)) {
            return;
        }

        if (state.getCurrentSessionState() != SessionState.INITIALIZED) {
            log.warn("[{}][{}] Msg {} can only be processed in {} state, current state - {}.", state.getClientId(), state.getCurrentSessionId(),
                    msg.getMsgType(), SessionState.INITIALIZED, state.getCurrentSessionState());
            ctx.tellWithHighPriority(new MqttDisconnectMsg(state.getCurrentSessionId(), new DisconnectReason(DisconnectReasonType.ON_ERROR,
                    "Failed to process message")));
            return;
        }

        try {
            state.updateSessionState(SessionState.CONNECTING);
            connectService.startConnection(state, msg);
        } catch (Exception e) {
            log.error("[{}][{}] Failed to process {}.", state.getClientId(), state.getCurrentSessionId(), msg.getMsgType(), e);
            ctx.tellWithHighPriority(new MqttDisconnectMsg(state.getCurrentSessionId(), new DisconnectReason(DisconnectReasonType.ON_ERROR,
                    "Failed to process message")));
        }
    }

    private void processConnectionAcceptedMsg(ConnectionAcceptedMsg msg) {
        if (currentStateIsDisconnected(msg)) {
            return;
        }

        if (state.getCurrentSessionState() != SessionState.CONNECTING) {
            log.warn("[{}][{}] Msg {} can only be processed in {} state, current state - {}.", state.getClientId(), state.getCurrentSessionId(),
                    msg.getMsgType(), SessionState.CONNECTING, state.getCurrentSessionState());
            ctx.tellWithHighPriority(new MqttDisconnectMsg(state.getCurrentSessionId(), new DisconnectReason(DisconnectReasonType.ON_ERROR,
                    "Failed to process message")));
            return;
        }

        try {
            connectService.acceptConnection(state, msg, getActorRef());
            state.updateSessionState(SessionState.CONNECTED);
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to process {}.", state.getClientId(), state.getCurrentSessionId(), msg.getMsgType(), e);
            ctx.tellWithHighPriority(new MqttDisconnectMsg(state.getCurrentSessionId(), new DisconnectReason(DisconnectReasonType.ON_ERROR,
                    "Failed to process message")));
        }
    }

    private boolean currentStateIsDisconnected(SessionDependentMsg msg) {
        if (state.getCurrentSessionState() == SessionState.DISCONNECTED) {
            log.debug("[{}][{}] Session is in state {}, ignoring {}", state.getClientId(), state.getCurrentSessionId(),
                    SessionState.DISCONNECTED, msg.getMsgType());
            return true;
        }
        return false;
    }

    private boolean actorNeedsToBeStopped(boolean successfulProcessing) {
        return !successfulProcessing || state.getCurrentSessionState() == SessionState.DISCONNECTED;
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
        state.setStopActorCommandId(UUID.randomUUID());
        StopActorCommandMsg stopActorCommandMsg = new StopActorCommandMsg(state.getStopActorCommandId());

        long delay = state.isClientIdGenerated() ? TimeUnit.SECONDS.toMillis(actorConfiguration.getWaitBeforeGeneratedActorStopSeconds())
                : TimeUnit.SECONDS.toMillis(actorConfiguration.getWaitBeforeNamedActorStopSeconds());
        log.debug("[{}][{}][{}] Scheduling actor stop command with {} delay", state.getClientId(), state.getCurrentSessionId(),
                state.getCurrentSessionState(), delay);
        systemContext.scheduleMsgWithDelay(ctx, stopActorCommandMsg, delay);
    }

    private boolean sessionNotMatch(TbActorMsg msg) {
        return msg instanceof SessionDependentMsg && !((SessionDependentMsg) msg).getSessionId().equals(state.getCurrentSessionId());
    }
}
