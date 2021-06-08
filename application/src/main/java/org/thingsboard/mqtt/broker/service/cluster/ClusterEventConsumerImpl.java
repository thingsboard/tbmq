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
package org.thingsboard.mqtt.broker.service.cluster;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ClusterEventQueueFactory;
import org.thingsboard.mqtt.broker.service.cluster.model.ClusterEvent;

import javax.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class ClusterEventConsumerImpl implements ClusterEventConsumer {
    private final ExecutorService consumersExecutor = Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName("cluster-event-consumer"));
    private volatile boolean stopped = false;

    @Value("${queue.cluster-event.poll-interval}")
    private long pollDuration;

    private final TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.ClusterEventProto>> consumer;
    private final ClusterEventProcessor clusterEventProcessor;
    private final ClusterEventConverter clusterEventConverter;

    public ClusterEventConsumerImpl(ClusterEventQueueFactory clusterEventQueueFactory, ServiceInfoProvider serviceInfoProvider, ClusterEventProcessor clusterEventProcessor, ClusterEventConverter clusterEventConverter) {
        this.consumer = clusterEventQueueFactory.createEventConsumer(serviceInfoProvider.getServiceId());
        this.clusterEventProcessor = clusterEventProcessor;
        this.clusterEventConverter = clusterEventConverter;
    }

    @Override
    public void startConsuming() {
        log.debug("Start consuming Cluster events.");
        consumer.subscribe();
        consumersExecutor.submit(this::processClusterEvents);
    }

    private void processClusterEvents() {
        while (!stopped) {
            try {
                List<TbProtoQueueMsg<QueueProtos.ClusterEventProto>> msgs = consumer.poll(pollDuration);
                if (msgs.isEmpty()) {
                    continue;
                }
                for (TbProtoQueueMsg<QueueProtos.ClusterEventProto> msg : msgs) {
                    try {
                        ClusterEvent clusterEvent = clusterEventConverter.convert(msg.getValue());
                        clusterEventProcessor.processClusterEvent(clusterEvent);
                    } catch (Exception e) {
                        log.warn("Failed to process msg {}. Exception - {}, reason - {}", msg.getValue(), e.getClass().getSimpleName(), e.getMessage());
                    }
                }
                consumer.commit();
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
        log.info("Cluster Event Consumer stopped.");
    }


    @PreDestroy
    public void destroy() {
        stopped = true;
        consumer.unsubscribeAndClose();
        consumersExecutor.shutdownNow();
    }
}
