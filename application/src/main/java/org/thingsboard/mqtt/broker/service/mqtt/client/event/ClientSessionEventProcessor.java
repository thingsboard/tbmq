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
package org.thingsboard.mqtt.broker.service.mqtt.client.event;

import com.google.common.util.concurrent.SettableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.util.DonAsynchron;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgHeaders;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ClientSessionEventQueueFactory;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;
import org.thingsboard.mqtt.broker.service.mqtt.client.ClientSessionService;
import org.thingsboard.mqtt.broker.service.mqtt.client.disconnect.DisconnectClientCommandService;
import org.thingsboard.mqtt.broker.service.subscription.SubscriptionManager;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;

import static org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventConst.REQUEST_ID_HEADER;
import static org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventConst.REQUEST_TIME;
import static org.thingsboard.mqtt.broker.util.BytesUtil.bytesToLong;
import static org.thingsboard.mqtt.broker.util.BytesUtil.bytesToUuid;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClientSessionEventProcessor {

    private final ScheduledExecutorService timeoutExecutor = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService consumersExecutor = Executors.newCachedThreadPool(ThingsBoardThreadFactory.forName("client-session-event-consumer"));
    private final List<ExecutorService> clientExecutors = new ArrayList<>();
    private volatile boolean stopped = false;

    private final ClientSessionEventQueueFactory clientSessionEventQueueFactory;
    private final ClientSessionService clientSessionService;
    private final DisconnectClientCommandService disconnectClientCommandService;
    private final SubscriptionManager subscriptionManager;

    @Value("${queue.client-session-event.consumers-count}")
    private int consumersCount;
    @Value("${queue.client-session-event.poll-interval}")
    private long pollDuration;
    @Value("${queue.client-session-event.client-threads-count}")
    private long clientThreadsCount;

    @Value("${queue.client-session-event-response.max-request-timeout}")
    private long requestTimeout;

    private final List<TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.ClientSessionEventProto>>> eventConsumers = new ArrayList<>();
    private TbQueueProducer<TbProtoQueueMsg<QueueProtos.ClientSessionEventResponseProto>> eventResponseProducer;

    @PostConstruct
    public void init() {
        this.clientThreadsCount = clientThreadsCount <= 0 ? Runtime.getRuntime().availableProcessors() : clientThreadsCount;
        for (int i = 0; i < clientThreadsCount; i++) {
            // TODO: maybe it's better to have an actor for each client and push every client-related action to that actor's queue?
            clientExecutors.add(Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName("client-session-event-executor-" + i)));
        }
        this.eventResponseProducer = clientSessionEventQueueFactory.createEventResponseProducer();
        for (int i = 0; i < consumersCount; i++) {
            eventConsumers.add(clientSessionEventQueueFactory.createEventConsumer(Integer.toString(i)));
        }
        for (TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.ClientSessionEventProto>> eventConsumer : eventConsumers) {
            eventConsumer.subscribe();
            launchConsumer(eventConsumer);
        }
    }

    private void launchConsumer(TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.ClientSessionEventProto>> consumer) {
        consumersExecutor.submit(() -> {
            while (!stopped) {
                try {
                    List<TbProtoQueueMsg<QueueProtos.ClientSessionEventProto>> msgs = consumer.poll(pollDuration);
                    if (msgs.isEmpty()) {
                        continue;
                    }
                    for (TbProtoQueueMsg<QueueProtos.ClientSessionEventProto> msg : msgs) {
                        processMsg(msg).get();
                        consumer.commit(msg.getPartition(), msg.getOffset() + 1);
                    }
                } catch (Exception e) {
                    if (!stopped) {
                        log.error("Failed to process messages from queue.", e);
                        try {
                            Thread.sleep(pollDuration);
                        } catch (InterruptedException e2) {
                            log.trace("Failed to wait until the server has capacity to handle new requests", e2);
                        }
                    }
                }
            }
            log.info("Client Session Event Consumer stopped.");
        });
    }

    private Future<Void> processMsg(TbProtoQueueMsg<QueueProtos.ClientSessionEventProto> msg) {
        ClientSessionEvent clientSessionEvent = ProtoConverter.convertToClientSessionEvent(msg.getValue());
        String clientId = clientSessionEvent.getClientInfo().getClientId();
        return getClientExecutor(clientId).submit(() -> {
            switch (clientSessionEvent.getEventType()) {
                case CONNECTION_REQUEST:
                    processConnectionRequest(clientSessionEvent.getSessionId(), clientSessionEvent.getClientInfo(), clientSessionEvent.isPersistent(), msg.getHeaders());
                    return null;
                case DISCONNECTED:
                    processDisconnected(clientSessionEvent.getSessionId(), clientSessionEvent.getClientInfo());
                    return null;
                case TRY_CLEAR_SESSION_REQUEST:
                    processTryClearSessionRequest(clientSessionEvent.getClientInfo());
                    return null;
                default:
                    log.error("Type {} is not supported.", clientSessionEvent.getEventType());
                    throw new RuntimeException("Type " + clientSessionEvent.getEventType() + " is not supported.");
            }
        });
    }

    private void processDisconnected(UUID sessionId, ClientInfo clientInfo) {
        String clientId = clientInfo.getClientId();

        ClientSession clientSession = clientSessionService.getClientSession(clientId);
        if (clientSession == null) {
            log.warn("[{}][{}] Cannot find client session.", clientId, sessionId);
            return;
        }
        if (!sessionId.equals(clientSession.getSessionInfo().getSessionId())) {
            log.error("[{}] Got disconnect event from the session with different sessionId. Currently connected sessionId - {}, " +
                    "received sessionId - {}.", clientId, clientSession.getSessionInfo().getSessionId(), sessionId);
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

    private void processConnectionRequest(UUID sessionId, ClientInfo clientInfo, boolean isPersistent, TbQueueMsgHeaders requestHeaders) {
        String clientId = clientInfo.getClientId();
        ClientSession currentlyConnectedSession = clientSessionService.getClientSession(clientId);
        if (currentlyConnectedSession != null && currentlyConnectedSession.isConnected()) {
            UUID currentlyConnectedSessionId = currentlyConnectedSession.getSessionInfo().getSessionId();
            if (sessionId.equals(currentlyConnectedSessionId)) {
                log.warn("[{}][{}] Got CONNECT request from already connected session.", clientId, currentlyConnectedSessionId);
                return;
            }

            SettableFuture<Void> future = disconnectClientCommandService.startWaitingForDisconnect(sessionId, currentlyConnectedSessionId, clientId);
            DonAsynchron.withCallbackAndTimeout(future,
                    unused -> saveClientSession(sessionId, clientInfo, isPersistent, requestHeaders),
                    t -> {
                        long requestTime = bytesToLong(requestHeaders.get(REQUEST_TIME));
                        byte[] requestIdHeader = requestHeaders.get(REQUEST_ID_HEADER);
                        UUID requestId = bytesToUuid(requestIdHeader);
                        log.debug("[{}][{}] Failed to process connection request. Reason - {}", clientId, requestId, t.getMessage());
                        log.trace("Detailed error: ", t);
                        if (t instanceof TimeoutException) {
                            getClientExecutor(clientId).execute(() -> disconnectClientCommandService.clearWaitingFuture(sessionId, currentlyConnectedSessionId, clientId));
                        }
                        long currentTime = System.currentTimeMillis();
                        if (requestTime + requestTimeout >= currentTime) {
                            sendEventResponse(clientId, requestId, requestHeaders, false);
                        } else {
                            log.debug("[{}][{}] Connection request timed out.", clientId, requestId);
                        }
                    },
                    requestTimeout, timeoutExecutor, getClientExecutor(clientId));

            log.trace("[{}] Disconnecting currently connected client session, sessionId - {}.", clientId, currentlyConnectedSessionId);
            // TODO (Cluster Mode): store node topic and send disconnect command there
            disconnectClientCommandService.disconnectSession(clientId, currentlyConnectedSessionId);

        } else {
            saveClientSession(sessionId, clientInfo, isPersistent, requestHeaders);
        }
    }

    private void processTryClearSessionRequest(ClientInfo clientInfo) {
        String clientId = clientInfo.getClientId();
        ClientSession clientSession = clientSessionService.getClientSession(clientId);
        if (clientSession.isConnected()) {
            log.info("[{}} Is connected now, ignoring {}.", clientId, ClientSessionEventType.TRY_CLEAR_SESSION_REQUEST);
        } else {
            log.debug("[{}} Clearing client session.", clientId);
            clientSessionService.clearClientSession(clientId);
            subscriptionManager.clearSubscriptions(clientId);
        }
    }

    private void saveClientSession(UUID sessionId, ClientInfo clientInfo, boolean isPersistent, TbQueueMsgHeaders requestHeaders) {
        long requestTime = bytesToLong(requestHeaders.get(REQUEST_TIME));
        byte[] requestIdHeader = requestHeaders.get(REQUEST_ID_HEADER);
        UUID requestId = bytesToUuid(requestIdHeader);
        long currentTime = System.currentTimeMillis();
        if (requestTime + requestTimeout >= currentTime) {
            ClientSession clientSession = ClientSession.builder()
                    .connected(true)
                    .sessionInfo(SessionInfo.builder()
                            .sessionId(sessionId)
                            .persistent(isPersistent)
                            .clientInfo(clientInfo)
                            .build())
                    .build();
            clientSessionService.saveClientSession(clientInfo.getClientId(), clientSession);

            sendEventResponse(clientInfo.getClientId(), requestId, requestHeaders, true);
        } else {
            log.debug("[{}][{}] Connection request timed out.", clientInfo.getClientId(), requestId);
        }
    }

    private void sendEventResponse(String clientId, UUID requestId, TbQueueMsgHeaders requestHeaders, boolean successfulConnect) {
        QueueProtos.ClientSessionEventResponseProto response = QueueProtos.ClientSessionEventResponseProto.newBuilder()
                .setSuccess(successfulConnect).build();
        eventResponseProducer.send(new TbProtoQueueMsg<>(clientId, response, requestHeaders), new TbQueueCallback() {
            @Override
            public void onSuccess(TbQueueMsgMetadata metadata) {
                log.trace("[{}][{}] Successfully sent response.", clientId, requestId);
            }

            @Override
            public void onFailure(Throwable t) {
                log.debug("[{}][{}] Failed to sent response. Reason - {}.", clientId, requestId, t.getMessage());
                log.trace("Detailed error: ", t);
            }
        });
    }

    private ExecutorService getClientExecutor(String clientId) {
        int clientExecutorIndex = (clientId.hashCode() & 0x7FFFFFFF) % clientExecutors.size();
        return clientExecutors.get(clientExecutorIndex);
    }


    @PreDestroy
    public void destroy() {
        stopped = true;
        eventConsumers.forEach(TbQueueConsumer::unsubscribeAndClose);
        consumersExecutor.shutdownNow();
        if (eventResponseProducer != null) {
            eventResponseProducer.stop();
        }
        for (ExecutorService clientExecutor : clientExecutors) {
            clientExecutor.shutdownNow();
        }
        timeoutExecutor.shutdownNow();
    }
}
