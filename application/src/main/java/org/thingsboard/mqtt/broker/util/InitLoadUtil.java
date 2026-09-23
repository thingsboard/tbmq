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
package org.thingsboard.mqtt.broker.util;

import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.exception.QueuePersistenceException;

@Slf4j
public final class InitLoadUtil {

    static final int MARKER_PERSIST_MAX_ATTEMPTS = 10;
    static final long MARKER_PERSIST_RETRY_PAUSE_MS = 1000;

    private InitLoadUtil() {
    }

    @FunctionalInterface
    public interface QueuePersistenceAction {
        void run() throws QueuePersistenceException;
    }

    /**
     * Persists the marker record that a compacted-topic initLoad reads back to detect it has caught up.
     * <p>
     * On a fresh Kafka cluster the topic is created synchronously right before this first write, and the partition
     * leader may not have applied the new topic metadata yet — the produce fails with UNKNOWN_TOPIC_OR_PARTITION
     * once the producer's few retries are spent. Retrying here keeps that transient window from failing startup.
     */
    public static void persistMarkerWithRetry(String name, QueuePersistenceAction action) throws QueuePersistenceException {
        persistMarkerWithRetry(name, action, MARKER_PERSIST_MAX_ATTEMPTS, MARKER_PERSIST_RETRY_PAUSE_MS);
    }

    static void persistMarkerWithRetry(String name, QueuePersistenceAction action,
                                       int maxAttempts, long pauseMs) throws QueuePersistenceException {
        for (int attempt = 1; ; attempt++) {
            try {
                action.run();
                return;
            } catch (QueuePersistenceException e) {
                if (attempt >= maxAttempts) {
                    throw e;
                }
                log.warn("[{}] Failed to persist initLoad marker (attempt {}/{}), retrying in {} ms. Reason - {}",
                        name, attempt, maxAttempts, pauseMs, e.getMessage());
                sleep(pauseMs, e);
            }
        }
    }

    private static void sleep(long pauseMs, QueuePersistenceException cause) throws QueuePersistenceException {
        if (pauseMs <= 0) {
            return;
        }
        try {
            Thread.sleep(pauseMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw cause;
        }
    }
}
