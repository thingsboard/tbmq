/**
 * Copyright © 2016-2022 The Thingsboard Authors
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

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thingsboard.mqtt.broker.actors.client.messages.ClientCallback;
import org.thingsboard.mqtt.broker.actors.client.messages.ConnectionRequestInfo;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.ConnectionInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.util.CallbackUtil;
import org.thingsboard.mqtt.broker.constant.BrokerConstants;
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
            log.debug("[{}][{}] Connection request timed out.", clientId, requestInfo.getRequestId());
            return;
        }

        log.trace("[{}] Processing connection request, sessionId - {}", clientId, sessionInfo.getSessionId());

        ClientSession currentClientSession = getClientSession(clientId);
        UUID currentClientSessionId = getCurrentClientSessionId(currentClientSession);

        if (sessionInfo.getSessionId().equals(currentClientSessionId)) {
            log.warn("[{}][{}] Got CONNECT request from already present session.", clientId, currentClientSessionId);
            sendEventResponse(clientId, requestInfo, false, false);
            return;
        }

        if (currentClientSession != null && currentClientSession.isConnected()) {
            String currentSessionServiceId = currentClientSession.getSessionInfo().getServiceId();
            var isNewSessionPersistent = sessionInfo.isPersistent();
            log.trace("[{}] Requesting disconnect of the client session, serviceId - {}, sessionId - {}.",
                    clientId, currentSessionServiceId, currentClientSessionId);
            disconnectCurrentSession(currentSessionServiceId, clientId, currentClientSessionId, isNewSessionPersistent);
            finishDisconnect(currentClientSession);
        }

        PreviousSessionInfo previousSessionInfo = getPreviousSessionInfo(currentClientSession);
        updateClientSession(sessionInfo, requestInfo, previousSessionInfo);
    }

    private void disconnectCurrentSession(String serviceId, String clientId, UUID sessionId, boolean isNewSessionPersistent) {
        disconnectClientCommandService.disconnectSession(serviceId, clientId, sessionId, isNewSessionPersistent);
    }

    private UUID getCurrentClientSessionId(ClientSession currentlyConnectedSession) {
        return currentlyConnectedSession != null ? getSessionIdFromClientSession(currentlyConnectedSession) : null;
    }

    private PreviousSessionInfo getPreviousSessionInfo(ClientSession currentlyConnectedSession) {
        return currentlyConnectedSession != null ? createPreviousSessionInfo(currentlyConnectedSession) : null;
    }

    private PreviousSessionInfo createPreviousSessionInfo(ClientSession currentlyConnectedSession) {
        return new PreviousSessionInfo(
                currentlyConnectedSession.getSessionInfo().isPersistent(),
                currentlyConnectedSession.getSessionInfo().getClientInfo().getType());
    }

    @Override
    public void processSessionDisconnected(String clientId, UUID sessionId) {
        ClientSession clientSession = getClientSession(clientId);
        if (clientSession == null) {
            log.debug("[{}][{}] Cannot find client session.", clientId, sessionId);
        } else {
            UUID currentSessionId = getSessionIdFromClientSession(clientSession);
            if (!sessionId.equals(currentSessionId)) {
                log.debug("[{}] Got disconnected event from the session with different sessionId. Currently connected sessionId - {}, " +
                        "received sessionId - {}.", clientId, currentSessionId, sessionId);
            } else if (!clientSession.isConnected()) {
                log.debug("[{}] Client session is already disconnected.", clientId);
            } else {
                finishDisconnect(clientSession);
            }
        }
    }

    // TODO: think in general about what data can get stuck in the DB forever
    @Override
    public void processClearSession(String clientId, UUID sessionId) {
        if (StringUtils.isEmpty(clientId) || sessionId == null) {
            throw new RuntimeException("Trying to clear session for empty clientId or sessionId");
        }
        try {
            ClientSession clientSession = getClientSession(clientId);
            Set<TopicSubscription> clientSubscriptions = getClientSubscriptions(clientId);
            if (clientSession == null) {
                log.warn("[{}] Trying to clear non-existent session, subscriptions - {}", clientId, clientSubscriptions);
                log.debug("[{}] Clearing client subscriptions.", clientId);
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
                    log.debug("[{}][{}] Clearing client session.", clientId, currentSessionId);
                    clearClientSession(clientId, "[{}] Cleared client session", "[{}] Failed to clear client session. Exception - {}, reason - {}");
                    clearClientSubscriptions(clientId);
                    clearPersistedMessages(clientSession.getSessionInfo().getClientInfo());
                }
            }
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to clear session. Exception - {}, reason - {}.", clientId, sessionId, e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private void clearPersistedMessages(ClientInfo clientSession) {
        msgPersistenceManager.clearPersistedMessages(clientSession);
    }

    private Set<TopicSubscription> getClientSubscriptions(String clientId) {
        return clientSubscriptionService.getClientSubscriptions(clientId);
    }

    private ClientSession getClientSession(String clientId) {
        return clientSessionService.getClientSession(clientId);
    }

    private UUID getSessionIdFromClientSession(ClientSession clientSession) {
        return clientSession.getSessionInfo().getSessionId();
    }

    private void clearClientSubscriptions(String clientId) {
        clearClientSubscriptions(clientId,
                "[{}] Cleared client subscriptions",
                "[{}] Failed to clear client subscriptions. Exception - {}, reason - {}");
    }

    private void clearClientSubscriptions(String clientId, String traceLogStr, String errorLogStr) {
        clientSubscriptionService.clearSubscriptionsAndPersist(clientId, createCallback(
                () -> log.trace(traceLogStr, clientId),
                t -> log.warn(errorLogStr, clientId, t.getClass().getSimpleName(), t.getMessage())));
    }

    private void clearClientSession(String clientId, String traceLogStr, String errorLogStr) {
        clientSessionService.clearClientSession(clientId, createCallback(
                () -> log.trace(traceLogStr, clientId),
                t -> log.warn(errorLogStr, clientId, t.getClass().getSimpleName(), t.getMessage())));
    }

    @Override
    public void processRemoveApplicationTopicRequest(String clientId, ClientCallback callback) {
        if (StringUtils.isEmpty(clientId)) {
            callback.onFailure(new RuntimeException("Trying to remove APPLICATION topic for empty clientId"));
            return;
        }
        ClientSession clientSession = getClientSession(clientId);
        ClientType currentType = clientSession.getSessionInfo().getClientInfo().getType();
        if (currentType == ClientType.APPLICATION) {
            log.debug("[{}] Current type of the client is APPLICATION", clientId);
            callback.onSuccess();
            return;
        }

        applicationTopicService.deleteTopic(clientId, CallbackUtil.createCallback(callback::onSuccess, callback::onFailure));
    }

    private void finishDisconnect(ClientSession clientSession) {
        SessionInfo sessionInfo = clientSession.getSessionInfo();
        String clientId = sessionInfo.getClientInfo().getClientId();
        log.trace("[{}] Finishing client session disconnection.", clientId);
        if (sessionInfo.isPersistent()) {
            ClientSession disconnectedClientSession = markSessionDisconnected(clientSession);
            saveClientSession(clientId, disconnectedClientSession);
        } else {
            clearClientSession(clientId,
                    "[{}] Finished disconnect by clearing client session",
                    "[{}] Failed to finish disconnect and clear client session. Exception - {}, reason - {}");
            clearClientSubscriptions(clientId,
                    "[{}] Finished disconnect by clearing client subscriptions",
                    "[{}] Failed to finish disconnect and clear client subscriptions. Exception - {}, reason - {}");
        }
    }

    private void saveClientSession(String clientId, ClientSession disconnectedClientSession) {
        clientSessionService.saveClientSession(clientId, disconnectedClientSession, createCallback(
                () -> log.trace("[{}] Finished disconnect by saving client session", clientId),
                t -> log.warn("[{}] Failed to finish disconnect and save client session. Exception - {}, reason - {}",
                        clientId, t.getClass().getSimpleName(), t.getMessage())));
    }

    private ClientSession markSessionDisconnected(ClientSession clientSession) {
        ConnectionInfo connectionInfo = clientSession.getSessionInfo().getConnectionInfo().toBuilder()
                .disconnectedAt(System.currentTimeMillis())
                .build();
        SessionInfo sessionInfo = clientSession.getSessionInfo().toBuilder()
                .connectionInfo(connectionInfo)
                .build();
        return clientSession.toBuilder()
                .connected(false)
                .sessionInfo(sessionInfo)
                .build();
    }

    private boolean isApplicationRemoved(PreviousSessionInfo previousSessionInfo, ClientInfo clientInfo) {
        return previousSessionInfo != null && previousSessionInfo.getClientType() == ClientType.APPLICATION
                && clientInfo.getType() == ClientType.DEVICE;
    }

    void updateClientSession(SessionInfo sessionInfo, ConnectionRequestInfo connectionRequestInfo, PreviousSessionInfo previousSessionInfo) {
        ClientInfo clientInfo = sessionInfo.getClientInfo();
        boolean sessionPresent = previousSessionInfo != null;
        log.trace("[{}] Updating client session.", clientInfo.getClientId());

        AtomicBoolean wasErrorProcessed = new AtomicBoolean(false);
        AtomicInteger finishedOperations = new AtomicInteger(0);

        boolean needClearPresentSession = sessionPresent && !sessionInfo.isPersistent();
        if (needClearPresentSession) {
            log.trace("[{}][{}] Clearing prev persisted session.", clientInfo.getType(), clientInfo.getClientId());
            clearSubscriptions(connectionRequestInfo, clientInfo, wasErrorProcessed, finishedOperations);
            clearPersistedMessages(clientInfo);
        }

        boolean applicationRemoved = isApplicationRemoved(previousSessionInfo, clientInfo);
        if (applicationRemoved) {
            clearPersistedMessages(clientInfo);
            applicationRemovedEventService.sendApplicationRemovedEvent(clientInfo.getClientId());
        }

        ClientSession clientSession = prepareClientSession(sessionInfo);
        clientSessionService.saveClientSession(clientInfo.getClientId(), clientSession, createCallback(
                () -> eventResponseSenderExecutor.execute(() -> {
                    if (!needClearPresentSession || finishedOperations.incrementAndGet() >= 2) {
                        sendEventResponse(clientInfo.getClientId(), connectionRequestInfo, true, sessionPresent);
                    }
                }),
                t -> eventResponseSenderExecutor.execute(() -> {
                    if (!needClearPresentSession || !wasErrorProcessed.getAndSet(true)) {
                        sendEventResponse(clientInfo.getClientId(), connectionRequestInfo, false, sessionPresent);
                    }
                })));
    }

    private ClientSession prepareClientSession(SessionInfo sessionInfo) {
        return ClientSession.builder()
                .connected(true)
                .sessionInfo(sessionInfo)
                .build();
    }

    private void clearSubscriptions(ConnectionRequestInfo connectionRequestInfo, ClientInfo clientInfo,
                                    AtomicBoolean wasErrorProcessed, AtomicInteger finishedOperations) {
        clientSubscriptionService.clearSubscriptionsAndPersist(clientInfo.getClientId(), createCallback(
                () -> {
                    if (finishedOperations.incrementAndGet() >= 2) {
                        sendEventResponse(clientInfo.getClientId(), connectionRequestInfo, true, true);
                    }
                },
                t -> {
                    if (!wasErrorProcessed.getAndSet(true)) {
                        sendEventResponse(clientInfo.getClientId(), connectionRequestInfo, false, true);
                    }
                }));
    }

    private void sendEventResponse(String clientId, ConnectionRequestInfo connectionRequestInfo, boolean success, boolean sessionPresent) {
        QueueProtos.ClientSessionEventResponseProto response = QueueProtos.ClientSessionEventResponseProto.newBuilder()
                .setSuccess(success)
                .setSessionPresent(sessionPresent)
                .build();
        TbQueueMsgHeaders headers = createResponseHeaders(connectionRequestInfo.getRequestId());
        eventResponseProducer.send(connectionRequestInfo.getResponseTopic(), new TbProtoQueueMsg<>(clientId, response, headers), new TbQueueCallback() {
            @Override
            public void onSuccess(TbQueueMsgMetadata metadata) {
                log.trace("[{}][{}] Successfully sent response.", clientId, connectionRequestInfo.getRequestId());
            }

            @Override
            public void onFailure(Throwable t) {
                log.warn("[{}][{}] Failed to send response.", clientId, connectionRequestInfo.getRequestId(), t);
            }
        });
    }

    private DefaultTbQueueMsgHeaders createResponseHeaders(UUID requestId) {
        DefaultTbQueueMsgHeaders headers = new DefaultTbQueueMsgHeaders();
        headers.put(BrokerConstants.REQUEST_ID_HEADER, BytesUtil.uuidToBytes(requestId));
        return headers;
    }

    boolean isRequestTimedOut(long requestTime) {
        return requestTime + requestTimeout < System.currentTimeMillis();
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
    static final class PreviousSessionInfo {
        private final boolean persistent;
        private final ClientType clientType;
    }
}