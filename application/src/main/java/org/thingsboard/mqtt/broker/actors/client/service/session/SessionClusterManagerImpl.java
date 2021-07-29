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
package org.thingsboard.mqtt.broker.actors.client.service.session;

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
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
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
import static org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventConst.REQUEST_ID_HEADER;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionClusterManagerImpl implements SessionClusterManager {
    private final ScheduledExecutorService timeoutExecutor = Executors.newSingleThreadScheduledExecutor();
    private ExecutorService eventResponseSenderExecutor;

    @Value("${queue.client-session-event-response.max-request-timeout}")
    private long requestTimeout;
    @Value("${queue.client-session-event-response.response-sender-threads}")
    private int eventResponseSenderThreads;

    private final ClientSessionService clientSessionService;
    private final ClientSubscriptionService clientSubscriptionService;
    private final DisconnectClientCommandService disconnectClientCommandService;
    private final ClientSessionEventQueueFactory clientSessionEventQueueFactory;
    private final ServiceInfoProvider serviceInfoProvider;
    private final MsgPersistenceManager msgPersistenceManager;

    private TbQueueProducer<TbProtoQueueMsg<QueueProtos.ClientSessionEventResponseProto>> eventResponseProducer;

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
    public void processConnectionRequest(SessionInfo sessionInfo, ConnectionRequestInfo requestInfo, ClientCallback callback) {
        // It is possible that for some time sessions can be connected with the same clientId to different Nodes
        String clientId = sessionInfo.getClientInfo().getClientId();
        UUID sessionId = sessionInfo.getSessionId();

        log.trace("[{}] Processing connection request, sessionId - {}", clientId, sessionId);

        ClientSession currentlyConnectedSession = clientSessionService.getClientSession(clientId);
        UUID currentlyConnectedSessionId = currentlyConnectedSession != null ?
                currentlyConnectedSession.getSessionInfo().getSessionId() : null;

        if (sessionId.equals(currentlyConnectedSessionId)) {
            log.warn("[{}][{}] Got CONNECT request from already connected session.", clientId, currentlyConnectedSessionId);
            sendEventResponse(clientId, requestInfo, false, false);
            return;
        }

        if (currentlyConnectedSession != null && currentlyConnectedSession.isConnected()) {
            String currentSessionServiceId = currentlyConnectedSession.getSessionInfo().getServiceId();
            log.trace("[{}] Requesting disconnect of the client session, serviceId - {}, sessionId - {}.", clientId, currentSessionServiceId, currentlyConnectedSessionId);
            disconnectClientCommandService.disconnectSession(currentSessionServiceId, clientId, currentlyConnectedSessionId);
            finishDisconnect(currentlyConnectedSession);
        }

        boolean wasPrevSessionPersistent = currentlyConnectedSession != null && currentlyConnectedSession.getSessionInfo().isPersistent();
        updateClientSession(sessionInfo, requestInfo, wasPrevSessionPersistent, callback);
    }

    @Override
    public void processSessionDisconnected(String clientId, UUID sessionId) {
        ClientSession clientSession = clientSessionService.getClientSession(clientId);
        if (clientSession == null) {
            log.debug("[{}][{}] Cannot find client session.", clientId, sessionId);
        } else if (!sessionId.equals(clientSession.getSessionInfo().getSessionId())) {
            log.debug("[{}] Got disconnected event from the session with different sessionId. Currently connected sessionId - {}, " +
                    "received sessionId - {}.", clientId, clientSession.getSessionInfo().getSessionId(), sessionId);
        } else if (!clientSession.isConnected()) {
            log.debug("[{}] Client session is already disconnected.", clientId);
        } else {
            finishDisconnect(clientSession);
        }
    }

    // TODO: think in general about what data can get stuck in the DB forever
    @Override
    public void processClearSession(String clientId, UUID sessionId) {
        if (StringUtils.isEmpty(clientId) || sessionId == null) {
            throw new RuntimeException("Trying to clear session for empty clientId or sessionId");
        }
        try {
            ClientSession clientSession = clientSessionService.getClientSession(clientId);
            Set<TopicSubscription> clientSubscriptions = clientSubscriptionService.getClientSubscriptions(clientId);
            if (clientSession == null) {
                log.warn("[{}] Trying to clear non-existent session, subscriptions - {}", clientId, clientSubscriptions);
                log.debug("[{}] Clearing client subscriptions.", clientId);
                clientSubscriptionService.clearSubscriptionsAndPersist(clientId, createCallback(() -> log.trace("[{}] Cleared client subscriptions", clientId),
                        t -> log.warn("[{}] Failed to clear client subscriptions. Exception - {}, reason - {}", clientId, t.getClass().getSimpleName(), t.getMessage()))
                );
            } else if (sessionId.equals(clientSession.getSessionInfo().getSessionId())) {
                log.info("[{}][{}] Ignoring {} for session - {}.", clientId, clientSession.getSessionInfo().getSessionId(), ClientSessionEventType.TRY_CLEAR_SESSION_REQUEST, sessionId);
            } else if (clientSession.isConnected()) {
                log.info("[{}][{}] Session is connected now, ignoring {}.", clientId, clientSession.getSessionInfo().getSessionId(), ClientSessionEventType.TRY_CLEAR_SESSION_REQUEST);
            } else {
                log.debug("[{}][{}] Clearing client session.", clientId, clientSession.getSessionInfo().getSessionId());
                clientSessionService.clearClientSession(clientId,
                        createCallback(() -> log.trace("[{}] Cleared client session", clientId),
                                t -> log.warn("[{}] Failed to clear client session. Exception - {}, reason - {}", clientId, t.getClass().getSimpleName(), t.getMessage()))
                );
                clientSubscriptionService.clearSubscriptionsAndPersist(clientId, createCallback(() -> log.trace("[{}] Cleared client subscriptions", clientId),
                        t -> log.warn("[{}] Failed to clear client subscriptions. Exception - {}, reason - {}", clientId, t.getClass().getSimpleName(), t.getMessage())));
                msgPersistenceManager.clearPersistedMessages(clientSession.getSessionInfo().getClientInfo());
            }
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to clear session. Exception - {}, reason - {}.", clientId, sessionId, e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private void finishDisconnect(ClientSession clientSession) {
        String clientId = clientSession.getSessionInfo().getClientInfo().getClientId();
        log.trace("[{}] Finishing client session disconnection.", clientId);
        if (clientSession.getSessionInfo().isPersistent()) {
            ClientSession disconnectedClientSession = clientSession.toBuilder().connected(false).build();
            clientSessionService.saveClientSession(clientId, disconnectedClientSession, createCallback(() -> log.trace("[{}] Finished disconnect by saving client session", clientId),
                    t -> log.warn("[{}] Failed to finish disconnect and save client session. Exception - {}, reason - {}", clientId, t.getClass().getSimpleName(), t.getMessage())));
        } else {
            clientSessionService.clearClientSession(clientId, createCallback(() -> log.trace("[{}] Finished disconnect by clearing client session", clientId),
                    t -> log.warn("[{}] Failed to finish disconnect and clear client session. Exception - {}, reason - {}", clientId, t.getClass().getSimpleName(), t.getMessage())));
            clientSubscriptionService.clearSubscriptionsAndPersist(clientId, createCallback(() -> log.trace("[{}] Finished disconnect by clearing client subscriptions", clientId),
                    t -> log.warn("[{}] Failed to finish disconnect and clear client subscriptions. Exception - {}, reason - {}", clientId, t.getClass().getSimpleName(), t.getMessage())));
        }
    }

    private void updateClientSession(SessionInfo sessionInfo, ConnectionRequestInfo connectionRequestInfo, boolean wasPrevSessionPersistent, ClientCallback callback) {
        ClientInfo clientInfo = sessionInfo.getClientInfo();
        log.trace("[{}] Updating client session.", clientInfo.getClientId());

        if (isRequestTimedOut(connectionRequestInfo.getRequestTime())) {
            log.debug("[{}][{}] Connection request timed out.", clientInfo.getClientId(), connectionRequestInfo.getRequestId());
            return;
        }

        boolean needPrevSessionClear = wasPrevSessionPersistent && !sessionInfo.isPersistent();
        AtomicBoolean wasErrorProcessed = new AtomicBoolean(false);
        AtomicInteger finishedOperations = new AtomicInteger(0);
        if (needPrevSessionClear) {
            log.trace("[{}][{}] Clearing prev persisted session.", clientInfo.getType(), clientInfo.getClientId());
            clientSubscriptionService.clearSubscriptionsAndPersist(clientInfo.getClientId(), createCallback(() -> {
                if (finishedOperations.incrementAndGet() >= 2) {
                    callback.onSuccess();
                    sendEventResponse(clientInfo.getClientId(), connectionRequestInfo, true, wasPrevSessionPersistent);
                }
            }, t -> {
                if (!wasErrorProcessed.getAndSet(true)) {
                    callback.onFailure(t);
                    sendEventResponse(clientInfo.getClientId(), connectionRequestInfo, false, wasPrevSessionPersistent);
                }
            }));
            msgPersistenceManager.clearPersistedMessages(clientInfo);
        }

        ClientSession clientSession = ClientSession.builder()
                .connected(true)
                .sessionInfo(sessionInfo)
                .build();
        clientSessionService.saveClientSession(clientInfo.getClientId(), clientSession, createCallback(() -> eventResponseSenderExecutor.execute(() -> {
            if (!needPrevSessionClear || finishedOperations.incrementAndGet() >= 2) {
                callback.onSuccess();
                sendEventResponse(clientInfo.getClientId(), connectionRequestInfo, true, wasPrevSessionPersistent);
            }
        }), t -> eventResponseSenderExecutor.execute(() -> {
            if (!needPrevSessionClear || !wasErrorProcessed.getAndSet(true)) {
                callback.onFailure(t);
                sendEventResponse(clientInfo.getClientId(), connectionRequestInfo, false, wasPrevSessionPersistent);
            }
        })));
    }

    private void sendEventResponse(String clientId, ConnectionRequestInfo connectionRequestInfo, boolean successfulConnect, boolean wasPrevSessionPersistent) {
        QueueProtos.ClientSessionEventResponseProto response = QueueProtos.ClientSessionEventResponseProto.newBuilder()
                .setSuccess(successfulConnect)
                .setWasPrevSessionPersistent(wasPrevSessionPersistent)
                .build();
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
