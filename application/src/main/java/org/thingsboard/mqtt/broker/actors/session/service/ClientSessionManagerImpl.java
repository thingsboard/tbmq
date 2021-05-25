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

import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.session.messages.ClearSessionMsg;
import org.thingsboard.mqtt.broker.actors.session.messages.ConnectionRequestInfo;
import org.thingsboard.mqtt.broker.actors.session.messages.ConnectionRequestMsg;
import org.thingsboard.mqtt.broker.actors.session.messages.SessionDisconnectedMsg;
import org.thingsboard.mqtt.broker.actors.session.messages.TryConnectMsg;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.util.DonAsynchron;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgHeaders;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.DefaultTbQueueMsgHeaders;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ClientSessionEventQueueFactory;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;
import org.thingsboard.mqtt.broker.service.mqtt.client.disconnect.DisconnectClientCommandService;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventType;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionService;
import org.thingsboard.mqtt.broker.service.subscription.SubscriptionManager;
import org.thingsboard.mqtt.broker.util.BytesUtil;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventConst.REQUEST_ID_HEADER;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientSessionManagerImpl implements ClientSessionManager {
    private final ScheduledExecutorService timeoutExecutor = Executors.newSingleThreadScheduledExecutor();

    @Value("${queue.client-session-event-response.max-request-timeout}")
    private long requestTimeout;

    private final ClientSessionService clientSessionService;
    private final SubscriptionManager subscriptionManager;
    // TODO: move this to Actor state (on DISCONNECTED check pending connection requests + schedule RETRY_DISCONNECT_COMMAND message for an actor)
    private final DisconnectClientCommandService disconnectClientCommandService;
    private final ClientSessionEventQueueFactory clientSessionEventQueueFactory;
    private final ServiceInfoProvider serviceInfoProvider;

    private TbQueueProducer<TbProtoQueueMsg<QueueProtos.ClientSessionEventResponseProto>> eventResponseProducer;

    @PostConstruct
    public void init() {
        this.eventResponseProducer = clientSessionEventQueueFactory.createEventResponseProducer(serviceInfoProvider.getServiceId());
    }

    @Override
    public void processConnectionRequest(ConnectionRequestMsg connectionRequestMsg, Consumer<TryConnectMsg> tryConnectMsgPublisher) {
        SessionInfo sessionInfo = connectionRequestMsg.getSessionInfo();
        String clientId = sessionInfo.getClientInfo().getClientId();
        UUID sessionId = sessionInfo.getSessionId();
        ConnectionRequestInfo requestInfo = connectionRequestMsg.getRequestInfo();
        // TODO: it's possible that we get not-relevant data since consumer didn't get message yet
        //      this can happen if node just got control over this clientId
        //      Solutions:
        //          - can force wait till the end of the topic using dummy session
        //          - gain more control over reassigning partitions + do some sync logic on reassign
        //          - remake logic to ensure that the 'leader' for client has all information (look at Raft algorithm)
        //          - save with 'version' field and if ClientSession listener encounters version conflict - merge two values or do smth else (at least log that smth is wrong)
        ClientSession currentlyConnectedSession = clientSessionService.getClientSession(clientId);
        if (currentlyConnectedSession == null || !currentlyConnectedSession.isConnected()) {
            saveClientSession(sessionInfo, requestInfo);
            return;
        }

        UUID currentlyConnectedSessionId = currentlyConnectedSession.getSessionInfo().getSessionId();
        if (sessionId.equals(currentlyConnectedSessionId)) {
            log.warn("[{}][{}] Got CONNECT request from already connected session.", clientId, currentlyConnectedSessionId);
            return;
        }

        SettableFuture<Void> future = disconnectClientCommandService.startWaitingForDisconnect(sessionId, currentlyConnectedSessionId, clientId);
        DonAsynchron.withCallbackAndTimeout(future,
                unused -> tryConnectMsgPublisher.accept(new TryConnectMsg(sessionInfo, requestInfo)),
                t -> onDisconnectRejected(clientId, sessionId, requestInfo, currentlyConnectedSessionId, t),
                requestTimeout, timeoutExecutor, MoreExecutors.directExecutor());

        log.trace("[{}] Disconnecting currently connected client session, sessionId - {}.", clientId, currentlyConnectedSessionId);
        disconnectClientCommandService.disconnectSession(sessionInfo.getServiceId(), clientId, currentlyConnectedSessionId);
    }

    @Override
    public void tryConnectSession(TryConnectMsg tryConnectMsg) {
        SessionInfo sessionInfo = tryConnectMsg.getSessionInfo();
        String clientId = sessionInfo.getClientInfo().getClientId();

        ClientSession currentlyConnectedSession = clientSessionService.getClientSession(clientId);
        if (currentlyConnectedSession != null && currentlyConnectedSession.isConnected()) {
            log.debug("[{}] Client session is already connected.", clientId);
        } else {
            saveClientSession(sessionInfo, tryConnectMsg.getRequestInfo());
        }
    }

    private void onDisconnectRejected(String clientId, UUID sessionId, ConnectionRequestInfo requestInfo, UUID currentlyConnectedSessionId, Throwable t) {
        log.debug("[{}][{}] Failed to process connection request. Exception - {}, reason - {}", clientId, requestInfo.getRequestId(), t.getClass().getSimpleName(), t.getMessage());
        log.trace("Detailed error: ", t);
        if (t instanceof TimeoutException) {
            disconnectClientCommandService.clearWaitingFuture(sessionId, currentlyConnectedSessionId, clientId);
        }
        if (isRequestTimedOut(requestInfo.getRequestTime())) {
            log.debug("[{}][{}] Connection request timed out.", clientId, requestInfo.getRequestId());
        } else {
            sendEventResponse(clientId, requestInfo, false);
        }
    }

    @Override
    public void processSessionDisconnected(String clientId, SessionDisconnectedMsg sessionDisconnectedMsg) {
        UUID sessionId = sessionDisconnectedMsg.getSessionId();

        ClientSession clientSession = clientSessionService.getClientSession(clientId);
        if (clientSession == null) {
            log.warn("[{}][{}] Cannot find client session.", clientId, sessionId);
            return;
        }
        if (!sessionId.equals(clientSession.getSessionInfo().getSessionId())) {
            log.warn("[{}] Got disconnected event from the session with different sessionId. Currently connected sessionId - {}, " +
                    "received sessionId - {}.", clientId, clientSession.getSessionInfo().getSessionId(), sessionId);
            return;
        }
        if (!clientSession.isConnected()) {
            log.debug("[{}] Client session is already disconnected.", clientId);
            return;
        }

        if (clientSession.getSessionInfo().isPersistent()) {
            ClientSession disconnectedClientSession = clientSession.toBuilder().connected(false).build();
            clientSessionService.saveClientSession(clientId, disconnectedClientSession);
        } else {
            clientSessionService.clearClientSession(clientId);
            subscriptionManager.clearSubscriptions(clientId);
        }

        disconnectClientCommandService.notifyWaitingSession(clientId, sessionId);
    }

    @Override
    public void processClearSession(String clientId, ClearSessionMsg clearSessionMsg) {
        // TODO: get session from the state
        ClientSession clientSession = clientSessionService.getClientSession(clientId);
        UUID currentSessionId = clientSession.getSessionInfo().getSessionId();
        if (!currentSessionId.equals(clearSessionMsg.getSessionId())) {
            log.info("[{}][{}] Ignoring {} for session - {}.", clientId, currentSessionId, ClientSessionEventType.TRY_CLEAR_SESSION_REQUEST, clearSessionMsg.getSessionId());
        } else if (clientSession.isConnected()) {
            log.info("[{}][{}] Is connected now, ignoring {}.", clientId, currentSessionId, ClientSessionEventType.TRY_CLEAR_SESSION_REQUEST);
        } else {
            log.debug("[{}][{}] Clearing client session.", clientId, currentSessionId);
            clientSessionService.clearClientSession(clientId);
            subscriptionManager.clearSubscriptions(clientId);
        }
    }

    private void saveClientSession(SessionInfo sessionInfo, ConnectionRequestInfo connectionRequestInfo) {
        ClientInfo clientInfo = sessionInfo.getClientInfo();

        if (isRequestTimedOut(connectionRequestInfo.getRequestTime())) {
            log.debug("[{}][{}] Connection request timed out.", clientInfo.getClientId(), connectionRequestInfo.getRequestId());
            return;
        }

        ClientSession clientSession = ClientSession.builder()
                .connected(true)
                .sessionInfo(sessionInfo)
                .build();
        clientSessionService.saveClientSession(clientInfo.getClientId(), clientSession);

        sendEventResponse(clientInfo.getClientId(), connectionRequestInfo, true);
    }

    private void sendEventResponse(String clientId, ConnectionRequestInfo connectionRequestInfo, boolean successfulConnect) {
        QueueProtos.ClientSessionEventResponseProto response = QueueProtos.ClientSessionEventResponseProto.newBuilder()
                .setSuccess(successfulConnect).build();
        TbQueueMsgHeaders headers = createResponseHeaders(connectionRequestInfo.getRequestId());
        eventResponseProducer.send(connectionRequestInfo.getResponseTopic(), new TbProtoQueueMsg<>(clientId, response, headers), new TbQueueCallback() {
            @Override
            public void onSuccess(TbQueueMsgMetadata metadata) {
                log.trace("[{}][{}] Successfully sent response.", clientId, connectionRequestInfo.getRequestId());
            }
            @Override
            public void onFailure(Throwable t) {
                log.debug("[{}][{}] Failed to send response. Exception - {}, reason - {}.", clientId, connectionRequestInfo.getRequestId(), t.getClass().getSimpleName(), t.getMessage());
                log.trace("Detailed error: ", t);
            }
        });
    }

    private DefaultTbQueueMsgHeaders createResponseHeaders(UUID requestId) {
        DefaultTbQueueMsgHeaders headers = new DefaultTbQueueMsgHeaders();
        headers.put(REQUEST_ID_HEADER, BytesUtil.uuidToBytes(requestId));
        return headers;
    }

    private boolean isRequestTimedOut(long requestTime) {
        return requestTime + requestTimeout < System.currentTimeMillis();
    }

    @PreDestroy
    public void destroy() {
        if (eventResponseProducer != null) {
            eventResponseProducer.stop();
        }
        timeoutExecutor.shutdownNow();
    }
}
