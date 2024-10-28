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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.device.processing;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
public class DevicePackProcessingContext {

    @Getter
    private final ConcurrentMap<String, ClientIdMessagesPack> pendingMap;
    @Getter
    private final ConcurrentMap<String, ClientIdMessagesPack> failedMap = new ConcurrentHashMap<>();
    @Getter
    private final ConcurrentMap<String, DevicePublishMsgListAndPrevPacketId> successMap = new ConcurrentHashMap<>();

    private final CountDownLatch processingTimeoutLatch;

    public DevicePackProcessingContext(ConcurrentMap<String, ClientIdMessagesPack> pendingClientIdPacks) {
        this.pendingMap = pendingClientIdPacks;
        this.processingTimeoutLatch = new CountDownLatch(pendingMap.size());
    }

    public boolean await(long packProcessingTimeout, TimeUnit timeUnit) throws InterruptedException {
        return processingTimeoutLatch.await(packProcessingTimeout, timeUnit);
    }

    public void onSuccess(String clientId, int previousPacketId) {
        ClientIdMessagesPack pack = pendingMap.remove(clientId);
        if (pack != null) {
            successMap.put(clientId, new DevicePublishMsgListAndPrevPacketId(pack.messages(), previousPacketId));
            processingTimeoutLatch.countDown();
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Couldn't find messages pack for clientId {} to acknowledge success.", clientId);
            }
        }
    }

    public void onFailure(String clientId) {
        ClientIdMessagesPack pack = pendingMap.remove(clientId);
        if (pack != null) {
            failedMap.put(clientId, pack);
            processingTimeoutLatch.countDown();
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Couldn't find messages pack for clientId {} to acknowledge failure.", clientId);
            }
        }
    }

    public void cleanup() {
        pendingMap.clear();
        successMap.clear();
    }

}
