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

    // copies with a Message Expiry Interval of 0 that were not kept; the store counts the entries it expires itself
    private final AtomicLong expiredCopies = new AtomicLong();

    private PartitionedWorkers<PendingCopy> workers;

    @PostConstruct
    public void init() {
        workers = new PartitionedWorkers<>(THREAD_NAME_PREFIX, properties.effectivePersistPartitions(),
                properties.persistPartitionQueueCapacity(), properties.getPersist().getBatchSize());
        statsFactory.createGauge(PERSIST_QUEUED_GAUGE, workers, PartitionedWorkers::queued);
        statsFactory.createFunctionCounter(StatsType.EXPIRED_DEVICE_MSGS.getPrintName(), this, StandaloneDeviceMsgQueueConsumer::expiredDeviceMsgs);
        statsFactory.createFunctionCounter(StatsType.TRIMMED_DEVICE_MSGS.getPrintName(), store, DeviceMsgQueueStore::trimmedEntries);
    }

    @Override
    public void startConsuming() {
        workers.start(this::persist);
    }

    public PartitionedWorkers<?> workers() {
        return workers;
    }

    /**
     * Queues the copy for the worker of its subscriber and blocks while that worker's queue is full. When it cannot be
     * queued, the callback fails instead: with {@link org.thingsboard.mqtt.broker.exception.PipelineClosedException}
     * once the workers no longer accept copies on shutdown, which fails the whole publish the same way, or with the
     * {@link InterruptedException} of an interrupted caller.
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
            boolean expired;
            try {
                msg = ProtoConverter.protoToDevicePublishMsg(clientId, copy.msg().getValue(), copy.msg().getHeaders());
                newMsg = DeviceMsgBodyCodec.toNew(msg);
                Integer msgExpiryInterval = newMsg.msgExpiryIntervalSec();
                expired = msgExpiryInterval != null && msgExpiryInterval == 0 && !isConnected(clientId);
            } catch (Throwable t) {
                fail(copy, t);
                log.error("[{}] Failed to prepare the message for the DEVICE queue", clientId, t);
                continue;
            }
            if (expired) {
                // [MQTT-3.3.2-5] a lifetime of 0 seconds has passed before an offline subscriber could take it
                expiredCopies.incrementAndGet();
                succeed(copy);
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
            toStore.forEach(copy -> fail(copy, t));
            log.error("Failed to store {} messages for {} DEVICE clients", toStore.size(), queues.size(), t);
            return;
        }
        toStore.forEach(this::succeed);
        lastMsgs.forEach(this::wakeUp);
    }

    /**
     * A wake-up only: the device actor reads the new messages from the store. The session is looked up again, after the
     * append, so a subscriber that connected while the batch was written is woken up too.
     */
    private void wakeUp(String clientId, DevicePublishMsg lastMsg) {
        try {
            if (isConnected(clientId)) {
                deviceActorManager.notifyPublishMsg(clientId, lastMsg);
            }
        } catch (Throwable t) {
            // the messages are stored: the next wake-up or connect delivers them
            log.error("[{}] Failed to wake up the device actor", clientId, t);
        }
    }

    /**
     * A shared group's queue key never has a session, so a group is offline by the same test.
     */
    private boolean isConnected(String clientId) {
        ClientSessionInfo session = clientSessionCache.getClientSessionInfo(clientId);
        return session != null && session.isConnected();
    }

    /**
     * A copy is failed, not counted as dropped: the publish's caller counts the failed publish once.
     */
    private void fail(PendingCopy copy, Throwable failure) {
        try {
            copy.callback().onFailure(failure);
        } catch (Throwable t) {
            // the other copies of the batch must still be completed
            log.error("[{}] The failure callback of a DEVICE queue message failed", copy.clientId(), t);
        }
    }

    private void succeed(PendingCopy copy) {
        try {
            copy.callback().onSuccess();
        } catch (Throwable t) {
            // the other copies of the batch must still be completed
            log.error("[{}] The success callback of a DEVICE queue message failed", copy.clientId(), t);
        }
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
