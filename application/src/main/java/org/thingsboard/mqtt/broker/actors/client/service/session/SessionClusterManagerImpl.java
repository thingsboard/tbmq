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
import org.thingsboard.mqtt.broker.actors.client.messages.cluster.ConnectionRequestMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.cluster.SessionDisconnectedMsg;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.cache.TbCacheOps;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
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
import org.thingsboard.mqtt.broker.service.mqtt.client.event.data.ClientConnectInfo;
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
        eventResponseProducer = clientSessionEventQueueFactory.createEventResponseProducer(serviceInfoProvider.getServiceId());
        eventResponseSenderExecutor = Executors.newFixedThreadPool(eventResponseSenderThreads);
    }

    @Override
    public void processConnectionRequest(ConnectionRequestMsg msg) {
        SessionInfo connectingSessionInfo = msg.getSessionInfo();
        ConnectionRequestInfo requestInfo = msg.getRequestInfo();
        ClientConnectInfo connectInfo = msg.getConnectInfo();

        String clientId = connectingSessionInfo.getClientId();
        UUID sessionId = connectingSessionInfo.getSessionId();
        log.trace("[{}] Processing connection request, sessionId - {}", clientId, sessionId);

        if (isRequestTimedOut(requestInfo.getRequestTime())) {
            log.warn("[{}][{}] Connection request timed out.", clientId, requestInfo.getRequestId());
            return;
        }

        ClientSessionInfo currentSession = getClientSessionInfo(clientId);

        if (isSameSession(currentSession, sessionId)) {
            log.warn("[{}][{}] Got CONNECT request from already present session.", clientId, sessionId);
            sendConnectResponse(clientId, requestInfo, false, false);
            return;
        }

        currentSession = disconnectIfConflicting(currentSession, connectingSessionInfo);
        persistClientInfoInCache(clientId, connectInfo);
        updateClientSessionOnConnect(connectingSessionInfo, requestInfo, currentSession);
    }

    /**
     * Save (new) session and optionally clear old data if Clean Start or type transition APP->DEVICE happened.
     */
    void updateClientSessionOnConnect(SessionInfo connectingSessionInfo,
                                      ConnectionRequestInfo requestInfo,
                                      ClientSessionInfo currentSession) {
        String clientId = connectingSessionInfo.getClientId();
        ClientType clientType = connectingSessionInfo.getClientType();

        log.trace("[{}] Updating client session.", clientId);

        if (currentSession == null) {
            ClientSessionInfo newSessionInfo = toClientSessionInfo(connectingSessionInfo);
            clientSessionService.saveClientSession(newSessionInfo, createCallback(
                    () -> sendConnectResponse(clientId, requestInfo, true, false),
                    t -> sendConnectResponse(clientId, requestInfo, false, false)
            ));
            return;
        }

        removeClientLatestTs(clientId);

        boolean cleanStart = connectingSessionInfo.isCleanStart();
        processRemoveApplication(connectingSessionInfo, currentSession);

        TwoPhaseCompletion completion = cleanStart ?
                TwoPhaseCompletion.forTwoOperations(
                        () -> sendConnectResponse(clientId, requestInfo, true, false),
                        () -> sendConnectResponse(clientId, requestInfo, false, false))
                :
                TwoPhaseCompletion.forSingleOperation(
                        () -> sendConnectResponse(clientId, requestInfo, true, true),
                        () -> sendConnectResponse(clientId, requestInfo, false, true));

        if (cleanStart) {
            clearSubscriptionsOnCleanStart(clientId, completion);
            clearPersistedMessages(clientId, clientType);
        }

        ClientSessionInfo newSessionInfo = toClientSessionInfo(connectingSessionInfo);
        clientSessionService.saveClientSession(newSessionInfo, createCallback(
                completion::successStep,
                completion::failStep
        ));
    }

    private void processRemoveApplication(SessionInfo connectingSession, ClientSessionInfo currentSession) {
        String clientId = connectingSession.getClientId();
        ClientType clientType = connectingSession.getClientType();

        boolean appClientCountDecremented = false;

        if (connectingSession.isCleanStart()) {
            appClientCountDecremented = decrementAppClientsIfNeeded(currentSession);
        }

        boolean removeApplication = isApplicationRemoved(currentSession, clientType);
        if (removeApplication) {
            if (!appClientCountDecremented) {
                decrementAppClientsIfNeeded(currentSession);
            }
            clearPersistedMessages(clientId, clientType);
            applicationRemovedEventService.sendApplicationRemovedEvent(clientId);
        }
    }

    private boolean decrementAppClientsIfNeeded(ClientSessionInfo previousSession) {
        if (previousSession.isPersistentAppClient()) {
            rateLimitCacheService.decrementApplicationClientsCount();
            return true;
        }
        return false;
    }

    private void clearSubscriptionsOnCleanStart(String clientId, TwoPhaseCompletion completion) {
        log.trace("[{}] Clearing current persisted session subscriptions.", clientId);
        clientSubscriptionService.clearSubscriptionsAndPersist(clientId, createCallback(
                completion::successStep,
                completion::failStep
        ));
    }

    /**
     * If there is a connected session for the same clientId, disconnect it and locally finish cleanup.
     * Returns null if the session was fully removed.
     */
    private ClientSessionInfo disconnectIfConflicting(ClientSessionInfo currentSession, SessionInfo connectingSessionInfo) {
        if (currentSession == null || !currentSession.isConnected()) {
            return currentSession;
        }
        return disconnectCurrentSession(currentSession, connectingSessionInfo);
    }

    @Override
    public void processSessionDisconnected(String clientId, SessionDisconnectedMsg msg) {
        UUID eventSessionId = msg.getSessionId();
        ClientSessionInfo session = getClientSessionInfo(clientId);

        if (session == null) {
            log.debug("[{}][{}] Cannot find client session.", clientId, eventSessionId);
            return;
        }

        if (!eventSessionId.equals(session.getSessionId())) {
            log.debug("[{}] Got disconnected event from the session with different sessionId. Currently connected sessionId - {}, received sessionId - {}.",
                    clientId, session.getSessionId(), eventSessionId);
            return;
        }

        if (!session.isConnected()) {
            log.debug("[{}] Client session is already disconnected.", clientId);
            return;
        }

        log.trace("[{}] Finishing client session disconnection [{}].", clientId, msg);
        finishDisconnect(session, msg);
    }

    @Override
    public void processClearSession(String clientId, UUID sessionId) {
        try {
            ClientSessionInfo session = getClientSessionInfo(clientId);

            if (session == null) {
                clearSubscriptionsIfLeftoversExist(clientId);
                return;
            }

            if (!sessionId.equals(session.getSessionId())) {
                log.info("[{}][{}] Ignoring {} for session - {}.",
                        clientId, session.getSessionId(), ClientSessionEventType.CLEAR_SESSION_REQUEST, sessionId);
                return;
            }

            if (session.isConnected()) {
                log.info("[{}][{}] Session is connected now, ignoring {}.",
                        clientId, session.getSessionId(), ClientSessionEventType.CLEAR_SESSION_REQUEST);
                return;
            }

            clearDisconnectedSessionData(session);

        } catch (Exception e) {
            log.warn("[{}][{}] Failed to clear session", clientId, sessionId, e);
        }
    }

    private void clearSubscriptionsIfLeftoversExist(String clientId) {
        Set<TopicSubscription> subs = clientSubscriptionService.getClientSubscriptions(clientId);
        log.debug("[{}] Trying to clear non-existent session, session subscriptions - {}", clientId, subs);
        if (!CollectionUtils.isEmpty(subs)) {
            clearClientSubscriptions(clientId);
        }
    }

    private void clearDisconnectedSessionData(ClientSessionInfo session) {
        String clientId = session.getClientId();
        UUID currentSessionId = session.getSessionId();

        log.debug("[{}][{}] Clearing client session.", clientId, currentSessionId);

        rateLimitCacheService.decrementSessionCount();
        if (session.isPersistentAppClient()) {
            rateLimitCacheService.decrementApplicationClientsCount();
        }

        fullSessionRemove(session, true);
    }

    @Override
    public void processRemoveApplicationTopicRequest(String clientId, ClientCallback callback) {
        log.trace("[{}] Processing REMOVE_APPLICATION_TOPIC_REQUEST_MSG", clientId);
        if (StringUtils.isEmpty(clientId)) {
            callback.onFailure(new RuntimeException("Trying to remove APPLICATION topic for empty clientId"));
            return;
        }
        ClientSessionInfo session = getClientSessionInfo(clientId);
        if (session == null || session.isAppClient()) {
            log.debug("[{}] Skipping application topic removal: {}", clientId,
                    session == null ? "no client session found" : "type is APPLICATION");
            callback.onSuccess();
            return;
        }

        applicationTopicService.deleteTopic(clientId, CallbackUtil.createCallback(callback::onSuccess, callback::onFailure));
    }

    void finishDisconnect(ClientSessionInfo session, SessionDisconnectedMsg msg) {
        if (session.isPersistent()) {
            saveClientSession(markSessionDisconnected(session, msg.getSessionExpiryInterval()));
            return;
        } else {
            if (msg.getReasonType().isNotConflictingSession()) {
                rateLimitCacheService.decrementSessionCount();
            }
        }

        fullSessionRemove(session, false);
    }

    ClientSessionInfo finishOnConflictDisconnect(ClientSessionInfo session) {
        if (session.isPersistent()) {
            ClientSessionInfo disconnected = markSessionDisconnected(session, -1);
            saveClientSession(disconnected);
            return disconnected;
        }

        fullSessionRemove(session, false);
        return null;
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

    private boolean isApplicationRemoved(ClientSessionInfo currentSession, ClientType clientType) {
        return currentSession.isAppClient() && clientType == ClientType.DEVICE;
    }

    private ClientSessionInfo toClientSessionInfo(SessionInfo sessionInfo) {
        return ClientSessionInfoFactory.sessionInfoToClientSessionInfo(sessionInfo);
    }

    private void sendConnectResponse(String clientId, ConnectionRequestInfo requestInfo, boolean success, boolean sessionPresent) {
        ClientSessionEventResponseProto response = ProtoConverter.toConnectionResponseProto(success, sessionPresent);
        TbQueueMsgHeaders headers = createResponseHeaders(requestInfo.getRequestId());

        eventResponseSenderExecutor.execute(() ->
                eventResponseProducer.send(
                        requestInfo.getResponseTopic(),
                        null,
                        new TbProtoQueueMsg<>(clientId, response, headers),
                        new TbQueueCallback() {
                            @Override
                            public void onSuccess(TbQueueMsgMetadata metadata) {
                                log.trace("[{}][{}] Successfully sent response.", clientId, requestInfo.getRequestId());
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                log.warn("[{}][{}] Failed to send response.", clientId, requestInfo.getRequestId(), t);
                            }
                        }));
    }

    private DefaultTbQueueMsgHeaders createResponseHeaders(UUID requestId) {
        DefaultTbQueueMsgHeaders headers = new DefaultTbQueueMsgHeaders();
        headers.put(BrokerConstants.REQUEST_ID_HEADER, BytesUtil.uuidToBytes(requestId));
        return headers;
    }

    boolean isRequestTimedOut(long requestTime) {
        return requestTime + requestTimeout < System.currentTimeMillis();
    }

    private ClientSessionInfo getClientSessionInfo(String clientId) {
        return clientSessionService.getClientSessionInfo(clientId);
    }

    private void saveClientSession(ClientSessionInfo clientSessionInfo) {
        clientSessionService.saveClientSession(clientSessionInfo, null);
    }

    private void fullSessionRemove(ClientSessionInfo session, boolean shouldClearPersistedMessages) {
        String clientId = session.getClientId();
        clearSessionAndSubscriptions(clientId);
        if (shouldClearPersistedMessages) {
            clearPersistedMessages(clientId, session.getType());
        }
        removeClientLatestTs(clientId);
        evictFromCache(clientId);
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

    private ClientSessionInfo disconnectCurrentSession(ClientSessionInfo currentSession, SessionInfo connectingSessionInfo) {
        String clientId = connectingSessionInfo.getClientId();
        String serviceId = currentSession.getServiceId();
        UUID sessionId = currentSession.getSessionId();
        log.trace("[{}] Requesting session conflict disconnect, serviceId - {}, sessionId - {}.",
                clientId, serviceId, sessionId);
        disconnectClientCommandService.disconnectOnSessionConflict(serviceId, clientId, sessionId, connectingSessionInfo.isCleanStart());
        return finishOnConflictDisconnect(currentSession);
    }

    private boolean isSameSession(ClientSessionInfo currentSession, UUID incomingSessionId) {
        return currentSession != null && incomingSessionId.equals(currentSession.getSessionId());
    }

    private void persistClientInfoInCache(String clientId, ClientConnectInfo connectInfo) {
        cacheOps.put(CLIENT_SESSION_CREDENTIALS_CACHE, clientId, connectInfo.getAuthDetails());
        cacheOps.put(CLIENT_MQTT_VERSION_CACHE, clientId, connectInfo.getMqttVersion());
    }

    private void evictFromCache(String clientId) {
        cacheOps.evictIfPresentSafe(CLIENT_SESSION_CREDENTIALS_CACHE, clientId);
        cacheOps.evictIfPresentSafe(CLIENT_MQTT_VERSION_CACHE, clientId);
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

    @RequiredArgsConstructor
    private static final class TwoPhaseCompletion {

        private final int requiredSuccessSteps;
        private final Runnable onAllSuccess;
        private final Runnable onFailureOnce;

        private final AtomicInteger successSteps = new AtomicInteger();
        private final AtomicBoolean failureFired = new AtomicBoolean();


        static TwoPhaseCompletion forTwoOperations(Runnable onAllSuccess, Runnable onFailureOnce) {
            return new TwoPhaseCompletion(2, onAllSuccess, onFailureOnce);
        }

        static TwoPhaseCompletion forSingleOperation(Runnable onAllSuccess, Runnable onFailureOnce) {
            return new TwoPhaseCompletion(1, onAllSuccess, onFailureOnce);
        }

        void successStep() {
            if (successSteps.incrementAndGet() >= requiredSuccessSteps) {
                onAllSuccess.run();
            }
        }

        void failStep(Throwable t) {
            if (failureFired.compareAndSet(false, true)) {
                onFailureOnce.run();
            }
        }
    }
}
