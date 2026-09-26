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
package org.thingsboard.mqtt.broker.service.trace;

import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.gen.queue.ClientTraceEventProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ClientTraceQueueFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientTraceRecorder {
    private final ClientTraceRegistry registry;
    private final ClientTraceQueueFactory queueFactory;
    private final ServiceInfoProvider serviceInfoProvider;

    @Value("${queue.client-trace.queue-capacity:10000}")
    private int capacity;
    @Value("${queue.client-trace.batch-size:200}")
    private int batchSize;
    @Value("${queue.client-trace.max-events-per-second:1000}")
    private int maxEventsPerSecond;
    @Value("${queue.client-trace.max-details-length:2048}")
    private int maxDetailsLength;
    @Value("${queue.client-trace.max-topics:20}")
    private int maxTopics;

    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong nextCapture = new AtomicLong();
    private final AtomicLong nextWarning = new AtomicLong();
    private Semaphore slots;
    private long captureInterval;
    private BlockingQueue<ClientTraceEventProto> queue;
    private TbQueueProducer<TbProtoQueueMsg<ClientTraceEventProto>> producer;
    private Thread sender;

    @PostConstruct
    void start() {
        if (capacity < 1 || batchSize < 1 || maxEventsPerSecond < 1 || maxDetailsLength < 1 || maxTopics < 1) {
            log.warn("Client trace disabled: capture limits must be positive");
            return;
        }
        queue = new ArrayBlockingQueue<>(capacity);
        slots = new Semaphore(capacity);
        captureInterval = Math.max(1, TimeUnit.SECONDS.toNanos(1) / maxEventsPerSecond);
        nextCapture.set(System.nanoTime());
        nextWarning.set(System.nanoTime());
        try {
            producer = queueFactory.createProducer(serviceInfoProvider.getServiceId());
        } catch (Exception e) {
            log.warn("Client trace disabled: producer initialization failed", e);
            return;
        }
        running.set(true);
        sender = new Thread(this::sendLoop, "client-trace-sender");
        sender.setDaemon(true);
        sender.start();
    }

    public void tryRecord(String clientId, UUID sessionId, InetSocketAddress address, String direction, MqttMessage message) {
        if (!running.get()) return;
        boolean reserved = false;
        try {
            if (clientId == null || message == null || message.fixedHeader() == null) return;
            var trace = registry.get(clientId).orElse(null);
            if (trace == null) return;
            // A single CAS keeps admission bounded even under contention; tracing is best effort.
            long now = System.nanoTime();
            long next = nextCapture.get();
            if (now - next < 0 || !nextCapture.compareAndSet(next, now + captureInterval)) {
                dropped.incrementAndGet();
                return;
            }
            if (!slots.tryAcquire()) {
                dropped.incrementAndGet();
                return;
            }
            reserved = true;
            ClientTraceEventProto.Builder event = ClientTraceEventProto.newBuilder()
                    .setEventId(UUID.randomUUID().toString()).setTraceId(trace.id().toString())
                    .setClientId(clientId).setSessionId(sessionId.toString())
                    .setTs(System.currentTimeMillis()).setDirection(direction)
                    .setPacketType(message.fixedHeader().messageType().name())
                    .setQos(message.fixedHeader().qosLevel().value())
                    .setRemoteAddress(address == null ? "" : address.toString())
                    .setServiceId(serviceInfoProvider.getServiceId());
            enrich(event, message, trace.level());
            if (running.get() && queue.offer(event.build())) {
                reserved = false; // The sender now owns this slot.
            } else {
                dropped.incrementAndGet();
            }
        } catch (Exception e) {
            recordFailure(e);
        } finally {
            if (reserved) slots.release();
        }
    }

    public long getDroppedEvents() {
        return dropped.get();
    }

    private void recordFailure(Throwable error) {
        long count = dropped.incrementAndGet();
        long now = System.nanoTime();
        long next = nextWarning.get();
        if (now - next >= 0 && nextWarning.compareAndSet(next, now + TimeUnit.SECONDS.toNanos(30))) {
            log.warn("Client trace failure; {} events dropped", count, error);
        }
    }

    private void appendBounded(StringBuilder details, String value) {
        int length = Math.min(value.length(), maxDetailsLength - details.length());
        // Avoid splitting UTF-16 surrogate pairs at the truncation boundary.
        if (length > 0 && length < value.length() && Character.isHighSurrogate(value.charAt(length - 1))) length--;
        details.append(value, 0, length);
    }

    private void enrich(ClientTraceEventProto.Builder event, MqttMessage message, ClientTraceLevel level) {
        if (message instanceof MqttPublishMessage publish) {
            event.setTopic(publish.variableHeader().topicName()).setPacketId(publish.variableHeader().packetId())
                    .setPayloadSize(publish.payload().readableBytes());
        } else if (message.variableHeader() instanceof MqttMessageIdVariableHeader header) {
            event.setPacketId(header.messageId());
        }
        if (level == ClientTraceLevel.FULL) {
            StringBuilder details = new StringBuilder(Math.min(maxDetailsLength, 256));
            if (message instanceof MqttSubscribeMessage subscribe) {
                var topics = subscribe.payload().topicSubscriptions();
                for (int i = 0; i < Math.min(topics.size(), maxTopics) && details.length() < maxDetailsLength; i++) {
                    if (i > 0) appendBounded(details, ", ");
                    appendBounded(details, topics.get(i).topicFilter());
                    appendBounded(details, ":" + topics.get(i).qualityOfService().value());
                }
            } else if (message instanceof MqttUnsubscribeMessage unsubscribe) {
                var topics = unsubscribe.payload().topics();
                for (int i = 0; i < Math.min(topics.size(), maxTopics) && details.length() < maxDetailsLength; i++) {
                    if (i > 0) appendBounded(details, ", ");
                    appendBounded(details, topics.get(i));
                }
            }
            event.setDetails(details.toString());
        }
    }

    private void sendLoop() {
        List<ClientTraceEventProto> batch = new ArrayList<>(batchSize);
        while (running.get() || !queue.isEmpty()) {
            try {
                batch.add(queue.take());
                queue.drainTo(batch, batchSize - 1);
                slots.release(batch.size());
                for (ClientTraceEventProto event : batch) {
                    producer.send(new TbProtoQueueMsg<>(event.getClientId(), event), new LogOnlyCallback());
                }
                batch.clear();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                dropped.addAndGet(Math.max(0, batch.size() - 1));
                recordFailure(e);
                batch.clear();
            }
        }
    }

    @PreDestroy
    void stop() {
        running.set(false);
        if (sender != null) sender.interrupt();
        if (producer != null) producer.stop();
    }

    private class LogOnlyCallback implements TbQueueCallback {
        @Override public void onSuccess(TbQueueMsgMetadata metadata) {}
        @Override public void onFailure(Throwable t) { recordFailure(t); }
    }
}
