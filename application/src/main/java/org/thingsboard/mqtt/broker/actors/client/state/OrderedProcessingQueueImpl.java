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
package org.thingsboard.mqtt.broker.actors.client.state;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.thingsboard.mqtt.broker.exception.FullMsgQueueException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@RequiredArgsConstructor
@Getter
public class OrderedProcessingQueueImpl implements OrderedProcessingQueue {

    private final Queue<MqttMsgWrapper> receivedMsgQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queueSize = new AtomicInteger();
    private final Lock lock = new ReentrantLock();

    private final int maxAwaitingQueueSize;

    @Override
    public MqttMsgWrapper addMsgId(int msgId) throws FullMsgQueueException {
        if (queueSize.get() >= maxAwaitingQueueSize) {
            throw new FullMsgQueueException("In-flight queue size is already " + queueSize.get());
        }

        MqttMsgWrapper msgStatus = MqttMsgWrapper.newInstance(msgId);
        receivedMsgQueue.add(msgStatus);
        queueSize.incrementAndGet();
        return msgStatus;
    }

    @Override
    public List<Integer> ack(MqttMsgWrapper msgWrapper) {
        if (msgWrapper == null) {
            return Collections.emptyList();
        }
        msgWrapper.setAck(true);

        return tryProcess();
    }

    private List<Integer> tryProcess() {
        List<Integer> list = new ArrayList<>();

        MqttMsgWrapper head;
        while ((head = receivedMsgQueue.peek()) != null && head.isAck()) {
            if (!lock.tryLock()) {
                return Collections.emptyList();
            }
            try {
                while ((head = receivedMsgQueue.peek()) != null && head.isAck()) {
                    final MqttMsgWrapper polled = receivedMsgQueue.poll();
                    queueSize.decrementAndGet();
                    if (head != polled) {  // double-check
                        throw new RuntimeException("Polled head [" + polled + "] is not the same as peeked head [" + head + "]. Msg order is broken");
                    }
                    list.add(polled.getMsgId());
                }
            } finally {
                lock.unlock();
            }
        }
        return list;
    }

}
