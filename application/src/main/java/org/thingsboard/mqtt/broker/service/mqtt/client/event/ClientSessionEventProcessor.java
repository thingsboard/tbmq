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
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
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
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionService;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventConst.REQUEST_ID_HEADER;
import static org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventConst.REQUEST_TIME;
import static org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventConst.RESPONSE_TOPIC_HEADER;
import static org.thingsboard.mqtt.broker.util.BytesUtil.bytesToLong;
import static org.thingsboard.mqtt.broker.util.BytesUtil.bytesToString;
import static org.thingsboard.mqtt.broker.util.BytesUtil.bytesToUuid;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClientSessionEventProcessor {

    // TODO: add manual control over ClientSession for Admins

    private ExecutorService consumersExecutor;
    private List<ExecutorService> clientExecutors;
    private final ScheduledExecutorService timeoutExecutor = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean stopped = false;

    private final ClientSessionEventQueueFactory clientSessionEventQueueFactory;
    private final ClientSessionService clientSessionService;
    private final DisconnectClientCommandService disconnectClientCommandService;
    private final SubscriptionManager subscriptionManager;
    private final ClientSessionEventFactory eventFactory;
    private final ServiceInfoProvider serviceInfoProvider;

    @Value("${queue.client-session-event.consumers-count}")
    private int consumersCount;
    @Value("${queue.client-session-event.poll-interval}")
    private long pollDuration;
    @Value("${queue.client-session-event.client-threads-count}")
    private int clientThreadsCount;

    @Value("${queue.client-session-event-response.max-request-timeout}")
    private long requestTimeout;

    private final List<TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.ClientSessionEventProto>>> eventConsumers = new ArrayList<>();
    private TbQueueProducer<TbProtoQueueMsg<QueueProtos.ClientSessionEventResponseProto>> eventResponseProducer;

    @PostConstruct
    public void init() {
        this.clientThreadsCount = clientThreadsCount <= 0 ? Runtime.getRuntime().availableProcessors() : clientThreadsCount;
        // TODO: maybe it's better to have an actor for each client and push every client-related action to that actor's queue?
        // TODO: or publish all client-related events back to the Kafka queue
        this.clientExecutors = IntStream.range(0, clientThreadsCount).boxed()
                .map(i -> Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName("client-session-event-executor-" + i)))
                .collect(Collectors.toList());
        for (int i = 0; i < clientThreadsCount; i++) {
            clientExecutors.add(Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName("client-session-event-executor-" + i)));
        }
        this.consumersExecutor = Executors.newFixedThreadPool(consumersCount, ThingsBoardThreadFactory.forName("client-session-event-consumer"));
        this.eventResponseProducer = clientSessionEventQueueFactory.createEventResponseProducer(serviceInfoProvider.getServiceId());
        for (int i = 0; i < consumersCount; i++) {
            initConsumer(i);
        }
    }

    private void initConsumer(int consumerId) {
        String consumerName = serviceInfoProvider.getServiceId() + "-" + consumerId;
        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.ClientSessionEventProto>> eventConsumer = clientSessionEventQueueFactory.createEventConsumer(consumerName);
        eventConsumers.add(eventConsumer);
        eventConsumer.subscribe();
        consumersExecutor.submit(() -> processClientSessionEvents(eventConsumer));
    }

    private void processClientSessionEvents(TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.ClientSessionEventProto>> consumer) {
        while (!stopped) {
            try {
                List<TbProtoQueueMsg<QueueProtos.ClientSessionEventProto>> msgs = consumer.poll(pollDuration);
                if (msgs.isEmpty()) {
                    continue;
                }
                // TODO: if consumer gets disconnected from Kafka, partition will be rebalanced to different Node and therefore same Client may be concurrently processed
                for (TbProtoQueueMsg<QueueProtos.ClientSessionEventProto> msg : msgs) {
                    processMsg(msg).get();
                    // TODO: test what happens if commit fails (probably need logic to retry commit before polling more messages)
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
    }

    private Future<Void> processMsg(TbProtoQueueMsg<QueueProtos.ClientSessionEventProto> msg) {
        ClientSessionEvent clientSessionEvent = eventFactory.convertToClientSessionEvent(msg.getValue());
        String clientId = clientSessionEvent.getClientId();
        return getClientExecutor(clientId).submit(() -> {
            switch (clientSessionEvent.getType()) {
                case CONNECTION_REQUEST:
                    processConnectionRequest((ConnectionRequestEvent)clientSessionEvent, msg.getHeaders());
                    return null;
                case DISCONNECTED:
                    processDisconnected((DisconnectedEvent)clientSessionEvent);
                    return null;
                case TRY_CLEAR_SESSION_REQUEST:
                    processTryClearSessionRequest((TryClearSessionRequestEvent)clientSessionEvent);
                    return null;
                default:
                    log.error("Type {} is not supported.", clientSessionEvent.getType());
                    throw new RuntimeException("Type " + clientSessionEvent.getType() + " is not supported.");
            }
        });
    }

    private void processDisconnected(DisconnectedEvent event) {
        ClientInfo clientInfo = event.getClientInfo();
        UUID sessionId = event.getSessionId();
        String clientId = clientInfo.getClientId();
        disconnectClientCommandService.notifyWaitingSession(clientId, sessionId);

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

    }

    private void processConnectionRequest(ConnectionRequestEvent event, TbQueueMsgHeaders requestHeaders) {
        SessionInfo sessionInfo = event.getSessionInfo();
        String clientId = sessionInfo.getClientInfo().getClientId();
        UUID sessionId = sessionInfo.getSessionId();
        // TODO: it's possible that we get not-relevant data since consumer didn't get message yet
        //      this can happen if node just got control over this clientId
        //      Solutions:
        //          - can force wait till the end of the topic using dummy session
        //          - gain more control over reassigning partitions + do some sync logic on reassign
        //          - remake logic to ensure that the 'leader' for client has all information (look at Raft algorithm)
        //          - save with 'version' field and if ClientSession listener encounters version conflict - merge two values or do smth else (at least log that smth is wrong)
        ClientSession currentlyConnectedSession = clientSessionService.getClientSession(clientId);
        if (currentlyConnectedSession == null || !currentlyConnectedSession.isConnected()) {
            saveClientSession(sessionInfo, requestHeaders);
            return;
        }

        UUID currentlyConnectedSessionId = currentlyConnectedSession.getSessionInfo().getSessionId();
        if (sessionId.equals(currentlyConnectedSessionId)) {
            log.warn("[{}][{}] Got CONNECT request from already connected session.", clientId, currentlyConnectedSessionId);
            return;
        }

        SettableFuture<Void> future = disconnectClientCommandService.startWaitingForDisconnect(sessionId, currentlyConnectedSessionId, clientId);
        DonAsynchron.withCallbackAndTimeout(future,
                unused -> saveClientSession(sessionInfo, requestHeaders),
                t -> {
                    long requestTime = bytesToLong(requestHeaders.get(REQUEST_TIME));
                    UUID requestId = getRequestId(requestHeaders);
                    log.debug("[{}][{}] Failed to process connection request. Exception - {}, reason - {}", clientId, requestId, t.getClass().getSimpleName(), t.getMessage());
                    log.trace("Detailed error: ", t);
                    if (t instanceof TimeoutException) {
                        getClientExecutor(clientId).execute(() -> disconnectClientCommandService.clearWaitingFuture(sessionId, currentlyConnectedSessionId, clientId));
                    }
                    if (isRequestTimedOut(requestTime)) {
                        log.debug("[{}][{}] Connection request timed out.", clientId, requestId);
                    } else {
                        sendEventResponse(clientId, requestId, requestHeaders, false);
                    }
                },
                requestTimeout, timeoutExecutor, getClientExecutor(clientId));

        log.trace("[{}] Disconnecting currently connected client session, sessionId - {}.", clientId, currentlyConnectedSessionId);
        disconnectClientCommandService.disconnectSession(sessionInfo.getServiceId(), clientId, currentlyConnectedSessionId);

    }

    private void processTryClearSessionRequest(TryClearSessionRequestEvent event) {
        String clientId = event.getClientId();
        ClientSession clientSession = clientSessionService.getClientSession(clientId);
        if (clientSession.isConnected()) {
            log.info("[{}} Is connected now, ignoring {}.", clientId, ClientSessionEventType.TRY_CLEAR_SESSION_REQUEST);
        } else {
            log.debug("[{}} Clearing client session.", clientId);
            clientSessionService.clearClientSession(clientId);
            subscriptionManager.clearSubscriptions(clientId);
        }
    }

    private void saveClientSession(SessionInfo sessionInfo, TbQueueMsgHeaders requestHeaders) {
        ClientInfo clientInfo = sessionInfo.getClientInfo();
        long requestTime = bytesToLong(requestHeaders.get(REQUEST_TIME));
        UUID requestId = getRequestId(requestHeaders);

        if (isRequestTimedOut(requestTime)) {
            log.debug("[{}][{}] Connection request timed out.", clientInfo.getClientId(), requestId);
            return;
        }

        ClientSession clientSession = ClientSession.builder()
                .connected(true)
                .sessionInfo(sessionInfo)
                .build();
        clientSessionService.saveClientSession(clientInfo.getClientId(), clientSession);

        sendEventResponse(clientInfo.getClientId(), requestId, requestHeaders, true);
    }

    private void sendEventResponse(String clientId, UUID requestId, TbQueueMsgHeaders requestHeaders, boolean successfulConnect) {
        QueueProtos.ClientSessionEventResponseProto response = QueueProtos.ClientSessionEventResponseProto.newBuilder()
                .setSuccess(successfulConnect).build();
        String responseTopic = bytesToString(requestHeaders.get(RESPONSE_TOPIC_HEADER));
        eventResponseProducer.send(responseTopic, new TbProtoQueueMsg<>(clientId, response, requestHeaders), new TbQueueCallback() {
            @Override
            public void onSuccess(TbQueueMsgMetadata metadata) {
                log.trace("[{}][{}] Successfully sent response.", clientId, requestId);
            }
            @Override
            public void onFailure(Throwable t) {
                log.debug("[{}][{}] Failed to send response. Exception - {}, reason - {}.", clientId, requestId, t.getClass().getSimpleName(), t.getMessage());
                log.trace("Detailed error: ", t);
            }
        });
    }

    private UUID getRequestId(TbQueueMsgHeaders requestHeaders) {
        byte[] requestIdHeader = requestHeaders.get(REQUEST_ID_HEADER);
        return bytesToUuid(requestIdHeader);
    }

    private boolean isRequestTimedOut(long requestTime) {
        return requestTime + requestTimeout < System.currentTimeMillis();
    }

    private ExecutorService getClientExecutor(String clientId) {
        int clientExecutorIndex = (clientId.hashCode() & 0x7FFFFFFF) % clientExecutors.size();
        return clientExecutors.get(clientExecutorIndex);
    }


    @PreDestroy
    public void destroy() {
        stopped = true;
        eventConsumers.forEach(TbQueueConsumer::unsubscribeAndClose);
        if (consumersExecutor != null) {
            consumersExecutor.shutdownNow();
        }
        if (eventResponseProducer != null) {
            eventResponseProducer.stop();
        }
        if (clientExecutors != null) {
            for (ExecutorService clientExecutor : clientExecutors) {
                clientExecutor.shutdownNow();
            }
        }
        timeoutExecutor.shutdownNow();
    }
}
