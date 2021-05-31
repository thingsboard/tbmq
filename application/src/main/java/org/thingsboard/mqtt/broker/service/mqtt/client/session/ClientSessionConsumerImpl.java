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
package org.thingsboard.mqtt.broker.service.mqtt.client.session;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ClientSessionQueueFactory;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionConst.EMPTY_CLIENT_SESSION_INFO_PROTO;
import static org.thingsboard.mqtt.broker.util.BytesUtil.bytesToString;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClientSessionConsumerImpl implements ClientSessionConsumer {
    private volatile boolean initializing = true;
    private volatile boolean stopped = false;

    private final ExecutorService consumerExecutor = Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName("client-session-listener"));

    @Value("${queue.client-session.poll-interval}")
    private long pollDuration;

    private final ClientSessionQueueFactory clientSessionQueueFactory;
    private final ServiceInfoProvider serviceInfoProvider;
    private final ClientSessionPersistenceService persistenceService;

    private TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.ClientSessionInfoProto>> clientSessionConsumer;

    @PostConstruct
    public void init() {
        this.clientSessionConsumer = clientSessionQueueFactory.createConsumer(serviceInfoProvider.getServiceId());
    }

    @Override
    public Map<String, ClientSessionInfo> initLoad() {
        // TODO: if 'serviceId' of session == 'currentServiceId' -> it's OK, else we need to ensure that all events from other services are consumed (we can publish blank msg for that client)
        //          need to have 'versionId' to check if ClientSession is updated based on the correct value
        log.info("Loading client sessions.");

        ClientSessionInfo dummySession = persistDummySession();

        clientSessionConsumer.subscribe();

        List<TbProtoQueueMsg<QueueProtos.ClientSessionInfoProto>> messages;
        boolean encounteredDummySession = false;
        Map<String, ClientSessionInfo> allClientSessions = new HashMap<>();
        do {
            try {
                // TODO: think how to migrate data inside of the Kafka (in case of any changes to the protocol)
                messages = clientSessionConsumer.poll(pollDuration);
                for (TbProtoQueueMsg<QueueProtos.ClientSessionInfoProto> msg : messages) {
                    String clientId = msg.getKey();
                    if (isClientSessionInfoProtoEmpty(msg.getValue())) {
                        // this means Kafka log compaction service haven't cleared empty message yet
                        log.debug("[{}] Encountered empty ClientSessionInfo.", clientId);
                        allClientSessions.remove(clientId);
                    } else {
                        ClientSessionInfo clientSession = ProtoConverter.convertToClientSessionInfo(msg.getValue());
                        if (dummySession.equals(clientSession)) {
                            encounteredDummySession = true;
                        } else {
                            allClientSessions.put(clientId, clientSession);
                        }
                    }
                }
                clientSessionConsumer.commit();
            } catch (Exception e) {
                log.error("Failed to load client sessions.", e);
                throw e;
            }
        } while (!stopped && !encounteredDummySession);

        clearDummySession(dummySession);

        initializing = false;

        return allClientSessions;
    }

    @Override
    public void listen(ClientSessionChangesCallback callback) {
        if (initializing) {
            throw new RuntimeException("Cannot start listening before initialization is finished.");
        }
        // TODO: add concurrent consumers for multiple partitions (need to store offsets)
        consumerExecutor.execute(() -> {
            while (!stopped) {
                try {
                    // TODO: test what happens if we got disconnected and connected again (will we read all msgs from beginning?)
                    List<TbProtoQueueMsg<QueueProtos.ClientSessionInfoProto>> messages = clientSessionConsumer.poll(pollDuration);
                    if (messages.isEmpty()) {
                        continue;
                    }
                    for (TbProtoQueueMsg<QueueProtos.ClientSessionInfoProto> msg : messages) {
                        String clientId = msg.getKey();
                        String serviceId = bytesToString(msg.getHeaders().get(ClientSessionConst.SERVICE_ID_HEADER));
                        if (isClientSessionInfoProtoEmpty(msg.getValue())) {
                            callback.accept(clientId, serviceId, null);
                        } else {
                            ClientSessionInfo clientSession = ProtoConverter.convertToClientSessionInfo(msg.getValue());
                            callback.accept(clientId, serviceId, clientSession);
                        }
                    }
                    clientSessionConsumer.commit();
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
        });

    }

    private ClientSessionInfo persistDummySession() {
        String dummyClientId = UUID.randomUUID().toString();
        ClientSessionInfo dummyClientSessionInfo = ClientSessionInfo.builder()
                .clientSession(ClientSession.builder()
                        .connected(false)
                        .sessionInfo(SessionInfo.builder()
                                .serviceId(serviceInfoProvider.getServiceId())
                                .persistent(false)
                                .sessionId(UUID.randomUUID())
                                .clientInfo(new ClientInfo(dummyClientId, ClientType.DEVICE))
                                .build())
                        .build())
                .lastUpdateTime(System.currentTimeMillis())
                .build();
        persistenceService.persistClientSessionInfo(dummyClientId, ProtoConverter.convertToClientSessionInfoProto(dummyClientSessionInfo));
        return dummyClientSessionInfo;
    }

    private void clearDummySession(ClientSessionInfo dummySession) {
        String clientId = dummySession.getClientSession().getSessionInfo().getClientInfo().getClientId();
        persistenceService.persistClientSessionInfo(clientId, EMPTY_CLIENT_SESSION_INFO_PROTO);
    }

    private boolean isClientSessionInfoProtoEmpty(QueueProtos.ClientSessionInfoProto clientSessionInfoProto) {
        return clientSessionInfoProto.getSessionInfo().getClientInfo().getClientId().isEmpty()
                && clientSessionInfoProto.getSessionInfo().getClientInfo().getClientType().isEmpty();
    }

    @PreDestroy
    public void destroy() {
        stopped = true;
        if (clientSessionConsumer != null) {
            clientSessionConsumer.unsubscribeAndClose();
        }
        consumerExecutor.shutdownNow();
    }
}
