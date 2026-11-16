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
package org.thingsboard.mqtt.broker.standalone.pipeline;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgCallback;

import java.util.List;
import java.util.function.Consumer;

/**
 * The queue of the dispatch stage, partitioned by topic: the {@link StandalonePublishMsgQueuePublisher} puts the
 * publishes in, and the {@link StandalonePublishMsgConsumerService} starts its workers and dispatches them. The queue
 * is a bean of its own because the dispatcher publishes through the publisher, and the consumer needs the dispatcher.
 * <p>
 * The worker queues are unbounded: the {@link PipelineBudget} bounds this stage, so a submit never blocks the caller.
 */
@Component
@StandaloneComponent
@RequiredArgsConstructor
public class StandalonePublishMsgQueue {

    static final String DISPATCH_QUEUED_GAUGE = "standaloneDispatchQueued";
    private static final String THREAD_NAME_PREFIX = "standalone-dispatch";
    private static final int UNBOUNDED = 0;
    private static final int MAX_BATCH = 256;

    private final PipelineProperties properties;
    private final StatsFactory statsFactory;

    private PartitionedWorkers<PendingPublish> workers;

    @PostConstruct
    public void init() {
        workers = new PartitionedWorkers<>(THREAD_NAME_PREFIX, properties.effectiveDispatchPartitions(), UNBOUNDED, MAX_BATCH);
        statsFactory.createGauge(DISPATCH_QUEUED_GAUGE, workers, PartitionedWorkers::queued);
    }

    void start(Consumer<List<PendingPublish>> dispatcher) {
        workers.start(dispatcher);
    }

    public PartitionedWorkers<?> workers() {
        return workers;
    }

    /**
     * Queues the publish for the worker of its key, the MQTT topic. When it cannot be queued, the callback fails
     * instead: with {@link org.thingsboard.mqtt.broker.exception.PipelineClosedException} once the workers no longer
     * accept items on shutdown, or with the {@link InterruptedException} of an interrupted caller.
     */
    void submit(TbProtoQueueMsg<PublishMsgProto> msg, PublishMsgCallback callback) {
        try {
            workers.submit(msg.getKey(), new PendingPublish(msg, callback));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            callback.onFailure(e);
        } catch (RuntimeException e) {
            callback.onFailure(e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (workers != null) {
            workers.stop();
        }
    }

    record PendingPublish(TbProtoQueueMsg<PublishMsgProto> msg, PublishMsgCallback callback) {
    }

}
