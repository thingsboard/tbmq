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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Running average of processed pack sizes for the periodic stats log line, shared by the publish-msg,
 * device and client-session-event consumer stats (which each pair it with a {@link ResettableTimer}
 * for the pack processing time).
 *
 * <p>This is a log-only diagnostic and is deliberately NOT exported as a Micrometer meter — only the
 * pack processing <i>time</i> is. {@link #getAvg()} returns 0 (not NaN) at idle intervals where no
 * pack was recorded, mirroring {@link ResettableTimer#getAvg()}; this matters for sparse streams
 * (e.g. client-session events split across several consumers) whose intervals are often empty.
 */
public class PackSizeStats {

    private final AtomicLong totalPackSize = new AtomicLong();
    private final AtomicInteger packCount = new AtomicInteger();

    public void record(int packSize) {
        totalPackSize.addAndGet(packSize);
        packCount.incrementAndGet();
    }

    public double getAvg() {
        int count = packCount.get();
        return count == 0 ? 0 : Math.ceil((double) totalPackSize.get() / count);
    }

    public void reset() {
        totalPackSize.set(0);
        packCount.set(0);
    }
}
