/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.queue.publish;

import com.google.protobuf.GeneratedMessageV3;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.stats.ProducerStatsManager;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
public class TbPublishBlockingQueue<PROTO extends GeneratedMessageV3> implements TbPublishQueue<PROTO> {

    private final BlockingQueue<QueuedPacket<PROTO>> queue = new LinkedBlockingQueue<>();

    private final TbQueueProducer<TbProtoQueueMsg<PROTO>> producer;
    private final String queueName;
    private final long maxDelay;
    private final ProducerStatsManager statsManager;
    private final Integer partition;

    private ExecutorService executor;

    @Builder
    public TbPublishBlockingQueue(TbQueueProducer<TbProtoQueueMsg<PROTO>> producer, String queueName, long maxDelay,
                                  ProducerStatsManager statsManager, Integer partition) {
        this.producer = producer;
        this.queueName = queueName;
        this.maxDelay = maxDelay;
        this.statsManager = statsManager;
        this.partition = partition;
    }

    @Override
    public void init() {
        statsManager.registerProducerQueue(queueName, queue);
        this.executor = Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName("publish-queue-" + queueName.toLowerCase()));
        executor.execute(() -> processElementsQueue(queueName, partition));
    }

    private void processElementsQueue(String queueName, Integer partition) {
        while (!Thread.interrupted()) {
            try {
                QueuedPacket<PROTO> queuedElement = queue.poll(maxDelay, TimeUnit.MILLISECONDS);
                if (queuedElement == null) {
                    continue;
                }
                TbQueueCallback callback = queuedElement.getCallback();
                TbProtoQueueMsg<PROTO> queueMsg = queuedElement.getQueueMsg();
                String topic = queuedElement.getTopic();
                try {
                    if (topic != null) {
                        producer.send(topic, partition, queueMsg, callback);
                    } else {
                        producer.send(queueMsg, callback);
                    }
                } catch (Exception e) {
                    log.error("[{}] Failed to send msg to the queue", queueName, e);
                    callback.onFailure(e);
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    log.info("[{}] Queue polling was interrupted.", queueName);
                    break;
                } else {
                    log.error("[{}] Failed to process msg.", queueName, e);
                }
            }
        }
    }

    @Override
    public void add(TbProtoQueueMsg<PROTO> queueMsg, TbQueueCallback callback) {
        add(queueMsg, callback, null);
    }

    @Override
    public void add(TbProtoQueueMsg<PROTO> queueMsg, TbQueueCallback callback, String topic) {
        queue.add(new QueuedPacket<>(queueMsg, callback, topic));
    }

    @Override
    public void destroy() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Getter
    private static class QueuedPacket<PROTO extends GeneratedMessageV3> {
        private final TbProtoQueueMsg<PROTO> queueMsg;
        private final TbQueueCallback callback;
        private final String topic;

        public QueuedPacket(TbProtoQueueMsg<PROTO> queueMsg, TbQueueCallback callback, String topic) {
            this.queueMsg = queueMsg;
            this.callback = callback;
            this.topic = topic;
        }
    }
}
