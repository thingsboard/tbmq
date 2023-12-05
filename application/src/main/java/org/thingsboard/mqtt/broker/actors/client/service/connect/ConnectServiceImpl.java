/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.StringUtils;
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.service.limits.RateLimitService;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ConnectionResponse;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.service.mqtt.keepalive.KeepAliveService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
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

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_ACCEPTED;

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

        if (log.isTraceEnabled()) {
            log.trace("[{}][{}][{}] Processing connect msg.", sessionCtx.getAddress(), clientId, sessionId);
        }

        boolean proceedWithConnection = shouldProceedWithConnection(sessionCtx, msg);
        if (!proceedWithConnection) {
            return;
        }

        int sessionExpiryInterval = getSessionExpiryInterval(msg);
        sessionCtx.setSessionInfo(
                getSessionInfo(msg, sessionId, clientId, sessionCtx.getClientType(),
                        sessionExpiryInterval, actorState.getCurrentSessionCtx().getAddress().getAddress().getAddress())
        );

        sessionCtx.setTopicAliasCtx(getTopicAliasCtx(msg));
        keepAliveService.registerSession(clientId, sessionId, getKeepAliveSeconds(actorState, msg));

        ListenableFuture<ConnectionResponse> connectFuture = clientSessionEventService.requestConnection(sessionCtx.getSessionInfo());
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
                                msg.getKeepAliveTimeSeconds())
                );
            }

            @Override
            public void onFailure(Throwable t) {
                refuseConnection(sessionCtx, t);
            }
        }, connectHandlerExecutor);
    }

    private TopicAliasCtx getTopicAliasCtx(MqttConnectMsg msg) {
        MqttProperties.IntegerProperty property = MqttPropertiesUtil.getTopicAliasMaxProperty(msg.getProperties());
        if (property != null) {
            int value = Math.min(property.value(), maxTopicAlias);
            if (log.isDebugEnabled()) {
                log.debug("Max Topic Alias [{}] received on CONNECT for client {}", value, msg.getClientIdentifier());
            }
            return value > 0 ? new TopicAliasCtx(true, value) : TopicAliasCtx.DISABLED_TOPIC_ALIASES;
        }
        return TopicAliasCtx.DISABLED_TOPIC_ALIASES;
    }

    private int getSessionExpiryInterval(MqttConnectMsg msg) {
        MqttProperties.IntegerProperty property = (MqttProperties.IntegerProperty) msg.getProperties()
                .getProperty(MqttProperties.MqttPropertyType.SESSION_EXPIRY_INTERVAL.value());
        if (property != null) {
            return Math.min(property.value(), maxExpiryInterval);
        }
        return 0;
    }

    int getKeepAliveSeconds(ClientActorStateInfo actorState, MqttConnectMsg msg) {
        var clientId = actorState.getClientId();
        var mqttVersion = actorState.getCurrentSessionCtx().getMqttVersion();

        var keepAliveSeconds = msg.getKeepAliveTimeSeconds();
        if (MqttVersion.MQTT_5 == mqttVersion && keepAliveSeconds > maxServerKeepAlive) {
            if (log.isDebugEnabled()) {
                log.debug("[{}] Client's keep alive value is greater than allowed, setting keepAlive to server's value {}s", clientId, maxServerKeepAlive);
            }
            keepAliveSeconds = maxServerKeepAlive;
        }
        return keepAliveSeconds;
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

        if (log.isDebugEnabled()) {
            log.debug("[{}] [{}] Client connected!", actorState.getClientId(), actorState.getCurrentSessionId());
        }

        clientSessionCtxService.registerSession(sessionCtx);

        if (sessionCtx.getSessionInfo().isPersistent()) {
            msgPersistenceManager.startProcessingPersistedMessages(actorState, connectionAcceptedMsg.isSessionPresent());
            startProcessingSharedSubscriptionsIfPresent(sessionCtx);
        }

        actorState.getQueuedMessages().process(msg -> messageHandler.process(sessionCtx, msg, actorRef));
    }

    private void startProcessingSharedSubscriptionsIfPresent(ClientSessionCtx sessionCtx) {
        Set<TopicSharedSubscription> subscriptions = clientSubscriptionCache.getClientSharedSubscriptions(sessionCtx.getClientId());
        if (!CollectionUtils.isEmpty(subscriptions)) {
            msgPersistenceManager.startProcessingSharedSubscriptions(sessionCtx, subscriptions);
        }
    }

    private void pushConnAckMsg(ClientActorStateInfo actorState, ConnectionAcceptedMsg msg) {
        ClientSessionCtx sessionCtx = actorState.getCurrentSessionCtx();
        var sessionPresent = msg.isSessionPresent();
        var assignedClientId = actorState.isClientIdGenerated() ? actorState.getClientId() : null;
        var keepAliveSecs = Math.min(msg.getKeepAliveTimeSeconds(), maxServerKeepAlive);
        var sessionExpiryInterval = actorState.getCurrentSessionCtx().getSessionInfo().getSessionExpiryInterval();
        var maxTopicAlias = actorState.getCurrentSessionCtx().getTopicAliasCtx().getMaxTopicAlias();
        MqttConnAckMessage mqttConnAckMsg = createMqttConnAckMsg(
                sessionPresent,
                assignedClientId,
                keepAliveSecs,
                sessionExpiryInterval,
                maxTopicAlias);
        sessionCtx.getChannel().writeAndFlush(mqttConnAckMsg);
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
        return new MqttDisconnectMsg(sessionId,
                new DisconnectReason(DisconnectReasonType.ON_ERROR, BrokerConstants.FAILED_TO_CONNECT_CLIENT_MSG));
    }

    private MqttConnAckMessage createMqttConnAckMsg(boolean sessionPresent, String assignedClientId,
                                                    int keepAliveTimeSeconds, int sessionExpiryInterval,
                                                    int maxTopicAlias) {
        return mqttMessageGenerator.createMqttConnAckMsg(
                CONNECTION_ACCEPTED,
                sessionPresent,
                assignedClientId,
                keepAliveTimeSeconds,
                sessionExpiryInterval,
                maxTopicAlias);
    }

    private void logConnectionRefused(Throwable t, ClientSessionCtx clientSessionCtx) {
        if (t == null) {
            if (log.isDebugEnabled()) {
                log.debug("[{}][{}] Client wasn't connected.", clientSessionCtx.getClientId(), clientSessionCtx.getSessionId());
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("[{}][{}] Client wasn't connected.", clientSessionCtx.getClientId(), clientSessionCtx.getSessionId(), t);
            }
        }
    }

    SessionInfo getSessionInfo(MqttConnectMsg msg, UUID sessionId, String clientId,
                               ClientType clientType, int sessionExpiryInterval, byte[] clientIpAdr) {
        return ClientSessionInfoFactory.getSessionInfo(
                sessionId,
                msg.isCleanStart(),
                serviceInfoProvider.getServiceId(),
                new ClientInfo(clientId, clientType, clientIpAdr),
                ClientSessionInfoFactory.getConnectionInfo(msg.getKeepAliveTimeSeconds()),
                sessionExpiryInterval);
    }

    boolean shouldProceedWithConnection(ClientSessionCtx ctx, MqttConnectMsg msg) {
        if (isPersistentClientWithoutClientId(msg)) {
            log.warn("[{}] Client identifier is empty and clean session flag is set to false!", ctx.getSessionId());
            createAndSendConnAckMsg(MqttReasonCodeResolver.connectionRefusedClientIdNotValid(ctx), ctx);
            disconnect(msg.getClientIdentifier(), ctx.getSessionId());
            return false;
        }
        if (!rateLimitService.checkSessionsLimit(msg.getClientIdentifier())) {
            createAndSendConnAckMsg(MqttReasonCodeResolver.connectionRefusedQuotaExceeded(ctx), ctx);
            disconnect(msg.getClientIdentifier(), ctx.getSessionId());
            return false;
        }
        return true;
    }

    private boolean isPersistentClientWithoutClientId(MqttConnectMsg msg) {
        return !msg.isCleanStart() && StringUtils.isEmpty(msg.getClientIdentifier());
    }

    private void createAndSendConnAckMsg(MqttConnectReturnCode code, ClientSessionCtx ctx) {
        MqttConnAckMessage mqttConnAckMsg = createMqttConnAckMsg(code);
        ctx.getChannel().writeAndFlush(mqttConnAckMsg);
    }

    private MqttConnAckMessage createMqttConnAckMsg(MqttConnectReturnCode code) {
        return mqttMessageGenerator.createMqttConnAckMsg(code);
    }

    @PreDestroy
    public void destroy() {
        if (log.isDebugEnabled()) {
            log.debug("Shutting down executors");
        }
        if (connectHandlerExecutor != null) {
            connectHandlerExecutor.shutdownNow();
        }
    }
}
