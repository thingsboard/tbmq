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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application.topic;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ApplicationRemovedEventQueueFactory;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;

import javax.annotation.PreDestroy;
import java.util.List;

@Slf4j
@Service
public class ApplicationRemovedEventProcessorImpl implements ApplicationRemovedEventProcessor {

    private static final int MAX_EMPTY_EVENTS = 5;
    private volatile boolean stopped = false;

    @Value("${queue.application-removed-event.poll-interval}")
    private long pollDuration;

    private final TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.ApplicationRemovedEventProto>> consumer;
    private final ClientSessionEventService clientSessionEventService;

    public ApplicationRemovedEventProcessorImpl(ClientSessionEventService clientSessionEventService, ApplicationRemovedEventQueueFactory applicationRemovedEventQueueFactory, ServiceInfoProvider serviceInfoProvider) {
        this.consumer = applicationRemovedEventQueueFactory.createEventConsumer(serviceInfoProvider.getServiceId());
        consumer.subscribe();
        this.clientSessionEventService = clientSessionEventService;
    }

    @Scheduled(cron = "${queue.application-removed-event.processing.cron}", zone = "${queue.application-removed-event.processing.zone}")
    private void scheduleApplicationRemovedEventProcessing() {
        try {
            processEvents();
        } catch (Exception e) {
            log.error("Failed to process APPLICATION removed events.", e);
        }
    }

    @Override
    public void processEvents() {
        if (log.isDebugEnabled()) {
            log.debug("Start processing APPLICATION removed events.");
        }
        int currentEmptyEvents = 0;
        while (!stopped) {
            List<TbProtoQueueMsg<QueueProtos.ApplicationRemovedEventProto>> msgs = consumer.poll(pollDuration);
            if (msgs.isEmpty()) {
                if (++currentEmptyEvents > MAX_EMPTY_EVENTS) {
                    break;
                } else {
                    continue;
                }
            }
            currentEmptyEvents = 0;
            for (TbProtoQueueMsg<QueueProtos.ApplicationRemovedEventProto> msg : msgs) {
                if (log.isDebugEnabled()) {
                    log.debug("[{}] Requesting topic removal", msg.getValue().getClientId());
                }
                clientSessionEventService.requestApplicationTopicRemoved(msg.getValue().getClientId());
            }
            consumer.commitSync();
        }
        if (log.isDebugEnabled()) {
            log.debug("Finished processing APPLICATION removed events.");
        }
    }

    @PreDestroy
    public void destroy() {
        stopped = true;
        consumer.unsubscribeAndClose();
    }
}
