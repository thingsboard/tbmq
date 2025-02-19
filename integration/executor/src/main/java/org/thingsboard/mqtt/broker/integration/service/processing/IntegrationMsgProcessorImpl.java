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
package org.thingsboard.mqtt.broker.integration.service.processing;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.util.CallbackUtil;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.gen.integration.PublishIntegrationMsgProto;
import org.thingsboard.mqtt.broker.integration.api.TbPlatformIntegration;
import org.thingsboard.mqtt.broker.integration.service.data.IntegrationHolder;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.integration.IntegrationMsgQueueProvider;
import org.thingsboard.mqtt.broker.service.queue.IntegrationTopicService;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class IntegrationMsgProcessorImpl implements IntegrationMsgProcessor {

    private final ConcurrentMap<String, IntegrationHolder> integrations = new ConcurrentHashMap<>();

    private final IntegrationMsgQueueProvider integrationMsgQueueProvider;
    private final IntegrationTopicService integrationTopicService;

    private volatile boolean stopped = false;
    private ExecutorService integrationMsgsConsumerExecutor;
    private ScheduledExecutorService taskExecutor;

    @Value("${queue.integration-msg.poll-interval:100}")
    private long pollDuration;

    @PostConstruct
    public void init() {
        log.info("Initializing IE msg processor");
        integrationMsgsConsumerExecutor = ThingsBoardExecutors.initCachedExecutorService("ie-msg-consumers");
        taskExecutor = ThingsBoardExecutors.newSingleScheduledThreadPool("ie-msg-task");
    }

    @PreDestroy
    public void destroy() {
        log.info("Destroying IE msg processor");
        stopped = true;
        integrations.forEach((integrationId, integration) -> stopIntegrationCancelTask(integration));
        if (integrationMsgsConsumerExecutor != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(integrationMsgsConsumerExecutor, "IE msg consumers'");
        }
        if (taskExecutor != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(taskExecutor, "IE msg task'");
        }
    }

    @Override
    public void startProcessingIntegrationMessages(TbPlatformIntegration integration) {
        String integrationId = integration.getIntegrationId();

        String integrationTopic = integrationTopicService.createTopic(integrationId);
        log.debug("[{}] Starting ie messages processing.", integrationId);
        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishIntegrationMsgProto>> consumer = initConsumer(integrationId, integrationTopic);
        IntegrationHolder integrationHolder = new IntegrationHolder(integration);
        Future<?> future = integrationMsgsConsumerExecutor.submit(() -> {
            try {
                processMessages(consumer, integrationHolder);
            } finally {
                consumer.unsubscribeAndClose();
            }
        });
        integrationHolder.setFuture(future);
        integrations.put(integrationId, integrationHolder);
    }

    @Override
    public void stopProcessingPersistedMessages(String integrationId) {
        log.debug("[{}] Stopping ie messages processing.", integrationId);
        IntegrationHolder integrationHolder = integrations.remove(integrationId);
        if (integrationHolder == null) {
            log.warn("[{}] Cannot find integration for integrationId", integrationId);
        } else {
            try {
                stopIntegrationCancelTask(integrationHolder);
            } catch (Exception e) {
                log.warn("[{}] Exception stopping future for client.", integrationId, e);
            }
        }
    }

    @Override
    public void clearIntegrationMessages(String integrationId) {
        log.debug("[{}] Clearing consumer group and topic for IE", integrationId);
        taskExecutor.schedule(() -> {
            try {
                integrationTopicService.deleteTopic(integrationId, CallbackUtil.createCallback(
                        () -> {
                        }, throwable -> {
                        }));
            } catch (Exception e) {
                log.warn("[{}] Exception clearing consumer group and topic for IE", integrationId, e);
            }
        }, 10, TimeUnit.SECONDS);
    }

    private TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishIntegrationMsgProto>> initConsumer(String integrationId, String topic) {
        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishIntegrationMsgProto>> consumer = createConsumer(integrationId, topic);
        try {
            consumer.assignPartition(0);

            Optional<Long> committedOffset = consumer.getCommittedOffset(consumer.getTopic(), 0);
            if (committedOffset.isEmpty()) {
                long endOffset = consumer.getEndOffset(consumer.getTopic(), 0);
                consumer.commit(0, endOffset);
            }
            return consumer;
        } catch (Exception e) {
            log.error("[{}] Failed to init ie consumer", integrationId, e);
            consumer.unsubscribeAndClose();
            throw e;
        }
    }

    private TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishIntegrationMsgProto>> createConsumer(String integrationId, String topic) {
        return integrationMsgQueueProvider
                .getNewIeMsgConsumer(
                        topic,
                        integrationTopicService.getConsumerGroup(integrationId),
                        integrationId);
    }

    private void processMessages(TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishIntegrationMsgProto>> consumer,
                                 IntegrationHolder integrationHolder) {
        while (isProcessorActive(integrationHolder)) {
            try {
                List<TbProtoQueueMsg<PublishIntegrationMsgProto>> messages = consumer.poll(pollDuration);
                if (messages.isEmpty()) {
                    continue;
                }
                //todo: add reprocessing logic

                log.debug("[{}] Start sending the pack of messages {}", integrationHolder.getIntegrationId(), messages.size());
                for (TbProtoQueueMsg<PublishIntegrationMsgProto> message : messages) {
                    integrationHolder.getIntegration().process(message.getValue());
                }
                consumer.commitSync();
            } catch (Exception e) {
                if (isProcessorActive(integrationHolder)) {
                    log.warn("[{}] Failed to process messages from queue.", integrationHolder.getIntegrationId(), e);
                    try {
                        Thread.sleep(pollDuration);
                    } catch (InterruptedException e2) {
                        log.trace("Failed to wait until the server has capacity to handle new requests", e2);
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        log.info("[{}] IE messages consumer stopped.", integrationHolder.getIntegrationId());
    }

    private boolean isProcessorActive(IntegrationHolder integrationHolder) {
        return !stopped && !Thread.interrupted() && !integrationHolder.isStopped();
    }

    private void stopIntegrationCancelTask(IntegrationHolder integration) {
        integration.getFuture().cancel(false);
        integration.setStopped(true);
    }

}
