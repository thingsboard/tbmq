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

import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.data.mqtt.MqttPubMsgWithCreatedTime;
import org.thingsboard.mqtt.broker.service.mqtt.delivery.MqttPublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.mqtt.flow.control.FlowControlService;
import org.thingsboard.mqtt.broker.service.stats.FlowControlStats;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class PublishedInFlightCtxImpl implements PublishedInFlightCtx {

    private final Set<Integer> inFlightPacketIds = ConcurrentHashMap.newKeySet();
    private final Queue<MqttPubMsgWithCreatedTime> delayedMsgQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger delayedMsgCounter = new AtomicInteger(0);

    private final Lock lock = new ReentrantLock();

    private final FlowControlService flowControlService;
    private final ClientSessionCtx clientSessionCtx;
    private final MqttPublishMsgDeliveryService deliveryService;
    private final FlowControlStats stats;
    private final String clientId;
    private final int clientReceiveMax;
    private final int delayedMsgQueueMaxSize;

    public PublishedInFlightCtxImpl(FlowControlService flowControlService,
                                    ClientSessionCtx clientSessionCtx,
                                    MqttPublishMsgDeliveryService deliveryService,
                                    FlowControlStats stats,
                                    int clientReceiveMax,
                                    int delayedMsgQueueMaxSize) {
        this.flowControlService = flowControlService;
        this.clientSessionCtx = clientSessionCtx;
        this.deliveryService = deliveryService;
        this.stats = stats;
        this.clientId = clientSessionCtx.getClientId();
        this.clientReceiveMax = clientReceiveMax;
        this.delayedMsgQueueMaxSize = delayedMsgQueueMaxSize;
    }

    @Override
    public boolean addInFlightMsg(MqttPublishMessage mqttPubMsg) {
        if (atMostOnce(mqttPubMsg)) {
            return true;
        }
        int packetId = mqttPubMsg.variableHeader().packetId();
        log.trace("[{}] Adding in-flight msg [{}]", clientId, packetId);

        boolean reserve = false;
        boolean register = false;
        MqttPublishMessage toRelease = null;

        lock.lock();
        try {
            if (delayedMsgQueue.isEmpty() && inFlightPacketIds.size() < clientReceiveMax) {
                inFlightPacketIds.add(packetId);
                reserve = true;
            } else if (delayedMsgCounter.get() < delayedMsgQueueMaxSize) {
                delayedMsgQueue.add(new MqttPubMsgWithCreatedTime(mqttPubMsg, System.currentTimeMillis()));
                delayedMsgCounter.incrementAndGet();
                register = true;
            } else {
                toRelease = mqttPubMsg;
            }
        } finally {
            lock.unlock();
        }

        if (reserve) {
            stats.incInflight();
        } else if (register) {
            log.debug("[{}][{}] Max in-flight messages reached! Buffering msg in delay queue", clientId, clientReceiveMax);
            stats.incDelayed();
            flowControlService.addToMap(clientId, this);
        } else {
            log.warn("[{}] Message is skipped! Max in-flight messages reached [{}] and delay queue is full [{}]!",
                    clientId, clientReceiveMax, delayedMsgQueueMaxSize);
            ReferenceCountUtil.release(toRelease);
            stats.incDropOverflow();
        }
        return reserve;
    }

    @Override
    public void ackInFlightMsg(int msgId) {
        log.trace("[{}] Acknowledging in-flight msg [{}]", clientId, msgId);
        boolean removed;
        lock.lock();
        try {
            removed = inFlightPacketIds.remove(msgId);
        } finally {
            lock.unlock();
        }
        if (!removed) {
            log.warn("[{}] Unexpected ack for packetId={} not currently in-flight", clientId, msgId);
            stats.incUnknownAck();
            return;
        }
        stats.decInflight();
        tryDrain();
    }

    @Override
    public void onChannelWritable() {
        tryDrain();
    }

    /**
     * Drains the buffered messages while the channel is writable and the in-flight window has space.
     * TTL is NOT checked here — see {@link #expireTtl(long)}.
     * Loop is bounded: the queue size cannot exceed {@code delayedMsgQueueMaxSize}.
     * Each iteration calls the delivery service OUTSIDE the lock.
     */
    private void tryDrain() {
        while (clientSessionCtx.getChannel().channel().isWritable()) {
            MqttPublishMessage toSend = null;
            boolean queueEmpty = false;

            lock.lock();
            try {
                MqttPubMsgWithCreatedTime head = delayedMsgQueue.peek();
                if (head == null) {
                    queueEmpty = true;
                } else if (inFlightPacketIds.size() >= clientReceiveMax) {
                    // Window full; the next ack will retry the drain.
                    return;
                } else {
                    delayedMsgQueue.poll();
                    delayedMsgCounter.decrementAndGet();
                    inFlightPacketIds.add(head.getMqttPublishMessage().variableHeader().packetId());
                    toSend = head.getMqttPublishMessage();
                }
            } finally {
                lock.unlock();
            }

            if (queueEmpty) {
                flowControlService.removeFromMap(clientId);
                return;
            }

            stats.decDelayed();
            stats.incInflight();
            deliveryService.sendAlreadyTrackedPublishMsgToClient(clientSessionCtx, toSend);
        }
    }

    @Override
    public void expireTtl(long ttlMs) {
        long cutoff = System.currentTimeMillis() - ttlMs;
        List<MqttPublishMessage> toRelease = new ArrayList<>();
        boolean queueEmpty;

        lock.lock();
        try {
            while (true) {
                MqttPubMsgWithCreatedTime head = delayedMsgQueue.peek();
                if (head == null || head.getCreatedTime() >= cutoff) {
                    break;
                }
                delayedMsgQueue.poll();
                delayedMsgCounter.decrementAndGet();
                toRelease.add(head.getMqttPublishMessage());
            }
            queueEmpty = delayedMsgQueue.isEmpty();
        } finally {
            lock.unlock();
        }

        for (MqttPublishMessage m : toRelease) {
            ReferenceCountUtil.release(m);
            stats.decDelayed();
            stats.incDropTtl();
        }

        if (queueEmpty) {
            flowControlService.removeFromMap(clientId);
        }
    }

    @Override
    public void release() {
        List<MqttPublishMessage> toRelease;
        int inFlightSize;
        int delayedSize;

        lock.lock();
        try {
            delayedSize = delayedMsgCounter.get();
            toRelease = new ArrayList<>(delayedSize);
            for (MqttPubMsgWithCreatedTime entry : delayedMsgQueue) {
                toRelease.add(entry.getMqttPublishMessage());
            }
            delayedMsgQueue.clear();
            delayedMsgCounter.set(0);
            inFlightSize = inFlightPacketIds.size();
            inFlightPacketIds.clear();
        } finally {
            lock.unlock();
        }

        if (inFlightSize > 0) {
            stats.decInflight(inFlightSize);
        }
        if (delayedSize > 0) {
            stats.decDelayed(delayedSize);
        }
        for (MqttPublishMessage m : toRelease) {
            ReferenceCountUtil.release(m);
        }
    }

    private boolean atMostOnce(MqttPublishMessage mqttPubMsg) {
        return MqttQoS.AT_MOST_ONCE == mqttPubMsg.fixedHeader().qosLevel();
    }

}
