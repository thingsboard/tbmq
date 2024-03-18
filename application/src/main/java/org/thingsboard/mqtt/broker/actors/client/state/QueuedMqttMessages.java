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
package org.thingsboard.mqtt.broker.actors.client.state;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.QueueableMqttMsg;
import org.thingsboard.mqtt.broker.exception.FullMsgQueueException;

import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Consumer;

/**
 * not thread safe
 */
@Slf4j
@RequiredArgsConstructor
public class QueuedMqttMessages {

    private final Queue<QueueableMqttMsg> queuedMessages = new LinkedList<>();

    private final int maxQueueSize;

    public void process(Consumer<QueueableMqttMsg> processor) {
        while (!queuedMessages.isEmpty()) {
            QueueableMqttMsg msg = queuedMessages.poll();
            processor.accept(msg);
        }
    }

    public void add(QueueableMqttMsg msg) throws FullMsgQueueException {
        if (queuedMessages.size() >= maxQueueSize) {
            throw new FullMsgQueueException("Current queue size is " + queuedMessages.size());
        } else {
            queuedMessages.add(msg);
        }
    }

    public void clear() {
        queuedMessages.forEach(QueueableMqttMsg::release);
        queuedMessages.clear();
    }

}
