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
package org.thingsboard.mqtt.broker.service.mqtt.client.blocked.consumer;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.util.BytesUtil;
import org.thingsboard.mqtt.broker.common.data.util.UUIDUtil;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.exception.QueuePersistenceException;
import org.thingsboard.mqtt.broker.gen.queue.BlockedClientProto;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.constants.QueueConstants;
import org.thingsboard.mqtt.broker.queue.provider.BlockedClientQueueFactory;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.ClientIdBlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.producer.BlockedClientProducerService;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
public class BlockedClientConsumerServiceImpl implements BlockedClientConsumerService {

    private final ExecutorService consumerExecutor = Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName("blocked-client-consumer"));

    private final BlockedClientQueueFactory queueFactory;
    private final ServiceInfoProvider serviceInfoProvider;
    private final BlockedClientProducerService producerService;
    private final TbQueueAdmin queueAdmin;

    @Value("${queue.blocked-client.poll-interval}")
    private long pollDuration;

    private volatile boolean initializing = true;
    private volatile boolean stopped = false;

    private TbQueueControlledOffsetConsumer<TbProtoQueueMsg<BlockedClientProto>> blockedClientConsumer;

    @PostConstruct
    public void init() {
        long currentCgSuffix = System.currentTimeMillis();
        String uniqueConsumerGroupId = serviceInfoProvider.getServiceId() + "-" + currentCgSuffix;
        this.blockedClientConsumer = queueFactory.createConsumer(serviceInfoProvider.getServiceId(), uniqueConsumerGroupId);
        queueAdmin.deleteOldConsumerGroups(BrokerConstants.BLOCKED_CLIENT_CG_PREFIX, serviceInfoProvider.getServiceId(), currentCgSuffix);
    }

    @Override
    public Map<String, BlockedClient> initLoad() throws QueuePersistenceException {
        log.debug("Starting blocked client initLoad");
        long startTime = System.nanoTime();
        long totalMessageCount = 0L;

        String dummyBlockedClientKey = persistDummyBlockedClient();
        blockedClientConsumer.assignOrSubscribe();

        List<TbProtoQueueMsg<BlockedClientProto>> messages;
        boolean encounteredDummyClient = false;
        Map<String, BlockedClient> blockedClientMap = new HashMap<>();
        do {
            try {
                messages = blockedClientConsumer.poll(pollDuration);
                int packSize = messages.size();
                log.debug("Read {} blocked clients from single poll", packSize);
                totalMessageCount += packSize;
                for (TbProtoQueueMsg<BlockedClientProto> msg : messages) {
                    String key = msg.getKey();
                    if (isBlockedClientProtoEmpty(msg.getValue())) {
                        // this means Kafka log compaction service haven't cleared empty message yet
                        log.trace("[{}] Encountered empty BlockedClient", key);
                        blockedClientMap.remove(key);
                    } else {
                        BlockedClient blockedClient = convertToBlockedClient(msg);
                        if (dummyBlockedClientKey.equals(key)) {
                            encounteredDummyClient = true;
                        } else {
                            blockedClientMap.put(key, blockedClient);
                        }
                    }
                }
                blockedClientConsumer.commitSync();
            } catch (Exception e) {
                log.error("Failed to load blocked clients", e);
                throw e;
            }
        } while (!stopped && !encounteredDummyClient);

        clearDummyBlockedClient(dummyBlockedClientKey);

        initializing = false;

        if (log.isDebugEnabled()) {
            long endTime = System.nanoTime();
            log.debug("Finished blocked client initLoad for {} messages within time: {} nanos", totalMessageCount, endTime - startTime);
        }

        return blockedClientMap;
    }

    @Override
    public void listen(BlockedClientChangesCallback callback) {
        if (initializing) {
            throw new RuntimeException("Cannot start listening before blocked clients initialization is finished");
        }
        consumerExecutor.execute(() -> {
            while (!stopped) {
                try {
                    List<TbProtoQueueMsg<BlockedClientProto>> messages = blockedClientConsumer.poll(pollDuration);
                    if (messages.isEmpty()) {
                        continue;
                    }
                    for (TbProtoQueueMsg<BlockedClientProto> msg : messages) {
                        String key = msg.getKey();
                        String serviceId = BytesUtil.bytesToString(msg.getHeaders().get(BrokerConstants.SERVICE_ID_HEADER));

                        if (isBlockedClientProtoEmpty(msg.getValue())) {
                            callback.accept(key, serviceId, null);
                        } else {
                            BlockedClient blockedClient = convertToBlockedClient(msg);
                            callback.accept(key, serviceId, blockedClient);
                        }
                    }
                    blockedClientConsumer.commitSync();
                } catch (Exception e) {
                    if (!stopped) {
                        log.error("Failed to process messages from queue", e);
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

    private BlockedClient convertToBlockedClient(TbProtoQueueMsg<BlockedClientProto> msg) {
        return ProtoConverter.convertProtoToBlockedClient(msg.getValue());
    }

    private String persistDummyBlockedClient() throws QueuePersistenceException {
        BlockedClient blockedClient = new ClientIdBlockedClient(UUIDUtil.randomUuid());
        String dummyBlockedClientKey = blockedClient.getKey();
        producerService.persistDummyBlockedClient(dummyBlockedClientKey, ProtoConverter.convertToBlockedClientProto(blockedClient));
        return dummyBlockedClientKey;
    }

    private void clearDummyBlockedClient(String dummyBlockedClientKey) throws QueuePersistenceException {
        producerService.persistDummyBlockedClient(dummyBlockedClientKey, QueueConstants.EMPTY_BLOCKED_CLIENT_PROTO);
    }

    private boolean isBlockedClientProtoEmpty(BlockedClientProto blockedClientProto) {
        return QueueConstants.EMPTY_BLOCKED_CLIENT_PROTO.equals(blockedClientProto);
    }

    @PreDestroy
    public void destroy() {
        stopped = true;
        if (blockedClientConsumer != null) {
            blockedClientConsumer.unsubscribeAndClose();
            if (blockedClientConsumer.getConsumerGroupId() != null) {
                queueAdmin.deleteConsumerGroups(Collections.singleton(blockedClientConsumer.getConsumerGroupId()));
            }
        }
        ThingsBoardExecutors.shutdownAndAwaitTermination(consumerExecutor, "Blocked client consumer");
    }
}
