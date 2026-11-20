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
package org.thingsboard.mqtt.broker.dao.rocksdb.timeseries;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.stats.DefaultCounter;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsType;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.store.StoreFullException;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Skips the stats writes that a full store refuses: a stats write must not fail because the disk is full. Each skipped
 * write is counted in {@code diskFullSkippedStatsWrites} and logged at WARN at most once a minute. Any other failure of
 * the write is thrown as it is.
 */
@Slf4j
@Component
@StandaloneComponent
public class StatsWriteSkipper {

    private static final long WARN_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(1);

    private final DefaultCounter skippedWrites;
    private final LongSupplier nanoClock;
    private final AtomicLong skippedSinceWarn = new AtomicLong();
    private final AtomicLong nextWarnNanos;

    @Autowired
    public StatsWriteSkipper(StatsFactory statsFactory) {
        this(statsFactory, System::nanoTime);
    }

    // the clock is a seam for the test of the warning rate
    StatsWriteSkipper(StatsFactory statsFactory, LongSupplier nanoClock) {
        this.skippedWrites = statsFactory.createDefaultCounter(StatsType.DISK_FULL_SKIPPED_STATS_WRITES.getPrintName());
        this.nanoClock = nanoClock;
        this.nextWarnNanos = new AtomicLong(nanoClock.getAsLong());
    }

    /**
     * Runs the write.
     *
     * @return {@code false} when the store refused the write as full: it is skipped and counted
     */
    public boolean write(Runnable write) {
        try {
            write.run();
            return true;
        } catch (StoreFullException e) {
            skippedWrites.increment();
            long skipped = skippedSinceWarn.incrementAndGet();
            long now = nanoClock.getAsLong();
            long next = nextWarnNanos.get();
            if (now - next >= 0 && nextWarnNanos.compareAndSet(next, now + WARN_INTERVAL_NANOS)) {
                skippedSinceWarn.addAndGet(-skipped);
                log.warn("The store is full: skipped {} stats writes since the last warning. {}", skipped, e.getMessage());
            }
            return false;
        }
    }

}
