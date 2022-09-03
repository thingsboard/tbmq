/**
 * Copyright © 2016-2022 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.service.stats.ApplicationProcessorStats;

import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ApplicationPackProcessingCtx {

    @Getter
    private final ConcurrentMap<Integer, PersistedPublishMsg> publishPendingMsgMap = new ConcurrentHashMap<>();
    @Getter
    private final ConcurrentMap<Integer, PersistedPubRelMsg> pubRelPendingMsgMap = new ConcurrentHashMap<>();

    private final ApplicationProcessorStats stats;
    private final long processingStartTimeNanos;
    private final CountDownLatch processingTimeoutLatch;
    @Getter
    private final ApplicationPubRelMsgCtx pubRelMsgCtx;

    public ApplicationPackProcessingCtx() {
        this.stats = null;
        this.processingStartTimeNanos = System.nanoTime();
        this.processingTimeoutLatch = null;
        this.pubRelMsgCtx = new ApplicationPubRelMsgCtx(Collections.emptySet());
    }

    public ApplicationPackProcessingCtx(ApplicationSubmitStrategy submitStrategy, ApplicationPubRelMsgCtx pubRelMsgCtx, ApplicationProcessorStats stats) {
        this.processingStartTimeNanos = System.nanoTime();
        this.stats = stats;
        this.pubRelMsgCtx = pubRelMsgCtx;
        for (PersistedMsg persistedMsg : submitStrategy.getPendingMap().values()) {
            switch (persistedMsg.getPacketType()) {
                case PUBLISH:
                    publishPendingMsgMap.put(persistedMsg.getPacketId(), (PersistedPublishMsg) persistedMsg);
                    break;
                case PUBREL:
                    pubRelPendingMsgMap.put(persistedMsg.getPacketId(), (PersistedPubRelMsg) persistedMsg);
                    break;
                default:
                    break;
            }
        }
        this.processingTimeoutLatch = new CountDownLatch(publishPendingMsgMap.size() + pubRelPendingMsgMap.size());
    }

    public boolean await(long packProcessingTimeout, TimeUnit timeUnit) throws InterruptedException {
        return processingTimeoutLatch.await(packProcessingTimeout, timeUnit);
    }

    // TODO: save only messages with higher offset (InFlightMessagesCtx)

    public boolean onPubAck(Integer packetId) {
        PersistedPublishMsg msg = publishPendingMsgMap.remove(packetId);
        if (msg != null) {
            stats.logPubAckLatency(System.nanoTime() - processingStartTimeNanos, TimeUnit.NANOSECONDS);
            processingTimeoutLatch.countDown();
            return true;
        } else {
            log.debug("Couldn't find PUBLISH packet {} to process publish msg success.", packetId);
        }
        return false;
    }

    public boolean onPubRec(Integer packetId) {
        // TODO: think what to do if PUBREC came after PackContext timeout
        PersistedPublishMsg msg = publishPendingMsgMap.get(packetId);
        if (msg != null) {
            stats.logPubRecLatency(System.nanoTime() - processingStartTimeNanos, TimeUnit.NANOSECONDS);

            pubRelMsgCtx.addPubRelMsg(new PersistedPubRelMsg(packetId, msg.getPacketOffset()));
            onPublishMsgSuccess(packetId);
            return true;
        } else {
            log.debug("Couldn't find PUBLISH packet {} to process PUBREC msg.", packetId);
        }
        return false;
    }

    private void onPublishMsgSuccess(Integer packetId) {
        PersistedPublishMsg msg = publishPendingMsgMap.remove(packetId);
        if (msg != null) {
            processingTimeoutLatch.countDown();
        } else {
            log.debug("Couldn't find PUBLISH packet {} to process publish msg success.", packetId);
        }
    }

    public boolean onPubComp(Integer packetId) {
        PersistedPubRelMsg msg = pubRelPendingMsgMap.remove(packetId);
        if (msg != null) {
            stats.logPubCompLatency(System.nanoTime() - processingStartTimeNanos, TimeUnit.NANOSECONDS);
            processingTimeoutLatch.countDown();
            return true;
        } else {
            log.warn("Couldn't find packet {} to complete delivery.", packetId);
        }
        return false;
    }

    public void clear() {
        publishPendingMsgMap.clear();
        pubRelPendingMsgMap.clear();
    }
}
