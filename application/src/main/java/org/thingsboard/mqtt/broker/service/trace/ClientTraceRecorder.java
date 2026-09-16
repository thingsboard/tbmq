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

    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong dropped = new AtomicLong();
    private BlockingQueue<ClientTraceEventProto> queue;
    private TbQueueProducer<TbProtoQueueMsg<ClientTraceEventProto>> producer;
    private Thread sender;

    @PostConstruct
    void start() {
        queue = new ArrayBlockingQueue<>(capacity);
        producer = queueFactory.createProducer(serviceInfoProvider.getServiceId());
        running.set(true);
        sender = new Thread(this::sendLoop, "client-trace-sender");
        sender.setDaemon(true);
        sender.start();
    }

    public void tryRecord(String clientId, UUID sessionId, InetSocketAddress address, String direction, MqttMessage message) {
        if (clientId == null || message == null || message.fixedHeader() == null) return;
        registry.get(clientId).ifPresent(trace -> {
            ClientTraceEventProto.Builder event = ClientTraceEventProto.newBuilder()
                    .setEventId(UUID.randomUUID().toString()).setTraceId(trace.id().toString())
                    .setClientId(clientId).setSessionId(sessionId.toString())
                    .setTs(System.currentTimeMillis()).setDirection(direction)
                    .setPacketType(message.fixedHeader().messageType().name())
                    .setQos(message.fixedHeader().qosLevel().value())
                    .setRemoteAddress(address == null ? "" : address.toString())
                    .setServiceId(serviceInfoProvider.getServiceId());
            enrich(event, message, trace.level());
            if (!queue.offer(event.build())) {
                long count = dropped.incrementAndGet();
                if (count == 1 || count % 1000 == 0) log.warn("Client trace queue is full; dropped {} events", count);
            }
        });
    }

    private void enrich(ClientTraceEventProto.Builder event, MqttMessage message, ClientTraceLevel level) {
        if (message instanceof MqttPublishMessage publish) {
            event.setTopic(publish.variableHeader().topicName()).setPacketId(publish.variableHeader().packetId())
                    .setPayloadSize(publish.payload().readableBytes());
        } else if (message.variableHeader() instanceof MqttMessageIdVariableHeader header) {
            event.setPacketId(header.messageId());
        }
        if (level == ClientTraceLevel.FULL) {
            if (message instanceof MqttSubscribeMessage subscribe) {
                event.setDetails(subscribe.payload().topicSubscriptions().stream()
                        .map(s -> s.topicFilter() + ":" + s.qualityOfService().value()).toList().toString());
            } else if (message instanceof MqttUnsubscribeMessage unsubscribe) {
                event.setDetails(unsubscribe.payload().topics().toString());
            }
        }
    }

    private void sendLoop() {
        List<ClientTraceEventProto> batch = new ArrayList<>(batchSize);
        while (running.get() || !queue.isEmpty()) {
            try {
                batch.add(queue.take());
                queue.drainTo(batch, batchSize - 1);
                for (ClientTraceEventProto event : batch) {
                    producer.send(new TbProtoQueueMsg<>(event.getClientId(), event), new LogOnlyCallback());
                }
                batch.clear();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("Failed to publish client trace batch", e);
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

    private static class LogOnlyCallback implements TbQueueCallback {
        @Override public void onSuccess(TbQueueMsgMetadata metadata) {}
        @Override public void onFailure(Throwable t) { log.warn("Failed to publish client trace event", t); }
    }
}
