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
package org.thingsboard.mqtt.broker.dao.rocksdb.client;

import com.google.common.util.concurrent.ListenableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.thingsboard.mqtt.broker.common.data.UnauthorizedClient;
import org.thingsboard.mqtt.broker.common.data.client.unauthorized.UnauthorizedClientQuery;
import org.thingsboard.mqtt.broker.common.data.page.TimePageLink;
import org.thingsboard.mqtt.broker.dao.rocksdb.RecordingRocksDbStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreMemoryBudget;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreProperties;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreWriteClass;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityDescriptors;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.RocksEntityStore;
import org.thingsboard.mqtt.broker.dao.store.StoreException;
import org.thingsboard.mqtt.broker.dao.store.StoreFullException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RocksDbUnauthorizedClientDaoTest {

    @TempDir
    Path dir;

    private RecordingRocksDbStore rocksDbStore;
    private RocksEntityStore store;

    @BeforeEach
    void open() {
        StoreProperties properties = new StoreProperties();
        properties.setPath(dir.toString());
        rocksDbStore = new RecordingRocksDbStore(properties, StoreMemoryBudget.forContainerMemory(1L << 30, 0, 0));
        rocksDbStore.open();
        store = new RocksEntityStore(rocksDbStore);
        store.buildProjections();
    }

    @AfterEach
    void close() {
        rocksDbStore.close();
    }

    @Test
    void aStoreFailureOnSaveIsAFailedFutureNotAThrow() {
        RocksDbUnauthorizedClientDao dao = new RocksDbUnauthorizedClientDao(store, 10);
        StoreFullException full = new StoreFullException("no room");
        rocksDbStore.failNextWrite(full);

        AtomicReference<ListenableFuture<Void>> save = new AtomicReference<>();
        assertThatCode(() -> save.set(dao.save(client("a", 1)))).doesNotThrowAnyException();

        assertThat(save.get()).isDone();
        assertThatThrownBy(() -> save.get().get()).isInstanceOf(ExecutionException.class).hasCause(full);
        assertThat(dao.findByClientId("a")).isNull();
        // the order was left as it was: the client fits after the failure, and the limit still holds
        assertThat(dao.save(client("a", 1))).succeedsWithin(0, TimeUnit.SECONDS);
        assertThat(store.count(EntityDescriptors.UNAUTHORIZED_CLIENT)).isEqualTo(1);
    }

    @Test
    void aStoreFailureOnAnEvictingSaveKeepsBothTheEvictedClientAndTheOrder() throws Exception {
        RocksDbUnauthorizedClientDao dao = new RocksDbUnauthorizedClientDao(store, 2);
        dao.save(client("a", 10)).get();
        dao.save(client("b", 20)).get();
        rocksDbStore.failNextWrite(new StoreException("write failed"));

        assertThat(dao.save(client("c", 30))).failsWithin(0, TimeUnit.SECONDS);

        assertThat(clientIds()).containsExactlyInAnyOrder("a", "b");
        dao.save(client("c", 30)).get();
        assertThat(clientIds()).containsExactlyInAnyOrder("b", "c");
    }

    @Test
    void aClosedStoreFailsTheFuturesOfSaveAndRemove() {
        RocksDbUnauthorizedClientDao dao = new RocksDbUnauthorizedClientDao(store, 10);
        rocksDbStore.close();

        AtomicReference<ListenableFuture<Void>> save = new AtomicReference<>();
        AtomicReference<ListenableFuture<Void>> remove = new AtomicReference<>();
        assertThatCode(() -> {
            save.set(dao.save(client("a", 1)));
            remove.set(dao.remove(client("a", 1)));
        }).doesNotThrowAnyException();

        assertThatThrownBy(() -> save.get().get()).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(StoreException.class);
        assertThatThrownBy(() -> remove.get().get()).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(StoreException.class);
    }

    @Test
    void aSaveWithoutAClientIdIsAFailedFuture() {
        RocksDbUnauthorizedClientDao dao = new RocksDbUnauthorizedClientDao(store, 10);

        assertThat(dao.save(client(null, 1))).failsWithin(0, TimeUnit.SECONDS);
        assertThat(dao.save(client("a", 1))).succeedsWithin(0, TimeUnit.SECONDS);
    }

    @Test
    void aNewClientAtTheLimitEvictsTheOldestInTheSameBatch() throws Exception {
        RocksDbUnauthorizedClientDao dao = new RocksDbUnauthorizedClientDao(store, 3);
        dao.save(client("a", 10)).get();
        dao.save(client("b", 30)).get();
        dao.save(client("c", 20)).get();
        rocksDbStore.takeWriteClasses();

        dao.save(client("d", 40)).get();

        assertThat(rocksDbStore.takeWriteClasses()).containsExactly(StoreWriteClass.NEW_DATA);
        assertThat(clientIds()).containsExactlyInAnyOrder("b", "c", "d");
        assertThat(store.count(EntityDescriptors.UNAUTHORIZED_CLIENT)).isEqualTo(3);
    }

    @Test
    void ofClientsWithTheSameTsTheLowestClientIdIsEvictedFirst() throws Exception {
        RocksDbUnauthorizedClientDao dao = new RocksDbUnauthorizedClientDao(store, 2);
        dao.save(client("b", 10)).get();
        dao.save(client("a", 10)).get();

        dao.save(client("c", 10)).get();
        assertThat(clientIds()).containsExactlyInAnyOrder("b", "c");

        dao.save(client("d", 10)).get();
        assertThat(clientIds()).containsExactlyInAnyOrder("c", "d");
    }

    @Test
    void aNewClientOlderThanEveryKeptOneIsNotKept() throws Exception {
        RocksDbUnauthorizedClientDao dao = new RocksDbUnauthorizedClientDao(store, 2);
        dao.save(client("a", 10)).get();
        dao.save(client("b", 20)).get();
        rocksDbStore.takeWriteClasses();

        dao.save(client("z", 5)).get();

        assertThat(rocksDbStore.takeWriteClasses()).isEmpty();
        assertThat(clientIds()).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void aStoreOverTheLimitAtStartupIsTrimmedByTheNextNewClientInOneBatch() throws Exception {
        RocksDbUnauthorizedClientDao large = new RocksDbUnauthorizedClientDao(store, 10);
        for (int i = 1; i <= 5; i++) {
            large.save(client("c" + i, i * 10)).get();
        }
        RocksDbUnauthorizedClientDao small = new RocksDbUnauthorizedClientDao(store, 2);
        rocksDbStore.takeWriteClasses();

        small.save(client("c3", 35)).get();
        assertThat(store.count(EntityDescriptors.UNAUTHORIZED_CLIENT)).as("an update does not trim").isEqualTo(5);

        small.save(client("new", 45)).get();

        assertThat(rocksDbStore.takeWriteClasses()).containsExactly(StoreWriteClass.NEW_DATA, StoreWriteClass.NEW_DATA);
        assertThat(clientIds()).containsExactlyInAnyOrder("c5", "new");
    }

    @Test
    void aNewClientOlderThanEveryKeptOneIsDroppedWhileTheStoreIsTrimmed() throws Exception {
        RocksDbUnauthorizedClientDao large = new RocksDbUnauthorizedClientDao(store, 10);
        for (int i = 1; i <= 5; i++) {
            large.save(client("c" + i, i * 10)).get();
        }
        RocksDbUnauthorizedClientDao small = new RocksDbUnauthorizedClientDao(store, 2);
        rocksDbStore.takeWriteClasses();

        small.save(client("old", 1)).get();

        assertThat(rocksDbStore.takeWriteClasses()).containsExactly(StoreWriteClass.DELETE);
        assertThat(clientIds()).containsExactlyInAnyOrder("c4", "c5");
    }

    @Test
    void removalsFreeRoomUnderTheLimit() throws Exception {
        RocksDbUnauthorizedClientDao dao = new RocksDbUnauthorizedClientDao(store, 2);
        dao.save(client("a", 10)).get();
        dao.save(client("b", 20)).get();

        dao.remove(client("a", 10)).get();
        dao.save(client("c", 5)).get();
        assertThat(clientIds()).containsExactlyInAnyOrder("b", "c");

        dao.deleteById("b");
        dao.save(client("d", 1)).get();
        assertThat(clientIds()).containsExactlyInAnyOrder("c", "d");

        dao.remove(client("missing", 1)).get();
        dao.deleteById("missing");
        assertThat(clientIds()).containsExactlyInAnyOrder("c", "d");
    }

    @Test
    void aCleanupRemovesTheExpiredClientsInOneDeleteBatchAndFreesTheirRoom() throws Exception {
        long now = System.currentTimeMillis();
        RocksDbUnauthorizedClientDao dao = new RocksDbUnauthorizedClientDao(store, 3);
        dao.save(client("old-1", now - TimeUnit.HOURS.toMillis(3))).get();
        dao.save(client("old-2", now - TimeUnit.HOURS.toMillis(2))).get();
        dao.save(client("fresh", now - TimeUnit.MINUTES.toMillis(10))).get();
        rocksDbStore.takeWriteClasses();

        dao.cleanupUnauthorizedClients(TimeUnit.HOURS.toSeconds(1));

        assertThat(rocksDbStore.takeWriteClasses()).containsExactly(StoreWriteClass.DELETE);
        assertThat(clientIds()).containsExactly("fresh");
        dao.save(client("x", 1)).get();
        dao.save(client("y", 2)).get();
        assertThat(clientIds()).containsExactlyInAnyOrder("fresh", "x", "y");

        rocksDbStore.takeWriteClasses();
        dao.cleanupUnauthorizedClients(TimeUnit.DAYS.toSeconds(1) * 365 * 100);
        assertThat(rocksDbStore.takeWriteClasses()).as("nothing expired, nothing written").isEmpty();
    }

    @Test
    void deleteAllIsOneDeleteBatchAndFreesTheWholeLimit() throws Exception {
        RocksDbUnauthorizedClientDao dao = new RocksDbUnauthorizedClientDao(store, 3);
        for (int i = 0; i < 3; i++) {
            dao.save(client("c" + i, i)).get();
        }
        rocksDbStore.takeWriteClasses();

        dao.deleteAll();

        assertThat(rocksDbStore.takeWriteClasses()).containsExactly(StoreWriteClass.DELETE);
        assertThat(store.count(EntityDescriptors.UNAUTHORIZED_CLIENT)).isZero();
        for (int i = 0; i < 3; i++) {
            dao.save(client("d" + i, -i)).get();
        }
        assertThat(clientIds()).containsExactlyInAnyOrder("d0", "d1", "d2");
    }

    @Test
    void concurrentNewClientsAtTheLimitNeverLeaveMoreThanIt() throws Exception {
        int max = 50;
        RocksDbUnauthorizedClientDao dao = new RocksDbUnauthorizedClientDao(store, max);
        int threads = 8;
        int perThread = 500;
        runConcurrently(threads, t -> {
            for (int i = 0; i < perThread; i++) {
                int n = i * threads + t;
                dao.save(client(String.format("c%05d", n), n)).get();
                assertThat(store.count(EntityDescriptors.UNAUTHORIZED_CLIENT)).isLessThanOrEqualTo(max);
            }
        });

        Set<String> newest = IntStream.range(threads * perThread - max, threads * perThread)
                .mapToObj(n -> String.format("c%05d", n)).collect(Collectors.toSet());
        assertThat(clientIds()).isEqualTo(newest);
    }

    @Test
    void concurrentSavesAndRemovesKeepTheOrderInStepWithTheStore() throws Exception {
        int max = 20;
        RocksDbUnauthorizedClientDao dao = new RocksDbUnauthorizedClientDao(store, max);
        runConcurrently(4, t -> {
            for (int i = 0; i < 1_000; i++) {
                String clientId = "c" + (i % 40);
                switch ((i + t) % 4) {
                    case 0, 1 -> dao.save(client(clientId, i * 4L + t)).get();
                    case 2 -> dao.remove(client(clientId, 0)).get();
                    default -> dao.deleteById(clientId);
                }
                assertThat(store.count(EntityDescriptors.UNAUTHORIZED_CLIENT)).isLessThanOrEqualTo(max);
            }
        });

        // an order out of step with the store would evict too few clients (one it does not know is never evicted)
        // or too many (one it still holds counts towards the limit)
        for (int i = 0; i < max; i++) {
            dao.save(client("fill-" + i, Long.MAX_VALUE - max + i)).get();
        }
        assertThat(clientIds()).hasSize(max).allMatch(id -> id.startsWith("fill-"));
        dao.deleteAll();
        assertThat(store.findAll(EntityDescriptors.UNAUTHORIZED_CLIENT)).isEmpty();
    }

    @Test
    void theQueryFilterLeavesOutWhatTheSqlQueryLeavesOut() throws Exception {
        RocksDbUnauthorizedClientDao dao = new RocksDbUnauthorizedClientDao(store, 10);
        // saved through the DAO, below the service's validation: rows the SQL table's NOT NULL columns would refuse
        dao.save(UnauthorizedClient.builder().clientId("no-ip").reason("r").ts(1L).build()).get();
        dao.save(UnauthorizedClient.builder().clientId("no-reason").ipAddress("1.1.1.1").ts(1L).build()).get();
        dao.save(UnauthorizedClient.builder().clientId("no-ts").ipAddress("1.1.1.1").reason("r").build()).get();

        UnauthorizedClientQuery query = UnauthorizedClientQuery.builder().pageLink(new TimePageLink(10)).build();
        assertThat(dao.findAll(query).getData()).extracting(UnauthorizedClient::getClientId).containsExactly("no-ts");
        query.setPageLink(new TimePageLink(10, 0, null, null, 0L, null));
        assertThat(dao.findAll(query).getData()).isEmpty();
    }

    private Set<String> clientIds() {
        return store.findAll(EntityDescriptors.UNAUTHORIZED_CLIENT).stream().map(UnauthorizedClient::getClientId)
                .collect(Collectors.toSet());
    }

    private static UnauthorizedClient client(String clientId, long ts) {
        return UnauthorizedClient.builder().clientId(clientId).ipAddress("10.0.0.1").reason("Bad credentials").ts(ts).build();
    }

    private interface Body {
        void run(int thread) throws Exception;
    }

    private static void runConcurrently(int threads, Body body) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int thread = t;
                futures.add(executor.submit(() -> {
                    start.await();
                    body.run(thread);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }

}
