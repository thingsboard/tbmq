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
package org.thingsboard.mqtt.broker.dao.rocksdb;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.dao.store.StoreException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class WalSyncerTest {

    private RocksDbStore store;
    private StoreProperties properties;
    private WalSyncer syncer;

    @BeforeEach
    void setUp() {
        store = mock(RocksDbStore.class);
        properties = new StoreProperties();
        syncer = new WalSyncer(store, properties);
    }

    @AfterEach
    void tearDown() {
        syncer.stop();
    }

    @Test
    void periodicModeSyncsAtTheInterval() {
        properties.setWalSyncIntervalMs(50);
        syncer.start();

        verify(store, timeout(1000).atLeast(3)).syncWal();
    }

    @Test
    void periodicModeRunsOnADaemonThreadNamedStoreWalSyncer() throws InterruptedException {
        AtomicReference<Thread> thread = new AtomicReference<>();
        CountDownLatch synced = new CountDownLatch(1);
        doAnswer(invocation -> {
            thread.set(Thread.currentThread());
            synced.countDown();
            return null;
        }).when(store).syncWal();
        properties.setWalSyncIntervalMs(50);
        syncer.start();

        assertThat(synced.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(thread.get().getName()).isEqualTo("store-wal-syncer");
        assertThat(thread.get().isDaemon()).isTrue();
    }

    @Test
    void alwaysModeStartsNoThread() {
        properties.setWalSync("always");
        syncer.start();

        verify(store, after(200).never()).syncWal();
    }

    @Test
    void aFailedSyncDoesNotStopTheSyncerAndIsCounted() {
        doThrow(new StoreException("sync")).doNothing().when(store).syncWal();
        properties.setWalSyncIntervalMs(50);
        syncer.start();

        verify(store, timeout(1000).atLeast(3)).syncWal();
        assertThat(syncer.getFailedSyncCount()).isEqualTo(1);
    }

    @Test
    void anUnexpectedErrorDoesNotStopTheSyncerEither() {
        doThrow(new IllegalStateException("boom")).doNothing().when(store).syncWal();
        properties.setWalSyncIntervalMs(50);
        syncer.start();

        verify(store, timeout(1000).atLeast(3)).syncWal();
        assertThat(syncer.getFailedSyncCount()).isEqualTo(1);
    }

    @Test
    void anUnknownModeFailsStartup() {
        properties.setWalSync("sometimes");

        assertThatThrownBy(syncer::start).hasMessageContaining("periodic").hasMessageContaining("always");
    }

    @Test
    void aMissingModeFailsStartup() {
        properties.setWalSync(null);

        assertThatThrownBy(syncer::start).hasMessageContaining("periodic").hasMessageContaining("always");
    }

    @Test
    void aNonPositiveIntervalFailsStartupInPeriodicMode() {
        properties.setWalSyncIntervalMs(0);

        assertThatThrownBy(syncer::start).hasMessageContaining("wal-sync-interval-ms");
    }

    @Test
    void stopEndsTheThread() {
        properties.setWalSyncIntervalMs(50);
        syncer.start();
        verify(store, timeout(1000).atLeast(1)).syncWal();

        syncer.stop();
        clearInvocations(store);

        verify(store, after(200).never()).syncWal();
    }

    @Test
    void stopWithoutStartAndTwiceIsHarmless() {
        syncer.stop();
        syncer.stop();
    }

    @Test
    void aFailureCausedByStoppingIsNotCounted() throws InterruptedException {
        CountDownLatch inSync = new CountDownLatch(1);
        doAnswer(invocation -> {
            inSync.countDown();
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                throw new StoreException("interrupted while the store was closing");
            }
            return null;
        }).when(store).syncWal();
        properties.setWalSyncIntervalMs(50);
        syncer.start();
        assertThat(inSync.await(1, TimeUnit.SECONDS)).isTrue();

        syncer.stop();

        assertThat(syncer.getFailedSyncCount()).isZero();
    }

}
