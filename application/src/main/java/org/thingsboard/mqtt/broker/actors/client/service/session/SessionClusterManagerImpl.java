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
package org.thingsboard.mqtt.broker.actors.client.service.session;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thingsboard.mqtt.broker.actors.client.messages.ClientCallback;
import org.thingsboard.mqtt.broker.actors.client.messages.ConnectionRequestInfo;
import org.thingsboard.mqtt.broker.actors.client.messages.cluster.SessionDisconnectedMsg;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientSession;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.ConnectionInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.util.CallbackUtil;
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgHeaders;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.DefaultTbQueueMsgHeaders;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ClientSessionEventQueueFactory;
import org.thingsboard.mqtt.broker.service.mqtt.client.disconnect.DisconnectClientCommandService;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventType;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.topic.ApplicationRemovedEventService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.topic.ApplicationTopicService;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.util.BytesUtil;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.thingsboard.mqtt.broker.common.data.util.CallbackUtil.createCallback;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionClusterManagerImpl implements SessionClusterManager {

    private final ScheduledExecutorService timeoutExecutor = Executors.newSingleThreadScheduledExecutor();

    private final ClientSessionService clientSessionService;
    private final ClientSubscriptionService clientSubscriptionService;
    private final DisconnectClientCommandService disconnectClientCommandService;
    private final ClientSessionEventQueueFactory clientSessionEventQueueFactory;
    private final ServiceInfoProvider serviceInfoProvider;
    private final MsgPersistenceManager msgPersistenceManager;
    private final ApplicationRemovedEventService applicationRemovedEventService;
    private final ApplicationTopicService applicationTopicService;

    private TbQueueProducer<TbProtoQueueMsg<QueueProtos.ClientSessionEventResponseProto>> eventResponseProducer;
    private ExecutorService eventResponseSenderExecutor;

    @Value("${queue.client-session-event-response.max-request-timeout}")
    private long requestTimeout;
    @Value("${queue.client-session-event-response.response-sender-threads}")
    private int eventResponseSenderThreads;

    @PostConstruct
    public void init() {
        this.eventResponseProducer = clientSessionEventQueueFactory.createEventResponseProducer(serviceInfoProvider.getServiceId());
        this.eventResponseSenderExecutor = Executors.newFixedThreadPool(eventResponseSenderThreads);
    }

    // TODO: it's possible that we get not-relevant data since consumer didn't get message yet, this can happen if node just got control over this clientId. Solutions:
    //          - force wait till the end of the topic using dummy session
    //          - gain more control over reassigning partitions + do some sync logic on reassign
    //          - remake logic to ensure that the 'leader' for client has all information (look at Raft algorithm)
    //          - save with 'version' field and if ClientSession listener encounters version conflict - merge two values or do smth else (at least log that smth is wrong)

    @Override
    public void processConnectionRequest(SessionInfo sessionInfo, ConnectionRequestInfo requestInfo) {
        // It is possible that for some time sessions can be connected with the same clientId to different Nodes
        String clientId = sessionInfo.getClientInfo().getClientId();

        if (isRequestTimedOut(requestInfo.getRequestTime())) {
            log.warn("[{}][{}] Connection request timed out.", clientId, requestInfo.getRequestId());
            return;
        }

        if (log.isTraceEnabled()) {
            log.trace("[{}] Processing connection request, sessionId - {}", clientId, sessionInfo.getSessionId());
        }

        ClientSession currentClientSession = getClientSessionForClient(clientId);
        UUID currentClientSessionId = getCurrentClientSessionIdIfPresent(currentClientSession);

        if (sessionInfo.getSessionId().equals(currentClientSessionId)) {
            log.warn("[{}][{}] Got CONNECT request from already present session.", clientId, currentClientSessionId);
            sendEventResponse(clientId, requestInfo, false, false);
            return;
        }

        if (currentClientSessionPresentAndConnected(currentClientSession)) {
            disconnectCurrentSession(currentClientSession, currentClientSessionId, sessionInfo);
        }

        PreviousSessionInfo previousSessionInfo = getPreviousSessionInfo(currentClientSession);
        updateClientSession(sessionInfo, requestInfo, previousSessionInfo);
    }

    void updateClientSession(SessionInfo sessionInfo, ConnectionRequestInfo connectionRequestInfo,
                             PreviousSessionInfo previousSessionInfo) {
        ClientInfo clientInfo = sessionInfo.getClientInfo();
        boolean sessionPresent = previousSessionInfo != null;
        if (log.isTraceEnabled()) {
            log.trace("[{}] Updating client session.", clientInfo.getClientId());
        }

        AtomicBoolean wasErrorProcessed = new AtomicBoolean(false);
        AtomicInteger finishedOperations = new AtomicInteger(0);

        boolean needClearPreviousSession = sessionPresent && sessionInfo.isCleanStart();
        if (needClearPreviousSession) {
            if (log.isTraceEnabled()) {
                log.trace("[{}][{}] Clearing prev persisted session.", clientInfo.getType(), clientInfo.getClientId());
            }
            clearSubscriptionsAndSendResponseIfReady(clientInfo, connectionRequestInfo, finishedOperations, wasErrorProcessed);
            clearPersistedMessages(clientInfo);
        }

        boolean applicationRemoved = isApplicationRemoved(previousSessionInfo, clientInfo);
        if (applicationRemoved) {
            clearPersistedMessages(clientInfo);
            applicationRemovedEventService.sendApplicationRemovedEvent(clientInfo.getClientId());
        }

        ClientSession clientSession = prepareClientSession(sessionInfo);
        clientSessionService.saveClientSession(clientInfo.getClientId(), clientSession, createCallback(
                () -> {
                    if (!needClearPreviousSession || finishedOperations.incrementAndGet() >= 2) {
                        sendEventResponse(clientInfo.getClientId(), connectionRequestInfo, true, sessionPresent);
                    }
                },
                t -> {
                    if (!needClearPreviousSession || !wasErrorProcessed.getAndSet(true)) {
                        sendEventResponse(clientInfo.getClientId(), connectionRequestInfo, false, sessionPresent);
                    }
                }));
    }

    @Override
    public void processSessionDisconnected(String clientId, SessionDisconnectedMsg msg) {
        UUID sessionId = msg.getSessionId();
        ClientSession clientSession = getClientSessionForClient(clientId);
        if (clientSession == null) {
            if (log.isDebugEnabled()) {
                log.debug("[{}][{}] Cannot find client session.", clientId, sessionId);
            }
        } else {
            UUID currentSessionId = getSessionIdFromClientSession(clientSession);
            if (!sessionId.equals(currentSessionId)) {
                if (log.isDebugEnabled()) {
                    log.debug("[{}] Got disconnected event from the session with different sessionId. Currently connected sessionId - {}, " +
                            "received sessionId - {}.", clientId, currentSessionId, sessionId);
                }
            } else if (!clientSession.isConnected()) {
                if (log.isDebugEnabled()) {
                    log.debug("[{}] Client session is already disconnected.", clientId);
                }
            } else {
                finishDisconnect(clientSession, false, msg.getSessionExpiryInterval());
            }
        }
    }

    @Override
    public void processClearSession(String clientId, UUID sessionId) {
        if (StringUtils.isEmpty(clientId) || sessionId == null) {
            throw new RuntimeException("Trying to clear session for empty clientId or sessionId");
        }
        try {
            ClientSession clientSession = getClientSessionForClient(clientId);
            Set<TopicSubscription> clientSubscriptions = getClientSubscriptionsForClient(clientId);
            if (clientSession == null) {
                log.warn("[{}] Trying to clear non-existent session, clearing subscriptions - {}", clientId, clientSubscriptions);
                clearClientSubscriptions(clientId);
            } else {
                UUID currentSessionId = getSessionIdFromClientSession(clientSession);
                if (!sessionId.equals(currentSessionId)) {
                    log.info("[{}][{}] Ignoring {} for session - {}.",
                            clientId, currentSessionId, ClientSessionEventType.TRY_CLEAR_SESSION_REQUEST, sessionId);
                } else if (clientSession.isConnected()) {
                    log.info("[{}][{}] Session is connected now, ignoring {}.",
                            clientId, currentSessionId, ClientSessionEventType.TRY_CLEAR_SESSION_REQUEST);
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("[{}][{}] Clearing client session.", clientId, currentSessionId);
                    }
                    clearSessionAndSubscriptions(clientId);
                    clearPersistedMessages(clientSession.getSessionInfo().getClientInfo());
                }
            }
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to clear session", clientId, sessionId, e);
        }
    }

    @Override
    public void processRemoveApplicationTopicRequest(String clientId, ClientCallback callback) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Processing REMOVE_APPLICATION_TOPIC_REQUEST_MSG processRemoveApplicationTopicRequest", clientId);
        }
        if (StringUtils.isEmpty(clientId)) {
            callback.onFailure(new RuntimeException("Trying to remove APPLICATION topic for empty clientId"));
            return;
        }
        ClientSession clientSession = getClientSessionForClient(clientId);
        ClientType currentType = clientSession.getSessionInfo().getClientInfo().getType();
        if (currentType == ClientType.APPLICATION) {
            if (log.isDebugEnabled()) {
                log.debug("[{}] Current type of the client is APPLICATION", clientId);
            }
            callback.onSuccess();
            return;
        }

        applicationTopicService.deleteTopic(clientId, CallbackUtil.createCallback(callback::onSuccess, callback::onFailure));
    }

    private void finishDisconnect(ClientSession clientSession, boolean forceClearSession, int sessionExpiryInterval) {
        SessionInfo sessionInfo = clientSession.getSessionInfo();
        String clientId = sessionInfo.getClientInfo().getClientId();
        if (log.isTraceEnabled()) {
            log.trace("[{}] Finishing client session disconnection.", clientId);
        }
        if (forceClearSession) {
            if (sessionInfo.isPersistent()) {
                clearPersistedMessages(clientSession.getSessionInfo().getClientInfo());
            }
            clearSessionAndSubscriptions(clientId);
            return;
        }
        if (sessionInfo.isPersistent()) {
            ClientSession disconnectedClientSession = markSessionDisconnected(clientSession, sessionExpiryInterval);
            saveClientSession(clientId, disconnectedClientSession);
            return;
        }
        clearSessionAndSubscriptions(clientId);
    }

    private ClientSession markSessionDisconnected(ClientSession clientSession, int sessionExpiryInterval) {
        ConnectionInfo connectionInfo = clientSession.getSessionInfo().getConnectionInfo().toBuilder()
                .disconnectedAt(System.currentTimeMillis())
                .build();
        SessionInfo sessionInfo = clientSession.getSessionInfo().toBuilder()
                .connectionInfo(connectionInfo)
                .build();
        sessionInfo = sessionExpiryInterval == -1 ? sessionInfo :
                sessionInfo.toBuilder().sessionExpiryInterval(sessionExpiryInterval).build();
        return clientSession.toBuilder()
                .connected(false)
                .sessionInfo(sessionInfo)
                .build();
    }

    private boolean isApplicationRemoved(PreviousSessionInfo previousSessionInfo, ClientInfo clientInfo) {
        return previousSessionInfo != null && previousSessionInfo.getClientType() == ClientType.APPLICATION
                && clientInfo.getType() == ClientType.DEVICE;
    }

    private ClientSession prepareClientSession(SessionInfo sessionInfo) {
        return ClientSession.builder()
                .connected(true)
                .sessionInfo(sessionInfo)
                .build();
    }

    private void clearSubscriptionsAndSendResponseIfReady(ClientInfo clientInfo, ConnectionRequestInfo connectionRequestInfo,
                                                          AtomicInteger finishedOperations, AtomicBoolean wasErrorProcessed) {
        clientSubscriptionService.clearSubscriptionsAndPersist(clientInfo.getClientId(), createCallback(
                () -> {
                    if (finishedOperations.incrementAndGet() >= 2) {
                        sendEventResponse(clientInfo.getClientId(), connectionRequestInfo, true, false);
                    }
                },
                t -> {
                    if (!wasErrorProcessed.getAndSet(true)) {
                        sendEventResponse(clientInfo.getClientId(), connectionRequestInfo, false, false);
                    }
                }));
    }

    private void sendEventResponse(String clientId, ConnectionRequestInfo connectionRequestInfo, boolean success, boolean sessionPresent) {
        QueueProtos.ClientSessionEventResponseProto response = getEventResponseProto(success, sessionPresent);
        TbQueueMsgHeaders headers = createResponseHeaders(connectionRequestInfo.getRequestId());
        eventResponseSenderExecutor.execute(
                () -> eventResponseProducer.send(
                        connectionRequestInfo.getResponseTopic(),
                        new TbProtoQueueMsg<>(clientId, response, headers),
                        new TbQueueCallback() {
                            @Override
                            public void onSuccess(TbQueueMsgMetadata metadata) {
                                if (log.isTraceEnabled()) {
                                    log.trace("[{}][{}] Successfully sent response.", clientId, connectionRequestInfo.getRequestId());
                                }
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                log.warn("[{}][{}] Failed to send response.", clientId, connectionRequestInfo.getRequestId(), t);
                            }
                        }));
    }

    private QueueProtos.ClientSessionEventResponseProto getEventResponseProto(boolean success, boolean sessionPresent) {
        return QueueProtos.ClientSessionEventResponseProto.newBuilder()
                .setSuccess(success)
                .setSessionPresent(sessionPresent)
                .build();
    }

    private DefaultTbQueueMsgHeaders createResponseHeaders(UUID requestId) {
        DefaultTbQueueMsgHeaders headers = new DefaultTbQueueMsgHeaders();
        headers.put(BrokerConstants.REQUEST_ID_HEADER, BytesUtil.uuidToBytes(requestId));
        return headers;
    }

    boolean isRequestTimedOut(long requestTime) {
        return requestTime + requestTimeout < System.currentTimeMillis();
    }

    private boolean currentClientSessionPresentAndConnected(ClientSession currentClientSession) {
        return currentClientSession != null && currentClientSession.isConnected();
    }

    private void disconnectCurrentSession(String serviceId, String clientId, UUID sessionId, boolean newSessionCleanStart) {
        disconnectClientCommandService.disconnectSession(serviceId, clientId, sessionId, newSessionCleanStart);
    }

    private void clearSessionAndSubscriptions(String clientId) {
        clearClientSession(clientId);
        clearClientSubscriptions(clientId);
    }

    private void clearClientSubscriptions(String clientId) {
        clientSubscriptionService.clearSubscriptionsAndPersist(clientId, null);
    }

    private Set<TopicSubscription> getClientSubscriptionsForClient(String clientId) {
        return clientSubscriptionService.getClientSubscriptions(clientId);
    }

    private void clearPersistedMessages(ClientInfo clientSession) {
        msgPersistenceManager.clearPersistedMessages(clientSession);
    }

    private ClientSession getClientSessionForClient(String clientId) {
        return clientSessionService.getClientSession(clientId);
    }

    private void saveClientSession(String clientId, ClientSession clientSession) {
        clientSessionService.saveClientSession(clientId, clientSession, null);
    }

    private void clearClientSession(String clientId) {
        clientSessionService.clearClientSession(clientId, null);
    }

    private void disconnectCurrentSession(ClientSession currentClientSession, UUID currentClientSessionId, SessionInfo sessionInfo) {
        String clientId = sessionInfo.getClientInfo().getClientId();
        String currentSessionServiceId = currentClientSession.getSessionInfo().getServiceId();
        if (log.isTraceEnabled()) {
            log.trace("[{}] Requesting disconnect of the client session, serviceId - {}, sessionId - {}.",
                    clientId, currentSessionServiceId, currentClientSessionId);
        }
        disconnectCurrentSession(currentSessionServiceId, clientId, currentClientSessionId, sessionInfo.isCleanStart());
        finishDisconnect(currentClientSession, true, -1);
    }

    private UUID getCurrentClientSessionIdIfPresent(ClientSession currentlyConnectedSession) {
        return currentlyConnectedSession == null ? null : getSessionIdFromClientSession(currentlyConnectedSession);
    }

    private PreviousSessionInfo getPreviousSessionInfo(ClientSession currentlyConnectedSession) {
        return currentlyConnectedSession == null ? null : createPreviousSessionInfo(currentlyConnectedSession);
    }

    private PreviousSessionInfo createPreviousSessionInfo(ClientSession currentlyConnectedSession) {
        return new PreviousSessionInfo(currentlyConnectedSession.getSessionInfo().getClientInfo().getType());
    }

    private UUID getSessionIdFromClientSession(ClientSession clientSession) {
        return clientSession.getSessionInfo().getSessionId();
    }

    @PreDestroy
    public void destroy() {
        if (eventResponseProducer != null) {
            eventResponseProducer.stop();
        }
        timeoutExecutor.shutdownNow();
        if (eventResponseSenderExecutor != null) {
            eventResponseSenderExecutor.shutdownNow();
        }
    }

    @Data
    @AllArgsConstructor
    static final class PreviousSessionInfo {
        private final ClientType clientType;
    }
}
