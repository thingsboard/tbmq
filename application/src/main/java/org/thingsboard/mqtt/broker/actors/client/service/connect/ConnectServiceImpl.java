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
package org.thingsboard.mqtt.broker.actors.client.service.connect;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thingsboard.mqtt.broker.actors.client.messages.ConnectionAcceptedMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttConnectMsg;
import org.thingsboard.mqtt.broker.actors.client.service.MqttMessageHandlerImpl;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorStateInfo;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.client.MqttClientWrapperService;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionReader;
import org.thingsboard.mqtt.broker.service.mqtt.keepalive.KeepAliveService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.mqtt.will.LastWillService;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import javax.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_ACCEPTED;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConnectServiceImpl implements ConnectService {
    private final ExecutorService connectHandlerExecutor = Executors.newCachedThreadPool(ThingsBoardThreadFactory.forName("connect-handler-executor"));

    private final ClientMqttActorManager clientMqttActorManager;
    private final MqttMessageGenerator mqttMessageGenerator;
    private final MqttClientWrapperService mqttClientService;
    private final ClientSessionEventService clientSessionEventService;
    private final ClientSessionReader clientSessionReader;
    private final KeepAliveService keepAliveService;
    private final ServiceInfoProvider serviceInfoProvider;
    private final LastWillService lastWillService;
    private final ClientSessionCtxService clientSessionCtxService;
    private final ClientSubscriptionService clientSubscriptionService;
    private final MsgPersistenceManager msgPersistenceManager;
    private final MqttMessageHandlerImpl messageHandler;

    @Override
    public void startConnection(ClientActorStateInfo actorState, MqttConnectMsg msg) throws MqttException {
        UUID sessionId = actorState.getCurrentSessionId();
        ClientSessionCtx sessionCtx = actorState.getCurrentSessionCtx();
        String clientId = actorState.getClientId();

        log.trace("[{}][{}] Processing connect msg.", clientId, sessionId);

        validate(sessionCtx, msg);

        sessionCtx.setSessionInfo(getSessionInfo(msg, sessionId, clientId));

        keepAliveService.registerSession(clientId, sessionId, msg.getKeepAliveTimeSeconds());

        ClientSession prevSession = clientSessionReader.getClientSession(clientId);
        boolean isPrevSessionPersistent = prevSession != null && prevSession.getSessionInfo().isPersistent();

        ListenableFuture<Boolean> connectFuture = clientSessionEventService.requestConnection(sessionCtx.getSessionInfo());
        Futures.addCallback(connectFuture, new FutureCallback<>() {
            @Override
            public void onSuccess(Boolean successfulConnection) {
                if (successfulConnection) {
                    clientMqttActorManager.notifyConnectionAccepted(clientId, sessionId, isPrevSessionPersistent, msg.getLastWillMsg());
                } else {
                    refuseConnection(sessionCtx, null);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                refuseConnection(sessionCtx, t);
            }
        }, connectHandlerExecutor);
    }

    @Override
    public void acceptConnection(ClientActorStateInfo actorState, ConnectionAcceptedMsg connectionAcceptedMsg) {
        ClientSessionCtx sessionCtx = actorState.getCurrentSessionCtx();
        SessionInfo sessionInfo = sessionCtx.getSessionInfo();
        ClientInfo clientInfo = sessionInfo.getClientInfo();

        if (connectionAcceptedMsg.getLastWillMsg() != null) {
            lastWillService.saveLastWillMsg(sessionInfo, connectionAcceptedMsg.getLastWillMsg());
        }

        boolean isCurrentSessionPersistent = sessionInfo.isPersistent();
        if (connectionAcceptedMsg.isPrevSessionPersistent() && !isCurrentSessionPersistent) {
            log.debug("[{}][{}] Clearing persisted session.", clientInfo.getType(), clientInfo.getClientId());
            clientSubscriptionService.clearSubscriptions(clientInfo.getClientId());
            msgPersistenceManager.clearPersistedMessages(clientInfo);
        }

        sessionCtx.getChannel().writeAndFlush(mqttMessageGenerator.createMqttConnAckMsg(CONNECTION_ACCEPTED,
                connectionAcceptedMsg.isPrevSessionPersistent() && isCurrentSessionPersistent));
        log.info("[{}] [{}] Client connected!", actorState.getClientId(), actorState.getCurrentSessionId());

        clientSessionCtxService.registerSession(sessionCtx);

        if (sessionCtx.getSessionInfo().isPersistent()) {
            msgPersistenceManager.startProcessingPersistedMessages(actorState);
        }

        actorState.getQueuedMessages().process(msg -> messageHandler.process(sessionCtx, msg));
    }

    private void refuseConnection(ClientSessionCtx clientSessionCtx, Throwable t) {
        String clientId = clientSessionCtx.getClientId();
        UUID sessionId = clientSessionCtx.getSessionId();
        if (t == null) {
            log.debug("[{}][{}] Client wasn't connected.", clientId, sessionId);
        } else {
            log.debug("[{}][{}] Client wasn't connected. Exception - {}, reason - {}.", clientId, sessionId, t.getClass().getSimpleName(), t.getMessage());
            log.trace("Detailed error: ", t);
        }
        try {
            clientSessionCtx.getChannel().writeAndFlush(mqttMessageGenerator.createMqttConnAckMsg(CONNECTION_REFUSED_IDENTIFIER_REJECTED, false));
        } catch (Exception e) {
            log.trace("[{}][{}] Failed to send CONN_ACK response.", clientId, sessionId);
        } finally {
            clientMqttActorManager.disconnect(clientId, sessionId,
                    new DisconnectReason(DisconnectReasonType.ON_ERROR, "Failed to connect client"));
        }
    }

    private SessionInfo getSessionInfo(MqttConnectMsg msg, UUID sessionId, String clientId) {
        ClientInfo clientInfo = mqttClientService.getMqttClient(clientId)
                .map(mqttClient -> new ClientInfo(mqttClient.getClientId(), mqttClient.getType()))
                .orElse(new ClientInfo(clientId, ClientType.DEVICE));
        boolean isPersistentSession = !msg.isCleanSession();
        return SessionInfo.builder()
                .serviceId(serviceInfoProvider.getServiceId())
                .sessionId(sessionId).persistent(isPersistentSession).clientInfo(clientInfo).build();
    }

    private void validate(ClientSessionCtx ctx, MqttConnectMsg msg) {
        if (!msg.isCleanSession() && StringUtils.isEmpty(msg.getClientIdentifier())) {
            ctx.getChannel().writeAndFlush(mqttMessageGenerator.createMqttConnAckMsg(CONNECTION_REFUSED_IDENTIFIER_REJECTED, false));
            throw new MqttException("Client identifier is empty and 'clean session' flag is set to 'false'!");
        }
    }

    @PreDestroy
    public void destroy() {
        log.debug("Shutting down executors");
        connectHandlerExecutor.shutdownNow();
    }
}
