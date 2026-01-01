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
package org.thingsboard.mqtt.broker.actors.client.service.connect;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttVersion;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.actors.TbActorRef;
import org.thingsboard.mqtt.broker.actors.client.messages.ConnectionAcceptedMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttConnectMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.actors.client.service.MqttMessageHandler;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorStateInfo;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.exception.ConnectionValidationException;
import org.thingsboard.mqtt.broker.exception.DataValidationException;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.limits.RateLimitService;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ConnectionResponse;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.data.ClientConnectInfo;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.service.mqtt.flow.control.FlowControlService;
import org.thingsboard.mqtt.broker.service.mqtt.keepalive.KeepAliveService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.mqtt.validation.PublishMsgValidationService;
import org.thingsboard.mqtt.broker.service.mqtt.will.LastWillService;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionCache;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;
import org.thingsboard.mqtt.broker.session.TopicAliasCtx;
import org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory;
import org.thingsboard.mqtt.broker.util.MqttPropertiesUtil;
import org.thingsboard.mqtt.broker.util.MqttReasonCodeResolver;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConnectServiceImpl implements ConnectService {

    private final ClientMqttActorManager clientMqttActorManager;
    private final MqttMessageGenerator mqttMessageGenerator;
    private final ClientSessionEventService clientSessionEventService;
    private final KeepAliveService keepAliveService;
    private final ServiceInfoProvider serviceInfoProvider;
    private final LastWillService lastWillService;
    private final ClientSessionCtxService clientSessionCtxService;
    private final MsgPersistenceManager msgPersistenceManager;
    private final MqttMessageHandler messageHandler;
    private final ClientSubscriptionCache clientSubscriptionCache;
    private final RateLimitService rateLimitService;
    private final FlowControlService flowControlService;
    private final PublishMsgValidationService publishMsgValidationService;

    private ExecutorService connectHandlerExecutor;

    @Value("${mqtt.connect.threads:4}")
    private int threadsCount;
    @Setter
    @Value("${mqtt.keep-alive.max-keep-alive:600}")
    private int maxServerKeepAlive;
    @Value("${mqtt.client-session-expiry.max-expiry-interval:604800}")
    private int maxExpiryInterval;
    @Value("${mqtt.topic.alias-max:10}")
    private int maxTopicAlias;
    @Value("${mqtt.flow-control.enabled:true}")
    private boolean flowControlEnabled;
    @Value("${mqtt.flow-control.delayed-queue-max-size:1000}")
    private int delayedQueueMaxSize;
    @Setter
    @Value("${mqtt.flow-control.mqtt3x-receive-max:65535}")
    private int mqtt3xReceiveMax;

    @PostConstruct
    public void init() {
        if (this.maxTopicAlias < 0) {
            throw new RuntimeException("'Max Topic Alias' can not be negative!");
        }
        this.connectHandlerExecutor = ThingsBoardExecutors.initExecutorService(threadsCount, "connect-handler-executor");
    }

    @Override
    public void startConnection(ClientActorStateInfo actorState, MqttConnectMsg msg) throws MqttException {
        UUID sessionId = actorState.getCurrentSessionId();
        ClientSessionCtx sessionCtx = actorState.getCurrentSessionCtx();
        String clientId = actorState.getClientId();

        log.trace("[{}][{}][{}] Processing connect msg.", sessionCtx.getAddress(), clientId, sessionId);

        int sessionExpiryInterval = getSessionExpiryInterval(msg);
        int keepAliveSeconds = getKeepAliveSeconds(actorState, msg);
        SessionInfo sessionInfo = getSessionInfo(
                sessionId,
                msg.isCleanStart(),
                clientId,
                sessionCtx.getClientType(),
                sessionCtx.getAddressBytes(),
                keepAliveSeconds,
                sessionExpiryInterval
        );

        boolean proceedWithConnection = shouldProceedWithConnection(actorState, msg, sessionInfo);
        if (!proceedWithConnection) {
            return;
        }

        // SessionInfo should be set for ctx only after validating the connection by shouldProceedWithConnection method
        // to process disconnection correctly
        sessionCtx.setSessionInfo(sessionInfo);

        if (flowControlEnabled) {
            int receiveMaxValue = getReceiveMaxValue(msg, sessionCtx);
            sessionCtx.initPublishedInFlightCtx(flowControlService, sessionCtx, receiveMaxValue, delayedQueueMaxSize);
        }

        sessionCtx.setTopicAliasCtx(getTopicAliasCtx(clientId, msg));
        keepAliveService.registerSession(clientId, sessionId, keepAliveSeconds);

        ClientConnectInfo clientConnectInfo = ClientConnectInfo.fromCtx(sessionCtx);
        ListenableFuture<ConnectionResponse> connectFuture = clientSessionEventService.requestConnection(sessionInfo, clientConnectInfo);
        Futures.addCallback(connectFuture, new FutureCallback<>() {
            @Override
            public void onSuccess(ConnectionResponse connectionResponse) {
                if (connectionResponse.isSuccess()) {
                    notifyConnectionAccepted(connectionResponse);
                } else {
                    refuseConnection(sessionCtx, null);
                }
            }

            private void notifyConnectionAccepted(ConnectionResponse connectionResponse) {
                clientMqttActorManager.notifyConnectionAccepted(
                        clientId,
                        new ConnectionAcceptedMsg(
                                sessionId,
                                connectionResponse.isSessionPresent(),
                                msg.getLastWillMsg(),
                                msg.getProperties())
                );
            }

            @Override
            public void onFailure(Throwable t) {
                refuseConnection(sessionCtx, t);
            }
        }, connectHandlerExecutor);
    }

    @Override
    public void acceptConnection(ClientActorStateInfo actorState, ConnectionAcceptedMsg connectionAcceptedMsg, TbActorRef actorRef) {
        ClientSessionCtx sessionCtx = actorState.getCurrentSessionCtx();
        SessionInfo sessionInfo = sessionCtx.getSessionInfo();

        lastWillService.cancelLastWillDelayIfScheduled(sessionCtx.getClientId());
        if (connectionAcceptedMsg.getLastWillMsg() != null) {
            lastWillService.saveLastWillMsg(sessionInfo, connectionAcceptedMsg.getLastWillMsg());
        }

        pushConnAckMsg(actorState, connectionAcceptedMsg);

        log.debug("[{}] [{}] Client connected!", actorState.getClientId(), actorState.getCurrentSessionId());

        clientSessionCtxService.registerSession(sessionCtx);

        if (sessionCtx.getSessionInfo().isPersistent()) {
            msgPersistenceManager.startProcessingPersistedMessages(actorState);
            startProcessingSharedSubscriptionsIfPresent(sessionCtx);
        }

        processQueuedMessages(actorState, sessionCtx, actorRef);
    }

    private void processQueuedMessages(ClientActorStateInfo actorState, ClientSessionCtx sessionCtx, TbActorRef actorRef) {
        actorState.getQueuedMessages().process(msg -> {
            try {
                messageHandler.process(sessionCtx, msg, actorRef);
            } finally {
                msg.release();
            }
        });
    }

    private void startProcessingSharedSubscriptionsIfPresent(ClientSessionCtx sessionCtx) {
        Set<TopicSharedSubscription> subscriptions = clientSubscriptionCache.getClientSharedSubscriptions(sessionCtx.getClientId());
        if (!CollectionUtils.isEmpty(subscriptions)) {
            msgPersistenceManager.startProcessingSharedSubscriptions(sessionCtx, subscriptions);
        }
    }

    private void pushConnAckMsg(ClientActorStateInfo actorState, ConnectionAcceptedMsg msg) {
        MqttConnAckMessage mqttConnAckMsg = mqttMessageGenerator.createMqttConnAckMsg(actorState, msg);
        actorState.getCurrentSessionCtx().getChannel().writeAndFlush(mqttConnAckMsg);
    }

    void refuseConnection(ClientSessionCtx clientSessionCtx, Throwable t) {
        logConnectionRefused(t, clientSessionCtx);

        sendConnectionRefusedMsgAndDisconnect(clientSessionCtx);
    }

    private void sendConnectionRefusedMsgAndDisconnect(ClientSessionCtx ctx) {
        try {
            createAndSendConnAckMsg(MqttReasonCodeResolver.connectionRefusedServerUnavailable(ctx), ctx);
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to send CONN_ACK response.", ctx.getClientId(), ctx.getSessionId());
        } finally {
            disconnect(ctx.getClientId(), ctx.getSessionId());
        }
    }

    private void disconnect(String clientId, UUID sessionId) {
        clientMqttActorManager.disconnect(clientId, newDisconnectMsg(sessionId));
    }

    private MqttDisconnectMsg newDisconnectMsg(UUID sessionId) {
        return new MqttDisconnectMsg(sessionId, new DisconnectReason(DisconnectReasonType.ON_CONNECTION_FAILURE));
    }

    private void logConnectionRefused(Throwable t, ClientSessionCtx clientSessionCtx) {
        if (t == null) {
            log.debug("[{}][{}] Client wasn't connected.", clientSessionCtx.getClientId(), clientSessionCtx.getSessionId());
        } else {
            log.debug("[{}][{}] Client wasn't connected.", clientSessionCtx.getClientId(), clientSessionCtx.getSessionId(), t);
        }
    }

    SessionInfo getSessionInfo(UUID sessionId, boolean cleanStart, String clientId,
                               ClientType clientType, byte[] clientIpAdr, int keepAliveSeconds, int sessionExpiryInterval) {
        return ClientSessionInfoFactory.getSessionInfo(
                sessionId,
                cleanStart,
                serviceInfoProvider.getServiceId(),
                new ClientInfo(clientId, clientType, clientIpAdr),
                ClientSessionInfoFactory.getConnectionInfo(keepAliveSeconds),
                sessionExpiryInterval);
    }

    boolean shouldProceedWithConnection(ClientActorStateInfo actorState, MqttConnectMsg msg, SessionInfo sessionInfo) {
        ClientSessionCtx ctx = actorState.getCurrentSessionCtx();
        String clientId = actorState.getClientId();
        try {
            validateClientId(ctx, msg);
            validateSessionsRateLimit(ctx, clientId);
            validateLastWillMessage(ctx, clientId, msg);
            validateApplicationClientsLimit(ctx, sessionInfo);
        } catch (ConnectionValidationException e) {
            log.warn("[{}] Connection validation failed: {}", ctx.getSessionId(), e.getMessage());
            createAndSendConnAckMsg(e.getMqttConnectReturnCode(), ctx);
            disconnect(clientId, ctx.getSessionId());
            return false;
        }
        return true;
    }

    private void validateClientId(ClientSessionCtx ctx, MqttConnectMsg msg) throws ConnectionValidationException {
        if (isPersistentClientWithoutClientId(msg)) {
            throw new ConnectionValidationException("Client identifier is empty and clean session flag is set to false",
                    MqttReasonCodeResolver.connectionRefusedClientIdNotValid(ctx));
        }
    }

    private void validateSessionsRateLimit(ClientSessionCtx ctx, String clientId) throws ConnectionValidationException {
        if (!rateLimitService.checkSessionsLimit(clientId)) {
            throw new ConnectionValidationException("Sessions limit exceeded",
                    MqttReasonCodeResolver.connectionRefusedQuotaExceeded(ctx));
        }
    }

    private void validateLastWillMessage(ClientSessionCtx ctx, String clientId, MqttConnectMsg msg) throws ConnectionValidationException {
        PublishMsg lastWillMsg = msg.getLastWillMsg();
        if (lastWillMsg != null) {
            boolean validationSucceed;
            try {
                validationSucceed = publishMsgValidationService.validatePubMsg(ctx, clientId, lastWillMsg);
            } catch (DataValidationException e) {
                throw new ConnectionValidationException("Topic name of Last will message is invalid",
                        MqttReasonCodeResolver.connectionRefusedTopicNameInvalid(ctx));
            }
            if (!validationSucceed) {
                throw new ConnectionValidationException("Last will message validation failed. Client is not authorized to " +
                        "publish to the topic",
                        MqttReasonCodeResolver.connectionRefusedNotAuthorized(ctx));
            }
        }
    }

    private void validateApplicationClientsLimit(ClientSessionCtx ctx, SessionInfo sessionInfo) throws ConnectionValidationException {
        if (!rateLimitService.checkApplicationClientsLimit(sessionInfo)) {
            throw new ConnectionValidationException("Application clients limit exceeded",
                    MqttReasonCodeResolver.connectionRefusedQuotaExceeded(ctx));
        }
    }

    private boolean isPersistentClientWithoutClientId(MqttConnectMsg msg) {
        return !msg.isCleanStart() && StringUtils.isEmpty(msg.getClientIdentifier());
    }

    private void createAndSendConnAckMsg(MqttConnectReturnCode code, ClientSessionCtx ctx) {
        MqttConnAckMessage mqttConnAckMsg = mqttMessageGenerator.createMqttConnAckMsg(code);
        ctx.getChannel().writeAndFlush(mqttConnAckMsg);
    }

    private TopicAliasCtx getTopicAliasCtx(String clientId, MqttConnectMsg msg) {
        MqttProperties.IntegerProperty property = MqttPropertiesUtil.getTopicAliasMaxProperty(msg.getProperties());
        if (property != null) {
            int value = Math.min(property.value(), maxTopicAlias);
            log.debug("Max Topic Alias [{}] received on CONNECT for client {}", value, clientId);
            return value > 0 ? new TopicAliasCtx(true, value) : TopicAliasCtx.DISABLED_TOPIC_ALIASES;
        }
        return TopicAliasCtx.DISABLED_TOPIC_ALIASES;
    }

    private int getSessionExpiryInterval(MqttConnectMsg msg) {
        return MqttPropertiesUtil.getConnectSessionExpiryIntervalValue(msg.getProperties(), maxExpiryInterval);
    }

    int getKeepAliveSeconds(ClientActorStateInfo actorState, MqttConnectMsg msg) {
        var clientId = actorState.getClientId();
        var mqttVersion = actorState.getCurrentSessionCtx().getMqttVersion();

        var keepAliveSeconds = msg.getKeepAliveTimeSeconds();
        if (MqttVersion.MQTT_5 == mqttVersion && keepAliveSeconds > maxServerKeepAlive) {
            log.debug("[{}] Client's keep alive value is greater than allowed, setting keepAlive to server's value {}s", clientId, maxServerKeepAlive);
            keepAliveSeconds = maxServerKeepAlive;
        }
        return keepAliveSeconds;
    }

    int getReceiveMaxValue(MqttConnectMsg msg, ClientSessionCtx ctx) {
        return MqttVersion.MQTT_5 == ctx.getMqttVersion() ?
                MqttPropertiesUtil.getReceiveMaxValue(msg.getProperties()) : mqtt3xReceiveMax;
    }

    @PreDestroy
    public void destroy() {
        log.debug("Shutting down connect handler executor");
        if (connectHandlerExecutor != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(connectHandlerExecutor, "Connect handler");
        }
    }
}
