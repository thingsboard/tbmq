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
package org.thingsboard.mqtt.broker.dao.rocksdb.contract;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.support.AnnotationConfigContextLoader;
import org.thingsboard.mqtt.broker.common.data.UnauthorizedClient;
import org.thingsboard.mqtt.broker.dao.client.unauthorized.UnauthorizedClientServiceImpl;
import org.thingsboard.mqtt.broker.dao.contract.AbstractUnauthorizedClientContractTest;
import org.thingsboard.mqtt.broker.dao.rocksdb.client.RocksDbUnauthorizedClientDao;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityDescriptors;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.RocksEntityStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ContextConfiguration(classes = {RocksDbContractTestConfig.class, RocksDbUnauthorizedClientContractTest.Services.class},
        loader = AnnotationConfigContextLoader.class)
public class RocksDbUnauthorizedClientContractTest extends AbstractUnauthorizedClientContractTest {

    // the default of standalone.unauthorized-clients.max, which the context does not set
    private static final int MAX = 10_000;

    @Configuration
    @Import(UnauthorizedClientServiceImpl.class)
    static class Services {
    }

    @Autowired
    private RocksEntityStore entityStore;

    // The cases below hold on the store alone: the SQL table has no count limit.

    @Test
    public void aFloodKeepsTheNewestMaxClients() throws Exception {
        unauthorizedClientService.deleteUnauthorizedClient(savedUnauthorizedClient.getClientId());
        int clients = 2 * MAX;
        long base = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1);
        // each thread saves its share in a random order, so older clients keep arriving after newer ones
        List<List<Integer>> shares = new ArrayList<>();
        Random random = new Random(42);
        for (int t = 0; t < 4; t++) {
            int thread = t;
            List<Integer> share = IntStream.range(0, clients).filter(i -> i % 4 == thread).boxed().collect(Collectors.toList());
            Collections.shuffle(share, random);
            shares.add(share);
        }
        ExecutorService executor = Executors.newFixedThreadPool(4);
        long start = System.nanoTime();
        try {
            List<Future<List<ListenableFuture<Void>>>> threads = new ArrayList<>();
            for (List<Integer> share : shares) {
                threads.add(executor.submit(() -> {
                    List<ListenableFuture<Void>> saves = new ArrayList<>(share.size());
                    for (int i : share) {
                        saves.add(unauthorizedClientService.save(flooding(i, base + i)));
                    }
                    return saves;
                }));
            }
            for (Future<List<ListenableFuture<Void>>> thread : threads) {
                Futures.allAsList(thread.get(30, TimeUnit.SECONDS)).get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
        long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(entityStore.count(EntityDescriptors.UNAUTHORIZED_CLIENT)).isEqualTo(MAX);
        Set<String> kept = entityStore.findAll(EntityDescriptors.UNAUTHORIZED_CLIENT).stream()
                .map(UnauthorizedClient::getClientId).collect(Collectors.toSet());
        Set<String> newest = IntStream.range(clients - MAX, clients).mapToObj(RocksDbUnauthorizedClientContractTest::floodingId)
                .collect(Collectors.toSet());
        assertThat(kept).isEqualTo(newest);
        assertThat(tookMs).as("flood time, ms").isLessThan(30_000);
    }

    @Test
    public void anUpdateOfAnExistingClientDoesNotEvict() throws Exception {
        unauthorizedClientService.deleteUnauthorizedClient(savedUnauthorizedClient.getClientId());
        RocksDbUnauthorizedClientDao dao = new RocksDbUnauthorizedClientDao(entityStore, 3);
        try {
            dao.save(flooding(1, 100)).get();
            dao.save(flooding(2, 200)).get();
            dao.save(flooding(3, 300)).get();

            UnauthorizedClient update = flooding(1, 400).toBuilder().reason("Client blocked").build();
            dao.save(update).get();
            dao.save(flooding(2, 50)).get();

            assertThat(entityStore.findAll(EntityDescriptors.UNAUTHORIZED_CLIENT)).containsExactlyInAnyOrder(
                    update, flooding(2, 50), flooding(3, 300));

            // the update counts: the client saved second, now the oldest, is the one evicted
            dao.save(flooding(4, 500)).get();
            assertThat(entityStore.findAll(EntityDescriptors.UNAUTHORIZED_CLIENT)).extracting(UnauthorizedClient::getClientId)
                    .containsExactlyInAnyOrder(floodingId(1), floodingId(3), floodingId(4));
        } finally {
            dao.deleteAll();
        }
    }

    @Test
    public void aNonPositiveMaxFailsStartup() {
        for (int max : new int[]{0, -1}) {
            assertThatThrownBy(() -> new RocksDbUnauthorizedClientDao(entityStore, max))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("standalone.unauthorized-clients.max")
                    .hasMessageContaining(String.valueOf(max));
        }
    }

    private static UnauthorizedClient flooding(int i, long ts) {
        return UnauthorizedClient.builder().clientId(floodingId(i)).ipAddress("192.168.0.1").reason("Invalid credentials")
                .ts(ts).build();
    }

    private static String floodingId(int i) {
        return String.format("flood-%05d", i);
    }

}
