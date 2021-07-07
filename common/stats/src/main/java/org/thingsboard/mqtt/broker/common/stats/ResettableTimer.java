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
package org.thingsboard.mqtt.broker.common.stats;

import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ResettableTimer {
    private final AtomicInteger logCount = new AtomicInteger(0);
    private final AtomicLong passedNanoseconds = new AtomicLong(0);
    private final Timer timer;

    public ResettableTimer(Timer timer) {
        this.timer = timer;
    }

    public void logTime(long amount, TimeUnit unit) {
        timer.record(amount, unit);
        passedNanoseconds.addAndGet(TimeUnit.NANOSECONDS.convert(amount, unit));
        logCount.incrementAndGet();
    }

    public double getAvg() {
        double currentLogCount = logCount.get();
        double currentPassedNanos = passedNanoseconds.get();
        double avgNanoTime = currentLogCount > 0 ? currentPassedNanos / currentLogCount : 0;
        return avgNanoTime / 1_000_000;
    }

    public int getCount() {
        return logCount.get();
    }

    public void reset() {
        logCount.getAndSet(0);
        passedNanoseconds.getAndSet(0);
    }

    public Timer getTimer() {
        return timer;
    }
}
