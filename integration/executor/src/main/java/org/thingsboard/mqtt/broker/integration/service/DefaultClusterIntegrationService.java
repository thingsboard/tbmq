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
package org.thingsboard.mqtt.broker.integration.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.callback.TbCallback;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationLifecycleMsg;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationType;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.gen.integration.DownlinkIntegrationMsgProto;
import org.thingsboard.mqtt.broker.gen.integration.IntegrationValidationRequestProto;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.integration.HttpIntegrationDownlinkQueueFactory;
import org.thingsboard.mqtt.broker.queue.util.IntegrationProtoConverter;
import org.thingsboard.mqtt.broker.service.IntegrationManagerService;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;

@Slf4j
@Service
@RequiredArgsConstructor
@Order(value = 1)
public class DefaultClusterIntegrationService {

    private final ConcurrentMap<TopicPartition, Boolean> partitionRestorationMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<TopicPartition, ConcurrentMap<UUID, IntegrationLifecycleMsg>> integrationStateMap = new ConcurrentHashMap<>();

    private final HttpIntegrationDownlinkQueueFactory httpIntegrationDownlinkQueueFactory;
    private final ServiceInfoProvider serviceInfoProvider;
    private final IntegrationManagerService integrationManagerService;

    @Value("${queue.integration-downlink.poll-interval}")
    private long pollDuration;

    private volatile boolean stopped = false;
    private ExecutorService consumersExecutor;

    private TbQueueControlledOffsetConsumer<TbProtoQueueMsg<DownlinkIntegrationMsgProto>> httpIntegrationDownlinkConsumer;

    @PostConstruct
    public void init() {
        log.info("Initializing DefaultClusterIntegrationService");
        this.consumersExecutor = ThingsBoardExecutors.initCachedExecutorService("ie-downlink-service");
//        for (IntegrationType integrationType : serviceInfoProvider.getSupportedIntegrationTypes()) {
        for (IntegrationType integrationType : List.of(IntegrationType.HTTP)) {
            this.httpIntegrationDownlinkConsumer = httpIntegrationDownlinkQueueFactory.createConsumer(serviceInfoProvider.getServiceId());
            httpIntegrationDownlinkConsumer.subscribe(new TbConsumerRebalanceListener(httpIntegrationDownlinkConsumer, integrationType));
            launchConsumer(httpIntegrationDownlinkConsumer);
        }
    }

    @PreDestroy
    public void stop() {
        log.info("Destroying DefaultClusterIntegrationService");
        stopped = true;
        if (consumersExecutor != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(consumersExecutor, "IE downlink consumer");
        }
        if (httpIntegrationDownlinkConsumer != null) {
            httpIntegrationDownlinkConsumer.unsubscribeAndClose();
        }
    }

    private void launchConsumer(TbQueueControlledOffsetConsumer<TbProtoQueueMsg<DownlinkIntegrationMsgProto>> consumer) {
        consumersExecutor.execute(() -> {
            while (!stopped) {
                try {
                    List<TbProtoQueueMsg<DownlinkIntegrationMsgProto>> msgs = consumer.poll(pollDuration);
                    if (msgs.isEmpty()) {
                        continue;
                    }
                    //todo: add reprocessing logic

                    if (log.isDebugEnabled()) {
                        log.debug("Received pack of messages: {}", msgs);
                    } else {
                        log.info("Received pack with size: {}", msgs.size());
                    }

                    Map<Integer, Long> highestOffsets = null;

                    for (TbProtoQueueMsg<DownlinkIntegrationMsgProto> msg : msgs) {
                        log.debug("Got the next message {}", msg);

                        DownlinkIntegrationMsgProto downlinkIntegrationMsg = msg.getValue();
                        int partition = msg.getPartition();
                        long offset = msg.getOffset();

                        TopicPartition topicPartition = newTopicPartition(consumer.getTopic(), partition);
                        Boolean isRestorationMode = partitionRestorationMap.get(topicPartition);
                        if (isRestorationMode == null) {
                            log.debug("[{}] Partition was revoked", topicPartition);
                            removeAndStopIntegrations(topicPartition);
                            continue;
                        }

                        if (isRestorationMode) {

                            highestOffsets = initMapIfNull(highestOffsets);
                            highestOffsets.put(partition, offset);

                            if (downlinkIntegrationMsg.hasValidationRequestMsg()) {
                                log.debug("[{}] Skipping integration validation request during restore process", topicPartition);
                            } else if (downlinkIntegrationMsg.hasIntegrationMsg()) {
                                deserializeAndCacheIntegration(downlinkIntegrationMsg, topicPartition);
                            }
                        } else {
                            if (downlinkIntegrationMsg.hasIntegrationMsg()) {
                                IntegrationLifecycleMsg lifecycleMsg = deserializeAndCacheIntegration(downlinkIntegrationMsg, topicPartition);
                                handleIntegrationEvent(lifecycleMsg);
                            } else if (downlinkIntegrationMsg.hasValidationRequestMsg()) {
                                handleValidationRequest(downlinkIntegrationMsg.getValidationRequestMsg(), TbCallback.EMPTY);
                            }
                        }
                    }

                    if (!CollectionUtils.isEmpty(highestOffsets)) {
                        for (Map.Entry<Integer, Long> entry : highestOffsets.entrySet()) {
                            int partition = entry.getKey();
                            long offset = entry.getValue();
                            TopicPartition topicPartition = newTopicPartition(consumer.getTopic(), partition);

                            Boolean isRestorationMode = partitionRestorationMap.get(topicPartition);
                            if (Boolean.TRUE.equals(isRestorationMode) && isEndOfPartition(consumer, partition, offset)) {
                                log.info("Partition {} switched to real-time processing mode.", partition);
                                partitionRestorationMap.put(topicPartition, false);

                                // Process restored integrations for this partition
                                Map<UUID, IntegrationLifecycleMsg> uuidIntegrationLifecycleMsgMap = integrationStateMap.get(topicPartition);
                                if (!CollectionUtils.isEmpty(uuidIntegrationLifecycleMsgMap)) {
                                    uuidIntegrationLifecycleMsgMap.values().forEach(this::handleIntegrationEvent);
                                }
                            }
                        }
                    }

                    consumer.commitSync();
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
            log.info("Integration Executor downlink queue consumer has been stopped");
        });
    }

    private IntegrationLifecycleMsg deserializeAndCacheIntegration(DownlinkIntegrationMsgProto downlinkIntegrationMsg, TopicPartition topicPartition) {
        IntegrationLifecycleMsg lifecycleMsg = fromProto(downlinkIntegrationMsg);

        Map<UUID, IntegrationLifecycleMsg> integrationsMap = integrationStateMap.computeIfAbsent(topicPartition, __ -> new ConcurrentHashMap<>());
        integrationsMap.put(lifecycleMsg.getIntegrationId(), lifecycleMsg);
        return lifecycleMsg;
    }

    private boolean isEndOfPartition(TbQueueControlledOffsetConsumer<TbProtoQueueMsg<DownlinkIntegrationMsgProto>> consumer, int partition, long offset) {
        try {
            long endOffset = consumer.getEndOffset(consumer.getTopic(), partition);
            return offset >= endOffset - 1;
        } catch (Exception e) {
            log.error("[{}][{}] Failed to identify current position and end offset.", consumer.getTopic(), partition, e);
            throw e;
        }
    }

    private void handleIntegrationEvent(IntegrationLifecycleMsg integrationLifecycleMsg) {
        log.trace("handleIntegrationEvent: {}", integrationLifecycleMsg);
        integrationManagerService.handleIntegrationLifecycleMsg(integrationLifecycleMsg);
    }

    private void handleIntegrationStopEvent(IntegrationLifecycleMsg integrationLifecycleMsg) {
        log.trace("handleIntegrationStopEvent: {}", integrationLifecycleMsg);
        integrationManagerService.handleStopIntegrationLifecycleMsg(integrationLifecycleMsg);
    }

    private void handleValidationRequest(IntegrationValidationRequestProto msg, TbCallback callback) {
        log.trace("Received downlink request: {}", msg);
        integrationManagerService.handleValidationRequest(msg, callback);
    }

    private TopicPartition newTopicPartition(String topic, int partition) {
        return new TopicPartition(topic, partition);
    }

    private IntegrationLifecycleMsg fromProto(DownlinkIntegrationMsgProto downlinkIntegrationMsg) {
        return IntegrationProtoConverter.fromProto(downlinkIntegrationMsg.getIntegrationMsg());
    }

    private Map<Integer, Long> initMapIfNull(Map<Integer, Long> highestOffsets) {
        return highestOffsets == null ? new HashMap<>() : highestOffsets;
    }


    @Data
    private class TbConsumerRebalanceListener implements ConsumerRebalanceListener {

        private final TbQueueControlledOffsetConsumer<TbProtoQueueMsg<DownlinkIntegrationMsgProto>> consumer;
        private final IntegrationType integrationType;

        @Override
        public void onPartitionsRevoked(Collection<TopicPartition> topicPartitions) {
            log.info("[{}] Partitions revoked: {}", integrationType, topicPartitions);
            for (var topicPartition : topicPartitions) {
                partitionRestorationMap.remove(topicPartition);
                removeAndStopIntegrations(topicPartition);
            }
            if (stopped) {
                integrationManagerService.proceedGracefulShutdown();
            }
        }

        @Override
        public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
            log.info("[{}] Partitions assigned: {}", integrationType, partitions);
            consumer.seekToTheBeginning(partitions);

            Map<TopicPartition, Long> endOffsetMap = consumer.getEndOffset(partitions);

            for (TopicPartition partition : partitions) {
                if (endOffsetMap.get(partition) == 0) {
                    log.info("Partition {} is set to real-time processing mode.", partition);
                    partitionRestorationMap.put(partition, false);
                } else {
                    log.info("Partition {} is set to restoration processing mode.", partition);
                    partitionRestorationMap.put(partition, true);
                }
            }
        }
    }

    private void removeAndStopIntegrations(TopicPartition topicPartition) {
        Map<UUID, IntegrationLifecycleMsg> removed = integrationStateMap.remove(topicPartition);
        if (!CollectionUtils.isEmpty(removed)) {
            removed.values().forEach(DefaultClusterIntegrationService.this::handleIntegrationStopEvent);
        }
    }
}
