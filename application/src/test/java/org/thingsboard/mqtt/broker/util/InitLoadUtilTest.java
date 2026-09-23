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

import org.junit.Test;
import org.thingsboard.mqtt.broker.exception.QueuePersistenceException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class InitLoadUtilTest {

    @Test
    public void givenTransientFailures_whenPersistMarkerWithRetry_thenRetriesUntilSuccess() throws Exception {
        AtomicInteger attempts = new AtomicInteger();

        InitLoadUtil.persistMarkerWithRetry("test", () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new QueuePersistenceException("This server does not host this topic-partition.");
            }
        }, 5, 0);

        assertEquals(3, attempts.get());
    }

    @Test
    public void givenPersistentFailure_whenPersistMarkerWithRetry_thenThrowsLastErrorAfterMaxAttempts() {
        AtomicInteger attempts = new AtomicInteger();
        QueuePersistenceException error = new QueuePersistenceException("boom");

        QueuePersistenceException thrown = assertThrows(QueuePersistenceException.class,
                () -> InitLoadUtil.persistMarkerWithRetry("test", () -> {
                    attempts.incrementAndGet();
                    throw error;
                }, 4, 0));

        assertSame(error, thrown);
        assertEquals(4, attempts.get());
    }

    @Test
    public void givenImmediateSuccess_whenPersistMarkerWithRetry_thenRunsOnce() throws Exception {
        AtomicInteger attempts = new AtomicInteger();

        InitLoadUtil.persistMarkerWithRetry("test", attempts::incrementAndGet, 4, 0);

        assertEquals(1, attempts.get());
    }
}
