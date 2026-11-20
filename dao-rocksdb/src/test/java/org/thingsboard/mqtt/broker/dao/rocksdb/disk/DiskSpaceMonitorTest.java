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
package org.thingsboard.mqtt.broker.dao.rocksdb.disk;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreProperties;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiskSpaceMonitorTest {

    private static final long MIB = 1L << 20;
    private static final long GIB = 1L << 30;
    private static final String PATH = "/data/db";

    private final AtomicReference<FreeSpaceProbe.Space> space =
            new AtomicReference<>(new FreeSpaceProbe.Space(50 * GIB, 100 * GIB));
    private DiskSpaceMonitor monitor;

    @AfterEach
    void stop() {
        if (monitor != null) {
            monitor.stop();
        }
    }

    @Test
    void theWatermarkIsTheLargerOfThePercentOfTheVolumeAndTheMinimumBytes() {
        // 5% of 100 GiB = 5 GiB beats 256 MiB
        assertThat(DiskSpaceMonitor.watermark(100 * GIB, 5, 256 * MIB)).isEqualTo(5 * GIB);
        // 5% of 2 GiB = 102.4 MiB loses to 256 MiB
        assertThat(DiskSpaceMonitor.watermark(2 * GIB, 5, 256 * MIB)).isEqualTo(256 * MIB);
        assertThat(DiskSpaceMonitor.watermark(Long.MAX_VALUE, 100, 0)).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void beforeTheFirstCheckTheStoreIsNotBelowTheWatermark() {
        monitor = monitor(properties());

        assertThat(monitor.belowWatermark()).isFalse();
        assertThat(monitor.degradedReason()).isEmpty();
    }

    @Test
    void aCheckBelowTheWatermarkDegradesAndOneAboveItRecovers() {
        monitor = monitor(properties());
        space.set(new FreeSpaceProbe.Space(5 * GIB - 1, 100 * GIB));

        monitor.check();

        assertThat(monitor.belowWatermark()).isTrue();
        assertThat(monitor.degradedReason()).hasValue("Not enough free disk space for the store at " + PATH
                + ": " + (5 * GIB - 1) + " bytes free, watermark " + 5 * GIB + " bytes");
        assertThat(monitor.storeFullException()).hasMessage("Not enough free disk space for the store at " + PATH
                + ": " + (5 * GIB - 1) + " bytes free, watermark " + 5 * GIB + " bytes");

        space.set(new FreeSpaceProbe.Space(5 * GIB, 100 * GIB));
        monitor.check();

        assertThat(monitor.belowWatermark()).isFalse();
        assertThat(monitor.degradedReason()).isEmpty();
    }

    @Test
    void theMinimumBytesApplyOnASmallVolume() {
        monitor = monitor(properties());
        space.set(new FreeSpaceProbe.Space(200 * MIB, 2 * GIB));

        monitor.check();

        assertThat(monitor.belowWatermark()).isTrue();
    }

    @Test
    void aBackgroundErrorDegradesUntilACheckSeesSpaceAndTheRecoveryCallbackSucceeds() {
        monitor = monitor(properties());
        Deque<Boolean> results = new ArrayDeque<>(java.util.List.of(false, true));
        AtomicInteger calls = new AtomicInteger();
        monitor.onRecovery(() -> {
            calls.incrementAndGet();
            return results.removeFirst();
        });

        monitor.reportBackgroundError("FLUSH: IOError(NoSpace) No space left on device");

        assertThat(monitor.belowWatermark()).isFalse();
        assertThat(monitor.degradedReason()).hasValue("The store reported a background error: FLUSH: IOError(NoSpace) No space left on device");

        // no space yet: the callback is not tried
        space.set(new FreeSpaceProbe.Space(MIB, 100 * GIB));
        monitor.check();
        assertThat(calls).hasValue(0);
        assertThat(monitor.degradedReason()).get().asString()
                .startsWith("Not enough free disk space")
                .contains("background error: FLUSH");

        // space back, but the store does not accept writes yet
        space.set(new FreeSpaceProbe.Space(50 * GIB, 100 * GIB));
        monitor.check();
        assertThat(calls).hasValue(1);
        assertThat(monitor.degradedReason()).hasValue("The store reported a background error: FLUSH: IOError(NoSpace) No space left on device");

        // tried again on the next check
        monitor.check();
        assertThat(calls).hasValue(2);
        assertThat(monitor.degradedReason()).isEmpty();

        // nothing to recover from any more
        monitor.check();
        assertThat(calls).hasValue(2);
    }

    @Test
    void aRecoveryCallbackThatThrowsKeepsTheStoreDegraded() {
        monitor = monitor(properties());
        monitor.onRecovery(() -> {
            throw new IllegalStateException("boom");
        });
        monitor.reportBackgroundError("COMPACTION: IOError");

        assertThatCode(monitor::check).doesNotThrowAnyException();

        assertThat(monitor.degradedReason()).isPresent();
    }

    @Test
    void aBackgroundErrorWithoutARecoveryCallbackStaysDegraded() {
        monitor = monitor(properties());
        monitor.reportBackgroundError("FLUSH: IOError");

        monitor.check();

        assertThat(monitor.degradedReason()).isPresent();
    }

    @Test
    void aFailingProbeIsLoggedAndKeepsTheLastReading() {
        AtomicReference<IOException> failure = new AtomicReference<>();
        monitor = new DiskSpaceMonitor(properties(), path -> {
            if (failure.get() != null) {
                throw failure.get();
            }
            return space.get();
        });
        space.set(new FreeSpaceProbe.Space(MIB, 100 * GIB));
        monitor.check();
        failure.set(new IOException("no such volume"));

        assertThatCode(monitor::check).doesNotThrowAnyException();

        assertThat(monitor.belowWatermark()).isTrue();
    }

    @Test
    void theProbeGetsTheStorePath() {
        AtomicReference<String> probed = new AtomicReference<>();
        monitor = new DiskSpaceMonitor(properties(), path -> {
            probed.set(path.toString());
            return space.get();
        });

        monitor.check();

        assertThat(probed).hasValue(PATH);
    }

    @Test
    void startChecksAtOnceAndAProbeFailureDoesNotStopTheSchedule() throws InterruptedException {
        StoreProperties properties = properties();
        properties.getDisk().setCheckIntervalMs(10);
        AtomicInteger probes = new AtomicInteger();
        CountDownLatch belowAfterFailure = new CountDownLatch(1);
        monitor = new DiskSpaceMonitor(properties, path -> {
            int n = probes.incrementAndGet();
            if (n == 2) {
                throw new IOException("transient");
            }
            if (n >= 3) {
                belowAfterFailure.countDown();
                return new FreeSpaceProbe.Space(MIB, 100 * GIB);
            }
            return space.get();
        });

        monitor.start();

        assertThat(probes.get()).isGreaterThanOrEqualTo(1);
        assertThat(belowAfterFailure.await(5, TimeUnit.SECONDS)).isTrue();
        monitor.stop();
        int afterStop = probes.get();
        Thread.sleep(50);
        assertThat(probes.get()).isLessThanOrEqualTo(afterStop + 1);
    }

    @Test
    void startRefusesInvalidSettings() {
        StoreProperties interval = properties();
        interval.getDisk().setCheckIntervalMs(0);
        assertThatThrownBy(() -> monitor(interval).start())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("store.disk.check-interval-ms");

        StoreProperties percent = properties();
        percent.getDisk().setMinFreePercent(101);
        assertThatThrownBy(() -> monitor(percent).start())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("store.disk.min-free-percent");

        StoreProperties bytes = properties();
        bytes.getDisk().setMinFreeBytes(-1);
        assertThatThrownBy(() -> monitor(bytes).start())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("store.disk.min-free-bytes");
    }

    @Test
    void theDefaultsMatchTheSettings() {
        StoreProperties.Disk disk = new StoreProperties().getDisk();

        assertThat(disk.getCheckIntervalMs()).isEqualTo(10000);
        assertThat(disk.getMinFreePercent()).isEqualTo(5);
        assertThat(disk.getMinFreeBytes()).isEqualTo(268435456L);
    }

    private DiskSpaceMonitor monitor(StoreProperties properties) {
        return new DiskSpaceMonitor(properties, path -> space.get());
    }

    private static StoreProperties properties() {
        StoreProperties properties = new StoreProperties();
        properties.setPath(PATH);
        return properties;
    }

}
