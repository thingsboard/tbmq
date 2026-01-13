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
package org.thingsboard.mqtt.broker.service.testing.integration.ts;

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
@ContextConfiguration(classes = TimeseriesYearsIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "sql.ts_key_value_partitioning=YEARS"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class TimeseriesYearsIntegrationTestCase extends AbstractPubSubIntegrationTest {

    private static final long TTL_1_YEAR_SEC = 2628000 * 12;
    private static final long TTL_1_YEAR_MS = TTL_1_YEAR_SEC * 1000;
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
    public void givenSavedRecordsOlderThanMaxPartitionByTTL_whenExecuteCleanUpForYears_thenRemovedPartitions() throws Throwable {
        long ts1 = LocalDate.now().minusYears(2).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
        long ts2 = LocalDate.now().minusYears(2).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
        long ts3 = LocalDate.now().minusYears(3).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
        long ts4 = LocalDate.now().minusYears(3).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
        long ts5 = LocalDate.now().minusYears(4).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();

        String entityId = RandomStringUtils.randomAlphabetic(10);
        List<TsKvEntry> kvEntries = List.of(
                new BasicTsKvEntry(ts1, KV),
                new BasicTsKvEntry(ts2, KV),
                new BasicTsKvEntry(ts3, KV),
                new BasicTsKvEntry(ts4, KV),
                new BasicTsKvEntry(ts5, KV)
        );

        timeseriesService.save(entityId, kvEntries).get(30, TimeUnit.SECONDS);

        CleanUpResult cleanUpResult = timeseriesService.cleanUp(TTL_1_YEAR_SEC);
        Assert.assertEquals(3, cleanUpResult.getDeletedPartitions());
        Assert.assertEquals(0, cleanUpResult.getDeletedRows());
    }

    @Test
    public void givenSavedRecords_whenExecuteCleanUpForYears_thenRemovedPartitionsAndRows() throws Throwable {
        long ts1 = System.currentTimeMillis() - TTL_1_YEAR_MS;
        long ts2 = System.currentTimeMillis() - TTL_1_YEAR_MS * 2 - ONE_HOUR_MS;
        long ts3 = System.currentTimeMillis() - TTL_1_YEAR_MS * 3;
        long ts4 = System.currentTimeMillis() - TTL_1_YEAR_MS * 4;
        long ts5 = System.currentTimeMillis() - TTL_1_YEAR_MS * 5;

        String entityId = RandomStringUtils.randomAlphabetic(10);
        List<TsKvEntry> kvEntries = List.of(
                new BasicTsKvEntry(ts1, KV),
                new BasicTsKvEntry(ts2, KV),
                new BasicTsKvEntry(ts3, KV),
                new BasicTsKvEntry(ts4, KV),
                new BasicTsKvEntry(ts5, KV)
        );

        timeseriesService.save(entityId, kvEntries).get(30, TimeUnit.SECONDS);

        CleanUpResult cleanUpResult = timeseriesService.cleanUp(TTL_1_YEAR_SEC * 2);
        Assert.assertEquals(3, cleanUpResult.getDeletedPartitions());
        Assert.assertEquals(1, cleanUpResult.getDeletedRows());

    }

}
