/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.dao.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.mqtt.broker.common.data.kv.Aggregation;
import org.thingsboard.mqtt.broker.common.data.kv.BaseReadTsKvQuery;
import org.thingsboard.mqtt.broker.common.data.kv.BaseTsKvQuery;
import org.thingsboard.mqtt.broker.common.data.kv.BasicTsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.KvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.LongDataEntry;
import org.thingsboard.mqtt.broker.common.data.kv.ReadTsKvQuery;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@Slf4j
@DaoSqlTest
public class TimeseriesServiceTest extends AbstractServiceTest {

    private final int MAX_TIMEOUT = 30;

    private final String LONG_KEY = "incomingMsgs";

    private final long TS = 42L;
    private final String DESC_ORDER = "DESC";

    KvEntry longKvEntry = new LongDataEntry(LONG_KEY, Long.MAX_VALUE);

    @Autowired
    private TimeseriesService tsService;

    @Test
    public void testFindAllLatest() throws Exception {
        String entityId = RandomStringUtils.randomAlphabetic(20);

        saveEntries(entityId, TS - 2);
        saveEntries(entityId, TS - 1);
        saveEntries(entityId, TS);

        List<TsKvEntry> tsList = tsService.findAllLatest(entityId).get(MAX_TIMEOUT, TimeUnit.SECONDS);

        assertNotNull(tsList);
        assertEquals(6, tsList.size());
        for (TsKvEntry tsKvEntry : tsList) {
            if (tsKvEntry.getKey().equals(LONG_KEY)) {
                assertEquals(TS, tsKvEntry.getTs());
            } else {
                assertNull(tsKvEntry.getValue());
            }
        }
    }

    @Test
    public void testFindLatest() throws Exception {
        String entityId = RandomStringUtils.randomAlphabetic(20);

        saveEntries(entityId, TS - 2);
        saveEntries(entityId, TS - 1);
        saveEntries(entityId, TS);

        List<TsKvEntry> entries = tsService.findLatest(entityId, Collections.singleton(LONG_KEY)).get(MAX_TIMEOUT, TimeUnit.SECONDS);
        Assert.assertEquals(1, entries.size());
        Assert.assertEquals(toTsEntry(TS, longKvEntry), entries.get(0));
    }

    @Test
    public void testFindByQueryAscOrder() throws Exception {
        String entityId = RandomStringUtils.randomAlphabetic(20);

        saveEntries(entityId, TS - 3);
        saveEntries(entityId, TS - 2);
        saveEntries(entityId, TS - 1);

        List<ReadTsKvQuery> queries = new ArrayList<>();
        queries.add(new BaseReadTsKvQuery(LONG_KEY, TS - 3, TS, 0, 1000, Aggregation.NONE, "ASC"));

        List<TsKvEntry> entries = tsService.findAll(entityId, queries).get(MAX_TIMEOUT, TimeUnit.SECONDS);
        Assert.assertEquals(3, entries.size());
        Assert.assertEquals(toTsEntry(TS - 3, longKvEntry), entries.get(0));
        Assert.assertEquals(toTsEntry(TS - 2, longKvEntry), entries.get(1));
        Assert.assertEquals(toTsEntry(TS - 1, longKvEntry), entries.get(2));
    }

    @Test
    public void testFindByQueryDescOrder() throws Exception {
        String entityId = RandomStringUtils.randomAlphabetic(20);

        saveEntries(entityId, TS - 3);
        saveEntries(entityId, TS - 2);
        saveEntries(entityId, TS - 1);

        List<ReadTsKvQuery> queries = new ArrayList<>();
        queries.add(new BaseReadTsKvQuery(LONG_KEY, TS - 3, TS, 0, 1000, Aggregation.NONE, "DESC"));

        List<TsKvEntry> entries = tsService.findAll(entityId, queries).get(MAX_TIMEOUT, TimeUnit.SECONDS);
        Assert.assertEquals(3, entries.size());
        Assert.assertEquals(toTsEntry(TS - 1, longKvEntry), entries.get(0));
        Assert.assertEquals(toTsEntry(TS - 2, longKvEntry), entries.get(1));
        Assert.assertEquals(toTsEntry(TS - 3, longKvEntry), entries.get(2));
    }

    @Test
    public void testFindByQuery_whenPeriodEqualsOneMillisecondPeriod() throws Exception {
        String entityId = RandomStringUtils.randomAlphabetic(20);
        saveEntries(entityId, TS - 1L);
        saveEntries(entityId, TS);
        saveEntries(entityId, TS + 1L);

        List<ReadTsKvQuery> queries = List.of(new BaseReadTsKvQuery(LONG_KEY, TS, TS, 1, 1, Aggregation.COUNT, DESC_ORDER));

        List<TsKvEntry> entries = tsService.findAll(entityId, queries).get();
        Assert.assertEquals(1, entries.size());
        Assert.assertEquals(toTsEntry(TS, new LongDataEntry(LONG_KEY, 1L)), entries.get(0));
    }

    @Test
    public void testFindByQuery_whenPeriodEqualsInterval() throws Exception {
        String entityId = RandomStringUtils.randomAlphabetic(20);
        saveEntries(entityId, TS - 1L);
        for (long i = TS; i <= TS + 100L; i += 10L) {
            saveEntries(entityId, i);
        }
        saveEntries(entityId, TS + 100L + 1L);

        List<ReadTsKvQuery> queries = List.of(new BaseReadTsKvQuery(LONG_KEY, TS, TS + 100, 100, 1, Aggregation.COUNT, DESC_ORDER));

        List<TsKvEntry> entries = tsService.findAll(entityId, queries).get();
        Assert.assertEquals(1, entries.size());
        Assert.assertEquals(toTsEntry(TS + 50, new LongDataEntry(LONG_KEY, 10L)), entries.get(0));
    }

    @Test
    public void testFindByQuery_whenPeriodHaveTwoIntervalWithEqualsLength() throws Exception {
        String entityId = RandomStringUtils.randomAlphabetic(20);
        saveEntries(entityId, TS - 1L);
        for (long i = TS; i <= TS + 100000L; i += 10000L) {
            saveEntries(entityId, i);
        }
        saveEntries(entityId, TS + 100000L + 1L);

        List<ReadTsKvQuery> queries = List.of(new BaseReadTsKvQuery(LONG_KEY, TS, TS + 99999, 50000, 1, Aggregation.COUNT, DESC_ORDER));

        List<TsKvEntry> entries = tsService.findAll(entityId, queries).get();
        Assert.assertEquals(2, entries.size());
        Assert.assertEquals(toTsEntry(TS + 25000, new LongDataEntry(LONG_KEY, 5L)), entries.get(0));
        Assert.assertEquals(toTsEntry(TS + 75000 - 1, new LongDataEntry(LONG_KEY, 5L)), entries.get(1));
    }

    @Test
    public void testFindByQuery_whenPeriodHaveTwoInterval_whereSecondShorterThanFirst() throws Exception {
        String entityId = RandomStringUtils.randomAlphabetic(20);
        saveEntries(entityId, TS - 1L);
        for (long i = TS; i <= TS + 80000L; i += 10000L) {
            saveEntries(entityId, i);
        }
        saveEntries(entityId, TS + 80000L + 1L);

        List<ReadTsKvQuery> queries = List.of(new BaseReadTsKvQuery(LONG_KEY, TS, TS + 80000, 50000, 1, Aggregation.COUNT, DESC_ORDER));

        List<TsKvEntry> entries = tsService.findAll(entityId, queries).get();
        Assert.assertEquals(2, entries.size());
        Assert.assertEquals(toTsEntry(TS + 25000, new LongDataEntry(LONG_KEY, 5L)), entries.get(0));
        Assert.assertEquals(toTsEntry(TS + 65000, new LongDataEntry(LONG_KEY, 3L)), entries.get(1));
    }

    @Test
    public void testFindByQuery_whenPeriodHaveTwoIntervalWithEqualsLength_whereNotAllEntriesInRange() throws Exception {
        String entityId = RandomStringUtils.randomAlphabetic(20);
        for (long i = TS - 1L; i <= TS + 100000L + 1L; i += 10000) {
            saveEntries(entityId, i);
        }

        List<ReadTsKvQuery> queries = List.of(new BaseReadTsKvQuery(LONG_KEY, TS, TS + 99999, 50000, 1, Aggregation.COUNT, DESC_ORDER));

        List<TsKvEntry> entries = tsService.findAll(entityId, queries).get();
        Assert.assertEquals(2, entries.size());
        Assert.assertEquals(toTsEntry(TS + 25000, new LongDataEntry(LONG_KEY, 5L)), entries.get(0));
        Assert.assertEquals(toTsEntry(TS + 75000 - 1, new LongDataEntry(LONG_KEY, 4L)), entries.get(1));
    }

    @Test
    public void testFindByQuery_whenPeriodHaveTwoInterval_whereSecondShorterThanFirst_andNotAllEntriesInRange() throws Exception {
        String entityId = RandomStringUtils.randomAlphabetic(20);
        for (long i = TS - 1L; i <= TS + 100000L + 1L; i += 10000L) {
            saveEntries(entityId, i);
        }

        List<ReadTsKvQuery> queries = List.of(new BaseReadTsKvQuery(LONG_KEY, TS, TS + 80000, 50000, 1, Aggregation.COUNT, DESC_ORDER));

        List<TsKvEntry> entries = tsService.findAll(entityId, queries).get();
        Assert.assertEquals(2, entries.size());
        Assert.assertEquals(toTsEntry(TS + 25000, new LongDataEntry(LONG_KEY, 5L)), entries.get(0));
        Assert.assertEquals(toTsEntry(TS + 65000, new LongDataEntry(LONG_KEY, 3L)), entries.get(1));
    }

    @Test
    public void testDeleteDeviceTsData() throws Exception {
        String entityId = RandomStringUtils.randomAlphabetic(20);

        saveEntries(entityId, 10000);
        saveEntries(entityId, 20000);
        saveEntries(entityId, 30000);
        saveEntries(entityId, 40000);

        tsService.remove(entityId, Collections.singletonList(
                new BaseTsKvQuery(LONG_KEY, 25000, 45000))).get(MAX_TIMEOUT, TimeUnit.SECONDS);

        List<TsKvEntry> list = tsService.findAll(entityId, Collections.singletonList(
                new BaseReadTsKvQuery(LONG_KEY, 5000, 45000, 10000, 10, Aggregation.NONE))).get(MAX_TIMEOUT, TimeUnit.SECONDS);
        Assert.assertEquals(2, list.size());

        List<TsKvEntry> latest = tsService.findLatest(entityId, Collections.singletonList(LONG_KEY)).get(MAX_TIMEOUT, TimeUnit.SECONDS);
        Assert.assertEquals(40000, latest.get(0).getTs());
    }

    @Test
    public void testFindDeviceTsData() throws Exception {
        String entityId = RandomStringUtils.randomAlphabetic(20);

        save(entityId, 5000, 100);
        save(entityId, 15000, 200);

        save(entityId, 25000, 300);
        save(entityId, 35000, 400);

        save(entityId, 45000, 500);
        save(entityId, 55000, 600);

        List<TsKvEntry> list = tsService.findAll(entityId, Collections.singletonList(new BaseReadTsKvQuery(LONG_KEY, 0,
                60000, 20000, 3, Aggregation.NONE))).get(MAX_TIMEOUT, TimeUnit.SECONDS);
        assertEquals(3, list.size());
        assertEquals(55000, list.get(0).getTs());
        assertEquals(Optional.of(600L), list.get(0).getLongValue());

        assertEquals(45000, list.get(1).getTs());
        assertEquals(Optional.of(500L), list.get(1).getLongValue());

        assertEquals(35000, list.get(2).getTs());
        assertEquals(Optional.of(400L), list.get(2).getLongValue());

        list = tsService.findAll(entityId, Collections.singletonList(new BaseReadTsKvQuery(LONG_KEY, 0,
                60000, 20000, 3, Aggregation.AVG))).get(MAX_TIMEOUT, TimeUnit.SECONDS);
        assertEquals(3, list.size());
        assertEquals(10000, list.get(0).getTs());
        assertEquals(Optional.of(150.0), list.get(0).getDoubleValue());

        assertEquals(30000, list.get(1).getTs());
        assertEquals(Optional.of(350.0), list.get(1).getDoubleValue());

        assertEquals(50000, list.get(2).getTs());
        assertEquals(Optional.of(550.0), list.get(2).getDoubleValue());

        list = tsService.findAll(entityId, Collections.singletonList(new BaseReadTsKvQuery(LONG_KEY, 0,
                60000, 20000, 3, Aggregation.SUM))).get(MAX_TIMEOUT, TimeUnit.SECONDS);

        assertEquals(3, list.size());
        assertEquals(10000, list.get(0).getTs());
        assertEquals(Optional.of(300L), list.get(0).getLongValue());

        assertEquals(30000, list.get(1).getTs());
        assertEquals(Optional.of(700L), list.get(1).getLongValue());

        assertEquals(50000, list.get(2).getTs());
        assertEquals(Optional.of(1100L), list.get(2).getLongValue());

        list = tsService.findAll(entityId, Collections.singletonList(new BaseReadTsKvQuery(LONG_KEY, 0,
                60000, 20000, 3, Aggregation.MIN))).get(MAX_TIMEOUT, TimeUnit.SECONDS);

        assertEquals(3, list.size());
        assertEquals(10000, list.get(0).getTs());
        assertEquals(Optional.of(100L), list.get(0).getLongValue());

        assertEquals(30000, list.get(1).getTs());
        assertEquals(Optional.of(300L), list.get(1).getLongValue());

        assertEquals(50000, list.get(2).getTs());
        assertEquals(Optional.of(500L), list.get(2).getLongValue());

        list = tsService.findAll(entityId, Collections.singletonList(new BaseReadTsKvQuery(LONG_KEY, 0,
                60000, 20000, 3, Aggregation.MAX))).get(MAX_TIMEOUT, TimeUnit.SECONDS);

        assertEquals(3, list.size());
        assertEquals(10000, list.get(0).getTs());
        assertEquals(Optional.of(200L), list.get(0).getLongValue());

        assertEquals(30000, list.get(1).getTs());
        assertEquals(Optional.of(400L), list.get(1).getLongValue());

        assertEquals(50000, list.get(2).getTs());
        assertEquals(Optional.of(600L), list.get(2).getLongValue());

        list = tsService.findAll(entityId, Collections.singletonList(new BaseReadTsKvQuery(LONG_KEY, 0,
                60000, 20000, 3, Aggregation.COUNT))).get(MAX_TIMEOUT, TimeUnit.SECONDS);

        assertEquals(3, list.size());
        assertEquals(10000, list.get(0).getTs());
        assertEquals(Optional.of(2L), list.get(0).getLongValue());

        assertEquals(30000, list.get(1).getTs());
        assertEquals(Optional.of(2L), list.get(1).getLongValue());

        assertEquals(50000, list.get(2).getTs());
        assertEquals(Optional.of(2L), list.get(2).getLongValue());
    }

    @Test
    public void testSaveTs_RemoveTs_AndSaveTsAgain() throws Exception {
        String entityId = RandomStringUtils.randomAlphabetic(20);

        save(entityId, 2000000L, 95);
        save(entityId, 4000000L, 100);
        save(entityId, 6000000L, 105);
        List<TsKvEntry> list = tsService.findAll(entityId, Collections.singletonList(new BaseReadTsKvQuery(LONG_KEY, 0L,
                8000000L, 200000, 3, Aggregation.NONE))).get(MAX_TIMEOUT, TimeUnit.SECONDS);
        assertEquals(3, list.size());

        tsService.remove(entityId, Collections.singletonList(
                new BaseTsKvQuery(LONG_KEY, 0L, 8000000L))).get(MAX_TIMEOUT, TimeUnit.SECONDS);
        list = tsService.findAll(entityId, Collections.singletonList(new BaseReadTsKvQuery(LONG_KEY, 0L,
                8000000L, 200000, 3, Aggregation.NONE))).get(MAX_TIMEOUT, TimeUnit.SECONDS);
        assertEquals(0, list.size());

        save(entityId, 2000000L, 99);
        save(entityId, 4000000L, 104);
        save(entityId, 6000000L, 109);
        list = tsService.findAll(entityId, Collections.singletonList(new BaseReadTsKvQuery(LONG_KEY, 0L,
                8000000L, 200000, 3, Aggregation.NONE))).get(MAX_TIMEOUT, TimeUnit.SECONDS);
        assertEquals(3, list.size());
    }

    private void save(String entityId, long ts, long value) throws Exception {
        TsKvEntry entry = new BasicTsKvEntry(ts, new LongDataEntry(LONG_KEY, value));
        tsService.save(entityId, entry).get(MAX_TIMEOUT, TimeUnit.SECONDS);
    }

    private void saveEntries(String entityId, long ts) throws ExecutionException, InterruptedException, TimeoutException {
        tsService.save(entityId, toTsEntry(ts, longKvEntry)).get(MAX_TIMEOUT, TimeUnit.SECONDS);
    }

    private TsKvEntry toTsEntry(long ts, KvEntry entry) {
        return new BasicTsKvEntry(ts, entry);
    }
}
