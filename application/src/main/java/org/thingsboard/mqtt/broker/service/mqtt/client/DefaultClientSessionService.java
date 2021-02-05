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
import org.thingsboard.mqtt.broker.common.data.queue.TopicInfo;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ClientSessionQueueFactory;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DefaultClientSessionService implements ClientSessionService {

    private final Map<String, ClientSession> clientSessionMap = new ConcurrentHashMap<>();


    @Value("${queue.client-session.poll-interval}")
    private long pollDuration;
    @Value("${queue.client-session.acknowledge-wait-timeout-ms}")
    private long ackTimeoutMs;

    private final TbQueueProducer<TbProtoQueueMsg<QueueProtos.ClientSessionProto>> clientSessionProducer;
    private final TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.ClientSessionProto>> clientSessionConsumer;

    public DefaultClientSessionService(ClientSessionQueueFactory clientSessionQueueFactory) {
        this.clientSessionProducer = clientSessionQueueFactory.createProducer();
        this.clientSessionConsumer = clientSessionQueueFactory.createConsumer();
    }

    @PostConstruct
    public void init() {
        clientSessionConsumer.assignAllPartitions();
        clientSessionConsumer.seekToTheBeginning();
        List<TbProtoQueueMsg<QueueProtos.ClientSessionProto>> messages;
        do {
            try {
                messages = clientSessionConsumer.poll(pollDuration);
                for (TbProtoQueueMsg<QueueProtos.ClientSessionProto> msg : messages) {
                    String clientId = msg.getKey();
                    if (isClientSessionProtoEmpty(msg.getValue())) {
                        // this means Kafka log compaction service haven't cleared empty message yet
                        log.debug("[{}] Encountered empty ClientSession.", clientId);
                        // TODO test this
                        clientSessionMap.remove(clientId);
                    } else {
                        ClientSession prevClientSession = ProtoConverter.convertToClientSession(msg.getValue());
                        ClientSession clientSession = prevClientSession.toBuilder().connected(false).build();
                        clientSessionMap.put(clientId, clientSession);
                    }
                }
                clientSessionConsumer.commit();
            } catch (Exception e) {
                log.error("Failed to load persisted client sessions.", e);
                throw e;
            }
        } while (!messages.isEmpty());

        clientSessionConsumer.unsubscribeAndClose();
    }

    private boolean isClientSessionProtoEmpty(QueueProtos.ClientSessionProto clientSessionProto) {
        return clientSessionProto.getClientInfo().getClientId().isEmpty() && clientSessionProto.getClientInfo().getClientType().isEmpty();
    }

    @Override
    public List<String> getPersistedClients() {
        return clientSessionMap.entrySet().stream()
                .filter(entry -> entry.getValue().isPersistent())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    @Override
    public Collection<ClientSession> getPersistedClientSessions() {
        return clientSessionMap.values();
    }

    @Override
    public ClientSession getClientSession(String clientId) {
        return clientSessionMap.get(clientId);
    }

    @Override
    public void replaceClientSession(String clientId, ClientSession expectedClientSession, ClientSession newClientSession) {
        if (!clientId.equals(newClientSession.getClientInfo().getClientId())) {
            log.error("Error replacing client session. " +
                    "Key clientId - {}, ClientSession's clientId - {}.", clientId, newClientSession.getClientInfo().getClientId());
            throw new MqttException("Key clientId should be equals to ClientSession's clientId");
        }
        if (expectedClientSession == null) {
            ClientSession prevValue = clientSessionMap.putIfAbsent(clientId, newClientSession);
            if (prevValue != null) {
                throw new MqttException("Client session with such clientId exists already.");
            }
        } else {
            boolean replaced = clientSessionMap.replace(clientId, expectedClientSession, newClientSession);
            if (!replaced) {
                throw new MqttException("Client session with such clientId differs from the expected client session.");
            }
        }

        if (!newClientSession.isPersistent()) {
            return;
        }
        TopicInfo topicInfo = new TopicInfo(clientSessionProducer.getDefaultTopic());
        QueueProtos.ClientSessionProto clientSessionProto = ProtoConverter.convertToClientSessionProto(newClientSession);

        tryPersistClientSession(clientId, topicInfo, clientSessionProto);
    }

    @Override
    public void clearClientSessionFromPersistentStorage(String clientId) {
        TopicInfo topicInfo = new TopicInfo(clientSessionProducer.getDefaultTopic());
        QueueProtos.ClientSessionProto clientSessionProto = QueueProtos.ClientSessionProto.newBuilder().build();
        tryPersistClientSession(clientId, topicInfo, clientSessionProto);
    }

    private void tryPersistClientSession(String clientId, TopicInfo topicInfo, QueueProtos.ClientSessionProto clientSessionProto) {
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch updateWaiter = new CountDownLatch(1);
        clientSessionProducer.send(topicInfo, new TbProtoQueueMsg<>(clientId, clientSessionProto),
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

    @PreDestroy
    public void destroy() {
        if (clientSessionProducer != null) {
            clientSessionProducer.stop();
        }
    }
}
