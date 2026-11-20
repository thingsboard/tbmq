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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.BackgroundErrorReason;
import org.rocksdb.RocksDB;
import org.rocksdb.Status;
import org.slf4j.LoggerFactory;
import org.thingsboard.mqtt.broker.dao.rocksdb.disk.DiskSpaceMonitor;
import org.thingsboard.mqtt.broker.dao.rocksdb.disk.FreeSpaceProbe;
import org.thingsboard.mqtt.broker.dao.store.StoreFullException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RocksDbStoreWatermarkTest {

    private static final long GIB = 1L << 30;
    private static final FreeSpaceProbe.Space FULL = new FreeSpaceProbe.Space(GIB / 1024, 100 * GIB);
    private static final FreeSpaceProbe.Space ROOMY = new FreeSpaceProbe.Space(50 * GIB, 100 * GIB);

    @TempDir
    Path dir;

    private final AtomicReference<FreeSpaceProbe.Space> space = new AtomicReference<>(ROOMY);
    private DiskSpaceMonitor monitor;
    private RocksDbStore store;
    private Logger monitorLogger;
    private ListAppender<ILoggingEvent> monitorLogs;

    @BeforeEach
    void open() {
        // the monitor warns on every full disk these tests make; keep it off the console
        monitorLogger = (Logger) LoggerFactory.getLogger(DiskSpaceMonitor.class);
        monitorLogs = new ListAppender<>();
        monitorLogs.start();
        monitorLogger.addAppender(monitorLogs);
        monitorLogger.setAdditive(false);
        StoreProperties properties = new StoreProperties();
        properties.setPath(dir.toString());
        monitor = new DiskSpaceMonitor(properties, path -> space.get());
        store = new RocksDbStore(properties, StoreMemoryBudget.forContainerMemory(1L << 30, 0, 0), monitor);
        store.open();
    }

    @AfterEach
    void close() {
        store.close();
        monitorLogger.detachAppender(monitorLogs);
        monitorLogger.setAdditive(true);
    }

    @Test
    void belowTheWatermarkNewDataIsRefusedAndNothingIsWritten() {
        store.put(StoreWriteClass.NEW_DATA, StoreColumnFamily.RETAINED_MSG, bytes("existing"), bytes("v1"));
        space.set(FULL);
        monitor.check();

        assertThatThrownBy(() -> store.put(StoreWriteClass.NEW_DATA, StoreColumnFamily.RETAINED_MSG, bytes("new"), bytes("v")))
                .isInstanceOf(StoreFullException.class)
                .hasMessage("Not enough free disk space for the store at " + dir + ": " + FULL.usableBytes()
                        + " bytes free, watermark " + 5 * GIB + " bytes");
        AtomicBoolean bodyRan = new AtomicBoolean();
        assertThatThrownBy(() -> store.write(StoreWriteClass.NEW_DATA, batch -> {
            bodyRan.set(true);
            batch.put(StoreColumnFamily.RETAINED_MSG, bytes("batched"), bytes("v"));
        })).isInstanceOf(StoreFullException.class);

        assertThat(bodyRan).isFalse();
        assertThat(store.get(StoreColumnFamily.RETAINED_MSG, bytes("new"))).isNull();
        assertThat(store.get(StoreColumnFamily.RETAINED_MSG, bytes("batched"))).isNull();
    }

    @Test
    void belowTheWatermarkUpdatesAndDeletesGoThrough() {
        store.put(StoreWriteClass.NEW_DATA, StoreColumnFamily.CLIENT_SESSION, bytes("a"), bytes("v1"));
        store.put(StoreWriteClass.NEW_DATA, StoreColumnFamily.CLIENT_SESSION, bytes("b"), bytes("v1"));
        store.put(StoreWriteClass.NEW_DATA, StoreColumnFamily.CLIENT_SESSION, bytes("c"), bytes("v1"));
        space.set(FULL);
        monitor.check();

        store.put(StoreWriteClass.UPDATE, StoreColumnFamily.CLIENT_SESSION, bytes("a"), bytes("v2"));
        store.delete(StoreWriteClass.DELETE, StoreColumnFamily.CLIENT_SESSION, bytes("b"));
        store.write(StoreWriteClass.UPDATE, batch -> batch.put(StoreColumnFamily.CLIENT_SESSION, bytes("c"), bytes("v2")));
        store.write(StoreWriteClass.DELETE, batch -> batch.delete(StoreColumnFamily.CLIENT_SESSION, bytes("c")));

        assertThat(store.get(StoreColumnFamily.CLIENT_SESSION, bytes("a"))).isEqualTo(bytes("v2"));
        assertThat(store.get(StoreColumnFamily.CLIENT_SESSION, bytes("b"))).isNull();
        assertThat(store.get(StoreColumnFamily.CLIENT_SESSION, bytes("c"))).isNull();
    }

    @Test
    void whenSpaceIsBackNewDataIsWrittenAgain() {
        space.set(FULL);
        monitor.check();
        space.set(ROOMY);
        monitor.check();

        store.put(StoreWriteClass.NEW_DATA, StoreColumnFamily.RETAINED_MSG, bytes("new"), bytes("v"));
        store.write(StoreWriteClass.NEW_DATA, batch -> batch.put(StoreColumnFamily.RETAINED_MSG, bytes("batched"), bytes("v")));

        assertThat(store.get(StoreColumnFamily.RETAINED_MSG, bytes("new"))).isEqualTo(bytes("v"));
        assertThat(store.get(StoreColumnFamily.RETAINED_MSG, bytes("batched"))).isEqualTo(bytes("v"));
    }

    @Test
    void theStoreRegistersItsResumeAtOpenSoABackgroundErrorClearsOnceTheStoreTakesWritesAgain() {
        monitor.reportBackgroundError("FLUSH: IOError(NoSpace) No space left on device");
        assertThat(monitor.degradedReason()).isPresent();

        monitor.check();

        assertThat(monitor.degradedReason()).isEmpty();
    }

    @Test
    void theResumeProbeLeavesTheMetaUnchanged() {
        List<String> before = metaEntries();

        assertThat(store.resumeAfterBackgroundError()).isTrue();

        assertThat(metaEntries()).isEqualTo(before);
        assertThat(store.get(StoreColumnFamily.META, RocksDbStore.META_RESUME_PROBE_KEY)).isNull();
    }

    @Test
    void theResumeProbeIsAnUpdateThatTheWatermarkNeverRefuses() {
        space.set(FULL);
        monitor.check();

        assertThat(store.resumeAfterBackgroundError()).isTrue();
    }

    @Test
    void theResumeProbeFailsWhenTheStoreDoesNotTakeWrites() {
        store.close();

        assertThat(store.resumeAfterBackgroundError()).isFalse();
    }

    @Test
    void theListenerReportsABackgroundErrorWithItsReasonAndStatus() {
        RocksDB.loadLibrary();
        List<String> reported = new ArrayList<>();
        try (StoreBackgroundErrorListener listener = new StoreBackgroundErrorListener(reported::add)) {
            listener.onBackgroundError(BackgroundErrorReason.FLUSH,
                    new Status(Status.Code.IOError, Status.SubCode.NoSpace, "No space left on device"));
            listener.onBackgroundError(BackgroundErrorReason.COMPACTION,
                    new Status(Status.Code.Corruption, Status.SubCode.None, null));
        }

        assertThat(reported).containsExactly("FLUSH: IOError(NoSpace) No space left on device", "COMPACTION: Corruption");
    }

    private List<String> metaEntries() {
        return store.scan(StoreColumnFamily.META, null, null, iterator -> {
            List<String> entries = new ArrayList<>();
            for (; iterator.isValid(); iterator.next()) {
                entries.add(new String(iterator.key(), StandardCharsets.UTF_8) + "=" + new String(iterator.value(), StandardCharsets.ISO_8859_1));
            }
            return entries;
        });
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

}
