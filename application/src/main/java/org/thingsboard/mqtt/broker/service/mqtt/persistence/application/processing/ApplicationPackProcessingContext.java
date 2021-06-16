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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing;

import com.google.common.collect.Sets;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ApplicationPackProcessingContext {

    @Getter
    private final ConcurrentMap<Integer, PersistedPublishMsg> publishPendingMsgMap = new ConcurrentHashMap<>();
    @Getter
    private final ConcurrentMap<Integer, PersistedPubRelMsg> pubRelPendingMsgMap = new ConcurrentHashMap<>();

    private final CountDownLatch processingTimeoutLatch;
    // TODO: wrap in separate class
    @Getter
    private final Collection<PersistedPubRelMsg> newPubRelPackets;

    public ApplicationPackProcessingContext(ApplicationSubmitStrategy submitStrategy, Collection<PersistedPubRelMsg> newPubRelPackets) {
        this.newPubRelPackets = newPubRelPackets;
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

    public void onPubAck(Integer packetId) {
        onPublishMsgSuccess(packetId);
    }

    public void onPubRec(Integer packetId) {
        // TODO: think what to do if PUBREC came after PackContext timeout
        Long packetOffset = onPublishMsgSuccess(packetId);
        if (packetOffset != null) {
            newPubRelPackets.add(new PersistedPubRelMsg(packetId, packetOffset));
        }
    }

    private Long onPublishMsgSuccess(Integer packetId) {
        PersistedPublishMsg msg = publishPendingMsgMap.remove(packetId);
        if (msg != null) {
            processingTimeoutLatch.countDown();
            return msg.getPacketOffset();
        } else {
            log.warn("Couldn't find PUBLISH packet {} to process publish msg success.", packetId);
            return null;
        }
    }

    public void onPubComp(Integer packetId) {
        PersistedPubRelMsg msg = pubRelPendingMsgMap.remove(packetId);
        if (msg != null) {
            processingTimeoutLatch.countDown();
        } else {
            log.warn("Couldn't find packet {} to complete delivery.", packetId);
        }
    }

    public void clear() {
        publishPendingMsgMap.clear();
        pubRelPendingMsgMap.clear();
    }
}
