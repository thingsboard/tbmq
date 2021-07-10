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
package org.thingsboard.mqtt.broker.service.processing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.PublishMsgQueueFactory;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublishMsgQueueServiceImpl implements PublishMsgQueueService {
    private final ExecutorService publishExecutor = Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName("incoming-publish-thread"));
    private final BlockingQueue<PublishMsgWithCallback> pendingMessagesQueue = new LinkedBlockingQueue<>();

    @Value("${queue.publish-msg.publisher-thread-max-delay}")
    private long maxDelay;

    private final PublishMsgQueueFactory publishMsgQueueFactory;
    private final StatsManager statsManager;

    private TbQueueProducer<TbProtoQueueMsg<QueueProtos.PublishMsgProto>> publishMsgProducer;

    @PostConstruct
    public void init() {
        this.publishMsgProducer = publishMsgQueueFactory.createProducer();
        statsManager.registerPendingPublishMessages(pendingMessagesQueue);
        publishExecutor.execute(this::publishMessages);
    }


    @Override
    public void sendMsg(QueueProtos.PublishMsgProto msgProto, TbQueueCallback callback) {
        pendingMessagesQueue.add(new PublishMsgWithCallback(msgProto, callback));
    }

    private void publishMessages() {
        while (!Thread.interrupted()) {
            PublishMsgWithCallback publishMsgWithCallback;
            try {
                publishMsgWithCallback = pendingMessagesQueue.poll(maxDelay, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                log.info("Queue polling was interrupted.");
                break;
            }
            if (publishMsgWithCallback == null) {
                continue;
            }
            TbQueueCallback callback = publishMsgWithCallback.getCallback();
            QueueProtos.PublishMsgProto msgProto = publishMsgWithCallback.getMsgProto();
            try {
                publishMsgProducer.send(new TbProtoQueueMsg<>(msgProto.getTopicName(), msgProto), callback);
            } catch (Exception e) {
                log.error("Failed to send publish msg to the queue", e);
                callback.onFailure(e);
            }
        }
    }


    @PreDestroy
    public void destroy() {
        publishExecutor.shutdownNow();
        publishMsgProducer.stop();
    }


    @Getter
    @RequiredArgsConstructor
    private static class PublishMsgWithCallback {
        private final QueueProtos.PublishMsgProto msgProto;
        private final TbQueueCallback callback;
    }
}
