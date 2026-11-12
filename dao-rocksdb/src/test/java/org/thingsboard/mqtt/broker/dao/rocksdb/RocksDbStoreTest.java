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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.RocksIterator;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatch;
import org.thingsboard.mqtt.broker.dao.store.StoreException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class RocksDbStoreTest {

    @TempDir
    Path dir;

    private final List<RocksDbStore> opened = new ArrayList<>();

    @AfterEach
    void closeAll() {
        opened.forEach(RocksDbStore::close);
        opened.clear();
    }

    @Test
    void firstOpenWritesMetaAndASecondOpenReadsTheSameInstallId() {
        UUID id = open(dir).meta().installId();
        closeAll();

        assertThat(open(dir).meta()).isEqualTo(new StoreMeta(1, "LITE", id));
    }

    @Test
    void allEightColumnFamiliesExistAfterOpen() {
        RocksDbStore store = open(dir);

        assertThat(StoreColumnFamily.values()).hasSize(8);
        for (StoreColumnFamily cf : StoreColumnFamily.values()) {
            byte[] key = bytes("key-" + cf.name());
            store.put(cf, key, bytes("value-" + cf.name()));
            assertThat(store.get(cf, key)).isEqualTo(bytes("value-" + cf.name()));
        }
    }

    @Test
    void aSecondStoreOnTheSamePathFailsWithAMessageThatNamesThePath() {
        RocksDbStore first = open(dir);

        assertThatThrownBy(() -> open(dir))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining(dir.toString());

        first.put(StoreColumnFamily.RETAINED_MSG, bytes("k"), bytes("v"));
        assertThat(first.get(StoreColumnFamily.RETAINED_MSG, bytes("k"))).isEqualTo(bytes("v"));
    }

    @Test
    void aPathWithoutWritePermissionFailsWithAMessageThatNamesThePath() throws Exception {
        assertThat(dir.toFile().setWritable(false)).isTrue();
        try {
            assumeFalse(Files.isWritable(dir), "running as a user that ignores directory permissions");

            assertThatThrownBy(() -> open(dir.resolve("db")))
                    .isInstanceOf(StoreException.class)
                    .hasMessageContaining(dir.toString());
        } finally {
            dir.toFile().setWritable(true);
        }
    }

    @Test
    void aStoreOfAnotherProductIsRefused() {
        open(dir).put(StoreColumnFamily.META, RocksDbStore.META_PRODUCT_KEY, bytes("X"));
        closeAll();

        assertThatThrownBy(() -> open(dir))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("product 'X'")
                .hasMessageContaining(dir.toString());
    }

    @Test
    void aStoreWithAHigherSchemaVersionIsRefused() {
        open(dir).put(StoreColumnFamily.META, RocksDbStore.META_SCHEMA_VERSION_KEY, new byte[]{0, 0, 0, 2});
        closeAll();

        assertThatThrownBy(() -> open(dir))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("schema version 2")
                .hasMessageContaining(dir.toString());
    }

    @Test
    void aRefusedOpenReleasesTheLock() {
        open(dir).put(StoreColumnFamily.META, RocksDbStore.META_PRODUCT_KEY, bytes("X"));
        closeAll();
        assertThatThrownBy(() -> open(dir)).hasMessageContaining("product 'X'");

        // a second refusal for the same reason, not "lock held by the first attempt"
        assertThatThrownBy(() -> open(dir)).hasMessageContaining("product 'X'");
    }

    @Test
    void writesAreReadableAfterCloseAndReopen() {
        RocksDbStore store = open(dir);
        store.put(StoreColumnFamily.CLIENT_SESSION, bytes("c1"), bytes("session"));
        closeAll();

        assertThat(open(dir).get(StoreColumnFamily.CLIENT_SESSION, bytes("c1"))).isEqualTo(bytes("session"));
    }

    @Test
    void deleteRemovesTheKeyAndABatchIsApplied() throws RocksDBException {
        RocksDbStore store = open(dir);
        store.put(StoreColumnFamily.BLOCKED_CLIENT, bytes("a"), bytes("1"));
        try (WriteBatch batch = new WriteBatch()) {
            batch.put(store.handle(StoreColumnFamily.BLOCKED_CLIENT), bytes("b"), bytes("2"));
            batch.delete(store.handle(StoreColumnFamily.BLOCKED_CLIENT), bytes("a"));
            store.write(batch);
        }

        assertThat(store.get(StoreColumnFamily.BLOCKED_CLIENT, bytes("a"))).isNull();
        store.delete(StoreColumnFamily.BLOCKED_CLIENT, bytes("b"));
        assertThat(store.get(StoreColumnFamily.BLOCKED_CLIENT, bytes("b"))).isNull();
    }

    @Test
    void anIteratorWalksOneColumnFamilyInKeyOrder() {
        RocksDbStore store = open(dir);
        store.put(StoreColumnFamily.RETAINED_MSG, bytes("b"), bytes("2"));
        store.put(StoreColumnFamily.RETAINED_MSG, bytes("a"), bytes("1"));
        store.put(StoreColumnFamily.CLIENT_SESSION, bytes("c"), bytes("3"));

        List<String> keys = new ArrayList<>();
        try (RocksIterator it = store.newIterator(StoreColumnFamily.RETAINED_MSG)) {
            for (it.seekToFirst(); it.isValid(); it.next()) {
                keys.add(new String(it.key(), StandardCharsets.UTF_8));
            }
        }

        assertThat(keys).containsExactly("a", "b");
    }

    @Test
    void syncWalSucceedsOnAnOpenStore() {
        RocksDbStore store = open(dir);
        store.put(StoreColumnFamily.RETAINED_MSG, bytes("a"), bytes("1"));

        assertThatCode(store::syncWal).doesNotThrowAnyException();
    }

    @Test
    void closeIsIdempotentAndAClosedStoreRefusesOperations() {
        RocksDbStore store = open(dir);

        store.close();

        assertThatCode(store::close).doesNotThrowAnyException();
        assertThatThrownBy(() -> store.get(StoreColumnFamily.META, bytes("k")))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining(dir.toString());
    }

    @Test
    void closingWhileOtherThreadsReadAndWriteOnlyEverSurfacesAStoreException() throws Exception {
        int threads = 8;
        for (int round = 0; round < 5; round++) {
            RocksDbStore store = open(dir.resolve("round-" + round));
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch working = new CountDownLatch(threads);
            AtomicInteger refused = new AtomicInteger();
            List<Future<Throwable>> results = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int id = t;
                results.add(executor.submit(() -> {
                    working.countDown();
                    try {
                        for (long i = 0; ; i++) {
                            byte[] key = bytes("k-" + id + "-" + (i % 1000));
                            store.put(StoreColumnFamily.CLIENT_SESSION, key, bytes("v" + i));
                            store.get(StoreColumnFamily.CLIENT_SESSION, key);
                            store.delete(StoreColumnFamily.CLIENT_SESSION, key);
                        }
                    } catch (StoreException e) {
                        refused.incrementAndGet();
                        return null;
                    } catch (Throwable e) {
                        return e;
                    }
                }));
            }
            assertThat(working.await(10, TimeUnit.SECONDS)).isTrue();
            TimeUnit.MILLISECONDS.sleep(50);

            store.close();

            for (Future<Throwable> result : results) {
                assertThat(result.get(10, TimeUnit.SECONDS)).isNull();
            }
            assertThat(refused.get()).isEqualTo(threads);
            executor.shutdown();
        }
    }

    private RocksDbStore open(Path path) {
        StoreProperties properties = new StoreProperties();
        properties.setPath(path.toString());
        RocksDbStore store = new RocksDbStore(properties, StoreMemoryBudget.forContainerMemory(1L << 30, 0, 0));
        opened.add(store);
        store.open();
        return store;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

}
