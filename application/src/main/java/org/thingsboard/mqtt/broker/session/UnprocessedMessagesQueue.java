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
package org.thingsboard.mqtt.broker.session;

import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.util.ReferenceCountUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
public class UnprocessedMessagesQueue {

    private final AtomicBoolean isProcessing = new AtomicBoolean(true);
    @Getter
    private final ConcurrentLinkedQueue<MqttMessage> unprocessedMessages = new ConcurrentLinkedQueue<>();

    public void process(Consumer<MqttMessage> processor) {
        while (!unprocessedMessages.isEmpty()) {
            MqttMessage msg = unprocessedMessages.poll();
            try {
                processor.accept(msg);
            } finally {
                ReferenceCountUtil.safeRelease(msg);
            }
        }
    }

    public boolean isProcessing() {
        return isProcessing.get();
    }

    public void finishProcessing() {
        isProcessing.getAndSet(false);
    }

    public void queueMessage(MqttMessage msg) {
        if (!isProcessing()) {
            throw new RuntimeException("Queue is not processing messages anymore");
        }
        unprocessedMessages.add(msg);
        if (!isProcessing()) {
            release();
        }
    }

    public void release() {
        finishProcessing();
        log.debug("Releasing {} queued messages.", unprocessedMessages.size());
        while (!unprocessedMessages.isEmpty()) {
            MqttMessage msg = unprocessedMessages.poll();
            ReferenceCountUtil.safeRelease(msg);
        }
    }
}
