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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.support.AnnotationConfigContextLoader;
import org.thingsboard.mqtt.broker.common.data.kv.Aggregation;
import org.thingsboard.mqtt.broker.common.data.kv.BaseReadTsKvQuery;
import org.thingsboard.mqtt.broker.common.data.kv.BasicTsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.CleanUpResult;
import org.thingsboard.mqtt.broker.common.data.kv.LongDataEntry;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.dao.contract.AbstractTimeseriesContractTest;
import org.thingsboard.mqtt.broker.dao.rocksdb.RocksDbStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreColumnFamily;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreMemoryBudget;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreProperties;
import org.thingsboard.mqtt.broker.dao.rocksdb.timeseries.RocksDbAggregationTimeseriesDao;
import org.thingsboard.mqtt.broker.dao.rocksdb.timeseries.RocksDbKeyDictionaryDao;
import org.thingsboard.mqtt.broker.dao.rocksdb.timeseries.RocksDbTimeseriesDao;
import org.thingsboard.mqtt.broker.dao.rocksdb.timeseries.RocksDbTimeseriesLatestDao;
import org.thingsboard.mqtt.broker.dao.timeseries.BaseTimeseriesService;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ContextConfiguration(classes = {RocksDbContractTestConfig.class, RocksDbTimeseriesContractTest.Services.class},
        loader = AnnotationConfigContextLoader.class)
@TestPropertySource(properties = "database.ts_max_intervals=700")
public class RocksDbTimeseriesContractTest extends AbstractTimeseriesContractTest {

    private static final String KEY = "incomingMsgs";

    @Configuration
    @Import(BaseTimeseriesService.class)
    static class Services {
    }

    // The cases below run on a store of their own, so the ids and counts they check do not depend on the other cases.
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    // The layout of a dictionary entry is the store's own: the SQL table has a row with a serial id.
    @Test
    public void aKeyDictionaryEntryIsTheUniqueIndexKeyOfTheKeyAndAFourByteId() throws IOException {
        RocksDbStore store = openStore();
        try {
            RocksDbKeyDictionaryDao dictionary = new RocksDbKeyDictionaryDao(store);

            assertThat(dictionary.getOrSaveKeyId(KEY)).isEqualTo(1);
            assertThat(dictionary.getOrSaveKeyId("sessions")).isEqualTo(2);

            byte[] entryKey = ByteBuffer.allocate(5 + 12).put((byte) 64).putInt(12).put(KEY.getBytes(StandardCharsets.UTF_8)).array();
            assertThat(store.get(StoreColumnFamily.ENTITY_INDEX, entryKey)).containsExactly(0, 0, 0, 1);
        } finally {
            store.close();
        }
    }

    // SQL keeps the ids in its table by construction; the store rebuilds its maps from the entries on startup.
    @Test
    public void theKeyDictionaryKeepsItsIdsAcrossARestart() throws IOException {
        String path = folder.newFolder().getPath();
        RocksDbStore store = openStore(path);
        try {
            RocksDbKeyDictionaryDao dictionary = new RocksDbKeyDictionaryDao(store);
            dictionary.getOrSaveKeyId("a");
            dictionary.getOrSaveKeyId("b");
            dictionary.getOrSaveKeyId("клієнт");
        } finally {
            store.close();
        }

        store = openStore(path);
        try {
            RocksDbKeyDictionaryDao dictionary = new RocksDbKeyDictionaryDao(store);

            assertThat(dictionary.getKeyId("a")).isEqualTo(1);
            assertThat(dictionary.getKeyId("b")).isEqualTo(2);
            assertThat(dictionary.getKey(3)).isEqualTo("клієнт");
            assertThat(dictionary.getKeyId("c")).isNull();
            assertThat(dictionary.getKey(4)).isNull();
            assertThat(dictionary.getOrSaveKeyId("b")).isEqualTo(2);
            assertThat(dictionary.getOrSaveKeyId("c")).isEqualTo(4);
        } finally {
            store.close();
        }
    }

    // The SQL DAO counts the partitions it drops; the store has none, and range-deletes the rows of every pair it holds,
    // whether or not the pair still has a latest value.
    @Test
    public void aCleanUpCountsTheRowsItDeletedAndNoPartitions() throws Exception {
        RocksDbStore store = openStore();
        try {
            RocksDbKeyDictionaryDao dictionary = new RocksDbKeyDictionaryDao(store);
            RocksDbAggregationTimeseriesDao aggregation = new RocksDbAggregationTimeseriesDao(store, dictionary);
            RocksDbTimeseriesDao dao = new RocksDbTimeseriesDao(store, dictionary, aggregation);
            RocksDbTimeseriesLatestDao latestDao = new RocksDbTimeseriesLatestDao(store, dictionary, aggregation);
            long now = System.currentTimeMillis();
            long old = now - TimeUnit.HOURS.toMillis(2);
            long recent = now - TimeUnit.MINUTES.toMillis(1);
            for (String entityId : List.of("ab", "abc", "b")) {
                for (String key : List.of(KEY, "sessions")) {
                    dao.save(entityId, entry(key, old, 1)).get();
                    dao.save(entityId, entry(key, old + 1, 2)).get();
                    dao.save(entityId, entry(key, recent, 3)).get();
                }
            }
            // a pair without a latest value is cleaned up as well
            dao.save("orphan", entry(KEY, old, 4)).get();
            latestDao.saveLatest("orphan", entry(KEY, old, 4)).get();
            latestDao.removeLatest("orphan", KEY).get();

            CleanUpResult result = dao.cleanUp(TimeUnit.HOURS.toSeconds(1));

            assertThat(result).isEqualTo(new CleanUpResult(0, 3 * 2 * 2 + 1));
            BaseReadTsKvQuery all = new BaseReadTsKvQuery(KEY, 0, now, 0, 10, Aggregation.NONE, "ASC");
            assertThat(dao.findAllAsync("ab", List.of(all)).get()).containsExactly(entry(KEY, recent, 3));
            assertThat(dao.findAllAsync("orphan", List.of(all)).get()).isEmpty();
            assertThat(dao.cleanUp(TimeUnit.HOURS.toSeconds(1))).isEqualTo(new CleanUpResult(0, 0));
        } finally {
            store.close();
        }
    }

    private static TsKvEntry entry(String key, long ts, long value) {
        return new BasicTsKvEntry(ts, new LongDataEntry(key, value));
    }

    private RocksDbStore openStore() throws IOException {
        return openStore(folder.newFolder().getPath());
    }

    private static RocksDbStore openStore(String path) {
        StoreProperties properties = new StoreProperties();
        properties.setPath(path);
        RocksDbStore store = new RocksDbStore(properties, StoreMemoryBudget.forContainerMemory(1L << 30, 0, 0));
        store.open();
        return store;
    }

}
