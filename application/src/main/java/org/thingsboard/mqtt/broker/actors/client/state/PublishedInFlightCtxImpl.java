/**
 * Copyright © 2016-2025 The Thingsboard Authors
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

import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.util.ReferenceCountUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.mqtt.MqttPubMsgWithCreatedTime;
import org.thingsboard.mqtt.broker.service.mqtt.flow.control.FlowControlService;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Data
public class PublishedInFlightCtxImpl implements PublishedInFlightCtx {

    private final Queue<Integer> publishedInFlightMsgQueue = new ConcurrentLinkedQueue<>();
    private final Queue<Integer> receivedAckMsgInWrongOrderQueue = new ConcurrentLinkedQueue<>();
    private final Queue<MqttPubMsgWithCreatedTime> delayedMsgQueue = new ConcurrentLinkedQueue<>();

    private final AtomicInteger publishedInFlightMsgCounter = new AtomicInteger(0);
    private final AtomicInteger delayedMsgCounter = new AtomicInteger(0);

    private final Lock lock = new ReentrantLock();

    private final FlowControlService flowControlService;
    private final ClientSessionCtx clientSessionCtx;
    private final String clientId;
    private final int clientReceiveMax;
    private final int delayedMsgQueueMaxSize;

    public PublishedInFlightCtxImpl(FlowControlService flowControlService, ClientSessionCtx clientSessionCtx) {
        this(flowControlService, clientSessionCtx, BrokerConstants.DEFAULT_RECEIVE_MAXIMUM, BrokerConstants.DELAYED_MSG_QUEUE_MAX_SIZE);
    }

    public PublishedInFlightCtxImpl(FlowControlService flowControlService, ClientSessionCtx clientSessionCtx, int clientReceiveMax, int delayedMsgQueueMaxSize) {
        this.flowControlService = flowControlService;
        this.clientSessionCtx = clientSessionCtx;
        this.clientId = clientSessionCtx.getClientId();
        this.clientReceiveMax = clientReceiveMax;
        this.delayedMsgQueueMaxSize = delayedMsgQueueMaxSize;
    }

    @Override
    public boolean addInFlightMsg(MqttPublishMessage mqttPubMsg) {
        if (atMostOnce(mqttPubMsg)) {
            return true;
        }
        lock.lock();
        try {
            int delayedMsgQueueSize = delayedMsgQueueSize();
            if (delayedMsgQueueSize == 0) {
                if (publishedInFlightMsgQueueSize() >= clientReceiveMax) {
                    if (log.isDebugEnabled()) {
                        log.debug("[{}][{}] Max in-flight messages reached! Adding msg to empty delay queue [{}]", clientId, clientReceiveMax, delayedMsgQueueSize);
                    }
                    addDelayedMsg(mqttPubMsg);
                    flowControlService.addToMap(clientId, this);
                    return false;
                }
                return addPublishedInFlightMsg(mqttPubMsg);
            } else {
                if (delayedMsgQueueSize >= delayedMsgQueueMaxSize) {
                    log.error("[{}] Message is skipped! Max in-flight messages reached [{}] and delay queue is full [{}]!",
                            clientId, clientReceiveMax, delayedMsgQueueMaxSize);
                    ReferenceCountUtil.release(mqttPubMsg);
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("[{}][{}] Max in-flight messages reached! Adding msg to delay queue [{}]", clientId, clientReceiveMax, delayedMsgQueueSize);
                    }
                    addDelayedMsg(mqttPubMsg);
                }
                return false;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void ackInFlightMsg(int msgId) {
        lock.lock();
        try {
            Integer publishedInFlightHead = publishedInFlightMsgQueue.peek();
            if (publishedInFlightHead != null) {
                if (publishedInFlightHead == msgId) {
                    removePublishedInFlightMsg();
                    Integer wrongOrderHead = receivedAckMsgInWrongOrderQueue.peek();
                    if (wrongOrderHead == null) {
                        return;
                    }

                    while (true) {
                        Integer publishedInFlightNextHead = publishedInFlightMsgQueue.peek();
                        if (publishedInFlightNextHead == null) {
                            if (log.isDebugEnabled()) {
                                log.debug("[{}] No more in-flight messages waiting for ack! Clearing received ack queue", clientId);
                            }
                            receivedAckMsgInWrongOrderQueue.clear();
                            break;
                        } else {
                            if (receivedAckMsgInWrongOrderQueue.contains(publishedInFlightNextHead)) {
                                receivedAckMsgInWrongOrderQueue.remove(publishedInFlightNextHead);
                                removePublishedInFlightMsg();
                            } else {
                                break;
                            }
                        }
                    }
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("[{}] Received ack [{}] in the wrong order. Head - [{}]", clientId, msgId, publishedInFlightHead);
                    }
                    receivedAckMsgInWrongOrderQueue.add(msgId);
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("[{}] In-flight msg queue is empty. Received ack [{}] for published msg that was sent outside of the current network connection", clientId, msgId);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean processMsg(long ttlMs) {
        lock.lock();
        try {
            if (!allowedToSendMsg()) {
                if (log.isDebugEnabled()) {
                    log.debug("[{}] Still reaching clientReceiveMax... Waiting for more ack messages", clientId);
                }
                return false;
            }

            while (true) {
                MqttPubMsgWithCreatedTime head = delayedMsgQueue.poll();
                if (head == null) {
                    if (log.isDebugEnabled()) {
                        log.debug("[{}] Delayed queue is empty!", clientId);
                    }
                    flowControlService.removeFromMap(clientId);
                    return false;
                }
                delayedMsgCounter.decrementAndGet();
                if (head.getCreatedTime() + ttlMs < System.currentTimeMillis()) {
                    if (log.isDebugEnabled()) {
                        log.debug("[{}] Msg expired in delayed queue {}", clientId, head);
                    }
                    ReferenceCountUtil.release(head.getMqttPublishMessage());
                    continue;
                }

                addPublishedInFlightMsg(head.getMqttPublishMessage());
                sendDelayedMsg(head.getMqttPublishMessage());
                break;
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    public void sendDelayedMsg(MqttPublishMessage mqttPubMsg) {
        clientSessionCtx.getChannel().writeAndFlush(mqttPubMsg);
    }

    private MqttPubMsgWithCreatedTime getDelayedMsg(MqttPublishMessage mqttPubMsg) {
        return new MqttPubMsgWithCreatedTime(mqttPubMsg, System.currentTimeMillis());
    }

    private int delayedMsgQueueSize() {
        return delayedMsgCounter.get();
    }

    private boolean allowedToSendMsg() {
        return publishedInFlightMsgQueueSize() < clientReceiveMax;
    }

    private int publishedInFlightMsgQueueSize() {
        return publishedInFlightMsgCounter.get();
    }

    private boolean atMostOnce(MqttPublishMessage mqttPubMsg) {
        return MqttQoS.AT_MOST_ONCE == mqttPubMsg.fixedHeader().qosLevel();
    }

    private void addDelayedMsg(MqttPublishMessage mqttPubMsg) {
        delayedMsgCounter.incrementAndGet();
        delayedMsgQueue.add(getDelayedMsg(mqttPubMsg));
    }

    private boolean addPublishedInFlightMsg(MqttPublishMessage mqttPubMsg) {
        publishedInFlightMsgCounter.incrementAndGet();
        return publishedInFlightMsgQueue.add(mqttPubMsg.variableHeader().packetId());
    }

    private void removePublishedInFlightMsg() {
        publishedInFlightMsgQueue.poll();
        publishedInFlightMsgCounter.decrementAndGet();
    }

}
