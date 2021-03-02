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
package org.thingsboard.mqtt.broker.service.mqtt.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ClientSessionQueueFactory;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;

import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class DefaultClientSessionPersistenceService implements ClientSessionPersistenceService {
    @Value("${queue.client-session.poll-interval}")
    private long pollDuration;
    @Value("${queue.client-session.acknowledge-wait-timeout-ms}")
    private long ackTimeoutMs;

    private final ClientSessionQueueFactory clientSessionQueueFactory;
    private final TbQueueProducer<TbProtoQueueMsg<QueueProtos.ClientSessionProto>> clientSessionProducer;

    public DefaultClientSessionPersistenceService(ClientSessionQueueFactory clientSessionQueueFactory) {
        this.clientSessionQueueFactory = clientSessionQueueFactory;
        this.clientSessionProducer = clientSessionQueueFactory.createProducer();
    }

    @Override
    public Map<String, ClientSession> loadAllClientSessions() {
        log.info("Loading client sessions.");
        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.ClientSessionProto>> clientSessionConsumer = clientSessionQueueFactory.createConsumer(UUID.randomUUID().toString());
        clientSessionConsumer.assignAllPartitions();
        List<TbProtoQueueMsg<QueueProtos.ClientSessionProto>> messages;
        Map<String, ClientSession> allClientSessions = new HashMap<>();
        do {
            try {
                messages = clientSessionConsumer.poll(pollDuration);
                for (TbProtoQueueMsg<QueueProtos.ClientSessionProto> msg : messages) {
                    String clientId = msg.getKey();
                    if (isClientSessionProtoEmpty(msg.getValue())) {
                        // this means Kafka log compaction service haven't cleared empty message yet
                        log.debug("[{}] Encountered empty ClientSession.", clientId);
                        allClientSessions.remove(clientId);
                    } else {
                        ClientSession prevClientSession = ProtoConverter.convertToClientSession(msg.getValue());
                        ClientSession clientSession = prevClientSession.toBuilder().connected(false).build();
                        allClientSessions.put(clientId, clientSession);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to load client sessions.", e);
                throw e;
            }
        } while (!messages.isEmpty());

        clientSessionConsumer.unsubscribeAndClose();

        return allClientSessions;
    }

    @Override
    public void persistClientSession(String clientId, QueueProtos.ClientSessionProto clientSessionProto) {
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        // TODO: think if we need waiting for the result here
        CountDownLatch updateWaiter = new CountDownLatch(1);
        clientSessionProducer.send(new TbProtoQueueMsg<>(clientId, clientSessionProto),
                new TbQueueCallback() {
                    @Override
                    public void onSuccess(TbQueueMsgMetadata metadata) {
                        updateWaiter.countDown();
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        errorRef.getAndSet(t);
                        updateWaiter.countDown();
                    }
                });

        boolean waitSuccessful = false;
        try {
            // TODO is this OK that the thread is blocked?
            waitSuccessful = updateWaiter.await(ackTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            errorRef.getAndSet(e);
        }
        Throwable error = errorRef.get();
        if (!waitSuccessful || error != null) {
            log.warn("[{}] Failed to update client session. Reason - {}.",
                    clientId, error != null ? error.getMessage() : "timeout waiting");
            if (error != null) {
                log.trace("Detailed error:", error);
            }
            throw new MqttException("Failed to update client session.");
        }
    }

    private boolean isClientSessionProtoEmpty(QueueProtos.ClientSessionProto clientSessionProto) {
        return clientSessionProto.getSessionInfo().getClientInfo().getClientId().isEmpty()
                && clientSessionProto.getSessionInfo().getClientInfo().getClientType().isEmpty();
    }

    @PreDestroy
    public void destroy() {
        if (clientSessionProducer != null) {
            clientSessionProducer.stop();
        }
    }
}
