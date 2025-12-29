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
package org.thingsboard.mqtt.broker.actors.client.service.session;

import com.google.common.util.concurrent.ListenableFuture;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.actors.client.messages.ClientCallback;
import org.thingsboard.mqtt.broker.actors.client.messages.ConnectionRequestInfo;
import org.thingsboard.mqtt.broker.actors.client.messages.cluster.SessionDisconnectedMsg;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.cache.TbCacheOps;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.common.data.util.BytesUtil;
import org.thingsboard.mqtt.broker.common.data.util.CallbackUtil;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.common.util.DonAsynchron;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesService;
import org.thingsboard.mqtt.broker.gen.queue.ClientSessionEventResponseProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgHeaders;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.DefaultTbQueueMsgHeaders;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ClientSessionEventQueueFactory;
import org.thingsboard.mqtt.broker.service.limits.RateLimitCacheService;
import org.thingsboard.mqtt.broker.service.mqtt.client.disconnect.DisconnectClientCommandService;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventType;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.topic.ApplicationRemovedEventService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.topic.ApplicationTopicService;
import org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.thingsboard.mqtt.broker.cache.CacheConstants.CLIENT_MQTT_VERSION_CACHE;
import static org.thingsboard.mqtt.broker.cache.CacheConstants.CLIENT_SESSION_CREDENTIALS_CACHE;
import static org.thingsboard.mqtt.broker.common.data.util.CallbackUtil.createCallback;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionClusterManagerImpl implements SessionClusterManager {

    private final ClientSessionService clientSessionService;
    private final ClientSubscriptionService clientSubscriptionService;
    private final DisconnectClientCommandService disconnectClientCommandService;
    private final ClientSessionEventQueueFactory clientSessionEventQueueFactory;
    private final ServiceInfoProvider serviceInfoProvider;
    private final MsgPersistenceManager msgPersistenceManager;
    private final ApplicationRemovedEventService applicationRemovedEventService;
    private final ApplicationTopicService applicationTopicService;
    private final RateLimitCacheService rateLimitCacheService;
    private final TbCacheOps cacheOps;
    private final TimeseriesService timeseriesService;

    private TbQueueProducer<TbProtoQueueMsg<ClientSessionEventResponseProto>> eventResponseProducer;
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

    @Override
    public void processConnectionRequest(SessionInfo connectingSessionInfo, ConnectionRequestInfo requestInfo) {
        // It is possible that for some time sessions can be connected with the same clientId to different Nodes
        String clientId = connectingSessionInfo.getClientId();
        UUID sessionId = connectingSessionInfo.getSessionId();
        log.trace("[{}] Processing connection request, sessionId - {}", clientId, sessionId);

        if (isRequestTimedOut(requestInfo.getRequestTime())) {
            log.warn("[{}][{}] Connection request timed out.", clientId, requestInfo.getRequestId());
            return;
        }

        ClientSessionInfo currentClientSession = getClientSessionInfo(clientId);
        UUID currentClientSessionId = getSessionId(currentClientSession);

        if (sessionId.equals(currentClientSessionId)) {
            log.warn("[{}][{}] Got CONNECT request from already present session.", clientId, currentClientSessionId);
            sendEventResponse(clientId, requestInfo, false, false);
            return;
        }

        if (currentClientSessionPresentAndConnected(currentClientSession)) {
            boolean sessionRemoved = disconnectCurrentSession(currentClientSession, connectingSessionInfo);
            if (sessionRemoved) {
                currentClientSession = null;
            }
        }

        updateClientSession(connectingSessionInfo, requestInfo, currentClientSession);
    }

    void updateClientSession(SessionInfo sessionInfo, ConnectionRequestInfo connectionRequestInfo,
                             ClientSessionInfo currentSessionInfo) {
        ClientInfo clientInfo = sessionInfo.getClientInfo();
        boolean sessionPresent = currentSessionInfo != null;
        log.trace("[{}] Updating client session.", clientInfo.getClientId());
        if (sessionPresent) {
            removeClientLatestTs(clientInfo.getClientId());
        }

        AtomicBoolean wasErrorProcessed = new AtomicBoolean(false);
        AtomicInteger finishedOperations = new AtomicInteger(0);

        boolean decrementedAppClientsCount = false;

        boolean needClearCurrentSession = sessionPresent && sessionInfo.isCleanStart();
        if (needClearCurrentSession) {
            log.trace("[{}][{}] Clearing current persisted session.", clientInfo.getType(), clientInfo.getClientId());
            if (currentSessionInfo.isPersistentAppClient()) {
                rateLimitCacheService.decrementApplicationClientsCount();
                decrementedAppClientsCount = true;
            }
            clearSubscriptionsAndSendResponseIfReady(clientInfo, connectionRequestInfo, finishedOperations, wasErrorProcessed);
            clearPersistedMessages(clientInfo.getClientId(), clientInfo.getType());
        }

        boolean applicationRemoved = isApplicationRemoved(currentSessionInfo, clientInfo);
        if (applicationRemoved) {
            if (!decrementedAppClientsCount && currentSessionInfo.isPersistentAppClient()) {
                rateLimitCacheService.decrementApplicationClientsCount();
            }
            clearPersistedMessages(clientInfo.getClientId(), clientInfo.getType());
            applicationRemovedEventService.sendApplicationRemovedEvent(clientInfo.getClientId());
        }

        ClientSessionInfo clientSessionInfo = toClientSessionInfo(sessionInfo);
        clientSessionService.saveClientSession(clientSessionInfo, createCallback(
                () -> {
                    if (!needClearCurrentSession || finishedOperations.incrementAndGet() >= 2) {
                        sendEventResponse(clientInfo.getClientId(), connectionRequestInfo, true, sessionPresent);
                    }
                },
                t -> {
                    if (!needClearCurrentSession || !wasErrorProcessed.getAndSet(true)) {
                        sendEventResponse(clientInfo.getClientId(), connectionRequestInfo, false, sessionPresent);
                    }
                }));
    }

    @Override
    public void processSessionDisconnected(String clientId, SessionDisconnectedMsg msg) {
        UUID sessionId = msg.getSessionId();
        ClientSessionInfo clientSessionInfo = getClientSessionInfo(clientId);
        if (clientSessionInfo == null) {
            log.debug("[{}][{}] Cannot find client session.", clientId, sessionId);
        } else {
            UUID currentSessionId = clientSessionInfo.getSessionId();
            if (!sessionId.equals(currentSessionId)) {
                log.debug("[{}] Got disconnected event from the session with different sessionId. Currently connected sessionId - {}, " +
                        "received sessionId - {}.", clientId, currentSessionId, sessionId);
            } else if (!clientSessionInfo.isConnected()) {
                log.debug("[{}] Client session is already disconnected.", clientId);
            } else {
                finishDisconnect(clientSessionInfo, msg.getSessionExpiryInterval());
            }
        }
    }

    @Override
    public void processClearSession(String clientId, UUID sessionId) {
        if (StringUtils.isEmpty(clientId) || sessionId == null) {
            throw new RuntimeException("Trying to clear session for empty clientId or sessionId");
        }
        try {
            ClientSessionInfo clientSessionInfo = getClientSessionInfo(clientId);
            if (clientSessionInfo == null) {
                Set<TopicSubscription> clientSubscriptions = clientSubscriptionService.getClientSubscriptions(clientId);
                log.debug("[{}] Trying to clear non-existent session, session subscriptions - {}", clientId, clientSubscriptions);
                if (!CollectionUtils.isEmpty(clientSubscriptions)) {
                    clearClientSubscriptions(clientId);
                }
            } else {
                UUID currentSessionId = clientSessionInfo.getSessionId();
                if (!sessionId.equals(currentSessionId)) {
                    log.info("[{}][{}] Ignoring {} for session - {}.",
                            clientId, currentSessionId, ClientSessionEventType.CLEAR_SESSION_REQUEST, sessionId);
                } else if (clientSessionInfo.isConnected()) {
                    log.info("[{}][{}] Session is connected now, ignoring {}.",
                            clientId, currentSessionId, ClientSessionEventType.CLEAR_SESSION_REQUEST);
                } else {
                    log.debug("[{}][{}] Clearing client session.", clientId, currentSessionId);
                    rateLimitCacheService.decrementSessionCount();
                    if (clientSessionInfo.isPersistentAppClient()) {
                        rateLimitCacheService.decrementApplicationClientsCount();
                    }
                    evictFromCache(clientId);
                    clearSessionAndSubscriptions(clientId);
                    clearPersistedMessages(clientId, clientSessionInfo.getType());
                    removeClientLatestTs(clientId);
                }
            }
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to clear session", clientId, sessionId, e);
        }
    }

    @Override
    public void processRemoveApplicationTopicRequest(String clientId, ClientCallback callback) {
        log.trace("[{}] Processing REMOVE_APPLICATION_TOPIC_REQUEST_MSG", clientId);
        if (StringUtils.isEmpty(clientId)) {
            callback.onFailure(new RuntimeException("Trying to remove APPLICATION topic for empty clientId"));
            return;
        }
        ClientSessionInfo clientSessionInfo = getClientSessionInfo(clientId);
        if (clientSessionInfo == null || clientSessionInfo.isAppClient()) {
            log.debug("[{}] Skipping application topic removal: {}", clientId, clientSessionInfo == null ? "no client session found" : "type is APPLICATION");
            callback.onSuccess();
            return;
        }

        applicationTopicService.deleteTopic(clientId, CallbackUtil.createCallback(callback::onSuccess, callback::onFailure));
    }

    private boolean finishDisconnect(ClientSessionInfo clientSessionInfo, int sessionExpiryInterval) {
        String clientId = clientSessionInfo.getClientId();
        log.trace("[{}] Finishing client session disconnection [{}].", clientId, clientSessionInfo);
        if (clientSessionInfo.isPersistent()) {
            saveClientSession(markSessionDisconnected(clientSessionInfo, sessionExpiryInterval));
            return false;
        } else {
//            if (disconnectReasonType.isNotConflictingSession()) {
//                rateLimitCacheService.decrementSessionCount();
//            }
        }
        evictFromCache(clientId);
        clearSessionAndSubscriptions(clientId);
        removeClientLatestTs(clientId);
        return true;
    }

    private void removeClientLatestTs(String clientId) {
        ListenableFuture<Collection<String>> future = timeseriesService.removeAllLatestForClient(clientId);
        DonAsynchron.withCallback(future,
                keys -> log.debug("[{}] Removed all latest keys for client {}", clientId, keys),
                throwable -> log.warn("[{}] Failed to removed all latest keys for client", clientId, throwable));
    }

    ClientSessionInfo markSessionDisconnected(ClientSessionInfo clientSessionInfo, int sessionExpiryInterval) {
        int currentSessionExpiryInterval = clientSessionInfo.getSessionExpiryInterval();
        return clientSessionInfo
                .toBuilder()
                .connected(false)
                .disconnectedAt(System.currentTimeMillis())
                .sessionExpiryInterval(sessionExpiryInterval == -1 ? currentSessionExpiryInterval : sessionExpiryInterval)
                .build();
    }

    private boolean isApplicationRemoved(ClientSessionInfo currentSessionInfo, ClientInfo clientInfo) {
        return currentSessionInfo != null && currentSessionInfo.getType() == ClientType.APPLICATION
                && clientInfo.getType() == ClientType.DEVICE;
    }

    private ClientSessionInfo toClientSessionInfo(SessionInfo sessionInfo) {
        return ClientSessionInfoFactory.sessionInfoToClientSessionInfo(sessionInfo);
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
        ClientSessionEventResponseProto response = getEventResponseProto(success, sessionPresent);
        TbQueueMsgHeaders headers = createResponseHeaders(connectionRequestInfo.getRequestId());
        eventResponseSenderExecutor.execute(
                () -> eventResponseProducer.send(
                        connectionRequestInfo.getResponseTopic(),
                        null,
                        new TbProtoQueueMsg<>(clientId, response, headers),
                        new TbQueueCallback() {
                            @Override
                            public void onSuccess(TbQueueMsgMetadata metadata) {
                                log.trace("[{}][{}] Successfully sent response.", clientId, connectionRequestInfo.getRequestId());
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                log.warn("[{}][{}] Failed to send response.", clientId, connectionRequestInfo.getRequestId(), t);
                            }
                        }));
    }

    private ClientSessionEventResponseProto getEventResponseProto(boolean success, boolean sessionPresent) {
        return ClientSessionEventResponseProto.newBuilder()
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

    private boolean currentClientSessionPresentAndConnected(ClientSessionInfo currentClientSession) {
        return currentClientSession != null && currentClientSession.isConnected();
    }

    private ClientSessionInfo getClientSessionInfo(String clientId) {
        return clientSessionService.getClientSessionInfo(clientId);
    }

    private void saveClientSession(ClientSessionInfo clientSessionInfo) {
        clientSessionService.saveClientSession(clientSessionInfo, null);
    }

    private void clearSessionAndSubscriptions(String clientId) {
        clearClientSession(clientId);
        clearClientSubscriptions(clientId);
    }

    private void clearClientSession(String clientId) {
        clientSessionService.clearClientSession(clientId, null);
    }

    private void clearClientSubscriptions(String clientId) {
        clientSubscriptionService.clearSubscriptionsAndPersist(clientId, null);
    }

    private void clearPersistedMessages(String clientId, ClientType type) {
        msgPersistenceManager.clearPersistedMessages(clientId, type);
    }

    private boolean disconnectCurrentSession(ClientSessionInfo clientSessionInfo, SessionInfo connectingSessionInfo) {
        String clientId = connectingSessionInfo.getClientId();
        String serviceId = clientSessionInfo.getServiceId();
        UUID sessionId = clientSessionInfo.getSessionId();
        log.trace("[{}] Requesting session conflict disconnect, serviceId - {}, sessionId - {}.",
                clientId, serviceId, sessionId);
        disconnectClientCommandService.disconnectOnSessionConflict(serviceId, clientId, sessionId, connectingSessionInfo.isCleanStart());
        return finishDisconnect(clientSessionInfo, -1);
    }

    private UUID getSessionId(ClientSessionInfo clientSessionInfo) {
        return clientSessionInfo == null ? null : clientSessionInfo.getSessionId();
    }

    private void evictFromCache(String clientId) {
        cacheOps.evictIfPresent(CLIENT_SESSION_CREDENTIALS_CACHE, clientId);
        cacheOps.evictIfPresent(CLIENT_MQTT_VERSION_CACHE, clientId);
    }

    @PreDestroy
    public void destroy() {
        if (eventResponseProducer != null) {
            eventResponseProducer.stop();
        }
        if (eventResponseSenderExecutor != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(eventResponseSenderExecutor, "Session cluster manager");
        }
    }
}
