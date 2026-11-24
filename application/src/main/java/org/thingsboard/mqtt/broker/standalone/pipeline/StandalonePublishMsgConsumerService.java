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

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.service.processing.MsgDispatcherService;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgCallback;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgConsumerService;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgWithId;
import org.thingsboard.mqtt.broker.standalone.pipeline.StandalonePublishMsgQueue.PendingPublish;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stage 1 of the publish pipeline: the workers of the {@link StandalonePublishMsgQueue} hand each publish to the
 * dispatcher, which delivers it to the subscribers. The publish's callback completes once every copy was handed off.
 */
@Slf4j
@Service
@StandaloneComponent
@RequiredArgsConstructor
public class StandalonePublishMsgConsumerService implements PublishMsgConsumerService {

    private static final String CONSUMER_ID_PREFIX = "dispatch-";

    private final StandalonePublishMsgQueue queue;
    private final MsgDispatcherService msgDispatcherService;
    private final StatsFactory statsFactory;

    // the id of a dispatched publish: the dispatcher reads only the publish and its headers, so an id unique within this
    // process is enough. UUID.randomUUID() would take a lock on a shared SecureRandom for every publish
    private final long publishIdHighBits = ThreadLocalRandom.current().nextLong();
    private final AtomicLong publishIdSequence = new AtomicLong();

    @Override
    public void startConsuming() {
        queue.start(partition -> {
            StandaloneDispatchStats stats = new StandaloneDispatchStats(CONSUMER_ID_PREFIX + partition, statsFactory);
            return batch -> dispatch(batch, stats);
        });
    }

    // this bean depends on the dispatcher, so Spring destroys it first: no worker dispatches into destroyed beans
    @PreDestroy
    public void destroy() {
        queue.destroy();
    }

    private void dispatch(List<PendingPublish> batch, StandaloneDispatchStats stats) {
        for (PendingPublish pending : batch) {
            TbProtoQueueMsg<PublishMsgProto> msg = pending.msg();
            CountedPublishCallback callback = new CountedPublishCallback(pending.callback(), stats);
            stats.onDispatched();
            try {
                UUID id = new UUID(publishIdHighBits, publishIdSequence.getAndIncrement());
                long start = System.nanoTime();
                msgDispatcherService.processPublishMsg(new PublishMsgWithId(id, msg.getValue(), msg.getHeaders()), callback);
                stats.logMsgProcessingTime(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            } catch (Throwable t) {
                // an Error too: the publish is failed, and the worker lives on to serve its partition
                log.error("[{}] Failed to dispatch the publish to topic {}", msg.getValue().getClientId(), msg.getKey(), t);
                callback.onFailure(t);
            }
        }
    }

    /**
     * Counts the outcome of a publish once, whichever of the dispatcher and the worker resolves it first.
     */
    private static final class CountedPublishCallback implements PublishMsgCallback {

        private final PublishMsgCallback delegate;
        private final StandaloneDispatchStats stats;
        private final AtomicBoolean resolved = new AtomicBoolean();

        private CountedPublishCallback(PublishMsgCallback delegate, StandaloneDispatchStats stats) {
            this.delegate = delegate;
            this.stats = stats;
        }

        @Override
        public void onSuccess() {
            if (resolved.compareAndSet(false, true)) {
                stats.onSuccess();
            }
            delegate.onSuccess();
        }

        @Override
        public void onFailure(Throwable t) {
            if (resolved.compareAndSet(false, true)) {
                stats.onFailure();
            }
            delegate.onFailure(t);
        }

    }

}
