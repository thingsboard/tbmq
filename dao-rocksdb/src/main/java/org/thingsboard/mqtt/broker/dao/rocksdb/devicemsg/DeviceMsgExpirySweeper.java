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
package org.thingsboard.mqtt.broker.dao.rocksdb.devicemsg;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgQueueStore;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Deletes the expired entries of the DEVICE queues every {@code store.device-msg.sweep-interval-ms} on a daemon
 * thread, so the queues of clients that never read again do not keep them until the TTL. A failed sweep is logged
 * and the next one runs on schedule.
 * <p>
 * Depends on the store, so Spring destroys it first: {@link #stop()} ends the thread before the store closes.
 */
@Slf4j
@Component
public class DeviceMsgExpirySweeper {

    private static final String THREAD_NAME = "store-device-msg-sweeper";
    private static final long STOP_TIMEOUT_MS = 5000;

    private final DeviceMsgQueueStore store;
    private final long sweepIntervalMs;

    private volatile boolean stopping;
    private ScheduledExecutorService executor;

    public DeviceMsgExpirySweeper(DeviceMsgQueueStore store,
                                  @Value("${store.device-msg.sweep-interval-ms:600000}") long sweepIntervalMs) {
        this.store = store;
        this.sweepIntervalMs = sweepIntervalMs;
    }

    @PostConstruct
    public synchronized void start() {
        if (sweepIntervalMs <= 0) {
            throw new IllegalStateException("store.device-msg.sweep-interval-ms must be positive, got " + sweepIntervalMs);
        }
        if (executor != null) {
            return;
        }
        stopping = false;
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::sweepOnce, sweepIntervalMs, sweepIntervalMs, TimeUnit.MILLISECONDS);
    }

    private void sweepOnce() {
        try {
            int removed = store.sweepExpired();
            log.debug("Swept {} expired entries from the device queues", removed);
        } catch (Throwable t) {
            if (stopping) {
                log.debug("The device queue sweep was interrupted by the shutdown", t);
                return;
            }
            // never rethrow: an exception would cancel the schedule
            log.error("Failed to sweep the expired entries of the device queues, will retry in {} ms", sweepIntervalMs, t);
        }
    }

    @PreDestroy
    public synchronized void stop() {
        if (executor == null) {
            return;
        }
        stopping = true;
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(STOP_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                log.warn("The device queue sweeper thread did not stop within {} ms", STOP_TIMEOUT_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executor = null;
    }

}
