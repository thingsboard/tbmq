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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application.topic;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.gen.queue.ApplicationRemovedEventProto;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ApplicationRemovedEventQueueFactory;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationRemovedEventProcessorImpl implements ApplicationRemovedEventProcessor {

    private static final int MAX_EMPTY_EVENTS = 5;

    private final ClientSessionEventService clientSessionEventService;
    private final ApplicationRemovedEventQueueFactory applicationRemovedEventQueueFactory;
    private final ServiceInfoProvider serviceInfoProvider;

    @Value("${queue.application-removed-event.poll-interval:100}")
    private long pollDuration;

    private TbQueueControlledOffsetConsumer<TbProtoQueueMsg<ApplicationRemovedEventProto>> consumer;
    private volatile boolean stopped = false;

    @PostConstruct
    public void init() {
        consumer = applicationRemovedEventQueueFactory.createEventConsumer(serviceInfoProvider.getServiceId());
        consumer.subscribe();
    }

    @PreDestroy
    public void destroy() {
        stopped = true;
        if (consumer != null) {
            consumer.unsubscribeAndClose();
        }
    }

    @Scheduled(cron = "${queue.application-removed-event.processing.cron}", zone = "${queue.application-removed-event.processing.zone}")
    private void scheduleApplicationRemovedEventProcessing() {
        try {
            processEvents();
        } catch (Exception e) {
            log.error("Failed to process APPLICATION removed events", e);
        }
    }

    @Override
    public void processEvents() {
        log.debug("Start processing APPLICATION removed events");
        int currentEmptyEvents = 0;
        while (!stopped) {
            List<TbProtoQueueMsg<ApplicationRemovedEventProto>> msgs = consumer.poll(pollDuration);
            if (msgs.isEmpty()) {
                if (++currentEmptyEvents > MAX_EMPTY_EVENTS) {
                    break;
                } else {
                    continue;
                }
            }
            currentEmptyEvents = 0;
            for (TbProtoQueueMsg<ApplicationRemovedEventProto> msg : msgs) {
                log.debug("[{}] Requesting topic removal", msg.getValue().getClientId());
                clientSessionEventService.requestApplicationTopicRemoved(msg.getValue().getClientId());
            }
            consumer.commitSync();
        }
        log.debug("Finished processing APPLICATION removed events");
    }

}
