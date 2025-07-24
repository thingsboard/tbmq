/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.actors.device.retry;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Getter
@Slf4j
public class ExponentialBackoffPolicy implements RetryPolicy {

    public static final int DEFAULT_RETRY_INTERVAL_SEC = 1;
    public static final int MAX_RETRY_INTERVAL_SEC = 10;
    public static final int MAX_RETRY_COUNT = 5;
    public static final int EXP_MAX = 8;
    public static final long JITTER_MAX = 1;

    private final long retryIntervalMinSeconds;
    private final long retryIntervalMaxSeconds;

    private long lastRetryNanoTime = 0;
    private long retryCount = 0;

    public ExponentialBackoffPolicy(long retryIntervalMinSeconds) {
        this.retryIntervalMaxSeconds = calculateIntervalMax(retryIntervalMinSeconds);
        this.retryIntervalMinSeconds = calculateIntervalMin(retryIntervalMinSeconds);
    }

    long calculateIntervalMax(long reconnectIntervalMinSeconds) {
        return reconnectIntervalMinSeconds > MAX_RETRY_INTERVAL_SEC ? reconnectIntervalMinSeconds : MAX_RETRY_INTERVAL_SEC;
    }

    long calculateIntervalMin(long reconnectIntervalMinSeconds) {
        return Math.min((reconnectIntervalMinSeconds > 0 ? reconnectIntervalMinSeconds : DEFAULT_RETRY_INTERVAL_SEC), this.retryIntervalMaxSeconds);
    }

    @Override
    public boolean canRetry() {
        return retryCount < MAX_RETRY_COUNT;
    }

    @Override
    public void reset() {
        retryCount = 0;
        lastRetryNanoTime = 0;
    }

    @Override
    public long nextDelay() {
        final long currentNanoTime = getNanoTime();
        final long coolDownSpentNanos = currentNanoTime - lastRetryNanoTime;
        lastRetryNanoTime = currentNanoTime;
        if (isCooledDown(coolDownSpentNanos)) {
            retryCount = 0;
            return retryIntervalMinSeconds;
        }
        return calculateNextRetryDelay() + calculateJitter();
    }

    long calculateJitter() {
        return ThreadLocalRandom.current().nextInt() >= 0 ? JITTER_MAX : 0;
    }

    long calculateNextRetryDelay() {
        return Math.min(retryIntervalMaxSeconds, retryIntervalMinSeconds + calculateExp(retryCount++));
    }

    long calculateExp(long e) {
        return 1L << Math.min(e, EXP_MAX);
    }

    boolean isCooledDown(long coolDownSpentNanos) {
        return TimeUnit.NANOSECONDS.toSeconds(coolDownSpentNanos) > retryIntervalMaxSeconds + retryIntervalMinSeconds;
    }

    long getNanoTime() {
        return System.nanoTime();
    }

}
