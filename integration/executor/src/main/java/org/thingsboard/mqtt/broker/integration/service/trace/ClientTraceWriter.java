/**
 * Copyright © 2016-2026 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.integration.service.trace;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.gen.queue.ClientTraceEventProto;
import org.thingsboard.mqtt.broker.queue.TbQueueConsumer;
import org.thingsboard.mqtt.broker.queue.TbmqIntegrationExecutorComponent;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ClientTraceQueueFactory;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@TbmqIntegrationExecutorComponent
@ConditionalOnProperty(prefix = "client-trace.writer", name = "enabled", havingValue = "true")
public class ClientTraceWriter {

    private final ClientTraceQueueFactory queueFactory;
    private final ServiceInfoProvider serviceInfoProvider;
    private final ClickHouseClientTraceWriter clickHouseWriter;

    @Value("${client-trace.writer.poll-interval:500}")
    private long pollInterval;
    @Value("${client-trace.writer.failure-backoff:1000}")
    private long failureBackoff;

    private volatile boolean stopped;
    private TbQueueConsumer<TbProtoQueueMsg<ClientTraceEventProto>> consumer;
    private Thread thread;

    @PostConstruct
    void start() {
        consumer = queueFactory.createConsumer(serviceInfoProvider.getServiceId());
        consumer.subscribe();
        thread = Thread.ofPlatform().name("client-trace-writer").daemon().start(this::consume);
    }

    private void consume() {
        while (!stopped) {
            try {
                List<TbProtoQueueMsg<ClientTraceEventProto>> messages = consumer.poll(pollInterval);
                if (messages.isEmpty()) {
                    continue;
                }
                clickHouseWriter.insert(messages.stream().map(TbProtoQueueMsg::getValue).toList());
                consumer.commitSync();
            } catch (Exception e) {
                if (!stopped) {
                    log.error("Failed to persist client trace events; Kafka offsets were not committed", e);
                    pauseAfterFailure();
                }
            }
        }
    }

    private void pauseAfterFailure() {
        try {
            Thread.sleep(failureBackoff);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    void stop() {
        stopped = true;
        if (thread != null) {
            thread.interrupt();
        }
        if (consumer != null) {
            consumer.unsubscribeAndClose();
        }
    }
}
