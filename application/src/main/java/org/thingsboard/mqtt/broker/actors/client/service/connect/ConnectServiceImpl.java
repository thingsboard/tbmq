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
import org.springframework.util.StringUtils;
import org.thingsboard.mqtt.broker.actors.TbActorRef;
import org.thingsboard.mqtt.broker.actors.client.messages.ConnectionAcceptedMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttConnectMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.actors.client.service.MqttMessageHandlerImpl;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorStateInfo;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ConnectionResponse;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.service.mqtt.keepalive.KeepAliveService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.mqtt.will.LastWillService;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;
import org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory;
import org.thingsboard.mqtt.broker.util.MqttReasonCodeResolver;

import javax.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_ACCEPTED;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConnectServiceImpl implements ConnectService {

    private final ExecutorService connectHandlerExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2,
            ThingsBoardThreadFactory.forName("connect-handler-executor"));

    private final ClientMqttActorManager clientMqttActorManager;
    private final MqttMessageGenerator mqttMessageGenerator;
    private final ClientSessionEventService clientSessionEventService;
    private final KeepAliveService keepAliveService;
    private final ServiceInfoProvider serviceInfoProvider;
    private final LastWillService lastWillService;
    private final ClientSessionCtxService clientSessionCtxService;
    private final MsgPersistenceManager msgPersistenceManager;
    private final MqttMessageHandlerImpl messageHandler;

    @Setter
    @Value("${mqtt.keep-alive.max-keep-alive:600}")
    private int maxServerKeepAlive;
    @Value("${mqtt.client-session-expiry.max-expiry-interval:604800}")
    private int maxExpiryInterval;

    @Override
    public void startConnection(ClientActorStateInfo actorState, MqttConnectMsg msg) throws MqttException {
        UUID sessionId = actorState.getCurrentSessionId();
        ClientSessionCtx sessionCtx = actorState.getCurrentSessionCtx();
        String clientId = actorState.getClientId();

        if (log.isTraceEnabled()) {
            log.trace("[{}][{}][{}] Processing connect msg.", sessionCtx.getAddress(), clientId, sessionId);
        }

        validate(sessionCtx, msg);

        int sessionExpiryInterval = getSessionExpiryInterval(msg);
        sessionCtx.setSessionInfo(
                getSessionInfo(msg, sessionId, clientId, sessionCtx.getClientType(),
                        sessionExpiryInterval, actorState.getCurrentSessionCtx().getAddress().getHostName())
        );

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

        log.debug("[{}] [{}] Client connected!", actorState.getClientId(), actorState.getCurrentSessionId());

        clientSessionCtxService.registerSession(sessionCtx);

        if (sessionCtx.getSessionInfo().isPersistent()) {
            msgPersistenceManager.startProcessingPersistedMessages(actorState, connectionAcceptedMsg.isSessionPresent());
        }

        actorState.getQueuedMessages().process(msg -> messageHandler.process(sessionCtx, msg, actorRef));
    }

    private void pushConnAckMsg(ClientActorStateInfo actorState, ConnectionAcceptedMsg msg) {
        ClientSessionCtx sessionCtx = actorState.getCurrentSessionCtx();
        var sessionPresent = msg.isSessionPresent();
        var assignedClientId = actorState.isClientIdGenerated() ? actorState.getClientId() : null;
        var keepAliveSecs = Math.min(msg.getKeepAliveTimeSeconds(), maxServerKeepAlive);
        var sessionExpiryInterval = actorState.getCurrentSessionCtx().getSessionInfo().getSessionExpiryInterval();
        MqttConnAckMessage mqttConnAckMsg = createMqttConnAckMsg(sessionPresent, assignedClientId, keepAliveSecs, sessionExpiryInterval);
        sessionCtx.getChannel().writeAndFlush(mqttConnAckMsg);
    }

    void refuseConnection(ClientSessionCtx clientSessionCtx, Throwable t) {
        logConnectionRefused(t, clientSessionCtx);

        sendConnectionRefusedMsgAndDisconnect(clientSessionCtx);
    }

    private void sendConnectionRefusedMsgAndDisconnect(ClientSessionCtx clientSessionCtx) {
        try {
            MqttConnectReturnCode code = MqttReasonCodeResolver.connectionRefusedServerUnavailable(clientSessionCtx);
            MqttConnAckMessage mqttConnAckMsg = createMqttConnAckMsg(code);
            clientSessionCtx.getChannel().writeAndFlush(mqttConnAckMsg);
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to send CONN_ACK response.",
                    clientSessionCtx.getClientId(), clientSessionCtx.getSessionId());
        } finally {
            disconnect(clientSessionCtx);
        }
    }

    private void disconnect(ClientSessionCtx clientSessionCtx) {
        clientMqttActorManager.disconnect(
                clientSessionCtx.getClientId(), newDisconnectMsg(clientSessionCtx.getSessionId()));
    }

    private MqttDisconnectMsg newDisconnectMsg(UUID sessionId) {
        return new MqttDisconnectMsg(sessionId,
                new DisconnectReason(DisconnectReasonType.ON_ERROR, BrokerConstants.FAILED_TO_CONNECT_CLIENT_MSG));
    }

    private MqttConnAckMessage createMqttConnAckMsg(MqttConnectReturnCode code) {
        return mqttMessageGenerator.createMqttConnAckMsg(code);
    }

    private MqttConnAckMessage createMqttConnAckMsg(boolean sessionPresent, String assignedClientId,
                                                    int keepAliveTimeSeconds, int sessionExpiryInterval) {
        return mqttMessageGenerator.createMqttConnAckMsg(
                CONNECTION_ACCEPTED,
                sessionPresent,
                assignedClientId,
                keepAliveTimeSeconds,
                sessionExpiryInterval);
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
                               ClientType clientType, int sessionExpiryInterval, String clientIpAdr) {
        return ClientSessionInfoFactory.getSessionInfo(
                sessionId,
                msg.isCleanStart(),
                serviceInfoProvider.getServiceId(),
                new ClientInfo(clientId, clientType, clientIpAdr),
                ClientSessionInfoFactory.getConnectionInfo(msg.getKeepAliveTimeSeconds()),
                sessionExpiryInterval);
    }

    void validate(ClientSessionCtx ctx, MqttConnectMsg msg) {
        if (!msg.isCleanStart() && StringUtils.isEmpty(msg.getClientIdentifier())) {
            MqttConnectReturnCode code = MqttReasonCodeResolver.connectionRefusedClientIdNotValid(ctx);
            MqttConnAckMessage mqttConnAckMsg = createMqttConnAckMsg(code);
            ctx.getChannel().writeAndFlush(mqttConnAckMsg);
            throw new MqttException("Client identifier is empty and 'clean session' flag is set to 'false'!");
        }
    }

    @PreDestroy
    public void destroy() {
        if (log.isDebugEnabled()) {
            log.debug("Shutting down executors");
        }
        connectHandlerExecutor.shutdownNow();
    }
}
