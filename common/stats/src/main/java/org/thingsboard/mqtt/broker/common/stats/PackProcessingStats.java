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
package org.thingsboard.mqtt.broker.common.stats;

import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pack-processing stats for the periodic stats log line — bundles the processing-time timer and the
 * running pack-size total for one consumer stream, shared by the publish-msg, device and
 * client-session-event consumer stats.
 *
 * <p>The pack count has a single source: the timer's sample count. One {@link #record} bumps both the
 * timer and the size total, so the average processing time and the average pack size can never
 * disagree on how many packs were seen. Both averages are log-only diagnostics — only the underlying
 * Micrometer timer is exported as a meter. {@link #getAvgPackSize()} returns 0 (not NaN) at idle
 * intervals where no pack was recorded, mirroring {@link ResettableTimer#getAvg()}; this matters for
 * sparse streams (e.g. client-session events split across several consumers) whose intervals are
 * often empty.
 */
public class PackProcessingStats {

    private final ResettableTimer processingTimer;
    private final AtomicLong totalPackSize = new AtomicLong();

    public PackProcessingStats(Timer processingTimer) {
        this.processingTimer = new ResettableTimer(processingTimer);
    }

    public void record(int packSize, long amount, TimeUnit unit) {
        processingTimer.logTime(amount, unit);
        totalPackSize.addAndGet(packSize);
    }

    public double getAvgProcessingTime() {
        return processingTimer.getAvg();
    }

    public double getAvgPackSize() {
        int count = processingTimer.getCount();
        return count == 0 ? 0 : Math.ceil((double) totalPackSize.get() / count);
    }

    public void reset() {
        processingTimer.reset();
        totalPackSize.set(0);
    }
}
