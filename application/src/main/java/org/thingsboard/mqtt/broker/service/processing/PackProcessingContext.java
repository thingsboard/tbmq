/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.processing;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PackProcessingContext {

    @Getter
    private final ConcurrentMap<UUID, PublishMsgWithId> pendingMap;
    @Getter
    private final ConcurrentMap<UUID, PublishMsgWithId> failedMap = new ConcurrentHashMap<>();

    private final CountDownLatch processingTimeoutLatch;

    public PackProcessingContext(ConcurrentMap<UUID, PublishMsgWithId> pendingMessages) {
        this.pendingMap = pendingMessages;
        this.processingTimeoutLatch = new CountDownLatch(pendingMap.size());
    }

    public boolean await(long packProcessingTimeout, TimeUnit timeUnit) throws InterruptedException {
        return processingTimeoutLatch.await(packProcessingTimeout, timeUnit);
    }

    public void onSuccess(UUID id) {
        PublishMsgWithId msg = pendingMap.remove(id);
        if (msg != null) {
            processingTimeoutLatch.countDown();
        } else {
            log.debug("Couldn't find message {} to acknowledge success.", id);
        }
    }

    public void onFailure(UUID id) {
        PublishMsgWithId msg = pendingMap.remove(id);
        if (msg != null) {
            failedMap.put(id, msg);
            processingTimeoutLatch.countDown();
        } else {
            log.debug("Couldn't find message {} to acknowledge failure.", id);
        }
    }

    public void cleanup() {
        pendingMap.clear();
    }
}
