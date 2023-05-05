/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.integration.ts;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.common.data.kv.BasicTsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.CleanUpResult;
import org.thingsboard.mqtt.broker.common.data.kv.LongDataEntry;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.PostgreSqlInitializer;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesService;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = TimeseriesDaysIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "sql.ts_key_value_partitioning=DAYS"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class TimeseriesDaysIntegrationTestCase extends AbstractPubSubIntegrationTest {

    private static final int TTL_7_DAYS_SEC = 604800;
    private static final int TTL_1_DAY_MS = 86400000;
    private static final String KEY = "KEY";
    private static final LongDataEntry KV = new LongDataEntry(KEY, 1L);

    @Autowired
    private TimeseriesService timeseriesService;

    @Autowired
    protected DataSource dataSource;

    @Before
    public void dropTsKvPartitions() {
        try {
            PostgreSqlInitializer.dropTsKvPartitions(dataSource.getConnection());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void givenSavedRecordsOlderThanMaxPartitionByTTL_whenExecuteCleanUpForDays_thenRemovedPartitions() throws Throwable {
        long ts1 = LocalDate.now().minusDays(10)
                .atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
        long ts2 = LocalDate.now().minusMonths(1).minusDays(13)
                .atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
        long ts3 = LocalDate.now().minusMonths(1).minusDays(7)
                .atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
        long ts4 = LocalDate.now().minusDays(4)
                .atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();

        String entityId = RandomStringUtils.randomAlphabetic(10);
        List<TsKvEntry> kvEntries = List.of(
                new BasicTsKvEntry(ts1, KV),
                new BasicTsKvEntry(ts2, KV),
                new BasicTsKvEntry(ts3, KV),
                new BasicTsKvEntry(ts4, KV)
        );
        timeseriesService.save(entityId, kvEntries).get(30, TimeUnit.SECONDS);

        CleanUpResult cleanUpResult = timeseriesService.cleanUp(TTL_7_DAYS_SEC);
        Assert.assertEquals(3, cleanUpResult.getDeletedPartitions());
        Assert.assertEquals(0, cleanUpResult.getDeletedRows());
    }

    @Test
    public void givenSavedRecords_whenExecuteCleanUpForDays_thenRemovedPartitionsAndRows() throws Throwable {
        long ts1 = System.currentTimeMillis() - 10 * TTL_1_DAY_MS;
        long ts2 = System.currentTimeMillis() - 9 * TTL_1_DAY_MS;
        long ts3 = System.currentTimeMillis() - 8 * TTL_1_DAY_MS;
        long ts4 = System.currentTimeMillis() - 7 * TTL_1_DAY_MS - 30000;
        long ts5 = System.currentTimeMillis() - 7 * TTL_1_DAY_MS - 20000;
        long ts6 = System.currentTimeMillis() - 7 * TTL_1_DAY_MS - 10000;

        String entityId = RandomStringUtils.randomAlphabetic(10);
        List<TsKvEntry> kvEntries = List.of(
                new BasicTsKvEntry(ts1, KV),
                new BasicTsKvEntry(ts2, KV),
                new BasicTsKvEntry(ts3, KV),
                new BasicTsKvEntry(ts4, KV),
                new BasicTsKvEntry(ts5, KV),
                new BasicTsKvEntry(ts6, KV)
        );
        timeseriesService.save(entityId, kvEntries).get(30, TimeUnit.SECONDS);

        CleanUpResult cleanUpResult = timeseriesService.cleanUp(TTL_7_DAYS_SEC);
        Assert.assertEquals(3, cleanUpResult.getDeletedPartitions());
        Assert.assertEquals(3, cleanUpResult.getDeletedRows());
    }

    @Test
    public void givenSavedRecords_whenExecuteCleanUpForDay_thenRemovedPartitionsAndRows() throws Throwable {
        long ts1 = System.currentTimeMillis() - TTL_1_DAY_MS * 2;
        long ts2 = System.currentTimeMillis() - TTL_1_DAY_MS * 3;
        long ts3 = System.currentTimeMillis() - TTL_1_DAY_MS * 4;
        long ts4 = System.currentTimeMillis() - TTL_1_DAY_MS - 30000;
        long ts5 = System.currentTimeMillis() - TTL_1_DAY_MS - 20000;
        long ts6 = System.currentTimeMillis() - TTL_1_DAY_MS - 10000;

        String entityId = RandomStringUtils.randomAlphabetic(10);
        List<TsKvEntry> kvEntries = List.of(
                new BasicTsKvEntry(ts1, KV),
                new BasicTsKvEntry(ts2, KV),
                new BasicTsKvEntry(ts3, KV),
                new BasicTsKvEntry(ts4, KV),
                new BasicTsKvEntry(ts5, KV),
                new BasicTsKvEntry(ts6, KV)
        );
        timeseriesService.save(entityId, kvEntries).get(30, TimeUnit.SECONDS);

        CleanUpResult cleanUpResult = timeseriesService.cleanUp(TTL_7_DAYS_SEC / 7);
        Assert.assertEquals(3, cleanUpResult.getDeletedPartitions());
        Assert.assertEquals(3, cleanUpResult.getDeletedRows());
    }

}
