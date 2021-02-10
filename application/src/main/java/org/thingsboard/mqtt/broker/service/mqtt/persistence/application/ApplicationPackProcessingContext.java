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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ApplicationPackProcessingContext {

    @Getter
    private final ConcurrentMap<Integer, PublishMsgWithOffset> pendingMap;
    @Getter
    private final ConcurrentMap<Integer, PublishMsgWithOffset> successMap = new ConcurrentHashMap<>();

    private final CountDownLatch processingTimeoutLatch;
    private final ApplicationSubmitStrategy submitStrategy;

    public ApplicationPackProcessingContext(ApplicationSubmitStrategy submitStrategy) {
        this.submitStrategy = submitStrategy;
        this.pendingMap = submitStrategy.getPendingMap();
        this.processingTimeoutLatch = new CountDownLatch(pendingMap.size());
    }

    public boolean await(long packProcessingTimeout, TimeUnit timeUnit) throws InterruptedException {
        return processingTimeoutLatch.await(packProcessingTimeout, timeUnit);
    }

    public void onSuccess(Integer packetId) {
        PublishMsgWithOffset msg = pendingMap.remove(packetId);
        if (msg != null) {
            successMap.put(packetId, msg);
            submitStrategy.onSuccess(msg.getOffset());
            processingTimeoutLatch.countDown();
        } else {
            log.info("Couldn't find packet {} to acknowledge success.", packetId);
        }
    }
}
