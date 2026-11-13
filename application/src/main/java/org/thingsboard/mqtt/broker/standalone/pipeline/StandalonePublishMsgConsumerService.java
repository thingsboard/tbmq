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
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.service.processing.MsgDispatcherService;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgCallback;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgConsumerService;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgWithId;

import java.util.List;
import java.util.UUID;

/**
 * Stage 1 of the publish pipeline: the dispatch workers, partitioned by topic, hand each publish to the dispatcher,
 * which delivers it to the subscribers. The publish's callback completes once every copy was handed off.
 * <p>
 * The worker queues are unbounded: the {@link PipelineBudget} bounds this stage, so a submit never blocks the caller.
 */
@Slf4j
@Service
@StandaloneComponent
@RequiredArgsConstructor
public class StandalonePublishMsgConsumerService implements PublishMsgConsumerService {

    static final String DISPATCH_QUEUED_GAUGE = "standaloneDispatchQueued";
    private static final String THREAD_NAME_PREFIX = "standalone-dispatch";
    private static final int UNBOUNDED = 0;
    private static final int MAX_BATCH = 256;

    private final PipelineProperties properties;
    private final StatsFactory statsFactory;
    // the dispatcher publishes through this stage, so it is resolved on first use
    private final @Lazy MsgDispatcherService msgDispatcherService;

    private PartitionedWorkers<PendingPublish> workers;

    @PostConstruct
    public void init() {
        workers = new PartitionedWorkers<>(THREAD_NAME_PREFIX, properties.effectiveDispatchPartitions(), UNBOUNDED, MAX_BATCH, this::dispatch);
        statsFactory.createGauge(DISPATCH_QUEUED_GAUGE, workers, PartitionedWorkers::queued);
    }

    @Override
    public void startConsuming() {
        workers.start();
    }

    public PartitionedWorkers<?> workers() {
        return workers;
    }

    /**
     * Queues the publish for the worker of its key, the MQTT topic. When it cannot be queued, because the workers no
     * longer accept items or the caller is interrupted, the callback fails instead.
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

    private void dispatch(List<PendingPublish> batch) {
        for (PendingPublish pending : batch) {
            TbProtoQueueMsg<PublishMsgProto> msg = pending.msg();
            try {
                msgDispatcherService.processPublishMsg(new PublishMsgWithId(UUID.randomUUID(), msg.getValue(), msg.getHeaders()), pending.callback());
            } catch (Exception e) {
                log.error("[{}] Failed to dispatch the publish to topic {}", msg.getValue().getClientId(), msg.getKey(), e);
                pending.callback().onFailure(e);
            }
        }
    }

    @PreDestroy
    public void destroy() {
        if (workers != null) {
            workers.stop();
        }
    }

    private record PendingPublish(TbProtoQueueMsg<PublishMsgProto> msg, PublishMsgCallback callback) {
    }

}
