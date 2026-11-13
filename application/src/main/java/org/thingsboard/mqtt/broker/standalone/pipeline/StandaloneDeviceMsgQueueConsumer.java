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

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsType;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgQueueStore;
import org.thingsboard.mqtt.broker.dao.messages.NewDeviceMsg;
import org.thingsboard.mqtt.broker.dao.messages.QueueAppend;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.DeviceActorManager;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.queue.DeviceMsgQueueConsumer;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgCallback;
import org.thingsboard.mqtt.broker.standalone.device.DeviceMsgBodyCodec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stage 2 of the publish pipeline: writes the copies for persistent DEVICE subscribers to their store queues. The
 * copies are partitioned by subscriber client id, so the copies of one client are appended in the order they arrived.
 * A worker appends its whole batch in one store write and acknowledges a copy only after that write returned; the
 * device actor of a connected subscriber is then woken up to read its queue.
 */
@Slf4j
@Service
@StandaloneComponent
@RequiredArgsConstructor
public class StandaloneDeviceMsgQueueConsumer implements DeviceMsgQueueConsumer {

    static final String PERSIST_QUEUED_GAUGE = "standalonePersistQueued";
    private static final String THREAD_NAME_PREFIX = "standalone-persist";

    private final PipelineProperties properties;
    private final StatsFactory statsFactory;
    private final DeviceMsgQueueStore store;
    private final ClientSessionCache clientSessionCache;
    private final DeviceActorManager deviceActorManager;
    private final TbMessageStatsReportClient tbMessageStatsReportClient;

    // copies with a Message Expiry Interval of 0 that were not kept; the store counts the entries it expires itself
    private final AtomicLong expiredCopies = new AtomicLong();

    private PartitionedWorkers<PendingCopy> workers;

    @PostConstruct
    public void init() {
        PipelineProperties.Persist persist = properties.getPersist();
        workers = new PartitionedWorkers<>(THREAD_NAME_PREFIX, properties.effectivePersistPartitions(),
                persist.getPartitionQueueCapacity(), persist.getBatchSize(), this::persist);
        statsFactory.createGauge(PERSIST_QUEUED_GAUGE, workers, PartitionedWorkers::queued);
        statsFactory.createFunctionCounter(StatsType.EXPIRED_DEVICE_MSGS.getPrintName(), this, StandaloneDeviceMsgQueueConsumer::expiredDeviceMsgs);
        statsFactory.createFunctionCounter(StatsType.TRIMMED_DEVICE_MSGS.getPrintName(), store, DeviceMsgQueueStore::trimmedEntries);
    }

    @Override
    public void startConsuming() {
        workers.start();
    }

    public PartitionedWorkers<?> workers() {
        return workers;
    }

    /**
     * Queues the copy for the worker of its subscriber and blocks while that worker's queue is full. When it cannot be
     * queued, because the workers no longer accept copies or the caller is interrupted, the callback fails instead.
     */
    void submit(String clientId, TbProtoQueueMsg<PublishMsgProto> msg, PublishMsgCallback callback) {
        try {
            workers.submit(clientId, new PendingCopy(clientId, msg, callback));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            callback.onFailure(e);
        } catch (RuntimeException e) {
            callback.onFailure(e);
        }
    }

    private void persist(List<PendingCopy> batch) {
        Map<String, List<NewDeviceMsg>> queues = new LinkedHashMap<>();
        Map<String, DevicePublishMsg> lastMsgs = new LinkedHashMap<>();
        List<PendingCopy> toStore = new ArrayList<>(batch.size());
        for (PendingCopy copy : batch) {
            String clientId = copy.clientId();
            DevicePublishMsg msg;
            NewDeviceMsg newMsg;
            try {
                msg = ProtoConverter.protoToDevicePublishMsg(clientId, copy.msg().getValue(), copy.msg().getHeaders());
                newMsg = DeviceMsgBodyCodec.toNew(msg);
            } catch (Throwable t) {
                log.error("[{}] Failed to read the message for the DEVICE queue", clientId, t);
                fail(List.of(copy), t);
                continue;
            }
            Integer msgExpiryInterval = newMsg.msgExpiryIntervalSec();
            if (msgExpiryInterval != null && msgExpiryInterval == 0 && !isConnected(clientId)) {
                // [MQTT-3.3.2-5] a lifetime of 0 seconds has passed before an offline subscriber could take it
                expiredCopies.incrementAndGet();
                copy.callback().onSuccess();
                continue;
            }
            queues.computeIfAbsent(clientId, id -> new ArrayList<>()).add(newMsg);
            lastMsgs.put(clientId, msg);
            toStore.add(copy);
        }
        if (toStore.isEmpty()) {
            return;
        }
        try {
            store.append(queues.entrySet().stream().map(queue -> new QueueAppend(queue.getKey(), queue.getValue())).toList());
        } catch (Throwable t) {
            log.error("Failed to store {} messages for {} DEVICE clients", toStore.size(), queues.size(), t);
            fail(toStore, t);
            return;
        }
        toStore.forEach(copy -> copy.callback().onSuccess());
        // a wake-up only: the device actor reads the new messages from the store. The session is looked up again, so a
        // subscriber that connected while the batch was written is woken up too
        lastMsgs.forEach((clientId, msg) -> {
            if (isConnected(clientId)) {
                deviceActorManager.notifyPublishMsg(clientId, msg);
            }
        });
    }

    /**
     * A shared group's queue key never has a session, so a group is offline by the same test.
     */
    private boolean isConnected(String clientId) {
        ClientSessionInfo session = clientSessionCache.getClientSessionInfo(clientId);
        return session != null && session.isConnected();
    }

    private void fail(List<PendingCopy> copies, Throwable t) {
        tbMessageStatsReportClient.reportDroppedMsgs(copies.size());
        copies.forEach(copy -> copy.callback().onFailure(t));
    }

    private double expiredDeviceMsgs() {
        return expiredCopies.get() + store.expiredEntries();
    }

    @PreDestroy
    public void destroy() {
        if (workers != null) {
            workers.stop();
        }
    }

    private record PendingCopy(String clientId, TbProtoQueueMsg<PublishMsgProto> msg, PublishMsgCallback callback) {
    }

}
