/**
 * Copyright © 2016-2024 The Thingsboard Authors
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

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.exception.QueuePersistenceException;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.constants.QueueConstants;
import org.thingsboard.mqtt.broker.queue.provider.ClientSessionQueueFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.thingsboard.mqtt.broker.common.data.util.BytesUtil.bytesToString;
import static org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory.getClientSessionInfo;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClientSessionConsumerImpl implements ClientSessionConsumer {

    private final ExecutorService consumerExecutor = Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName("client-session-listener"));

    private final ClientSessionQueueFactory clientSessionQueueFactory;
    private final ServiceInfoProvider serviceInfoProvider;
    private final ClientSessionPersistenceService persistenceService;
    private final TbQueueAdmin queueAdmin;

    @Value("${queue.client-session.poll-interval}")
    private long pollDuration;

    private volatile boolean initializing = true;
    private volatile boolean stopped = false;

    private TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.ClientSessionInfoProto>> clientSessionConsumer;

    @PostConstruct
    public void init() {
        long currentCgSuffix = System.currentTimeMillis();
        String uniqueConsumerGroupId = serviceInfoProvider.getServiceId() + "-" + currentCgSuffix;
        this.clientSessionConsumer = clientSessionQueueFactory.createConsumer(serviceInfoProvider.getServiceId(), uniqueConsumerGroupId);
        queueAdmin.deleteOldConsumerGroups(BrokerConstants.CLIENT_SESSION_CG_PREFIX, serviceInfoProvider.getServiceId(), currentCgSuffix);
    }

    @Override
    public Map<String, ClientSessionInfo> initLoad() throws QueuePersistenceException {
        log.debug("Starting client sessions initLoad");
        long startTime = System.nanoTime();
        long totalMessageCount = 0L;

        String dummySessionClientId = persistDummySession();
        clientSessionConsumer.assignOrSubscribe();

        List<TbProtoQueueMsg<QueueProtos.ClientSessionInfoProto>> messages;
        boolean encounteredDummySession = false;
        Map<String, ClientSessionInfo> allClientSessions = new HashMap<>();
        do {
            try {
                messages = clientSessionConsumer.poll(pollDuration);
                int packSize = messages.size();
                log.debug("Read {} client session messages from single poll", packSize);
                totalMessageCount += packSize;
                for (TbProtoQueueMsg<QueueProtos.ClientSessionInfoProto> msg : messages) {
                    String clientId = msg.getKey();
                    if (isClientSessionInfoProtoEmpty(msg.getValue())) {
                        // this means Kafka log compaction service haven't cleared empty message yet
                        log.trace("[{}] Encountered empty ClientSessionInfo.", clientId);
                        allClientSessions.remove(clientId);
                    } else {
                        ClientSessionInfo clientSession = ProtoConverter.convertToClientSessionInfo(msg.getValue());
                        if (dummySessionClientId.equals(clientId)) {
                            encounteredDummySession = true;
                        } else {
                            allClientSessions.put(clientId, clientSession);
                        }
                    }
                }
                clientSessionConsumer.commitSync();
            } catch (Exception e) {
                log.error("Failed to load client sessions.", e);
                throw e;
            }
        } while (!stopped && !encounteredDummySession);

        clearDummySession(dummySessionClientId);

        initializing = false;

        if (log.isDebugEnabled()) {
            long endTime = System.nanoTime();
            log.debug("Finished client session messages initLoad for {} messages within time: {} nanos", totalMessageCount, endTime - startTime);
        }

        return allClientSessions;
    }

    @Override
    public void listen(ClientSessionChangesCallback callback) {
        if (initializing) {
            throw new RuntimeException("Cannot start listening before initialization is finished.");
        }
        consumerExecutor.execute(() -> {
            while (!stopped) {
                try {
                    List<TbProtoQueueMsg<QueueProtos.ClientSessionInfoProto>> messages = clientSessionConsumer.poll(pollDuration);
                    if (messages.isEmpty()) {
                        continue;
                    }
                    for (TbProtoQueueMsg<QueueProtos.ClientSessionInfoProto> msg : messages) {
                        String clientId = msg.getKey();
                        String serviceId = bytesToString(msg.getHeaders().get(BrokerConstants.SERVICE_ID_HEADER));
                        if (isClientSessionInfoProtoEmpty(msg.getValue())) {
                            callback.accept(clientId, serviceId, null);
                        } else {
                            ClientSessionInfo clientSession = ProtoConverter.convertToClientSessionInfo(msg.getValue());
                            callback.accept(clientId, serviceId, clientSession);
                        }
                    }
                    clientSessionConsumer.commitSync();
                } catch (Exception e) {
                    if (!stopped) {
                        log.error("Failed to process messages from queue.", e);
                        try {
                            Thread.sleep(pollDuration);
                        } catch (InterruptedException e2) {
                            if (log.isTraceEnabled()) {
                                log.trace("Failed to wait until the server has capacity to handle new requests", e2);
                            }
                        }
                    }
                }
            }
        });

    }

    private String persistDummySession() throws QueuePersistenceException {
        String dummyClientId = UUID.randomUUID().toString();
        ClientSessionInfo dummyClientSessionInfo = getClientSessionInfo(dummyClientId, serviceInfoProvider.getServiceId(), false);
        persistenceService.persistClientSessionInfoSync(dummyClientId, ProtoConverter.convertToClientSessionInfoProto(dummyClientSessionInfo));
        return dummyClientId;
    }

    private void clearDummySession(String clientId) throws QueuePersistenceException {
        persistenceService.persistClientSessionInfoSync(clientId, QueueConstants.EMPTY_CLIENT_SESSION_INFO_PROTO);
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
            if (this.clientSessionConsumer.getConsumerGroupId() != null) {
                queueAdmin.deleteConsumerGroups(Collections.singleton(this.clientSessionConsumer.getConsumerGroupId()));
            }
        }
        ThingsBoardExecutors.shutdownAndAwaitTermination(consumerExecutor, "Client sessions consumer");
    }
}
