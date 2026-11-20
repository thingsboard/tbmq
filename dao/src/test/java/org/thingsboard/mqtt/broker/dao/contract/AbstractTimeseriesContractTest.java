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
package org.thingsboard.mqtt.broker.dao.contract;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.kv.Aggregation;
import org.thingsboard.mqtt.broker.common.data.kv.BaseDeleteTsKvQuery;
import org.thingsboard.mqtt.broker.common.data.kv.BaseReadTsKvQuery;
import org.thingsboard.mqtt.broker.common.data.kv.BaseTsKvQuery;
import org.thingsboard.mqtt.broker.common.data.kv.BasicTsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.CleanUpResult;
import org.thingsboard.mqtt.broker.common.data.kv.DoubleDataEntry;
import org.thingsboard.mqtt.broker.common.data.kv.KvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.LongDataEntry;
import org.thingsboard.mqtt.broker.common.data.kv.ReadTsKvQuery;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvLatestRemovingResult;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesLatestDao;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.ASC_ORDER;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.DESC_ORDER;

@Slf4j
public abstract class AbstractTimeseriesContractTest extends AbstractContractTest {

    private final int MAX_TIMEOUT = 30;

    private final String LONG_KEY = "incomingMsgs";

    private final long TS = 42L;

    KvEntry longKvEntry = new LongDataEntry(LONG_KEY, Long.MAX_VALUE);

    @Autowired
    protected TimeseriesService tsService;

    @Autowired
    protected TimeseriesLatestDao timeseriesLatestDao;

    @Test
    public void testFindAllLatest() throws Exception {
        String entityId = RandomStringUtils.randomAlphabetic(20);

        saveEntries(entityId, TS - 2);
        saveEntries(entityId, TS - 1);
        saveEntries(entityId, TS);

        List<TsKvEntry> tsList = tsService.findAllLatest(entityId).get(MAX_TIMEOUT, TimeUnit.SECONDS);

        assertNotNull(tsList);
        assertEquals(1, tsList.size());
        assertEquals(LONG_KEY, tsList.get(0).getKey());
        assertEquals(TS, tsList.get(0).getTs());
    }

    @Test
    public void testRemoveLatest() throws Exception {
        String entityId = RandomStringUtils.randomAlphabetic(20);

        saveEntries(entityId, TS - 2);
        saveEntries(entityId, TS - 1);
        saveEntries(entityId, TS);

        List<TsKvEntry> tsList = tsService.findAllLatest(entityId).get(MAX_TIMEOUT, TimeUnit.SECONDS);
        assertNotNull(tsList);

        tsService.removeLatest(entityId, List.of(LONG_KEY)).get(MAX_TIMEOUT, TimeUnit.SECONDS);

        tsList = tsService.findAllLatest(entityId).get(MAX_TIMEOUT, TimeUnit.SECONDS);
        assertTrue(tsList.isEmpty());
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
        queries.add(new BaseReadTsKvQuery(LONG_KEY, TS - 3, TS, 0, 1000, Aggregation.NONE, ASC_ORDER));

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
        queries.add(new BaseReadTsKvQuery(LONG_KEY, TS - 3, TS, 0, 1000, Aggregation.NONE, DESC_ORDER));

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

        List<ReadTsKvQuery> queries = List.of(new BaseReadTsKvQuery(LONG_KEY, TS, TS, 1, 1, Aggregation.COUNT, ASC_ORDER));

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

        List<ReadTsKvQuery> queries = List.of(new BaseReadTsKvQuery(LONG_KEY, TS, TS + 100, 100, 1, Aggregation.COUNT, ASC_ORDER));

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

        List<ReadTsKvQuery> queries = List.of(new BaseReadTsKvQuery(LONG_KEY, TS, TS + 99999, 50000, 1, Aggregation.COUNT, ASC_ORDER));

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

        List<ReadTsKvQuery> queries = List.of(new BaseReadTsKvQuery(LONG_KEY, TS, TS + 80000, 50000, 1, Aggregation.COUNT, ASC_ORDER));

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

        List<ReadTsKvQuery> queries = List.of(new BaseReadTsKvQuery(LONG_KEY, TS, TS + 99999, 50000, 1, Aggregation.COUNT, ASC_ORDER));

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

        List<ReadTsKvQuery> queries = List.of(new BaseReadTsKvQuery(LONG_KEY, TS, TS + 80000, 50000, 1, Aggregation.COUNT, ASC_ORDER));

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
                60000, 20000, 3, Aggregation.AVG, ASC_ORDER))).get(MAX_TIMEOUT, TimeUnit.SECONDS);
        assertEquals(3, list.size());
        assertEquals(10000, list.get(0).getTs());
        assertEquals(Optional.of(150.0), list.get(0).getDoubleValue());

        assertEquals(30000, list.get(1).getTs());
        assertEquals(Optional.of(350.0), list.get(1).getDoubleValue());

        assertEquals(50000, list.get(2).getTs());
        assertEquals(Optional.of(550.0), list.get(2).getDoubleValue());

        list = tsService.findAll(entityId, Collections.singletonList(new BaseReadTsKvQuery(LONG_KEY, 0,
                60000, 20000, 3, Aggregation.SUM, ASC_ORDER))).get(MAX_TIMEOUT, TimeUnit.SECONDS);

        assertEquals(3, list.size());
        assertEquals(10000, list.get(0).getTs());
        assertEquals(Optional.of(300L), list.get(0).getLongValue());

        assertEquals(30000, list.get(1).getTs());
        assertEquals(Optional.of(700L), list.get(1).getLongValue());

        assertEquals(50000, list.get(2).getTs());
        assertEquals(Optional.of(1100L), list.get(2).getLongValue());

        list = tsService.findAll(entityId, Collections.singletonList(new BaseReadTsKvQuery(LONG_KEY, 0,
                60000, 20000, 3, Aggregation.MIN, ASC_ORDER))).get(MAX_TIMEOUT, TimeUnit.SECONDS);

        assertEquals(3, list.size());
        assertEquals(10000, list.get(0).getTs());
        assertEquals(Optional.of(100L), list.get(0).getLongValue());

        assertEquals(30000, list.get(1).getTs());
        assertEquals(Optional.of(300L), list.get(1).getLongValue());

        assertEquals(50000, list.get(2).getTs());
        assertEquals(Optional.of(500L), list.get(2).getLongValue());

        list = tsService.findAll(entityId, Collections.singletonList(new BaseReadTsKvQuery(LONG_KEY, 0,
                60000, 20000, 3, Aggregation.MAX, ASC_ORDER))).get(MAX_TIMEOUT, TimeUnit.SECONDS);

        assertEquals(3, list.size());
        assertEquals(10000, list.get(0).getTs());
        assertEquals(Optional.of(200L), list.get(0).getLongValue());

        assertEquals(30000, list.get(1).getTs());
        assertEquals(Optional.of(400L), list.get(1).getLongValue());

        assertEquals(50000, list.get(2).getTs());
        assertEquals(Optional.of(600L), list.get(2).getLongValue());

        list = tsService.findAll(entityId, Collections.singletonList(new BaseReadTsKvQuery(LONG_KEY, 0,
                60000, 20000, 3, Aggregation.COUNT, ASC_ORDER))).get(MAX_TIMEOUT, TimeUnit.SECONDS);

        assertEquals(3, list.size());
        assertEquals(10000, list.get(0).getTs());
        assertEquals(Optional.of(2L), list.get(0).getLongValue());

        assertEquals(30000, list.get(1).getTs());
        assertEquals(Optional.of(2L), list.get(1).getLongValue());

        assertEquals(50000, list.get(2).getTs());
        assertEquals(Optional.of(2L), list.get(2).getLongValue());
    }

    @Test
    public void testFindDeviceTsDataDesc() throws Exception {
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
                60000, 20000, 3, Aggregation.AVG, DESC_ORDER))).get(MAX_TIMEOUT, TimeUnit.SECONDS);
        assertEquals(3, list.size());
        assertEquals(10000, list.get(2).getTs());
        assertEquals(Optional.of(150.0), list.get(2).getDoubleValue());

        assertEquals(30000, list.get(1).getTs());
        assertEquals(Optional.of(350.0), list.get(1).getDoubleValue());

        assertEquals(50000, list.get(0).getTs());
        assertEquals(Optional.of(550.0), list.get(0).getDoubleValue());

        list = tsService.findAll(entityId, Collections.singletonList(new BaseReadTsKvQuery(LONG_KEY, 0,
                60000, 20000, 3, Aggregation.SUM, DESC_ORDER))).get(MAX_TIMEOUT, TimeUnit.SECONDS);

        assertEquals(3, list.size());
        assertEquals(10000, list.get(2).getTs());
        assertEquals(Optional.of(300L), list.get(2).getLongValue());

        assertEquals(30000, list.get(1).getTs());
        assertEquals(Optional.of(700L), list.get(1).getLongValue());

        assertEquals(50000, list.get(0).getTs());
        assertEquals(Optional.of(1100L), list.get(0).getLongValue());

        list = tsService.findAll(entityId, Collections.singletonList(new BaseReadTsKvQuery(LONG_KEY, 0,
                60000, 20000, 3, Aggregation.MIN, DESC_ORDER))).get(MAX_TIMEOUT, TimeUnit.SECONDS);

        assertEquals(3, list.size());
        assertEquals(10000, list.get(2).getTs());
        assertEquals(Optional.of(100L), list.get(2).getLongValue());

        assertEquals(30000, list.get(1).getTs());
        assertEquals(Optional.of(300L), list.get(1).getLongValue());

        assertEquals(50000, list.get(0).getTs());
        assertEquals(Optional.of(500L), list.get(0).getLongValue());

        list = tsService.findAll(entityId, Collections.singletonList(new BaseReadTsKvQuery(LONG_KEY, 0,
                60000, 20000, 3, Aggregation.MAX, DESC_ORDER))).get(MAX_TIMEOUT, TimeUnit.SECONDS);

        assertEquals(3, list.size());
        assertEquals(10000, list.get(2).getTs());
        assertEquals(Optional.of(200L), list.get(2).getLongValue());

        assertEquals(30000, list.get(1).getTs());
        assertEquals(Optional.of(400L), list.get(1).getLongValue());

        assertEquals(50000, list.get(0).getTs());
        assertEquals(Optional.of(600L), list.get(0).getLongValue());

        list = tsService.findAll(entityId, Collections.singletonList(new BaseReadTsKvQuery(LONG_KEY, 0,
                60000, 20000, 3, Aggregation.COUNT, DESC_ORDER))).get(MAX_TIMEOUT, TimeUnit.SECONDS);

        assertEquals(3, list.size());
        assertEquals(10000, list.get(2).getTs());
        assertEquals(Optional.of(2L), list.get(2).getLongValue());

        assertEquals(30000, list.get(1).getTs());
        assertEquals(Optional.of(2L), list.get(1).getLongValue());

        assertEquals(50000, list.get(0).getTs());
        assertEquals(Optional.of(2L), list.get(0).getLongValue());
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

    @Test
    public void aLimitedReadReturnsTheOldestAscendingAndTheNewestDescending() throws Exception {
        String entityId = RandomStringUtils.randomAlphabetic(20);
        save(entityId, 10, 1);
        save(entityId, 20, 2);
        save(entityId, 30, 3);

        assertThat(findAll(entityId, new BaseReadTsKvQuery(LONG_KEY, 10, 31, 0, 2, Aggregation.NONE, ASC_ORDER)))
                .containsExactly(longEntry(10, 1), longEntry(20, 2));
        assertThat(findAll(entityId, new BaseReadTsKvQuery(LONG_KEY, 10, 31, 0, 2, Aggregation.NONE, DESC_ORDER)))
                .containsExactly(longEntry(30, 3), longEntry(20, 2));
        // the end of the range is excluded
        assertThat(findAll(entityId, new BaseReadTsKvQuery(LONG_KEY, 10, 30, 0, 5, Aggregation.NONE, DESC_ORDER)))
                .containsExactly(longEntry(20, 2), longEntry(10, 1));
        assertThat(findAll(entityId, new BaseReadTsKvQuery(LONG_KEY, 11, 20, 0, 5, Aggregation.NONE, ASC_ORDER))).isEmpty();
    }

    @Test
    public void anAggregationSkipsAnEmptyBucketAndKeepsTheValueTypes() throws Exception {
        String entityId = RandomStringUtils.randomAlphabetic(20);
        // buckets [0, 20000), [20000, 40000) left empty, and the shorter [40000, 50000)
        save(entityId, 5000, 100);
        save(entityId, 15000, 201);
        save(entityId, 45000, 7);
        save(entityId, 50000, 1000);

        assertThat(aggregate(entityId, Aggregation.AVG, ASC_ORDER))
                .containsExactly(doubleEntry(10000, 150.5), doubleEntry(45000, 7.0));
        assertThat(aggregate(entityId, Aggregation.SUM, ASC_ORDER))
                .containsExactly(longEntry(10000, 301), longEntry(45000, 7));
        assertThat(aggregate(entityId, Aggregation.MIN, ASC_ORDER))
                .containsExactly(longEntry(10000, 100), longEntry(45000, 7));
        assertThat(aggregate(entityId, Aggregation.MAX, ASC_ORDER))
                .containsExactly(longEntry(10000, 201), longEntry(45000, 7));
        assertThat(aggregate(entityId, Aggregation.COUNT, ASC_ORDER))
                .containsExactly(longEntry(10000, 2), longEntry(45000, 1));
        assertThat(aggregate(entityId, Aggregation.COUNT, DESC_ORDER))
                .containsExactly(longEntry(45000, 1), longEntry(10000, 2));
        assertThat(aggregate(RandomStringUtils.randomAlphabetic(20), Aggregation.AVG, ASC_ORDER)).isEmpty();
    }

    @Test
    public void aRemovalDeletesItsRangeWithTheEndExcluded() throws Exception {
        String entityId = RandomStringUtils.randomAlphabetic(20);
        save(entityId, 10000, 1);
        save(entityId, 20000, 2);
        save(entityId, 30000, 3);
        save(entityId, 40000, 4);

        tsService.remove(entityId, List.of(new BaseTsKvQuery(LONG_KEY, 20000, 40000))).get(MAX_TIMEOUT, TimeUnit.SECONDS);

        assertThat(findAll(entityId, new BaseReadTsKvQuery(LONG_KEY, 0, 50000, 0, 10, Aggregation.NONE, ASC_ORDER)))
                .containsExactly(longEntry(10000, 1), longEntry(40000, 4));
    }

    @Test
    public void aCleanUpRemovesOnlyTheEntriesOlderThanTheTtl() throws Exception {
        String entityId = RandomStringUtils.randomAlphabetic(20);
        String otherEntityId = RandomStringUtils.randomAlphabetic(20);
        long now = System.currentTimeMillis();
        long old = now - TimeUnit.HOURS.toMillis(2);
        long recent = now - TimeUnit.MINUTES.toMillis(1);
        save(entityId, old, 1);
        save(entityId, recent, 2);
        save(otherEntityId, old, 3);

        CleanUpResult result = tsService.cleanUp(TimeUnit.HOURS.toSeconds(1));

        // the SQL DAO may drop the partition of an old entry rather than delete its row
        assertThat(result.getDeletedRows() + result.getDeletedPartitions()).isPositive();
        ReadTsKvQuery all = new BaseReadTsKvQuery(LONG_KEY, 0, now + 1, 0, 10, Aggregation.NONE, ASC_ORDER);
        assertThat(findAll(entityId, all)).containsExactly(longEntry(recent, 2));
        assertThat(findAll(otherEntityId, all)).isEmpty();
        // the latest values are kept
        assertThat(timeseriesLatestDao.findLatestOpt(otherEntityId, LONG_KEY).get(MAX_TIMEOUT, TimeUnit.SECONDS))
                .contains(longEntry(old, 3));
    }

    @Test
    public void theLatestValueOfAKeyIsTheOneSavedLast() throws Exception {
        String entityId = RandomStringUtils.randomAlphabetic(20);
        save(entityId, 100, 1);
        save(entityId, 200, 2);

        assertThat(tsService.findLatest(entityId, List.of(LONG_KEY)).get(MAX_TIMEOUT, TimeUnit.SECONDS))
                .containsExactly(longEntry(200, 2));
        assertThat(timeseriesLatestDao.findLatestOpt(entityId, LONG_KEY).get(MAX_TIMEOUT, TimeUnit.SECONDS))
                .contains(longEntry(200, 2));

        String keyWithoutValue = RandomStringUtils.randomAlphabetic(10);
        assertThat(timeseriesLatestDao.findLatestOpt(entityId, keyWithoutValue).get(MAX_TIMEOUT, TimeUnit.SECONDS)).isEmpty();
        TsKvEntry none = timeseriesLatestDao.findLatest(entityId, keyWithoutValue).get(MAX_TIMEOUT, TimeUnit.SECONDS);
        assertThat(none.getKey()).isEqualTo(keyWithoutValue);
        assertThat(none.getValue()).isNull();
        assertThat(none.getTs() % TimeUnit.MINUTES.toMillis(1)).isZero();
    }

    @Test
    public void theLatestValuesOfANodeAndOfAClientAreReadInTheOrderOfTheirKeys() throws Exception {
        String entityId = RandomStringUtils.randomAlphabetic(20);
        String nodeKey = BrokerConstants.HISTORICAL_KEYS.get(1);
        String clientKey = BrokerConstants.CLIENT_SESSION_METRIC_KEYS.get(2);
        save(entityId, nodeKey, 100, 5);
        save(entityId, clientKey, 100, 7);

        List<Optional<TsKvEntry>> node = timeseriesLatestDao.findAllLatestForNode(entityId).get(MAX_TIMEOUT, TimeUnit.SECONDS);
        assertThat(node).hasSize(BrokerConstants.HISTORICAL_KEYS.size());
        for (int i = 0; i < node.size(); i++) {
            assertThat(node.get(i)).isEqualTo(i == 1 ? Optional.of(entry(nodeKey, 100, 5)) : Optional.empty());
        }
        List<Optional<TsKvEntry>> client = timeseriesLatestDao.findAllLatestForClient(entityId).get(MAX_TIMEOUT, TimeUnit.SECONDS);
        assertThat(client).hasSize(BrokerConstants.CLIENT_SESSION_METRIC_KEYS.size());
        for (int i = 0; i < client.size(); i++) {
            assertThat(client.get(i)).isEqualTo(i == 2 ? Optional.of(entry(clientKey, 100, 7)) : Optional.empty());
        }

        assertThat(tsService.removeAllLatestForClient(entityId).get(MAX_TIMEOUT, TimeUnit.SECONDS)).containsExactly(clientKey);
        assertThat(tsService.findAllLatest(entityId).get(MAX_TIMEOUT, TimeUnit.SECONDS)).containsExactly(entry(nodeKey, 100, 5));
    }

    @Test
    public void aLatestRemovalReportsWhatItRemoved() throws Exception {
        String entityId = RandomStringUtils.randomAlphabetic(20);
        save(entityId, TS - 10, 1);
        save(entityId, TS - 5, 2);
        save(entityId, TS, 3);

        // out of the range of the query: kept
        TsKvLatestRemovingResult kept = timeseriesLatestDao.removeLatest(entityId,
                new BaseDeleteTsKvQuery(LONG_KEY, TS + 1, TS + 100, true)).get(MAX_TIMEOUT, TimeUnit.SECONDS);
        assertThat(kept).isEqualTo(new TsKvLatestRemovingResult(LONG_KEY, false));
        assertThat(latest(entityId)).contains(longEntry(TS, 3));

        // removed and rewritten from the time series older than the range
        TsKvLatestRemovingResult rewritten = timeseriesLatestDao.removeLatest(entityId,
                new BaseDeleteTsKvQuery(LONG_KEY, TS, TS + 1, true)).get(MAX_TIMEOUT, TimeUnit.SECONDS);
        assertThat(rewritten).isEqualTo(new TsKvLatestRemovingResult(longEntry(TS - 5, 2)));
        assertThat(latest(entityId)).contains(longEntry(TS - 5, 2));

        // removed without a rewrite
        TsKvLatestRemovingResult removed = timeseriesLatestDao.removeLatest(entityId,
                new BaseDeleteTsKvQuery(LONG_KEY, 0, TS, false)).get(MAX_TIMEOUT, TimeUnit.SECONDS);
        assertThat(removed).isEqualTo(new TsKvLatestRemovingResult(LONG_KEY, true));
        assertThat(latest(entityId)).isEmpty();
        // and nothing left to remove
        assertThat(timeseriesLatestDao.removeLatest(entityId, new BaseDeleteTsKvQuery(LONG_KEY, 0, TS, false))
                .get(MAX_TIMEOUT, TimeUnit.SECONDS)).isEqualTo(new TsKvLatestRemovingResult(LONG_KEY, false));

        // by key
        save(entityId, TS + 1, 4);
        assertThat(timeseriesLatestDao.removeLatest(entityId, LONG_KEY).get(MAX_TIMEOUT, TimeUnit.SECONDS))
                .isEqualTo(new TsKvLatestRemovingResult(LONG_KEY, true));
        assertThat(latest(entityId)).isEmpty();
        String neverSaved = RandomStringUtils.randomAlphabetic(20);
        assertThat(timeseriesLatestDao.removeLatest(entityId, neverSaved).get(MAX_TIMEOUT, TimeUnit.SECONDS))
                .isEqualTo(new TsKvLatestRemovingResult(neverSaved, false));
    }

    private List<TsKvEntry> findAll(String entityId, ReadTsKvQuery query) throws Exception {
        return tsService.findAll(entityId, List.of(query)).get(MAX_TIMEOUT, TimeUnit.SECONDS);
    }

    private List<TsKvEntry> aggregate(String entityId, Aggregation aggregation, String order) throws Exception {
        return findAll(entityId, new BaseReadTsKvQuery(LONG_KEY, 0, 50000, 20000, 1, aggregation, order));
    }

    private Optional<TsKvEntry> latest(String entityId) throws Exception {
        return timeseriesLatestDao.findLatestOpt(entityId, LONG_KEY).get(MAX_TIMEOUT, TimeUnit.SECONDS);
    }

    private void save(String entityId, String key, long ts, long value) throws Exception {
        tsService.save(entityId, entry(key, ts, value)).get(MAX_TIMEOUT, TimeUnit.SECONDS);
    }

    private TsKvEntry longEntry(long ts, long value) {
        return entry(LONG_KEY, ts, value);
    }

    private static TsKvEntry entry(String key, long ts, long value) {
        return new BasicTsKvEntry(ts, new LongDataEntry(key, value));
    }

    private TsKvEntry doubleEntry(long ts, double value) {
        return new BasicTsKvEntry(ts, new DoubleDataEntry(LONG_KEY, value));
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
