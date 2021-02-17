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
package org.thingsboard.mqtt.broker.service.mqtt.persistence;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.QueueFactory;
import org.thingsboard.mqtt.broker.service.mqtt.LastPublishCtx;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
public class BaseLastPublishCtxService implements LastPublishCtxService {
    private final QueueProtos.LastPublishCtxProto EMPTY_LAST_PUBLISH_CTX_PROTO = QueueProtos.LastPublishCtxProto.newBuilder().build();

    private final Map<String, LastPublishCtx> lastPublishCtxMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> lastPersistedPacketIdMap = new ConcurrentHashMap<>();

    private final List<ExecutorService> clientExecutors;
    private final TbQueueProducer<TbProtoQueueMsg<QueueProtos.LastPublishCtxProto>> lastPublishCtxProducer;

    private final long pollDuration;
    private final QueueFactory<QueueProtos.LastPublishCtxProto> queueFactory;

    @Builder
    public BaseLastPublishCtxService(String name, int clientThreadsCount, long pollDuration, QueueFactory<QueueProtos.LastPublishCtxProto> queueFactory) {
        this.clientExecutors = new ArrayList<>(clientThreadsCount);
        for (int i = 0; i < clientThreadsCount; i++) {
            clientExecutors.add(Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName(name + "-" + i)));
        }
        this.pollDuration = pollDuration;
        this.queueFactory = queueFactory;
        this.lastPublishCtxProducer = queueFactory.createProducer();
    }

    @Override
    public void loadPersistedCtx() {
        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.LastPublishCtxProto>> consumer = queueFactory.createConsumer();
        consumer.assignAllPartitions();
        consumer.seekToTheBeginning();
        List<TbProtoQueueMsg<QueueProtos.LastPublishCtxProto>> messages;
        do {
            try {
                messages = consumer.poll(pollDuration);
                for (TbProtoQueueMsg<QueueProtos.LastPublishCtxProto> msg : messages) {
                    String clientId = msg.getKey();
                    if (isLastPublishCtxProtoEmpty(msg.getValue())) {
                        // this means Kafka log compaction service haven't cleared empty message yet
                        log.debug("[{}] Encountered empty last publish context.", clientId);
                        lastPublishCtxMap.remove(clientId);
                    } else {
                        LastPublishCtx lastPublishCtx = ProtoConverter.convertToLastPublishCtx(msg.getValue());
                        lastPublishCtxMap.put(clientId, lastPublishCtx);
                    }
                }
                consumer.commit();
            } catch (Exception e) {
                log.error("Failed to load persisted publish contexts.", e);
                throw e;
            }
        } while (!messages.isEmpty());
        consumer.unsubscribeAndClose();
    }

    @Override
    public int getNextPacketId(String clientId) {
        LastPublishCtx lastPublishCtx = lastPublishCtxMap.computeIfAbsent(clientId, ignored -> new LastPublishCtx(0));
        return lastPublishCtx.getNextPacketId();
    }

    @Override
    public void saveLastPublishCtx(String clientId, int packetId) {
        getClientExecutor(clientId).execute(() -> {
            Integer lastPersistedPacketId = this.lastPersistedPacketIdMap.computeIfAbsent(clientId, ignored -> 0);
            if (lastPersistedPacketId >= packetId) {
                log.trace("[{}] Not persisting publish context, packetId - {}, last persisted packet id - {}.",
                        clientId, packetId, lastPersistedPacketId);
            } else {
                lastPersistedPacketIdMap.put(clientId, packetId);
                lastPublishCtxProducer.send(new TbProtoQueueMsg<>(clientId, ProtoConverter.createLastPublishCtxProto(packetId)),
                        new TbQueueCallback() {
                            @Override
                            public void onSuccess(TbQueueMsgMetadata metadata) {
                                log.trace("[{}] Successfully sent last publish context to the queue.", clientId);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                // TODO think if it's critical if we failed to publish latest context
                                log.error("[{}] Failed to send last publish context to the queue. Reason - {}.",
                                        clientId, t.getMessage());
                                log.debug("Detailed error: ", t);
                            }
                        });
            }
        });
    }

    @Override
    public void clearContext(String clientId) {
        getClientExecutor(clientId).execute(() -> {
            LastPublishCtx removedCtx = lastPublishCtxMap.remove(clientId);
            if (removedCtx == null) {
                log.trace("[{}] No persisted publish context found.", clientId);
                return;
            }
            lastPublishCtxProducer.send(new TbProtoQueueMsg<>(clientId, EMPTY_LAST_PUBLISH_CTX_PROTO),
                    new TbQueueCallback() {
                        @Override
                        public void onSuccess(TbQueueMsgMetadata metadata) {
                            log.trace("[{}] Successfully cleared persisted publish context.", clientId);
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            log.warn("[{}] Failed to clear persisted publish context.", clientId);
                            log.trace("Detailed error:", t);
                        }
                    });
        });
    }

    @Override
    public void destroy() {
        if (lastPublishCtxProducer != null) {
            lastPublishCtxProducer.stop();
        }
        gracefullyStopClientExecutors();
    }

    private void gracefullyStopClientExecutors() {
        for (int i = 0; i < clientExecutors.size(); i++) {
            ExecutorService clientExecutor = clientExecutors.get(i);
            log.debug("Shutting down executor #{}", i);
            clientExecutor.shutdown();
            try {
                if (!clientExecutor.awaitTermination(100, TimeUnit.MILLISECONDS)) {
                    log.warn("Failed to await termination of executor #{}", i);
                    clientExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.warn("Failed to await termination of executor #{}", i);
                clientExecutor.shutdownNow();
            }
        }
    }

    private boolean isLastPublishCtxProtoEmpty(QueueProtos.LastPublishCtxProto lastPublishCtxProto) {
        return lastPublishCtxProto.getPacketId() == 0;
    }

    private Executor getClientExecutor(String clientId) {
        int clientExecutorIndex = (clientId.hashCode() & 0x7FFFFFFF) % clientExecutors.size();
        return clientExecutors.get(clientExecutorIndex);
    }
}
